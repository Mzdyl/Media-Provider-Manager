package me.gm.cleaner.plugin

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Environment
import android.os.IBinder
import android.os.OperationCanceledException
import android.os.Parcel
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.io.File

/** Opt-in device regression: preserves existing rules and deletes only its own fixture URIs. */
@RunWith(AndroidJUnit4::class)
class Api102DeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val resolver = context.contentResolver
    private lateinit var service: IBinder
    private val media = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    private val fixtures = mutableListOf<Uri>()
    private val suffix = UUID.randomUUID().toString()
    private val hidden = "Pictures/MPM_API102_${suffix}_hidden/"
    private val visible = "Pictures/MPM_API102_${suffix}_visible/"

    // Use the stable wire protocol instead of referencing classes renamed by the tested APK's R8.
    private fun <T> call(id: Int, write: (Parcel) -> Unit = {}, read: (Parcel) -> T): T {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken("me.gm.cleaner.plugin.IManagerService")
            write(data)
            check(service.transact(IBinder.FIRST_CALL_TRANSACTION + id, data, reply, 0)) {
                "Unsupported module transaction $id; restart the MediaProvider scope"
            }
            reply.readException()
            return read(reply)
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    private fun readSettings(who: Int): String = call(20, { it.writeInt(who) }) { it.readString().orEmpty() }
    private fun writeSettings(who: Int, json: String) = call(21, { it.writeInt(who); it.writeString(json) }) { }

    @Test
    fun modernProviderPreservesFilteringAndBinderContracts() {
        service = resolver.query(MediaStore.Images.Media.INTERNAL_CONTENT_URI, null, null, null, null)!!.use {
            checkNotNull(it.extras.getBinder("me.gm.cleaner.plugin.cursor.extra.BINDER")) {
                "No module Binder; enable API 102 module and restart the MediaProvider scope"
            }
        }
        assertEquals(102, call(1) { it.readInt() })
        assertEquals(BuildConfig.VERSION_CODE, call(0) { it.readInt() })
        val originalRoot = readSettings(ROOT)
        val originalRules = readSettings(RULES)
        val rootSettings = JSONObject(originalRoot.ifBlank { "{}" })
        val recordingChanged = !rootSettings.optBoolean("usage_record", true)
        val baselineRules = JSONArray(originalRules.ifBlank { "[]" })
        // Existing rules for other apps stay in force throughout the regression.
        for (i in 0 until baselineRules.length()) {
            val rule = baselineRules.getJSONObject(i)
            val packages = rule.optJSONArray("apply_to_app") ?: continue
            rule.put("apply_to_app", JSONArray((0 until packages.length()).map { packages.getString(it) }.filter { it != context.packageName }))
        }
        try {
            if (recordingChanged) writeSettings(ROOT, rootSettings.put("usage_record", true).toString())
            writeSettings(RULES, baselineRules.toString())
            // Hidden items are inserted first so post-pagination filtering would return an empty page.
            val blockedRows = List(4) { createImage(hidden, "hidden_$it.png") }
            val visibleRows = List(3) { createImage(visible, "visible_$it.png") }
            val allIds = (blockedRows + visibleRows).map { it.lastPathSegment!! }
            assertEquals(7, queryIds(allIds).size)
            val queryRules = JSONArray(baselineRules.toString()).put(rule("query", hidden))
            writeSettings(RULES, queryRules.toString())
            assertEquals(visibleRows.map { it.lastPathSegment!!.toLong() }, queryIds(allIds))
            assertEquals(visibleRows.take(2).map { it.lastPathSegment!!.toLong() }, queryIds(allIds, 2))
            assertEquals(visibleRows.drop(2).map { it.lastPathSegment!!.toLong() }, queryIds(allIds, 2, 2))

            val cancelled = CancellationSignal().apply { cancel() }
            try {
                resolver.query(media, arrayOf("_id"), queryArgs(allIds), cancelled)?.close()
                fail("Cancelled queries must propagate cancellation")
            } catch (_: OperationCanceledException) {
                // Expected: the filter must not turn cancellation into an unfiltered retry.
            }

            val insertRules = JSONArray(queryRules.toString()).put(rule("insert", hidden))
            writeSettings(RULES, insertRules.toString())
            val rejected = resolver.insert(media, values(hidden, "rejected.png"))
            if (rejected != null) fixtures.add(rejected)
            assertNull("A denied insert must not create a media row", rejected)
            assertNotNull(createImage(visible, "permitted.png"))

            val beforeDelete = usageCount(2)
            assertEquals(1, resolver.delete(visibleRows.last(), null, null))
            val deadline = android.os.SystemClock.elapsedRealtime() + 10_000
            while (usageCount(2) <= beforeDelete && android.os.SystemClock.elapsedRealtime() < deadline) {
                Thread.sleep(100)
            }
            assertTrue("Delete hook must record the operation", usageCount(2) > beforeDelete)
            assertTrue("Query hook must write usage records", usageCount(0) > 0)
            assertTrue("Insert hook must write usage records", usageCount(1) > 0)
        } finally {
            // Always restore rules first so test filters cannot prevent fixture cleanup.
            try {
                writeSettings(RULES, originalRules)
                fixtures.forEach { resolver.delete(it, null, null) }
                // File.delete only removes an empty directory; never recursively remove media.
                listOf(hidden, visible).forEach { File(Environment.getExternalStorageDirectory(), it).delete() }
            } finally {
                if (recordingChanged) writeSettings(ROOT, originalRoot)
            }
        }
        assertEquals(originalRules, readSettings(RULES))
        assertEquals(originalRoot, readSettings(ROOT))
    }

    private fun rule(operation: String, directory: String) = JSONObject()
        .put("template_name", "API102 $operation $suffix")
        .put("hook_operation", JSONArray().put(operation))
        .put("apply_to_app", JSONArray().put(context.packageName))
        .put("permitted_media_types", JSONObject.NULL)
        .put("filter_path", JSONArray().put(Environment.getExternalStorageDirectory().path + "/" + directory))

    private fun values(directory: String, name: String) = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
        put(MediaStore.MediaColumns.RELATIVE_PATH, directory)
    }

    private fun createImage(directory: String, name: String): Uri {
        val uri = checkNotNull(resolver.insert(media, values(directory, name)))
        fixtures.add(uri)
        resolver.openOutputStream(uri)!!.use {
            it.write(android.util.Base64.decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a7WQAAAAASUVORK5CYII=", android.util.Base64.DEFAULT))
        }
        return uri
    }

    private fun queryArgs(ids: List<String>, limit: Int? = null, offset: Int = 0) = Bundle().apply {
        putString(ContentResolver.QUERY_ARG_SQL_SELECTION, "_id IN (${ids.joinToString { "?" }})")
        putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, ids.toTypedArray())
        putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf("_id"))
        putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_ASCENDING)
        if (limit != null) {
            putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
            putInt(ContentResolver.QUERY_ARG_OFFSET, offset)
        }
    }

    private fun queryIds(ids: List<String>, limit: Int? = null, offset: Int = 0): List<Long> =
        resolver.query(media, arrayOf("_id", "_display_name"), queryArgs(ids, limit, offset), null)!!.use { cursor ->
            assertArrayEquals(arrayOf("_id", "_display_name"), cursor.columnNames)
            buildList { while (cursor.moveToNext()) add(cursor.getLong(0)) }
        }

    private fun usageCount(operation: Int): Int = call(31, {
        it.writeInt(operation)
        it.writeStringList(listOf(context.packageName))
    }) { it.readInt() }

    companion object {
        private const val ROOT = 1
        private const val RULES = 2
    }
}
