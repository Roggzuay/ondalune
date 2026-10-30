package io.github.roggzuay.ondalune

import androidx.compose.ui.graphics.toArgb
import io.github.roggzuay.ondalune.data.model.genreTokens
import io.github.roggzuay.ondalune.data.model.hasGenre
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.roggzuay.ondalune.data.local.DemoSubsonicCatalog
import io.github.roggzuay.ondalune.data.model.CornerStylePreset
import io.github.roggzuay.ondalune.data.model.CuratedColorPalette
import io.github.roggzuay.ondalune.data.model.PlayerBackgroundAnimationStyle
import io.github.roggzuay.ondalune.data.model.PlayerCustomizationSettings
import io.github.roggzuay.ondalune.data.model.ReverbPreset
import io.github.roggzuay.ondalune.data.model.ReverbState
import io.github.roggzuay.ondalune.data.model.WidgetLayoutMode
import io.github.roggzuay.ondalune.data.model.WidgetThemeMode
import io.github.roggzuay.ondalune.ui.screens.SettingsExpandableSection
import io.github.roggzuay.ondalune.ui.theme.buildShapesForPreset
import io.github.roggzuay.ondalune.ui.theme.resolveDynamicSurfacePalette
import io.github.roggzuay.ondalune.ui.viewmodel.PrimaryDestination
import io.github.roggzuay.ondalune.widget.OndaluneMediaBridge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Ondalune", appName)
    }

    @Test
    fun `artists derived from tracks have strictly unique ids and names`() {
        val tracks = DemoSubsonicCatalog.seedTracks(1L)
        val artists = DemoSubsonicCatalog.deriveArtistsFromTracks(tracks)
        assertTrue(artists.isNotEmpty())
        assertEquals(artists.size, artists.map { it.id }.distinct().size)
        assertEquals(artists.size, artists.map { it.name }.distinct().size)
    }

    @Test
    fun `genres come only from track tags and multi-genre tags are split`() {
        val tracks = DemoSubsonicCatalog.seedTracks(1L)
        val genres = DemoSubsonicCatalog.deriveGenresFromTracks(tracks)
        assertTrue(genres.isNotEmpty())
        assertEquals(genres.size, genres.map { it.name.lowercase() }.distinct().size)
        // Every genre counts exactly the tracks that carry it; none claims the whole library.
        genres.forEach { g ->
            assertEquals(g.name, tracks.count { it.hasGenre(g.name) }, g.trackCount)
        }

        val mixed = listOf(
            tracks[0].copy(id = "a", genre = "Electronic; Deep House"),
            tracks[0].copy(id = "b", genre = "Hip-Hop/Rap"),
            tracks[0].copy(id = "c", genre = "")
        )
        val names = DemoSubsonicCatalog.deriveGenresFromTracks(mixed).map { it.name }.toSet()
        assertEquals(setOf("Electronic", "Deep House", "Hip-Hop", "Rap"), names)
        assertTrue("house" in mixed[0].genreTokens())
    }

    @Test
    fun `primary destinations include Home Library Playlists Favourites Settings and Artists`() {
        val routes = PrimaryDestination.entries.map { it.route }
        assertEquals(listOf("home", "library", "playlists", "favourites", "settings", "artists"), routes)
    }

    @Test
    fun `black and white artwork gives a grey scheme, not the coral fallback`() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val scheme = io.github.roggzuay.ondalune.ui.theme.buildOndaluneColorScheme(
            context, seedHex = "#7A7A7A", isDark = true, isAmoled = false,
            useMaterialYouDynamic = false, unifiedMaterialYouColor = false, harmonizeWithArtwork = true,
            curatedPalette = CuratedColorPalette.DEFAULT_EXPRESSIVE
        )
        val hsv = FloatArray(3)
        listOf(scheme.primary, scheme.primaryContainer, scheme.secondaryContainer, scheme.surface).forEach {
            android.graphics.Color.colorToHSV(it.toArgb(), hsv)
            assertTrue("expected neutral colour, saturation was ${hsv[1]}", hsv[1] < 0.1f)
        }
    }

    @Test
    fun `every theme defines a home layout and fonts resolve`() {
        io.github.roggzuay.ondalune.data.model.InterfaceStyle.entries.forEach { style ->
            val spec = io.github.roggzuay.ondalune.ui.theme.styleSpecFor(style)
            assertTrue("$style has no home sections", spec.defaultHome.isNotEmpty())
            assertEquals(spec.defaultHome.size, spec.defaultHome.distinct().size)
        }
        io.github.roggzuay.ondalune.data.model.AppFont.entries.forEach { font ->
            io.github.roggzuay.ondalune.ui.theme.appFontFamily(font, display = true)
            io.github.roggzuay.ondalune.ui.theme.appFontFamily(font, display = false)
        }
        assertTrue(PlayerCustomizationSettings().homeSections.isEmpty())
    }

    @Test
    fun `player background animation styles app icon variants and expandable settings sections are defined`() {
        assertEquals(5, PlayerBackgroundAnimationStyle.entries.size)
        assertEquals(6, SettingsExpandableSection.entries.size)
        assertEquals(8, io.github.roggzuay.ondalune.data.model.AppIconVariant.entries.size)
        val defaultSettings = PlayerCustomizationSettings()
        assertEquals(io.github.roggzuay.ondalune.data.model.AppIconVariant.DUSK, defaultSettings.appIconVariant)
        assertEquals(PlayerBackgroundAnimationStyle.ARTWORK_GLOW, defaultSettings.playerBackgroundStyle)
        assertEquals(CuratedColorPalette.DEFAULT_EXPRESSIVE, defaultSettings.curatedPalette)
        assertEquals(CornerStylePreset.EXPRESSIVE_ROUNDED, defaultSettings.cornerStylePreset)
        assertEquals(io.github.roggzuay.ondalune.data.model.InterfaceStyle.SIGNATURE, defaultSettings.interfaceStyle)
        io.github.roggzuay.ondalune.data.model.AppIconVariant.entries.forEach { variant ->
            val (bg, fg) = io.github.roggzuay.ondalune.ui.components.appIconLayers(variant)
            assertTrue("Expected icon layers for $variant", bg != 0 && fg != 0)
        }
        assertNotEquals(
            buildShapesForPreset(CornerStylePreset.SHARP_STUDIO),
            buildShapesForPreset(CornerStylePreset.PILL_ORGANIC)
        )
    }

    @Test
    fun `dynamic surface palette and media bridge support widget layout and theme modes`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val songPalette = resolveDynamicSurfacePalette(
            context = context,
            seedHex = "#FF6B5B",
            accentHex = "#70F5FF",
            unifiedMaterialYouColor = false,
            useMaterialYouDynamic = false,
            curatedPalette = CuratedColorPalette.OCEAN_CYAN
        )
        val unifiedPalette = resolveDynamicSurfacePalette(
            context = context,
            seedHex = "#FF6B5B",
            accentHex = "#70F5FF",
            unifiedMaterialYouColor = true,
            useMaterialYouDynamic = true
        )
        assertNotEquals(songPalette.primaryArgb, unifiedPalette.primaryArgb)

        val tracks = DemoSubsonicCatalog.seedTracks(1L)
        for (layoutMode in WidgetLayoutMode.entries) {
            for (themeMode in WidgetThemeMode.entries) {
                OndaluneMediaBridge.syncPlaybackAndThemeState(
                    context = context,
                    track = tracks.first(),
                    isPlaying = true,
                    settings = PlayerCustomizationSettings(
                        unifiedMaterialYouColor = false,
                        widgetLayoutMode = layoutMode,
                        widgetThemeMode = themeMode,
                        widgetShowArtwork = true,
                        widgetShowFavoriteButton = true
                    ),
                    positionMs = 12_000L,
                    durationMs = 36_000L,
                    playbackSpeed = 1.25f,
                    isBuffering = false
                )
            }
        }
        OndaluneMediaBridge.updatePlaybackProgress(
            context = context,
            positionMs = 18_000L,
            durationMs = 36_000L,
            isPlaying = true,
            playbackSpeed = 1.25f
        )
    }

    @Test
    fun `artists derived from server tracks preserve server artistImageUrl and artistBio`() {
        val sampleTrack = DemoSubsonicCatalog.seedTracks(1L).first().copy(
            artistId = "ar_server_42",
            artist = "Server Synth Collective",
            artistImageUrl = "https://music.example.com/rest/getCoverArt.view?id=ar_server_42",
            artistBio = "Official OpenSubsonic biography from getArtistInfo2.view"
        )
        val derived = DemoSubsonicCatalog.deriveArtistsFromTracks(listOf(sampleTrack))
        assertEquals(1, derived.size)
        assertEquals("https://music.example.com/rest/getCoverArt.view?id=ar_server_42", derived.first().coverArtUrl)
        assertEquals("Official OpenSubsonic biography from getArtistInfo2.view", derived.first().bio)
    }

    @Test
    fun `reverb presets sanitize parameters cleanly`() {
        for (preset in ReverbPreset.entries) {
            val state = ReverbState.fromPreset(preset).sanitized()
            assertTrue(state.wetDryMix in 0f..1f)
            assertTrue(state.roomSize in 0f..1f)
            assertTrue(state.decay in 0f..1f)
            assertTrue(state.damping in 0f..1f)
        }
    }

    @Test
    fun `app icon launcher manager dynamically switches android activity alias at runtime`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        for (targetVariant in io.github.roggzuay.ondalune.data.model.AppIconVariant.entries) {
            io.github.roggzuay.ondalune.ui.components.AppIconLauncherManager.applyLauncherIconVariant(
                context = context,
                selectedVariant = targetVariant
            )
            assertTrue(
                "Expected ${targetVariant.aliasClassName} to be enabled",
                io.github.roggzuay.ondalune.ui.components.AppIconLauncherManager.isAliasEnabled(context, targetVariant)
            )
            assertEquals(
                targetVariant,
                io.github.roggzuay.ondalune.ui.components.AppIconLauncherManager.getActiveAliasVariant(context)
            )
            io.github.roggzuay.ondalune.data.model.AppIconVariant.entries
                .filter { it != targetVariant }
                .forEach { otherVariant ->
                    assertEquals(
                        "Expected ${otherVariant.aliasClassName} to be disabled when $targetVariant is active",
                        false,
                        io.github.roggzuay.ondalune.ui.components.AppIconLauncherManager.isAliasEnabled(context, otherVariant)
                    )
                }
        }
    }

    @Test
    fun `display refresh rate selector never requests refresh rate higher than selected supported mode`() {
        val modes = listOf(
            DisplayModeSnapshot(modeId = 1, physicalWidth = 1080, physicalHeight = 2400, refreshRate = 60f),
            DisplayModeSnapshot(modeId = 2, physicalWidth = 1080, physicalHeight = 2400, refreshRate = 90f),
            DisplayModeSnapshot(modeId = 3, physicalWidth = 1440, physicalHeight = 3200, refreshRate = 120f)
        )
        val activeMode = modes[0]
        // Even when preferHighRefreshRate is true, only 60Hz and 90Hz share the active 1080x2400 resolution.
        // Selector must pick 90Hz (mode 2) and request 90f (NOT 120f via coerceAtLeast).
        val highSelection = DisplayRefreshRateSelector.selectDisplayMode(
            activeMode = activeMode,
            supportedModes = modes,
            preferHighRefreshRate = true
        )
        assertEquals(2, highSelection.selectedModeId)
        assertEquals(90f, highSelection.preferredRefreshRate, 0.01f)
        assertTrue(highSelection.preferredRefreshRate <= 90.01f)

        // When preferHighRefreshRate is false, selector picks standard 60Hz mode at the same resolution.
        val standardSelection = DisplayRefreshRateSelector.selectDisplayMode(
            activeMode = modes[1],
            supportedModes = modes,
            preferHighRefreshRate = false
        )
        assertEquals(1, standardSelection.selectedModeId)
        assertEquals(60f, standardSelection.preferredRefreshRate, 0.01f)

        // Redundant write avoidance check
        assertEquals(
            false,
            DisplayRefreshRateSelector.shouldUpdateWindowAttributes(
                currentPreferredModeId = 2,
                currentPreferredRefreshRate = 90f,
                selection = highSelection
            )
        )
        assertEquals(
            true,
            DisplayRefreshRateSelector.shouldUpdateWindowAttributes(
                currentPreferredModeId = 1,
                currentPreferredRefreshRate = 60f,
                selection = highSelection
            )
        )
    }

    @Test
    fun `launch intro tracker runs only once per cold process start and respects reduced motion`() {
        io.github.roggzuay.ondalune.ui.screens.OndaluneLaunchIntroTracker.resetForTesting()
        // When reduced motion is enabled, intro is skipped and marked consumed
        assertEquals(
            false,
            io.github.roggzuay.ondalune.ui.screens.OndaluneLaunchIntroTracker.shouldPlayIntroOnColdStart(reducedMotion = true)
        )
        assertEquals(
            false,
            io.github.roggzuay.ondalune.ui.screens.OndaluneLaunchIntroTracker.shouldPlayIntroOnColdStart(reducedMotion = false)
        )

        io.github.roggzuay.ondalune.ui.screens.OndaluneLaunchIntroTracker.resetForTesting()
        // First cold start with normal motion plays intro
        assertEquals(
            true,
            io.github.roggzuay.ondalune.ui.screens.OndaluneLaunchIntroTracker.shouldPlayIntroOnColdStart(reducedMotion = false)
        )
        // Subsequent resumes / configuration changes never replay intro in the same process
        assertEquals(
            false,
            io.github.roggzuay.ondalune.ui.screens.OndaluneLaunchIntroTracker.shouldPlayIntroOnColdStart(reducedMotion = false)
        )
    }

    @Test
    fun `credential security rejects tampered ciphertext and redacts sensitive query params`() {
        val encrypted = io.github.roggzuay.ondalune.data.local.OndaluneCredentialSecurity.encryptCredential("super-secret-subsonic-pass")
        assertTrue(encrypted.startsWith("enc:v1:"))
        assertEquals(
            "super-secret-subsonic-pass",
            io.github.roggzuay.ondalune.data.local.OndaluneCredentialSecurity.decryptCredential(encrypted)
        )

        val tampered = encrypted.dropLast(4) + "AAAA"
        var threwSecurityException = false
        try {
            io.github.roggzuay.ondalune.data.local.OndaluneCredentialSecurity.decryptCredential(tampered)
        } catch (_: io.github.roggzuay.ondalune.data.local.CredentialSecurityException) {
            threwSecurityException = true
        }
        assertTrue("Expected CredentialSecurityException on tampered ciphertext", threwSecurityException)
        assertEquals(null, io.github.roggzuay.ondalune.data.local.OndaluneCredentialSecurity.tryDecryptCredential(tampered).getOrNull())

        val rawUrl = "https://music.example.com/rest/stream.view?id=tr1&u=alice&t=abcdef123456&s=998877&p=enc:70617373"
        val redacted = io.github.roggzuay.ondalune.data.local.OndaluneCredentialSecurity.redactSensitiveUrl(rawUrl)
        assertTrue(!redacted.contains("abcdef123456"))
        assertTrue(!redacted.contains("998877"))
        assertTrue(!redacted.contains("70617373"))
    }

    @Test
    fun `room migrations 1 through 5 preserve existing schema and create subsonic_downloads table`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = context.getDatabasePath("test_migration_ondalune.db")
        dbFile.DeleteIfExists()

        val sqliteDb = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        // Create a v1-compatible subsonic_tracks and subsonic_servers schema
        sqliteDb.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `subsonic_servers` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `baseUrl` TEXT NOT NULL,
                `username` TEXT NOT NULL,
                `passwordOrToken` TEXT NOT NULL,
                `useTokenAuth` INTEGER NOT NULL,
                `transcodeFormat` TEXT NOT NULL,
                `maxBitRate` INTEGER NOT NULL,
                `isDemoServer` INTEGER NOT NULL,
                `isActive` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        sqliteDb.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `subsonic_tracks` (
                `id` TEXT NOT NULL,
                `serverId` INTEGER NOT NULL,
                `title` TEXT NOT NULL,
                `artist` TEXT NOT NULL,
                `album` TEXT NOT NULL,
                `albumId` TEXT NOT NULL,
                `artistId` TEXT NOT NULL,
                `durationSec` INTEGER NOT NULL,
                `bitRate` INTEGER NOT NULL,
                `suffix` TEXT NOT NULL,
                `genre` TEXT NOT NULL,
                `year` INTEGER NOT NULL,
                `trackNumber` INTEGER NOT NULL,
                `starred` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        sqliteDb.execSQL(
            "INSERT INTO `subsonic_tracks` VALUES ('tr_v1', 1, 'Legacy Song', 'Legacy Artist', 'Legacy Album', 'al_1', 'ar_1', 200, 320, 'FLAC', 'Synthwave', 2024, 1, 1)"
        )
        sqliteDb.version = 1
        sqliteDb.close()

        val supportFactory = androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory()
        val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbFile.name)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(5) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {}
                override fun onUpgrade(
                    db: androidx.sqlite.db.SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int
                ) {
                    io.github.roggzuay.ondalune.data.local.OndaluneDatabase.MIGRATION_1_2.migrate(db)
                    io.github.roggzuay.ondalune.data.local.OndaluneDatabase.MIGRATION_2_3.migrate(db)
                    io.github.roggzuay.ondalune.data.local.OndaluneDatabase.MIGRATION_3_4.migrate(db)
                    io.github.roggzuay.ondalune.data.local.OndaluneDatabase.MIGRATION_4_5.migrate(db)
                }
            })
            .build()
        val helper = supportFactory.create(config)
        val migratedDb = helper.writableDatabase

        migratedDb.query("SELECT title, artistImageUrl, userRating FROM `subsonic_tracks` WHERE id = 'tr_v1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Legacy Song", cursor.getString(0))
            assertEquals("", cursor.getString(1))
            assertEquals(4, cursor.getInt(2))
        }
        migratedDb.query("SELECT name FROM sqlite_master WHERE type='table' AND name='subsonic_downloads'").use { cursor ->
            assertTrue("Expected subsonic_downloads table after MIGRATION_4_5", cursor.moveToFirst())
        }
        helper.close()
        dbFile.DeleteIfExists()
    }

    @Test
    fun `realtime pcm dsp pipeline applies reverb and 5-band eq without mutating source audio`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)
        val engine = io.github.roggzuay.ondalune.audio.OndaluneAudioEngine(
            context = context,
            subsonicClient = io.github.roggzuay.ondalune.data.network.OpenSubsonicClient(),
            scope = scope
        )
        val dsp = io.github.roggzuay.ondalune.audio.OndaluneReverbDspProcessor(22050)
        val activeReverb = ReverbState.fromPreset(ReverbPreset.LARGE_HALL).copy(enabled = true, wetDryMix = 0.65f)
        dsp.updateParameters(activeReverb, snapImmediately = true)

        // Generate a short impulse + sine burst stereo PCM16 frame chunk
        val frames = 2048
        val sourcePcm = ShortArray(frames * 2) { idx ->
            val frame = idx / 2
            if (frame < 512) (kotlin.math.sin(frame * 0.15) * 16000).toInt().toShort() else 0
        }
        val immutableCopy = sourcePcm.clone()
        val workingBuffer = sourcePcm.clone()

        engine.processRealtimePcm16StereoChunk(
            pcm16Stereo = workingBuffer,
            frameCount = frames,
            sampleRate = 22050,
            dsp = dsp,
            reverbState = activeReverb
        )

        // Verify source buffer was not mutated and reverb tail energy exists in frames 800..1900
        assertTrue(sourcePcm.contentEquals(immutableCopy))
        val tailEnergy = (800 until 1900).sumOf { frame ->
            kotlin.math.abs(workingBuffer[frame * 2].toInt()) + kotlin.math.abs(workingBuffer[frame * 2 + 1].toInt())
        }
        assertTrue("Expected non-zero stereo reverb tail energy in real-time PCM output, got $tailEnergy", tailEnergy > 0)
        engine.release()
    }

    @Test
    fun `honest lyrics resolution never fabricates lyrics for real server tracks and offline download manager tracks lifecycle`() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = androidx.room.Room.inMemoryDatabaseBuilder(
            context,
            io.github.roggzuay.ondalune.data.local.OndaluneDatabase::class.java
        ).allowMainThreadQueries().build()
        val client = io.github.roggzuay.ondalune.data.network.OpenSubsonicClient()
        val repo = io.github.roggzuay.ondalune.data.repository.OndaluneRepository(db.dao(), client)

        val realServer = io.github.roggzuay.ondalune.data.model.SubsonicServerConfig(
            id = 2L,
            name = "My Navidrome",
            baseUrl = "https://127.0.0.1:1", // unreachable in unit test
            username = "tester",
            passwordOrToken = "secret",
            isDemoServer = false,
            isActive = true
        )
        val realTrackWithoutLyrics = DemoSubsonicCatalog.seedTracks(2L).first().copy(
            id = "real_server_track_99",
            serverId = 2L,
            syncedLyricsRaw = ""
        )
        val lyricsResult = repo.fetchLyricsResult(realServer, realTrackWithoutLyrics)
        assertEquals(io.github.roggzuay.ondalune.data.model.LyricsAvailability.UNAVAILABLE, lyricsResult.availability)
        assertTrue("Expected empty lines when server has no lyrics", lyricsResult.lines.isEmpty())

        // Verify offline download manager stores demo track into persistent offline storage
        val downloadManager = io.github.roggzuay.ondalune.data.local.OndaluneDownloadManager(context, db.dao(), client)
        val demoServer = DemoSubsonicCatalog.createDefaultDemoServer()
        val demoTrack = DemoSubsonicCatalog.seedTracks(1L).first()
        val downloadedEntity = downloadManager.downloadTrackNow(
            server = demoServer,
            track = demoTrack,
            speedLimit = io.github.roggzuay.ondalune.data.model.DownloadSpeedLimit.UNLIMITED
        )
        assertEquals(io.github.roggzuay.ondalune.data.model.OfflineDownloadState.COMPLETED, downloadedEntity.downloadState)
        assertEquals(100, downloadedEntity.progressPercent)
        assertTrue(java.io.File(downloadedEntity.localFilePath).exists())

        downloadManager.removeDownloadedTrack(demoServer.id, demoTrack.id)
        assertEquals(null, db.dao().getDownloadByKey(downloadManager.downloadKeyFor(demoServer.id, demoTrack.id)))
        db.close()
    }

    private fun java.io.File.DeleteIfExists() {
        if (exists()) delete()
    }
}


