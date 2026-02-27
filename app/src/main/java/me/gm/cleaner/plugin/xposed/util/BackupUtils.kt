/*
 * Copyright 2021 Green Mushroom
 *
 * Licensed under the Apache License, Version 2.0 (theGM/cleaner-plugin);
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

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import me.gm.cleaner.plugin.R
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

object BackupUtils {

    /**
     * Backup rules from the given JSON string to the clipboard.
     */
    fun backupToClipboard(context: Context, json: String?) {
        if (json.isNullOrEmpty()) {
            Toast.makeText(context, R.string.backup_fail_empty, Toast.LENGTH_SHORT).show()
            return
        }
        
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("MediaProviderManagerRules", json)
        clipboard.setPrimaryClip(clip)
        
        Toast.makeText(context, R.string.backup_ok, Toast.LENGTH_SHORT).show()
    }

    /**
     * Validate and return the backup string from the clipboard.
     * Returns null if invalid or empty.
     */
    fun getFromClipboard(context: Context): String? {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).text?.toString()
            if (!text.isNullOrEmpty() && isValidJson(text)) {
                return text
            }
        }
        Toast.makeText(context, R.string.restore_fail_invalid, Toast.LENGTH_SHORT).show()
        return null
    }

    private fun isValidJson(json: String): Boolean {
        return try {
            JSONObject(json)
            true
        } catch (e: JSONException) {
            try {
                JSONArray(json)
                true
            } catch (e2: JSONException) {
                false
            }
        }
    }
}
