package io.github.roggzuay.ondalune.ui.components

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import io.github.roggzuay.ondalune.data.model.SubsonicTrack
import io.github.roggzuay.ondalune.ui.theme.ArtworkPalette
import io.github.roggzuay.ondalune.ui.theme.cornerShape
import java.io.File
import java.io.FileOutputStream

/**
 * The card you share when a song is worth telling someone about: the cover, the song, and the
 * colours the cover gave the player, in the proportions a phone's share sheet wants.
 *
 * It is Ondalune's own look rather than a template, which is the point: the colours come from the
 * record, exactly as they do on the player it was taken from.
 */
@Composable
fun NowPlayingCard(
    track: SubsonicTrack,
    palette: ArtworkPalette,
    /** The line of the song that was playing, when the lyrics are up; blank otherwise. */
    lyricLine: String = "",
    modifier: Modifier = Modifier
) {
    val source = palette.regions.ifEmpty { palette.swatches }
    val top = source.getOrElse(0) { palette.seed }
    val bottom = source.getOrElse(3) { source.getOrElse(1) { palette.seed } }
    val dark = ((top.luminance() + bottom.luminance()) / 2f) < 0.5f
    val ink = if (dark) Color.White else Color.Black
    val quiet = ink.copy(alpha = 0.62f)

    Box(
        modifier = modifier
            .width(CardWidth)
            .background(Brush.verticalGradient(listOf(top, bottom)))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            ExpressiveArtwork(
                seedHex = track.paletteSeedHex,
                accentHex = track.accentHex,
                artStyleIndex = track.artStyleIndex,
                coverArtUrl = track.coverArtUrl,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(cornerShape(20.dp))
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.titleMedium,
                    color = quiet,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (track.album.isNotBlank()) Text(
                    text = track.album,
                    style = MaterialTheme.typography.bodyMedium,
                    color = quiet.copy(alpha = 0.78f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (lyricLine.isNotBlank()) Text(
                text = "“$lyricLine”",
                style = MaterialTheme.typography.titleMedium,
                color = ink.copy(alpha = 0.88f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(cornerShape(3.dp))
                        .background(ink.copy(alpha = 0.5f))
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Ondalune",
                    style = MaterialTheme.typography.labelLarge,
                    color = quiet,
                    fontSize = 13.sp
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "from my own library",
                    style = MaterialTheme.typography.labelMedium,
                    color = quiet.copy(alpha = 0.7f)
                )
            }
            Spacer(Modifier.height(2.dp))
        }
    }
}

/** The width the card is drawn at; the height follows the content. */
val CardWidth = 340.dp

/**
 * Writes a rendered card into the cache and hands back a content URI the share sheet can read.
 *
 * Cards live in `cache/shared`, which is cleared each time so the folder never becomes a quiet
 * collection of everything you have ever shared.
 */
fun writeShareableCard(context: Context, bitmap: Bitmap, name: String): android.net.Uri? {
    val folder = File(context.cacheDir, "shared")
    runCatching {
        folder.listFiles()?.forEach { it.delete() }
        folder.mkdirs()
    }
    val file = File(folder, "${safeName(name)}.png")
    val written = runCatching {
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }.isSuccess
    if (!written) return null
    return runCatching {
        FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
    }.getOrNull()
}

/**
 * A file name a file system will accept, whatever the song is called. A name with nothing
 * readable left in it falls back rather than becoming a row of underscores.
 */
internal fun safeName(raw: String): String {
    if (raw.none { it.isLetterOrDigit() }) return "ondalune"
    val cleaned = raw.map { if (it.isLetterOrDigit() || it == ' ' || it == '-') it else '_' }
        .joinToString("")
        .trim()
        .replace(Regex("\\s+"), "-")
        .take(48)
    return cleaned.ifEmpty { "ondalune" }
}
