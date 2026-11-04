package io.github.roggzuay.ondalune

import io.github.roggzuay.ondalune.data.model.OfflineDownloadState
import io.github.roggzuay.ondalune.data.model.OfflineDownloadState.COMPLETED
import io.github.roggzuay.ondalune.data.model.OfflineDownloadState.DOWNLOADING
import io.github.roggzuay.ondalune.data.model.OfflineDownloadState.FAILED
import io.github.roggzuay.ondalune.data.model.OfflineDownloadState.PAUSED
import io.github.roggzuay.ondalune.data.model.OfflineDownloadState.QUEUED
import io.github.roggzuay.ondalune.data.model.SubsonicDownloadEntity
import io.github.roggzuay.ondalune.ui.components.DownloadOrder
import io.github.roggzuay.ondalune.ui.components.DownloadPhase
import io.github.roggzuay.ondalune.ui.components.downloadStatusOf
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadStatusTest {
    private fun row(state: OfflineDownloadState, percent: Int = 0) = SubsonicDownloadEntity(
        downloadKey = "1_$state$percent", trackId = "t", serverId = 1, albumId = "a", title = "", artist = "",
        album = "", suffix = "mp3", localFilePath = "", progressPercent = percent, status = state.name
    )

    @Test
    fun `nothing downloaded is idle`() {
        assertEquals(DownloadPhase.IDLE, downloadStatusOf(listOf(null, null)).phase)
        assertEquals(DownloadPhase.IDLE, downloadStatusOf(emptyList()).phase)
    }

    @Test
    fun `album still waiting spins, then fills song by song`() {
        assertEquals(DownloadPhase.QUEUED, downloadStatusOf(listOf(row(QUEUED), row(QUEUED))).phase)
        val half = downloadStatusOf(listOf(row(COMPLETED, 100), row(QUEUED)))
        assertEquals(DownloadPhase.DOWNLOADING, half.phase)
        assertEquals(0.5f, half.progress, 0.001f)
        val moving = downloadStatusOf(listOf(row(DOWNLOADING, 50), null))
        assertEquals(DownloadPhase.DOWNLOADING, moving.phase)
        assertEquals(0.25f, moving.progress, 0.001f)
    }

    @Test
    fun `saved only when every song is on the phone`() {
        assertEquals(DownloadPhase.SAVED, downloadStatusOf(listOf(row(COMPLETED, 100), row(COMPLETED, 100))).phase)
        assertEquals(DownloadPhase.IDLE, downloadStatusOf(listOf(row(COMPLETED, 100), null)).phase)
    }

    @Test
    fun `moving beats paused beats failed`() {
        assertEquals(DownloadPhase.DOWNLOADING, downloadStatusOf(listOf(row(DOWNLOADING, 10), row(PAUSED), row(FAILED))).phase)
        assertEquals(DownloadPhase.PAUSED, downloadStatusOf(listOf(row(PAUSED, 40), row(FAILED))).phase)
        assertEquals(DownloadPhase.FAILED, downloadStatusOf(listOf(row(COMPLETED, 100), row(FAILED))).phase)
    }
}

class DownloadOrderTest {
    private fun row(track: String, album: String, state: OfflineDownloadState, updated: Long, percent: Int = 0) =
        SubsonicDownloadEntity(
            downloadKey = "1_$track", trackId = track, serverId = 1, albumId = album, title = track, artist = "",
            album = "", suffix = "mp3", localFilePath = "", progressPercent = percent, status = state.name, updatedAtMs = updated
        )

    @Test
    fun `rows keep their places while progress and state change`() {
        val order = DownloadOrder()
        val numbers = mapOf("a1" to 1, "a2" to 2, "a3" to 3, "b1" to 1)
        val comparator = order.comparator { numbers.getValue(it) }
        // An album queued in a race (track 3 written first), then a single song from another album.
        val first = listOf(row("a3", "A", QUEUED, 10), row("a1", "A", QUEUED, 11), row("a2", "A", QUEUED, 12), row("b1", "B", QUEUED, 20))
        order.place(first)
        val expected = listOf("a1", "a2", "a3", "b1")
        assertEquals(expected, first.sortedWith(comparator).map { it.trackId })
        // Progress ticks rewrite updatedAtMs and states; the order must not move.
        val later = listOf(row("b1", "B", QUEUED, 90), row("a2", "A", DOWNLOADING, 95, 40), row("a3", "A", QUEUED, 99), row("a1", "A", DOWNLOADING, 99, 70))
        order.place(later)
        assertEquals(expected, later.sortedWith(comparator).map { it.trackId })
    }
}
