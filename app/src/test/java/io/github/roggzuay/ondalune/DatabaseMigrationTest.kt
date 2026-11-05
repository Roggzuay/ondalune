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

    @Test
    fun `7 to 8 keeps playlists, clears the made-up 118 BPM and adds folder tables`() {
        helper.createDatabase("migration-test-8", 7).use { db ->
            db.execSQL(
                "INSERT INTO subsonic_tracks (id, serverId, title, artist, artistId, album, albumId, durationSec, bitRate, suffix, " +
                    "bitDepth, samplingRate, starred, trackNumber, year, genre, folderPath, coverArtUrl, artistImageUrl, artistBio, " +
                    "artStyleIndex, paletteSeedHex, accentHex, bpm, musicalKey, syncedLyricsRaw, playCount, userRating) " +
                    "VALUES ('s1', 7, 'Song', 'Artist', 'ar1', 'Album', 'al1', 200, 320, 'FLAC', 16, 44100, 1, 1, 2020, 'Jazz', '/', '', '', '', " +
                    "0, '#000000', '#FFFFFF', 118, 'F Minor', '', 5, 4), " +
                    "('s2', 7, 'Fast', 'Artist', 'ar1', 'Album', 'al1', 200, 320, 'FLAC', 16, 44100, 0, 2, 2020, 'Jazz', '/', '', '', '', " +
                    "0, '#000000', '#FFFFFF', 128, 'A Major', '', 0, 4)"
            )
            db.execSQL(
                "INSERT INTO subsonic_playlists (id, serverId, name, comment, trackIdsCsv, paletteSeedHex, accentHex, updatedAtMs) " +
                    "VALUES ('pl_1', 7, 'Mine', '', 's1,s2', '#000000', '#FFFFFF', 1)"
            )
        }
        helper.runMigrationsAndValidate("migration-test-8", 8, true, OndaluneDatabase.MIGRATION_7_8).use { db ->
            db.query("SELECT id, bpm, musicalKey, starred, playCount FROM subsonic_tracks ORDER BY id").use { c ->
                c.moveToFirst()
                assertEquals(0, c.getInt(1))
                assertEquals("", c.getString(2))
                assertEquals(1, c.getInt(3))
                assertEquals(5, c.getInt(4))
                c.moveToNext()
                assertEquals("a real tempo stays", 128, c.getInt(1))
                assertEquals("A Major", c.getString(2))
            }
            db.query("SELECT name, trackIdsCsv, remoteId, readOnly FROM subsonic_playlists").use { c ->
                c.moveToFirst()
                assertEquals("Mine", c.getString(0))
                assertEquals("s1,s2", c.getString(1))
                assertEquals("", c.getString(2))
                assertEquals(0, c.getInt(3))
            }
            db.query("SELECT COUNT(*) FROM local_folders").use { c -> c.moveToFirst(); assertEquals(0, c.getInt(0)) }
        }
    }
}
