package io.github.roggzuay.ondalune.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.github.roggzuay.ondalune.data.model.LibraryMix
import io.github.roggzuay.ondalune.ui.theme.LocalReducedMotion

private data class IntroPage(val title: String, val body: String)

/**
 * "A Home that learns", shown once when the library first has mixes: what the mixes are made of,
 * and what autoplay does (with its switch). It only describes what the app does today; song
 * analysis gets a page when it ships. Skip, Back and Continue; the bar shows how far along it is.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeIntro(
    mixes: List<LibraryMix>,
    autoplay: Boolean,
    onAutoplayChange: (Boolean) -> Unit,
    songAnalysis: Boolean,
    onSongAnalysisChange: (Boolean) -> Unit,
    onDone: () -> Unit
) {
    val pages = listOf(
        IntroPage("A Home that learns", "Mixes and autoplay follow how you actually listen."),
        IntroPage(
            "Mixes from your library",
            "Your favourite genres, the artist you play most, your hearted songs, new arrivals and old favourites you haven't heard lately. Made on this phone from your own library, and fresh every day."
        ),
        IntroPage(
            "Smarter autoplay",
            "When your queue ends, Ondalune keeps going with songs like the last ones, leaning on the ones you love and leaving out what you just heard. Your own queue always plays first."
        ),
        IntroPage(
            "Optional song analysis",
            "Let Ondalune listen to a short part of each song you've downloaded, on this phone, to tell calm songs from lively ones for mood mixes and autoplay. It stays off until you choose, nothing is uploaded, and turning it off deletes what it learned."
        )
    )
    var page by rememberSaveable { mutableIntStateOf(0) }
    val reduced = LocalReducedMotion.current
    val progress by animateFloatAsState((page + 1f) / pages.size, label = "introProgress")
    BackHandler { if (page > 0) page-- else onDone() }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxSize()
            // Swallow touches so nothing behind the intro reacts.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .testTag("home_intro")
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.weight(1f))
                TextButton(onClick = onDone, modifier = Modifier.heightIn(min = 48.dp).testTag("home_intro_skip")) { Text("Skip") }
            }
            AnimatedContent(
                targetState = page,
                transitionSpec = {
                    if (reduced) fadeIn() togetherWith fadeOut()
                    else {
                        val forward = targetState > initialState
                        (slideInHorizontally { if (forward) it / 3 else -it / 3 } + fadeIn())
                            .togetherWith(slideOutHorizontally { if (forward) -it / 3 else it / 3 } + fadeOut())
                    }
                },
                modifier = Modifier.weight(1f).fillMaxWidth(),
                label = "introPage"
            ) { index ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    IntroVisual(index, mixes)
                    Spacer(Modifier.height(32.dp))
                    Text(
                        pages[index].title,
                        style = MaterialTheme.typography.headlineLargeEmphasized,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        pages[index].body,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(max = 460.dp)
                    )
                    if (index >= 2) {
                        val (label, checked, onChange) = if (index == 2) Triple("Autoplay similar songs", autoplay, onAutoplayChange)
                        else Triple("Analyse downloaded songs", songAnalysis, onSongAnalysisChange)
                        Spacer(Modifier.height(24.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .widthIn(max = 460.dp)
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.large)
                                .clickable(role = Role.Switch) { onChange(!checked) }
                                .padding(horizontal = 20.dp, vertical = 12.dp)
                        ) {
                            Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Switch(checked = checked, onCheckedChange = onChange, modifier = Modifier.testTag("home_intro_switch_$index"))
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                if (page > 0) {
                    TextButton(onClick = { page-- }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Back") }
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = { if (page < pages.lastIndex) page++ else onDone() },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.heightIn(min = 52.dp).testTag("home_intro_continue")
                ) { Text(if (page < pages.lastIndex) "Continue" else "Done", style = MaterialTheme.typography.titleMediumEmphasized) }
            }
        }
    }
}

/** The user's own mixes, fanned out; a plain icon on the autoplay page or before any mix exists. */
@Composable
private fun IntroVisual(page: Int, mixes: List<LibraryMix>) {
    val shown = mixes.filter { it.covers.isNotEmpty() }.take(3)
    if (page >= 2 || shown.isEmpty()) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(160.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.extraLarge)
        ) {
            Icon(if (page == 3) Icons.Filled.GraphicEq else Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(72.dp))
        }
        return
    }
    Box(contentAlignment = Alignment.Center, modifier = Modifier.height(200.dp).fillMaxWidth()) {
        shown.forEachIndexed { i, mix ->
            val side = i - (shown.size - 1) / 2f
            MixCollage(
                mix.covers,
                Modifier
                    .zIndex(if (side == 0f) 1f else 0f)
                    .offset(x = (side * 92).dp, y = (kotlin.math.abs(side) * 14).dp)
                    .graphicsLayer { rotationZ = side * 8f }
                    .size(150.dp)
            )
        }
    }
}
