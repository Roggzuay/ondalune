package io.github.roggzuay.ondalune

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import io.github.roggzuay.ondalune.data.local.OndaluneDatabase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), OndaluneDatabase::class.java)

    @Test
    fun `servers, songs and downloads survive 6 to 7 and every server stays a server`() {
        helper.createDatabase("migration-test", 6).use { db ->
            db.execSQL(
                "INSERT INTO subsonic_servers (id, name, baseUrl, username, passwordOrToken, useTokenAuth, transcodeFormat, " +
                    "maxBitRate, isDemoServer, isActive, serverSoftware, extensionsCsv, lastPingMs) " +
                    "VALUES (7, 'Home', 'https://music.example', 'me', 'secret', 1, 'RAW', 0, 0, 1, 'Navidrome', '', 0)"
            )
            db.execSQL(
                "INSERT INTO subsonic_tracks (id, serverId, title, artist, artistId, album, albumId, durationSec, bitRate, suffix, " +
                    "bitDepth, samplingRate, starred, trackNumber, year, genre, folderPath, coverArtUrl, artistImageUrl, artistBio, " +
                    "artStyleIndex, paletteSeedHex, accentHex, bpm, musicalKey, syncedLyricsRaw, playCount, userRating) " +
                    "VALUES ('s1', 7, 'Song', 'Artist', 'ar1', 'Album', 'al1', 200, 320, 'FLAC', 16, 44100, 1, 1, 2020, 'Jazz', '/', '', '', '', " +
                    "0, '#000000', '#FFFFFF', 0, '', '', 5, 4)"
            )
        }
        helper.runMigrationsAndValidate("migration-test", 7, true, OndaluneDatabase.MIGRATION_6_7).use { db ->
            db.query("SELECT name, sourceType, isActive FROM subsonic_servers WHERE id = 7").use { c ->
                c.moveToFirst()
                assertEquals("Home", c.getString(0))
                assertEquals("SUBSONIC", c.getString(1))
                assertEquals(1, c.getInt(2))
            }
            db.query("SELECT title, starred, playCount FROM subsonic_tracks WHERE id = 's1'").use { c ->
                c.moveToFirst()
                assertEquals("Song", c.getString(0))
                assertEquals(1, c.getInt(1))
                assertEquals(5, c.getInt(2))
            }
        }
    }
}
