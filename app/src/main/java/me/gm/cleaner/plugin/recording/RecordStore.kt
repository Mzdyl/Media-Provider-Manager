package me.gm.cleaner.plugin.recording

import androidx.sqlite.db.SupportSQLiteDatabase
import me.gm.cleaner.plugin.dao.ListConverter
import me.gm.cleaner.plugin.dao.MediaProviderRecord
import java.io.File

/** Called only by the single record writer, never by a MediaProvider hook or its main thread. */
class RecordStore(private val db: SupportSQLiteDatabase, private val file: File) {
    data class Status(val state: String, val rows: Long, val bytes: Long, val walBytes: Long, val legacy: Boolean)
    private val wal = File(file.path + "-wal")
    private val pageSize = RecordSchema.longValue(db, "PRAGMA page_size")
    private val reservePages = 128L
    private var spacePressure = false
    private val legacyTables = listOf("MediaProviderRecord", "MediaProviderQueryRecord", "MediaProviderInsertRecord", "MediaProviderDeleteRecord")
        .filter { RecordSchema.longValue(db, "SELECT EXISTS(SELECT 1 FROM sqlite_master WHERE type='table' AND name=?)", arrayOf<Any?>(it)) != 0L }

    init {
        RecordSchema.onOpen(db)
        ratchetFileLimit()
    }

    fun canWrite(): Boolean {
        if (file.parentFile!!.usableSpace < RecordPolicy.MIN_FREE_BYTES) return false
        if (wal.length() >= RecordPolicy.WAL_PAUSE_BYTES) checkpoint()
        return wal.length() < RecordPolicy.WAL_PAUSE_BYTES
    }

    private fun checkpoint() {
        db.query("PRAGMA wal_checkpoint(PASSIVE)").use { while (it.moveToNext()) { } }
        // A completed checkpoint may leave an allocated WAL file. Do not TRUNCATE while readers are active.
        db.query("PRAGMA wal_checkpoint(TRUNCATE)").use { while (it.moveToNext()) { } }
    }

    private fun ratchetFileLimit() {
        val pages = RecordSchema.longValue(db, "PRAGMA page_count")
        val cap = maxOf(pages, (RecordPolicy.MAX_DATABASE_BYTES + pageSize - 1) / pageSize)
        db.query("PRAGMA max_page_count=$cap").use { while (it.moveToNext()) { } }
    }

    fun append(records: List<MediaProviderRecord>, now: Long): Boolean {
        if (!canWrite()) return false
        db.beginTransactionNonExclusive()
        try {
            for (input in records.take(RecordPolicy.WRITE_BATCH)) {
                val record = RecordPolicy.bound(input) ?: continue
                if (record.timeMillis < now - RecordPolicy.RETENTION_MS) continue
                put(record)
            }
            db.setTransactionSuccessful()
        } catch (full: android.database.sqlite.SQLiteFullException) {
            spacePressure = true
            throw full
        } finally { db.endTransaction() }
        return true
    }

    private fun put(record: MediaProviderRecord) {
        val key = record.aggregateKey
        if (key != null) {
            val existing = db.query("SELECT * FROM UsageRecord WHERE aggregate_key=? AND id>(SELECT clear_before_id FROM RecordMaintenance WHERE id=1)", arrayOf<Any?>(key)).use {
                me.gm.cleaner.plugin.dao.MediaProviderRecord.convert(it).firstOrNull()
            }
            if (existing != null) {
                val merged = RecordPolicy.merge(existing.copy(aggregateKey = key), record)
                db.execSQL("""UPDATE UsageRecord SET time_millis=?,last_time_millis=?,event_count=?,data=?,mime_type=?,intercepted=?,
                    sample_kind=?,sample_time_millis=?,details_truncated=? WHERE id=?""", arrayOf<Any?>(
                    merged.timeMillis, merged.lastTimeMillis, merged.eventCount, ListConverter.listToString(merged.data),
                    ListConverter.listToString(merged.mimeType), ListConverter.booleanListToString(merged.intercepted),
                    merged.sampleKind, merged.sampleTimeMillis, if (merged.detailsTruncated) 1 else 0, existing.id))
                return
            }
        }
        db.execSQL("""INSERT INTO UsageRecord(time_millis,package_name,`match`,operation,data,mime_type,intercepted,
            event_count,last_time_millis,sample_time_millis,sample_kind,details_truncated,filter_applied,aggregate_key)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)""", arrayOf<Any?>(record.timeMillis, record.packageName, record.match, record.operation,
            ListConverter.listToString(record.data), ListConverter.listToString(record.mimeType),
            ListConverter.booleanListToString(record.intercepted), record.eventCount, record.lastTimeMillis,
            record.sampleTimeMillis, record.sampleKind, if (record.detailsTruncated) 1 else 0,
            if (record.filterApplied) 1 else 0, key))
    }

    /** Hide history atomically; physical removal is bounded background work, not Room.clearAllTables/VACUUM. */
    fun clear() {
        db.execSQL("""UPDATE RecordMaintenance SET clear_before_id=COALESCE((SELECT MAX(id) FROM UsageRecord),0),
            legacy_import_done=1,legacy_cursor=0 WHERE id=1""")
    }

    /** Each pass does at most 16 legacy deletions, 16 metadata imports and 16 live deletions. */
    fun maintain(now: Long): Status {
        if (!canWrite()) return status(if (file.parentFile!!.usableSpace < RecordPolicy.MIN_FREE_BYTES) "low_space" else "reader_busy")
        val importDone = RecordSchema.longValue(db, "SELECT legacy_import_done FROM RecordMaintenance WHERE id=1") != 0L
        val floor = RecordSchema.longValue(db, "SELECT legacy_floor FROM RecordMaintenance WHERE id=1")
        var remaining = RecordPolicy.MAINTENANCE_BATCH
        for (table in legacyTables) {
            if (remaining == 0) break
            val restriction = if (table == "MediaProviderRecord" && !importDone) "WHERE id<$floor" else ""
            val ids = db.query("SELECT rowid FROM $table $restriction ORDER BY rowid LIMIT $remaining").use {
                buildList { while (it.moveToNext()) add(it.getLong(0)) }
            }
            for (id in ids) {
                if (!canWrite()) return status("reader_busy")
                db.execSQL("DELETE FROM $table WHERE rowid=?", arrayOf<Any?>(id))
                remaining--
            }
        }
        if (!importDone && canWrite()) importLegacy(now, floor)
        if (spacePressure && canWrite()) {
            db.execSQL("DELETE FROM UsageRecord WHERE id IN (SELECT id FROM UsageRecord ORDER BY time_millis,id LIMIT ${RecordPolicy.WRITE_BATCH})")
            spacePressure = false
        }
        if (canWrite()) db.execSQL("""DELETE FROM UsageRecord WHERE id IN (SELECT id FROM UsageRecord
            WHERE time_millis<? OR id<=(SELECT clear_before_id FROM RecordMaintenance WHERE id=1)
            ORDER BY time_millis,id LIMIT ${RecordPolicy.MAINTENANCE_BATCH})""", arrayOf<Any?>(now - RecordPolicy.RETENTION_MS))
        if (canWrite() && RecordSchema.longValue(db, "PRAGMA auto_vacuum") == 2L) {
            val free = RecordSchema.longValue(db, "PRAGMA freelist_count")
            val reclaim = minOf(RecordPolicy.VACUUM_PAGES.toLong(), (free - reservePages).coerceAtLeast(0))
            if (reclaim > 0) db.query("PRAGMA incremental_vacuum($reclaim)").use { while (it.moveToNext()) { } }
        }
        ratchetFileLimit()
        if (wal.length() >= RecordPolicy.WAL_PAUSE_BYTES / 2) checkpoint()
        return status("ready")
    }

    private fun importLegacy(now: Long, floor: Long) {
        val cursor = RecordSchema.longValue(db, "SELECT legacy_cursor FROM RecordMaintenance WHERE id=1")
        // Never materialize the old data/mime_type/intercepted blobs, even for migration.
        val summaries = db.query("""SELECT id,time_millis,substr(package_name,1,256),`match`,operation
            FROM MediaProviderRecord WHERE id>=? AND id<? ORDER BY id DESC LIMIT ${RecordPolicy.MAINTENANCE_BATCH}""", arrayOf<Any?>(floor, cursor)).use { c ->
            buildList {
                while (c.moveToNext()) add(c.getLong(0) to MediaProviderRecord(0, c.getLong(1), c.getString(2), c.getInt(3), c.getInt(4),
                    emptyList(), emptyList(), emptyList(), sampleKind = RecordPolicy.SAMPLE_LEGACY, detailsTruncated = true))
            }
        }
        db.beginTransactionNonExclusive()
        try {
            for ((_, summary) in summaries) {
                if (summary.timeMillis >= now - RecordPolicy.RETENTION_MS) RecordPolicy.bound(summary)?.let(::put)
            }
            if (summaries.isEmpty()) db.execSQL("UPDATE RecordMaintenance SET legacy_import_done=1 WHERE id=1")
            else db.execSQL("UPDATE RecordMaintenance SET legacy_cursor=? WHERE id=1", arrayOf<Any?>(summaries.last().first))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun status(state: String = "ready"): Status {
        val legacy = legacyTables.any { RecordSchema.longValue(db, "SELECT EXISTS(SELECT 1 FROM $it LIMIT 1)") != 0L }
        val free = RecordSchema.longValue(db, "PRAGMA freelist_count") > reservePages
        val incremental = RecordSchema.longValue(db, "PRAGMA auto_vacuum") == 2L
        val pending = free && incremental
        return Status(if (state == "ready" && (legacy || pending)) "maintenance"
            else if (state == "ready" && free && !incremental) "reusable_space" else state,
            RecordSchema.longValue(db, "SELECT COUNT(*) FROM UsageRecord WHERE id>(SELECT clear_before_id FROM RecordMaintenance WHERE id=1)"),
            file.length(), wal.length(), legacy)
    }
}
