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

package me.gm.cleaner.plugin.model

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import me.gm.cleaner.plugin.xposed.hooker.InsertHooker
import me.gm.cleaner.plugin.xposed.hooker.QueryHooker
import me.gm.cleaner.plugin.xposed.util.FileUtils
import me.gm.cleaner.plugin.xposed.util.MimeUtils
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

data class Template(
    @field:SerializedName("template_name") val templateName: String,
    @field:SerializedName("hook_operation") val hookOperation: List<String>,
    @field:SerializedName("apply_to_app") val applyToApp: List<String>?,
    @field:SerializedName("permitted_media_types") val permittedMediaTypes: List<Int>?,
    @field:SerializedName("filter_path") val filterPath: List<String>?,
) {
    companion object {
        val GSON: Gson = Gson()
    }
}

class Templates(json: String?) {
    private val _values = mutableListOf<Template>()
    val values: List<Template>
        get() = _values

    // Thread-safe cache for filtered templates by (operation, packageName)
    // Use LRU-style cache to prevent unbounded memory growth
    private val filteredCache = ConcurrentHashMap<String, List<Template>>()
    private val accessOrderQueue = ConcurrentLinkedQueue<String>()
    
    // Maximum cache size to prevent memory leaks
    companion object {
        private const val MAX_CACHE_SIZE = 200
    }

    init {
        if (!json.isNullOrEmpty()) {
            _values.addAll(
                Template.GSON.fromJson(json, Array<Template>::class.java)
            )
        }
    }
    
    /**
     * Clear the cache. Should be called when templates are updated.
     */
    fun clearCache() {
        filteredCache.clear()
        accessOrderQueue.clear()
    }

    fun getFilteredTemplates(cls: Class<*>, packageName: String): List<Template> {
        val operation = when (cls) {
            QueryHooker::class.java -> "query"
            InsertHooker::class.java -> "insert"
            else -> throw IllegalArgumentException()
        }

        val cacheKey = "$operation:$packageName"
        
        // Get or compute value
        val result = filteredCache.getOrPut(cacheKey) {
            _values.filter { template ->
                template.hookOperation.contains(operation) &&
                        template.applyToApp?.contains(packageName) == true
            }
        }
        
        // Update access order for LRU eviction
        accessOrderQueue.remove(cacheKey)
        accessOrderQueue.offer(cacheKey)
        
        // Evict oldest entries if cache exceeds max size
        evictOldestIfNeeded()
        
        return result
    }
    
    /**
     * Evict oldest entries when cache exceeds max size.
     * Uses LRU (Least Recently Used) eviction policy.
     */
    private fun evictOldestIfNeeded() {
        while (filteredCache.size > MAX_CACHE_SIZE) {
            val oldestKey = accessOrderQueue.poll()
            if (oldestKey != null) {
                filteredCache.remove(oldestKey)
            } else {
                break
            }
        }
    }

    fun applyTemplates(
        templates: List<Template>, dataList: List<String>, mimeTypeList: List<String>
    ): List<Boolean> =
        dataList.zip(mimeTypeList).map { (data, mimeType) ->
            templates.any { template ->
                val permittedTypes = template.permittedMediaTypes
                (permittedTypes != null && permittedTypes.isNotEmpty() &&
                        MimeUtils.resolveMediaType(mimeType) !in permittedTypes) ||
                        template.filterPath?.any { FileUtils.contains(it, data) } == true
            }
        }
}
