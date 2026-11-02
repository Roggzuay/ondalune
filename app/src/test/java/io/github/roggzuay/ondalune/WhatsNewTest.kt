package io.github.roggzuay.ondalune

import io.github.roggzuay.ondalune.ui.components.majorMinor
import io.github.roggzuay.ondalune.ui.components.whatsNewToShow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WhatsNewTest {
    @Test
    fun majorMinorIgnoresPatchAndSuffix() {
        assertEquals("1.1", majorMinor("1.1.0-preview"))
        assertEquals("1.0", majorMinor("1.0.6.2-preview"))
        assertEquals("2.0", majorMinor("2.0"))
    }

    @Test
    fun updateToMinorReleaseShowsItsNotesOnce() {
        assertEquals("1.1", whatsNewToShow("1.1.0", lastSeen = null, isUpdate = true)?.version)
        assertEquals("1.1", whatsNewToShow("1.1.0", lastSeen = "1.0", isUpdate = true)?.version)
        assertNull(whatsNewToShow("1.1.0", lastSeen = "1.1", isUpdate = true))
    }

    @Test
    fun freshInstallsAndPatchReleasesStayQuiet() {
        assertNull(whatsNewToShow("1.1.0", lastSeen = null, isUpdate = false))
        assertNull(whatsNewToShow("1.1.2", lastSeen = "1.1", isUpdate = true))
        assertNull(whatsNewToShow("1.0.6", lastSeen = null, isUpdate = true))
    }
}
