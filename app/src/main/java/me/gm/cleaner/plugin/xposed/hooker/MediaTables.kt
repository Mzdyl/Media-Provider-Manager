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

object MediaTables {
    val SYSTEM_CALLING_PACKAGES = setOf(
        "com.android.providers.media",
        "com.android.providers.media.module",
        "com.google.android.providers.media",
        "com.google.android.providers.media.module",
        "com.samsung.android.providers.media"
    )

    const val IMAGES_MEDIA = 1
    const val IMAGES_MEDIA_ID = 2
    const val IMAGES_MEDIA_ID_THUMBNAIL = 3
    const val IMAGES_THUMBNAILS = 4
    const val IMAGES_THUMBNAILS_ID = 5

    const val AUDIO_MEDIA = 100
    const val AUDIO_MEDIA_ID = 101
    const val AUDIO_MEDIA_ID_GENRES = 102
    const val AUDIO_MEDIA_ID_GENRES_ID = 103
    const val AUDIO_GENRES = 106
    const val AUDIO_GENRES_ID = 107
    const val AUDIO_GENRES_ID_MEMBERS = 108
    const val AUDIO_GENRES_ALL_MEMBERS = 109
    const val AUDIO_PLAYLISTS = 110
    const val AUDIO_PLAYLISTS_ID = 111
    const val AUDIO_PLAYLISTS_ID_MEMBERS = 112
    const val AUDIO_PLAYLISTS_ID_MEMBERS_ID = 113
    const val AUDIO_ARTISTS = 114
    const val AUDIO_ARTISTS_ID = 115
    const val AUDIO_ALBUMS = 116
    const val AUDIO_ALBUMS_ID = 117
    const val AUDIO_ARTISTS_ID_ALBUMS = 118
    const val AUDIO_ALBUMART = 119
    const val AUDIO_ALBUMART_ID = 120
    const val AUDIO_ALBUMART_FILE_ID = 121

    const val VIDEO_MEDIA = 200
    const val VIDEO_MEDIA_ID = 201
    const val VIDEO_MEDIA_ID_THUMBNAIL = 202
    const val VIDEO_THUMBNAILS = 203
    const val VIDEO_THUMBNAILS_ID = 204

    const val VOLUMES = 300
    const val VOLUMES_ID = 301

    const val MEDIA_SCANNER = 500

    const val FS_ID = 600
    const val VERSION = 601

    const val FILES = 700
    const val FILES_ID = 701

    const val DOWNLOADS = 800
    const val DOWNLOADS_ID = 801
}
