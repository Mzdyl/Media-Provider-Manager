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

open class SharedPreferencesWrapper : SharedPreferences {
    protected lateinit var delegate: SharedPreferences

    override fun getAll(): Map<String, *> = delegate.all

    override fun getString(key: String?, defValue: String?): String? =
        delegate.getString(key, defValue)

    override fun getStringSet(key: String?, defValues: Set<String>?): Set<String>? =
        delegate.getStringSet(key, defValues)

    override fun getInt(key: String?, defValue: Int): Int =
        delegate.getInt(key, defValue)

    override fun getLong(key: String?, defValue: Long): Long =
        delegate.getLong(key, defValue)

    override fun getFloat(key: String?, defValue: Float): Float =
        delegate.getFloat(key, defValue)

    override fun getBoolean(key: String?, defValue: Boolean): Boolean =
        delegate.getBoolean(key, defValue)

    override fun contains(key: String?): Boolean = delegate.contains(key)

    override fun edit(): SharedPreferences.Editor = delegate.edit()

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
        delegate.registerOnSharedPreferenceChangeListener(listener)
    }

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
        delegate.unregisterOnSharedPreferenceChangeListener(listener)
    }
}
