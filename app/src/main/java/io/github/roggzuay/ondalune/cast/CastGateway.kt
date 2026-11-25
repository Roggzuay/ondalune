package io.github.roggzuay.ondalune.cast

import io.github.roggzuay.ondalune.data.model.SubsonicTrack
import kotlinx.coroutines.flow.StateFlow

/**
 * Playing on something other than this phone.
 *
 * Casting is the one thing in Ondalune that cannot be in every build. Google's Cast SDK is
 * proprietary and needs Play Services, which a reproducible F-Droid build cannot carry, so it
 * rides with the GitHub build only. Everything above this interface is the same either way: the
 * app asks whether casting is [supported] and gets a plain no in the build without it.
 *
 * **The sound engine does not follow the music onto a Cast device.** A Cast receiver fetches the
 * song from your server and plays it itself, so the speed, the pitch, the reverb, the equalizer,
 * the crossfeed and the crossfade all stop applying while it is playing there. That is a fact
 * about how casting works, not a thing left undone, and the player says so where it happens
 * rather than letting you wonder why the song sounds different.
 */
interface CastGateway {

    /** False in a build without the Cast SDK. Nothing else here does anything when it is false. */
    val supported: Boolean

    /**
     * Switched on in Settings, and off by default.
     *
     * Looking for a Cast device means Google Play services sending messages around your network,
     * and Ondalune does not do that behind your back: until this is on, the framework is never
     * started, nothing is discovered and the Cast button is not drawn.
     */
    var enabled: Boolean

    val state: StateFlow<CastState>

    /** Starts the Cast framework. Called once, from the activity, and only when [supported]. */
    fun attach(activity: android.app.Activity)

    /**
     * Hands a song to the connected device.
     *
     * @param streamUrl the URL the device fetches for itself: it must be reachable from the
     *   device, not only from this phone.
     */
    fun load(
        track: SubsonicTrack,
        streamUrl: String,
        coverUrl: String,
        positionMs: Long,
        autoplay: Boolean
    )

    fun play()

    fun pause()

    fun seekTo(positionMs: Long)

    /** Stops the device and hands playback back to the phone. */
    fun stop()

    /** Where the device has got to, in milliseconds; 0 when nothing is playing there. */
    fun positionMs(): Long

    /** How long the device says the song is; 0 when it does not know yet. */
    fun durationMs(): Long

    /** Called on the main thread when the device finishes a song, so the queue can move on. */
    fun onTrackEnded(callback: () -> Unit)

    /** Told when a connection fails, so it can be said out loud rather than spun on for ever. */
    fun onFailure(callback: (String) -> Unit)
}

/** What the Cast session is doing, as the rest of the app needs to know it. */
data class CastState(
    val connected: Boolean = false,
    /** The device's own name, as it calls itself: "Living room", "Kitchen display". */
    val deviceName: String = "",
    val playing: Boolean = false,
    /** Set while the device is still joining, so the UI can say "Connecting…". */
    val connecting: Boolean = false
) {
    /** The sound engine is bypassed exactly when something else is doing the playing. */
    val enginePaused: Boolean get() = connected
}
