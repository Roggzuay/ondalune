package io.github.roggzuay.ondalune.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.graphics.drawable.AnimatedImageDrawable
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.Surface
import android.view.TextureView
import android.widget.ImageView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import io.github.roggzuay.ondalune.data.model.SubsonicTrack
import io.github.roggzuay.ondalune.data.motion.MotionArtworkMode
import io.github.roggzuay.ondalune.data.motion.MotionArtworkRepository
import io.github.roggzuay.ondalune.data.motion.MotionArtworkSettings
import io.github.roggzuay.ondalune.data.motion.MotionAsset
import io.github.roggzuay.ondalune.data.motion.MotionIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** What the player's cover does. */
enum class MotionLook { STILL, ORIGINAL }

/**
 * The album's own moving artwork when it has some; otherwise, or when motion is off, Android's
 * animations are switched off or battery saver is on, the still cover.
 */
fun motionLook(mode: MotionArtworkMode, hasOriginal: Boolean, reducedMotion: Boolean, powerSave: Boolean): MotionLook = when {
    mode == MotionArtworkMode.DISABLED || reducedMotion || powerSave -> MotionLook.STILL
    hasOriginal -> MotionLook.ORIGINAL
    else -> MotionLook.STILL
}

/** Moving artwork only runs while the full player is open and Ondalune is on screen. */
fun motionRuns(playerOpen: Boolean, lifecycle: Lifecycle.State) = playerOpen && lifecycle.isAtLeast(Lifecycle.State.RESUMED)

/** The player's access to moving artwork; null where there is none (previews, tests). */
class MotionArtworkUi(val repository: MotionArtworkRepository, val settings: State<MotionArtworkSettings>)

val LocalMotionArtwork = staticCompositionLocalOf<MotionArtworkUi?> { null }

/** How [track]'s cover shows right now, and the original artwork when that is what shows. */
@Composable
fun rememberMotionLook(track: SubsonicTrack): Pair<MotionLook, MotionAsset?> {
    val ui = LocalMotionArtwork.current ?: return MotionLook.STILL to null
    val found by ui.repository.found.collectAsState()
    val asset = found[MotionIdentity.of(track).releaseKey]
    val look = motionLook(
        mode = ui.settings.value.mode,
        hasOriginal = asset != null,
        reducedMotion = rememberReducedMotion(),
        powerSave = rememberPowerSave()
    )
    return look to asset.takeIf { look == MotionLook.ORIGINAL }
}

/**
 * [track]'s cover in the player: [still] as it always was, with the album's moving artwork faded
 * in over it once its first frame is ready, or the cover itself drifting gently. Nothing moves
 * unless [playerOpen]; songs of another album never show this album's artwork.
 */
@Composable
fun MotionCover(
    track: SubsonicTrack,
    playerOpen: Boolean,
    modifier: Modifier = Modifier,
    /** False when the album's moving artwork plays elsewhere (full screen behind the player). */
    original: Boolean = true,
    still: @Composable () -> Unit
) {
    val asset = rememberMotionLook(track).second.takeIf { original }
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val running = motionRuns(playerOpen, lifecycle)
    val ui = LocalMotionArtwork.current
    Box(modifier.clipToBounds()) {
        still()
        if (asset != null) {
            var ready by remember(asset.location) { mutableStateOf(false) }
            val shown by animateFloatAsState(if (ready) 1f else 0f, tween(700), label = "motion-fade")
            val onFailed = { ui?.repository?.reportUnplayable(asset); Unit }
            val layer = Modifier.fillMaxSize().graphicsLayer { alpha = shown }
            if (asset.format.isVideo) {
                LoopingVideo(asset.location, running, null, onFirstFrame = { ready = true }, onFailed = onFailed, modifier = layer)
            } else {
                AnimatedPicture(asset.location, running, null, onReady = { ready = true }, onFailed = onFailed, modifier = layer)
            }
        }
    }
}

/**
 * The album's moving artwork over the whole player, when it has some. Artwork about as tall as
 * the screen fills it; square or wide artwork is shown whole across the width at the top, never
 * zoomed in, with a blurred copy of the same moving frames filling the rest. [shown]
 * follows it in: 0 while there is none or its first frame isn't ready, 1 once it covers the
 * screen, so the still cover can step aside. Soft shades top and bottom keep the bars and
 * controls readable on any picture; [shade] (1 with everything showing, 0 with only the play
 * buttons left) lightens them as the rest of the player steps aside.
 */
@Composable
fun MotionBackdrop(
    track: SubsonicTrack,
    playerOpen: Boolean,
    shown: MutableFloatState,
    modifier: Modifier = Modifier,
    shade: () -> Float = { 1f }
) {
    val (_, asset) = rememberMotionLook(track)
    if (asset == null) {
        SideEffect { shown.floatValue = 0f }
        return
    }
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val running = motionRuns(playerOpen, lifecycle)
    val ui = LocalMotionArtwork.current
    var ready by remember(asset.location) { mutableStateOf(false) }
    val alpha by animateFloatAsState(if (ready && playerOpen) 1f else 0f, tween(if (playerOpen) 700 else 200), label = "motion-backdrop")
    // Followed frame by frame: the fade is read only while drawing, so nothing recomposes for it.
    LaunchedEffect(shown) { snapshotFlow { alpha }.collect { shown.floatValue = it } }
    DisposableEffect(shown) { onDispose { shown.floatValue = 0f } }
    val surface = MaterialTheme.colorScheme.surface
    val onFailed = { ui?.repository?.reportUnplayable(asset); Unit }
    val frames = remember(asset.location) { MotionFrames() }
    // The blurred copy follows the artwork at about 16 frames a second, and holds still with it.
    LaunchedEffect(frames, ready, running) {
        if (!ready) return@LaunchedEffect
        do {
            frames.capture()
            delay(MotionFrames.EVERY_MS)
        } while (running)
    }
    BoxWithConstraints(
        modifier
            .clipToBounds()
            .graphicsLayer { this.alpha = alpha }
            .drawWithContent {
                drawContent()
                val s = shade()
                drawRect(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.32f * s), 1f to Color.Transparent, endY = size.height * 0.16f))
                // Never quite gone: the play buttons left at the bottom still need it.
                val bottom = 0.4f + 0.6f * s
                drawRect(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.55f to surface.copy(alpha = 0.55f * bottom),
                        1f to surface.copy(alpha = 0.82f * bottom),
                        startY = size.height * 0.5f,
                        endY = size.height
                    )
                )
            }
    ) {
        val aspect = frames.aspect
        val fills = aspect > 0f && aspect <= maxWidth / maxHeight * 1.5f
        val sharp = if (fills) Modifier.fillMaxSize() else {
            MotionBlurFill(frames, Modifier.fillMaxSize())
            Modifier
                .fillMaxWidth()
                .aspectRatio(if (aspect > 0f) aspect else 1f)
                // Its lower edge melts into the blur instead of ending in a line.
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(Brush.verticalGradient(0.78f to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
                }
        }
        if (asset.format.isVideo) {
            LoopingVideo(asset.location, running, frames, onFirstFrame = { ready = true }, onFailed = onFailed, modifier = sharp)
        } else {
            AnimatedPicture(asset.location, running, frames, onReady = { ready = true }, onFailed = onFailed, modifier = sharp)
        }
    }
}

/**
 * Small copies of the frame on screen, for the blurred fill around artwork that doesn't fill the
 * screen. The players say how to take one ([grab]) and the artwork's shape ([aspect]).
 */
private class MotionFrames {
    var aspect by mutableFloatStateOf(0f)
    var bitmap: Bitmap? = null
        private set
    /** Bumped with each copy: the fill redraws on it. */
    var version by mutableIntStateOf(0)
    var grab: ((Bitmap) -> Unit)? = null

    fun capture() {
        val take = grab ?: return
        if (aspect <= 0f) return
        val w = (if (aspect >= 1f) SIZE else SIZE * aspect).roundToInt().coerceAtLeast(1)
        val h = (if (aspect >= 1f) SIZE / aspect else SIZE).roundToInt().coerceAtLeast(1)
        val b = bitmap?.takeIf { it.width == w && it.height == h } ?: Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bitmap = it }
        if (runCatching { take(b) }.isSuccess) version++
    }

    companion object {
        /** Longest side of a copy: it is blurred anyway, and small copies cost next to nothing. */
        const val SIZE = 64f
        const val EVERY_MS = 62L
    }
}

/** The latest copy, filling the screen, blurred and a little darkened. */
@Composable
private fun MotionBlurFill(frames: MotionFrames, modifier: Modifier) {
    val blur = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(56.dp, BlurredEdgeTreatment.Rectangle) else Modifier
    Canvas(modifier.then(blur)) {
        frames.version
        val b = frames.bitmap ?: return@Canvas
        val scale = maxOf(size.width / b.width, size.height / b.height)
        val w = (b.width * scale).roundToInt()
        val h = (b.height * scale).roundToInt()
        drawImage(
            b.asImageBitmap(),
            dstOffset = IntOffset(((size.width - w) / 2).roundToInt(), ((size.height - h) / 2).roundToInt()),
            dstSize = IntSize(w, h),
            filterQuality = FilterQuality.Low
        )
        drawRect(Color.Black.copy(alpha = 0.22f))
    }
}

/**
 * A silent looping video, cropped to fill its frame. It never asks for audio focus or adds its
 * own sound; it pauses whenever [running] goes false and is released when it leaves the screen.
 */
@Composable
private fun LoopingVideo(location: String, running: Boolean, frames: MotionFrames?, onFirstFrame: () -> Unit, onFailed: () -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val latestRunning by rememberUpdatedState(running)
    val player = remember(location) { LoopingVideoPlayer(context, location, onFirstFrame, onFailed) { w, h -> frames?.aspect = w.toFloat() / h } }
    DisposableEffect(player) { onDispose { player.release() } }
    LaunchedEffect(player, running) { player.setRunning(running) }
    AndroidView(
        factory = { ctx -> TextureView(ctx).also { view -> player.attach(view) { latestRunning }; frames?.grab = { view.getBitmap(it) } } },
        modifier = modifier.testTag("motion_artwork_video")
    )
}

private class LoopingVideoPlayer(
    private val context: Context,
    private val location: String,
    private val onFirstFrame: () -> Unit,
    private val onFailed: () -> Unit,
    private val onSize: (Int, Int) -> Unit
) {
    private var player: MediaPlayer? = null
    private var surface: Surface? = null
    private var prepared = false
    private var wantRunning = false
    private var videoW = 0
    private var videoH = 0
    private var view: TextureView? = null

    fun attach(textureView: TextureView, running: () -> Boolean) {
        view = textureView
        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                wantRunning = running()
                start(Surface(st))
            }
            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) = crop()
            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                release()
                return true
            }
            override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
        }
    }

    private fun start(s: Surface) {
        surface = s
        val mp = MediaPlayer()
        player = mp
        try {
            if (location.startsWith("content://")) mp.setDataSource(context, Uri.parse(location)) else mp.setDataSource(location)
            mp.setSurface(s)
            mp.isLooping = true
            mp.setVolume(0f, 0f)
            mp.setOnVideoSizeChangedListener { _, w, h ->
                videoW = w
                videoH = h
                if (w > 0 && h > 0) onSize(w, h)
                crop()
            }
            mp.setOnInfoListener { _, what, _ ->
                if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) onFirstFrame()
                false
            }
            mp.setOnErrorListener { _, _, _ -> onFailed(); true }
            mp.setOnPreparedListener {
                prepared = true
                // Show a first frame even when paused, so there is never a black square.
                if (wantRunning) it.start() else { it.start(); it.pause() }
            }
            mp.prepareAsync()
        } catch (_: Exception) {
            onFailed()
        }
    }

    fun setRunning(running: Boolean) {
        wantRunning = running
        val mp = player ?: return
        if (!prepared) return
        runCatching { if (running && !mp.isPlaying) mp.start() else if (!running && mp.isPlaying) mp.pause() }
    }

    /** Fills the view like a centre-cropped picture, whatever the video's shape. */
    private fun crop() {
        val v = view ?: return
        if (videoW == 0 || videoH == 0 || v.width == 0 || v.height == 0) return
        val viewRatio = v.width.toFloat() / v.height
        val videoRatio = videoW.toFloat() / videoH
        val (sx, sy) = if (videoRatio > viewRatio) videoRatio / viewRatio to 1f else 1f to viewRatio / videoRatio
        v.setTransform(Matrix().apply { setScale(sx, sy, v.width / 2f, v.height / 2f) })
    }

    fun release() {
        runCatching { player?.release() }
        player = null
        prepared = false
        surface?.release()
        surface = null
    }
}

/** An animated WebP or GIF, decoded a frame at a time by Android (9 and newer) and cropped to fill. */
@Composable
private fun AnimatedPicture(location: String, running: Boolean, frames: MotionFrames?, onReady: () -> Unit, onFailed: () -> Unit, modifier: Modifier) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
    val context = LocalContext.current
    var drawable by remember(location) { mutableStateOf<AnimatedImageDrawable?>(null) }
    LaunchedEffect(location) {
        drawable = withContext(Dispatchers.IO) {
            runCatching {
                val source = if (location.startsWith("content://")) ImageDecoder.createSource(context.contentResolver, Uri.parse(location))
                else ImageDecoder.createSource(File(location))
                ImageDecoder.decodeDrawable(source) { decoder, info, _ ->
                    // No bigger than a phone screen needs: frames are decoded at this size.
                    val longest = maxOf(info.size.width, info.size.height)
                    if (longest > 1080) decoder.setTargetSampleSize((longest + 1079) / 1080)
                } as? AnimatedImageDrawable
            }.getOrNull()
        }
        if (frames != null) drawable?.let { d ->
            frames.aspect = d.intrinsicWidth.toFloat() / d.intrinsicHeight.coerceAtLeast(1)
            // The same drawable, drawn small: its frame always matches the one on screen.
            frames.grab = { b ->
                b.eraseColor(0)
                if (d.bounds.isEmpty) d.setBounds(0, 0, d.intrinsicWidth, d.intrinsicHeight)
                val c = android.graphics.Canvas(b)
                c.scale(b.width.toFloat() / d.intrinsicWidth, b.height.toFloat() / d.intrinsicHeight)
                d.draw(c)
            }
        }
        if (drawable == null) onFailed() else onReady()
    }
    val d = drawable ?: return
    LaunchedEffect(d, running) {
        d.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
        if (running) d.start() else d.stop()
    }
    DisposableEffect(d) { onDispose { d.stop() } }
    AndroidView(
        factory = { ctx -> ImageView(ctx).apply { scaleType = ImageView.ScaleType.CENTER_CROP } },
        update = { it.setImageDrawable(d) },
        modifier = modifier.testTag("motion_artwork_picture")
    )
}

/** True when "Remove animations" (or animator duration 0) is on in Android's settings. */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)
    }
}

/** Follows battery saver as it is switched on and off. */
@Composable
fun rememberPowerSave(): Boolean {
    val context = LocalContext.current
    val pm = remember { context.getSystemService(PowerManager::class.java) }
    var on by remember { mutableStateOf(pm?.isPowerSaveMode == true) }
    DisposableEffect(pm) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) { on = pm?.isPowerSaveMode == true }
        }
        context.registerReceiver(receiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    return on
}

/** Where the player's cover comes from: opened by holding the cover. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MotionArtworkInfoSheet(track: SubsonicTrack, onDismiss: () -> Unit) {
    val (look, asset) = rememberMotionLook(track)
    val mode = LocalMotionArtwork.current?.settings?.value?.mode
    val calm = rememberReducedMotion() || rememberPowerSave()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding()
                .testTag("motion_artwork_info_sheet"),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Artwork", style = MaterialTheme.typography.headlineSmall)
            InfoRow("Album", listOf(track.album, track.artist).filter { it.isNotBlank() }.joinToString(", "))
            when (look) {
                MotionLook.ORIGINAL -> {
                    asset!!
                    InfoRow("What you see", "Moving artwork that came with the album")
                    InfoRow("Source", asset.providerName)
                    InfoRow("File", asset.format.label + if (asset.bytes > 0) ", " + sizeLabel(asset.bytes) else "")
                    asset.attribution?.let { InfoRow("Credit", it) }
                }
                MotionLook.STILL -> InfoRow(
                    "What you see",
                    when {
                        mode == MotionArtworkMode.DISABLED -> "The still cover: motion artwork is off in Settings"
                        calm && mode != null -> "The still cover: battery saver or Android's \"Remove animations\" is on"
                        mode == null -> "The still cover"
                        else -> "The still cover from your library"
                    }
                )
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

/** "110 KB", "4.2 MB": small artwork files don't round down to nothing. */
fun sizeLabel(bytes: Long): String =
    if (bytes < 1024L * 1024) "${(bytes + 1023) / 1024} KB" else "%.1f MB".format(bytes / (1024.0 * 1024))
