/*
 * Copyright 2021 Green Mushroom
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.gm.cleaner.plugin.xposed

import android.content.Context
import android.content.pm.PackageInfo
import android.content.res.Resources
import android.os.*
import androidx.room.Room
import me.gm.cleaner.plugin.xposed.util.Reflection
import me.gm.cleaner.plugin.BuildConfig
import me.gm.cleaner.plugin.IManagerService
import me.gm.cleaner.plugin.IMediaChangeObserver
import me.gm.cleaner.plugin.R
import me.gm.cleaner.plugin.dao.MIGRATION_1_2
import me.gm.cleaner.plugin.dao.MIGRATION_2_3
import me.gm.cleaner.plugin.dao.MediaProviderRecord
import me.gm.cleaner.plugin.dao.MediaProviderRecordDao
import me.gm.cleaner.plugin.dao.MediaProviderRecordDatabase
import me.gm.cleaner.plugin.model.ParceledListSlice
import me.gm.cleaner.plugin.model.SpIdentifiers
import me.gm.cleaner.plugin.util.L
import java.io.File
import java.util.ArrayDeque
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class ManagerService : IManagerService.Stub() {
    lateinit var classLoader: ClassLoader
        protected set
    lateinit var resources: Resources
        protected set
    lateinit var context: Context
        private set
    private lateinit var database: MediaProviderRecordDatabase
    lateinit var dao: MediaProviderRecordDao
        private set
    private val observers = RemoteCallbackList<IMediaChangeObserver>()
    val rootSp by lazy { JsonFileSpImpl(File(context.filesDir, "root")) }
    val ruleSp by lazy { TemplatesJsonFileSpImpl(File(context.filesDir, "rule")) }

    private var appUid: Int = -1

    // Async database write mechanism
    private val recordQueue = ArrayDeque<MediaProviderRecord>()
    private val recordQueueLock = Any()
    private val droppedRecordCount = AtomicLong(0)
    private var writeHandler: Handler? = null
    private var handlerThread: HandlerThread? = null
    private val hasPendingWrite = AtomicBoolean(false)

    private fun enforceCallerPermission() {
        val callingUid = Binder.getCallingUid()
        if (callingUid != appUid && callingUid != Process.SYSTEM_UID) {
            throw SecurityException("Unauthorized caller: uid=$callingUid")
        }
    }

    fun initialize(context: Context, providerClassLoader: ClassLoader, moduleInfo: android.content.pm.ApplicationInfo) {
        classLoader = providerClassLoader
        resources = context.packageManager.getResourcesForApplication(moduleInfo)
        this.context = context
        appUid = context.packageManager.getPackageUid(BuildConfig.APPLICATION_ID, 0)
        database = Room
            .databaseBuilder(
                context,
                MediaProviderRecordDatabase::class.java,
                MEDIA_PROVIDER_USAGE_RECORD_DATABASE_NAME
            )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()
        dao = database.mediaProviderRecordDao()
        
        // Initialize async write handler
        handlerThread = HandlerThread("MediaRecordWriter").also { it.start() }
        writeHandler = object : Handler(handlerThread!!.looper) {
            override fun handleMessage(msg: Message) {
                if (msg.what == MSG_WRITE_RECORDS) {
                    flushRecordQueue()
                }
            }
        }
        writeHandler?.post { pruneOldRecords() }
    }
    
    /**
     * Clean up resources when service is being destroyed.
     * Should be called from Xposed hook when MediaProvider is shutting down.
     */
    fun close() {
        // Flush remaining records before shutdown
        flushRecordQueueSync()

        // Remove pending dispatch callbacks
        writeHandler?.removeCallbacksAndMessages(null)
        dispatchScheduled = false
        
        writeHandler = null
        handlerThread?.quitSafely()
        handlerThread = null

        if (::database.isInitialized) database.close()
        
        // Clear observers
        observers.kill()
    }
    
    /**
     * Insert record asynchronously to avoid blocking MediaProvider thread.
     * Records are batched and written in background thread.
     */
    fun insertRecordAsync(record: MediaProviderRecord) {
        if (enqueueRecord(record)) scheduleFlush()
    }
    
    /**
     * Insert multiple records asynchronously.
     */
    fun insertRecordsAsync(records: List<MediaProviderRecord>) {
        var enqueued = false
        records.forEach { enqueued = enqueueRecord(it) || enqueued }
        if (enqueued) scheduleFlush()
    }

    private fun enqueueRecord(record: MediaProviderRecord): Boolean {
        if (writeHandler == null) return false
        synchronized(recordQueueLock) {
            if (recordQueue.size >= MAX_QUEUED_RECORDS) {
                val dropped = droppedRecordCount.incrementAndGet()
                if (dropped == 1L || dropped % DROPPED_RECORD_LOG_INTERVAL == 0L) {
                    L.w(
                        "ManagerService",
                        "Dropped $dropped usage records because the writer queue is full",
                    )
                }
                return false
            }
            recordQueue.offerLast(record)
        }
        return true
    }

    private fun drainRecordBatch(): List<MediaProviderRecord> = synchronized(recordQueueLock) {
        buildList(minOf(recordQueue.size, MAX_BATCH_SIZE)) {
            while (size < MAX_BATCH_SIZE) {
                add(recordQueue.pollFirst() ?: break)
            }
        }
    }

    private fun hasQueuedRecords(): Boolean = synchronized(recordQueueLock) {
        recordQueue.isNotEmpty()
    }
    
    private fun scheduleFlush() {
        if (hasPendingWrite.compareAndSet(false, true)) {
            writeHandler?.sendEmptyMessageDelayed(MSG_WRITE_RECORDS, WRITE_DELAY_MS)
        }
    }
    
    private fun flushRecordQueue() {
        hasPendingWrite.set(false)
        val batch = drainRecordBatch()
        
        var persisted = false
        if (batch.isNotEmpty()) {
            try {
                if (batch.size == 1) {
                    dao.insert(batch[0])
                } else {
                    dao.insertAll(batch)
                }
                persisted = true
            } catch (e: Exception) {
                L.e("Failed to persist usage record batch", e)
            }
        }
        
        // If there are more records, schedule another flush
        if (hasQueuedRecords()) {
            scheduleFlush()
        }
        
        // Dispatch media change after write
        if (persisted) {
            dispatchMediaChange()
        }
        maybePruneOldRecords()
    }
    
    /**
     * Synchronously flush all remaining records in the queue.
     * Used during shutdown to ensure no records are lost.
     */
    private fun flushRecordQueueSync() {
        while (hasQueuedRecords()) {
            val batch = drainRecordBatch()
            
            if (batch.isNotEmpty()) {
                try {
                    if (batch.size == 1) {
                        dao.insert(batch[0])
                    } else {
                        dao.insertAll(batch)
                    }
                } catch (e: Exception) {
                    L.e("Failed to flush usage records", e)
                }
            } else {
                break
            }
        }
    }

    private val packageManagerService: IInterface by lazy {
        val binder = Reflection.callStaticMethod(
            Reflection.findClass("android.os.ServiceManager", classLoader),
            "getService", "package"
        ) as IBinder
        Reflection.callStaticMethod(
            Reflection.findClass(
                "android.content.pm.IPackageManager\$Stub", classLoader
            ), "asInterface", binder
        ) as IInterface
    }

    override fun getModuleVersion() = BuildConfig.VERSION_CODE

    override fun getXposedApiVersion() = BuildConfig.XPOSED_API_VERSION

    override fun getInstalledPackages(userId: Int, flags: Int): ParceledListSlice<PackageInfo> {
        enforceCallerPermission()
        val parceledListSlice = Reflection.callMethod(
            packageManagerService,
            "getInstalledPackages",
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) flags.toLong() else flags,
            userId
        )
        val list = (Reflection.callMethod(parceledListSlice, "getList") as? List<*>)
            ?.filterIsInstance<PackageInfo>()
            .orEmpty()
        return ParceledListSlice(list)
    }

    override fun getPackageInfo(packageName: String, flags: Int, userId: Int): PackageInfo? {
        enforceCallerPermission()
        return Reflection.callMethod(
            packageManagerService,
            "getPackageInfo",
            packageName,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) flags.toLong() else flags,
            userId
        ) as? PackageInfo
    }

    override fun readSp(who: Int): String? {
        enforceCallerPermission()
        return when (who) {
            SpIdentifiers.ROOT_PREFERENCES -> rootSp.read()
            SpIdentifiers.TEMPLATE_PREFERENCES -> ruleSp.read()
            else -> null
        }
    }

    override fun writeSp(who: Int, what: String) {
        enforceCallerPermission()
        when (who) {
            SpIdentifiers.ROOT_PREFERENCES -> rootSp.write(what)
            SpIdentifiers.TEMPLATE_PREFERENCES -> ruleSp.write(what)
        }
    }

    override fun clearAllTables() {
        enforceCallerPermission()
        val handler = writeHandler
        if (handler == null || Looper.myLooper() == handler.looper) {
            clearRecordsInternal()
            return
        }
        val latch = CountDownLatch(1)
        handler.post {
            try {
                clearRecordsInternal()
            } finally {
                latch.countDown()
            }
        }
        if (!latch.await(CLEAR_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            throw IllegalStateException("Timed out clearing usage records")
        }
    }

    override fun packageUsageTimes(operation: Int, packageNames: List<String>): Int {
        enforceCallerPermission()
        return dao.packageUsageTimes(operation, packageNames.toTypedArray())
    }

    override fun registerMediaChangeObserver(observer: IMediaChangeObserver) {
        enforceCallerPermission()
        observers.register(observer)
    }

    override fun unregisterMediaChangeObserver(observer: IMediaChangeObserver) {
        enforceCallerPermission()
        observers.unregister(observer)
    }

    private var lastDispatchTime = 0L
    private var dispatchScheduled = false

    /**
     * Dispatch media change with debouncing to avoid excessive notifications.
     * Multiple calls within 500ms will be coalesced into a single notification.
     * Uses a scheduled approach to batch multiple rapid changes.
     */
    @Synchronized
    fun dispatchMediaChange() {
        val now = SystemClock.uptimeMillis()
        
        // If we're within the debounce window, schedule a delayed dispatch
        if (now - lastDispatchTime < DEBOUNCE_INTERVAL_MS) {
            if (!dispatchScheduled) {
                dispatchScheduled = true
                writeHandler?.postDelayed({
                    dispatchMediaChangeInternal()
                }, DEBOUNCE_INTERVAL_MS - (now - lastDispatchTime))
            }
            return
        }
        
        // Otherwise, dispatch immediately
        dispatchMediaChangeInternal()
    }

    private fun clearRecordsInternal() {
        writeHandler?.removeMessages(MSG_WRITE_RECORDS)
        synchronized(recordQueueLock) { recordQueue.clear() }
        hasPendingWrite.set(false)
        database.clearAllTables()
        dispatchMediaChange()
    }

    private var lastPruneTime = 0L

    private fun maybePruneOldRecords() {
        val now = System.currentTimeMillis()
        if (now - lastPruneTime >= PRUNE_INTERVAL_MS) pruneOldRecords(now)
    }

    private fun pruneOldRecords(now: Long = System.currentTimeMillis()) {
        try {
            val deleted = dao.deleteOlderThan(now - RECORD_RETENTION_MS)
            lastPruneTime = now
            if (deleted > 0) dispatchMediaChange()
        } catch (e: Exception) {
            L.e("Failed to prune old usage records", e)
        }
    }
    
    private fun dispatchMediaChangeInternal() {
        val now = SystemClock.uptimeMillis()
        lastDispatchTime = now
        dispatchScheduled = false
        
        var i = observers.beginBroadcast()
        while (i > 0) {
            i--
            val observer = observers.getBroadcastItem(i)
            if (observer != null) {
                try {
                    observer.onChange()
                } catch (ignored: RemoteException) {
                }
            }
        }
        observers.finishBroadcast()
    }

    companion object {
        const val MEDIA_PROVIDER_USAGE_RECORD_DATABASE_NAME = "media_provider.db"

        private const val MSG_WRITE_RECORDS = 1
        private const val WRITE_DELAY_MS = 100L // Batch writes within 100ms
        private const val MAX_BATCH_SIZE = 50
        private const val MAX_QUEUED_RECORDS = 500
        private const val DROPPED_RECORD_LOG_INTERVAL = 100L
        private const val DEBOUNCE_INTERVAL_MS = 500L // Debounce interval for media change notifications
        private const val CLEAR_TIMEOUT_SECONDS = 5L
        private const val PRUNE_INTERVAL_MS = 6L * 60L * 60L * 1000L
        private const val RECORD_RETENTION_MS = 90L * 24L * 60L * 60L * 1000L
    }
}
