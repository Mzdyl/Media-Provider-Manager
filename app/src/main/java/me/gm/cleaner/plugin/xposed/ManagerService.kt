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
import me.gm.cleaner.plugin.dao.MIGRATION_1_4
import me.gm.cleaner.plugin.dao.MIGRATION_2_4
import me.gm.cleaner.plugin.dao.MIGRATION_3_4
import me.gm.cleaner.plugin.recording.RecordWriter
import me.gm.cleaner.plugin.recording.RecordBuffer
import me.gm.cleaner.plugin.recording.RecordPolicy
import me.gm.cleaner.plugin.dao.MediaProviderRecord
import me.gm.cleaner.plugin.dao.MediaProviderRecordDao
import me.gm.cleaner.plugin.dao.MediaProviderRecordDatabase
import me.gm.cleaner.plugin.model.ParceledListSlice
import me.gm.cleaner.plugin.model.SpIdentifiers
import java.io.File

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

    private var recordWriter: RecordWriter? = null
    val recordingEnabled: Boolean get() = recordWriter?.enabled == true

    fun queryRecordTicket(packageName: String, table: Int, filtered: Boolean): RecordBuffer.Ticket? =
        recordWriter?.query(packageName, table, filtered, System.currentTimeMillis())

    fun insertRecordAsync(record: MediaProviderRecord, ticket: RecordBuffer.Ticket? = null) {
        recordWriter?.offer(record, ticket)
    }

    fun insertRecordsAsync(records: List<MediaProviderRecord>) { records.forEach { insertRecordAsync(it) } }

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
            .addMigrations(MIGRATION_1_4, MIGRATION_2_4, MIGRATION_3_4)
            .setJournalMode(androidx.room.RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .build()
        dao = database.mediaProviderRecordDao()

        recordWriter = RecordWriter(database, context.getDatabasePath(MEDIA_PROVIDER_USAGE_RECORD_DATABASE_NAME),
            rootSp.getBoolean("usage_record", true), ::dispatchMediaChangeInternal)
    }

    fun close() {
        recordWriter?.close()
        recordWriter = null
        observers.kill()
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
            SpIdentifiers.ROOT_PREFERENCES -> {
                rootSp.write(what)
                recordWriter?.setEnabled(rootSp.getBoolean("usage_record", true))
            }
            SpIdentifiers.TEMPLATE_PREFERENCES -> ruleSp.write(what)
        }
    }

    override fun clearAllTables() {
        enforceCallerPermission()
        checkNotNull(recordWriter) { "Record writer unavailable" }.clear()
    }

    override fun getRecordStorageStatus(): String {
        enforceCallerPermission()
        return recordWriter?.statusJson ?: "{\"state\":\"starting\"}"
    }

    @Suppress("WrongConstant") // Values are validated by the client-query protocol before this call.
    fun loadRecords(start: Long, end: Long, operations: IntArray): android.database.Cursor {
        if (recordWriter?.ready != true) return android.database.MatrixCursor(arrayOf("id"))
        return dao.loadForTimeMillis(maxOf(start, System.currentTimeMillis() - RecordPolicy.RETENTION_MS), end, operations)
    }

    override fun packageUsageTimes(operation: Int, packageNames: List<String>): Int {
        enforceCallerPermission()
        if (recordWriter?.ready != true) return 0
        return dao.packageUsageTimes(operation, packageNames.toTypedArray(), System.currentTimeMillis() - RecordPolicy.RETENTION_MS)
    }

    override fun registerMediaChangeObserver(observer: IMediaChangeObserver) {
        enforceCallerPermission()
        observers.register(observer)
    }

    override fun unregisterMediaChangeObserver(observer: IMediaChangeObserver) {
        enforceCallerPermission()
        observers.unregister(observer)
    }

    fun dispatchMediaChange() { recordWriter?.signalChange() }

    private fun dispatchMediaChangeInternal() {
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

    }
}
