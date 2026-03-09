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
import de.robv.android.xposed.XposedHelpers
import me.gm.cleaner.plugin.BuildConfig
import me.gm.cleaner.plugin.IManagerService
import me.gm.cleaner.plugin.IMediaChangeObserver
import me.gm.cleaner.plugin.R
import me.gm.cleaner.plugin.dao.MIGRATION_1_2
import me.gm.cleaner.plugin.dao.MediaProviderRecord
import me.gm.cleaner.plugin.dao.MediaProviderRecordDao
import me.gm.cleaner.plugin.dao.MediaProviderRecordDatabase
import me.gm.cleaner.plugin.model.ParceledListSlice
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

abstract class ManagerService : IManagerService.Stub() {
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
    private val recordQueue = ConcurrentLinkedQueue<MediaProviderRecord>()
    private var writeHandler: Handler? = null
    private var handlerThread: HandlerThread? = null
    private val hasPendingWrite = AtomicBoolean(false)
    
    companion object {
        private const val MSG_WRITE_RECORDS = 1
        private const val WRITE_DELAY_MS = 100L // Batch writes within 100ms
        private const val MAX_BATCH_SIZE = 50
    }

    private fun enforceCallerPermission() {
        val callingUid = Binder.getCallingUid()
        if (callingUid != appUid && callingUid != Process.SYSTEM_UID) {
            throw SecurityException("Unauthorized caller: uid=$callingUid")
        }
    }

    protected fun onCreate(context: Context) {
        this.context = context
        appUid = context.packageManager.getPackageUid(BuildConfig.APPLICATION_ID, 0)
        database = Room
            .databaseBuilder(
                context,
                MediaProviderRecordDatabase::class.java,
                MEDIA_PROVIDER_USAGE_RECORD_DATABASE_NAME
            )
            .addMigrations(MIGRATION_1_2)
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
    }
    
    /**
     * Insert record asynchronously to avoid blocking MediaProvider thread.
     * Records are batched and written in background thread.
     */
    fun insertRecordAsync(record: MediaProviderRecord) {
        recordQueue.offer(record)
        scheduleFlush()
    }
    
    /**
     * Insert multiple records asynchronously.
     */
    fun insertRecordsAsync(records: List<MediaProviderRecord>) {
        records.forEach { recordQueue.offer(it) }
        scheduleFlush()
    }
    
    private fun scheduleFlush() {
        if (hasPendingWrite.compareAndSet(false, true)) {
            writeHandler?.sendEmptyMessageDelayed(MSG_WRITE_RECORDS, WRITE_DELAY_MS)
        }
    }
    
    private fun flushRecordQueue() {
        hasPendingWrite.set(false)
        val batch = mutableListOf<MediaProviderRecord>()
        while (batch.size < MAX_BATCH_SIZE) {
            val record = recordQueue.poll() ?: break
            batch.add(record)
        }
        
        if (batch.isNotEmpty()) {
            try {
                if (batch.size == 1) {
                    dao.insert(batch[0])
                } else {
                    dao.insertAll(batch)
                }
            } catch (e: Exception) {
                // Log and continue, don't crash the system process
            }
        }
        
        // If there are more records, schedule another flush
        if (recordQueue.isNotEmpty()) {
            scheduleFlush()
        }
        
        // Dispatch media change after write
        if (batch.isNotEmpty()) {
            dispatchMediaChange()
        }
    }

    private val packageManagerService: IInterface by lazy {
        val binder = XposedHelpers.callStaticMethod(
            XposedHelpers.findClass("android.os.ServiceManager", classLoader),
            "getService", "package"
        ) as IBinder
        XposedHelpers.callStaticMethod(
            XposedHelpers.findClass(
                "android.content.pm.IPackageManager\$Stub", classLoader
            ), "asInterface", binder
        ) as IInterface
    }

    override fun getModuleVersion() = BuildConfig.VERSION_CODE

    override fun getInstalledPackages(userId: Int, flags: Int): ParceledListSlice<PackageInfo> {
        enforceCallerPermission()
        val parceledListSlice = XposedHelpers.callMethod(
            packageManagerService,
            "getInstalledPackages",
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) flags.toLong() else flags,
            userId
        )
        val list = XposedHelpers.callMethod(parceledListSlice, "getList") as List<PackageInfo>
        return ParceledListSlice(list)
    }

    override fun getPackageInfo(packageName: String, flags: Int, userId: Int): PackageInfo? {
        enforceCallerPermission()
        return XposedHelpers.callMethod(
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
            R.xml.root_preferences -> rootSp.read()
            R.xml.template_preferences -> ruleSp.read()
            else -> throw IllegalArgumentException()
        }
    }

    override fun writeSp(who: Int, what: String) {
        enforceCallerPermission()
        when (who) {
            R.xml.root_preferences -> rootSp.write(what)
            R.xml.template_preferences -> ruleSp.write(what)
        }
    }

    override fun clearAllTables() {
        enforceCallerPermission()
        database.clearAllTables()
    }

    override fun packageUsageTimes(operation: Int, packageNames: List<String>): Int {
        enforceCallerPermission()
        return dao.packageUsageTimes(operation, *packageNames.toTypedArray())
    }

    override fun registerMediaChangeObserver(observer: IMediaChangeObserver) {
        observers.register(observer)
    }

    override fun unregisterMediaChangeObserver(observer: IMediaChangeObserver) {
        observers.unregister(observer)
    }

    private var lastDispatchTime = 0L

    /**
     * Dispatch media change with debouncing to avoid excessive notifications.
     * Multiple calls within 500ms will be coalesced into a single notification.
     */
    @Synchronized
    fun dispatchMediaChange() {
        val now = SystemClock.uptimeMillis()
        if (now - lastDispatchTime >= 500) {
            lastDispatchTime = now
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
    }

    companion object {
        const val MEDIA_PROVIDER_USAGE_RECORD_DATABASE_NAME = "media_provider.db"
    }
}
