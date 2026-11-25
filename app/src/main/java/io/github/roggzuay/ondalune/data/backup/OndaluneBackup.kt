package io.github.roggzuay.ondalune.data.backup

import io.github.roggzuay.ondalune.data.model.SoundProfile
import io.github.roggzuay.ondalune.data.model.StoredSound
import io.github.roggzuay.ondalune.data.model.TrackOverride
import org.json.JSONArray
import org.json.JSONObject

/**
 * Backup and restore: one readable file holding how you have Ondalune set up.
 *
 * What goes in: every setting, your equalizer curves, the sounds you have saved for songs and
 * albums, and the song details you have corrected.
 *
 * What never goes in: your passwords, your usernames, or your servers' addresses. A backup file
 * is an ordinary file that gets copied into cloud drives, and a file naming the machine your
 * music lives on is both identifying and a map for anyone who finds it.
 *
 * Everything that belongs to a server therefore travels under a one-way name worked out from that
 * server's address and username rather than the row number it happens to have on this phone. Two
 * phones with the same server work the same name out independently, so a restore lands on the
 * right songs without the file ever saying where they are.
 *
 * That name cannot be salted, since nobody is there to share a salt with, so it can be guessed
 * at by anyone holding the file who already suspects an address. It is deliberately slow to
 * compute for that reason. PRIVACY.md says so plainly rather than implying more than it is.
 */
object OndaluneBackup {
    const val Format = "ondalune-backup"
    const val Version = 1

    /**
     * Settings worth carrying to another phone. Anything outside these prefixes stays behind:
     * the queue, what was playing, which notes have been seen, and this phone's own identity.
     */
    private val KeptPrefixes = listOf("cfg_", "eq_", "dsp_", "reverb_", "filter_")
    private val KeptKeys = setOf("repeat_mode", "shuffle_mode")

    /** Settings that describe this phone or this moment, never another one. */
    private val NeverKept = setOf("cfg_initialized")

    fun keeps(key: String): Boolean =
        key !in NeverKept && (key in KeptKeys || KeptPrefixes.any { key.startsWith(it) })

    /** Picks the settings worth keeping out of everything SharedPreferences holds. */
    fun settingsFrom(all: Map<String, *>): Map<String, Any> =
        all.asSequence()
            .filter { keeps(it.key) }
            .mapNotNull { (key, value) ->
                when (value) {
                    is String, is Boolean, is Int, is Long, is Float -> key to value
                    else -> null
                }
            }
            .toMap()

    fun encode(backup: BackupData): String {
        val root = JSONObject()
        root.put("format", Format)
        root.put("version", Version)
        root.put("createdAt", backup.createdAt)
        root.put("appVersion", backup.appVersion)

        val settings = JSONObject()
        backup.settings.forEach { (key, value) -> settings.put(key, value) }
        root.put("settings", settings)

        root.put(
            "soundProfiles",
            JSONArray().apply {
                backup.soundProfiles.forEach { put(it.toJson()) }
            }
        )
        root.put(
            "trackEdits",
            JSONArray().apply {
                backup.trackEdits.forEach { put(it.toJson()) }
            }
        )
        root.put("servers", JSONArray().apply { backup.serverKeys.forEach { put(it) } })
        return root.toString(2)
    }

    /** Reads a backup file. Null when it is not one of ours, or is from a version we can't read. */
    fun decode(text: String): BackupData? {
        val root = runCatching { JSONObject(text) }.getOrNull() ?: return null
        if (root.optString("format") != Format) return null
        if (root.optInt("version", 0) !in 1..Version) return null

        val settings = mutableMapOf<String, Any>()
        root.optJSONObject("settings")?.let { obj ->
            obj.keys().forEach { key ->
                if (!keeps(key)) return@forEach
                when (val value = obj.opt(key)) {
                    is String, is Boolean, is Int, is Long, is Double -> settings[key] = value
                }
            }
        }

        return BackupData(
            createdAt = root.optLong("createdAt"),
            appVersion = root.optString("appVersion"),
            settings = settings,
            soundProfiles = root.optJSONArray("soundProfiles").objects().mapNotNull { PortableProfile.from(it) },
            trackEdits = root.optJSONArray("trackEdits").objects().mapNotNull { PortableEdit.from(it) },
            serverKeys = root.optJSONArray("servers").strings()
        )
    }

    private fun JSONArray?.objects(): List<JSONObject> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { optJSONObject(it) }
    }

    private fun JSONArray?.strings(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { optString(it).takeIf { s -> s.isNotEmpty() } }
    }
}

/** One backup file, read or about to be written. */
data class BackupData(
    val createdAt: Long,
    val appVersion: String,
    val settings: Map<String, Any> = emptyMap(),
    val soundProfiles: List<PortableProfile> = emptyList(),
    val trackEdits: List<PortableEdit> = emptyList(),
    /** The servers these profiles and edits came from, by address, so a restore can say so. */
    val serverKeys: List<String> = emptyList()
) {
    /** What the restore sheet says it found, in plain words. */
    fun summary(): String {
        val parts = mutableListOf<String>()
        if (settings.isNotEmpty()) parts += "${settings.size} settings"
        if (soundProfiles.isNotEmpty()) parts += countOf(soundProfiles.size, "saved sound")
        if (trackEdits.isNotEmpty()) parts += countOf(trackEdits.size, "corrected song")
        return if (parts.isEmpty()) "Nothing to put back" else parts.joinToString(", ")
    }

    private fun countOf(n: Int, noun: String) = if (n == 1) "1 $noun" else "$n ${noun}s"
}

/** A saved sound, keyed by its server's address rather than its row number on one phone. */
data class PortableProfile(
    val serverKey: String,
    val scope: String,
    val scopeId: String,
    val sound: StoredSound,
    val name: String?,
    val updatedAt: Long
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("server", serverKey)
        put("scope", scope)
        put("scopeId", scopeId)
        put("speed", sound.speed.toDouble())
        put("pitchMode", sound.pitchMode)
        put("pitchRatio", sound.independentPitchRatio.toDouble())
        put("reverbOn", sound.reverbEnabled)
        put("reverbPreset", sound.reverbPreset)
        put("wetDry", sound.wetDryMix.toDouble())
        put("roomSize", sound.roomSize.toDouble())
        put("decay", sound.decay.toDouble())
        put("damping", sound.damping.toDouble())
        put("eqPreset", sound.eqPreset)
        put("eqBands", sound.eqBandsDb)
        name?.let { put("name", it) }
        put("updatedAt", updatedAt)
    }

    fun toProfile(serverId: Long) = SoundProfile(
        serverId = serverId,
        scope = scope,
        scopeId = scopeId,
        sound = sound,
        name = name,
        updatedAt = updatedAt
    )

    companion object {
        fun of(profile: SoundProfile, serverKey: String) = PortableProfile(
            serverKey = serverKey,
            scope = profile.scope,
            scopeId = profile.scopeId,
            sound = profile.sound,
            name = profile.name,
            updatedAt = profile.updatedAt
        )

        fun from(json: JSONObject): PortableProfile? {
            val scopeId = json.optString("scopeId").takeIf { it.isNotEmpty() } ?: return null
            return PortableProfile(
                serverKey = json.optString("server"),
                scope = json.optString("scope", "TRACK"),
                scopeId = scopeId,
                sound = StoredSound(
                    speed = json.optDouble("speed", 1.0).toFloat(),
                    pitchMode = json.optString("pitchMode", StoredSound().pitchMode),
                    independentPitchRatio = json.optDouble("pitchRatio", 1.0).toFloat(),
                    reverbEnabled = json.optBoolean("reverbOn", false),
                    reverbPreset = json.optString("reverbPreset", StoredSound().reverbPreset),
                    wetDryMix = json.optDouble("wetDry", 0.0).toFloat(),
                    roomSize = json.optDouble("roomSize", 0.52).toFloat(),
                    decay = json.optDouble("decay", 0.46).toFloat(),
                    damping = json.optDouble("damping", 0.50).toFloat(),
                    eqPreset = json.optString("eqPreset", StoredSound().eqPreset),
                    eqBandsDb = json.optString("eqBands", StoredSound().eqBandsDb)
                ),
                name = json.optString("name").takeIf { it.isNotEmpty() },
                updatedAt = json.optLong("updatedAt")
            )
        }
    }
}

/** A song detail you corrected, keyed the same way. */
data class PortableEdit(
    val serverKey: String,
    val trackId: String,
    val title: String?,
    val artist: String?,
    val genre: String?,
    val year: Int?
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("server", serverKey)
        put("trackId", trackId)
        title?.let { put("title", it) }
        artist?.let { put("artist", it) }
        genre?.let { put("genre", it) }
        year?.let { put("year", it) }
    }

    fun toOverride(serverId: Long) = TrackOverride(
        serverId = serverId,
        trackId = trackId,
        title = title,
        artist = artist,
        genre = genre,
        year = year
    )

    companion object {
        fun of(override: TrackOverride, serverKey: String) = PortableEdit(
            serverKey = serverKey,
            trackId = override.trackId,
            title = override.title,
            artist = override.artist,
            genre = override.genre,
            year = override.year
        )

        fun from(json: JSONObject): PortableEdit? {
            val trackId = json.optString("trackId").takeIf { it.isNotEmpty() } ?: return null
            return PortableEdit(
                serverKey = json.optString("server"),
                trackId = trackId,
                title = json.optString("title").takeIf { it.isNotEmpty() },
                artist = json.optString("artist").takeIf { it.isNotEmpty() },
                genre = json.optString("genre").takeIf { it.isNotEmpty() },
                year = json.optInt("year").takeIf { it > 0 }
            )
        }
    }
}
