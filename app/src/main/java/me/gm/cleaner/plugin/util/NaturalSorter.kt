/*
 * Copyright 2023 Green Mushroom
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

// https://gist.github.com/seven332/eadc44f1b35f756e46c410a8487fcc1d

package me.gm.cleaner.plugin.util

import java.math.BigInteger
import java.text.Collator
import java.util.Comparator

class NaturalSorter : Comparator<String> {
    companion object {
        private val collator = Collator.getInstance()
    }

    override fun compare(o1: String?, o2: String?): Int {
        var index1 = 0
        var index2 = 0
        while (true) {
            val data1 = nextSlice(o1, index1)
            val data2 = nextSlice(o2, index2)

            if (data1 == null && data2 == null) return 0
            if (data1 == null) return -1
            if (data2 == null) return 1

            index1 += data1.length
            index2 += data2.length

            val result = if (isDigit(data1) && isDigit(data2)) {
                BigInteger(data1).compareTo(BigInteger(data2)).let {
                    if (it == 0) data1.length.compareTo(data2.length) else it
                }
            } else {
                collator.compare(data1, data2)
            }

            if (result != 0) return result
        }
    }

    private fun isDigit(str: String): Boolean {
        val ch = str.first()
        return ch in '0'..'9'
    }

    private fun nextSlice(str: String?, index: Int): String? {
        if (str == null) return null
        val length = str.length
        if (index == length) return null

        val ch = str[index]
        return when {
            ch == '.' || ch == ' ' -> str.substring(index, index + 1)
            ch in '0'..'9' -> str.substring(index, nextNumberBound(str, index + 1))
            else -> str.substring(index, nextOtherBound(str, index + 1))
        }
    }

    private fun nextNumberBound(str: String, index: Int): Int {
        var i = index
        while (i < str.length) {
            val ch = str[i]
            if (ch !in '0'..'9') break
            i++
        }
        return i
    }

    private fun nextOtherBound(str: String, index: Int): Int {
        var i = index
        while (i < str.length) {
            val ch = str[i]
            if (ch == '.' || ch == ' ' || ch in '0'..'9') break
            i++
        }
        return i
    }
}
