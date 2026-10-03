package me.gm.cleaner.plugin.recording

import androidx.sqlite.db.SupportSQLiteDatabase

object RecordSchema {
    fun createTables(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS UsageRecord (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            time_millis INTEGER NOT NULL, package_name TEXT NOT NULL, `match` INTEGER NOT NULL,
            operation INTEGER NOT NULL, data TEXT NOT NULL, mime_type TEXT NOT NULL, intercepted TEXT NOT NULL,
            event_count INTEGER NOT NULL DEFAULT 1, last_time_millis INTEGER NOT NULL DEFAULT 0,
            sample_time_millis INTEGER NOT NULL DEFAULT 0, sample_kind INTEGER NOT NULL DEFAULT 1,
            details_truncated INTEGER NOT NULL DEFAULT 0, filter_applied INTEGER NOT NULL DEFAULT 0,
            aggregate_key TEXT)""")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_UsageRecord_time_millis ON UsageRecord(time_millis)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_UsageRecord_package_name_operation ON UsageRecord(package_name, operation)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_UsageRecord_aggregate_key ON UsageRecord(aggregate_key)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS RecordMaintenance (
            id INTEGER NOT NULL PRIMARY KEY, clear_before_id INTEGER NOT NULL,
            legacy_cursor INTEGER NOT NULL, legacy_floor INTEGER NOT NULL, legacy_import_done INTEGER NOT NULL)""")
    }

    /** Constant-size catalog changes only. No scan or rewrite of the legacy detail columns. */
    fun onOpen(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS MediaProviderRecord (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, time_millis INTEGER NOT NULL,
            package_name TEXT NOT NULL, `match` INTEGER NOT NULL, operation INTEGER NOT NULL,
            data TEXT NOT NULL, mime_type TEXT NOT NULL, intercepted TEXT NOT NULL)""")
        val mode = longValue(db, "PRAGMA auto_vacuum")
        if (mode == 1L) db.execSQL("PRAGMA auto_vacuum=INCREMENTAL")
        db.execSQL("""INSERT OR IGNORE INTO RecordMaintenance
            SELECT 1, 0, COALESCE((SELECT MAX(id) FROM MediaProviderRecord),0)+1,
            COALESCE((SELECT id FROM MediaProviderRecord ORDER BY id DESC LIMIT 1 OFFSET ${RecordPolicy.LEGACY_SUMMARIES - 1}),0), 0""")
        for (event in listOf("INSERT", "UPDATE")) {
            db.execSQL("""CREATE TRIGGER IF NOT EXISTS usage_record_size_${event.lowercase()}
                BEFORE $event ON UsageRecord WHEN
                length(CAST(NEW.data AS BLOB)) + length(CAST(NEW.mime_type AS BLOB)) +
                length(CAST(NEW.intercepted AS BLOB)) + length(CAST(NEW.package_name AS BLOB)) > ${RecordPolicy.MAX_DETAIL_BYTES}
                OR length(CAST(NEW.package_name AS BLOB)) > 256 OR NEW.event_count < 1
                BEGIN SELECT RAISE(ABORT, 'usage record exceeds diagnostic limits'); END""")
        }
        db.execSQL("""CREATE TRIGGER IF NOT EXISTS usage_record_count AFTER INSERT ON UsageRecord
            WHEN (SELECT COUNT(*) FROM UsageRecord) > ${RecordPolicy.MAX_RECORDS}
            BEGIN DELETE FROM UsageRecord WHERE id IN (SELECT id FROM UsageRecord
            ORDER BY time_millis, id LIMIT (SELECT COUNT(*)-${RecordPolicy.MAX_RECORDS} FROM UsageRecord)); END""")
        db.query("PRAGMA busy_timeout=0").use { while (it.moveToNext()) { } }
        db.query("PRAGMA wal_autocheckpoint=128").use { while (it.moveToNext()) { /* consume pragma */ } }
    }

    fun longValue(db: SupportSQLiteDatabase, sql: String, args: Array<out Any?> = emptyArray()): Long =
        db.query(sql, args).use { if (it.moveToFirst()) it.getLong(0) else 0 }
}
