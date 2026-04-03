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

package me.gm.cleaner.plugin.dao

import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.WeakHashMap

class JsonSharedPreferencesImpl : SharedPreferences {
    private val lock = Any()
    private val store: JSONObject
    private val listeners = WeakHashMap<SharedPreferences.OnSharedPreferenceChangeListener, Any>()

    constructor() {
        store = JSONObject()
    }

    constructor(jsonObject: JSONObject) {
        store = jsonObject
    }

    @Throws(JSONException::class)
    constructor(json: String?) {
        store = if (json.isNullOrEmpty()) JSONObject() else JSONObject(json)
    }

    val delegate: JSONObject
        get() = synchronized(lock) {
            try {
                val names = getNames(store)
                if (names != null) JSONObject(store, names) else JSONObject()
            } catch (e: JSONException) {
                throw RuntimeException(e)
            }
        }

    companion object {
        fun getNames(jo: JSONObject): Array<String>? {
            val length = jo.length()
            if (length == 0) return null
            return jo.keys().asSequence().toList().toTypedArray()
        }
    }

    override fun getAll(): Map<String, Any?> = synchronized(lock) {
        buildMap {
            store.keys().forEach { key ->
                put(key, store.opt(key))
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> get(key: String, defValue: T?): T? = synchronized(lock) {
        val result = store.opt(key) ?: return defValue
        result as T
    }

    override fun getString(key: String?, defValue: String?): String? = get(key!!, defValue)

    override fun getStringSet(key: String?, defValues: Set<String>?): Set<String>? = synchronized(lock) {
        val jsonArray = store.optJSONArray(key) ?: return defValues
        try {
            (0 until jsonArray.length()).map { jsonArray[it].toString() }.toSet()
        } catch (e: JSONException) {
            throw RuntimeException(e)
        }
    }

    override fun getInt(key: String?, defValue: Int): Int = get(key!!, defValue) ?: defValue

    override fun getLong(key: String?, defValue: Long): Long = get(key!!, defValue) ?: defValue

    override fun getFloat(key: String?, defValue: Float): Float = get(key!!, defValue) ?: defValue

    override fun getBoolean(key: String?, defValue: Boolean): Boolean = get(key!!, defValue) ?: defValue

    override fun contains(key: String?): Boolean = synchronized(lock) { key != null && store.has(key) }

    override fun edit(): JsonEditorImpl = JsonEditorImpl { false }

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
        synchronized(lock) { listeners[listener!!] = Any() }
    }

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
        synchronized(lock) { listeners.remove(listener) }
    }

    override fun toString(): String = synchronized(lock) { store.toString() }

    inner class JsonEditorImpl(private val awaitCommit: (JSONObject) -> Boolean) : SharedPreferences.Editor {
        private val editorLock = Any()
        private val modified = mutableMapOf<String, Any?>()
        private var clear = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor = synchronized(editorLock) {
            modified[key!!] = value
            this
        }

        override fun putStringSet(key: String?, values: Set<String>?): SharedPreferences.Editor = synchronized(editorLock) {
            modified[key!!] = values?.let { JSONArray(it) }
            this
        }

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = synchronized(editorLock) {
            modified[key!!] = value
            this
        }

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = synchronized(editorLock) {
            modified[key!!] = value
            this
        }

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = synchronized(editorLock) {
            modified[key!!] = value
            this
        }

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = synchronized(editorLock) {
            modified[key!!] = value
            this
        }

        /**
         * Maps `name` to `value`, clobbering any existing name/value
         * mapping with the same name. If the value is `null`, any existing
         * mapping for `name` is removed.
         *
         * @param value a [JSONObject], [JSONArray], String, Boolean,
         *              Integer, Long, Double, [JSONObject.NULL], or `null`. May not be
         *              `Double.NaN` or `Double.isInfinite`.
         * @return this object.
         */
        fun putAny(key: String, value: Any?): SharedPreferences.Editor = synchronized(editorLock) {
            modified[key] = value
            this
        }

        override fun remove(key: String?): SharedPreferences.Editor = synchronized(editorLock) {
            modified[key!!] = this@JsonEditorImpl
            this
        }

        override fun clear(): SharedPreferences.Editor = synchronized(editorLock) {
            clear = true
            this
        }

        override fun commit(): Boolean {
            val jsonToWriteToDisk = commitToMemory()
            return commitToDisk(jsonToWriteToDisk)
        }

        private fun commitToMemory(): JSONObject {
            val keysModified = mutableListOf<String>()
            val listenersCopy: Set<SharedPreferences.OnSharedPreferenceChangeListener>
            
            synchronized(lock) {
                listenersCopy = listeners.keys.toSet()
                val hasListeners = listenersCopy.isNotEmpty()
                
                synchronized(editorLock) {
                    if (clear) {
                        modified.clear()
                        store.keys().forEach { remove(it) }
                        clear = false
                    }

                    for ((k, v) in modified) {
                        // "this" is the magic value for a removal mutation. In addition,
                        // setting a value to "null" for a given key is specified to be
                        // equivalent to calling remove on that key.
                        if (v === this@JsonEditorImpl || v == null) {
                            if (!contains(k)) continue
                            store.remove(k)
                        } else {
                            val existingValue = get(k, null as Any?)
                            if (existingValue == v) continue
                            try {
                                store.put(k, v)
                            } catch (e: JSONException) {
                                throw RuntimeException(e)
                            }
                        }

                        if (hasListeners) keysModified.add(k)
                    }
                    modified.clear()
                }
            }
            notifyListeners(listenersCopy, keysModified)
            return JSONObject(getAll())
        }

        private fun notifyListeners(
            listeners: Set<SharedPreferences.OnSharedPreferenceChangeListener>,
            keysModified: List<String>
        ) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                keysModified.reversed().forEach { key ->
                    listeners.forEach { listener ->
                        listener.onSharedPreferenceChanged(this@JsonSharedPreferencesImpl, key)
                    }
                }
            } else {
                Handler(Looper.getMainLooper()).post { notifyListeners(listeners, keysModified) }
            }
        }

        fun commitToDisk(jo: JSONObject): Boolean = awaitCommit(jo)

        override fun apply() {
            val jsonToWriteToDisk = commitToMemory()
            Thread { commitToDisk(jsonToWriteToDisk) }.start()
        }
    }
}
