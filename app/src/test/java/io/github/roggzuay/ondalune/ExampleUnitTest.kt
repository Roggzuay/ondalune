package io.github.roggzuay.ondalune

import io.github.roggzuay.ondalune.audio.OndaluneReverbDspProcessor
import io.github.roggzuay.ondalune.data.local.DemoSubsonicCatalog
import io.github.roggzuay.ondalune.data.local.OndaluneCredentialSecurity
import io.github.roggzuay.ondalune.data.model.LibrarySortOption
import io.github.roggzuay.ondalune.data.model.PitchCorrectionMode
import io.github.roggzuay.ondalune.data.model.PlayerBackgroundAnimationStyle
import io.github.roggzuay.ondalune.data.model.ReverbPreset
import io.github.roggzuay.ondalune.data.model.ReverbState
import io.github.roggzuay.ondalune.data.model.SpeedPitchState
import io.github.roggzuay.ondalune.data.model.TabDisplayPreference
import io.github.roggzuay.ondalune.ui.components.computeArtistMonogram
import io.github.roggzuay.ondalune.ui.viewmodel.OndaluneViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ExampleUnitTest {

    @Test
    fun `reverb dsp processor bypasses dry signal and processes wet signal without clipping`() {
        val processor = OndaluneReverbDspProcessor(sampleRate = 22050)
        val inputImpulse = FloatArray(512) { idx -> if (idx in 0..15) 0.75f else 0f }

        // 1. Dry / OFF preset should leave buffer untouched
        val dryCopy = inputImpulse.copyOf()
        processor.applyPreset(ReverbPreset.OFF, snapImmediately = true)
        processor.processStereoInterleavedFloat(dryCopy)
        assertTrue(inputImpulse.contentEquals(dryCopy))

        // 2. Large Hall preset should produce audible late reverb tail on subsequent zero samples
        val wetCopy = inputImpulse.copyOf()
        val largeHallState = ReverbState.fromPreset(ReverbPreset.LARGE_HALL)
        assertTrue(largeHallState.isActivelyProcessing)
        processor.updateParameters(largeHallState, snapImmediately = true)
        processor.processStereoInterleavedFloat(wetCopy)

        // Feed a second block of silence so delay lines emit reverberant tail energy
        val tailBlock = FloatArray(2048) { 0f }
        processor.processStereoInterleavedFloat(tailBlock)
        val tailEnergy = tailBlock.sumOf { abs(it).toDouble() }
        assertTrue("Expected non-zero reverberant tail energy, got $tailEnergy", tailEnergy > 0.01)
        assertTrue("Expected all samples clamped within [-0.996, 0.996]", tailBlock.all { it in -0.996f..0.996f && !it.isNaN() })
    }

    @Test
    fun `speed and dynamic pitch state operates simultaneously with reverb state`() {
        val speedPitch = SpeedPitchState(
            speed = 1.25f,
            pitchMode = PitchCorrectionMode.INDEPENDENT_SEMITONES,
            independentPitchRatio = SpeedPitchState.semitonesToRatio(-2f)
        )
        val reverb = ReverbState.fromPreset(ReverbPreset.HALL)
        assertEquals(1.25f, speedPitch.speed, 0.001f)
        assertEquals(-2f, speedPitch.effectiveSemitones, 0.05f)
        assertTrue(reverb.isActivelyProcessing)
        val hardwareParams = OndaluneReverbDspProcessor.toEnvironmentalReverbParams(reverb)
        assertTrue(hardwareParams.decayTimeMs in 100..9000)
    }

    @Test
    fun `independent per-category sorting and direction work across albums artists and tracks`() {
        val tracks = DemoSubsonicCatalog.seedTracks(1L)
        val albums = DemoSubsonicCatalog.deriveAlbumsFromTracks(tracks)
        val artists = DemoSubsonicCatalog.deriveArtistsFromTracks(tracks)

        val albumsAsc = OndaluneViewModel.sortAlbums(
            albums,
            TabDisplayPreference(sortOption = LibrarySortOption.NAME_ASC, ascending = true)
        )
        val albumsDesc = OndaluneViewModel.sortAlbums(
            albums,
            TabDisplayPreference(sortOption = LibrarySortOption.NAME_ASC, ascending = false)
        )
        assertEquals(albumsAsc.first().id, albumsDesc.last().id)

        val artistsByTrackCount = OndaluneViewModel.sortArtists(
            artists,
            TabDisplayPreference(sortOption = LibrarySortOption.TRACK_COUNT, ascending = false)
        )
        assertTrue(artistsByTrackCount.first().trackCount >= artistsByTrackCount.last().trackCount)

        val tracksByDurationAsc = OndaluneViewModel.sortTracks(
            tracks,
            TabDisplayPreference(sortOption = LibrarySortOption.DURATION, ascending = true)
        )
        val tracksByDurationDesc = OndaluneViewModel.sortTracks(
            tracks,
            TabDisplayPreference(sortOption = LibrarySortOption.DURATION, ascending = false)
        )
        assertTrue(tracksByDurationAsc.first().durationSec <= tracksByDurationAsc.last().durationSec)
        assertEquals(tracksByDurationAsc.first().id, tracksByDurationDesc.last().id)

        val albumPref = TabDisplayPreference.defaultForCategory("ALBUMS")
        val songPref = TabDisplayPreference.defaultForCategory("SONGS")
        assertNotEquals(albumPref.viewMode, songPref.viewMode)
    }

    @Test
    fun `credential security encrypts and decrypts tokens and redacts sensitive urls`() {
        val secret = "SuperSecretSubsonicToken!2026"
        val encrypted = OndaluneCredentialSecurity.encryptCredential(secret)
        assertTrue(OndaluneCredentialSecurity.isEncrypted(encrypted))
        assertNotEquals(secret, encrypted)
        assertEquals(secret, OndaluneCredentialSecurity.decryptCredential(encrypted))

        val rawUrl = "https://music.example.com/rest/stream.view?u=alice&t=9f8e7d6c5b&s=123456&p=enc:70617373&id=tr_1"
        val redacted = OndaluneCredentialSecurity.redactSensitiveUrl(rawUrl)
        assertFalse(redacted.contains("9f8e7d6c5b"))
        assertFalse(redacted.contains("70617373"))
        assertTrue(redacted.contains("t=[REDACTED]"))
    }

    @Test
    fun `artist monogram generator extracts clean initials`() {
        assertEquals("SS", computeArtistMonogram("Server Synth Collective"))
        assertEquals("DA", computeArtistMonogram("Daft"))
        assertEquals("♪", computeArtistMonogram("   "))
    }

    @Test
    fun `smart dynamic search ranks relevant matches and returns empty when query is not in library`() {
        val tracks = DemoSubsonicCatalog.seedTracks(1L)
        val albums = DemoSubsonicCatalog.deriveAlbumsFromTracks(tracks)
        val artists = DemoSubsonicCatalog.deriveArtistsFromTracks(tracks)

        val matchedTracks = OndaluneViewModel.searchTracks(tracks, "Chrominance")
        assertTrue("Expected Chrominance query to find matching tracks", matchedTracks.isNotEmpty())

        // Typo-tolerant 1-edit-distance match ("Chrominancx" -> "Chrominance")
        val fuzzyTracks = OndaluneViewModel.searchTracks(tracks, "Chrominancx")
        assertTrue("Expected 1-edit typo query 'Chrominancx' to match 'Chrominance'", fuzzyTracks.isNotEmpty())

        // Non-existent query returns empty results across all categories
        val missingQuery = "zzzz_nonexistent_library_item_9999"
        assertTrue(OndaluneViewModel.searchTracks(tracks, missingQuery).isEmpty())
        assertTrue(OndaluneViewModel.searchAlbums(albums, missingQuery).isEmpty())
        assertTrue(OndaluneViewModel.searchArtists(artists, missingQuery).isEmpty())

        // Theme animation labels do not mention official third-party app names
        assertEquals("Velvet Mesh", PlayerBackgroundAnimationStyle.APPLE_GLITTER_GRAIN.label)
        assertTrue(PlayerBackgroundAnimationStyle.entries.none { it.label.contains("Apple Music", ignoreCase = true) })
    }
}
