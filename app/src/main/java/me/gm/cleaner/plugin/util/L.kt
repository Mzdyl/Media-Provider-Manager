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

package me.gm.cleaner.plugin.util

import de.robv.android.xposed.XposedBridge
import me.gm.cleaner.plugin.BuildConfig
import java.util.concurrent.atomic.AtomicReference

/**
 * Unified logging utility for Media Provider Manager.
 * 
 * DEBUG builds: All log levels are enabled with verbose output
 * RELEASE builds: Only error logs are enabled
 * 
 * Usage:
 *   L.d("Debug message")
 *   L.i("Info message")
 *   L.w("Warning message")
 *   L.e("Error message")
 *   L.e("Error with exception", throwable)
 */
object L {
    private const val TAG = "MPM"
    
    // Log level constants
    const val VERBOSE = 0
    const val DEBUG = 1
    const val INFO = 2
    const val WARNING = 3
    const val ERROR = 4
    const val NONE = 5
    
    // Minimum log level based on build type
    private val minLogLevel: Int = if (BuildConfig.DEBUG) VERBOSE else ERROR
    
    // For deduplication
    private val lastLog = AtomicReference<String?>(null)
    private val lastLogTime = AtomicReference<Long>(0L)
    
    /**
     * Check if debug logging is enabled
     */
    @JvmStatic
    val isDebug: Boolean
        get() = BuildConfig.DEBUG
    
    /**
     * Check if a specific log level is enabled
     */
    @JvmStatic
    fun isLoggable(level: Int): Boolean = level >= minLogLevel
    
    /**
     * Log a debug message. Only outputs in DEBUG builds.
     */
    @JvmStatic
    fun d(message: String) {
        if (isLoggable(DEBUG)) {
            XposedBridge.log("$TAG: $message")
        }
    }
    
    /**
     * Log a debug message with a custom tag. Only outputs in DEBUG builds.
     */
    @JvmStatic
    fun d(tag: String, message: String) {
        if (isLoggable(DEBUG)) {
            XposedBridge.log("$TAG/$tag: $message")
        }
    }
    
    /**
     * Log an info message.
     */
    @JvmStatic
    fun i(message: String) {
        if (isLoggable(INFO)) {
            XposedBridge.log("$TAG: $message")
        }
    }
    
    /**
     * Log an info message with a custom tag.
     */
    @JvmStatic
    fun i(tag: String, message: String) {
        if (isLoggable(INFO)) {
            XposedBridge.log("$TAG/$tag: $message")
        }
    }
    
    /**
     * Log a warning message.
     */
    @JvmStatic
    fun w(message: String) {
        if (isLoggable(WARNING)) {
            XposedBridge.log("$TAG: $message")
        }
    }
    
    /**
     * Log a warning message with a custom tag.
     */
    @JvmStatic
    fun w(tag: String, message: String) {
        if (isLoggable(WARNING)) {
            XposedBridge.log("$TAG/$tag: $message")
        }
    }
    
    /**
     * Log an error message.
     */
    @JvmStatic
    fun e(message: String) {
        if (isLoggable(ERROR)) {
            XposedBridge.log("$TAG: $message")
        }
    }
    
    /**
     * Log an error message with a custom tag.
     */
    @JvmStatic
    fun e(tag: String, message: String) {
        if (isLoggable(ERROR)) {
            XposedBridge.log("$TAG/$tag: $message")
        }
    }
    
    /**
     * Log an error message with an exception.
     */
    @JvmStatic
    fun e(message: String, throwable: Throwable) {
        if (isLoggable(ERROR)) {
            XposedBridge.log("$TAG: $message")
            XposedBridge.log(throwable)
        }
    }
    
    /**
     * Log an error message with a custom tag and exception.
     */
    @JvmStatic
    fun e(tag: String, message: String, throwable: Throwable) {
        if (isLoggable(ERROR)) {
            XposedBridge.log("$TAG/$tag: $message")
            XposedBridge.log(throwable)
        }
    }
    
    /**
     * Log a verbose message. Only outputs in DEBUG builds.
     * Use for very detailed debugging output.
     */
    @JvmStatic
    fun v(message: String) {
        if (isLoggable(VERBOSE)) {
            XposedBridge.log("$TAG: $message")
        }
    }
    
    /**
     * Log a verbose message with a custom tag. Only outputs in DEBUG builds.
     */
    @JvmStatic
    fun v(tag: String, message: String) {
        if (isLoggable(VERBOSE)) {
            XposedBridge.log("$TAG/$tag: $message")
        }
    }
    
    /**
     * Log a message only once within a time window (1 second by default).
     * Useful for preventing log spam from repeated operations.
     */
    @JvmStatic
    fun logOnce(message: String, windowMs: Long = 1000) {
        if (!isLoggable(DEBUG)) return
        
        val now = System.currentTimeMillis()
        val prevMessage = lastLog.get()
        val prevTime = lastLogTime.get()
        
        if (prevMessage == message && now - prevTime < windowMs) {
            return
        }
        
        lastLog.set(message)
        lastLogTime.set(now)
        XposedBridge.log("$TAG: $message")
    }
    
    /**
     * Log method entry with parameters. Only in DEBUG builds.
     * Useful for tracing method calls.
     */
    @JvmStatic
    fun enter(method: String, vararg args: Any?) {
        if (isLoggable(VERBOSE)) {
            val argsStr = if (args.isEmpty()) "" else args.joinToString(", ") { 
                it?.let { "${it.javaClass.simpleName}=$it" } ?: "null"
            }
            XposedBridge.log("$TAG: → $method($argsStr)")
        }
    }
    
    /**
     * Log method exit with result. Only in DEBUG builds.
     */
    @JvmStatic
    fun exit(method: String, result: Any? = null) {
        if (isLoggable(VERBOSE)) {
            val resultStr = result?.let { "${it.javaClass.simpleName}=$it" } ?: "void"
            XposedBridge.log("$TAG: ← $method = $resultStr")
        }
    }
    
    /**
     * Log a method dump header for debugging reflection operations.
     */
    @JvmStatic
    fun dumpHeader(title: String) {
        if (isLoggable(DEBUG)) {
            XposedBridge.log("$TAG: ${"*".repeat(10)} $title ${"*".repeat(10)}")
        }
    }
    
    /**
     * Log a method dump footer.
     */
    @JvmStatic
    fun dumpFooter() {
        if (isLoggable(DEBUG)) {
            XposedBridge.log("$TAG: ${"*".repeat(30)}")
        }
    }
}
