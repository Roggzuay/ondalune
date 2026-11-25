package io.github.roggzuay.ondalune.ui.viewmodel

import android.content.SharedPreferences
import io.github.roggzuay.ondalune.data.backup.BackupData
import io.github.roggzuay.ondalune.data.backup.OndaluneBackup
import io.github.roggzuay.ondalune.data.backup.PortableEdit
import io.github.roggzuay.ondalune.data.backup.PortableProfile
import io.github.roggzuay.ondalune.data.local.OndaluneDao
import io.github.roggzuay.ondalune.data.model.SubsonicServerConfig
import io.github.roggzuay.ondalune.data.model.isLocalSource

/**
 * Builds a backup of how Ondalune is set up, and puts one back.
 *
 * Servers and passwords are never in the file (see [OndaluneBackup]), so everything that belongs
 * to a server travels under that server's own address. On the way back in, a profile whose server
 * is not on this phone is skipped rather than guessed at, and the restore says how many.
 */
class BackupController(
    private val prefs: SharedPreferences,
    private val dao: OndaluneDao,
    private val appVersion: String,
    private val now: () -> Long = { System.currentTimeMillis() }
) {
    /**
     * The name a server travels under in a backup file.
     *
     * It has to be the same on the phone the file came from and the phone it goes back to, and it
     * must not be your server's address. So it is a one-way hash of the address and the username,
     * never either of them: two phones with the same server work it out independently and get the
     * same answer, and the file itself says nothing about where your music lives. A backup gets
     * copied to cloud drives; its contents should survive that.
     *
     * The music on this phone has no address, so it travels under a plain name of its own.
     */
    private fun keyOf(server: SubsonicServerConfig): String =
        if (server.isLocalSource) PhoneKey else hashedKey(plainKey(server))

    /** What earlier preview builds wrote: kept only so their files still restore. */
    private fun legacyKeyOf(server: SubsonicServerConfig): String =
        if (server.isLocalSource) PhoneKey else plainKey(server)

    private fun plainKey(server: SubsonicServerConfig) =
        "${server.baseUrl.trimEnd('/')}|${server.username}"

    suspend fun build(): BackupData {
        val servers = dao.getAllServersOnce()
        val keys = servers.associate { it.id to keyOf(it) }
        val profiles = dao.getSoundProfilesOnce().mapNotNull { profile ->
            keys[profile.serverId]?.let { PortableProfile.of(profile, it) }
        }
        val edits = dao.getOverridesOnce().mapNotNull { override ->
            keys[override.serverId]?.let { PortableEdit.of(override, it) }
        }
        return BackupData(
            createdAt = now(),
            appVersion = appVersion,
            settings = OndaluneBackup.settingsFrom(prefs.all),
            soundProfiles = profiles,
            trackEdits = edits,
            serverKeys = keys.values.distinct()
        )
    }

    fun encode(data: BackupData): String = OndaluneBackup.encode(data)

    fun decode(text: String): BackupData? = OndaluneBackup.decode(text)

    /**
     * Puts a backup back. Settings are written first, then anything whose server is on this phone.
     * Nothing is deleted: a restore adds to what is here and replaces what it matches.
     */
    suspend fun apply(data: BackupData): RestoreResult {
        val editor = prefs.edit()
        data.settings.forEach { (key, value) ->
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Double -> editor.putFloat(key, value.toFloat())
                is String -> editor.putString(key, value)
            }
        }
        editor.apply()

        // Files written before the key was hashed carry the address in the clear; they still
        // restore, so nobody's backup is orphaned by the change.
        val servers = dao.getAllServersOnce()
        val byKey = HashMap<String, SubsonicServerConfig>(servers.size * 2)
        servers.forEach { server ->
            byKey[keyOf(server)] = server
            byKey[legacyKeyOf(server)] = server
        }
        var profiles = 0
        var skippedProfiles = 0
        data.soundProfiles.forEach { portable ->
            val server = byKey[portable.serverKey]
            if (server == null) {
                skippedProfiles++
            } else {
                dao.upsertSoundProfile(portable.toProfile(server.id))
                profiles++
            }
        }

        var edits = 0
        var skippedEdits = 0
        data.trackEdits.forEach { portable ->
            val server = byKey[portable.serverKey]
            if (server == null) {
                skippedEdits++
            } else {
                dao.upsertOverride(portable.toOverride(server.id))
                edits++
            }
        }

        return RestoreResult(
            settings = data.settings.size,
            soundProfiles = profiles,
            trackEdits = edits,
            skipped = skippedProfiles + skippedEdits,
            missingServers = (data.soundProfiles.map { it.serverKey } + data.trackEdits.map { it.serverKey })
                .distinct()
                .filterNot { it in byKey }
        )
    }

    private companion object {
        const val PhoneKey = "this-phone"

        /** A one-way name for a server: the same everywhere, and not the address. */
        fun hashedKey(plain: String): String =
            java.security.MessageDigest.getInstance("SHA-256")
                .digest(plain.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
    }
}

/** What a restore actually put back, said plainly. */
data class RestoreResult(
    val settings: Int,
    val soundProfiles: Int,
    val trackEdits: Int,
    val skipped: Int,
    val missingServers: List<String>
) {
    fun message(): String {
        val parts = mutableListOf<String>()
        if (settings > 0) parts += "$settings settings"
        if (soundProfiles > 0) parts += countOf(soundProfiles, "saved sound")
        if (trackEdits > 0) parts += countOf(trackEdits, "corrected song")
        val put = if (parts.isEmpty()) "Nothing to put back" else "Put back ${parts.joinToString(", ")}"
        if (skipped == 0) return put
        val servers = if (missingServers.size == 1) "a server" else "${missingServers.size} servers"
        return "$put. Left $skipped for $servers you haven't added here."
    }

    private fun countOf(n: Int, noun: String) = if (n == 1) "1 $noun" else "$n ${noun}s"
}
