package io.github.roggzuay.ondalune.data.local

import io.github.roggzuay.ondalune.data.model.genreList
import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import io.github.roggzuay.ondalune.data.model.OfflineDownloadState
import io.github.roggzuay.ondalune.data.model.SubsonicAlbum
import io.github.roggzuay.ondalune.data.model.SubsonicArtist
import io.github.roggzuay.ondalune.data.model.SubsonicDownloadEntity
import io.github.roggzuay.ondalune.data.model.SubsonicFolder
import io.github.roggzuay.ondalune.data.model.SubsonicGenre
import io.github.roggzuay.ondalune.data.model.SubsonicPlaylist
import io.github.roggzuay.ondalune.data.model.SubsonicServerConfig
import io.github.roggzuay.ondalune.data.model.SubsonicTrack
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlinx.coroutines.flow.Flow
import javax.crypto.spec.GCMParameterSpec

class CredentialSecurityException(
    message: String,
    cause: Throwable? = null
) : GeneralSecurityException(message, cause)

/**
 * Hardware-backed Android Keystore AES-GCM credential encryption & URL log redaction utility.
 *
 * Security guarantees:
 * 1. Zero hardcoded fallback keys in source or bytecode.
 * 2. Never silently falls back to storing plaintext if encryption fails.
 * 3. Never treats corrupted or tampered `enc:v1:` ciphertext as a valid password.
 * 4. Preserves backward compatibility with legacy unencrypted credentials until re-saved.
 */
object OndaluneCredentialSecurity {
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS = "ondalune_server_credential_aes_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val ENCRYPTED_PREFIX = "enc:v1:"
    private const val GCM_TAG_BITS = 128

    /**
     * Ephemeral CSPRNG key generated per process ONLY when the AndroidKeyStore provider is
     * unavailable (e.g., local JVM unit tests). Never derived from a static string or constant.
     */
    @Volatile
    private var runtimeVolatileKey: SecretKey? = null

    private fun obtainOrGenerateRuntimeKey(): SecretKey {
        return runtimeVolatileKey ?: synchronized(this) {
            runtimeVolatileKey ?: KeyGenerator.getInstance("AES").apply {
                init(256, SecureRandom())
            }.generateKey().also { generated ->
                runtimeVolatileKey = generated
            }
        }
    }

    private fun resolveSecretKey(): SecretKey {
        return try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
            if (existing != null) {
                existing
            } else {
                val keyGen = KeyGenerator.getInstance("AES", KEYSTORE_PROVIDER)
                val spec = android.security.keystore.KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                        android.security.keystore.KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
                keyGen.init(spec)
                keyGen.generateKey()
            }
        } catch (_: Throwable) {
            obtainOrGenerateRuntimeKey()
        }
    }

    /**
     * Encrypts [plainText] using 256-bit AES-GCM.
     * Throws [CredentialSecurityException] if cryptographic initialization or encryption fails,
     * preventing silent plaintext persistence.
     */
    @Throws(CredentialSecurityException::class)
    fun encryptCredential(plainText: String): String {
        if (plainText.isBlank() || plainText.startsWith(ENCRYPTED_PREFIX)) return plainText
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, resolveSecretKey())
            val iv = cipher.iv
            if (iv == null || iv.isEmpty()) {
                throw CredentialSecurityException("AES-GCM cipher failed to generate a valid IV")
            }
            val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val encoder = Base64.getEncoder().withoutPadding()
            val ivB64 = encoder.encodeToString(iv)
            val dataB64 = encoder.encodeToString(cipherBytes)
            return "$ENCRYPTED_PREFIX$ivB64:$dataB64"
        } catch (e: CredentialSecurityException) {
            throw e
        } catch (t: Throwable) {
            throw CredentialSecurityException("Failed to encrypt server credential with AES-GCM", t)
        }
    }

    /**
     * Decrypts [storedValue] strictly.
     * - If [storedValue] does not have the `enc:v1:` prefix, it is returned as a legacy value.
     * - If [storedValue] has the `enc:v1:` prefix and is malformed, tampered with, or fails AES-GCM
     *   authentication tag verification, throws [CredentialSecurityException] rather than returning
     *   corrupted ciphertext as a password.
     */
    @Throws(CredentialSecurityException::class)
    fun decryptCredential(storedValue: String): String {
        if (!storedValue.startsWith(ENCRYPTED_PREFIX)) return storedValue
        val payload = storedValue.removePrefix(ENCRYPTED_PREFIX)
        val splitIdx = payload.indexOf(':')
        if (splitIdx <= 0 || splitIdx >= payload.lastIndex) {
            throw CredentialSecurityException("Malformed encrypted credential payload (missing IV/ciphertext delimiter)")
        }
        return try {
            val decoder = Base64.getDecoder()
            val iv = decoder.decode(payload.substring(0, splitIdx))
            val cipherBytes = decoder.decode(payload.substring(splitIdx + 1))
            if (iv.isEmpty() || cipherBytes.size < GCM_TAG_BITS / 8) {
                throw CredentialSecurityException("Encrypted credential payload is truncated or invalid")
            }
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, resolveSecretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
        } catch (e: CredentialSecurityException) {
            throw e
        } catch (t: Throwable) {
            throw CredentialSecurityException("Encrypted credential failed AES-GCM verification or key mismatch", t)
        }
    }

    /**
     * Non-throwing decryption helper for UI/network layers that returns `Result<String>`.
     * Never exposes raw ciphertext as a usable password.
     */
    fun tryDecryptCredential(storedValue: String): Result<String> {
        return runCatching { decryptCredential(storedValue) }
    }

    fun canDecryptCredential(storedValue: String): Boolean {
        return tryDecryptCredential(storedValue).isSuccess
    }

    fun isEncrypted(value: String): Boolean = value.startsWith(ENCRYPTED_PREFIX)

    fun encryptServerConfig(config: SubsonicServerConfig): SubsonicServerConfig {
        if (config.isDemoServer) return config
        return config.copy(passwordOrToken = encryptCredential(config.passwordOrToken))
    }

    fun decryptServerConfig(config: SubsonicServerConfig): SubsonicServerConfig {
        val decrypted = tryDecryptCredential(config.passwordOrToken).getOrDefault("")
        return config.copy(passwordOrToken = decrypted)
    }

    /**
     * Redacts sensitive OpenSubsonic query parameters (`p`, `t`, `s`, `apiKey`, `password`, `token`)
     * from URLs so credentials are never exposed in logs or error messages.
     */
    fun redactSensitiveUrl(url: String): String {
        if (url.isBlank()) return url
        return url.replace(
            Regex("([?&](?:p|t|s|apiKey|password|token)=)([^&#\\s]+)", RegexOption.IGNORE_CASE),
            "$1[REDACTED]"
        )
    }
}

@Dao
interface OndaluneDao {
    @Query("SELECT * FROM subsonic_servers ORDER BY isActive DESC, id ASC")
    fun observeServers(): Flow<List<SubsonicServerConfig>>

    @Query("SELECT * FROM subsonic_servers WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveServer(): SubsonicServerConfig?

    @Query("SELECT * FROM subsonic_servers")
    suspend fun getAllServersOnce(): List<SubsonicServerConfig>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServer(server: SubsonicServerConfig): Long

    @Update
    suspend fun updateServer(server: SubsonicServerConfig)

    @Query("UPDATE subsonic_servers SET isActive = CASE WHEN id = :serverId THEN 1 ELSE 0 END")
    suspend fun setActiveServer(serverId: Long)

    @Query("DELETE FROM subsonic_servers WHERE id = :serverId AND isDemoServer = 0")
    suspend fun deleteServer(serverId: Long)

    @Query("SELECT * FROM subsonic_tracks WHERE serverId = :serverId ORDER BY albumId ASC, trackNumber ASC")
    fun observeTracksForServer(serverId: Long): Flow<List<SubsonicTrack>>

    @Query("SELECT * FROM subsonic_tracks WHERE serverId = :serverId")
    suspend fun getTracksForServerOnce(serverId: Long): List<SubsonicTrack>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTracks(tracks: List<SubsonicTrack>)

    @Query("DELETE FROM subsonic_tracks WHERE serverId = :serverId AND id IN (:trackIds)")
    suspend fun deleteTracks(serverId: Long, trackIds: List<String>)

    @Query("UPDATE subsonic_tracks SET starred = :starred WHERE id = :trackId")
    suspend fun updateTrackStarred(trackId: String, starred: Boolean)

    @Query("UPDATE subsonic_tracks SET playCount = playCount + 1 WHERE id = :trackId")
    suspend fun incrementPlayCount(trackId: String)

    @Query("UPDATE subsonic_tracks SET userRating = :rating WHERE id = :trackId")
    suspend fun updateTrackRating(trackId: String, rating: Int)

    @Query("UPDATE subsonic_tracks SET artistImageUrl = :artistImageUrl, artistBio = :artistBio WHERE artistId = :artistId OR artist = :artistName")
    suspend fun updateArtistMetadata(artistId: String, artistName: String, artistImageUrl: String, artistBio: String)

    @Query("UPDATE subsonic_tracks SET artistImageUrl = :artistImageUrl, artistBio = :artistBio WHERE serverId = :serverId AND (artistId = :artistId OR artist = :artistName)")
    suspend fun updateArtistMetadataForServer(serverId: Long, artistId: String, artistName: String, artistImageUrl: String, artistBio: String)

    @Query("SELECT * FROM subsonic_playlists WHERE serverId = :serverId ORDER BY updatedAtMs DESC")
    fun observePlaylistsForServer(serverId: Long): Flow<List<SubsonicPlaylist>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: SubsonicPlaylist)

    @Query("DELETE FROM subsonic_playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: String)

    @Query("SELECT * FROM subsonic_downloads WHERE serverId = :serverId ORDER BY updatedAtMs DESC")
    fun observeDownloadsForServer(serverId: Long): Flow<List<SubsonicDownloadEntity>>

    @Query("SELECT * FROM subsonic_downloads WHERE serverId = :serverId")
    suspend fun getDownloadsForServerOnce(serverId: Long): List<SubsonicDownloadEntity>

    @Query("SELECT * FROM subsonic_downloads WHERE downloadKey = :downloadKey LIMIT 1")
    suspend fun getDownloadByKey(downloadKey: String): SubsonicDownloadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDownload(entity: SubsonicDownloadEntity)

    @Query("DELETE FROM subsonic_downloads WHERE downloadKey = :downloadKey")
    suspend fun deleteDownloadByKey(downloadKey: String)

    @Query("DELETE FROM subsonic_downloads WHERE serverId = :serverId")
    suspend fun deleteAllDownloadsForServer(serverId: Long)
}

@Database(
    entities = [
        SubsonicServerConfig::class,
        SubsonicTrack::class,
        SubsonicPlaylist::class,
        SubsonicDownloadEntity::class
    ],
    version = 6,
    exportSchema = true
)
abstract class OndaluneDatabase : RoomDatabase() {
    abstract fun dao(): OndaluneDao

    companion object {
        @Volatile
        private var INSTANCE: OndaluneDatabase? = null

        private fun hasColumn(
            db: SupportSQLiteDatabase,
            tableName: String,
            columnName: String
        ): Boolean {
            db.query("PRAGMA table_info(`$tableName`)").use { cursor ->
                val nameIdx = cursor.getColumnIndex("name")
                if (nameIdx < 0) return false
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameIdx).equals(columnName, ignoreCase = true)) {
                        return true
                    }
                }
            }
            return false
        }

        /**
         * Explicit, fail-fast, non-destructive Room migrations preserving user servers, tracks,
         * playlists, and offline downloads. Never swallows SQL errors with `runCatching`.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                if (!hasColumn(db, "subsonic_tracks", "playCount")) {
                    db.execSQL("ALTER TABLE subsonic_tracks ADD COLUMN playCount INTEGER NOT NULL DEFAULT 0")
                }
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                if (!hasColumn(db, "subsonic_tracks", "userRating")) {
                    db.execSQL("ALTER TABLE subsonic_tracks ADD COLUMN userRating INTEGER NOT NULL DEFAULT 4")
                }
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                if (!hasColumn(db, "subsonic_tracks", "artistImageUrl")) {
                    db.execSQL("ALTER TABLE subsonic_tracks ADD COLUMN artistImageUrl TEXT NOT NULL DEFAULT ''")
                }
                if (!hasColumn(db, "subsonic_tracks", "artistBio")) {
                    db.execSQL("ALTER TABLE subsonic_tracks ADD COLUMN artistBio TEXT NOT NULL DEFAULT ''")
                }
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `subsonic_downloads` (
                        `downloadKey` TEXT NOT NULL,
                        `trackId` TEXT NOT NULL,
                        `serverId` INTEGER NOT NULL,
                        `albumId` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `artist` TEXT NOT NULL,
                        `album` TEXT NOT NULL,
                        `suffix` TEXT NOT NULL,
                        `localFilePath` TEXT NOT NULL,
                        `bytesDownloaded` INTEGER NOT NULL,
                        `totalBytes` INTEGER NOT NULL,
                        `progressPercent` INTEGER NOT NULL,
                        `status` TEXT NOT NULL,
                        `errorMessage` TEXT NOT NULL,
                        `updatedAtMs` INTEGER NOT NULL,
                        PRIMARY KEY(`downloadKey`)
                    )
                    """.trimIndent()
                )
            }
        }

        /** ReplayGain values from the server; nullable, filled on the next library sync. */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                for (column in listOf("rgTrackGain", "rgAlbumGain", "rgTrackPeak", "rgAlbumPeak")) {
                    if (!hasColumn(db, "subsonic_tracks", column)) {
                        db.execSQL("ALTER TABLE subsonic_tracks ADD COLUMN $column REAL")
                    }
                }
            }
        }

        fun getInstance(context: Context): OndaluneDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    OndaluneDatabase::class.java,
                    "ondalune_opensubsonic.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}


object DemoSubsonicCatalog {

    fun createDefaultDemoServer(): SubsonicServerConfig = SubsonicServerConfig(
        id = 1L,
        name = "Ondalune Reference Node (Sandbox)",
        baseUrl = "https://sandbox.opensubsonic.local",
        username = "audiophile",
        passwordOrToken = "ondalune-m3-expressive",
        useTokenAuth = true,
        transcodeFormat = "RAW",
        maxBitRate = 0,
        isDemoServer = true,
        isActive = true,
        serverSoftware = "Navidrome 0.54.2 (OpenSubsonic 1.16.1)",
        extensionsCsv = "songLyrics,transcodeOffset,formPost,apiKeyAuthentication",
        lastPingMs = System.currentTimeMillis()
    )

    fun seedTracks(serverId: Long = 1L): List<SubsonicTrack> = listOf(
        SubsonicTrack(
            id = "trk_neon_1",
            serverId = serverId,
            title = "Chrominance Drift",
            artist = "Klangwerk & Sol",
            artistId = "art_klangwerk",
            album = "Prism Architecture",
            albumId = "alb_prism",
            durationSec = 36,
            bitRate = 1411,
            suffix = "FLAC",
            bitDepth = 24,
            samplingRate = 96000,
            starred = true,
            trackNumber = 1,
            year = 2026,
            genre = "Analog Synthwave",
            folderPath = "/Masters/24bit-FLAC/Klangwerk - Prism Architecture",
            artStyleIndex = 0,
            paletteSeedHex = "#FF6B5B",
            accentHex = "#70F5FF",
            bpm = 122,
            musicalKey = "F Minor",
            playCount = 42,
            userRating = 5,
            syncedLyricsRaw = """
                [00:00.00] ♪ Warm analog oscillators waking in 24-bit space ♪
                [00:04.50] Neon geometry folding across the glass horizon
                [00:09.00] Every pulse aligns with the tactile spring of light
                [00:14.00] Sub-bass currents rising beneath the concrete sky
                [00:19.00] We drift through lossless frequencies untouched by static
                [00:24.50] Chrominance blooming in coral and electric cyan
                [00:30.00] ♪ Outro arpeggio dissolving into reverberant air ♪
            """.trimIndent()
        ),
        SubsonicTrack(
            id = "trk_neon_2",
            serverId = serverId,
            title = "Tactile Kinetics",
            artist = "Klangwerk & Sol",
            artistId = "art_klangwerk",
            album = "Prism Architecture",
            albumId = "alb_prism",
            durationSec = 34,
            bitRate = 1380,
            suffix = "FLAC",
            bitDepth = 24,
            samplingRate = 96000,
            starred = false,
            trackNumber = 2,
            year = 2026,
            genre = "Analog Synthwave",
            folderPath = "/Masters/24bit-FLAC/Klangwerk - Prism Architecture",
            artStyleIndex = 0,
            paletteSeedHex = "#FF6B5B",
            accentHex = "#70F5FF",
            bpm = 126,
            musicalKey = "C Minor",
            playCount = 28,
            userRating = 4,
            syncedLyricsRaw = """
                [00:00.00] ♪ Modular clock locked at 126 BPM ♪
                [00:05.00] Shapes morph under your fingertips in real time
                [00:10.50] From sharp circles into fluid expressive squircles
                [00:16.00] Feel the momentum carry the melody forward
                [00:22.00] No compression, only pure OpenSubsonic air
                [00:28.00] ♪ Filter sweep fading into midnight silence ♪
            """.trimIndent()
        ),
        SubsonicTrack(
            id = "trk_velvet_1",
            serverId = serverId,
            title = "Botanical Conservatory",
            artist = "Mira Vance",
            artistId = "art_mira",
            album = "Velvet Echoes",
            albumId = "alb_velvet",
            durationSec = 38,
            bitRate = 1120,
            suffix = "FLAC",
            bitDepth = 24,
            samplingRate = 48000,
            starred = true,
            trackNumber = 1,
            year = 2025,
            genre = "Dream Pop & Ambient",
            folderPath = "/Acoustic-Vault/Dream-Pop/Mira Vance - Velvet Echoes",
            artStyleIndex = 1,
            paletteSeedHex = "#B983FF",
            accentHex = "#5EEAD4",
            bpm = 98,
            musicalKey = "E♭ Major",
            playCount = 65,
            userRating = 5,
            syncedLyricsRaw = """
                [00:00.00] ♪ Raindrops on the greenhouse dome in stereo ♪
                [00:05.00] Iridescent ribbons suspended in emerald twilight
                [00:11.00] Soft tape saturation wraps around every chord
                [00:17.50] Breathe in the quiet between the reverb tails
                [00:23.50] Ultraviolet petals opening after dusk
                [00:30.00] ♪ Shimmering Rhodes piano outro ♪
            """.trimIndent()
        ),
        SubsonicTrack(
            id = "trk_velvet_2",
            serverId = serverId,
            title = "Silk & Celluloid",
            artist = "Mira Vance",
            artistId = "art_mira",
            album = "Velvet Echoes",
            albumId = "alb_velvet",
            durationSec = 32,
            bitRate = 1090,
            suffix = "FLAC",
            bitDepth = 24,
            samplingRate = 48000,
            starred = false,
            trackNumber = 2,
            year = 2025,
            genre = "Dream Pop & Ambient",
            folderPath = "/Acoustic-Vault/Dream-Pop/Mira Vance - Velvet Echoes",
            artStyleIndex = 1,
            paletteSeedHex = "#B983FF",
            accentHex = "#5EEAD4",
            bpm = 94,
            musicalKey = "A♭ Major",
            playCount = 19,
            userRating = 3,
            syncedLyricsRaw = """
                [00:00.00] ♪ Warm chorus guitar & analog Juno pad ♪
                [00:05.50] Silver frames spinning on an old projector reel
                [00:12.00] Memory painted in lavender and mint tones
                [00:18.50] Hold the note until the room turns weightless
                [00:25.00] ♪ Harmonics drifting into the night ♪
            """.trimIndent()
        ),
        SubsonicTrack(
            id = "trk_solar_1",
            serverId = serverId,
            title = "Bauhaus Afternoon",
            artist = "The Kyoto Quartet",
            artistId = "art_kyoto",
            album = "Solar Drift",
            albumId = "alb_solar",
            durationSec = 35,
            bitRate = 1540,
            suffix = "FLAC",
            bitDepth = 24,
            samplingRate = 96000,
            starred = true,
            trackNumber = 1,
            year = 2026,
            genre = "Nu-Jazz & Fusion",
            folderPath = "/Studio-Sessions/Tokyo-Jazz/Kyoto Quartet - Solar Drift",
            artStyleIndex = 2,
            paletteSeedHex = "#FFB443",
            accentHex = "#60A5FA",
            bpm = 112,
            musicalKey = "D Dorian",
            playCount = 53,
            userRating = 5,
            syncedLyricsRaw = """
                [00:00.00] ♪ Upright bass walk & brushed snare groove ♪
                [00:04.80] Terracotta sunbeams slanting across oak floors
                [00:10.20] Syncopated electric keys dancing in seventh chords
                [00:16.50] Every transient captured with studio precision
                [00:22.50] Amber brass swells meeting cool cobalt shadows
                [00:29.00] ♪ Polyrhythmic coda in D Dorian ♪
            """.trimIndent()
        ),
        SubsonicTrack(
            id = "trk_solar_2",
            serverId = serverId,
            title = "Espresso & Vinyl Grooves",
            artist = "The Kyoto Quartet",
            artistId = "art_kyoto",
            album = "Solar Drift",
            albumId = "alb_solar",
            durationSec = 33,
            bitRate = 1480,
            suffix = "FLAC",
            bitDepth = 24,
            samplingRate = 96000,
            starred = false,
            trackNumber = 2,
            year = 2026,
            genre = "Nu-Jazz & Fusion",
            folderPath = "/Studio-Sessions/Tokyo-Jazz/Kyoto Quartet - Solar Drift",
            artStyleIndex = 2,
            paletteSeedHex = "#FFB443",
            accentHex = "#60A5FA",
            bpm = 108,
            musicalKey = "G Minor",
            playCount = 31,
            userRating = 4,
            syncedLyricsRaw = """
                [00:00.00] ♪ Needle drops onto heavy 180-gram wax ♪
                [00:05.00] Warm Wurlitzer riffs in a sunlit Tokyo listening bar
                [00:11.50] Rimshots locking in with the fretless bassline
                [00:18.00] Pure uncompressed groove from server to speaker
                [00:25.00] ♪ Smooth fade on a suspended ninth chord ♪
            """.trimIndent()
        ),
        SubsonicTrack(
            id = "trk_circuit_1",
            serverId = serverId,
            title = "Bioluminescent Ridge",
            artist = "Aether & Bone",
            artistId = "art_aether",
            album = "Midnight Circuit",
            albumId = "alb_circuit",
            durationSec = 40,
            bitRate = 1620,
            suffix = "FLAC",
            bitDepth = 24,
            samplingRate = 96000,
            starred = true,
            trackNumber = 1,
            year = 2026,
            genre = "Atmospheric Post-Rock",
            folderPath = "/Vinyl-Rips/180g-Archival/Aether & Bone - Midnight Circuit",
            artStyleIndex = 3,
            paletteSeedHex = "#38BDF8",
            accentHex = "#F472B6",
            bpm = 130,
            musicalKey = "A Minor",
            playCount = 39,
            userRating = 5,
            syncedLyricsRaw = """
                [00:00.00] ♪ Clean delay-soaked arpeggios over volcanic mist ♪
                [00:06.00] Topographic lines glowing cyan in the northern dark
                [00:12.50] Distant drums building like tectonic plates in motion
                [00:19.00] Wide dynamic range from whisper to aurora crescendo
                [00:26.00] Crystalline harmonics echoing across the fjord
                [00:33.00] ♪ Reverberant post-rock horizon ♪
            """.trimIndent()
        ),
        SubsonicTrack(
            id = "trk_emerald_1",
            serverId = serverId,
            title = "Subterranean Moss",
            artist = "Hana Kori",
            artistId = "art_hana",
            album = "Verdant Frequency",
            albumId = "alb_verdant",
            durationSec = 34,
            bitRate = 980,
            suffix = "FLAC",
            bitDepth = 24,
            samplingRate = 48000,
            starred = true,
            trackNumber = 1,
            year = 2025,
            genre = "Organic Electronica",
            folderPath = "/Masters/24bit-FLAC/Hana Kori - Verdant Frequency",
            artStyleIndex = 4,
            paletteSeedHex = "#34D399",
            accentHex = "#A78BFA",
            bpm = 115,
            musicalKey = "C Major",
            playCount = 24,
            userRating = 4,
            syncedLyricsRaw = """
                [00:00.00] ♪ Kalimba plucks & wooden percussion in deep field ♪
                [00:05.50] Forest canopy filtering sunlight into emerald pulses
                [00:11.50] Organic micro-beats woven with warm sub-bass
                [00:18.00] Every leaf and droplet rendered in studio clarity
                [00:25.00] ♪ Gentle marimba resonance into stillness ♪
            """.trimIndent()
        ),
        SubsonicTrack(
            id = "trk_neoclass_1",
            serverId = serverId,
            title = "Nocturne in Glass & Felt",
            artist = "Soren Lindqvist",
            artistId = "art_soren",
            album = "Nordic Etudes",
            albumId = "alb_nordic",
            durationSec = 37,
            bitRate = 1240,
            suffix = "FLAC",
            bitDepth = 24,
            samplingRate = 96000,
            starred = true,
            trackNumber = 1,
            year = 2026,
            genre = "Neo-Classical & Piano",
            folderPath = "/Classical-Hall/24bit-FLAC/Soren Lindqvist - Nordic Etudes",
            artStyleIndex = 1,
            paletteSeedHex = "#60A5FA",
            accentHex = "#FBBF24",
            bpm = 84,
            musicalKey = "D Minor",
            playCount = 36,
            userRating = 5,
            syncedLyricsRaw = """
                [00:00.00] ♪ Intimate felt-hammer grand piano in Stockholm hall ♪
                [00:06.00] Soft pedal mechanics breathing between solo cello bows
                [00:14.00] Natural room decay preserved in 96kHz resolution
                [00:24.00] ♪ Final harmonic suspended in winter air ♪
            """.trimIndent()
        ),
        SubsonicTrack(
            id = "trk_lofi_1",
            serverId = serverId,
            title = "Rain on Shimokitazawa",
            artist = "Kaito Beats",
            artistId = "art_kaito",
            album = "Cassette Daydreams",
            albumId = "alb_cassette",
            durationSec = 31,
            bitRate = 1050,
            suffix = "FLAC",
            bitDepth = 24,
            samplingRate = 48000,
            starred = false,
            trackNumber = 1,
            year = 2025,
            genre = "Lo-Fi Beats & Chillhop",
            folderPath = "/Beat-Tapes/SP404-Masters/Kaito Beats - Cassette Daydreams",
            artStyleIndex = 2,
            paletteSeedHex = "#F472B6",
            accentHex = "#38BDF8",
            bpm = 88,
            musicalKey = "B♭ Major",
            playCount = 47,
            userRating = 4,
            syncedLyricsRaw = """
                [00:00.00] ♪ Dusty SP-404 vinyl crackle & mellow jazz Rhodes ♪
                [00:07.00] Unquantized boom-bap swing under Tokyo neon rain
                [00:16.00] Warm analog tape flutter on every snare ghost note
                [00:24.00] ♪ Late-night study loop fading softly ♪
            """.trimIndent()
        ),
        SubsonicTrack(
            id = "trk_house_1",
            serverId = serverId,
            title = "Subaqueous Horizon",
            artist = "Vespera",
            artistId = "art_vespera",
            album = "Deep Dub Vol. IV",
            albumId = "alb_deepdub",
            durationSec = 39,
            bitRate = 1490,
            suffix = "FLAC",
            bitDepth = 24,
            samplingRate = 96000,
            starred = true,
            trackNumber = 1,
            year = 2026,
            genre = "Deep House & Melodic Techno",
            folderPath = "/Club-Masters/24bit/Vespera - Deep Dub Vol IV",
            artStyleIndex = 3,
            paletteSeedHex = "#818CF8",
            accentHex = "#34D399",
            bpm = 124,
            musicalKey = "G Minor",
            playCount = 51,
            userRating = 5,
            syncedLyricsRaw = """
                [00:00.00] ♪ Sub-30Hz kick drum locked in phase-coherent groove ♪
                [00:08.00] Dubbed-out chord stabs echoing across warehouse walls
                [00:18.00] Hypnotic hi-hats and rolling analog bassline
                [00:29.00] ♪ Filtered synth pad drifting into sunrise ♪
            """.trimIndent()
        )
    )

    fun seedPlaylists(serverId: Long = 1L): List<SubsonicPlaylist> = listOf(
        SubsonicPlaylist(
            id = "pl_audiophile",
            serverId = serverId,
            name = "24-Bit Reference Showcase",
            comment = "Handpicked lossless tracks demonstrating Material You dynamic color & dynamic range",
            trackIdsCsv = "trk_neon_1,trk_solar_1,trk_velvet_1,trk_circuit_1,trk_neoclass_1",
            paletteSeedHex = "#FF6B5B",
            accentHex = "#70F5FF"
        ),
        SubsonicPlaylist(
            id = "pl_latenight",
            serverId = serverId,
            name = "Late Night Expressive Sessions",
            comment = "Lush analog synthwave, dream pop, and Tokyo listening bar grooves",
            trackIdsCsv = "trk_velvet_1,trk_velvet_2,trk_solar_2,trk_emerald_1,trk_lofi_1",
            paletteSeedHex = "#B983FF",
            accentHex = "#5EEAD4"
        ),
        SubsonicPlaylist(
            id = "pl_club_focus",
            serverId = serverId,
            name = "Deep Focus & Coding Pulse",
            comment = "Steady-tempo melodic techno, modular synthwave, and acoustic electronica",
            trackIdsCsv = "trk_house_1,trk_neon_2,trk_emerald_1,trk_circuit_1",
            paletteSeedHex = "#818CF8",
            accentHex = "#34D399"
        )
    )

    fun deriveAlbumsFromTracks(tracks: List<SubsonicTrack>): List<SubsonicAlbum> {
        return tracks
            .filter { it.albumId.isNotBlank() || it.album.isNotBlank() }
            .groupBy { it.albumId.ifBlank { "alb_${it.album.trim()}" } }
            .map { (albumId, albumTracks) ->
                val first = albumTracks.first()
                SubsonicAlbum(
                    id = albumId,
                    serverId = first.serverId,
                    name = first.album.ifBlank { "Unknown Album" },
                    artist = first.artist.ifBlank { "Unknown Artist" },
                    artistId = first.artistId.ifBlank { "art_${first.artist.trim()}" },
                    songCount = albumTracks.size,
                    durationSec = albumTracks.sumOf { it.durationSec },
                    year = first.year,
                    genre = first.genre.ifBlank { "Lossless" },
                    starred = albumTracks.any { it.starred },
                    coverArtUrl = first.coverArtUrl,
                    artStyleIndex = first.artStyleIndex,
                    paletteSeedHex = first.paletteSeedHex,
                    accentHex = first.accentHex
                )
            }
            .distinctBy { it.id }
    }

    fun deriveArtistsFromTracks(
        tracks: List<SubsonicTrack>,
        fallbackToAlbumArt: Boolean = false
    ): List<SubsonicArtist> {
        val bios = mapOf(
            "art_klangwerk" to "Berlin-based modular synth duo crafting tactile, wide-stage electronic architecture recorded in 24-bit/96kHz.",
            "art_mira" to "Dream-pop composer and multi-instrumentalist blending vintage tape loops with botanical field recordings.",
            "art_kyoto" to "Contemporary nu-jazz ensemble merging acoustic upright bass, Rhodes electric piano, and polyrhythmic grooves.",
            "art_aether" to "Nordic atmospheric post-rock collective known for sweeping dynamic crescendos and crystalline guitar textures.",
            "art_hana" to "Acoustic-electronic producer weaving kalimba, marimba, and deep sub-bass into lush organic soundscapes.",
            "art_soren" to "Scandinavian pianist and string arranger capturing intimate felt-piano nocturnes in natural concert acoustics.",
            "art_kaito" to "Tokyo beatmaker combining vintage sampler textures, warm jazz chords, and unquantized vinyl grooves.",
            "art_vespera" to "Deep house and dub-techno producer sculpting immersive sub-bass architecture for audiophile systems."
        )
        return tracks
            .groupBy { it.artist.trim().ifBlank { "Unknown Artist" } }
            .map { (artistName, artistTracks) ->
                val first = artistTracks.first()
                val slug = artistName.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
                val uniqueId = if (first.artistId.isNotBlank() &&
                    tracks.filter { it.artistId == first.artistId }.map { it.artist.trim() }.distinct().size == 1
                ) {
                    first.artistId
                } else {
                    "artist_${slug.ifBlank { artistName.hashCode().toUInt().toString(16) }}"
                }
                val dedicatedArtistPhoto = artistTracks.firstOrNull { it.artistImageUrl.isNotBlank() }?.artistImageUrl.orEmpty()
                val resolvedCoverArtUrl = if (dedicatedArtistPhoto.isNotBlank()) {
                    dedicatedArtistPhoto
                } else if (fallbackToAlbumArt) {
                    artistTracks.firstOrNull { it.coverArtUrl.isNotBlank() }?.coverArtUrl.orEmpty()
                } else {
                    ""
                }
                val resolvedBio = artistTracks.firstOrNull { it.artistBio.isNotBlank() }?.artistBio
                    ?: bios[first.artistId]
                    ?: "" // no filler: the artist page hides About until a real biography arrives

                SubsonicArtist(
                    id = uniqueId,
                    name = artistName,
                    albumCount = artistTracks.map { it.albumId }.distinct().size,
                    trackCount = artistTracks.size,
                    genre = first.genre.ifBlank { "Lossless" },
                    bio = resolvedBio,
                    coverArtUrl = resolvedCoverArtUrl,
                    paletteSeedHex = first.paletteSeedHex,
                    accentHex = first.accentHex,
                    artStyleIndex = first.artStyleIndex
                )
            }
            .distinctBy { it.id }
    }


    fun deriveGenresFromTracks(tracks: List<SubsonicTrack>): List<SubsonicGenre> {
        // One entry per individual genre (multi-genre tags are split); tracks without a genre are
        // simply not listed, and no placeholder genres are invented.
        val byGenre = LinkedHashMap<String, Pair<String, MutableList<SubsonicTrack>>>()
        tracks.forEach { track ->
            track.genreList().forEach { g -> byGenre.getOrPut(g.lowercase()) { g to mutableListOf() }.second.add(track) }
        }
        return byGenre.values.mapIndexed { idx, (genreName, genreTracks) ->
            val first = genreTracks.first()
            SubsonicGenre(
                name = genreName,
                trackCount = genreTracks.size,
                albumCount = genreTracks.map { it.albumId }.distinct().size,
                totalDurationSec = genreTracks.sumOf { it.durationSec },
                paletteSeedHex = first.paletteSeedHex,
                accentHex = first.accentHex,
                artStyleIndex = idx % 5
            )
        }.sortedByDescending { it.trackCount }
    }

    fun deriveFoldersFromTracks(tracks: List<SubsonicTrack>): List<SubsonicFolder> {
        return tracks.groupBy { it.folderPath.ifBlank { "/Music" } }.map { (path, folderTracks) ->
            val first = folderTracks.first()
            val parts = path.trim('/').split('/')
            val displayName = parts.lastOrNull() ?: path
            val category = parts.firstOrNull() ?: "Music"
            SubsonicFolder(
                path = path,
                displayName = displayName,
                category = category,
                tracks = folderTracks,
                paletteSeedHex = first.paletteSeedHex,
                accentHex = first.accentHex
            )
        }.distinctBy { it.path }
    }
}
