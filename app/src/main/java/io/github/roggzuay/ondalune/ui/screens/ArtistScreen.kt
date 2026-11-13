package io.github.roggzuay.ondalune.ui.screens

import io.github.roggzuay.ondalune.data.model.isOnDevice
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.Role
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.roggzuay.ondalune.data.model.ArtworkShapeMode
import io.github.roggzuay.ondalune.data.model.SubsonicAlbum
import io.github.roggzuay.ondalune.data.model.SubsonicArtist
import io.github.roggzuay.ondalune.data.model.SubsonicTrack
import io.github.roggzuay.ondalune.ui.components.ArtworkImage
import io.github.roggzuay.ondalune.ui.components.DownloadButton
import io.github.roggzuay.ondalune.ui.components.ExpressiveArtwork
import io.github.roggzuay.ondalune.ui.components.LocalDownloads
import io.github.roggzuay.ondalune.ui.components.LocalTrackActions
import io.github.roggzuay.ondalune.ui.components.SelectableTracks
import io.github.roggzuay.ondalune.ui.components.downloadStatusOf
import io.github.roggzuay.ondalune.ui.theme.ArtShape
import io.github.roggzuay.ondalune.ui.theme.ArtworkPalette
import io.github.roggzuay.ondalune.ui.theme.ArtworkPaletteCache
import io.github.roggzuay.ondalune.ui.theme.LocalReducedMotion

/** What the artist page needs beyond its arguments; provided by the shell. */
class ArtistExtras(
    val favourites: Set<String> = emptySet(),
    /** Similar artists the server named, by opened artist id. */
    val similar: Map<String, List<String>> = emptyMap(),
    val allArtists: List<SubsonicArtist> = emptyList(),
    val onToggleFavourite: (SubsonicArtist) -> Unit = {},
    val onOpenArtist: (String) -> Unit = {},
    /**
     * The page tells the shell while it is shown (with its portrait, if any): the app takes the
     * portrait's colours and hides its top bar, so the photo runs to the top of the screen.
     */
    val onShowPortrait: (artistId: String, url: String?) -> Unit = { _, _ -> },
    val onHidePortrait: (artistId: String) -> Unit = {}
)

val LocalArtistExtras = compositionLocalOf { ArtistExtras() }

/** Popular songs shown before "Show all". */
private const val PopularPreview = 5

/** Short releases: up to three songs, or up to six under half an hour (singles and EPs). */
private fun SubsonicAlbum.isShortRelease() = songCount <= 3 || (songCount <= 6 && durationSec in 1 until 30 * 60)

/**
 * An artist's page: a full-width portrait that fades into a background taken from the portrait's
 * own colours, the name over it, then play, shuffle, favourite, download and share, the latest
 * release, popular songs, albums, singles and EPs, artists the server calls similar, and the bio.
 * Wide screens put the portrait and actions beside the lists.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ArtistDetailSubScreen(
    artist: SubsonicArtist,
    tracks: List<SubsonicTrack>,
    albums: List<SubsonicAlbum>,
    currentTrackId: String?,
    isPlaying: Boolean,
    onBack: () -> Unit,
    onPlayTrack: (SubsonicTrack) -> Unit,
    onShuffle: () -> Unit,
    onToggleStar: (SubsonicTrack) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onPlayAlbum: (String) -> Unit
) {
    val popular = remember(tracks) { tracks.sortedWith(compareByDescending<SubsonicTrack> { it.playCount }.thenByDescending { it.starred }) }
    SelectableTracks(popular)
    val byNewest = remember(albums) { albums.sortedWith(compareByDescending<SubsonicAlbum> { it.year }.thenBy { it.name }) }
    val fullAlbums = remember(byNewest) { byNewest.filterNot { it.isShortRelease() } }
    val shortReleases = remember(byNewest) { byNewest.filter { it.isShortRelease() } }
    val extras = LocalArtistExtras.current
    // While this page is on screen the app's colours come from the portrait (the shell decides,
    // so the player and other pages keep the song's colours).
    DisposableEffect(artist.id, artist.coverArtUrl) {
        extras.onShowPortrait(artist.id, artist.coverArtUrl.ifBlank { null })
        onDispose { extras.onHidePortrait(artist.id) }
    }
    val related = remember(extras.similar, extras.allArtists, artist.id) {
        val ids = extras.similar[artist.id].orEmpty()
        val byId = extras.allArtists.associateBy { it.id }
        ids.mapNotNull { byId[it] }.filter { it.id != artist.id }
    }

    // The page takes its colour from the bottom of the portrait (where the name sits), blended
    // into the theme's surface so the usual text colours keep their contrast. No portrait: theme.
    val context = LocalContext.current
    val surface = MaterialTheme.colorScheme.surface
    val palette by produceState<ArtworkPalette?>(ArtworkPaletteCache.peek(artist.coverArtUrl), artist.coverArtUrl) {
        if (artist.coverArtUrl.isNotBlank()) value = ArtworkPaletteCache.load(context, artist.coverArtUrl)
    }
    val portraitColour = palette?.let { p -> if (p.regions.size >= 4) lerp(p.regions[2], p.regions[3], 0.5f) else p.seed }
    val pageColour by animateColorAsState(portraitColour?.let { lerp(surface, it, 0.35f) } ?: surface, label = "artistPage")

    val header: @Composable (portraitHeight: Dp, parallax: () -> Float) -> Unit = { portraitHeight, parallax ->
        ArtistPortrait(artist, pageColour, portraitHeight, parallax, onBack)
        ArtistActions(artist, tracks, popular, onPlayTrack, onShuffle)
    }
    val sections: LazyListScope.() -> Unit = {
        artistSections(
            artist, popular, byNewest.firstOrNull(), fullAlbums, shortReleases, related,
            currentTrackId, isPlaying, onPlayTrack, onToggleStar, onOpenAlbum, onPlayAlbum, extras.onOpenArtist
        )
        item { Spacer(Modifier.height(150.dp)) }
    }

    // No app bar here: the portrait runs up under the status bar, which its height includes.
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    BoxWithConstraints(Modifier.fillMaxSize().testTag("artist_page")) {
        val wideHeader = minOf(maxWidth * 0.42f, maxHeight * 0.62f) + statusTop
        if (maxWidth >= 840.dp) {
            // Tablets and unfolded foldables: portrait and actions on the left, the lists beside them.
            Row(Modifier.fillMaxSize().background(pageColour)) {
                Column(
                    Modifier
                        .weight(0.42f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                ) {
                    header(wideHeader) { 0f }
                }
                LazyColumn(
                    modifier = Modifier.weight(0.58f).fillMaxHeight(),
                    contentPadding = PaddingValues(top = 16.dp + statusTop),
                    content = sections
                )
            }
        } else {
            val listState = rememberLazyListState()
            val reduced = LocalReducedMotion.current
            val portraitHeight = minOf(maxWidth, maxHeight * 0.6f) + statusTop
            val scrolled = { if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset.toFloat() else 0f }
            // One colour behind the whole page, the one the portrait fades into: it never shifts
            // as you scroll (a scroll-linked gradient drifted, and jumped once the portrait left).
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .background(pageColour)
            ) {
                item(key = "artist_header") {
                    Column { header(portraitHeight) { if (reduced) 0f else scrolled() * 0.45f } }
                }
                sections()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ArtistPortrait(
    artist: SubsonicArtist,
    pageColour: Color,
    height: Dp,
    parallax: () -> Float,
    onBack: () -> Unit
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .clip(androidx.compose.ui.graphics.RectangleShape)
    ) {
        val haze = rememberHazeState()
        Box(Modifier.fillMaxSize().hazeSource(haze).graphicsLayer { translationY = parallax() }) {
            if (artist.coverArtUrl.isNotBlank()) {
                ArtworkImage(
                    url = artist.coverArtUrl,
                    contentDescription = "${artist.name} portrait",
                    modifier = Modifier.fillMaxSize(),
                    crossfadeMs = 250
                )
            } else {
                ExpressiveArtwork(
                    seedHex = artist.paletteSeedHex,
                    accentHex = artist.accentHex,
                    artStyleIndex = artist.artStyleIndex,
                    monogramText = artist.name,
                    shapeMode = ArtworkShapeMode.SQUIRCLE,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        // The portrait melts into the page colour where the name sits, so the name stays readable
        // on any photo and the page continues below without an edge.
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0.45f to Color.Transparent, 0.82f to pageColour.copy(alpha = 0.85f), 1f to pageColour))
        )
        // A round, frosted back button: the photo's colours show through it.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.TopStart)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(12.dp)
                .size(48.dp)
                .clip(CircleShape)
                .then(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.hazeEffect(
                        haze,
                        HazeStyle(backgroundColor = Color.Black, tint = HazeTint(Color.Black.copy(alpha = 0.18f)), blurRadius = 20.dp, noiseFactor = 0f)
                    ) else Modifier.background(Color.Black.copy(alpha = 0.35f))
                )
                .clickable(onClickLabel = "Back", role = Role.Button, onClick = onBack)
                .testTag("artist_back")
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
        }
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(
                text = artist.name,
                style = MaterialTheme.typography.displaySmallEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = listOfNotNull(
                    artist.genre.takeIf { it.isNotBlank() },
                    "${artist.albumCount} ${if (artist.albumCount == 1) "album" else "albums"}",
                    "${artist.trackCount} songs"
                ).joinToString(" • "),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ArtistActions(
    artist: SubsonicArtist,
    tracks: List<SubsonicTrack>,
    popular: List<SubsonicTrack>,
    onPlayTrack: (SubsonicTrack) -> Unit,
    onShuffle: () -> Unit
) {
    val extras = LocalArtistExtras.current
    val actions = LocalTrackActions.current
    val downloads = LocalDownloads.current
    val context = LocalContext.current
    val favourite = artist.id in extras.favourites
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Button(
            onClick = { popular.firstOrNull()?.let(onPlayTrack) },
            enabled = popular.isNotEmpty(),
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier.height(52.dp).testTag("artist_play_button")
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Play", style = MaterialTheme.typography.titleMediumEmphasized)
        }
        FilledTonalIconButton(
            onClick = onShuffle,
            enabled = tracks.isNotEmpty(),
            shapes = IconButtonDefaults.shapes(),
            modifier = Modifier.size(52.dp)
        ) { Icon(Icons.Filled.Shuffle, contentDescription = "Shuffle ${artist.name}") }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = { extras.onToggleFavourite(artist) }, modifier = Modifier.testTag("artist_favourite")) {
            Icon(
                if (favourite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = if (favourite) "Remove ${artist.name} from favourites" else "Add ${artist.name} to favourites",
                tint = if (favourite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // Songs already on the phone (its own music) have nothing to download.
        val remoteTracks = remember(tracks) { tracks.filterNot { it.isOnDevice } }
        if (actions != null && remoteTracks.isNotEmpty()) {
            val status = remember(remoteTracks, downloads) { downloadStatusOf(remoteTracks.map { downloads[it.id] }) }
            DownloadButton(
                status = status,
                onDownload = { actions.download(remoteTracks) },
                onCancel = { actions.cancelDownloads(remoteTracks) },
                onResume = { actions.resumeDownloads(remoteTracks) },
                onRemove = { actions.removeDownloads(remoteTracks) }
            ) { onClick, content ->
                IconButton(onClick = onClick, modifier = Modifier.testTag("artist_download")) { content() }
            }
        }
        IconButton(onClick = {
            // Only names: never a server address or anything private.
            val songs = popular.take(3).joinToString(", ") { it.title }
            val text = if (songs.isBlank()) artist.name else "${artist.name}: $songs"
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null))
        }) { Icon(Icons.Filled.Share, contentDescription = "Share ${artist.name}") }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun LazyListScope.artistSections(
    artist: SubsonicArtist,
    popular: List<SubsonicTrack>,
    latest: SubsonicAlbum?,
    fullAlbums: List<SubsonicAlbum>,
    shortReleases: List<SubsonicAlbum>,
    related: List<SubsonicArtist>,
    currentTrackId: String?,
    isPlaying: Boolean,
    onPlayTrack: (SubsonicTrack) -> Unit,
    onToggleStar: (SubsonicTrack) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onPlayAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit
) {
    if (latest != null) {
        item(key = "latest") {
            SectionTitle("Latest release")
            Box(Modifier.padding(horizontal = 16.dp)) {
                FeaturedAlbumHeroCard(
                    album = latest,
                    featured = false,
                    onOpenAlbum = { onOpenAlbum(latest.id) },
                    onPlayAlbum = { onPlayAlbum(latest.id) },
                    onShuffleAlbum = { onPlayAlbum(latest.id) }
                )
            }
        }
    }
    if (popular.isNotEmpty()) {
        item(key = "popular_title") { SectionTitle("Popular") }
        item(key = "popular") {
            var showAll by rememberSaveable(artist.id) { mutableStateOf(false) }
            Column(
                Modifier.padding(horizontal = 16.dp).animateContentSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Keyed: play counts re-sort this list, and each row's state must move with its track.
                (if (showAll) popular else popular.take(PopularPreview)).forEach { track ->
                    key(track.id) {
                        TrackExpressiveRow(
                            track = track,
                            isCurrent = track.id == currentTrackId,
                            isPlaying = isPlaying,
                            onPlay = { onPlayTrack(track) },
                            onToggleStar = { onToggleStar(track) }
                        )
                    }
                }
                if (popular.size > PopularPreview) {
                    TextButton(onClick = { showAll = !showAll }) {
                        Text(if (showAll) "Show less" else "Show all ${popular.size} songs")
                    }
                }
            }
        }
    }
    albumRow("Albums", "albums", fullAlbums, onOpenAlbum, onPlayAlbum)
    albumRow("Singles & EPs", "singles", shortReleases, onOpenAlbum, onPlayAlbum)
    if (related.isNotEmpty()) {
        item(key = "related") {
            SectionTitle("Similar artists")
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(related, key = { it.id }) { other ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(104.dp)
                            .clip(MaterialTheme.shapes.large)
                            .clickable { onOpenArtist(other.id) }
                    ) {
                        ExpressiveArtwork(
                            seedHex = other.paletteSeedHex,
                            accentHex = other.accentHex,
                            artStyleIndex = other.artStyleIndex,
                            coverArtUrl = other.coverArtUrl,
                            monogramText = other.name,
                            styleShape = ArtShape.CIRCLE,
                            modifier = Modifier.size(96.dp)
                        )
                        Text(
                            other.name,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }
        }
    }
    if (artist.bio.isNotBlank()) {
        item(key = "about") {
            var expanded by rememberSaveable(artist.id) { mutableStateOf(false) }
            var overflows by remember(artist.id, artist.bio) { mutableStateOf(false) }
            SectionTitle("About")
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec())
            ) {
                Column(
                    Modifier.clickable { expanded = !expanded }.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = artist.bio,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (expanded) Int.MAX_VALUE else 4,
                        overflow = TextOverflow.Ellipsis,
                        onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow }
                    )
                    if (overflows || expanded) {
                        Text(
                            text = if (expanded) "Show less" else "Read more",
                            style = MaterialTheme.typography.labelLargeEmphasized,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

private fun LazyListScope.albumRow(
    title: String,
    key: String,
    albums: List<SubsonicAlbum>,
    onOpenAlbum: (String) -> Unit,
    onPlayAlbum: (String) -> Unit
) {
    if (albums.isEmpty()) return
    item(key = key) {
        SectionTitle(title)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(albums, key = { it.id }) { album ->
                AlbumExpressiveCard(album = album, onClick = { onOpenAlbum(album.id) }, onPlayClick = { onPlayAlbum(album.id) })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmallEmphasized,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 10.dp)
    )
}
