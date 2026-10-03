package me.gm.cleaner.plugin.model

import org.junit.Assert.assertEquals
import org.junit.Test

class FilterPathDisplayTest {
    @Test
    fun revealsThinSpaceFromDeviceRuleWithoutChangingTheOriginalPath() {
        val path = "/storage/emulated/0/\u2009爱好"
        assertEquals("/storage/emulated/0/[U+2009]爱好", FilterPathDisplay.revealed(path))
        assertEquals('\u2009', path.substringAfterLast('/').first())
    }

    @Test
    fun revealsBoundarySpacesAndFormatCharacters() {
        assertEquals("/Pictures/[U+0020]Private[U+0020]/[U+200B]", FilterPathDisplay.revealed("/Pictures/ Private /\u200B"))
    }

    @Test
    fun ordinaryFolderNamesRemainReadable() {
        assertEquals("/Pictures/My Photos/爱好", FilterPathDisplay.revealed("/Pictures/My Photos/爱好"))
        assertEquals("", FilterPathDisplay.revealed(""))
    }
}
