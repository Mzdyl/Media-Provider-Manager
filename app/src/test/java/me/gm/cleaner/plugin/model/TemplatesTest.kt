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
    fun combinesPermittedTypesAcrossTemplatesAsAUnion() {
        val images = Template(
            templateName = "images",
            hookOperation = listOf("query"),
            applyToApp = listOf("com.example.reader"),
            permittedMediaTypes = listOf(1),
            filterPath = null,
        )
        val videos = images.copy(
            templateName = "videos",
            permittedMediaTypes = listOf(3),
        )
        val templates = Templates(null)

        assertFalse(templates.shouldIntercept(listOf(images, videos), null, "image/jpeg"))
        assertFalse(templates.shouldIntercept(listOf(images, videos), null, "video/mp4"))
        assertTrue(templates.shouldIntercept(listOf(images, videos), null, "audio/mpeg"))
    }

    @Test
    fun allPermittedTypesAreNeutralWhenCombinedWithARestriction() {
        val unrestricted = Template(
            templateName = "path policy",
            hookOperation = listOf("query"),
            applyToApp = listOf("com.example.reader"),
            permittedMediaTypes = (0..6).toList(),
            filterPath = listOf("/storage/emulated/0/Private"),
        )
        val images = unrestricted.copy(
            templateName = "images",
            permittedMediaTypes = listOf(1),
            filterPath = null,
        )
        val templates = Templates(null)

        assertFalse(templates.shouldIntercept(listOf(unrestricted, images), null, "image/jpeg"))
        assertTrue(templates.shouldIntercept(listOf(unrestricted, images), null, "video/mp4"))
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
