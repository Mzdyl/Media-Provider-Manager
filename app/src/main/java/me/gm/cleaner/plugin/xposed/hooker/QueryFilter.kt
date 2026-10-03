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

import android.provider.MediaStore.Files.FileColumns
import me.gm.cleaner.plugin.model.Template
import me.gm.cleaner.plugin.xposed.util.FileUtils

internal data class SqlSelection(
    val clause: String,
    val arguments: List<String>,
    val filtersMediaTypes: Boolean = true,
)

/** Compiles query templates into predicates that MediaProvider applies before pagination. */
internal object QueryFilter {

    fun build(templates: List<Template>, table: Int): SqlSelection? {
        val capabilities = capabilitiesFor(table) ?: return null
        val clauses = mutableListOf<String>()
        val arguments = mutableListOf<String>()
        var filtersMediaTypes = true

        val permittedTypes = templates.asSequence()
            .map { it.permittedMediaTypes.orEmpty().toSet() }
            .filter { it.isNotEmpty() && it != ALL_MEDIA_TYPES }
            .flatten()
            .toSortedSet()
        if (permittedTypes.isNotEmpty()) {
            when (capabilities.mediaTypeStrategy) {
                MediaTypeStrategy.COLUMN -> {
                    clauses += "(${FileColumns.MIME_TYPE} IS NULL OR " +
                        "${FileColumns.MEDIA_TYPE} IN (${placeholders(permittedTypes.size)}))"
                    arguments += permittedTypes.map(Int::toString)
                }

                MediaTypeStrategy.FIXED -> if (capabilities.fixedMediaType !in permittedTypes) {
                    clauses += "0"
                }

                MediaTypeStrategy.UNSUPPORTED -> filtersMediaTypes = false
            }
        }

        if (capabilities.hasDataColumn) {
            templates.asSequence()
                .flatMap { it.filterPath.orEmpty().asSequence() }
                .distinct()
                .forEach { path ->
                    val normalizedPath = FileUtils.normalizePath(path)
                    clauses += "(${FileColumns.DATA} IS NULL OR " +
                        "(LOWER(${FileColumns.DATA}) != LOWER(?) AND " +
                        "LOWER(${FileColumns.DATA}) NOT LIKE LOWER(?) ESCAPE '\\'))"
                    arguments += normalizedPath
                    arguments += if (normalizedPath == "/") {
                        "/%"
                    } else {
                        escapeLike(normalizedPath) + "/%"
                    }
                }
        }

        return clauses.takeIf { it.isNotEmpty() }
            ?.let { SqlSelection(it.joinToString(" AND "), arguments, filtersMediaTypes) }
    }

    fun merge(
        existingClause: String?,
        existingArguments: Array<String>?,
        filter: SqlSelection,
    ): SqlSelection {
        val clause = if (existingClause.isNullOrBlank()) {
            filter.clause
        } else {
            "($existingClause) AND (${filter.clause})"
        }
        return SqlSelection(
            clause,
            existingArguments.orEmpty().toList() + filter.arguments,
            filter.filtersMediaTypes,
        )
    }

    fun needsMediaTypeColumn(table: Int): Boolean = capabilitiesFor(table)?.mediaTypeStrategy == MediaTypeStrategy.COLUMN

    /** Mirrors the SQL predicate for the diagnostic sample; never infer types from a filename. */
    fun rejectsSample(templates: List<Template>, table: Int, data: String?, mimeType: String?, mediaType: Int?): Boolean {
        val capabilities = capabilitiesFor(table) ?: return false
        val permitted = templates.map { it.permittedMediaTypes.orEmpty().toSet() }
            .filter { it.isNotEmpty() && it != ALL_MEDIA_TYPES }.flatten().toSet()
        val typeRejected = permitted.isNotEmpty() && when (capabilities.mediaTypeStrategy) {
            MediaTypeStrategy.COLUMN -> mimeType != null && mediaType !in permitted
            MediaTypeStrategy.FIXED -> capabilities.fixedMediaType !in permitted
            MediaTypeStrategy.UNSUPPORTED -> false
        }
        val pathRejected = capabilities.hasDataColumn && data != null && templates.any { template ->
            template.filterPath.orEmpty().any { parent ->
                val normalized = asciiLower(FileUtils.normalizePath(parent))
                val child = asciiLower(data)
                child == normalized || child.startsWith(if (normalized == "/") "/" else "$normalized/")
            }
        }
        return typeRejected || pathRejected
    }

    private fun asciiLower(value: String): String = buildString(value.length) {
        value.forEach { append(if (it in 'A'..'Z') it.lowercaseChar() else it) }
    }

    private fun placeholders(count: Int): String = List(count) { "?" }.joinToString(", ")

    private fun escapeLike(value: String): String = buildString(value.length) {
        value.forEach { character ->
            if (character == '\\' || character == '%' || character == '_') append('\\')
            append(character)
        }
    }

    private fun capabilitiesFor(table: Int): TableCapabilities? = when (table) {
        MediaTables.FILES,
        MediaTables.FILES_ID,
        -> TableCapabilities(
            hasDataColumn = true,
            mediaTypeStrategy = MediaTypeStrategy.COLUMN,
        )

        MediaTables.IMAGES_MEDIA,
        MediaTables.IMAGES_MEDIA_ID,
        -> TableCapabilities.fixed(MEDIA_TYPE_IMAGE)

        MediaTables.AUDIO_MEDIA,
        MediaTables.AUDIO_MEDIA_ID,
        MediaTables.AUDIO_GENRES_ID_MEMBERS,
        MediaTables.AUDIO_GENRES_ALL_MEMBERS,
        MediaTables.AUDIO_PLAYLISTS_ID_MEMBERS,
        MediaTables.AUDIO_PLAYLISTS_ID_MEMBERS_ID,
        -> TableCapabilities.fixed(MEDIA_TYPE_AUDIO)

        MediaTables.AUDIO_PLAYLISTS,
        MediaTables.AUDIO_PLAYLISTS_ID,
        -> TableCapabilities.fixed(MEDIA_TYPE_PLAYLIST)

        MediaTables.VIDEO_MEDIA,
        MediaTables.VIDEO_MEDIA_ID,
        -> TableCapabilities.fixed(MEDIA_TYPE_VIDEO)

        MediaTables.DOWNLOADS,
        MediaTables.DOWNLOADS_ID,
        -> TableCapabilities(
            hasDataColumn = true,
            mediaTypeStrategy = MediaTypeStrategy.UNSUPPORTED,
        )

        MediaTables.IMAGES_MEDIA_ID_THUMBNAIL,
        MediaTables.IMAGES_THUMBNAILS,
        MediaTables.IMAGES_THUMBNAILS_ID,
        MediaTables.VIDEO_MEDIA_ID_THUMBNAIL,
        MediaTables.VIDEO_THUMBNAILS,
        MediaTables.VIDEO_THUMBNAILS_ID,
        MediaTables.AUDIO_ALBUMART,
        MediaTables.AUDIO_ALBUMART_ID,
        MediaTables.AUDIO_ALBUMART_FILE_ID,
        -> TableCapabilities.fixed(MEDIA_TYPE_IMAGE)

        else -> null
    }

    private data class TableCapabilities(
        val hasDataColumn: Boolean,
        val mediaTypeStrategy: MediaTypeStrategy,
        val fixedMediaType: Int? = null,
    ) {
        companion object {
            fun fixed(mediaType: Int) = TableCapabilities(
                hasDataColumn = true,
                mediaTypeStrategy = MediaTypeStrategy.FIXED,
                fixedMediaType = mediaType,
            )
        }
    }

    private enum class MediaTypeStrategy {
        COLUMN,
        FIXED,
        UNSUPPORTED,
    }

    private val ALL_MEDIA_TYPES = (0..6).toSortedSet()
    private const val MEDIA_TYPE_IMAGE = 1
    private const val MEDIA_TYPE_AUDIO = 2
    private const val MEDIA_TYPE_VIDEO = 3
    private const val MEDIA_TYPE_PLAYLIST = 4
}
