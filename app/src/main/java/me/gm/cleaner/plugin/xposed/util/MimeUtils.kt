/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.gm.cleaner.plugin.xposed.util

import android.content.ClipDescription
import android.mtp.MtpConstants
import android.provider.MediaStore.Files.FileColumns
import android.webkit.MimeTypeMap
import java.io.File
import java.util.Locale

object MimeUtils {
    /**
     * Variant of [Objects.equals] but which tests with
     * case-insensitivity.
     */
    fun equalIgnoreCase(a: String?, b: String?): Boolean = a?.equals(b, ignoreCase = true) == true

    /**
     * Variant of [String.startsWith] but which tests with
     * case-insensitivity.
     */
    fun startsWithIgnoreCase(target: String?, other: String?): Boolean {
        if (target == null || other == null) return false
        if (other.length > target.length) return false
        return target.regionMatches(0, other, 0, other.length, ignoreCase = true)
    }

    /**
     * Resolve the MIME type of the given file, returning
     * `application/octet-stream` if the type cannot be determined.
     */
    fun resolveMimeType(file: File): String {
        val extension = extractFileExtension(file.path) ?: return ClipDescription.MIMETYPE_UNKNOWN
        val mimeType = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(extension.lowercase(Locale.ROOT))
            ?: return ClipDescription.MIMETYPE_UNKNOWN
        return mimeType
    }

    fun extractFileExtension(data: String?): String? {
        if (data == null) return null
        val lastDot = data.lastIndexOf('.')
        return if (lastDot == -1) null else data.substring(lastDot + 1)
    }

    /**
     * Resolve the [FileColumns.MEDIA_TYPE] of the given MIME type. This
     * carefully checks for more specific types before generic ones, such as
     * treating `audio/mpegurl` as a playlist instead of an audio file.
     */
    fun resolveMediaType(mimeType: String?): Int = when {
        isPlaylistMimeType(mimeType) -> FileColumns.MEDIA_TYPE_PLAYLIST
        isSubtitleMimeType(mimeType) -> FileColumns.MEDIA_TYPE_SUBTITLE
        isAudioMimeType(mimeType) -> FileColumns.MEDIA_TYPE_AUDIO
        isVideoMimeType(mimeType) -> FileColumns.MEDIA_TYPE_VIDEO
        isImageMimeType(mimeType) -> FileColumns.MEDIA_TYPE_IMAGE
        isDocumentMimeType(mimeType) -> FileColumns.MEDIA_TYPE_DOCUMENT
        else -> FileColumns.MEDIA_TYPE_NONE
    }

    /**
     * Resolve the [FileColumns.FORMAT] of the given MIME type. Note that
     * since this column isn't public API, we're okay only getting very rough
     * values in place, and it's not worthwhile to build out complex matching.
     */
    fun resolveFormatCode(mimeType: String?): Int = when (resolveMediaType(mimeType)) {
        FileColumns.MEDIA_TYPE_AUDIO -> MtpConstants.FORMAT_UNDEFINED_AUDIO
        FileColumns.MEDIA_TYPE_VIDEO -> MtpConstants.FORMAT_UNDEFINED_VIDEO
        FileColumns.MEDIA_TYPE_IMAGE -> MtpConstants.FORMAT_DEFINED
        else -> MtpConstants.FORMAT_UNDEFINED
    }

    fun extractPrimaryType(mimeType: String): String {
        val slash = mimeType.indexOf('/')
        require(slash != -1)
        return mimeType.substring(0, slash)
    }

    fun isAudioMimeType(mimeType: String?): Boolean =
        mimeType != null && startsWithIgnoreCase(mimeType, "audio/")

    fun isVideoMimeType(mimeType: String?): Boolean =
        mimeType != null && startsWithIgnoreCase(mimeType, "video/")

    fun isImageMimeType(mimeType: String?): Boolean =
        mimeType != null && startsWithIgnoreCase(mimeType, "image/")

    private val PLAYLIST_MIME_TYPES = setOf(
        "application/vnd.apple.mpegurl",
        "application/vnd.ms-wpl",
        "application/x-extension-smpl",
        "application/x-mpegurl",
        "application/xspf+xml",
        "audio/mpegurl",
        "audio/x-mpegurl",
        "audio/x-scpls"
    )

    fun isPlaylistMimeType(mimeType: String?): Boolean =
        mimeType != null && mimeType.lowercase(Locale.ROOT) in PLAYLIST_MIME_TYPES

    private val SUBTITLE_MIME_TYPES = setOf(
        "application/lrc",
        "application/smil+xml",
        "application/ttml+xml",
        "application/x-extension-cap",
        "application/x-extension-srt",
        "application/x-extension-sub",
        "application/x-extension-vtt",
        "application/x-subrip",
        "text/vtt"
    )

    fun isSubtitleMimeType(mimeType: String?): Boolean =
        mimeType != null && mimeType.lowercase(Locale.ROOT) in SUBTITLE_MIME_TYPES

    private val DOCUMENT_MIME_TYPES = setOf(
        "application/epub+zip",
        "application/msword",
        "application/pdf",
        "application/rtf",
        "application/vnd.ms-excel",
        "application/vnd.ms-excel.addin.macroenabled.12",
        "application/vnd.ms-excel.sheet.binary.macroenabled.12",
        "application/vnd.ms-excel.sheet.macroenabled.12",
        "application/vnd.ms-excel.template.macroenabled.12",
        "application/vnd.ms-powerpoint",
        "application/vnd.ms-powerpoint.addin.macroenabled.12",
        "application/vnd.ms-powerpoint.presentation.macroenabled.12",
        "application/vnd.ms-powerpoint.slideshow.macroenabled.12",
        "application/vnd.ms-powerpoint.template.macroenabled.12",
        "application/vnd.ms-word.document.macroenabled.12",
        "application/vnd.ms-word.template.macroenabled.12",
        "application/vnd.oasis.opendocument.chart",
        "application/vnd.oasis.opendocument.database",
        "application/vnd.oasis.opendocument.formula",
        "application/vnd.oasis.opendocument.graphics",
        "application/vnd.oasis.opendocument.graphics-template",
        "application/vnd.oasis.opendocument.presentation",
        "application/vnd.oasis.opendocument.presentation-template",
        "application/vnd.oasis.opendocument.spreadsheet",
        "application/vnd.oasis.opendocument.spreadsheet-template",
        "application/vnd.oasis.opendocument.text",
        "application/vnd.oasis.opendocument.text-master",
        "application/vnd.oasis.opendocument.text-template",
        "application/vnd.oasis.opendocument.text-web",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "application/vnd.openxmlformats-officedocument.presentationml.slideshow",
        "application/vnd.openxmlformats-officedocument.presentationml.template",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.template",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.template",
        "application/vnd.stardivision.calc",
        "application/vnd.stardivision.chart",
        "application/vnd.stardivision.draw",
        "application/vnd.stardivision.impress",
        "application/vnd.stardivision.impress-packed",
        "application/vnd.stardivision.mail",
        "application/vnd.stardivision.math",
        "application/vnd.stardivision.writer",
        "application/vnd.stardivision.writer-global",
        "application/vnd.sun.xml.calc",
        "application/vnd.sun.xml.calc.template",
        "application/vnd.sun.xml.draw",
        "application/vnd.sun.xml.draw.template",
        "application/vnd.sun.xml.impress",
        "application/vnd.sun.xml.impress.template",
        "application/vnd.sun.xml.math",
        "application/vnd.sun.xml.writer",
        "application/vnd.sun.xml.writer.global",
        "application/vnd.sun.xml.writer.template",
        "application/x-mspublisher"
    )

    fun isDocumentMimeType(mimeType: String?): Boolean {
        if (mimeType == null) return false
        if (startsWithIgnoreCase(mimeType, "text/")) return true
        return mimeType.lowercase(Locale.ROOT) in DOCUMENT_MIME_TYPES
    }
}
