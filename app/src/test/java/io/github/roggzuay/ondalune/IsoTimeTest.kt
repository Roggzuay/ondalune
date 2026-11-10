package io.github.roggzuay.ondalune

import io.github.roggzuay.ondalune.data.network.isoToMs
import org.junit.Assert.assertEquals
import org.junit.Test

class IsoTimeTest {
    @Test
    fun `reads Navidrome and Jellyfin times`() {
        assertEquals(1709296496000L, isoToMs("2024-03-01T12:34:56.789Z"))
        assertEquals(1709296496000L, isoToMs("2024-03-01T12:34:56.7890000Z"))
        assertEquals(1709296496000L, isoToMs("2024-03-01T15:34:56+03:00"))
        assertEquals(1709296496000L, isoToMs("2024-03-01T12:34:56Z"))
    }

    @Test
    fun `missing or broken times are 0`() {
        assertEquals(0L, isoToMs(""))
        assertEquals(0L, isoToMs("yesterday"))
    }
}
