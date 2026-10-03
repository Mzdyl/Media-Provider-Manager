package me.gm.cleaner.plugin.recording

import me.gm.cleaner.plugin.dao.MediaProviderOperation.Companion.OP_QUERY
import me.gm.cleaner.plugin.dao.MediaProviderRecord
import java.util.UUID

/** A bounded, generation-aware handoff. Clear/disable invalidate samples still being collected. */
class RecordBuffer(initiallyEnabled: Boolean, private val session: String = UUID.randomUUID().toString()) {
    data class Ticket(val generation: Long, val time: Long, val key: String?, val capture: Boolean)
    private var generation = 0L
    @Volatile var enabled = initiallyEnabled
        private set
    private val pending = LinkedHashMap<String, MediaProviderRecord>()
    private val sampled = LinkedHashMap<String, Boolean>(128, 0.75f, true)
    private var sequence = 0L
    @Volatile var dropped = 0L
        private set

    @Synchronized fun query(packageName: String, table: Int, filtered: Boolean, now: Long): Ticket? {
        if (!enabled || packageName.length > 256) return null
        val key = "$session:$generation:" + RecordPolicy.queryKey(packageName, table, filtered, now)
        val capture = sampled.put(key, true) == null
        while (sampled.size > 128) sampled.remove(sampled.keys.first())
        return Ticket(generation, now, key, capture)
    }

    @Synchronized fun offer(input: MediaProviderRecord, ticket: Ticket? = null): Boolean {
        if (!enabled || (ticket != null && ticket.generation != generation)) return false
        val record = RecordPolicy.bound(input) ?: return false
        val key = if (record.operation == OP_QUERY) ticket?.key else null
        val tagged = record.copy(aggregateKey = key)
        if (key != null && pending.containsKey(key)) {
            pending[key] = RecordPolicy.merge(pending.getValue(key), tagged)
            return true
        }
        if (pending.size >= RecordPolicy.MAX_PENDING) { dropped++; return false }
        pending[key ?: "event:${sequence++}"] = tagged
        return true
    }

    @Synchronized fun drain(): List<MediaProviderRecord> {
        val batch = pending.entries.take(RecordPolicy.WRITE_BATCH).map { it.key to it.value }
        batch.forEach { pending.remove(it.first) }
        return batch.map { it.second }
    }

    @Synchronized fun noteDropped(count: Long) { dropped += minOf(count, Long.MAX_VALUE - dropped) }

    @Synchronized fun hasPending() = pending.isNotEmpty()
    @Synchronized fun reset() { generation++; pending.clear(); sampled.clear() }
    @Synchronized fun setEnabled(value: Boolean) { enabled = value; if (!value) reset() }
}
