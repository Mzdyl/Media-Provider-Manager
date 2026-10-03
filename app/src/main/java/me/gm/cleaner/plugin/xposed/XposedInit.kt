/*
 * Copyright 2021 Green Mushroom
 * Licensed under the Apache License, Version 2.0.
 * See http://www.apache.org/licenses/LICENSE-2.0
 */
package me.gm.cleaner.plugin.xposed

import android.content.ContentProvider
import android.content.Context
import android.content.ContentValues
import android.content.pm.ProviderInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.provider.MediaStore
import android.util.Log
import io.github.libxposed.api.XposedInterface.ExceptionMode
import io.github.libxposed.api.XposedInterface.HookHandle
import io.github.libxposed.api.XposedInterface.Hooker
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import me.gm.cleaner.plugin.util.L
import me.gm.cleaner.plugin.xposed.hooker.DeleteHooker
import me.gm.cleaner.plugin.xposed.hooker.FileHooker
import me.gm.cleaner.plugin.xposed.hooker.InsertHooker
import me.gm.cleaner.plugin.xposed.hooker.MediaTables
import me.gm.cleaner.plugin.xposed.hooker.QueryHooker
import me.gm.cleaner.plugin.xposed.util.Reflection
import java.io.File
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean

/** API 102 entry. The app process connects through libxposed-service, not a self-hook. */
class XposedInit : XposedModule() {
    private val attachHookInstalled = AtomicBoolean(false)
    private val mediaProviderInitialized = AtomicBoolean(false)
    private val downloadProviderInitialized = AtomicBoolean(false)

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        L.frameworkLogger = { priority, message, throwable ->
            log(priority, "MPM", message, throwable)
        }
        log(Log.INFO, "MPM", "API $apiVersion loaded in ${param.processName}")
    }

    override fun onPackageReady(param: PackageReadyParam) {
        if (param.packageName !in MediaTables.SYSTEM_CALLING_PACKAGES &&
            param.packageName != "com.android.providers.downloads"
        ) return
        if (!attachHookInstalled.compareAndSet(false, true)) return
        try {
            val method = ContentProvider::class.java.getDeclaredMethod(
                "attachInfo", Context::class.java, ProviderInfo::class.java,
            )
            hook(method).setExceptionMode(ExceptionMode.PROTECTIVE).intercept { chain ->
                try {
                    val context = chain.args[0] as? Context
                    val providerInfo = chain.args[1] as? ProviderInfo
                    val provider = chain.thisObject as? ContentProvider
                    if (context != null && providerInfo != null && provider != null) {
                        val authorities = providerInfo.authority.orEmpty().split(';')
                        when {
                            MediaStore.AUTHORITY in authorities -> initializeMediaProvider(
                                context, provider.javaClass.classLoader ?: param.classLoader,
                            )
                            "downloads" in authorities -> initializeDownloadProvider()
                        }
                    }
                } catch (t: Throwable) {
                    L.e("Provider attach hook failed; allowing provider initialization", t)
                }
                chain.proceed()
            }
        } catch (t: Throwable) {
            attachHookInstalled.set(false)
            L.e("Unable to hook ContentProvider.attachInfo for ${param.packageName}", t)
        }
    }

    private fun initializeMediaProvider(context: Context, loader: ClassLoader) {
        if (!mediaProviderInitialized.compareAndSet(false, true)) return
        val handles = mutableListOf<HookHandle>()
        var service: ManagerService? = null
        try {
            val providerClass = Reflection.findClass("com.android.providers.media.MediaProvider", loader)
            val manager = ManagerService()
            service = manager
            manager.initialize(context, loader, moduleApplicationInfo)
            // QueryCall explicitly propagates the original retry's exception. Protective mode
            // would replace it with the exception from the earlier filtered chain.proceed().
            hookCompatibleMethods(providerClass, "queryInternal", QueryHooker(manager, this), handles, ExceptionMode.PASSTHROUGH) {
                val params = it.parameterTypes
                params.size >= 4 && params[0] == Uri::class.java && params[1].isArray &&
                    params[2] == Bundle::class.java && params[3] == CancellationSignal::class.java
            }
            hookCompatibleMethods(providerClass, "insertFile", InsertHooker(manager), handles) {
                val params = it.parameterTypes
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    params.size >= 6 && params[2] == Int::class.javaPrimitiveType &&
                        params[3] == Uri::class.java && params[5] == ContentValues::class.java
                } else {
                    params.size >= 4 && params[1] == Int::class.javaPrimitiveType &&
                        params[2] == Uri::class.java && params[3] == ContentValues::class.java
                }
            }
            hookCompatibleMethods(providerClass, "deleteInternal", DeleteHooker(manager), handles) {
                val params = it.parameterTypes
                params.isNotEmpty() && params[0] == Uri::class.java &&
                    (Build.VERSION.SDK_INT < Build.VERSION_CODES.R ||
                        (params.size >= 2 && params[1] == Bundle::class.java))
            }
            L.i("MediaProvider hooks ready in ${context.packageName}")
        } catch (t: Throwable) {
            handles.forEach { runCatching { it.unhook() } }
            service?.let { runCatching { it.close() } }
            mediaProviderInitialized.set(false)
            L.e("MediaProvider hook initialization failed; provider will run unmodified", t)
        }
    }

    private fun hookCompatibleMethods(
        target: Class<*>,
        name: String,
        hooker: Hooker,
        handles: MutableList<HookHandle>,
        exceptionMode: ExceptionMode = ExceptionMode.PROTECTIVE,
        matches: (Method) -> Boolean,
    ) {
        val methods = target.declaredMethods.filter { it.name == name && matches(it) }
        if (methods.isEmpty()) {
            L.e("No compatible $name signature found; skipping hook")
        }
        methods.forEach {
            handles += hook(it).setExceptionMode(exceptionMode).intercept(hooker)
        }
        log(Log.INFO, "MPM", "Hooked ${methods.size} compatible $name methods")
    }

    private fun initializeDownloadProvider() {
        if (!downloadProviderInitialized.compareAndSet(false, true)) return
        val handles = mutableListOf<HookHandle>()
        try {
            val hooker = FileHooker()
            for (name in listOf("mkdir", "mkdirs")) {
                handles += hook(File::class.java.getDeclaredMethod(name))
                    .setExceptionMode(ExceptionMode.PROTECTIVE).intercept(hooker)
            }
            log(Log.INFO, "MPM", "DownloadProvider mkdir and mkdirs hooks ready")
        } catch (t: Throwable) {
            handles.forEach { runCatching { it.unhook() } }
            downloadProviderInitialized.set(false)
            L.e("DownloadProvider hook initialization failed; provider will run unmodified", t)
        }
    }
}
