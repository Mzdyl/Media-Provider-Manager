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

import android.app.RecoverableSecurityException
import android.content.ContentResolver.QUERY_ARG_SQL_SELECTION
import android.content.ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore.Files.FileColumns
import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedInterface.Hooker
import me.gm.cleaner.plugin.xposed.util.Reflection
import me.gm.cleaner.plugin.recording.RecordPolicy
import me.gm.cleaner.plugin.dao.MediaProviderOperation.Companion.OP_DELETE
import me.gm.cleaner.plugin.dao.MediaProviderRecord
import me.gm.cleaner.plugin.util.L
import me.gm.cleaner.plugin.xposed.ManagerService
import me.gm.cleaner.plugin.xposed.util.MimeUtils
import java.io.File

class DeleteHooker(private val service: ManagerService) : Hooker, MediaProviderHooker {
    override fun intercept(chain: Chain): Any? {
        try {
            recordDelete(chain)
        } catch (t: Throwable) {
            L.e("DeleteHooker", "Delete hook failed; allowing original delete", t)
        }
        return chain.proceed()
    }

    private fun recordDelete(param: Chain) {
        if (!service.recordingEnabled || param.isFuseThread || param.isSystemCallingPackage) {
            return
        }
        /** ARGUMENTS */
        val uri = param.args[0] as Uri
        val extras = param.args[1] as? Bundle ?: Bundle.EMPTY
        dlog("deleteInternal called: uri=$uri, callingPackage=${param.callingPackage}")
        val userWhere: String? = try {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> extras?.getString(
                    QUERY_ARG_SQL_SELECTION
                )

                Build.VERSION.SDK_INT == Build.VERSION_CODES.Q -> param.args[1] as? String
                else -> throw UnsupportedOperationException()
            }
        } catch (t: Throwable) {
            dlog("Error getting userWhere: $t")
            null
        }
        val userWhereArgs: Array<String>? = try {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> extras?.getStringArray(
                    QUERY_ARG_SQL_SELECTION_ARGS
                )

                Build.VERSION.SDK_INT == Build.VERSION_CODES.Q ->
                    (param.args[2] as? Array<*>)?.mapNotNull { it as? String }?.toTypedArray()
                else -> throw UnsupportedOperationException()
            }
        } catch (t: Throwable) {
            dlog("Error getting userWhereArgs: $t")
            null
        }

        /** PARSE */
        val match = try {
            param.matchUri(uri, param.isCallingPackageAllowedHidden)
        } catch (t: Throwable) {
            dlog("Error matching URI: $t")
            return
        }
        dlog("Matched table: $match")
        val data = mutableListOf<String>()
        val mimeType = mutableListOf<String>()
        when (match) {
            MediaTables.AUDIO_MEDIA_ID, MediaTables.VIDEO_MEDIA_ID, MediaTables.IMAGES_MEDIA_ID -> {
                try {
                    when {
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> Reflection.callMethod(
                            param.provider, "enforceCallingPermission", uri, extras, true
                        )

                        Build.VERSION.SDK_INT == Build.VERSION_CODES.Q -> Reflection.callMethod(
                            param.provider, "enforceCallingPermission", uri, true
                        )
                    }
                } catch (_: RecoverableSecurityException) {
                    // Let the original operation perform its permission escalation.
                    return
                }

                val qb = callGetQueryBuilderDelete(param.provider, TYPE_DELETE, match, uri, extras)
                if (qb == null) return
                val helper = try {
                    Reflection.callMethod(param.provider, "getDatabaseForUri", uri)
                } catch (t: Throwable) {
                    dlog("Error calling getDatabaseForUri in DeleteHooker: $t")
                    null
                }
                if (helper == null) return
                val projection = arrayOf(
                    FileColumns.MEDIA_TYPE,
                    FileColumns.DATA,
                    FileColumns._ID,
                    FileColumns.IS_DOWNLOAD,
                    FileColumns.MIME_TYPE,
                )

                val c = when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> Reflection.callMethod(
                        qb, "query", helper, projection, userWhere, userWhereArgs,
                        null, null, null, (RecordPolicy.MAX_DETAILS + 1).toString(), null
                    )

                    Build.VERSION.SDK_INT == Build.VERSION_CODES.Q -> Reflection.callMethod(
                        qb, "query", Reflection.callMethod(helper, "getWritableDatabase"),
                        projection, userWhere, userWhereArgs, null, null, null, (RecordPolicy.MAX_DETAILS + 1).toString(), null
                    )

                    else -> throw UnsupportedOperationException()
                } as Cursor
                try {
                    while (data.size <= RecordPolicy.MAX_DETAILS && c.moveToNext()) {
                        data += RecordPolicy.clip(c.getString(1).orEmpty(), RecordPolicy.MAX_PATH_BYTES)
                        mimeType += RecordPolicy.clip(c.getString(4).orEmpty(), RecordPolicy.MAX_MIME_BYTES)
                    }
                } finally {
                    c.close()
                }
            }

            MediaTables.FILES, MediaTables.FILES_ID -> Unit // Selection arguments are not proof of affected paths.
            else -> Unit
        }

        // There is a system confirm dialog before deletion, thus we don't intercept delete operation.

        /** RECORD - use async insert */
        if (service.recordingEnabled) {
            service.insertRecordAsync(
                MediaProviderRecord(
                    0,
                    System.currentTimeMillis(),
                    param.callingPackage,
                    match,
                    OP_DELETE,
                    data,
                    mimeType,
                    MutableList(data.size) { false },
                    sampleKind = if (data.isEmpty()) RecordPolicy.SAMPLE_NONE else RecordPolicy.SAMPLE_DETAILS,
                    // Delete metadata is a bounded pre-operation sample, not a complete result set.
                    detailsTruncated = true,
                )
            )
        }
    }

    private val TYPE_DELETE: Int = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> 3
        Build.VERSION.SDK_INT == Build.VERSION_CODES.Q -> 2
        else -> throw UnsupportedOperationException()
    }
}
