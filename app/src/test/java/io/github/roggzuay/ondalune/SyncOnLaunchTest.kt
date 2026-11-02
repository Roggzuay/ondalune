package io.github.roggzuay.ondalune

import io.github.roggzuay.ondalune.ui.viewmodel.shouldSyncOnLaunch
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncOnLaunchTest {
    @Test
    fun neverSyncedServerSyncsOnce() {
        assertTrue(shouldSyncOnLaunch(lastFullSyncAt = 0L, isDemoServer = false))
    }

    @Test
    fun syncedServerWaitsForTheUserHoweverOld() {
        val aWeekAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        assertFalse(shouldSyncOnLaunch(lastFullSyncAt = aWeekAgo, isDemoServer = false))
    }

    @Test
    fun demoServerNeverSyncs() {
        assertFalse(shouldSyncOnLaunch(lastFullSyncAt = 0L, isDemoServer = true))
    }
}
