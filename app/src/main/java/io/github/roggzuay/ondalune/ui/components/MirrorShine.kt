package io.github.roggzuay.ondalune.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import io.github.roggzuay.ondalune.ui.theme.artworkCacheKey

/**
 * While [active], light sweeps across the element like a reflection over a mirror: a soft glow
 * with a thin bright streak in it, then a short rest before the next pass.
 */
fun Modifier.mirrorShine(active: Boolean): Modifier = if (!active) this else composed {
    val sweep by rememberInfiniteTransition(label = "mirrorShine").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1300, delayMillis = 450, easing = FastOutSlowInEasing)),
        label = "sweep"
    )
    drawWithContent {
        drawContent()
        val band = (size.width + size.height) * 0.45f
        // Along a shallow diagonal, from fully off the left edge to fully off the right.
        val at = -band - size.height * 0.5f + sweep * (size.width + size.height * 0.5f + band * 2f)
        val start = Offset(at, 0f)
        drawRect(
            Brush.linearGradient(
                0f to Color.Transparent,
                0.38f to Color.White.copy(alpha = 0.07f),
                0.5f to Color.White.copy(alpha = 0.30f),
                0.54f to Color.White.copy(alpha = 0.10f),
                1f to Color.Transparent,
                start = start,
                end = start + Offset(band, band * 0.5f)
            )
        )
    }
}

/**
 * A cover or photo from [url], kept under its stable [artworkCacheKey] like every cover (so the
 * copies ArtworkStore saved are found), shining while it loads.
 */
@Composable
fun ArtworkImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    crossfadeMs: Int = 220
) {
    val context = LocalContext.current
    var loading by remember(url) { mutableStateOf(!url.isNullOrBlank()) }
    val request = remember(url) {
        url?.takeIf { it.isNotBlank() }?.let {
            val key = artworkCacheKey(it)
            ImageRequest.Builder(context).data(it).memoryCacheKey(key).diskCacheKey(key).crossfade(crossfadeMs).build()
        }
    }
    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        contentScale = contentScale,
        onState = { loading = it is AsyncImagePainter.State.Loading },
        modifier = modifier.mirrorShine(loading)
    )
}
