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

package me.gm.cleaner.plugin.xposed.hooker

import android.net.Uri
import android.os.Bundle
import io.github.libxposed.api.XposedInterface.Chain
import me.gm.cleaner.plugin.xposed.util.Reflection
import me.gm.cleaner.plugin.util.L
import java.lang.reflect.Method
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

interface MediaProviderHooker {
    companion object {
        private val queryBuilderMethods = ConcurrentHashMap<Class<*>, List<Method>>()
    }

    fun dlog(message: String) = L.dlog(message)

    private fun resolveQueryBuilderMethods(thisObject: Any): List<Method> =
        queryBuilderMethods.getOrPut(thisObject.javaClass) {
            thisObject.javaClass.declaredMethods.filter { method ->
                if (method.name != "getQueryBuilder") return@filter false
                val params = method.parameterTypes
                when (params.size) {
                    5, 6 -> params[0] == Int::class.javaPrimitiveType &&
                        params[1] == Int::class.javaPrimitiveType &&
                        params[2] == Uri::class.java &&
                        params[3] == Bundle::class.java

                    4 -> params[0] == Int::class.javaPrimitiveType &&
                        (params[1] == Uri::class.java || params[2] == Uri::class.java)

                    else -> false
                }
            }.onEach { it.isAccessible = true }
        }

    private fun invokeQueryBuilder(
        thisObject: Any,
        type: Int,
        table: Int,
        uri: Uri,
        query: Bundle,
        honoredArgs: java.util.function.Consumer<String>?,
    ): Any? {
        val methods = resolveQueryBuilderMethods(thisObject)
        for (method in methods) {
            try {
                val params = method.parameterTypes
                return when (params.size) {
                    6 -> method.invoke(
                        thisObject,
                        type,
                        table,
                        uri,
                        query,
                        honoredArgs,
                        defaultArgument(params[5]),
                    )

                    5 -> method.invoke(thisObject, type, table, uri, query, honoredArgs)
                    4 -> if (params[1] == Uri::class.java) {
                        method.invoke(thisObject, type, uri, table, query)
                    } else {
                        method.invoke(thisObject, type, table, uri, query)
                    }

                    else -> null
                }
            } catch (_: IllegalArgumentException) {
                // Try another compatible overload.
            } catch (t: Throwable) {
                val cause = if (t is java.lang.reflect.InvocationTargetException) {
                    t.targetException
                } else {
                    t
                }
                dlog("Error invoking getQueryBuilder: $cause")
                return null
            }
        }
        dlog("Failed to resolve a working getQueryBuilder overload")
        return null
    }

    private fun defaultArgument(type: Class<*>): Any? = when {
        type == Optional::class.java -> Optional.empty<Any>()
        !type.isPrimitive -> null
        type == Boolean::class.javaPrimitiveType -> false
        type == Char::class.javaPrimitiveType -> '\u0000'
        type == Byte::class.javaPrimitiveType -> 0.toByte()
        type == Short::class.javaPrimitiveType -> 0.toShort()
        type == Int::class.javaPrimitiveType -> 0
        type == Long::class.javaPrimitiveType -> 0L
        type == Float::class.javaPrimitiveType -> 0F
        type == Double::class.javaPrimitiveType -> 0.0
        else -> null
    }

    fun callGetQueryBuilder(
        thisObject: Any, type: Int, table: Int, uri: Uri, query: Bundle,
        honoredArgs: java.util.function.Consumer<String>
    ): Any? {
        return invokeQueryBuilder(thisObject, type, table, uri, query, honoredArgs)
    }

    fun callGetQueryBuilderDelete(
        thisObject: Any, type: Int, match: Int, uri: Uri, extras: Bundle
    ): Any? {
        return invokeQueryBuilder(thisObject, type, match, uri, extras, null)
    }

    val Chain.provider: Any
        get() = requireNotNull(thisObject) { "MediaProvider instance is missing" }

    fun Chain.ensureMediaProvider() {
        require(executable.declaringClass.name == "com.android.providers.media.MediaProvider")
    }

    val Chain.isFuseThread: Boolean
        get() = try {
            val fuseDaemonCls = Reflection.findClass(
                "com.android.providers.media.fuse.FuseDaemon", provider.javaClass.classLoader
            )
            Reflection.callStaticMethod(fuseDaemonCls, "native_is_fuse_thread") as Boolean
        } catch (e: ClassNotFoundException) {
            // Android 16+ may have changed FUSE architecture
            // Try to detect via alternative method on MediaProvider itself
            try {
                Reflection.callMethod(provider, "isFuseThread") as Boolean
            } catch (e2: Throwable) {
                // If we cannot determine, default to false to avoid blocking legitimate queries
                // (e.g., the binder query from the client app used to detect module activation)
                dlog("Cannot determine FUSE thread status, assuming NOT FUSE thread: $e2")
                false
            }
        } catch (e: Throwable) {
            dlog("Unexpected error checking FUSE thread: $e")
            false  // Default to false to avoid blocking legitimate queries
        }

    val Chain.isSystemCallingPackage: Boolean
        get() {
            val pkg = callingPackage
            return pkg in MediaTables.SYSTEM_CALLING_PACKAGES
        }

    val Chain.callingPackage: String
        get() {
            ensureMediaProvider()
            return try {
                val threadLocal =
                    Reflection.getObjectField(provider, "mCallingIdentity") as ThreadLocal<*>
                val identity = threadLocal.get()
                if (identity == null) {
                    L.e("QueryHooker", "mCallingIdentity ThreadLocal.get() returned null")
                    ""
                } else {
                    val pkg = Reflection.callMethod(identity, "getPackageName") as String
                    dlog("callingPackage resolved: $pkg")
                    pkg
                }
            } catch (e: NoSuchFieldException) {
                L.e("QueryHooker", "mCallingIdentity field not found on this Android version", e)
                ""
            } catch (e: ClassNotFoundException) {
                L.e("QueryHooker", "mCallingIdentity class not found", e)
                ""
            } catch (e: Throwable) {
                L.e("QueryHooker", "Unexpected error resolving callingPackage", e)
                ""
            }
        }

    val Chain.isCallingPackageAllowedHidden: Boolean
        get() {
            ensureMediaProvider()
            return Reflection.callMethod(provider, "isCallingPackageAllowedHidden") as Boolean
        }

    fun Chain.matchUri(uri: Uri, allowHidden: Boolean): Int {
        ensureMediaProvider()
        return Reflection.callMethod(provider, "matchUri", uri, allowHidden) as Int
    }
}
