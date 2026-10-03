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
        private val ALL_MEDIA_TYPES = (0..6).toSet()
    }

    init {
        if (!json.isNullOrEmpty()) {
            val parsed = Template.GSON.fromJson(json, Array<Template>::class.java)
                ?: throw IllegalArgumentException("Template document must be a JSON array")
            parsed.forEach { template ->
                require(template.templateName.isNotBlank()) { "Template name must not be blank" }
                require(template.hookOperation.isNotEmpty()) { "Template operation must not be empty" }
                require(template.hookOperation.all { it == "query" || it == "insert" }) {
                    "Unsupported template operation"
                }
                require(template.applyToApp?.none { it.isBlank() } != false) {
                    "Application package name must not be blank"
                }
                require(template.permittedMediaTypes?.all { it in 0..6 } != false) {
                    "Unsupported media type"
                }
                require(template.filterPath?.none { it.isBlank() } != false) {
                    "Filter path must not be blank"
                }
            }
            require(parsed.map { it.templateName }.distinct().size == parsed.size) {
                "Template names must be unique"
            }
            _values.addAll(parsed)
        }
    }
    
    /**
     * Clear the cache. Should be called when templates are updated.
     */
    fun clearCache() {
        filteredCache.clear()
        accessOrderQueue.clear()
    }

    fun getFilteredTemplates(operation: String, packageName: String): List<Template> {
        require(operation == "query" || operation == "insert") { "Unsupported operation: $operation" }

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

    fun shouldIntercept(
        templates: List<Template>,
        data: String?,
        mimeType: String?,
    ): Boolean {
        val permittedTypes = templates.asSequence()
            .map { it.permittedMediaTypes.orEmpty().toSet() }
            .filter { it.isNotEmpty() && it != ALL_MEDIA_TYPES }
            .flatten()
            .toSet()
        val mediaTypeRejected = mimeType != null && permittedTypes.isNotEmpty() &&
            MimeUtils.resolveMediaType(mimeType) !in permittedTypes
        val pathRejected = data != null && templates.asSequence()
            .flatMap { it.filterPath.orEmpty().asSequence() }
            .any { FileUtils.contains(it, data) }
        return mediaTypeRejected || pathRejected
    }
}
