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

package me.gm.cleaner.plugin.xposed.util

import android.os.FileObserver
import java.io.File
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Predicate
import java.util.function.Supplier

class FileCreationObserver(
    file: File,
    private val scheduler: Supplier<ScheduledExecutorService>
) : FileObserver(file.parentFile, MODIFY or CREATE) {
    private val target = file
    private var onMaybeFileCreatedListener: Predicate<Int>? = null
    private val queueSize = AtomicInteger()

    override fun onEvent(event: Int, path: String?) {
        if (path == null) return
        if (target.name == path) {
            queueSize.incrementAndGet()
            scheduler.get().schedule({
                val currentQueueSize = queueSize.decrementAndGet()
                val testTimes = 1 - currentQueueSize
                // Less than 0 when predicate returns false.
                if (currentQueueSize <= 0 && onMaybeFileCreatedListener?.test(testTimes) == true) {
                    stopWatching()
                }
            }, 5, TimeUnit.SECONDS)
        }
    }

    fun setOnMaybeFileCreatedListener(listener: Predicate<Int>): FileCreationObserver {
        onMaybeFileCreatedListener = listener
        return this
    }
}
