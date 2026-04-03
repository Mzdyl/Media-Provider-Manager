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
import java.util.concurrent.ConcurrentHashMap

/**
 * Unified logging utility for Media Provider Manager.
 * 
 * DEBUG builds: All log levels are enabled with verbose output
 * RELEASE builds: Only error logs are enabled
 * 
 * Features:
 * - Debouncing: Prevents repeated log messages within a time window
 * - Rate limiting: Counts suppressed messages and reports them
 * 
 * Usage:
 *   L.d("Debug message")
 *   L.d(tag, "Debug message")  // with custom tag
 *   L.dlog("Debounced message")  // with debouncing (recommended for hooks)
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
    
    // Debounce settings
    private const val DEFAULT_DEBOUNCE_MS = 1000L
    private const val MAX_CACHE_SIZE = 100
    
    // Debounce cache: message key -> last log time
    private val debounceCache = ConcurrentHashMap<String, Long>()
    // Suppressed count: message key -> count
    private val suppressedCount = ConcurrentHashMap<String, Int>()
    
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
     * Log a debug message with debouncing.
     * Prevents repeated messages within [windowMs] milliseconds.
     * Reports suppressed count when the message is finally logged.
     * 
     * @param message The message to log
     * @param windowMs Time window in milliseconds (default 1000ms)
     */
    @JvmStatic
    @JvmOverloads
    fun dlog(message: String, windowMs: Long = DEFAULT_DEBOUNCE_MS) {
        if (!isLoggable(DEBUG)) return
        
        val now = System.currentTimeMillis()
        val lastTime = debounceCache[message]
        
        if (lastTime != null && now - lastTime < windowMs) {
            // Increment suppressed count
            suppressedCount.merge(message, 1, Int::plus)
            return
        }
        
        // Clean up old entries if cache is too large
        if (debounceCache.size > MAX_CACHE_SIZE) {
            cleanupCache(now)
        }
        
        // Update cache
        debounceCache[message] = now
        
        // Check if there were suppressed messages
        val suppressed = suppressedCount.remove(message) ?: 0
        val finalMessage = if (suppressed > 0) {
            "$message (suppressed $suppressed)"
        } else {
            message
        }
        
        XposedBridge.log("$TAG: $finalMessage")
    }
    
    /**
     * Log a debug message with custom tag and debouncing.
     */
    @JvmStatic
    @JvmOverloads
    fun dlog(tag: String, message: String, windowMs: Long = DEFAULT_DEBOUNCE_MS) {
        if (!isLoggable(DEBUG)) return
        
        val key = "$tag:$message"
        val now = System.currentTimeMillis()
        val lastTime = debounceCache[key]
        
        if (lastTime != null && now - lastTime < windowMs) {
            suppressedCount.merge(key, 1, Int::plus)
            return
        }
        
        if (debounceCache.size > MAX_CACHE_SIZE) {
            cleanupCache(now)
        }
        
        debounceCache[key] = now
        
        val suppressed = suppressedCount.remove(key) ?: 0
        val finalMessage = if (suppressed > 0) {
            "$message (suppressed $suppressed)"
        } else {
            message
        }
        
        XposedBridge.log("$TAG/$tag: $finalMessage")
    }
    
    /**
     * Clean up old entries from the debounce cache.
     */
    private fun cleanupCache(now: Long) {
        val threshold = now - DEFAULT_DEBOUNCE_MS * 10 // Keep entries for 10x the debounce window
        debounceCache.entries.removeIf { it.value < threshold }
        suppressedCount.entries.removeIf { debounceCache[it.key] == null }
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