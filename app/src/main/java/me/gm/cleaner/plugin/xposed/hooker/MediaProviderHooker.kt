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

package me.gm.cleaner.plugin.xposed.hooker

import android.net.Uri
import android.os.Bundle
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import me.gm.cleaner.plugin.BuildConfig
import java.lang.reflect.Method
import java.util.Optional

interface MediaProviderHooker {
    companion object {
        private val lastLog = AtomicReference<String?>(null)
        private val lastLogTime = AtomicReference(0L)

        private val isQueryBuilderResolved = AtomicReference(false)

        @Volatile
        var queryBuilderMethodInstance: Method? = null
            private set

        fun logOnce(message: String) {
            if (BuildConfig.DEBUG) {
                val now = System.currentTimeMillis()
                val prevMessage = lastLog.get()
                val prevTime = lastLogTime.get()
                if (prevMessage == message && now - prevTime < 1000) {
                    return
                }
                lastLog.set(message)
                lastLogTime.set(now)
                XposedBridge.log("MPM_DEBUG: $message")
            }
        }
    }

    fun dlog(message: String) = logOnce(message)

    private fun resolveQueryBuilderMethod(thisObject: Any) {
        if (isQueryBuilderResolved.get()) return
        synchronized(MediaProviderHooker::class.java) {
            if (isQueryBuilderResolved.get()) return
            val clazz = thisObject.javaClass
            val methods = clazz.declaredMethods.filter { it.name == "getQueryBuilder" }

            val method = methods.find { m ->
                val params = m.parameterTypes
                params.size == 6 && params[2] == Uri::class.java && params[3] == Bundle::class.java
            } ?: methods.find { m ->
                val params = m.parameterTypes
                params.size == 5 && params[2] == Uri::class.java && params[3] == Bundle::class.java
            } ?: methods.find { m ->
                val params = m.parameterTypes
                params.size == 4 && (params[1] == Uri::class.java || params[2] == Uri::class.java)
            }

            method?.isAccessible = true
            queryBuilderMethodInstance = method
            dlog(if (method != null) "Resolved getQueryBuilder: $method" else "Failed to resolve getQueryBuilder")
            isQueryBuilderResolved.set(true)
        }
    }

    fun callGetQueryBuilder(
        thisObject: Any, type: Int, table: Int, uri: Uri, query: Bundle,
        honoredArgs: java.util.function.Consumer<String>
    ): Any? {
        resolveQueryBuilderMethod(thisObject)
        val m = queryBuilderMethodInstance ?: return null
        
        return try {
            val params = m.parameterTypes
            when (params.size) {
                6 -> {
                    val lastParam = if (params[5].name == "java.util.Optional") Optional.empty<Any>() else null
                    m.invoke(thisObject, type, table, uri, query, honoredArgs, lastParam)
                }
                5 -> m.invoke(thisObject, type, table, uri, query, honoredArgs)
                4 -> if (params[1] == Uri::class.java) m.invoke(thisObject, type, uri, table, query)
                     else m.invoke(thisObject, type, table, uri, query)
                else -> null
            }
        } catch (t: Throwable) {
            val cause = if (t is java.lang.reflect.InvocationTargetException) t.targetException else t
            dlog("Error invoking getQueryBuilder: $cause")
            null
        }
    }

    fun callGetQueryBuilderDelete(
        thisObject: Any, type: Int, match: Int, uri: Uri, extras: Bundle
    ): Any? {
        resolveQueryBuilderMethod(thisObject)
        val m = queryBuilderMethodInstance ?: return null
        
        return try {
            val params = m.parameterTypes
            when (params.size) {
                6 -> {
                    val lastParam = if (params[5].name == "java.util.Optional") Optional.empty<Any>() else null
                    m.invoke(thisObject, type, match, uri, extras, null, lastParam)
                }
                5 -> m.invoke(thisObject, type, match, uri, extras, null)
                4 -> if (params[1] == Uri::class.java) m.invoke(thisObject, type, uri, match, null)
                     else m.invoke(thisObject, type, match, uri, null)
                else -> null
            }
        } catch (t: Throwable) {
            val cause = if (t is java.lang.reflect.InvocationTargetException) t.targetException else t
            dlog("Error invoking getQueryBuilder (Delete): $cause")
            null
        }
    }

    fun XC_MethodHook.MethodHookParam.ensureMediaProvider() {
        require(method.declaringClass.name == "com.android.providers.media.MediaProvider")
    }

    val XC_MethodHook.MethodHookParam.isFuseThread: Boolean
        get() = try {
            val fuseDaemonCls = XposedHelpers.findClass(
                "com.android.providers.media.fuse.FuseDaemon", thisObject.javaClass.classLoader
            )
            XposedHelpers.callStaticMethod(fuseDaemonCls, "native_is_fuse_thread") as Boolean
        } catch (e: XposedHelpers.ClassNotFoundError) {
            false
        }

    val XC_MethodHook.MethodHookParam.isSystemCallingPackage: Boolean
        get() {
            val pkg = callingPackage
            return pkg in MediaTables.SYSTEM_CALLING_PACKAGES
        }

    val XC_MethodHook.MethodHookParam.callingPackage: String
        get() {
            ensureMediaProvider()
            val threadLocal =
                XposedHelpers.getObjectField(thisObject, "mCallingIdentity") as ThreadLocal<*>
            return XposedHelpers.callMethod(threadLocal.get(), "getPackageName") as String
        }

    val XC_MethodHook.MethodHookParam.isCallingPackageAllowedHidden: Boolean
        get() {
            ensureMediaProvider()
            return XposedHelpers.callMethod(thisObject, "isCallingPackageAllowedHidden") as Boolean
        }

    fun XC_MethodHook.MethodHookParam.matchUri(uri: Uri, allowHidden: Boolean): Int {
        ensureMediaProvider()
        return XposedHelpers.callMethod(thisObject, "matchUri", uri, allowHidden) as Int
    }
}