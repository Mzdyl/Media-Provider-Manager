package me.gm.cleaner.plugin;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Environment;
import android.os.IBinder;
import android.os.OperationCanceledException;
import android.os.Parcel;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** Platform-only test runner: no dependency on classes that the target app's R8 can remove. */
public final class Api102Instrumentation extends Instrumentation {
    private static final int ROOT = 1;
    private static final int RULES = 2;
    private Context context;
    private ContentResolver resolver;
    private IBinder service;
    private final Uri media = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
    private final List<Uri> fixtures = new ArrayList<>();
    private final String suffix = UUID.randomUUID().toString();
    private final String hidden = "Pictures/MPM_API102_" + suffix + "_hidden/";
    private final String visible = "Pictures/MPM_API102_" + suffix + "_visible/";

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }

    @Override public void onStart() {
        super.onStart();
        Bundle result = new Bundle();
        try {
            context = getTargetContext();
            resolver = context.getContentResolver();
            runRegression();
            result.putBoolean("api102_passed", true);
            result.putString("stream", "\nOK (API 102 provider regression)\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable failure) {
            result.putBoolean("api102_passed", false);
            result.putString("stream", Log.getStackTraceString(failure));
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private void stage(String value) {
        Bundle status = new Bundle();
        status.putString("api102_stage", value);
        sendStatus(1, status);
    }

    private interface Writer { void write(Parcel data); }
    private interface Reader<T> { T read(Parcel reply); }

    // Stable AIDL transaction IDs keep the test independent of the app's obfuscated classes.
    private <T> T call(int id, Writer writer, Reader<T> reader) throws Exception {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken("me.gm.cleaner.plugin.IManagerService");
            writer.write(data);
            check(service.transact(IBinder.FIRST_CALL_TRANSACTION + id, data, reply, 0), "Unsupported transaction " + id);
            reply.readException();
            return reader.read(reply);
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    private String readSettings(int who) throws Exception {
        String result = call(20, p -> p.writeInt(who), Parcel::readString);
        return result == null ? "" : result;
    }

    private void writeSettings(int who, String json) throws Exception {
        call(21, p -> { p.writeInt(who); p.writeString(json); }, p -> null);
    }

    private void runRegression() throws Exception {
        stage("binder");
        try (Cursor cursor = resolver.query(MediaStore.Images.Media.INTERNAL_CONTENT_URI, null, null, null, null)) {
            check(cursor != null, "No client cursor");
            service = cursor.getExtras().getBinder("me.gm.cleaner.plugin.cursor.extra.BINDER");
        }
        check(service != null, "No module Binder; enable API 102 module and restart its scope");
        check(call(1, p -> {}, Parcel::readInt) == 102, "Injected API must be 102");
        long installed = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).getLongVersionCode();
        check(call(0, p -> {}, Parcel::readInt) == installed, "Injected module version differs from installed version");
        String originalRoot = readSettings(ROOT);
        String originalRules = readSettings(RULES);
        JSONObject root = new JSONObject(originalRoot.isEmpty() ? "{}" : originalRoot);
        boolean recordingChanged = !root.optBoolean("usage_record", true);
        JSONArray baseline = new JSONArray(originalRules.isEmpty() ? "[]" : originalRules);
        for (int i = 0; i < baseline.length(); i++) {
            JSONObject template = baseline.getJSONObject(i);
            JSONArray packages = template.optJSONArray("apply_to_app");
            if (packages == null) continue;
            JSONArray kept = new JSONArray();
            for (int j = 0; j < packages.length(); j++) {
                if (!context.getPackageName().equals(packages.getString(j))) kept.put(packages.getString(j));
            }
            template.put("apply_to_app", kept);
        }
        try {
            if (recordingChanged) writeSettings(ROOT, root.put("usage_record", true).toString());
            writeSettings(RULES, baseline.toString());
            stage("fixtures");
            List<String> ids = new ArrayList<>();
            List<Long> visibleIds = new ArrayList<>();
            for (int i = 0; i < 4; i++) ids.add(createImage(hidden, "hidden_" + i + ".png").getLastPathSegment());
            for (int i = 0; i < 3; i++) {
                Uri uri = createImage(visible, "visible_" + i + ".png");
                ids.add(uri.getLastPathSegment());
                visibleIds.add(Long.parseLong(uri.getLastPathSegment()));
            }
            check(queryIds(ids, null, 0).size() == 7, "Baseline query must find all fixtures");
            stage("query-pagination-projection");
            JSONArray queryRules = new JSONArray(baseline.toString()).put(rule("query", hidden));
            writeSettings(RULES, queryRules.toString());
            check(queryIds(ids, null, 0).equals(visibleIds), "Hidden directory must be filtered");
            check(queryIds(ids, 2, 0).equals(visibleIds.subList(0, 2)), "Filtering must precede LIMIT");
            check(queryIds(ids, 2, 2).equals(visibleIds.subList(2, 3)), "Filtering must precede OFFSET");
            stage("cancellation");
            CancellationSignal signal = new CancellationSignal();
            signal.cancel();
            boolean cancelled = false;
            try (Cursor ignored = resolver.query(media, new String[]{"_id"}, queryArgs(ids, null, 0), signal)) {
                // A cancelled operation must not succeed through an unfiltered retry.
            } catch (OperationCanceledException expected) {
                cancelled = true;
            }
            check(cancelled, "Query cancellation must propagate");
            stage("insert");
            writeSettings(RULES, new JSONArray(queryRules.toString()).put(rule("insert", hidden)).toString());
            Uri rejected = resolver.insert(media, values(hidden, "rejected.png"));
            if (rejected != null) fixtures.add(rejected);
            check(rejected == null, "Denied insert must not create a media row");
            createImage(visible, "permitted.png");
            stage("delete-and-records");
            int beforeDelete = usageCount(2);
            Uri lastVisible = Uri.withAppendedPath(media, Long.toString(visibleIds.get(2)));
            check(resolver.delete(lastVisible, null, null) == 1, "Delete must still succeed");
            long deadline = SystemClock.elapsedRealtime() + 10_000;
            while (usageCount(2) <= beforeDelete && SystemClock.elapsedRealtime() < deadline) Thread.sleep(100);
            check(usageCount(2) > beforeDelete, "Delete must be recorded");
            check(usageCount(0) > 0, "Query must be recorded");
            check(usageCount(1) > 0, "Insert must be recorded");
        } finally {
            stage("restore-and-cleanup");
            try {
                writeSettings(RULES, originalRules);
                for (Uri uri : fixtures) resolver.delete(uri, null, null);
                // Delete only empty directories, never recursively remove media.
                new File(Environment.getExternalStorageDirectory(), hidden).delete();
                new File(Environment.getExternalStorageDirectory(), visible).delete();
            } finally {
                if (recordingChanged) writeSettings(ROOT, originalRoot);
            }
        }
        check(originalRoot.equals(readSettings(ROOT)), "Root settings must be restored exactly");
        check(originalRules.equals(readSettings(RULES)), "Rules must be restored exactly");
        stage("complete");
    }

    private JSONObject rule(String operation, String directory) throws Exception {
        return new JSONObject().put("template_name", "API102 " + operation + " " + suffix)
            .put("hook_operation", new JSONArray().put(operation))
            .put("apply_to_app", new JSONArray().put(context.getPackageName()))
            .put("permitted_media_types", JSONObject.NULL)
            .put("filter_path", new JSONArray().put(Environment.getExternalStorageDirectory().getPath() + "/" + directory));
    }

    private ContentValues values(String directory, String name) {
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "image/png");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, directory);
        return values;
    }

    private Uri createImage(String directory, String name) throws Exception {
        Uri uri = resolver.insert(media, values(directory, name));
        check(uri != null, "Permitted fixture insert must succeed");
        fixtures.add(uri);
        try (OutputStream output = resolver.openOutputStream(uri)) {
            check(output != null, "Fixture output stream is unavailable");
            output.write(Base64.decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a7WQAAAAASUVORK5CYII=", Base64.DEFAULT));
        }
        return uri;
    }

    private Bundle queryArgs(List<String> ids, Integer limit, int offset) {
        Bundle args = new Bundle();
        String[] placeholders = new String[ids.size()];
        Arrays.fill(placeholders, "?");
        args.putString(ContentResolver.QUERY_ARG_SQL_SELECTION, "_id IN (" + String.join(",", placeholders) + ")");
        args.putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, ids.toArray(new String[0]));
        args.putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, new String[]{"_id"});
        args.putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_ASCENDING);
        if (limit != null) {
            args.putInt(ContentResolver.QUERY_ARG_LIMIT, limit);
            args.putInt(ContentResolver.QUERY_ARG_OFFSET, offset);
        }
        return args;
    }

    private List<Long> queryIds(List<String> ids, Integer limit, int offset) {
        try (Cursor cursor = resolver.query(media, new String[]{"_id", "_display_name"}, queryArgs(ids, limit, offset), null)) {
            check(cursor != null, "Query must return a cursor");
            check(Arrays.equals(new String[]{"_id", "_display_name"}, cursor.getColumnNames()), "Projection must be preserved");
            List<Long> result = new ArrayList<>();
            while (cursor.moveToNext()) result.add(cursor.getLong(0));
            return result;
        }
    }

    private int usageCount(int operation) throws Exception {
        return call(31, p -> { p.writeInt(operation); p.writeStringList(Arrays.asList(context.getPackageName())); }, Parcel::readInt);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
