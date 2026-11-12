@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package io.github.roggzuay.ondalune.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.LongState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.roggzuay.ondalune.data.lyrics.LyricsQuality
import io.github.roggzuay.ondalune.data.lyrics.LyricsSource
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import io.github.roggzuay.ondalune.data.lyrics.isResting
import io.github.roggzuay.ondalune.data.lyrics.lineIndexAt
import io.github.roggzuay.ondalune.data.lyrics.sungFraction
import io.github.roggzuay.ondalune.data.model.LyricLine
import io.github.roggzuay.ondalune.data.model.LyricWord
import io.github.roggzuay.ondalune.data.model.TrackLyricsResult
import io.github.roggzuay.ondalune.data.model.InterfaceStyle
import io.github.roggzuay.ondalune.ui.theme.LocalInterfaceStyle
import io.github.roggzuay.ondalune.ui.theme.LocalReducedMotion
import io.github.roggzuay.ondalune.ui.theme.cornerShape
import io.github.roggzuay.ondalune.ui.theme.monoLabelSmall
import kotlinx.coroutines.delay

/**
 * The lyrics, in the place of the cover.
 *
 * Synced lyrics follow [audiblePositionMs], the position being heard, and show only what the
 * source states. A line lights up at its start. A line with word timing fills word by word, each
 * word from its own start to its own end, and stops with the last one. A line whose vocal has an
 * end goes quiet there and stays quiet through the instrumental until the next line starts.
 * Nothing is estimated from tempo or from the length of the text.
 */
@Composable
internal fun SyncedLyricsPane(
    lyrics: List<LyricLine>,
    lyricsResult: TrackLyricsResult,
    audiblePositionMs: () -> Long,
    isPlaying: Boolean,
    onSeekToLyric: (Long) -> Unit,
    modifier: Modifier = Modifier,
    source: LyricsSource = LyricsSource(),
    onOpenSource: () -> Unit = {}
) {
    val lineStyle = TextStyle(
        fontFamily = MaterialTheme.typography.titleLarge.fontFamily,
        fontWeight = if (LocalInterfaceStyle.current == InterfaceStyle.STANDARD) FontWeight.Bold else FontWeight.ExtraBold,
        fontSize = 27.sp,
        lineHeight = 35.sp,
        letterSpacing = (-0.3).sp
    )
    // Lines fade out at the top and bottom edges instead of hard-clipping.
    val edgeFade = Modifier
        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        .drawWithContent {
            drawContent()
            drawRect(
                brush = Brush.verticalGradient(0f to Color.Transparent, 0.10f to Color.Black, 0.86f to Color.Black, 1f to Color.Transparent),
                blendMode = BlendMode.DstIn
            )
        }

    Box(modifier.fillMaxSize().testTag("full_area_blurred_lyrics_pane")) {
        when {
            // Another track's lyrics start over: scroll position, follow state and clock.
            lyrics.isNotEmpty() -> key(lyrics) {
                SyncedLines(lyrics, lineStyle, audiblePositionMs, isPlaying, onSeekToLyric, edgeFade)
            }

            lyricsResult.plainTextLines.isNotEmpty() -> LazyColumn(
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = edgeFade.fillMaxSize().testTag("plain_unsynced_lyrics_list")
            ) {
                item {
                    Text(
                        text = "UNSYNCED LYRICS • ${lyricsResult.sourceLabel}",
                        style = monoLabelSmall(),
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
                itemsIndexed(lyricsResult.plainTextLines) { idx, plainLine ->
                    Text(
                        text = plainLine,
                        style = lineStyle.copy(fontSize = 23.sp, lineHeight = 31.sp, fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                        modifier = Modifier.fillMaxWidth().testTag("plain_lyric_line_$idx")
                    )
                }
            }

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp)
                    .testTag("unavailable_lyrics_state"),
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Lyrics,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(38.dp)
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "No lyrics for this track",
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (source.offers > 0) "Your server has none. Possible matches were found on LRCLIB; pick the right one."
                    else "Your server didn't return synced or plain lyrics, and none are embedded in the file.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                FilledTonalButton(onClick = onOpenSource, modifier = Modifier.testTag("find_lyrics_button")) {
                    Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (source.offers > 0) "Choose lyrics" else "Find lyrics")
                }
            }
        }
        // Where these lyrics come from, and the way to change them.
        if (lyrics.isNotEmpty() || lyricsResult.plainTextLines.isNotEmpty()) {
            val offer = source.fromServer && source.offers > 0 && source.serverQuality != LyricsQuality.WORDS
            Surface(
                onClick = onOpenSource,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f),
                contentColor = if (offer) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(bottom = 4.dp)
                    .heightIn(min = 48.dp)
                    .padding(vertical = 6.dp)
                    .semantics { contentDescription = "Lyrics source: ${source.label}. Change lyrics" }
                    .testTag("lyrics_source_chip")
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(
                        text = if (offer) "Better-timed lyrics may be online · Choose" else "${source.label.ifEmpty { "Lyrics" }} · Change",
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun SyncedLines(
    lyrics: List<LyricLine>,
    baseStyle: TextStyle,
    audiblePositionMs: () -> Long,
    isPlaying: Boolean,
    onSeekToLyric: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val lineStyle = when {
            maxHeight < 280.dp -> baseStyle.copy(fontSize = 21.sp, lineHeight = 28.sp)
            maxWidth >= 520.dp && maxHeight >= 480.dp -> baseStyle.copy(fontSize = 34.sp, lineHeight = 44.sp)
            else -> baseStyle
        }
        // The current line rests here, with the lines already sung above it.
        val anchor = maxHeight * 0.3f

        // The audible position, sampled once a frame. The current line is derived from it and its
        // row reads it while drawing, so a tick recomposes nothing until the line changes.
        val position = remember { mutableLongStateOf(audiblePositionMs()) }
        val latestPosition by rememberUpdatedState(audiblePositionMs)
        LaunchedEffect(isPlaying) {
            while (true) {
                withFrameNanos { position.longValue = latestPosition() }
                // Paused, the position only moves on a seek.
                if (!isPlaying) delay(120)
            }
        }
        val activeIdx by remember(lyrics) { derivedStateOf { lyrics.lineIndexAt(position.longValue) } }

        val listState = rememberLazyListState(initialFirstVisibleItemIndex = activeIdx.coerceAtLeast(0))
        // Scrolling the lyrics by hand stops them following the song until 3 s after letting go.
        val dragged by listState.interactionSource.collectIsDraggedAsState()
        var follow by remember { mutableStateOf(true) }
        LaunchedEffect(dragged) {
            if (dragged) follow = false
            else if (!follow) {
                delay(3000)
                follow = true
            }
        }
        val reducedMotion = LocalReducedMotion.current
        LaunchedEffect(lyrics, activeIdx, follow) {
            if (!follow) return@LaunchedEffect
            // Before the first line is sung, the first line waits at the anchor.
            val target = activeIdx.coerceAtLeast(0)
            // A line on screen glides to its place; one that is far away (a seek) is jumped to.
            val shown = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == target }
            if (shown != null && !reducedMotion) listState.animateScrollBy(shown.offset.toFloat(), LyricGlide)
            else listState.scrollToItem(target)
        }

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                start = 4.dp,
                end = 4.dp,
                top = anchor,
                // Room for the last line to reach the anchor.
                bottom = (maxHeight - anchor - 48.dp).coerceAtLeast(0.dp)
            ),
            modifier = modifier.fillMaxSize().testTag("synced_lyrics_list")
        ) {
            itemsIndexed(lyrics) { index, line ->
                LyricRow(line, index, order = index.compareTo(activeIdx), position, lineStyle, onSeekToLyric)
            }
        }

        AnimatedVisibility(
            visible = !follow && activeIdx >= 0,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp)
        ) {
            FilledTonalButton(onClick = { follow = true }, modifier = Modifier.testTag("lyrics_follow_button")) {
                Text("Back to current line")
            }
        }
    }
}

/** One line; [order] is negative once it has been sung, 0 while it is the current line, positive before. */
@Composable
private fun LyricRow(
    line: LyricLine,
    index: Int,
    order: Int,
    position: LongState,
    style: TextStyle,
    onSeek: (Long) -> Unit
) {
    val current = order == 0
    val timedWords = line.words.isNotEmpty()
    // Sung, and nothing is being sung: the vocal has ended and the next line has not started.
    val resting by remember(line, current) { derivedStateOf { current && line.isResting(position.longValue) } }
    val singing = current && !resting

    // The text itself. A line with word timing stays dim while it is sung; its words light up on top.
    val emphasis = animateFloatAsState(
        targetValue = when {
            order < 0 -> 0.4f
            resting -> RestingEmphasis
            singing && !timedWords -> 1f
            else -> 0.58f
        },
        animationSpec = if (singing) LyricRise else if (resting) LyricSettle else LyricGlide,
        label = "lyricEmphasis"
    )
    val sung = animateFloatAsState(
        targetValue = if (singing && timedWords) 1f else 0f,
        animationSpec = if (singing) LyricRise else LyricSettle,
        label = "lyricSung"
    )
    val scale = animateFloatAsState(if (current) 1f else 0.94f, LyricGlide, label = "lyricScale")

    val color = MaterialTheme.colorScheme.onSurface
    val glowColor = MaterialTheme.colorScheme.primary
    val glow = remember(glowColor) { Shadow(glowColor.copy(alpha = 0.75f), Offset(0f, 2f), 22f) }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val sungArea = remember { Path() }

    BasicText(
        text = line.text,
        style = if (singing && !timedWords) style.copy(shadow = glow) else style,
        color = { color.copy(alpha = emphasis.value) },
        onTextLayout = if (timedWords) { measured -> layout = measured } else null,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                transformOrigin = TransformOrigin(0f, 0.5f)
            }
            .clip(cornerShape(16.dp))
            .clickable(onClickLabel = "Play from this line") { onSeek(line.timeMs) }
            .padding(8.dp)
            .then(
                if (!timedWords) Modifier else Modifier.drawWithContent {
                    drawContent()
                    val alpha = sung.value
                    val measured = layout
                    if (alpha > 0f && measured != null) {
                        sungArea.setSung(measured, line.words, position.longValue)
                        clipPath(sungArea) { drawText(measured, color = color.copy(alpha = alpha), shadow = glow) }
                    }
                }
            )
            .semantics { selected = current }
            .testTag("lyric_line_$index")
    )
}

/** Makes this the area of [layout] covered by what has been sung of [words] at [positionMs]. */
private fun Path.setSung(layout: TextLayoutResult, words: List<LyricWord>, positionMs: Long) {
    rewind()
    val length = layout.layoutInput.text.length
    var next = 0
    for (word in words) {
        // The words put together are the line, each keeping the space after it.
        val start = next
        next += word.text.length
        val end = minOf(start + word.text.trimEnd().length, length)
        val fraction = word.sungFraction(positionMs)
        if (fraction <= 0f || end <= start) continue
        val row = layout.getLineForOffset(start)
        if (row == layout.getLineForOffset(end - 1)) {
            val first = layout.getBoundingBox(start)
            val last = layout.getBoundingBox(end - 1)
            val left = minOf(first.left, last.left)
            val right = maxOf(first.right, last.right)
            val width = (right - left) * fraction
            val top = layout.getLineTop(row)
            val bottom = layout.getLineBottom(row)
            if (layout.getBidiRunDirection(start) == ResolvedTextDirection.Rtl) addRect(Rect(right - width, top, right, bottom))
            else addRect(Rect(left, top, left + width, bottom))
        } else {
            // A word broken over two rows lights up letter by letter.
            for (i in start until end) if ((i - start + 1f) / (end - start) <= fraction) addRect(layout.getBoundingBox(i))
        }
    }
}

private const val RestingEmphasis = 0.72f
private val LyricGlide = tween<Float>(520, easing = FastOutSlowInEasing)
private val LyricRise = tween<Float>(160)
private val LyricSettle = tween<Float>(260)
