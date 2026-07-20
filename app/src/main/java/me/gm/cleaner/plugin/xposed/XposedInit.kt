/*
 * Copyright 2021 Green Mushroom
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *     required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.gm.cleaner.plugin.xposed

import android.app.Application
import android.content.ContentProvider
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.ProviderInfo
import android.content.res.AssetManager
import android.content.res.Resources
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.provider.MediaStore
import de.robv.android.xposed.*
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import me.gm.cleaner.plugin.BuildConfig
import me.gm.cleaner.plugin.util.L
import me.gm.cleaner.plugin.util.ModuleActivationStore
import me.gm.cleaner.plugin.xposed.hooker.DeleteHooker
import me.gm.cleaner.plugin.xposed.hooker.FileHooker
import me.gm.cleaner.plugin.xposed.hooker.InsertHooker
import me.gm.cleaner.plugin.xposed.hooker.QueryHooker
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class XposedInit : ManagerService(), IXposedHookLoadPackage, IXposedHookZygoteInit {

    private val mediaProviderInitialized = AtomicBoolean(false)
    private val downloadProviderInitialized = AtomicBoolean(false)

    @Throws(Throwable::class)
    private fun onModuleAppLoaded(lpparam: LoadPackageParam) {
        XposedHelpers.findAndHookMethod(
            Application::class.java,
            "onCreate",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val application = param.thisObject as? Application ?: return
                    if (application.packageName != BuildConfig.APPLICATION_ID) {
                        return
                    }
                    ModuleActivationStore.markAppProcessHooked(application)
                    L.d("Marked module app process as hooked")
                }
            }
        )
    }

    @Throws(Throwable::class)
    private fun onMediaProviderLoaded(lpparam: LoadPackageParam, context: Context) {
        if (!mediaProviderInitialized.compareAndSet(false, true)) return
        L.d("MediaProvider loaded: ${lpparam.packageName}")
        val mediaProvider = try {
            XposedHelpers.findClass(
                "com.android.providers.media.MediaProvider", lpparam.classLoader
            )
        } catch (e: XposedHelpers.ClassNotFoundError) {
            L.e("MediaProvider class not found!", e)
            mediaProviderInitialized.set(false)
            return
        }
        try {
            // Only save MediaProvider's classLoader after the target class is resolved.
            classLoader = lpparam.classLoader
            onCreate(context)

            if (L.isDebug) {
                L.dumpHeader("START METHOD DUMP")
                
                L.d("--- MediaProvider Methods ---")
                mediaProvider.declaredMethods.forEach { method ->
                    if (method.name in setOf("getQueryBuilder", "getDatabaseForUri", "resolveVolumeName", "queryInternal")) {
                        val params = method.parameterTypes.joinToString(", ") { it.name }
                        L.v("METHOD_DUMP: ${method.name}($params) -> ${method.returnType.name}")
                    }
                }
                
                try {
                    val databaseUtilsClass = XposedHelpers.findClass(
                        "com.android.providers.media.util.DatabaseUtils", lpparam.classLoader
                    )
                    L.d("--- DatabaseUtils Methods ---")
                    databaseUtilsClass.declaredMethods.forEach { method ->
                        if (method.name in setOf("resolveQueryArgs", "recoverAbusiveSortOrder", "recoverAbusiveLimit", "recoverAbusiveSelection")) {
                            val params = method.parameterTypes.joinToString(", ") { it.name }
                            L.v("METHOD_DUMP: ${method.name}($params) -> ${method.returnType.name}")
                        }
                    }
                } catch (e: Throwable) {
                    L.e("Could not find DatabaseUtils", e)
                }

                L.dumpFooter()
            }

            hookCompatibleMethods(mediaProvider, "queryInternal", QueryHooker(this@XposedInit)) {
                val params = it.parameterTypes
                params.size >= 4 &&
                    params[0] == android.net.Uri::class.java &&
                    params[1].isArray &&
                    params[2] == Bundle::class.java &&
                    params[3] == CancellationSignal::class.java
            }
            hookCompatibleMethods(mediaProvider, "insertFile", InsertHooker(this@XposedInit)) {
                val params = it.parameterTypes
                when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                        params.size >= 6 &&
                            params[2] == Int::class.javaPrimitiveType &&
                            params[3] == android.net.Uri::class.java &&
                            params[5] == android.content.ContentValues::class.java

                    else -> params.size >= 4 &&
                        params[1] == Int::class.javaPrimitiveType &&
                        params[2] == android.net.Uri::class.java &&
                        params[3] == android.content.ContentValues::class.java
                }
            }
            hookCompatibleMethods(mediaProvider, "deleteInternal", DeleteHooker(this@XposedInit)) {
                val params = it.parameterTypes
                params.isNotEmpty() &&
                    params[0] == android.net.Uri::class.java &&
                    (Build.VERSION.SDK_INT < Build.VERSION_CODES.R ||
                        (params.size >= 2 && params[1] == Bundle::class.java))
            }
        } catch (t: Throwable) {
            L.e("MediaProvider hook initialization failed; provider will run unmodified", t)
        }
    }

    private fun hookCompatibleMethods(
        targetClass: Class<*>,
        methodName: String,
        callback: XC_MethodHook,
        predicate: (java.lang.reflect.Method) -> Boolean,
    ) {
        val methods = targetClass.declaredMethods.filter {
            it.name == methodName && predicate(it)
        }
        if (methods.isEmpty()) {
            L.w("No compatible $methodName signature found; skipping hook")
            return
        }
        methods.forEach { method -> XposedBridge.hookMethod(method, callback) }
        L.d("Hooked ${methods.size} compatible $methodName method(s)")
    }

    @Throws(Throwable::class)
    private fun onDownloadManagerLoaded(lpparam: LoadPackageParam, context: Context) {
        if (!downloadProviderInitialized.compareAndSet(false, true)) return
        try {
            val hooker = FileHooker()
            XposedHelpers.findAndHookMethod(File::class.java, "mkdir", hooker)
            XposedHelpers.findAndHookMethod(File::class.java, "mkdirs", hooker)
        } catch (t: Throwable) {
            L.e("DownloadProvider hook initialization failed; provider will run unmodified", t)
        }
    }

    @Throws(Throwable::class)
    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        if (lpparam.packageName == BuildConfig.APPLICATION_ID) {
            onModuleAppLoaded(lpparam)
            return
        }
        if (lpparam.appInfo.flags and ApplicationInfo.FLAG_SYSTEM == 0) {
            return
        }
        try {
            XposedHelpers.findAndHookMethod(
                ContentProvider::class.java, "attachInfo",
                Context::class.java, ProviderInfo::class.java, Boolean::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        try {
                            val context = param.args[0] as? Context ?: return
                            val providerInfo = param.args[1] as? ProviderInfo ?: return

                            when (providerInfo.authority) {
                                MediaStore.AUTHORITY -> onMediaProviderLoaded(lpparam, context)
                                Downloads_Impl_AUTHORITY -> onDownloadManagerLoaded(lpparam, context)
                            }
                        } catch (t: Throwable) {
                            L.e("Provider attach hook failed; allowing provider initialization", t)
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            L.e("Unable to hook ContentProvider.attachInfo for ${lpparam.packageName}", t)
        }
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
