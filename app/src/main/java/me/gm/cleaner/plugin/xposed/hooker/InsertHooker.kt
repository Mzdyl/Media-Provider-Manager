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

import android.content.ClipDescription
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.FileUtils
import android.provider.MediaStore
import android.text.TextUtils
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import me.gm.cleaner.plugin.R
import me.gm.cleaner.plugin.dao.MediaProviderOperation.Companion.OP_INSERT
import me.gm.cleaner.plugin.dao.MediaProviderRecord
import me.gm.cleaner.plugin.xposed.ManagerService
import me.gm.cleaner.plugin.xposed.util.MimeUtils
import java.io.File
import java.util.*

class InsertHooker(private val service: ManagerService) : XC_MethodHook(), MediaProviderHooker {
    @Throws(Throwable::class)
    override fun beforeHookedMethod(param: MethodHookParam) {
        if (param.isFuseThread || param.isSystemCallingPackage) {
            return
        }
        /** ARGUMENTS */
        dlog("insertFile called. Args size: ${param.args.size}")
        param.args.forEachIndexed { index, arg ->
            dlog("arg[$index]: ${arg?.javaClass?.name} = $arg")
        }

        val match = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) param.args[2] else param.args[1]
        } catch (t: Throwable) {
            dlog("Error getting match arg: $t")
            null
        } as? Int ?: return

        val uri = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) param.args[3] else param.args[2]
        } catch (t: Throwable) {
            dlog("Error getting uri arg: $t")
            null
        } as? Uri ?: return

        val extras = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) param.args[4] else Bundle.EMPTY
        } catch (t: Throwable) {
            dlog("Error getting extras arg: $t")
            Bundle.EMPTY
        } as Bundle

        val values = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) param.args[5] else param.args[3]
        } catch (t: Throwable) {
            dlog("Error getting values arg: $t")
            null
        } as? ContentValues ?: return

        val mediaType = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) param.args[6] else param.args[4]
        } catch (t: Throwable) {
            dlog("Error getting mediaType arg: $t")
            return
        } as Int

        // Android 16 compatibility: Skip if this is an Android/data directory operation
        // to avoid interfering with app private storage directory creation
        val relativePath = values.getAsString(MediaStore.MediaColumns.RELATIVE_PATH)
        if (isAndroidDataOperation(relativePath)) {
            dlog("Skipping Android/data directory operation: $relativePath")
            return
        }

        /** PARSE */
        var mimeType = values.getAsString(MediaStore.MediaColumns.MIME_TYPE)
        val wasPathEmpty = wasPathEmpty(values)
        if (wasPathEmpty) {
            // Generate path when undefined
            ensureUniqueFileColumns(param.thisObject, match, uri, values, mimeType)
        }
        val data = values.getAsString(MediaStore.MediaColumns.DATA)
        if (mimeType.isNullOrEmpty()) {
            mimeType = values.getAsString(MediaStore.MediaColumns.MIME_TYPE)
            // Restore to support apps not targeting sdk R or higher
            values.remove(MediaStore.MediaColumns.MIME_TYPE)
        }
        if (wasPathEmpty) {
            // Restore to allow mkdir
            values.remove(MediaStore.MediaColumns.DATA)
        }

        /** INTERCEPT */
        val templates = service.ruleSp.templates.getFilteredTemplates(javaClass, param.callingPackage)
        val shouldIntercept = service.ruleSp.templates
            .applyTemplates(templates, listOf(data), listOf(mimeType)).first()
        if (shouldIntercept) {
            param.result = null
        }

        /** RECORD - use async insert */
        if (service.rootSp.getBoolean(
                service.resources.getString(R.string.usage_record_key), true
            )
        ) {
            service.insertRecordAsync(
                MediaProviderRecord(
                    0,
                    System.currentTimeMillis(),
                    param.callingPackage,
                    match,
                    OP_INSERT,
                    listOf(data),
                    listOf(mimeType),
                    listOf(shouldIntercept)
                )
            )
        }
    }

    private fun wasPathEmpty(values: ContentValues) =
        !values.containsKey(MediaStore.MediaColumns.DATA)
                || values.getAsString(MediaStore.MediaColumns.DATA).isEmpty()

    /**
     * Check if the operation targets Android/data or Android/obb directories.
     * These are app-private storage directories that should not be intercepted
     * to avoid breaking app storage access on Android 16+.
     */
    private fun isAndroidDataOperation(relativePath: String?): Boolean {
        if (relativePath.isNullOrEmpty()) return false
        val normalizedPath = relativePath.lowercase().trimEnd('/')
        return normalizedPath.startsWith("android/data/") ||
               normalizedPath.startsWith("android/obb/") ||
               normalizedPath == "android/data" ||
               normalizedPath == "android/obb"
    }

    private fun ensureUniqueFileColumns(
        thisObject: Any, match: Int, uri: Uri, values: ContentValues, mimeType: String?
    ) {
        var defaultMimeType = ClipDescription.MIMETYPE_UNKNOWN
        var defaultPrimary = Environment.DIRECTORY_DOWNLOADS
        var defaultSecondary: String? = null
        when (match) {
            MediaTables.AUDIO_MEDIA, MediaTables.AUDIO_MEDIA_ID -> {
                defaultMimeType = "audio/mpeg"
                defaultPrimary = Environment.DIRECTORY_MUSIC
            }

            MediaTables.VIDEO_MEDIA, MediaTables.VIDEO_MEDIA_ID -> {
                defaultMimeType = "video/mp4"
                defaultPrimary = Environment.DIRECTORY_MOVIES
            }

            MediaTables.IMAGES_MEDIA, MediaTables.IMAGES_MEDIA_ID -> {
                defaultMimeType = "image/jpeg"
                defaultPrimary = Environment.DIRECTORY_PICTURES
            }

            MediaTables.AUDIO_ALBUMART, MediaTables.AUDIO_ALBUMART_ID -> {
                defaultMimeType = "image/jpeg"
                defaultPrimary = Environment.DIRECTORY_MUSIC
                defaultSecondary = DIRECTORY_THUMBNAILS
            }

            MediaTables.VIDEO_THUMBNAILS, MediaTables.VIDEO_THUMBNAILS_ID -> {
                defaultMimeType = "image/jpeg"
                defaultPrimary = Environment.DIRECTORY_MOVIES
                defaultSecondary = DIRECTORY_THUMBNAILS
            }

            MediaTables.IMAGES_THUMBNAILS, MediaTables.IMAGES_THUMBNAILS_ID -> {
                defaultMimeType = "image/jpeg"
                defaultPrimary = Environment.DIRECTORY_PICTURES
                defaultSecondary = DIRECTORY_THUMBNAILS
            }

            MediaTables.AUDIO_PLAYLISTS, MediaTables.AUDIO_PLAYLISTS_ID -> {
                defaultMimeType = "audio/mpegurl"
                defaultPrimary = Environment.DIRECTORY_MUSIC
            }

            MediaTables.DOWNLOADS, MediaTables.DOWNLOADS_ID -> {
                defaultPrimary = Environment.DIRECTORY_DOWNLOADS
            }
        }
        // Give ourselves reasonable defaults when missing
        if (TextUtils.isEmpty(values.getAsString(MediaStore.MediaColumns.DISPLAY_NAME))) {
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, System.currentTimeMillis().toString())
        }
        // Use default directories when missing
        if (TextUtils.isEmpty(values.getAsString(MediaStore.MediaColumns.RELATIVE_PATH))) {
            if (defaultSecondary != null) {
                values.put(
                    MediaStore.MediaColumns.RELATIVE_PATH, "$defaultPrimary/$defaultSecondary/"
                )
            } else {
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, "$defaultPrimary/")
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val resolvedVolumeName = XposedHelpers.callMethod(
                    thisObject, "resolveVolumeName", uri
                ) as String
                val volumePath = XposedHelpers.callMethod(
                    thisObject, "getVolumePath", resolvedVolumeName
                ) as File

                val fileUtilsClass = XposedHelpers.findClass(
                    "com.android.providers.media.util.FileUtils", service.classLoader
                )
                val isFuseThread = XposedHelpers.callMethod(thisObject, "isFuseThread")
                        as Boolean
                XposedHelpers.callStaticMethod(
                    fileUtilsClass, "sanitizeValues", values, !isFuseThread
                )
                XposedHelpers.callStaticMethod(
                    fileUtilsClass, "computeDataFromValues", values, volumePath, isFuseThread
                )

                var res = File(values.getAsString(MediaStore.MediaColumns.DATA))
                res = XposedHelpers.callStaticMethod(
                    fileUtilsClass, "buildUniqueFile", res.parentFile, mimeType, res.name
                ) as File

                values.put(MediaStore.MediaColumns.DATA, res.absolutePath)
            } catch (t: Throwable) {
                // Android 16 compatibility: If internal methods fail or behave differently,
                // return early to let the original implementation handle it
                dlog("ensureUniqueFileColumns failed, letting original implementation handle: $t")
                return
            }
        } else {
            val resolvedVolumeName = XposedHelpers.callMethod(
                thisObject, "resolveVolumeName", uri
            ) as String

            val relativePath = XposedHelpers.callMethod(
                thisObject, "sanitizePath",
                values.getAsString(MediaStore.MediaColumns.RELATIVE_PATH)
            )
            val displayName = XposedHelpers.callMethod(
                thisObject, "sanitizeDisplayName",
                values.getAsString(MediaStore.MediaColumns.DISPLAY_NAME)
            )

            var res = XposedHelpers.callMethod(
                thisObject, "getVolumePath", resolvedVolumeName
            ) as File
            res = XposedHelpers.callStaticMethod(
                Environment::class.java, "buildPath", res, relativePath
            ) as File
            res = XposedHelpers.callStaticMethod(
                FileUtils::class.java, "buildUniqueFile", res, mimeType, displayName
            ) as File

            values.put(MediaStore.MediaColumns.DATA, res.absolutePath)
        }

        val displayName = values.getAsString(MediaStore.MediaColumns.DISPLAY_NAME)
        val mimeTypeFromExt = if (TextUtils.isEmpty(displayName)) null
        else MimeUtils.resolveMimeType(File(displayName))
        if (TextUtils.isEmpty(values.getAsString(MediaStore.MediaColumns.MIME_TYPE))) {
            // Extract the MIME type from the display name if we couldn't resolve it from the
            // raw path
            if (mimeTypeFromExt != null) {
                values.put(MediaStore.MediaColumns.MIME_TYPE, mimeTypeFromExt)
            } else {
                // We couldn't resolve mimeType, it means that both display name and MIME type
                // were missing in values, so we use defaultMimeType.
                values.put(MediaStore.MediaColumns.MIME_TYPE, defaultMimeType)
            }
        }
    }

    companion object {
        private const val DIRECTORY_THUMBNAILS = ".thumbnails"
    }
}
