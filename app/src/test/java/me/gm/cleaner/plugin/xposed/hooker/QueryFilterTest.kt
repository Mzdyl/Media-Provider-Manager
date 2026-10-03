package me.gm.cleaner.plugin.xposed.hooker

import me.gm.cleaner.plugin.model.Template
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QueryFilterTest {
    @Test
    fun diagnosticFlagsFollowSqlNullAndFixedCollectionSemantics() {
        val rule = Template("sample", listOf("query"), listOf("test.app"), listOf(2), null)
        assertFalse(QueryFilter.rejectsSample(listOf(rule), MediaTables.FILES, "/a.png", null, 1))
        assertTrue(QueryFilter.rejectsSample(listOf(rule), MediaTables.IMAGES_MEDIA, "/a.png", null, null))
        assertFalse(QueryFilter.rejectsSample(listOf(rule), MediaTables.DOWNLOADS, "/a.png", "image/png", 1))
        assertFalse(QueryFilter.rejectsSample(listOf(rule), MediaTables.FILES, "/a.png", "image/png", 2))
    }

    @Test
    fun diagnosticPathCaseRulesMatchSqliteAsciiLowerAndDirectoryBoundaries() {
        val rule = Template("sample", listOf("query"), listOf("test.app"), null, listOf("/Pictures/A%_"))
        assertTrue(QueryFilter.rejectsSample(listOf(rule), MediaTables.FILES, "/pictures/a%_/x.png", null, null))
        assertFalse(QueryFilter.rejectsSample(listOf(rule), MediaTables.FILES, "/Pictures/A%_other/x.png", null, null))
        val unicode = rule.copy(filterPath=listOf("/Ä"))
        assertFalse(QueryFilter.rejectsSample(listOf(unicode), MediaTables.FILES, "/ä/x.png", null, null))
    }


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
    fun combinesPermittedTypesAcrossTemplatesAsAUnion() {
        val filter = QueryFilter.build(
            listOf(
                template(permittedMediaTypes = listOf(1)),
                template(permittedMediaTypes = listOf(3)),
            ),
            MediaTables.FILES,
        )!!

        assertEquals("(mime_type IS NULL OR media_type IN (?, ?))", filter.clause)
        assertEquals(listOf("1", "3"), filter.arguments)
    }

    @Test
    fun allPermittedTypesDoNotAddARejectedMediaTypeToken() {
        val filter = QueryFilter.build(
            listOf(
                template(
                    permittedMediaTypes = (0..6).toList(),
                    filterPath = listOf("/storage/emulated/0/Pictures/Private"),
                ),
            ),
            MediaTables.IMAGES_MEDIA,
        )!!

        assertFalse(filter.clause.contains("media_type"))
        assertEquals(2, filter.arguments.size)
    }

    @Test
    fun allPermittedTypesAreNeutralWhenCombinedWithARestriction() {
        val filter = QueryFilter.build(
            listOf(
                template(permittedMediaTypes = (0..6).toList()),
                template(permittedMediaTypes = listOf(1)),
            ),
            MediaTables.FILES,
        )!!

        assertEquals("(mime_type IS NULL OR media_type IN (?))", filter.clause)
        assertEquals(listOf("1"), filter.arguments)
    }

    @Test
    fun collectionTablesUseTheirFixedMediaType() {
        assertNull(
            QueryFilter.build(
                listOf(template(permittedMediaTypes = listOf(1))),
                MediaTables.IMAGES_MEDIA,
            ),
        )
        assertEquals(
            "0",
            QueryFilter.build(
                listOf(template(permittedMediaTypes = listOf(3))),
                MediaTables.IMAGES_MEDIA,
            )!!.clause,
        )
    }

    @Test
    fun unsupportedMediaTypeChecksKeepPathFilteringWithoutInvalidSql() {
        val partialFilter = QueryFilter.build(
            listOf(
                template(
                    permittedMediaTypes = listOf(1),
                    filterPath = listOf("/storage/emulated/0/Download/Private"),
                ),
            ),
            MediaTables.DOWNLOADS,
        )!!
        assertFalse(partialFilter.clause.contains("media_type"))
        assertFalse(partialFilter.filtersMediaTypes)

        val pathOnly = QueryFilter.build(
            listOf(
                template(
                    permittedMediaTypes = (0..6).toList(),
                    filterPath = listOf("/storage/emulated/0/Download/Private"),
                ),
            ),
            MediaTables.DOWNLOADS,
        )!!
        assertFalse(pathOnly.clause.contains("media_type"))
        assertTrue(pathOnly.filtersMediaTypes)

        assertNull(
            QueryFilter.build(
                listOf(template(permittedMediaTypes = listOf(1))),
                MediaTables.DOWNLOADS,
            ),
        )
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
