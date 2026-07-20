package me.gm.cleaner.plugin.xposed.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileUtilsTest {

    @Test
    fun containsAcceptsSamePathAndDescendants() {
        assertTrue(FileUtils.contains("/storage/emulated/0/Pictures", "/storage/emulated/0/Pictures"))
        assertTrue(
            FileUtils.contains(
                "/storage/emulated/0/Pictures",
                "/storage/emulated/0/Pictures/Camera/photo.jpg",
            ),
        )
    }

    @Test
    fun containsRejectsSiblingPrefixes() {
        assertFalse(
            FileUtils.contains(
                "/storage/emulated/0/Pictures",
                "/storage/emulated/0/PicturesBackup/photo.jpg",
            ),
        )
    }

    @Test
    fun containsNormalizesDotSegments() {
        assertFalse(
            FileUtils.contains(
                "/storage/emulated/0/Download",
                "/storage/emulated/0/Download/../Tracking/id.txt",
            ),
        )
        assertTrue(
            FileUtils.contains(
                "/storage/emulated/0/Pictures/Camera",
                "/storage/emulated/0/Pictures/./Camera/photo.jpg",
            ),
        )
    }

    @Test
    fun rootOnlyContainsAbsolutePaths() {
        assertTrue(FileUtils.contains("/", "/storage/emulated/0/file.jpg"))
        assertFalse(FileUtils.contains("/", "relative/file.jpg"))
    }
}
