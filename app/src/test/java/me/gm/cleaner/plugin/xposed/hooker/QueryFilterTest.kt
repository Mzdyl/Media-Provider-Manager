package me.gm.cleaner.plugin.xposed.hooker

import me.gm.cleaner.plugin.model.Template
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QueryFilterTest {

    @Test
    fun pathRulesMatchOnlyTheDirectoryAndItsChildren() {
        val filter = QueryFilter.build(
            listOf(template(filterPath = listOf("/storage/emulated/0/Pictures/Private/"))),
            MediaTables.IMAGES_MEDIA,
        )!!

        assertTrue(filter.clause.contains("LOWER(_data) != LOWER(?)"))
        assertTrue(filter.clause.contains("LOWER(_data) NOT LIKE LOWER(?)"))
        assertEquals(
            listOf(
                "/storage/emulated/0/Pictures/Private",
                "/storage/emulated/0/Pictures/Private/%",
            ),
            filter.arguments,
        )
    }

    @Test
    fun pathRulesEscapeSqlLikeWildcards() {
        val filter = QueryFilter.build(
            listOf(template(filterPath = listOf("/storage/emulated/0/Pictures/100%_Private"))),
            MediaTables.FILES,
        )!!

        assertEquals(
            "/storage/emulated/0/Pictures/100\\%\\_Private/%",
            filter.arguments.last(),
        )
    }

    @Test
    fun rootPathDoesNotCreateADoubleSlashPattern() {
        val filter = QueryFilter.build(
            listOf(template(filterPath = listOf("/"))),
            MediaTables.FILES,
        )!!

        assertEquals(listOf("/", "/%"), filter.arguments)
    }

    @Test
    fun permittedTypesAllowNullMimeTypesAndSelectedMediaTypes() {
        val filter = QueryFilter.build(
            listOf(template(permittedMediaTypes = listOf(1, 3))),
            MediaTables.FILES,
        )!!

        assertEquals("(mime_type IS NULL OR media_type IN (?, ?))", filter.clause)
        assertEquals(listOf("1", "3"), filter.arguments)
    }

    @Test
    fun mergesWithCallerSelectionWithoutChangingArgumentOrder() {
        val merged = QueryFilter.merge(
            "owner_package_name = ?",
            arrayOf("com.example.owner"),
            SqlSelection("_data != ?", listOf("/private/file.jpg")),
        )

        assertEquals(
            "(owner_package_name = ?) AND (_data != ?)",
            merged.clause,
        )
        assertEquals(
            listOf("com.example.owner", "/private/file.jpg"),
            merged.arguments,
        )
    }

    @Test
    fun doesNotInjectFiltersIntoAggregateTables() {
        assertNull(
            QueryFilter.build(
                listOf(template(filterPath = listOf("/storage/emulated/0/Private"))),
                MediaTables.AUDIO_ARTISTS,
            ),
        )
    }

    private fun template(
        permittedMediaTypes: List<Int>? = null,
        filterPath: List<String>? = null,
    ) = Template(
        templateName = "test",
        hookOperation = listOf("query"),
        applyToApp = listOf("com.example.app"),
        permittedMediaTypes = permittedMediaTypes,
        filterPath = filterPath,
    )
}
