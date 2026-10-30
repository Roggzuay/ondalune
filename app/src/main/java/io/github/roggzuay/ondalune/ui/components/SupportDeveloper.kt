package io.github.roggzuay.ondalune.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

const val SupportUrl = "https://ko-fi.com/roggzuay"

/** Settings card that opens a thank-you note with a link to the developer's Ko-fi page. */
@Composable
fun SupportDeveloperCard(modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    SpringPressableSurface(
        onClick = { open = true },
        color = MaterialTheme.colorScheme.tertiaryContainer,
        cornerRadius = 22.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("support_developer_button")
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiary),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.LocalCafe, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiary)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Support Ondalune", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                Text(
                    "Buy the developer a coffee on Ko-fi",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                )
            }
            Icon(Icons.Filled.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
        }
    }
    if (open) SupportDeveloperDialog(onDismiss = { open = false })
}

@Composable
private fun SupportDeveloperDialog(onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val beat = rememberInfiniteTransition(label = "supportHeart")
    val pulse by beat.animateFloat(
        initialValue = 1f,
        targetValue = 1.14f,
        animationSpec = infiniteRepeatable(tween(520), RepeatMode.Reverse),
        label = "supportHeartPulse"
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Filled.Favorite,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier
                    .size(40.dp)
                    .graphicsLayer { scaleX = pulse; scaleY = pulse }
            )
        },
        title = { Text("Thank you for listening", textAlign = TextAlign.Center) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Hi, I'm Rogg. Ondalune is built by one person, in spare evenings, with a lot of love " +
                        "and a fair amount of coffee."
                )
                Text(
                    "It has no ads and no tracking, and it never will. If it has made your music feel a " +
                        "little more like yours, a coffee on Ko-fi keeps the updates coming."
                )
                Text(
                    "Whether you chip in or not, I'm really glad you're here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    runCatching { uriHandler.openUri(SupportUrl) }
                    onDismiss()
                },
                modifier = Modifier.testTag("support_open_kofi")
            ) {
                Icon(Icons.Filled.LocalCafe, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Buy me a coffee")
                Spacer(Modifier.width(6.dp))
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Opens ko-fi.com", modifier = Modifier.size(16.dp))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Maybe later") } }
    )
}
