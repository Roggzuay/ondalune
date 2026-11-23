package io.github.roggzuay.ondalune.data.motion

import io.github.roggzuay.ondalune.data.model.SubsonicTrack

/** How the player may move its cover. */
enum class MotionArtworkMode(val label: String, val detail: String) {
    AUTOMATIC("On", "Moving artwork when the album has some; the still cover otherwise"),
    DISABLED("Off", "Always the still cover")
}

/** When Ondalune looks for moving artwork. */
enum class MotionFetchScope(val label: String, val detail: String) {
    WHEN_PLAYED("When a song plays", "Looks up an album when one of its songs starts"),
    WHOLE_LIBRARY("Whole library, in the background", "Also goes through every album once a day, never on low battery, so moving covers are ready before you play them")
}

/** The Motion artwork settings, as saved with the rest of Ondalune's preferences. */
data class MotionArtworkSettings(
    val mode: MotionArtworkMode = MotionArtworkMode.AUTOMATIC,
    /** Look up moving artwork by itself; off: only what is already saved or sits in a music folder. */
    val autoRetrieve: Boolean = true,
    val wifiOnly: Boolean = true,
    val fetchScope: MotionFetchScope = MotionFetchScope.WHEN_PLAYED,
    val cacheLimitMb: Int = 250
) {
    companion object {
        val CACHE_LIMITS_MB = listOf(100, 250, 500, 1000)
        /** What a custom limit may be set to: 50 MB up to 100 GB. It is a ceiling, never a reservation. */
        val CUSTOM_LIMIT_MB = 50..102_400
    }
}

/** Animated formats Ondalune plays as artwork, best first. */
enum class MotionFormat(val label: String, val extension: String, val isVideo: Boolean) {
    MP4("MP4 video", "mp4", true),
    WEBM("WebM video", "webm", true),
    ANIMATED_WEBP("Animated WebP", "webp", false),
    GIF("GIF", "gif", false)
}

/**
 * What a song is, for telling whether a piece of artwork belongs to it. [serverId] and [albumId]
 * name the album in Ondalune's library; the rest is what the tags say.
 */
data class MotionIdentity(
    val serverId: Long,
    val albumId: String,
    val album: String,
    val artist: String,
    val title: String = "",
    val year: Int = 0,
    val durationSec: Int = 0,
    /** MusicBrainz release id, when known. */
    val releaseMbid: String? = null,
    val isrc: String? = null
) {
    /** One album, one entry: every song of a release shares its artwork. */
    val releaseKey: String get() = releaseMbid?.takeIf { it.isNotBlank() }?.let { "mb:$it" } ?: "$serverId:$albumId"

    companion object {
        fun of(track: SubsonicTrack) = MotionIdentity(
            serverId = track.serverId,
            albumId = track.albumId,
            album = track.album,
            artist = track.artist,
            title = track.title,
            year = track.year,
            durationSec = track.durationSec
        )
    }
}

/** Artwork a provider offers for an album, and what the provider says it belongs to. */
data class MotionCandidate(
    val providerId: String,
    /** Where the file is: a web address or a `content://` document. */
    val source: String,
    val format: MotionFormat,
    /** The album or song the provider ties this artwork to. */
    val claimed: MotionIdentity,
    val sizeBytes: Long = -1L
)

/** Artwork ready to show: a saved copy, or a file in the user's own music folder. */
data class MotionAsset(
    val releaseKey: String,
    val providerId: String,
    val providerName: String,
    val attribution: String?,
    /** A file path or a `content://` address. */
    val location: String,
    val format: MotionFormat,
    val album: String,
    val artist: String,
    val confidence: Float,
    val bytes: Long
)
