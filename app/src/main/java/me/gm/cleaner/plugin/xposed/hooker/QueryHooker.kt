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

import android.content.ContentResolver
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.provider.MediaStore
import android.provider.MediaStore.Files.FileColumns
import androidx.core.os.bundleOf
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedInterface.Hooker
import me.gm.cleaner.plugin.xposed.util.Reflection
import me.gm.cleaner.plugin.BuildConfig
import me.gm.cleaner.plugin.R
import me.gm.cleaner.plugin.dao.MediaProviderOperation.Companion.OP_QUERY
import me.gm.cleaner.plugin.dao.MediaProviderRecord
import me.gm.cleaner.plugin.model.Template
import me.gm.cleaner.plugin.util.L
import me.gm.cleaner.plugin.xposed.ManagerService
import me.gm.cleaner.plugin.xposed.util.MimeUtils
import java.io.File
import java.util.function.Consumer
import java.util.function.Function

class QueryHooker(
    private val service: ManagerService,
    private val framework: XposedInterface,
) : Hooker, MediaProviderHooker {

    override fun intercept(chain: Chain): Any? {
        val prepared = try {
            prepareQuery(chain)
        } catch (t: Throwable) {
            L.e("QueryHooker", "Failed to prepare query hook; allowing original query", t)
            null
        }
        if (prepared is ClientQuery) return prepared.cursor
        val state = prepared as? QueryState ?: return chain.proceed()
        val outcome = QueryCall.execute(framework, chain, state.arguments, state.sqlFilterApplied)
        val result = outcome.value
        outcome.filterFailure?.let { failure ->
            L.e(
                "QueryHooker",
                "MediaProvider rejected query filter for ${state.callingPackage} " +
                    "at ${state.uri} (table=${state.table}); returned the unfiltered query instead",
                failure,
            )
            return result
        }
        if (result is Cursor) {
            try {
                handleQueryResult(chain, state)
            } catch (t: Throwable) {
                L.e("QueryHooker", "Failed to record query result; returning original cursor", t)
            }
        }
        return result
    }

    private fun prepareQuery(param: Chain): PreparedQuery? {
        if (param.args.size < 4 || param.isFuseThread || param.isSystemCallingPackage) return null

        val uri = param.args[0] as? Uri ?: return null
        val projection = (param.args[1] as? Array<*>)
            ?.mapNotNull { it as? String }
            ?.toTypedArray()
        val queryArgs = param.args[2] as? Bundle ?: Bundle.EMPTY
        val signal = param.args[3] as? CancellationSignal
        val callingPackage = param.callingPackage
        if (callingPackage.isEmpty()) return null

        val query = resolveQueryArgs(param, uri, queryArgs)
        if (isClientQuery(callingPackage, uri)) {
            return ClientQuery(handleClientQuery(projection, query))
        }

        val table = try {
            param.matchUri(uri, param.isCallingPackageAllowedHidden)
        } catch (t: Throwable) {
            dlog("Skipping query hook because matchUri failed for $uri: $t")
            return null
        }
        val templates = service.ruleSp.templates.getFilteredTemplates("query", callingPackage)
        val shouldRecord = service.rootSp.getBoolean(
            service.resources.getString(R.string.usage_record_key),
            true,
        )
        if (templates.isEmpty() && !shouldRecord) return null

        val sqlFilter = QueryFilter.build(templates, table)
        val filteredQueryArgs = sqlFilter?.let {
            val filteredQueryArgs = Bundle(queryArgs)
            val mergedSelection = QueryFilter.merge(
                filteredQueryArgs.getString(ContentResolver.QUERY_ARG_SQL_SELECTION),
                filteredQueryArgs.getStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS),
                it,
            )
            filteredQueryArgs.putString(
                ContentResolver.QUERY_ARG_SQL_SELECTION,
                mergedSelection.clause,
            )
            filteredQueryArgs.putStringArray(
                ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
                mergedSelection.arguments.toTypedArray(),
            )
            filteredQueryArgs
        }

        val arguments = param.args.toTypedArray()
        if (filteredQueryArgs != null) arguments[2] = filteredQueryArgs
        return QueryState(
            uri = uri,
            query = query,
            signal = signal,
            callingPackage = callingPackage,
            table = table,
            templates = templates,
            shouldRecord = shouldRecord,
            arguments = arguments,
            sqlFilterApplied = sqlFilter != null,
            mediaTypeFilterApplied = sqlFilter?.filtersMediaTypes == true,
        )
    }

    private fun resolveQueryArgs(
        param: Chain,
        uri: Uri,
        queryArgs: Bundle,
    ): Bundle {
        val query = Bundle(queryArgs).apply { remove(INCLUDED_DEFAULT_DIRECTORIES) }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return query

        val honoredArgs = Consumer<String> { }
        try {
            val databaseUtilsClass = Reflection.findClass(
                "com.android.providers.media.util.DatabaseUtils",
                service.classLoader,
            )
            Reflection.callStaticMethod(
                databaseUtilsClass,
                "resolveQueryArgs",
                query,
                honoredArgs,
                Function<String, String> { value ->
                    Reflection.callMethod(
                        param.provider,
                        "ensureCustomCollator",
                        value,
                    ) as String
                },
            )

            val targetSdkVersion = Reflection.callMethod(
                param.provider,
                "getCallingPackageTargetSdkVersion",
            ) as Int
            if (targetSdkVersion < Build.VERSION_CODES.R) {
                Reflection.callStaticMethod(databaseUtilsClass, "recoverAbusiveSortOrder", query)
                Reflection.callStaticMethod(databaseUtilsClass, "recoverAbusiveLimit", uri, query)
            }
            if (targetSdkVersion < Build.VERSION_CODES.Q) {
                Reflection.callStaticMethod(databaseUtilsClass, "recoverAbusiveSelection", query)
            }
        } catch (t: Throwable) {
            dlog("Unable to normalize auxiliary query arguments: $t")
        }
        return query
    }

    private fun handleQueryResult(
        param: Chain,
        state: QueryState,
    ) {
        if (!state.shouldRecord) return

        val rows = queryAuxiliaryRows(param, state, MAX_RECORD_SIZE)
        val intercepted = if (state.sqlFilterApplied) {
            rows.map { row ->
                service.ruleSp.templates.shouldIntercept(
                    state.templates,
                    row.data,
                    row.mimeType.takeIf { state.mediaTypeFilterApplied },
                )
            }
        } else {
            List(rows.size) { false }
        }
        recordQuery(state, rows, intercepted)
    }

    private fun queryAuxiliaryRows(
        param: Chain,
        state: QueryState,
        maxRows: Int,
    ): List<MediaRow> {
        val helper = try {
            Reflection.callMethod(param.provider, "getDatabaseForUri", state.uri)
        } catch (t: Throwable) {
            dlog("Unable to resolve database for auxiliary query: $t")
            return emptyList()
        }
        val honoredArgs = Consumer<String> { }
        val queryBuilder = callGetQueryBuilder(
            param.provider,
            TYPE_QUERY,
            state.table,
            state.uri,
            Bundle(state.query),
            honoredArgs,
        ) ?: return emptyList()
        val projection = arrayOf(FileColumns._ID, FileColumns.DATA, FileColumns.MIME_TYPE)

        val cursor = try {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> Reflection.callMethod(
                    queryBuilder,
                    "query",
                    helper,
                    projection,
                    Bundle(state.query),
                    state.signal,
                )

                Build.VERSION.SDK_INT == Build.VERSION_CODES.Q -> {
                    val selection = state.query.getString(ContentResolver.QUERY_ARG_SQL_SELECTION)
                    val selectionArgs = state.query.getStringArray(
                        ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
                    )
                    val sortOrder = state.query.getString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER)
                    val groupBy = if (state.table == MediaTables.AUDIO_ARTISTS_ID_ALBUMS) {
                        "audio.album_id"
                    } else {
                        null
                    }
                    Reflection.callMethod(
                        queryBuilder,
                        "query",
                        Reflection.callMethod(helper, "getWritableDatabase"),
                        projection,
                        selection,
                        selectionArgs,
                        groupBy,
                        null,
                        sortOrder,
                        state.uri.getQueryParameter("limit"),
                        state.signal,
                    )
                }

                else -> return emptyList()
            } as Cursor
        } catch (t: Throwable) {
            dlog("Auxiliary query failed: $t")
            return emptyList()
        }

        return cursor.use {
            val idColumn = it.getColumnIndex(FileColumns._ID)
            val dataColumn = it.getColumnIndex(FileColumns.DATA)
            val mimeTypeColumn = it.getColumnIndex(FileColumns.MIME_TYPE)
            val rows = ArrayList<MediaRow>(minOf(it.count, maxRows))
            while (rows.size < maxRows && it.moveToNext()) {
                val data = it.stringOrNull(dataColumn)
                rows += MediaRow(
                    id = if (idColumn >= 0) it.getLong(idColumn) else NO_ID,
                    data = data,
                    mimeType = it.stringOrNull(mimeTypeColumn)
                        ?: data?.let { path -> MimeUtils.resolveMimeType(File(path)) },
                )
            }
            rows
        }
    }

    private fun recordQuery(
        state: QueryState,
        rows: List<MediaRow>,
        intercepted: List<Boolean> = List(rows.size) { false },
    ) {
        if (rows.isEmpty()) return
        val size = minOf(rows.size, intercepted.size, MAX_RECORD_SIZE)
        service.insertRecordAsync(
            MediaProviderRecord(
                id = 0,
                timeMillis = System.currentTimeMillis(),
                packageName = state.callingPackage,
                match = state.table,
                operation = OP_QUERY,
                data = List(size) { rows[it].data.orEmpty() },
                mimeType = List(size) { rows[it].mimeType.orEmpty() },
                intercepted = List(size) { intercepted[it] },
            ),
        )
    }

    private fun Cursor.stringOrNull(column: Int): String? =
        if (column >= 0 && !isNull(column)) getString(column) else null

    private fun isClientQuery(callingPackage: String, uri: Uri): Boolean =
        callingPackage == BuildConfig.APPLICATION_ID &&
            uri == MediaStore.Images.Media.INTERNAL_CONTENT_URI

    private fun handleClientQuery(table: Array<String>?, queryArgs: Bundle): Cursor {
        if (table == null || queryArgs.isEmpty) {
            return MatrixCursor(arrayOf("binder")).apply {
                extras = bundleOf(BINDER_EXTRA_KEY to service)
            }
        }

        val start = queryArgs.getString(ContentResolver.QUERY_ARG_SQL_SELECTION)?.toLongOrNull()
            ?: return MatrixCursor(MediaProviderRecordColumns.ALL)
        val end = queryArgs.getString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER)?.toLongOrNull()
            ?: return MatrixCursor(MediaProviderRecordColumns.ALL)
        val operations = table.mapNotNull { it.toIntOrNull() }
            .filter { it in OP_QUERY..me.gm.cleaner.plugin.dao.MediaProviderOperation.OP_DELETE }
            .distinct()
            .toIntArray()
        if (operations.isEmpty()) return MatrixCursor(MediaProviderRecordColumns.ALL)

        @Suppress("WrongConstant")
        return service.dao.loadForTimeMillis(start, end, operations)
    }

    private sealed interface PreparedQuery

    private data class ClientQuery(val cursor: Cursor) : PreparedQuery

    private data class QueryState(
        val uri: Uri,
        val query: Bundle,
        val signal: CancellationSignal?,
        val callingPackage: String,
        val table: Int,
        val templates: List<Template>,
        val shouldRecord: Boolean,
        val arguments: Array<Any?>,
        val sqlFilterApplied: Boolean,
        val mediaTypeFilterApplied: Boolean,
    ) : PreparedQuery

    private data class MediaRow(
        val id: Long,
        val data: String?,
        val mimeType: String?,
    )

    private object MediaProviderRecordColumns {
        val ALL = arrayOf(
            "id",
            "time_millis",
            "package_name",
            "match",
            "operation",
            "data",
            "mime_type",
            "intercepted",
        )
    }

    companion object {
        private const val BINDER_EXTRA_KEY = "me.gm.cleaner.plugin.cursor.extra.BINDER"
        private const val INCLUDED_DEFAULT_DIRECTORIES = "android:included-default-directories"
        private const val TYPE_QUERY = 0
        private const val MAX_RECORD_SIZE = 1_000
        private const val NO_ID = -1L
    }
}
