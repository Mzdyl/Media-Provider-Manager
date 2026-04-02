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

import android.text.TextUtils
import de.robv.android.xposed.XposedBridge
import me.gm.cleaner.plugin.dao.JsonSharedPreferencesImpl
import me.gm.cleaner.plugin.dao.SharedPreferencesWrapper
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

open class JsonFileSpImpl(src: File) : SharedPreferencesWrapper() {
    val file: File = src
    protected var contentCache: String? = null

    init {
        val json = try {
            val str = read()
            if (TextUtils.isEmpty(str)) JSONObject() else JSONObject(str)
        } catch (e: JSONException) {
            JSONObject()
        }
        delegate = JsonSharedPreferencesImpl(json)
    }

    private fun ensureFile() {
        if (!file.exists()) {
            try {
                file.createNewFile()
            } catch (e: IOException) {
                XposedBridge.log(e)
                throw RuntimeException(e)
            }
        }
    }

    @Synchronized
    fun read(): String? {
        if (contentCache == null) {
            ensureFile()
            try {
                FileInputStream(file).use {
                    val bb = ByteBuffer.allocate(it.available())
                    it.channel.read(bb)
                    contentCache = String(bb.array())
                }
            } catch (e: IOException) {
                XposedBridge.log(e)
            }
        }
        return contentCache
    }

    @Synchronized
    open fun write(what: String) {
        contentCache = what
        try {
            delegate = JsonSharedPreferencesImpl(JSONObject(what))
        } catch (_: JSONException) {}

        ensureFile()
        val tempFile = File(file.path + ".bak")
        try {
            FileOutputStream(tempFile).use { fos ->
                fos.write(what.toByteArray(StandardCharsets.UTF_8))
                fos.fd.sync() // 强制同步到硬件磁盘
            }
        } catch (e: IOException) {
            XposedBridge.log("Failed to write rules atomically: $e")
            // 备份方案：如果原子写入失败，尝试直接写入（虽然不安全，但在极端文件权限下可能是唯一出路）
            try {
                FileOutputStream(file).use { fos ->
                    fos.write(what.toByteArray(StandardCharsets.UTF_8))
                }
            } catch (ex: IOException) {
                XposedBridge.log("Critical failure writing rules: $ex")
            }
            return
        }

        // 原子替换
        if (!tempFile.renameTo(file)) {
            if (!file.delete() || !tempFile.renameTo(file)) {
                XposedBridge.log("Failed to rename temporary file to $file")
            }
        }
    }
}
