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

import android.content.ContentProvider
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.ProviderInfo
import android.content.res.AssetManager
import android.content.res.Resources
import android.provider.MediaStore
import de.robv.android.xposed.*
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import me.gm.cleaner.plugin.BuildConfig
import me.gm.cleaner.plugin.xposed.hooker.DeleteHooker
import me.gm.cleaner.plugin.xposed.hooker.FileHooker
import me.gm.cleaner.plugin.xposed.hooker.InsertHooker
import me.gm.cleaner.plugin.xposed.hooker.QueryHooker
import java.io.File

class XposedInit : ManagerService(), IXposedHookLoadPackage, IXposedHookZygoteInit {

    @Throws(Throwable::class)
    private fun onMediaProviderLoaded(lpparam: LoadPackageParam, context: Context) {
        if (BuildConfig.DEBUG) XposedBridge.log("MPM_DEBUG: MediaProvider loaded: ${lpparam.packageName}")
        val mediaProvider = try {
            XposedHelpers.findClass(
                "com.android.providers.media.MediaProvider", lpparam.classLoader
            )
        } catch (e: XposedHelpers.ClassNotFoundError) {
            if (BuildConfig.DEBUG) XposedBridge.log("MPM_DEBUG: MediaProvider class not found!")
            return
        }
        // only save MediaProvider's classLoader
        classLoader = lpparam.classLoader
        onCreate(context)
        try {
            if (BuildConfig.DEBUG) {
                XposedBridge.log("********** MPM_DEBUG: START METHOD DUMP **********")
                
                XposedBridge.log("--- MediaProvider Methods ---")
                mediaProvider.declaredMethods.forEach { method ->
                    if (method.name in setOf("getQueryBuilder", "getDatabaseForUri", "resolveVolumeName", "queryInternal")) {
                        val params = method.parameterTypes.joinToString(", ") { it.name }
                        XposedBridge.log("MPM_METHOD_DUMP: ${method.name}($params) -> ${method.returnType.name}")
                    }
                }
                
                try {
                    val databaseUtilsClass = XposedHelpers.findClass(
                        "com.android.providers.media.util.DatabaseUtils", lpparam.classLoader
                    )
                    XposedBridge.log("--- DatabaseUtils Methods ---")
                    databaseUtilsClass.declaredMethods.forEach { method ->
                        if (method.name in setOf("resolveQueryArgs", "recoverAbusiveSortOrder", "recoverAbusiveLimit", "recoverAbusiveSelection")) {
                            val params = method.parameterTypes.joinToString(", ") { it.name }
                            XposedBridge.log("MPM_METHOD_DUMP: ${method.name}($params) -> ${method.returnType.name}")
                        }
                    }
                } catch (e: Throwable) {
                    XposedBridge.log("MPM_DEBUG: Could not find DatabaseUtils: $e")
                }

                XposedBridge.log("*********** MPM_DEBUG: END METHOD DUMP ***********")
            }

            XposedBridge.hookAllMethods(
                mediaProvider, "queryInternal", QueryHooker(this@XposedInit)
            )
            if (BuildConfig.DEBUG) XposedBridge.log("MPM_DEBUG: Hooked queryInternal")
            XposedBridge.hookAllMethods(
                mediaProvider, "insertFile", InsertHooker(this@XposedInit)
            )
            if (BuildConfig.DEBUG) XposedBridge.log("MPM_DEBUG: Hooked insertFile")
            XposedBridge.hookAllMethods(
                mediaProvider, "deleteInternal", DeleteHooker(this@XposedInit)
            )
            if (BuildConfig.DEBUG) XposedBridge.log("MPM_DEBUG: Hooked deleteInternal")
        } catch (t: Throwable) {
            if (BuildConfig.DEBUG) XposedBridge.log("MPM_DEBUG: Error hooking MediaProvider: $t")
        }
    }

    @Throws(Throwable::class)
    private fun onDownloadManagerLoaded(lpparam: LoadPackageParam, context: Context) {
        XposedHelpers.findAndHookMethod(File::class.java, "mkdir", FileHooker())
        XposedHelpers.findAndHookMethod(File::class.java, "mkdirs", FileHooker())
    }

    @Throws(Throwable::class)
    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        if (lpparam.appInfo.flags and ApplicationInfo.FLAG_SYSTEM == 0) {
            return
        }
        XposedHelpers.findAndHookMethod(
            ContentProvider::class.java, "attachInfo",
            Context::class.java, ProviderInfo::class.java, Boolean::class.java,
            object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val context = param.args[0] as Context
                    val providerInfo = param.args[1] as ProviderInfo

                    when (providerInfo.authority) {
                        MediaStore.AUTHORITY -> onMediaProviderLoaded(lpparam, context)
                        Downloads_Impl_AUTHORITY -> onDownloadManagerLoaded(lpparam, context)
                    }
                }
            }
        )
    }

    @Throws(Throwable::class)
    @Suppress("DEPRECATION")
    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
        val assetManager = AssetManager::class.java.newInstance()
        XposedHelpers.callMethod(assetManager, "addAssetPath", startupParam.modulePath)
        resources = Resources(assetManager, null, null)
    }

    companion object {
        const val Downloads_Impl_AUTHORITY = "downloads"
    }
}
