package io.github.roggzuay.ondalune.data.motion

import io.github.roggzuay.ondalune.data.model.SubsonicServerConfig
import io.github.roggzuay.ondalune.data.model.SubsonicTrack
import java.io.InputStream

/** An opened piece of artwork: [stream] starts at [startsAt] bytes into the file. */
class MotionStream(val stream: InputStream, val startsAt: Long, val totalBytes: Long = -1L) : AutoCloseable {
    override fun close() = stream.close()
}

/**
 * A place moving artwork can come from. Each one says what it can look at, offers candidates with
 * what it believes they belong to (the matcher decides whether to believe it), and opens them.
 * New sources plug in here; nothing in playback or the player changes for them.
 */
interface MotionArtworkProvider {
    /** Stable id, saved with what it found. */
    val id: String
    /** Shown as the artwork's source. */
    val name: String
    /** Credit the source asks for, shown with its artwork; null when none is needed. */
    val attribution: String?
    /** Network providers obey the Wi-Fi-only and automatic-lookup settings. */
    val usesNetwork: Boolean
    /**
     * True when the artwork is the user's own file that Ondalune can show where it is, without
     * keeping a copy.
     */
    val showsInPlace: Boolean get() = false

    /** Whether this provider can say anything about [track] (from [server]). */
    fun handles(track: SubsonicTrack, server: SubsonicServerConfig?): Boolean

    /** Artwork for [track]'s album; empty when there is none. Throws when the source can't be reached. */
    suspend fun find(identity: MotionIdentity, track: SubsonicTrack, server: SubsonicServerConfig?): List<MotionCandidate>

    /** Opens [candidate] from [fromByte] on; a source that can't resume starts again from 0. */
    suspend fun open(candidate: MotionCandidate, fromByte: Long): MotionStream
}
