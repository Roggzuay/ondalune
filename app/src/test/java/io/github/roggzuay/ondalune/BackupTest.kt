package io.github.roggzuay.ondalune

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.roggzuay.ondalune.data.backup.BackupData
import io.github.roggzuay.ondalune.data.backup.OndaluneBackup
import io.github.roggzuay.ondalune.data.backup.PortableEdit
import io.github.roggzuay.ondalune.data.backup.PortableProfile
import io.github.roggzuay.ondalune.data.local.OndaluneDatabase
import io.github.roggzuay.ondalune.data.model.PitchCorrectionMode
import io.github.roggzuay.ondalune.data.model.ReverbPreset
import io.github.roggzuay.ondalune.data.model.ReverbState
import io.github.roggzuay.ondalune.data.model.SoundProfileScope
import io.github.roggzuay.ondalune.data.model.SoundSnapshot
import io.github.roggzuay.ondalune.data.model.SpeedPitchState
import io.github.roggzuay.ondalune.data.model.SubsonicServerConfig
import io.github.roggzuay.ondalune.data.model.TrackOverride
import io.github.roggzuay.ondalune.data.model.snapshot
import io.github.roggzuay.ondalune.data.model.soundProfileOf
import io.github.roggzuay.ondalune.ui.viewmodel.BackupController
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A backup holds how Ondalune is set up. It must carry the settings and the saved sounds across,
 * key them by the server's address rather than a row number, and never, ever carry a password.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BackupTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db: OndaluneDatabase
    private lateinit var prefs: android.content.SharedPreferences

    private val slowed = SoundSnapshot(
        speedPitch = SpeedPitchState(speed = 0.80f, pitchMode = PitchCorrectionMode.VARISPEED_NATURAL_LINKED),
        reverb = ReverbState.fromPreset(ReverbPreset.AMBIENT).copy(enabled = true).sanitized()
    )

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder(app, OndaluneDatabase::class.java).allowMainThreadQueries().build()
        prefs = app.getSharedPreferences("backup_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }

    @After
    fun close() = db.close()

    private fun controller(now: Long = 1_000L) =
        BackupController(prefs, db.dao(), appVersion = "2.1.0", now = { now })

    private suspend fun addServer(name: String, url: String, user: String): Long =
        db.dao().insertServer(
            SubsonicServerConfig(
                name = name,
                baseUrl = url,
                username = user,
                passwordOrToken = "hunter2",
                serverSoftware = "Navidrome"
            )
        )

    // ---- What is kept, and what is never kept ----

    @Test
    fun `settings worth carrying are kept`() {
        assertTrue(OndaluneBackup.keeps("cfg_crossfeed"))
        assertTrue(OndaluneBackup.keeps("eq_curves"))
        assertTrue(OndaluneBackup.keeps("dsp_speed"))
        assertTrue(OndaluneBackup.keeps("reverb_preset"))
        assertTrue(OndaluneBackup.keeps("repeat_mode"))
    }

    @Test
    fun `this phone's own business is left behind`() {
        assertFalse(OndaluneBackup.keeps("jellyfin_device_id"))
        assertFalse(OndaluneBackup.keeps("saved_queue_ids"))
        assertFalse(OndaluneBackup.keeps("last_played_track_id"))
        assertFalse(OndaluneBackup.keeps("onboarding_completed"))
        assertFalse(OndaluneBackup.keeps("whats_new_seen"))
        assertFalse(OndaluneBackup.keeps("hint_speed_hold_reset"))
        assertFalse(OndaluneBackup.keeps("cfg_initialized"))
    }

    @Test
    fun `no password ever reaches the file`() = runBlocking {
        addServer("Home", "https://music.example", "me")
        val text = controller().let { it.encode(it.build()) }
        assertFalse(text, text.contains("hunter2"))
        assertFalse(text, text.contains("passwordOrToken"))
    }

    @Test
    fun `the file never says where your music lives`() = runBlocking {
        val id = addServer("Home", "https://music.example/", "rogg")
        db.dao().upsertSoundProfile(soundProfileOf(id, SoundProfileScope.TRACK, "s1", slowed, 5L))
        db.dao().upsertOverride(TrackOverride(id, "s1", title = "Fixed"))
        val controller = controller()
        val text = controller.encode(controller.build())
        assertFalse(text, text.contains("music.example"))
        assertFalse(text, text.contains("rogg"))
        assertFalse(text, text.contains("hunter2"))
    }

    @Test
    fun `a server travels under a one-way name, the same on any phone`() = runBlocking {
        val id = addServer("Home", "https://music.example/", "me")
        db.dao().upsertSoundProfile(soundProfileOf(id, SoundProfileScope.TRACK, "s1", slowed, 5L))
        val key = controller().build().serverKeys.single()
        // A SHA-256 of the address and username: 64 hex characters, and neither of them readable.
        assertEquals(64, key.length)
        assertTrue(key, key.all { it.isDigit() || it in 'a'..'f' })
        assertFalse(key, key.contains("music.example"))
    }

    @Test
    fun `the same server on another phone works out the same name`() = runBlocking {
        val here = addServer("Home", "https://music.example/", "me")
        db.dao().upsertSoundProfile(soundProfileOf(here, SoundProfileScope.TRACK, "s1", slowed, 5L))
        val file = controller().build()

        // Another phone: the same server added under a different name, with a trailing slash gone.
        db.dao().deleteSoundProfilesForServer(here)
        db.dao().deleteServer(here)
        val there = addServer("My music", "https://music.example", "me")
        val result = controller().apply(file)
        assertEquals(1, result.soundProfiles)
        assertEquals(there, db.dao().getSoundProfilesOnce().single().serverId)
    }

    @Test
    fun `a file from an earlier preview, with the address in the clear, still restores`() = runBlocking {
        val id = addServer("Home", "https://music.example", "me")
        val data = BackupData(
            createdAt = 1L,
            appVersion = "2.1.0",
            soundProfiles = listOf(
                PortableProfile.of(
                    soundProfileOf(id, SoundProfileScope.TRACK, "s1", slowed, 5L),
                    "https://music.example|me"
                )
            )
        )
        assertEquals(1, controller().apply(data).soundProfiles)
    }

    // ---- The file itself ----

    @Test
    fun `a file written out reads straight back in`() = runBlocking {
        val id = addServer("Home", "https://music.example", "me")
        db.dao().upsertSoundProfile(soundProfileOf(id, SoundProfileScope.TRACK, "s1", slowed, 5L))
        db.dao().upsertOverride(TrackOverride(id, "s1", title = "Proper title", year = 1979))
        prefs.edit().putString("cfg_crossfeed", "NATURAL").putBoolean("cfg_keep_sound_history", false).commit()

        val controller = controller()
        val back = OndaluneBackup.decode(controller.encode(controller.build()))!!
        assertEquals("NATURAL", back.settings["cfg_crossfeed"])
        assertEquals(false, back.settings["cfg_keep_sound_history"])
        assertEquals(1, back.soundProfiles.size)
        assertEquals("s1", back.soundProfiles.first().scopeId)
        assertEquals("Proper title", back.trackEdits.first().title)
        assertEquals(1979, back.trackEdits.first().year)
    }

    @Test
    fun `a saved sound comes back exactly as it went in`() = runBlocking {
        val id = addServer("Home", "https://music.example", "me")
        db.dao().upsertSoundProfile(soundProfileOf(id, SoundProfileScope.TRACK, "s1", slowed, 5L))
        val controller = controller()
        val back = OndaluneBackup.decode(controller.encode(controller.build()))!!
        val restored = back.soundProfiles.first().toProfile(id).snapshot
        assertFalse(restored.differsFrom(slowed))
    }

    @Test
    fun `something that is not a backup is refused rather than half read`() {
        assertNull(OndaluneBackup.decode("{}"))
        assertNull(OndaluneBackup.decode("not json at all"))
        assertNull(OndaluneBackup.decode("""{"format":"someone-elses-backup","version":1}"""))
        assertNull(OndaluneBackup.decode("""{"format":"ondalune-backup","version":99}"""))
    }

    @Test
    fun `a file smuggling a password in its settings has it dropped on the way in`() {
        val text = """
            {"format":"ondalune-backup","version":1,"createdAt":1,"appVersion":"x",
             "settings":{"cfg_crossfeed":"WIDE","jellyfin_device_id":"abc","saved_queue_ids":"1,2"}}
        """.trimIndent()
        val back = OndaluneBackup.decode(text)!!
        assertEquals("WIDE", back.settings["cfg_crossfeed"])
        assertNull(back.settings["jellyfin_device_id"])
        assertNull(back.settings["saved_queue_ids"])
    }

    // ---- Putting one back ----

    @Test
    fun `a restore puts the settings and the saved sounds back`() = runBlocking {
        val id = addServer("Home", "https://music.example", "me")
        val data = BackupData(
            createdAt = 1L,
            appVersion = "2.1.0",
            settings = mapOf("cfg_crossfeed" to "WIDE"),
            soundProfiles = listOf(
                PortableProfile.of(
                    soundProfileOf(id, SoundProfileScope.TRACK, "s1", slowed, 5L),
                    "https://music.example|me"
                )
            ),
            trackEdits = listOf(PortableEdit("https://music.example|me", "s1", "Fixed", null, null, 1980))
        )
        val result = controller().apply(data)
        assertEquals(1, result.settings)
        assertEquals(1, result.soundProfiles)
        assertEquals(1, result.trackEdits)
        assertEquals(0, result.skipped)
        assertEquals("WIDE", prefs.getString("cfg_crossfeed", null))
        assertEquals("s1", db.dao().getSoundProfilesOnce().single().scopeId)
        assertEquals("Fixed", db.dao().getOverridesOnce().single().title)
    }

    @Test
    fun `a saved sound lands on the right server, whatever row number it has here`() = runBlocking {
        // The backup came from a phone where this server happened to be row 1; here it is row 2.
        addServer("Other", "https://other.example", "me")
        val home = addServer("Home", "https://music.example", "me")
        val data = BackupData(
            createdAt = 1L,
            appVersion = "2.1.0",
            soundProfiles = listOf(
                PortableProfile.of(
                    soundProfileOf(1L, SoundProfileScope.TRACK, "s1", slowed, 5L),
                    "https://music.example|me"
                )
            )
        )
        controller().apply(data)
        assertEquals(home, db.dao().getSoundProfilesOnce().single().serverId)
    }

    @Test
    fun `a sound for a server you haven't added here is left, and said so`() = runBlocking {
        addServer("Home", "https://music.example", "me")
        val data = BackupData(
            createdAt = 1L,
            appVersion = "2.1.0",
            soundProfiles = listOf(
                PortableProfile.of(
                    soundProfileOf(1L, SoundProfileScope.TRACK, "s1", slowed, 5L),
                    "https://somewhere.else|you"
                )
            )
        )
        val result = controller().apply(data)
        assertEquals(0, result.soundProfiles)
        assertEquals(1, result.skipped)
        assertTrue(result.message(), result.message().contains("haven't added"))
        assertTrue(db.dao().getSoundProfilesOnce().isEmpty())
    }

    @Test
    fun `a restore adds to what is here instead of wiping it`() = runBlocking {
        val id = addServer("Home", "https://music.example", "me")
        db.dao().upsertSoundProfile(soundProfileOf(id, SoundProfileScope.TRACK, "already-here", slowed, 1L))
        val data = BackupData(
            createdAt = 1L,
            appVersion = "2.1.0",
            soundProfiles = listOf(
                PortableProfile.of(
                    soundProfileOf(id, SoundProfileScope.TRACK, "from-the-file", slowed, 5L),
                    "https://music.example|me"
                )
            )
        )
        controller().apply(data)
        assertEquals(
            setOf("already-here", "from-the-file"),
            db.dao().getSoundProfilesOnce().map { it.scopeId }.toSet()
        )
    }

    @Test
    fun `an empty backup says there was nothing to put back`() = runBlocking {
        val result = controller().apply(BackupData(createdAt = 1L, appVersion = "2.1.0"))
        assertEquals("Nothing to put back", result.message())
    }

    @Test
    fun `the summary counts things in plain words`() {
        val one = BackupData(
            createdAt = 1L,
            appVersion = "x",
            soundProfiles = listOf(PortableProfile("k", "TRACK", "s1", io.github.roggzuay.ondalune.data.model.StoredSound(), null, 1L))
        )
        assertTrue(one.summary(), one.summary().contains("1 saved sound"))
    }
}
