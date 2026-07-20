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
)

/** Compiles query templates into predicates that MediaProvider applies before pagination. */
internal object QueryFilter {

    fun build(templates: List<Template>, table: Int): SqlSelection? {
        val capabilities = capabilitiesFor(table) ?: return null
        val clauses = mutableListOf<String>()
        val arguments = mutableListOf<String>()

        templates.forEach { template ->
            val permittedTypes = template.permittedMediaTypes.orEmpty().distinct()
            if (permittedTypes.isNotEmpty()) {
                when {
                    capabilities.hasMediaColumns -> {
                        clauses += "(${FileColumns.MIME_TYPE} IS NULL OR " +
                            "${FileColumns.MEDIA_TYPE} IN (${placeholders(permittedTypes.size)}))"
                        arguments += permittedTypes.map(Int::toString)
                    }

                    capabilities.fixedMediaType !in permittedTypes -> clauses += "0"
                }
            }

            if (capabilities.hasDataColumn) {
                template.filterPath.orEmpty().distinct().forEach { path ->
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
        }

        return clauses.takeIf { it.isNotEmpty() }
            ?.let { SqlSelection(it.joinToString(" AND "), arguments) }
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
        return SqlSelection(clause, existingArguments.orEmpty().toList() + filter.arguments)
    }

    private fun placeholders(count: Int): String = List(count) { "?" }.joinToString(", ")

    private fun escapeLike(value: String): String = buildString(value.length) {
        value.forEach { character ->
            if (character == '\\' || character == '%' || character == '_') append('\\')
            append(character)
        }
    }

    private fun capabilitiesFor(table: Int): TableCapabilities? = when (table) {
        MediaTables.IMAGES_MEDIA,
        MediaTables.IMAGES_MEDIA_ID,
        MediaTables.AUDIO_MEDIA,
        MediaTables.AUDIO_MEDIA_ID,
        MediaTables.AUDIO_GENRES_ID_MEMBERS,
        MediaTables.AUDIO_GENRES_ALL_MEMBERS,
        MediaTables.AUDIO_PLAYLISTS,
        MediaTables.AUDIO_PLAYLISTS_ID,
        MediaTables.AUDIO_PLAYLISTS_ID_MEMBERS,
        MediaTables.AUDIO_PLAYLISTS_ID_MEMBERS_ID,
        MediaTables.VIDEO_MEDIA,
        MediaTables.VIDEO_MEDIA_ID,
        MediaTables.FILES,
        MediaTables.FILES_ID,
        MediaTables.DOWNLOADS,
        MediaTables.DOWNLOADS_ID,
        -> TableCapabilities(hasDataColumn = true, hasMediaColumns = true)

        MediaTables.IMAGES_MEDIA_ID_THUMBNAIL,
        MediaTables.IMAGES_THUMBNAILS,
        MediaTables.IMAGES_THUMBNAILS_ID,
        MediaTables.VIDEO_MEDIA_ID_THUMBNAIL,
        MediaTables.VIDEO_THUMBNAILS,
        MediaTables.VIDEO_THUMBNAILS_ID,
        MediaTables.AUDIO_ALBUMART,
        MediaTables.AUDIO_ALBUMART_ID,
        MediaTables.AUDIO_ALBUMART_FILE_ID,
        -> TableCapabilities(
            hasDataColumn = true,
            hasMediaColumns = false,
            fixedMediaType = MEDIA_TYPE_IMAGE,
        )

        else -> null
    }

    private data class TableCapabilities(
        val hasDataColumn: Boolean,
        val hasMediaColumns: Boolean,
        val fixedMediaType: Int? = null,
    )

    private const val MEDIA_TYPE_IMAGE = 1
}
