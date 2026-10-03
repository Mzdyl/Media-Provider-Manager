package me.gm.cleaner.plugin;

import android.content.Context;
import android.content.ContextWrapper;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Looper;
import androidx.room.Room;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.framework.FrameworkSQLiteDatabase;
import me.gm.cleaner.plugin.dao.MediaProviderRecord;
import me.gm.cleaner.plugin.dao.MediaProviderRecordDatabase;
import me.gm.cleaner.plugin.dao.MediaProviderRecordKt;
import me.gm.cleaner.plugin.recording.RecordBuffer;
import me.gm.cleaner.plugin.recording.RecordPolicy;
import me.gm.cleaner.plugin.recording.RecordSchema;
import me.gm.cleaner.plugin.recording.RecordStore;
import me.gm.cleaner.plugin.recording.RecordWriter;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import kotlin.Unit;

/** Runs through app_process against synthetic files only; no APK installation or provider access. */
public final class RecordStorageProbe {
    private static File root;
    private static Context context;
    private static final long NOW = 1_800_000_000_000L;
    private static final List<MediaProviderRecordDatabase> opened = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        root = new File(args[0]).getCanonicalFile();
        check(root.getPath().startsWith("/data/local/tmp/mpm-record-test-"), "Unsafe test directory");
        check(root.isDirectory(), "Create the isolated test directory first");
        Looper.prepareMainLooper();
        Class<?> activityThread = Class.forName("android.app.ActivityThread");
        Object thread = activityThread.getMethod("systemMain").invoke(null);
        Context base = (Context) activityThread.getMethod("getSystemContext").invoke(thread);
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out), true));
        context = new ContextWrapper(base) {
            @Override public Context getApplicationContext() { return this; }
            @Override public File getDatabasePath(String name) { return new File(root, name); }
            @Override public File getFilesDir() { return root; }
            @Override public File getCacheDir() { return root; }
            @Override public File getNoBackupFilesDir() { return root; }
            @Override public File getDataDir() { return root; }
            @Override public ClassLoader getClassLoader() { return RecordStorageProbe.class.getClassLoader(); }
        };
        try {
            boundsAndRestart();
            legacyMigration();
            clearDuringMigration();
            readerBackpressure();
            writerBarriers();
            System.out.println("RECORD_STORAGE_PROBE_PASS");
        } catch (Throwable failure) {
            failure.printStackTrace(System.out);
            throw failure;
        } finally {
            for (MediaProviderRecordDatabase db : opened) if (db.isOpen()) db.close();
        }
    }

    private static MediaProviderRecordDatabase open(String name) {
        MediaProviderRecordDatabase database = Room.databaseBuilder(context, MediaProviderRecordDatabase.class, name)
            .addMigrations(MediaProviderRecordKt.getMIGRATION_1_4(), MediaProviderRecordKt.getMIGRATION_2_4(), MediaProviderRecordKt.getMIGRATION_3_4())
            .setJournalMode(androidx.room.RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .allowMainThreadQueries() // Test process only. Production never opts into main-thread SQL.
            .build();
        opened.add(database);
        return database;
    }

    private static MediaProviderRecord record(long time, int operation, String key, String path) {
        return new MediaProviderRecord(0, time, "probe.app", 1, operation, Collections.singletonList(path),
            Collections.singletonList("image/png"), Collections.singletonList(true), 1, time, time, 1, false, true, key);
    }

    private static long value(SupportSQLiteDatabase db, String sql) {
        try (Cursor cursor = db.query(sql)) { check(cursor.moveToFirst(), sql); return cursor.getLong(0); }
    }

    private static void boundsAndRestart() {
        File file = new File(root, "bounded.db");
        MediaProviderRecordDatabase room = open(file.getName());
        SupportSQLiteDatabase db = room.getOpenHelper().getWritableDatabase();
        RecordStore store = new RecordStore(db, file);
        store.maintain(NOW);
        for (int i=0; i<6_000; i+=16) {
            List<MediaProviderRecord> batch = new ArrayList<>();
            for (int j=0;j<16;j++) batch.add(record(NOW+i+j,1,null,"/fixture/"+(i+j)));
            check(store.append(batch,NOW), "Write rejected unexpectedly");
            check(value(db,"SELECT COUNT(*) FROM UsageRecord")<=5_000,"Row cap violated");
        }
        check(value(db,"SELECT COUNT(*) FROM UsageRecord")==5_000,"Expected capped history");
        check(value(db,"PRAGMA max_page_count") * value(db,"PRAGMA page_size") <= RecordPolicy.MAX_DATABASE_BYTES,"File cap missing");
        String large = String.join("", Collections.nCopies(100_000,"😀\u0001"));
        MediaProviderRecord huge = new MediaProviderRecord(0,NOW,"probe.app",1,1,Collections.nCopies(1000,large),
            Collections.nCopies(1000,"image/png"),Collections.nCopies(1000,true),1,NOW,NOW,1,false,false,null);
        check(store.append(Collections.singletonList(huge),NOW),"Bounded huge record failed");
        check(value(db,"SELECT MAX(length(CAST(data AS BLOB))+length(CAST(mime_type AS BLOB))+length(CAST(intercepted AS BLOB))+length(CAST(package_name AS BLOB))) FROM UsageRecord")<=8192,"Detail cap violated");
        boolean rejected=false;
        try { db.execSQL("UPDATE UsageRecord SET data=? WHERE id=(SELECT MAX(id) FROM UsageRecord)",new Object[]{large}); }
        catch (android.database.SQLException expected) { rejected=true; }
        check(rejected,"Database trigger did not enforce byte bound");
        room.close();
        room=open(file.getName()); db=room.getOpenHelper().getWritableDatabase(); store=new RecordStore(db,file);
        check(value(db,"SELECT COUNT(*) FROM UsageRecord")<=5000,"Restart lost row bound");
        store.clear();
        for(int i=0;i<400;i++) store.maintain(NOW);
        check(value(db,"SELECT COUNT(*) FROM UsageRecord")==0,"Clear did not converge");
        store.append(Arrays.asList(record(NOW-RecordPolicy.RETENTION_MS-1,1,null,"old"), record(NOW-RecordPolicy.RETENTION_MS,1,null,"boundary")),NOW);
        check(value(db,"SELECT COUNT(*) FROM UsageRecord")==1,"Retention boundary wrong");
        store.maintain(NOW+1);
        check(value(db,"SELECT COUNT(*) FROM UsageRecord")==0,"Expired row remained");
        for(int i=0;i<1000;i++) store.append(Collections.singletonList(record(NOW,0,"one-query","sample")),NOW);
        check(value(db,"SELECT COUNT(*) FROM UsageRecord")==1,"Repeated queries not aggregated");
        check(value(db,"SELECT SUM(event_count) FROM UsageRecord")==1000,"Aggregation lost call counts");
        System.out.println("PASS row/byte/time bounds, SQL guard, restart, aggregation");
        room.close();
    }

    private static void seed(String name, int rows, int payloadSize) {
        File file=new File(root,name);
        SQLiteDatabase raw=SQLiteDatabase.openOrCreateDatabase(file,null);
        raw.execSQL("PRAGMA auto_vacuum=FULL");
        raw.execSQL("VACUUM"); // Empty synthetic fixture only; never used by production.
        raw.execSQL("CREATE TABLE MediaProviderRecord(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,time_millis INTEGER NOT NULL,package_name TEXT NOT NULL,`match` INTEGER NOT NULL,operation INTEGER NOT NULL,data TEXT NOT NULL,mime_type TEXT NOT NULL,intercepted TEXT NOT NULL)");
        raw.execSQL("CREATE INDEX index_MediaProviderRecord_time_millis_operation ON MediaProviderRecord(time_millis,operation)");
        raw.execSQL("CREATE INDEX index_MediaProviderRecord_package_name_operation ON MediaProviderRecord(package_name,operation)");
        String payload=String.join("",Collections.nCopies(payloadSize,"x"));
        raw.beginTransaction();
        try {
            for(int i=0;i<rows;i++) raw.execSQL("INSERT INTO MediaProviderRecord(time_millis,package_name,`match`,operation,data,mime_type,intercepted) VALUES(?,?,?,?,?,?,?)",
                new Object[]{NOW-i,"legacy.app",1,0,payload,"[]","0:0"});
            raw.setTransactionSuccessful();
        } finally {raw.endTransaction();}
        raw.setVersion(3); raw.close();
    }

    private static void legacyMigration() {
        seed("legacy.db", 6000, 16*1024);
        File file=new File(root,"legacy.db");
        long originalSize=file.length();
        check(originalSize>RecordPolicy.MAX_DATABASE_BYTES,"Fixture is not oversized");
        MediaProviderRecordDatabase room=open(file.getName());
        SupportSQLiteDatabase db=room.getOpenHelper().getWritableDatabase();
        check(db.getVersion()==4,"Migration version wrong");
        check(value(db,"SELECT COUNT(*) FROM MediaProviderRecord")==6000,"Migration bulk-deleted legacy rows");
        RecordStore store=new RecordStore(db,file);
        check(value(db,"PRAGMA auto_vacuum")==2,"FULL auto-vacuum was not changed to incremental");
        long previous=6000; long maximumWal=0;
        for(int turn=0;turn<1600;turn++) {
            store.maintain(NOW);
            long count=value(db,"SELECT COUNT(*) FROM MediaProviderRecord");
            check(previous-count<=16,"Unbounded legacy deletion batch"); previous=count;
            maximumWal=Math.max(maximumWal,new File(file+"-wal").length());
            if(turn==40) {
                long imported=value(db,"SELECT COUNT(*) FROM UsageRecord");
                room.close(); room=open(file.getName()); db=room.getOpenHelper().getWritableDatabase(); store=new RecordStore(db,file);
                check(value(db,"SELECT COUNT(*) FROM UsageRecord")==imported,"Restart changed imported history");
            }
        }
        check(previous==0,"Legacy deletion did not converge");
        check(value(db,"SELECT COUNT(*) FROM UsageRecord")==2000,"Legacy summary bound wrong or import duplicated after restart");
        check(value(db,"SELECT COUNT(*) FROM UsageRecord WHERE sample_kind=2 AND data='[]' AND mime_type='[]' AND intercepted='0:0'")==2000,"Legacy detail blobs were imported or interception invented");
        check(file.length()<=RecordPolicy.MAX_DATABASE_BYTES,"Legacy physical space did not converge");
        check(maximumWal<8L*1024*1024,"Excessive WAL during legacy cleanup");
        check(value(db,"PRAGMA max_page_count")*value(db,"PRAGMA page_size")<=RecordPolicy.MAX_DATABASE_BYTES,"Ratchet did not converge");
        System.out.println("PASS legacy migration/restart/batches: old="+originalSize+" new="+file.length()+" maxWal="+maximumWal);
        room.close();
    }

    private static void clearDuringMigration() {
        seed("clear.db", 100, 4096);
        MediaProviderRecordDatabase room=open("clear.db");
        SupportSQLiteDatabase db=room.getOpenHelper().getWritableDatabase();
        RecordStore store=new RecordStore(db,new File(root,"clear.db"));
        store.maintain(NOW); store.clear();
        check(value(db,"SELECT COUNT(*) FROM UsageRecord WHERE id>(SELECT clear_before_id FROM RecordMaintenance WHERE id=1)")==0,"Clear not immediately visible");
        room.close(); room=open("clear.db"); db=room.getOpenHelper().getWritableDatabase(); store=new RecordStore(db,new File(root,"clear.db"));
        for(int i=0;i<50;i++)store.maintain(NOW);
        check(value(db,"SELECT COUNT(*) FROM UsageRecord")==0,"Legacy history resurrected after clear/restart");
        System.out.println("PASS clear during migration and restart"); room.close();
    }

    private static void readerBackpressure() {
        MediaProviderRecordDatabase room=open("reader.db");
        File file=new File(root,"reader.db");
        SupportSQLiteDatabase db=room.getOpenHelper().getWritableDatabase();
        RecordStore store=new RecordStore(db,file);store.maintain(NOW);
        store.append(Collections.singletonList(record(NOW,1,null,"seed")),NOW);
        SQLiteDatabase reader=SQLiteDatabase.openDatabase(file.getPath(),null,SQLiteDatabase.OPEN_READONLY);
        reader.execSQL("BEGIN");
        try(Cursor ignored=reader.rawQuery("SELECT * FROM UsageRecord",null)) {
            check(ignored.moveToFirst(),"Read snapshot missing");
            boolean paused=false;
            for(int i=0;i<10_000;i++) {
                if(!store.append(Collections.singletonList(record(NOW+i,1,null,"/"+String.join("",Collections.nCopies(300,"a")))),NOW)) {paused=true;break;}
            }
            check(paused,"Pinned reader did not apply WAL backpressure");
            check(new File(file+"-wal").length()<8L*1024*1024,"WAL grew beyond safety envelope");
        } finally {reader.execSQL("ROLLBACK");reader.close();}
        check(store.canWrite(),"Writer did not resume after reader released snapshot");
        System.out.println("PASS pinned-reader WAL bound and resume"); room.close();
    }

    private static void writerBarriers() throws Exception {
        MediaProviderRecordDatabase room=open("writer.db");
        RecordWriter writer=new RecordWriter(room,new File(root,"writer.db"),true,()->Unit.INSTANCE);
        await(()->writer.getEnabled(),"Writer did not become ready");
        long now=System.currentTimeMillis();
        List<Thread> threads=new ArrayList<>();
        for(int n=0;n<4;n++)threads.add(new Thread(()->{
            for(int i=0;i<250;i++) {
                RecordBuffer.Ticket ticket=writer.query("probe.app",1,true,now);
                if(ticket!=null)writer.offer(record(now,0,null,"sample"),ticket);
            }
        }));
        for(Thread thread:threads)thread.start();for(Thread thread:threads)thread.join();
        SupportSQLiteDatabase db=room.getOpenHelper().getWritableDatabase();
        await(()->value(db,"SELECT COALESCE(SUM(event_count),0) FROM UsageRecord")==1000,"Concurrent writes lost counts");
        writer.setEnabled(false);
        long count=value(db,"SELECT COALESCE(SUM(event_count),0) FROM UsageRecord");
        for(int i=0;i<100;i++)writer.offer(record(now,1,null,"disabled"),null);
        Thread.sleep(250);
        check(count==value(db,"SELECT COALESCE(SUM(event_count),0) FROM UsageRecord"),"Records persisted while disabled");
        writer.setEnabled(true);
        RecordBuffer.Ticket stale=writer.query("probe.app",1,true,now);
        writer.clear();writer.offer(record(now,0,null,"stale"),stale);
        Thread.sleep(250);
        check(value(db,"SELECT COUNT(*) FROM UsageRecord WHERE id>(SELECT clear_before_id FROM RecordMaintenance WHERE id=1)")==0,"In-flight sample reappeared after clear");
        writer.close();await(()->!room.isOpen(),"Writer did not close asynchronously");
        MediaProviderRecordDatabase reopened=open("writer.db");
        RecordWriter disabled=new RecordWriter(reopened,new File(root,"writer.db"),false,()->Unit.INSTANCE);
        await(()->disabled.getReady(),"Disabled writer did not run maintenance after restart");
        check(!disabled.getEnabled(),"Restart enabled recording unexpectedly");
        disabled.close();await(()->!reopened.isOpen(),"Disabled writer did not close");
        System.out.println("PASS concurrent writer, disable, clear barrier and disabled restart");
    }

    private interface Condition { boolean get(); }
    private static void await(Condition condition,String message)throws Exception {
        long end=System.currentTimeMillis()+10_000;
        while(!condition.get()&&System.currentTimeMillis()<end)Thread.sleep(20);
        check(condition.get(),message);
    }
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
