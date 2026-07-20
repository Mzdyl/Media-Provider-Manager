package me.gm.cleaner.plugin.dao

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ListConverterTest {

    @Test
    fun stringListsRoundTripWithoutNullEntries() {
        val source = listOf("/one.jpg", "/two.mp4", "")
        assertEquals(source, ListConverter.fromString(ListConverter.listToString(source)))
        assertNull(ListConverter.fromString("[\"valid\",null]"))
    }

    @Test
    fun booleanListsRoundTripCompactEncoding() {
        listOf(
            emptyList(),
            listOf(false),
            listOf(true, false, true, true, false, false, true),
        ).forEach { source ->
            assertEquals(
                source,
                ListConverter.booleanListFromString(ListConverter.booleanListToString(source)),
            )
        }
    }
}
