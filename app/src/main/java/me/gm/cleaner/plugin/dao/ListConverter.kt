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

import androidx.room.TypeConverter
import com.google.gson.reflect.TypeToken
import me.gm.cleaner.plugin.model.Template
import java.lang.Character.MAX_RADIX
import java.math.BigInteger

object ListConverter {
    // Cache TypeToken to avoid repeated object creation
    private val stringListType = object : TypeToken<List<String>?>() {}.type

    @TypeConverter
    fun fromString(value: String?): List<String>? {
        val list = Template.GSON.fromJson<List<String?>?>(value, stringListType) ?: return null
        return if (list.isEmpty() || list.any { it == null }) null
        else list as List<String>?
    }

    @TypeConverter
    fun listToString(list: List<String>?) = Template.GSON.toJson(list)

    @TypeConverter
    fun booleanListFromString(value: String): List<Boolean> {
        val splitIndex = value.indexOf(':', 1)
        val size = value.substring(0, splitIndex).toInt()
        val values = BigInteger(value.substring(splitIndex + 1), MAX_RADIX)

        return MutableList(size) { i -> values.testBit(i) }
    }

    @TypeConverter
    fun booleanListToString(list: List<Boolean>): String {
        var value = BigInteger.ZERO
        list.forEachIndexed { index, b ->
            if (b) {
                value = value.setBit(index)
            }
        }
        return "${list.size}:${value.toString(MAX_RADIX)}"
    }
}
