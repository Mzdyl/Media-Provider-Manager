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

package me.gm.cleaner.plugin.xposed.util

import android.os.Environment
import java.io.File

object FileUtils {
    fun contains(parent: File, child: File): Boolean = contains(parent.path, child.path)
    fun contains(parent: String, child: File): Boolean = contains(parent, child.path)
    fun contains(parent: File, child: String): Boolean = contains(parent.path, child)
    fun contains(parent: String, child: String): Boolean {
        val normalizedParent = normalizePath(parent)
        val normalizedChild = normalizePath(child)
        if (normalizedChild.equals(normalizedParent, ignoreCase = true)) return true
        if (normalizedParent == File.separator) {
            return normalizedChild.startsWith(File.separator)
        }
        return normalizedChild.startsWith(
            normalizedParent + File.separator,
            ignoreCase = true,
        )
    }

    internal fun normalizePath(path: String): String {
        val normalized = File(path).normalize().path
        return normalized.trimEnd(File.separatorChar).ifEmpty { File.separator }
    }

    val externalStorageDirPath: String by lazy { Environment.getExternalStorageDirectory().path }
    val androidDir: File by lazy { File(externalStorageDirPath, "Android") }
    val standardDirs: Array<String> = arrayOf(
        "Alarms",
        "Audiobooks",
        "DCIM",
        "Documents",
        "Download",
        "Movies",
        "Music",
        "Notifications",
        "Pictures",
        "Podcasts",
        "Ringtones",
        "Recordings",
    )
}
