package me.gm.cleaner.plugin.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplatesTest {

    @Test
    fun parsesAndAppliesPathRules() {
        val templates = Templates(
            """[
                {
                  "template_name": "private pictures",
                  "hook_operation": ["query"],
                  "apply_to_app": ["com.example.reader"],
                  "permitted_media_types": null,
                  "filter_path": ["/storage/emulated/0/Pictures/Private"]
                }
            ]""".trimIndent(),
        )

        assertTrue(
            templates.shouldIntercept(
                templates.values,
                "/storage/emulated/0/Pictures/Private/photo.jpg",
                "image/jpeg",
            ),
        )
        assertFalse(
            templates.shouldIntercept(
                templates.values,
                "/storage/emulated/0/Pictures/Public/photo.jpg",
                "image/jpeg",
            ),
        )
    }

    @Test
    fun mediaTypeRulesFailOpenWhenMimeTypeIsUnavailable() {
        val template = Template(
            templateName = "images only",
            hookOperation = listOf("query"),
            applyToApp = listOf("com.example.reader"),
            permittedMediaTypes = listOf(1),
            filterPath = null,
        )
        val templates = Templates(null)

        assertFalse(templates.shouldIntercept(listOf(template), null, "image/jpeg"))
        assertTrue(templates.shouldIntercept(listOf(template), null, "video/mp4"))
        assertFalse(templates.shouldIntercept(listOf(template), null, null))
    }

    @Test
    fun rejectsDuplicateNamesAndUnsupportedOperations() {
        assertThrows(IllegalArgumentException::class.java) {
            Templates(
                """[
                    {"template_name":"same","hook_operation":["query"]},
                    {"template_name":"same","hook_operation":["insert"]}
                ]""".trimIndent(),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            Templates(
                """[
                    {"template_name":"delete rule","hook_operation":["delete"]}
                ]""".trimIndent(),
            )
        }
    }

    @Test
    fun rejectsMalformedDocuments() {
        assertThrows(RuntimeException::class.java) { Templates("not json") }
        assertThrows(IllegalArgumentException::class.java) { Templates("null") }
    }
}
