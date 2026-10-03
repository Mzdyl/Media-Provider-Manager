package me.gm.cleaner.plugin.recording

import me.gm.cleaner.plugin.dao.ListConverter
import me.gm.cleaner.plugin.dao.MediaProviderOperation.Companion.OP_QUERY
import me.gm.cleaner.plugin.dao.MediaProviderRecord
import java.security.MessageDigest

/** Limits apply to diagnostics only; recording must never change a media operation's result. */
object RecordPolicy {
    const val MAX_RECORDS = 5_000
    const val MAX_DETAIL_BYTES = 8_192
    const val MAX_DETAILS = 16
    const val MAX_PATH_BYTES = 384
    const val MAX_MIME_BYTES = 96
    const val RETENTION_MS = 7L * 24 * 60 * 60 * 1000
    const val QUERY_WINDOW_MS = 60_000L
    const val MAX_PENDING = 256
    const val WRITE_BATCH = 16
    const val MAINTENANCE_BATCH = 16
    const val VACUUM_PAGES = 32
    const val MAX_DATABASE_BYTES = 64L * 1024 * 1024
    const val WAL_PAUSE_BYTES = 4L * 1024 * 1024
    const val MIN_FREE_BYTES = 16L * 1024 * 1024
    const val LEGACY_SUMMARIES = 2_000
    const val SAMPLE_NONE = 0
    const val SAMPLE_DETAILS = 1
    const val SAMPLE_LEGACY = 2

    fun queryKey(packageName: String, table: Int, filtered: Boolean, time: Long): String {
        val value = "$packageName\u0000$table\u0000$filtered\u0000${Math.floorDiv(time, QUERY_WINDOW_MS)}"
        return MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            .let { digest -> buildString(64) {
                val hex = "0123456789abcdef"
                digest.forEach { byte -> val n = byte.toInt() and 255; append(hex[n ushr 4]); append(hex[n and 15]) }
            } }
    }

    /** Does not split UTF-8 sequences. A shortened display path is explicitly marked as truncated. */
    fun clip(value: String, maxBytes: Int): String {
        if (value.length <= maxBytes / 4 && value.toByteArray(Charsets.UTF_8).size <= maxBytes) return value
        var end = 0
        var bytes = 0
        while (end < value.length) {
            val cp = value.codePointAt(end)
            val size = when { cp <= 0x7f -> 1; cp <= 0x7ff -> 2; cp <= 0xffff -> 3; else -> 4 }
            if (bytes + size > maxBytes) break
            bytes += size
            end += Character.charCount(cp)
        }
        return if (end == value.length) value else value.substring(0, end)
    }

    fun detailBytes(record: MediaProviderRecord): Int =
        listOf(ListConverter.listToString(record.data), ListConverter.listToString(record.mimeType),
            ListConverter.booleanListToString(record.intercepted), record.packageName)
            .sumOf { it.toByteArray(Charsets.UTF_8).size }

    fun bound(record: MediaProviderRecord): MediaProviderRecord? {
        if (record.packageName.isBlank() || record.packageName.length > 256 ||
            record.packageName.toByteArray(Charsets.UTF_8).size > 256 || record.operation !in 0..2 ||
            record.data.size != record.mimeType.size || record.data.size != record.intercepted.size) return null
        val paths = ArrayList<String>()
        val types = ArrayList<String>()
        val blocked = ArrayList<Boolean>()
        var truncated = record.detailsTruncated || record.data.size > MAX_DETAILS
        for (i in 0 until minOf(record.data.size, MAX_DETAILS)) {
            val path = clip(record.data[i], MAX_PATH_BYTES)
            val type = clip(record.mimeType[i], MAX_MIME_BYTES)
            paths += path; types += type; blocked += record.intercepted[i]
            val candidate = record.copy(data = paths, mimeType = types, intercepted = blocked)
            if (detailBytes(candidate) > MAX_DETAIL_BYTES) {
                paths.removeAt(paths.lastIndex); types.removeAt(types.lastIndex); blocked.removeAt(blocked.lastIndex)
                truncated = true
                break
            }
            truncated = truncated || path != record.data[i] || type != record.mimeType[i]
        }
        return record.copy(data = paths.toList(), mimeType = types.toList(), intercepted = blocked.toList(),
            detailsTruncated = truncated, eventCount = record.eventCount.coerceAtLeast(1),
            lastTimeMillis = maxOf(record.timeMillis, record.lastTimeMillis))
    }

    /** Preserve caller offsets while limiting only the auxiliary diagnostic query. */
    fun capSqlLimit(limit: String?, maximum: Int): String? {
        if (limit.isNullOrBlank()) return maximum.toString()
        val match = Regex("""^\s*(-?\d+)\s*(?:(?:,\s*(\d+))|(?:OFFSET\s+(\d+)))?\s*$""", RegexOption.IGNORE_CASE).matchEntire(limit) ?: return null
        val first = match.groupValues[1].toLongOrNull() ?: return null
        val comma = match.groupValues[2].takeIf { it.isNotEmpty() }?.toLongOrNull()
        val offset = if (comma != null) first else match.groupValues[3].ifEmpty { "0" }.toLongOrNull() ?: return null
        if (offset < 0) return null
        val count = comma ?: first
        val bounded = if (count < 0) maximum.toLong() else minOf(count, maximum.toLong())
        return if (offset == 0L) bounded.toString() else "$bounded OFFSET $offset"
    }

    fun merge(first: MediaProviderRecord, next: MediaProviderRecord): MediaProviderRecord {
        require(first.operation == OP_QUERY && first.aggregateKey != null && first.aggregateKey == next.aggregateKey)
        val sample = if (first.sampleKind == SAMPLE_NONE && next.sampleKind != SAMPLE_NONE) next else first
        return sample.copy(id = first.id, timeMillis = minOf(first.timeMillis, next.timeMillis),
            lastTimeMillis = maxOf(first.lastTimeMillis, next.lastTimeMillis),
            eventCount = (first.eventCount.toLong() + next.eventCount).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
    }
}
