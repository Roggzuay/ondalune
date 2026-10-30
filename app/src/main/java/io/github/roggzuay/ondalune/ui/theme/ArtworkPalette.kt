package io.github.roggzuay.ondalune.ui.theme

import android.content.Context
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import io.github.roggzuay.ondalune.data.model.SubsonicTrack
import com.materialkolor.quantize.QuantizerCelebi
import com.materialkolor.score.Score
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Colors pulled from the actual cover art: [seed] drives the Material tonal theme (same
 * quantize + score pipeline Android uses for wallpaper color), [swatches] are the cover's most
 * prominent colors for the animated player background.
 */
data class ArtworkPalette(
    val seed: Color,
    val swatches: List<Color>,
    /** The cover's own colour in each region: top-left, top-right, bottom-left, bottom-right, centre. */
    val regions: List<Color> = emptyList()
) {
    val seedHex: String get() = "#%06X".format(seed.toArgb() and 0xFFFFFF)
}

val LocalArtworkPalette = staticCompositionLocalOf {
    ArtworkPalette(OndaluneFallbackSeed, listOf(OndaluneFallbackSeed, Color(0xFF70F5FF)))
}

private val OndaluneFallbackSeed = Color(0xFFFF6B5B)

private val AuthParams = setOf("u", "t", "s", "p", "v", "c", "f", "apiKey")

/**
 * Stable cache key for a Subsonic cover URL: drops the per-request auth salt/token so the same
 * cover hits the image cache even after a re-sync regenerates its URL.
 */
fun artworkCacheKey(url: String): String {
    val uri = android.net.Uri.parse(url)
    if (uri.query.isNullOrEmpty()) return url
    val builder = uri.buildUpon().clearQuery()
    uri.queryParameterNames.filterNot { it in AuthParams }.sorted().forEach { name ->
        uri.getQueryParameters(name).forEach { builder.appendQueryParameter(name, it) }
    }
    return builder.build().toString()
}

object ArtworkPaletteCache {
    private val cache = LruCache<String, ArtworkPalette>(96)

    /** Already-extracted palette for [url], if any. Safe to call from non-UI code (notification, widget). */
    fun peek(url: String): ArtworkPalette? = if (url.isBlank()) null else cache.get(artworkCacheKey(url))

    /** Seed hex for [track]: extracted from its cover when available, else the server metadata hint. */
    fun seedHexFor(track: SubsonicTrack): String = peek(track.coverArtUrl)?.seedHex ?: track.paletteSeedHex

    suspend fun load(context: Context, url: String): ArtworkPalette? {
        if (url.isBlank()) return null
        cache.get(artworkCacheKey(url))?.let { return it }
        val request = ImageRequest.Builder(context)
            .data(url)
            .diskCacheKey(artworkCacheKey(url))
            .size(112) // quantizing ~12k pixels is plenty and stays well under a frame of work
            .allowHardware(false)
            .build()
        val result = context.imageLoader.execute(request) as? SuccessResult ?: return null
        val palette = withContext(Dispatchers.Default) {
            val bitmap = result.drawable.toBitmap()
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val quantized = QuantizerCelebi.quantize(pixels, 128)
            val ranked = Score.score(quantized, 4, OndaluneFallbackSeed.toArgb(), true)
            val dominant = quantized.entries.sortedByDescending { it.value }.take(4).map { it.key }
            val w = bitmap.width
            val h = bitmap.height
            // Black-and-white covers: Score finds nothing colourful and falls back to the app's
            // coral, which tinted the whole player pink. Use the cover's own grey instead; a
            // grey seed switches the scheme to Material's Monochrome palette (see Theme.kt).
            val hsv = FloatArray(3)
            val colourful = pixels.count { android.graphics.Color.colorToHSV(it, hsv); hsv[1] * hsv[2] > 0.22f && hsv[2] > 0.15f }
            val grayscale = colourful < pixels.size * 0.02f
            ArtworkPalette(
                seed = if (grayscale) Color(dominant.first()).toNeutralGrey() else Color(ranked.first()),
                swatches = (dominant + ranked).distinct().take(5).map { Color(it) },
                regions = listOf(
                    regionColor(pixels, w, 0, 0, w / 2, h / 2),
                    regionColor(pixels, w, w / 2, 0, w, h / 2),
                    regionColor(pixels, w, 0, h / 2, w / 2, h),
                    regionColor(pixels, w, w / 2, h / 2, w, h),
                    regionColor(pixels, w, w / 4, h / 4, w * 3 / 4, h * 3 / 4)
                )
            )
        }
        cache.put(artworkCacheKey(url), palette)
        return palette
    }
}

/** Same lightness, no colour; kept mid-tone so the derived scheme has usable contrast. */
private fun Color.toNeutralGrey(): Color {
    val hsl = FloatArray(3)
    androidx.core.graphics.ColorUtils.colorToHSL(toArgb(), hsl)
    return Color(androidx.core.graphics.ColorUtils.HSLToColor(floatArrayOf(0f, 0f, hsl[2].coerceIn(0.35f, 0.65f))))
}

/**
 * The colour a region of the cover reads as: its most common cluster, unless that is a near-grey
 * (white sky, black border) and a real colour covers at least a fifth of the region.
 */
private fun regionColor(pixels: IntArray, stride: Int, x0: Int, y0: Int, x1: Int, y1: Int): Color {
    val region = IntArray((x1 - x0) * (y1 - y0))
    var i = 0
    for (y in y0 until y1) for (x in x0 until x1) region[i++] = pixels[y * stride + x]
    val clusters = QuantizerCelebi.quantize(region, 6).entries.sortedByDescending { it.value }
    if (clusters.isEmpty()) return OndaluneFallbackSeed
    val total = clusters.sumOf { it.value }.toFloat()
    val hsv = FloatArray(3)
    fun saturation(argb: Int): Float { android.graphics.Color.colorToHSV(argb, hsv); return hsv[1] * hsv[2] }
    val top = clusters.first().key
    val colourful = clusters.firstOrNull { saturation(it.key) > 0.25f && it.value / total >= 0.2f }?.key
    return Color(if (saturation(top) < 0.12f && colourful != null) colourful else top)
}

/**
 * Palette for the current [track]. Returns the metadata fallback immediately and swaps to the
 * real cover-art colors as soon as extraction finishes (cached per cover URL).
 */
@Composable
fun rememberArtworkPalette(track: SubsonicTrack?): ArtworkPalette {
    val context = LocalContext.current
    val fallback = ArtworkPalette(
        seed = parseHexColor(track?.paletteSeedHex ?: "", OndaluneFallbackSeed),
        swatches = listOfNotNull(
            parseHexColor(track?.paletteSeedHex ?: "", OndaluneFallbackSeed),
            parseHexColor(track?.accentHex ?: "", Color(0xFF70F5FF))
        )
    )
    val url = track?.coverArtUrl.orEmpty()
    val palette by produceState(ArtworkPaletteCache.peek(url) ?: fallback, url, track?.id) {
        // Keep the current colors until the new cover's are ready; the metadata hint is only a
        // guess and flashed a wrong color for a moment on every track change.
        if (url.isBlank()) {
            value = fallback
            return@produceState
        }
        ArtworkPaletteCache.peek(url)?.let { value = it }
        value = ArtworkPaletteCache.load(context, url) ?: fallback
    }
    return palette
}
