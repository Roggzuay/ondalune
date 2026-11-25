package io.github.roggzuay.ondalune.cast

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.roggzuay.ondalune.data.model.SubsonicTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Casting, in the build that has no Cast SDK: a plain, honest no.
 *
 * This file and `src/cast` define exactly the same names, so nothing above them has to ask which
 * build it is in. Nothing here allocates, listens or starts anything.
 */
class NoCastGateway : CastGateway {
    override val supported: Boolean = false
    override val state: StateFlow<CastState> = MutableStateFlow(CastState())
    override fun attach(activity: Activity) = Unit
    override fun load(
        track: SubsonicTrack,
        streamUrl: String,
        coverUrl: String,
        positionMs: Long,
        autoplay: Boolean
    ) = Unit
    override fun play() = Unit
    override fun pause() = Unit
    override fun seekTo(positionMs: Long) = Unit
    override fun stop() = Unit
    override fun positionMs(): Long = 0L
    override fun durationMs(): Long = 0L
    override fun onTrackEnded(callback: () -> Unit) = Unit
}

fun createCastGateway(context: Context): CastGateway = NoCastGateway()

/** No Cast SDK, so no button: the top bar simply has one fewer thing in it. */
@Composable
fun CastButton(modifier: Modifier = Modifier) = Unit

/**
 * Named in the manifest so the entry points at a class that exists in both builds. Nothing reads
 * it here: without Play Services there is nothing to read it.
 */
class OndaluneCastOptions
