package io.github.roggzuay.ondalune.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.core.os.ConfigurationCompat
import io.github.roggzuay.ondalune.data.model.LibraryTotals
import io.github.roggzuay.ondalune.data.model.ListeningStats
import io.github.roggzuay.ondalune.data.model.PlayHistoryLimit
import io.github.roggzuay.ondalune.data.model.StatsRow
import io.github.roggzuay.ondalune.data.model.StatsWindow
import io.github.roggzuay.ondalune.data.model.humanDuration
import io.github.roggzuay.ondalune.ui.components.ExpressiveArtwork
import io.github.roggzuay.ondalune.ui.components.ExpressiveBounceIconButton
import io.github.roggzuay.ondalune.ui.theme.cornerShape
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Your year in Ondalune: what you played, for how long, and when.
 *
 * All of it is worked out on this phone from the plays Ondalune wrote down itself. Nothing is
 * sent anywhere and no account is involved, which is the whole reason it can exist here at all.
 */
@Composable
fun ListeningStatsSheet(
    stats: ListeningStats,
    totals: LibraryTotals,
    window: StatsWindow,
    /** When Ondalune began writing plays down on this phone; 0 when it has not yet. */
    countingSince: Long,
    onSelectWindow: (StatsWindow) -> Unit,
    onClose: () -> Unit
) {
    BackHandler(onBack = onClose)

    SwipeDownDismissModalContainer(
        onClose = onClose,
        testTag = "listening_stats_sheet",
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Insights, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Your listening", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        text = "Worked out on this phone. Nothing is sent anywhere.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            ExpressiveBounceIconButton(
                onClick = onClose,
                size = 48.dp,
                modifier = Modifier.testTag("close_listening_stats")
            ) { Icon(Icons.Filled.Close, contentDescription = "Close") }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(StatsWindow.entries) { choice ->
                        FilterChip(
                            selected = choice == window,
                            onClick = { onSelectWindow(choice) },
                            label = { Text(choice.label) },
                            modifier = Modifier.testTag("stats_window_${choice.name}")
                        )
                    }
                }
            }

            if (stats.isEmpty) {
                item {
                    Text(
                        text = "Nothing here yet. Play something and come back.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                item { HeadlineNumbers(stats) }
                item { WhenYouListen(stats) }
                if (stats.topSongs.isNotEmpty()) item { TopList("Songs", stats.topSongs, "top_songs") }
                if (stats.topArtists.isNotEmpty()) item { TopList("Artists", stats.topArtists, "top_artists") }
                if (stats.topAlbums.isNotEmpty()) item { TopList("Albums", stats.topAlbums, "top_albums") }
            }

            item { LibraryCard(totals) }

            // The one thing a page like this usually gets wrong: pretending it knows more than it does.
            item {
                // Read through the configuration, so the date follows the phone's language.
                val locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.ROOT
                Text(
                    text = buildString {
                        if (countingSince > 0) {
                            append("These numbers go back to ")
                            append(SimpleDateFormat("d MMMM yyyy", locale).format(Date(countingSince)))
                            append(". ")
                        } else {
                            append("Ondalune only started writing plays down in this version, so ")
                            append("these numbers begin now. ")
                        }
                        if (stats.reachedTheCap) {
                            append("Ondalune keeps the last $PlayHistoryLimit plays and no more, ")
                            append("and that is as far as this goes; the date moves forward as ")
                            append("older plays are let go. ")
                        }
                        append("The play counts below go further back, because your server and ")
                        append("Ondalune were both counting before any of this started.")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 24.dp)
                )
            }
        }
    }
}

@Composable
private fun HeadlineNumbers(stats: ListeningStats) {
    Surface(
        shape = cornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().testTag("stats_headline")
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                text = stats.listeningTime,
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "of music, across ${plural(stats.plays, "play")}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Figure(stats.songs, "songs")
                Figure(stats.artists, "artists")
                Figure(stats.albums, "albums")
            }
            if (stats.adjustedPlays > 0) Text(
                text = "${stats.adjustedShare}% of it played with the speed, pitch or reverb changed",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary
            )
        }
    }
}

@Composable
private fun Figure(value: Int, noun: String) {
    Column {
        Text(value.toString(), style = MaterialTheme.typography.headlineSmall)
        Text(noun, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** When the music is on: a plain bar for each hour, and the day it happens most. */
@Composable
private fun WhenYouListen(stats: ListeningStats) {
    val peak = (stats.byHour.maxOrNull() ?: 0).coerceAtLeast(1)
    Surface(
        shape = cornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().testTag("stats_when")
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("When you listen", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth().height(70.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                stats.byHour.forEachIndexed { hour, count ->
                    val share = count.toFloat() / peak
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height((6 + share * 60).dp)
                            .clip(cornerShape(3.dp))
                            .background(
                                if (count == 0) MaterialTheme.colorScheme.surfaceVariant
                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f + share * 0.65f)
                            )
                            .testTag("stats_hour_$hour")
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("00", "06", "12", "18", "23").forEach {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val busiest = listOfNotNull(
                stats.busiestHour?.let { "Most often around $it" },
                stats.busiestDay?.let { "and more on a $it than any other day" }
            ).joinToString(" ")
            if (busiest.isNotEmpty()) Text(
                text = busiest,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TopList(title: String, rows: List<StatsRow>, tag: String) {
    Surface(
        shape = cornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().testTag(tag)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            rows.forEachIndexed { index, row ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(22.dp)
                    )
                    ExpressiveArtwork(
                        seedHex = row.seedHex,
                        accentHex = row.accentHex,
                        artStyleIndex = row.artStyleIndex,
                        coverArtUrl = row.coverArtUrl,
                        modifier = Modifier.size(42.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = row.title,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = row.subtitle,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = plural(row.plays, "play"),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = humanDuration(row.seconds),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryCard(totals: LibraryTotals) {
    Surface(
        shape = cornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().testTag("stats_library")
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Your library", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "${totals.librarySongs} songs, ${totals.libraryLength} of music",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "${plural(totals.countedPlays, "play")} counted across " +
                    "${totals.songsEverPlayed} songs, and ${plural(totals.favourites, "favourite")}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun plural(n: Int, noun: String) = if (n == 1) "1 $noun" else "$n ${noun}s"
