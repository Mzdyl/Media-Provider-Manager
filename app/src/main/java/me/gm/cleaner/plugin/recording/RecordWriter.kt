package me.gm.cleaner.plugin.recording

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import me.gm.cleaner.plugin.dao.MediaProviderRecord
import me.gm.cleaner.plugin.dao.MediaProviderRecordDatabase
import me.gm.cleaner.plugin.util.L
import org.json.JSONObject
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** All SQL opening, migration, persistence, pruning and close operations share this worker. */
class RecordWriter(
    private val database: MediaProviderRecordDatabase,
    private val file: File,
    initiallyEnabled: Boolean,
    private val changed: () -> Unit,
) {
    private val buffer = RecordBuffer(initiallyEnabled)
    private val thread = HandlerThread("MediaRecordWriter").also { it.start() }
    private val handler = Handler(thread.looper)
    private var store: RecordStore? = null
    @Volatile private var closed = false
    @Volatile var ready = false
        private set
    @Volatile var statusJson = "{\"state\":\"starting\"}"
        private set
    @Volatile private var writable = false
    val enabled get() = !closed && ready && writable && buffer.enabled
    private var lastNotification = 0L


    fun query(packageName: String, table: Int, filtered: Boolean, now: Long): RecordBuffer.Ticket? =
        if (enabled) buffer.query(packageName, table, filtered, now) else null

    fun offer(record: MediaProviderRecord, ticket: RecordBuffer.Ticket? = null) {
        if (enabled && buffer.offer(record, ticket)) {
            synchronized(buffer) {
                if (!handler.hasCallbacks(flush)) handler.postDelayed(flush, 100)
            }
        }
    }

    private val flush: Runnable = Runnable {
        if (!closed && ready) {
            val batch = buffer.drain()
            try {
                if (buffer.enabled && batch.isNotEmpty()) {
                    if (store!!.append(batch, System.currentTimeMillis())) signalChange()
                    else {
                        buffer.noteDropped(batch.sumOf { it.eventCount.toLong() })
                        writable = false
                        handler.removeCallbacks(maintenanceTask)
                        handler.post(maintenanceTask)
                    }
                }
            } catch (failure: Exception) {
                // Never retry an uncertain commit: retrying could inflate event counts.
                buffer.noteDropped(batch.sumOf { it.eventCount.toLong() })
                writable = false
                handler.removeCallbacks(maintenanceTask)
                handler.post(maintenanceTask)
                L.e("Record batch dropped", failure)
            }
            synchronized(buffer) {
                if (buffer.hasPending() && !handler.hasCallbacks(flushTask())) handler.postDelayed(flushTask(), 100)
            }
        }
    }
    private fun flushTask(): Runnable = flush

    private val maintenanceTask = Runnable { maintenance() }

    private fun maintenance() {
        if (closed) return
        var delay = 60_000L
        try {
            val current = store ?: RecordStore(database.openHelper.writableDatabase, file).also { store = it; ready = true }
            val status = current.maintain(System.currentTimeMillis())
            writable = status.state in setOf("ready", "maintenance", "reusable_space")
            statusJson = JSONObject().put("state", status.state).put("rows", status.rows)
                .put("bytes", status.bytes).put("wal_bytes", status.walBytes)
                .put("legacy", status.legacy).put("dropped", buffer.dropped).toString()
            if (status.state == "maintenance") delay = 100L
            else if (status.state != "ready" && status.state != "reusable_space") delay = 5_000L
            signalChange()
        } catch (failure: Exception) {
            writable = false
            statusJson = "{\"state\":\"error\"}"
            L.e("Record maintenance deferred", failure)
            delay = 5_000L
        }
        handler.postDelayed(maintenanceTask, delay)
    }

    fun setEnabled(value: Boolean) {
        buffer.setEnabled(value)
        // The barrier ensures a batch already running finishes before disabling returns.
        if (!value) barrier { buffer.reset() }
    }

    fun clear() = barrier {
        val current = store ?: RecordStore(database.openHelper.writableDatabase, file).also { store = it; ready = true }
        buffer.reset()
        current.clear()
        signalChange()
        handler.removeCallbacks(maintenanceTask)
        handler.post(maintenanceTask)
    }

    private fun barrier(action: () -> Unit) {
        check(!closed) { "Record writer is closed" }
        if (Looper.myLooper() == thread.looper) { action(); return }
        val latch = CountDownLatch(1)
        var failure: Throwable? = null
        check(handler.post {
            try { action() } catch (t: Throwable) { failure = t } finally { latch.countDown() }
        })
        check(latch.await(5, TimeUnit.SECONDS)) { "Record writer is busy; try again" }
        failure?.let { throw it }
    }

    private val notification = Runnable {
        lastNotification = android.os.SystemClock.uptimeMillis()
        if (!closed) changed()
    }

    @Synchronized fun signalChange() {
        if (closed || handler.hasCallbacks(notification)) return
        val delay = (2_000 - (android.os.SystemClock.uptimeMillis() - lastNotification)).coerceAtLeast(0)
        handler.postDelayed(notification, delay)
    }

    init { handler.post(maintenanceTask) }

    fun close() {
        closed = true
        ready = false
        buffer.setEnabled(false)
        handler.removeCallbacksAndMessages(null)
        handler.post { database.close(); thread.quitSafely() }
    }
}
