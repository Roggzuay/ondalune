package io.github.roggzuay.ondalune

import io.github.roggzuay.ondalune.data.network.isNewerVersion
import io.github.roggzuay.ondalune.data.network.releaseNoteItems
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckTest {
    @Test
    fun newerReleaseTagIsAnUpdate() {
        assertTrue(isNewerVersion("v1.2.0", "1.1.0"))
        assertTrue(isNewerVersion("v1.1.1", "1.1.0"))
        assertTrue(isNewerVersion("v1.10.0", "1.9.3"))
        assertTrue(isNewerVersion("v2.0", "1.9.9.9"))
    }

    @Test
    fun sameOrOlderReleaseIsNotAnUpdate() {
        assertFalse(isNewerVersion("v1.1.0", "1.1.0"))
        assertFalse(isNewerVersion("v1.1", "1.1.0"))
        assertFalse(isNewerVersion("v1.0.6", "1.1.0"))
    }

    @Test
    fun previewBuildsCompareByTheirNumbers() {
        assertFalse(isNewerVersion("v1.1.0", "1.1.0.2-preview"))
        assertTrue(isNewerVersion("v1.1.1", "1.1.0.2-preview"))
    }

    @Test
    fun unreadableVersionsNeverShowAnUpdate() {
        assertFalse(isNewerVersion("nightly", "1.1.0"))
        assertFalse(isNewerVersion("", "1.1.0"))
        // Local builds are stamped with the build time (2026.11.02.1530), always ahead of a release.
        assertFalse(isNewerVersion("v1.2.0", "2026.11.02.1530"))
    }

    @Test
    fun releaseNotesKeepTheBulletsAndDropHeadersAndChecksum() {
        val body = """
            **New**
            - **A calmer look:** colour now comes from your music.
            - **Hold to reset:** hold the speed button to reset speed.

            **Fixes & improvements**, including a crash while the library syncs.

            Verify the download: `sha256sum -c Ondalune-1.1.0.apk.sha256`
        """.trimIndent()
        assertEquals(
            listOf(
                "A calmer look: colour now comes from your music.",
                "Hold to reset: hold the speed button to reset speed.",
                "Fixes & improvements, including a crash while the library syncs."
            ),
            releaseNoteItems(body)
        )
    }

    @Test
    fun releaseNotesHandleWindowsLineEndingsAndEmptyBodies() {
        assertEquals(listOf("One", "Two"), releaseNoteItems("- One\r\n* Two\r\n"))
        assertEquals(emptyList<String>(), releaseNoteItems(""))
    }
}
