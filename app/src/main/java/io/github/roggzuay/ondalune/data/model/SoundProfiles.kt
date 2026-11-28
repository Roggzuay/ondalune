package io.github.roggzuay.ondalune.data.model

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A sound profile is a song the way you like to hear it: the speed, the pitch, the reverb and the
 * equalizer, remembered against one track or one album and put back in one tap.
 *
 * Profiles are Ondalune's own, like the song details you correct in the app. Nothing here is ever
 * written to a server or to your files.
 */

/** Whether a saved sound belongs to one song or to a whole album. */
enum class SoundProfileScope { TRACK, ALBUM }

/**
 * The sound as it stands: what the engine is doing right now, or what a profile holds. Enums are
 * kept as names so the row survives an enum that gains or loses a case, and the bands as one
 * comma-joined string so the five of them stay together.
 */
data class StoredSound(
    val speed: Float = 1.0f,
    val pitchMode: String = PitchCorrectionMode.TIME_STRETCH_PRESERVE_PITCH.name,
    val independentPitchRatio: Float = 1.0f,
    val reverbEnabled: Boolean = false,
    val reverbPreset: String = ReverbPreset.OFF.name,
    val wetDryMix: Float = 0.0f,
    val roomSize: Float = 0.52f,
    val decay: Float = 0.46f,
    val damping: Float = 0.50f,
    val eqPreset: String = EqualizerPreset.FLAT_STUDIO.name,
    val eqBandsDb: String = "0,0,0,0,0"
)

/**
 * A saved sound. The key is the server it came from, whether it is a song or an album, and that
 * song or album's id, so the same song on two servers keeps two profiles.
 */
@Entity(tableName = "sound_profiles", primaryKeys = ["serverId", "scope", "scopeId"])
data class SoundProfile(
    val serverId: Long,
    /** A [SoundProfileScope] name. */
    val scope: String,
    val scopeId: String,
    @Embedded val sound: StoredSound = StoredSound(),
    /** Room for a named profile later; nothing sets it yet. */
    val name: String? = null,
    val updatedAt: Long = 0L
)

/** One play, with whatever the sound was doing while it played. */
@Entity(tableName = "play_history")
data class PlayHistoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serverId: Long,
    val trackId: String,
    val playedAt: Long,
    @Embedded val sound: StoredSound = StoredSound(),
    /** The song had a saved profile when it played, whether or not it was applied. */
    val hadProfile: Boolean = false
)

/** How many plays are kept. Older rows are dropped as new ones arrive. */
const val PlayHistoryLimit = 500

/**
 * The live sound, in the types the rest of the app uses. [SoundController] holds one of these as
 * the sound a track started with, and compares it with the sound now to know whether to offer to
 * save it.
 */
data class SoundSnapshot(
    val speedPitch: SpeedPitchState = SpeedPitchState(),
    val reverb: ReverbState = ReverbState(),
    val eqPreset: EqualizerPreset = EqualizerPreset.FLAT_STUDIO,
    val eqBandsDb: List<Float> = EqualizerPreset.FLAT_STUDIO.bandsDb
) {
    /** Nothing is being done to the sound: play it as it was mastered. */
    val isNeutral: Boolean
        get() = abs(speedPitch.speed - 1f) < SpeedEpsilon &&
            abs(speedPitch.effectivePitch - 1f) < PitchEpsilon &&
            !reverb.isActivelyProcessing &&
            eqBandsDb.all { abs(it) < BandEpsilon }

    /**
     * Whether this sound is meaningfully different from [other]. Floats are compared with a little
     * slack so dragging a slider back to where it was doesn't count as a change.
     */
    fun differsFrom(other: SoundSnapshot): Boolean {
        if (abs(speedPitch.speed - other.speedPitch.speed) >= SpeedEpsilon) return true
        if (speedPitch.pitchMode != other.speedPitch.pitchMode) return true
        if (abs(speedPitch.effectivePitch - other.speedPitch.effectivePitch) >= PitchEpsilon) return true
        if (reverb.isActivelyProcessing != other.reverb.isActivelyProcessing) return true
        if (reverb.isActivelyProcessing) {
            if (reverb.preset != other.reverb.preset) return true
            if (abs(reverb.wetDryMix - other.reverb.wetDryMix) >= MixEpsilon) return true
            if (abs(reverb.roomSize - other.reverb.roomSize) >= MixEpsilon) return true
            if (abs(reverb.decay - other.reverb.decay) >= MixEpsilon) return true
            if (abs(reverb.damping - other.reverb.damping) >= MixEpsilon) return true
        }
        if (eqPreset != other.eqPreset) return true
        if (eqBandsDb.size != other.eqBandsDb.size) return true
        return eqBandsDb.indices.any { abs(eqBandsDb[it] - other.eqBandsDb[it]) >= BandEpsilon }
    }

    /**
     * The sound in a few words, for the save card and the history ribbon:
     * "0.80x · -3.86 st · Dreamy 60%".
     */
    val summary: String
        get() {
            val parts = mutableListOf<String>()
            if (abs(speedPitch.speed - 1f) >= SpeedEpsilon) {
                parts += String.format(java.util.Locale.US, "%.2fx", speedPitch.speed)
            }
            val semitones = speedPitch.effectiveSemitones
            if (abs(semitones) >= SemitoneEpsilon) {
                parts += String.format(java.util.Locale.US, "%+.2f st", semitones)
            }
            if (reverb.isActivelyProcessing) {
                parts += "${reverb.preset.label} ${(reverb.wetDryMix * 100).roundToInt()}%"
            }
            if (eqPreset != EqualizerPreset.FLAT_STUDIO || eqBandsDb.any { abs(it) >= BandEpsilon }) {
                parts += eqPreset.label
            }
            return if (parts.isEmpty()) "As mastered" else parts.joinToString(" · ")
        }

    /** The shortest thing that still says what was done, for a ribbon across a cover. */
    val ribbon: String?
        get() = when {
            abs(speedPitch.speed - 1f) >= SpeedEpsilon ->
                String.format(java.util.Locale.US, "%.2fx", speedPitch.speed)
            abs(speedPitch.effectiveSemitones) >= SemitoneEpsilon ->
                String.format(java.util.Locale.US, "%+.0f st", speedPitch.effectiveSemitones)
            reverb.isActivelyProcessing -> "Reverb"
            eqPreset != EqualizerPreset.FLAT_STUDIO -> eqPreset.label
            eqBandsDb.any { abs(it) >= BandEpsilon } -> "EQ"
            else -> null
        }

    fun stored(): StoredSound = StoredSound(
        speed = speedPitch.speed,
        pitchMode = speedPitch.pitchMode.name,
        independentPitchRatio = speedPitch.independentPitchRatio,
        reverbEnabled = reverb.enabled,
        reverbPreset = reverb.preset.name,
        wetDryMix = reverb.wetDryMix,
        roomSize = reverb.roomSize,
        decay = reverb.decay,
        damping = reverb.damping,
        eqPreset = eqPreset.name,
        eqBandsDb = eqBandsDb.joinToString(",")
    )

    companion object {
        /** 0.005x: half of the smallest step the speed buttons take. */
        const val SpeedEpsilon = 0.005f
        const val PitchEpsilon = 0.005f
        const val MixEpsilon = 0.01f
        const val BandEpsilon = 0.05f
        const val SemitoneEpsilon = 0.05f
    }
}

/** The stored row read back into the types the app works in. Anything unreadable falls back. */
fun StoredSound.snapshot(): SoundSnapshot {
    val mode = runCatching { PitchCorrectionMode.valueOf(pitchMode) }
        .getOrDefault(PitchCorrectionMode.TIME_STRETCH_PRESERVE_PITCH)
    val preset = runCatching { ReverbPreset.valueOf(reverbPreset) }.getOrDefault(ReverbPreset.OFF)
    val eq = runCatching { EqualizerPreset.valueOf(eqPreset) }.getOrDefault(EqualizerPreset.FLAT_STUDIO)
    val bands = eqBandsDb.split(',')
        .mapNotNull { it.trim().toFloatOrNull()?.coerceIn(-10f, 10f) }
        .takeIf { it.size == eq.bandsDb.size }
        ?: eq.bandsDb
    return SoundSnapshot(
        speedPitch = SpeedPitchState(
            speed = speed.coerceIn(0.80f, 2.00f),
            pitchMode = mode,
            independentPitchRatio = independentPitchRatio.coerceIn(0.50f, 2.00f)
        ),
        reverb = ReverbState(
            enabled = reverbEnabled,
            preset = preset,
            wetDryMix = wetDryMix,
            roomSize = roomSize,
            decay = decay,
            damping = damping
        ).sanitized(),
        eqPreset = eq,
        eqBandsDb = bands
    )
}

/**
 * One frame of the slow slide from this sound into [target], at [fraction] of the way across.
 *
 * Tapping the strip does not switch the sound; it leans the song towards it, and this is the shape
 * of that lean. Everything continuous is simply mixed — the speed, the pitch, the reverb's wetness
 * and shape, each equalizer band. The two things that cannot be half-way are handled rather than
 * snapped:
 *
 * - **Pitch mode.** Between two sounds that use the same mode there is nothing to switch, so the
 *   mode is left alone and the numbers under it travel — which is also the only way the controls
 *   on screen never show a mode you did not choose. Only when the two ends disagree does the
 *   slide run in [PitchCorrectionMode.INDEPENDENT_SEMITONES] and travel along
 *   [SpeedPitchState.effectivePitch], the one number the engine actually hears; the real mode is
 *   then set at the end, when the ratio has arrived and setting it changes nothing audible.
 * - **Reverb.** A room cannot fade in while switched off, so it is switched on at the first frame
 *   and the target's preset taken straight away; only the mix climbs, from silence. Going the other
 *   way the mix falls to nothing and the reverb is switched off at the end, so the tail is never
 *   cut mid-decay. When both ends are rooms that are actually sounding, the preset is left alone
 *   until the slide lands: changing the algorithm under a size and tail that are still travelling
 *   is the jump this exists to avoid.
 *
 * At `fraction >= 1` this is exactly [target], so the slide always lands on the saved sound and
 * never near it.
 */
fun SoundSnapshot.blendTo(target: SoundSnapshot, fraction: Float): SoundSnapshot {
    val t = fraction.coerceIn(0f, 1f)
    if (t >= 1f) return target
    if (t <= 0f) return this
    val wantsReverb = target.reverb.isActivelyProcessing
    val hasReverb = reverb.isActivelyProcessing
    val blended = ReverbState(
        enabled = reverb.enabled || wantsReverb,
        // Off to on there is no room to keep, so the target's preset is taken at once. Between two
        // sounding rooms it is kept: switching from a cathedral to a small room on the first frame
        // put the engine in the small room while it still held the cathedral's size and seven
        // second tail, which is audibly the opposite of easing. t >= 1 returns target, so the
        // change arrives with everything else.
        preset = if (wantsReverb && !hasReverb) target.reverb.preset else reverb.preset,
        // A reverb that is off contributes no wetness to mix from, whatever its stored mix says.
        wetDryMix = mixFloat(
            if (hasReverb) reverb.wetDryMix else 0f,
            if (wantsReverb) target.reverb.wetDryMix else 0f,
            t
        ),
        roomSize = mixFloat(reverb.roomSize, target.reverb.roomSize, t),
        decay = mixFloat(reverb.decay, target.reverb.decay, t),
        damping = mixFloat(reverb.damping, target.reverb.damping, t)
    )
    val speed = mixFloat(speedPitch.speed, target.speedPitch.speed, t).coerceIn(0.80f, 2.00f)
    val sameMode = speedPitch.pitchMode == target.speedPitch.pitchMode
    return SoundSnapshot(
        speedPitch = if (sameMode) {
            SpeedPitchState(
                speed = speed,
                pitchMode = speedPitch.pitchMode,
                independentPitchRatio = mixFloat(
                    speedPitch.independentPitchRatio,
                    target.speedPitch.independentPitchRatio,
                    t
                ).coerceIn(0.50f, 2.00f)
            )
        } else {
            SpeedPitchState(
                speed = speed,
                pitchMode = PitchCorrectionMode.INDEPENDENT_SEMITONES,
                independentPitchRatio = mixFloat(
                    speedPitch.effectivePitch,
                    target.speedPitch.effectivePitch,
                    t
                ).coerceIn(0.50f, 2.00f)
            )
        },
        reverb = blended.sanitized(),
        eqPreset = target.eqPreset,
        eqBandsDb = mixBands(eqBandsDb, target.eqBandsDb, t)
    )
}

private fun mixFloat(from: Float, to: Float, t: Float): Float = from + (to - from) * t

/**
 * Bands mix one for one. Two curves of different lengths have nothing to mix, so the target's
 * curve is taken as it is rather than half of a curve that does not fit.
 */
private fun mixBands(from: List<Float>, to: List<Float>, t: Float): List<Float> =
    if (from.size != to.size) to else to.indices.map { mixFloat(from[it], to[it], t) }

/**
 * Eased travel across the slide: still at both ends, quickest in the middle.
 *
 * A straight ramp is audible as two corners — the moment the song starts bending and the moment it
 * stops. Smoothstep has no corners, which is what makes the change feel like the song doing it
 * rather than a setting being moved.
 */
fun soundGlideEase(fraction: Float): Float {
    val t = fraction.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

val SoundProfile.snapshot: SoundSnapshot get() = sound.snapshot()

val PlayHistoryEntry.snapshot: SoundSnapshot get() = sound.snapshot()

/** This play did something to the sound, so it is worth marking in the history. */
val PlayHistoryEntry.adjusted: Boolean get() = !snapshot.isNeutral

fun soundProfileOf(
    serverId: Long,
    scope: SoundProfileScope,
    scopeId: String,
    snapshot: SoundSnapshot,
    updatedAt: Long,
    name: String? = null
): SoundProfile = SoundProfile(
    serverId = serverId,
    scope = scope.name,
    scopeId = scopeId,
    sound = snapshot.stored(),
    name = name,
    updatedAt = updatedAt
)

/** A song as it was played: the track, and what the sound was doing at the time. */
data class PlayedSound(
    val playedAt: Long,
    val track: SubsonicTrack,
    val snapshot: SoundSnapshot,
    /** The song had a saved profile when it played. */
    val hadProfile: Boolean
) {
    /** The short word for the ribbon across the cover; null when nothing was changed. */
    val ribbon: String? get() = snapshot.ribbon
}

/**
 * The play history as the Home card shows it: newest first, one row per song, and only songs the
 * library still holds. [adjustedOnly] keeps the plays that actually did something to the sound,
 * which is what the card is for.
 */
fun playedSounds(
    history: List<PlayHistoryEntry>,
    tracks: List<SubsonicTrack>,
    limit: Int = 12,
    adjustedOnly: Boolean = true
): List<PlayedSound> {
    if (history.isEmpty() || tracks.isEmpty()) return emptyList()
    val byId = tracks.associateBy { it.id }
    val seen = HashSet<String>()
    val out = ArrayList<PlayedSound>(limit)
    for (entry in history.sortedByDescending { it.playedAt }) {
        if (out.size >= limit) break
        if (!seen.add(entry.trackId)) continue
        val track = byId[entry.trackId] ?: continue
        if (track.serverId != entry.serverId) continue
        val snapshot = entry.snapshot
        if (adjustedOnly && snapshot.isNeutral) continue
        out += PlayedSound(entry.playedAt, track, snapshot, entry.hadProfile)
    }
    return out
}

/** A saved sound with the song or album it belongs to, ready to list. */
data class SavedSound(
    val profile: SoundProfile,
    val title: String,
    val subtitle: String,
    val coverArtUrl: String,
    val seedHex: String,
    val accentHex: String,
    val artStyleIndex: Int,
    /** The song to play when this row is tapped; null for an album whose songs are gone. */
    val track: SubsonicTrack?
) {
    val snapshot: SoundSnapshot get() = profile.snapshot
    val isAlbum: Boolean get() = profile.scope == SoundProfileScope.ALBUM.name
}

/**
 * Saved sounds matched to the songs and albums they belong to, newest first. A profile whose song
 * or album has left the library is dropped from the list rather than shown as an empty row; the
 * row itself stays in the database until it is forgotten.
 */
fun savedSounds(
    profiles: List<SoundProfile>,
    tracks: List<SubsonicTrack>,
    albums: List<SubsonicAlbum>
): List<SavedSound> {
    if (profiles.isEmpty()) return emptyList()
    val trackById = tracks.associateBy { it.id }
    val albumById = albums.associateBy { it.id }
    val firstTrackOfAlbum = tracks.groupBy { it.albumId }
    return profiles.sortedByDescending { it.updatedAt }.mapNotNull { profile ->
        when (profile.scope) {
            SoundProfileScope.ALBUM.name -> {
                val album = albumById[profile.scopeId] ?: return@mapNotNull null
                SavedSound(
                    profile = profile,
                    title = album.name,
                    subtitle = album.artist,
                    coverArtUrl = album.coverArtUrl,
                    seedHex = album.paletteSeedHex,
                    accentHex = album.accentHex,
                    artStyleIndex = album.artStyleIndex,
                    track = firstTrackOfAlbum[album.id]?.minByOrNull { it.trackNumber }
                )
            }
            else -> {
                val track = trackById[profile.scopeId] ?: return@mapNotNull null
                SavedSound(
                    profile = profile,
                    title = track.title,
                    subtitle = track.artist,
                    coverArtUrl = track.coverArtUrl,
                    seedHex = track.paletteSeedHex,
                    accentHex = track.accentHex,
                    artStyleIndex = track.artStyleIndex,
                    track = track
                )
            }
        }
    }
}
