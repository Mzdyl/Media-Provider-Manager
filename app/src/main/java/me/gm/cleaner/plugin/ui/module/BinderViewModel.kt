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

package me.gm.cleaner.plugin.ui.module

import android.content.Context
import android.content.pm.PackageInfo
import android.os.IBinder
import android.os.Process
import android.provider.MediaStore
import android.util.SparseArray
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import me.gm.cleaner.plugin.IManagerService
import me.gm.cleaner.plugin.IMediaChangeObserver
import me.gm.cleaner.plugin.model.SpIdentifiers.ROOT_PREFERENCES
import me.gm.cleaner.plugin.model.SpIdentifiers.TEMPLATE_PREFERENCES
import javax.inject.Inject

@HiltViewModel
class BinderViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {
    // Lazy initialization to avoid blocking app startup
    private val binder: IBinder? by lazy {
        runCatching {
            context.contentResolver.query(
                MediaStore.Images.Media.INTERNAL_CONTENT_URI, null, null, null, null
            )?.use {
                it.extras.getBinder(BINDER_EXTRA_KEY)
            }
        }.getOrNull()
    }
    
    private val service: IManagerService? by lazy {
        binder?.let { IManagerService.Stub.asInterface(it) }
    }
    
    private val _remoteSpCacheLiveData = MutableLiveData(SparseArray<String>())
    val remoteSpCacheLiveData: LiveData<SparseArray<String>>
        get() = _remoteSpCacheLiveData
    val remoteSpCache: SparseArray<String>
        get() = _remoteSpCacheLiveData.value!!

    fun notifyRemoteSpChanged() {
        _remoteSpCacheLiveData.postValue(remoteSpCache)
    }

    fun pingBinder(): Boolean = binder?.pingBinder() == true

    val moduleVersion: Int
        get() = service?.moduleVersion ?: 0

    fun getInstalledPackages(flags: Int): List<PackageInfo> =
        service?.getInstalledPackages(Process.myUid() / AID_USER_OFFSET, flags)?.list ?: emptyList()

    fun getPackageInfo(packageName: String): PackageInfo? =
        service?.getPackageInfo(packageName, 0, Process.myUid() / AID_USER_OFFSET)

    fun readSp(who: Int): String? =
        service?.readSp(who)?.also { remoteSpCache.put(who, it) }

    fun writeSp(who: Int, what: String) {
        val cacheValue = remoteSpCache[who]
        if (cacheValue != what) {
            service?.writeSp(who, what)
            if (service != null) {
                remoteSpCache.put(who, what)
                notifyRemoteSpChanged()
            }
        }
    }
    
    fun readRootSp(): String? = readSp(ROOT_PREFERENCES)
    fun readTemplateSp(): String? = readSp(TEMPLATE_PREFERENCES)
    fun writeRootSp(what: String) = writeSp(ROOT_PREFERENCES, what)
    fun writeTemplateSp(what: String) = writeSp(TEMPLATE_PREFERENCES, what)

    fun clearAllTables() {
        service?.clearAllTables()
    }

    fun packageUsageTimes(operation: Int, packageNames: List<String>): Int =
        service?.packageUsageTimes(operation, packageNames) ?: 0

    fun registerMediaChangeObserver(observer: IMediaChangeObserver) {
        service?.registerMediaChangeObserver(observer)
    }

    fun unregisterMediaChangeObserver(observer: IMediaChangeObserver) {
        service?.unregisterMediaChangeObserver(observer)
    }

    companion object {
        const val AID_USER_OFFSET = 100000
        const val BINDER_EXTRA_KEY = "me.gm.cleaner.plugin.cursor.extra.BINDER"
    }
}
