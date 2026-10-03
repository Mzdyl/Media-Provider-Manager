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

import android.util.AtomicFile
import me.gm.cleaner.plugin.dao.JsonSharedPreferencesImpl
import me.gm.cleaner.plugin.dao.SharedPreferencesWrapper
import me.gm.cleaner.plugin.util.L
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.charset.StandardCharsets

open class JsonFileSpImpl(src: File) : SharedPreferencesWrapper() {
    val file: File = src
    private val atomicFile = AtomicFile(file)

    @Volatile
    protected var contentCache: String? = null

    init {
        delegate = parseDelegate(read())
    }

    protected open fun validateContent(what: String) {
        // Legacy installs can have an empty file, which read() treats as defaults.
        // Accept that same representation when restoring settings through Binder.
        if (what.isNotEmpty()) JSONObject(what)
    }

    private fun parseDelegate(content: String?): JsonSharedPreferencesImpl {
        if (content?.trimStart()?.startsWith("[") == true) {
            return JsonSharedPreferencesImpl()
        }
        return try {
            JsonSharedPreferencesImpl(
                if (content.isNullOrEmpty()) JSONObject() else JSONObject(content),
            )
        } catch (e: JSONException) {
            L.e("Invalid preferences JSON in ${file.path}; using defaults", e)
            JsonSharedPreferencesImpl()
        }
    }

    @Synchronized
    fun read(): String? {
        contentCache?.let { return it }
        contentCache = try {
            atomicFile.openRead().bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        } catch (_: FileNotFoundException) {
            ""
        } catch (e: IOException) {
            L.e("Failed to read ${file.path}", e)
            ""
        }
        return contentCache
    }

    @Synchronized
    open fun write(what: String) {
        validateContent(what)

        var output = atomicFile.startWrite()
        try {
            output.write(what.toByteArray(StandardCharsets.UTF_8))
            atomicFile.finishWrite(output)
            output = null
        } catch (t: Throwable) {
            output?.let { atomicFile.failWrite(it) }
            throw IOException("Failed to atomically write ${file.path}", t)
        }

        contentCache = what
        delegate = parseDelegate(what)
    }
}
