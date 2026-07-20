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

package me.gm.cleaner.plugin.xposed

import me.gm.cleaner.plugin.model.Templates
import me.gm.cleaner.plugin.util.L
import java.io.File

class TemplatesJsonFileSpImpl(src: File) : JsonFileSpImpl(src) {
    @Volatile
    var templates: Templates = parseTemplates(read())
        private set

    override fun validateContent(what: String) {
        Templates(what)
    }

    override fun write(what: String) {
        val updatedTemplates = Templates(what)
        super.write(what)
        templates.clearCache()
        templates = updatedTemplates
    }

    private fun parseTemplates(content: String?): Templates = try {
        Templates(content)
    } catch (t: Throwable) {
        L.e("Invalid template JSON in ${file.path}; disabling templates", t)
        Templates(null)
    }
}
