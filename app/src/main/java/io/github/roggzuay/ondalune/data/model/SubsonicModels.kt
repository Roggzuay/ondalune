package io.github.roggzuay.ondalune.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlin.math.ln
import kotlin.math.pow

@Entity(tableName = "subsonic_servers")
data class SubsonicServerConfig(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val baseUrl: String,
    val username: String,
    val passwordOrToken: String,
    val useTokenAuth: Boolean = true,
    val transcodeFormat: String = "RAW", // RAW, OPUS, MP3, AAC
    val maxBitRate: Int = 0, // 0 = Unlimited Lossless, 320, 256, 192, 128
    val isDemoServer: Boolean = false,
    val isActive: Boolean = false,
    val serverSoftware: String = "OpenSubsonic 1.16.1",
    val extensionsCsv: String = "songLyrics,transcodeOffset,formPost,apiKeyAuthentication",
    val lastPingMs: Long = 0L
)

@Entity(tableName = "subsonic_tracks")
data class SubsonicTrack(
    @PrimaryKey val id: String,
    val serverId: Long,
    val title: String,
    val artist: String,
    val artistId: String,
    val album: String,
    val albumId: String,
    val durationSec: Int,
    val bitRate: Int,
    val suffix: String, // e.g. "FLAC", "OPUS", "MP3"
    val bitDepth: Int = 24,
    val samplingRate: Int = 96000,
    val starred: Boolean = false,
    val trackNumber: Int = 1,
    val year: Int = 2025,
    val genre: String = "Electronic",
    val folderPath: String = "/Music/Lossless/24bit-Masters",
    val coverArtUrl: String = "",
    val artistImageUrl: String = "",
    val artistBio: String = "",
    val artStyleIndex: Int = 0,
    val paletteSeedHex: String = "#FF6B5B",
    val accentHex: String = "#70F5FF",
    val bpm: Int = 118,
    val musicalKey: String = "F Minor",
    val syncedLyricsRaw: String = "",
    val playCount: Int = 0,
    val userRating: Int = 4, // 0 = Unrated, 1..5 = Star Rating (/rest/setRating.view)
    /** ReplayGain from the server (OpenSubsonic `replayGain`): gains in dB, peaks as linear 0..1+. */
    val rgTrackGain: Float? = null,
    val rgAlbumGain: Float? = null,
    val rgTrackPeak: Float? = null,
    val rgAlbumPeak: Float? = null
) {
    /**
     * Computes the personalized Smart Shuffle priority weight combining:
     * - User star rating (1..5★)
     * - Starred/Favourite flag
     * - Play frequency (logarithmic curve so heavy-rotation tracks lead without starving deep cuts)
     */
    fun smartShuffleWeight(profile: SmartShuffleProfile = SmartShuffleProfile.BALANCED): Double {
        val ratingNorm = (userRating.coerceIn(0, 5) / 5.0)
        val starBonus = if (starred) 1.0 else 0.0
        val playFreqNorm = (ln(1.0 + playCount.coerceAtLeast(0).toDouble()) / ln(80.0)).coerceIn(0.0, 1.35)

        val (ratingMult, starMult, freqMult) = when (profile) {
            SmartShuffleProfile.BALANCED -> Triple(3.8, 2.6, 3.4)
            SmartShuffleProfile.TOP_RATED -> Triple(5.5, 3.8, 1.6)
            SmartShuffleProfile.MOST_PLAYED -> Triple(2.0, 1.8, 5.6)
        }

        return (1.0 + (ratingNorm * ratingMult) + (starBonus * starMult) + (playFreqNorm * freqMult))
            .coerceAtLeast(0.5)
    }

    /**
     * Returns a 55%..99% personalized affinity match score for UI badges in Smart Shuffle mode.
     */
    fun smartAffinityPercent(profile: SmartShuffleProfile = SmartShuffleProfile.BALANCED): Int {
        val weight = smartShuffleWeight(profile)
        // Typical weight range is 1.0 .. 11.2
        val normalized = ((weight - 1.0) / 9.2).coerceIn(0.0, 1.0)
        return (58 + (normalized * 41.0).toInt()).coerceIn(58, 99)
    }
}

data class SubsonicAlbum(
    val id: String,
    val serverId: Long,
    val name: String,
    val artist: String,
    val artistId: String,
    val songCount: Int,
    val durationSec: Int,
    val year: Int,
    val genre: String,
    val starred: Boolean,
    val coverArtUrl: String = "",
    val artStyleIndex: Int = 0,
    val paletteSeedHex: String = "#FF6B5B",
    val accentHex: String = "#70F5FF"
)

data class SubsonicArtist(
    val id: String,
    val name: String,
    val albumCount: Int,
    val trackCount: Int,
    val genre: String,
    val bio: String,
    val coverArtUrl: String = "",
    val paletteSeedHex: String,
    val accentHex: String,
    val artStyleIndex: Int
)

data class SubsonicGenre(
    val name: String,
    val trackCount: Int,
    val albumCount: Int,
    val totalDurationSec: Int,
    val paletteSeedHex: String,
    val accentHex: String,
    val artStyleIndex: Int
)

data class SubsonicFolder(
    val path: String,
    val displayName: String,
    val category: String,
    val tracks: List<SubsonicTrack>,
    val paletteSeedHex: String,
    val accentHex: String
)

@Entity(tableName = "subsonic_playlists")
data class SubsonicPlaylist(
    @PrimaryKey val id: String,
    @Suppress("unused") val serverId: Long,
    val name: String,
    val comment: String,
    val trackIdsCsv: String,
    val paletteSeedHex: String = "#FF6B5B",
    val accentHex: String = "#70F5FF",
    val updatedAtMs: Long = System.currentTimeMillis()
) {
    val trackIds: List<String>
        get() = if (trackIdsCsv.isBlank()) emptyList() else trackIdsCsv.split(",").filter { it.isNotBlank() }
}

data class LyricLine(
    val timeMs: Long,
    val text: String
)

enum class AlbumListType(val apiValue: String, val label: String) {
    NEWEST("newest", "Newly Added"),
    FREQUENT("frequent", "Heavy Rotation"),
    RECENT("recent", "Recently Played"),
    STARRED("starred", "Starred Gems"),
    RANDOM("random", "Crate Digger")
}

enum class ArtworkShapeMode(val label: String) {
    SQUIRCLE("Expressive Squircle"),
    VINYL("Spinning Vinyl"),
    COOKIE("12-Sided Cookie")
}

enum class LibraryViewMode(val label: String, val shortLabel: String) {
    COMPACT_LIST("Compact rows: small art, one line of detail", "Compact"),
    LIST("Detailed rows: large art, full detail, quick play", "Rows"),
    GRID("Tiles: three across, artwork first", "Tiles"),
    LARGE_GRID("Cards: two across, big artwork and play button", "Cards")
}

enum class LibrarySortOption(val label: String, val shortLabel: String) {
    NAME_ASC("Name", "Name"),
    NAME_DESC("Name (Z–A)", "Z–A"),
    ARTIST("Artist", "Artist"),
    ALBUM("Album", "Album"),
    YEAR_NEWEST("Release Year", "Year"),
    RECENTLY_ADDED("Recently Added", "Added"),
    MOST_PLAYED("Play Count", "Plays"),
    TOP_RATED("Star Rating", "Rating"),
    DURATION("Duration", "Length"),
    TRACK_NUMBER("Track Number", "Track #"),
    TRACK_COUNT("Song Count", "Songs"),
    ALBUM_COUNT("Album Count", "Albums"),
    DATE_MODIFIED("Date Updated", "Updated")
}

/**
 * Independent per-category view mode, sort field, sort direction, and grid column count.
 * Keyed by category identifier ("ALBUMS", "ARTISTS", "GENRES", "FOLDERS", "SONGS", "PLAYLISTS", "FAVOURITES").
 */
data class TabDisplayPreference(
    val viewMode: LibraryViewMode = LibraryViewMode.GRID,
    val sortOption: LibrarySortOption = LibrarySortOption.NAME_ASC,
    val ascending: Boolean = true,
    val gridColumns: Int = 2
) {
    companion object {
        fun defaultForCategory(categoryKey: String): TabDisplayPreference = when (categoryKey.uppercase()) {
            "ALBUMS" -> TabDisplayPreference(
                viewMode = LibraryViewMode.GRID,
                sortOption = LibrarySortOption.YEAR_NEWEST,
                ascending = false,
                gridColumns = 2
            )
            "ARTISTS" -> TabDisplayPreference(
                viewMode = LibraryViewMode.GRID,
                sortOption = LibrarySortOption.NAME_ASC,
                ascending = true,
                gridColumns = 2
            )
            "GENRES" -> TabDisplayPreference(
                viewMode = LibraryViewMode.GRID,
                sortOption = LibrarySortOption.TRACK_COUNT,
                ascending = false,
                gridColumns = 2
            )
            "FOLDERS" -> TabDisplayPreference(
                viewMode = LibraryViewMode.LIST,
                sortOption = LibrarySortOption.NAME_ASC,
                ascending = true,
                gridColumns = 2
            )
            "SONGS" -> TabDisplayPreference(
                viewMode = LibraryViewMode.LIST,
                sortOption = LibrarySortOption.NAME_ASC,
                ascending = true,
                gridColumns = 2
            )
            "PLAYLISTS" -> TabDisplayPreference(
                viewMode = LibraryViewMode.LIST,
                sortOption = LibrarySortOption.DATE_MODIFIED,
                ascending = false,
                gridColumns = 2
            )
            "FAVOURITES" -> TabDisplayPreference(
                viewMode = LibraryViewMode.LIST,
                sortOption = LibrarySortOption.TOP_RATED,
                ascending = false,
                gridColumns = 2
            )
            else -> TabDisplayPreference()
        }

        fun supportedSortOptionsFor(categoryKey: String): List<LibrarySortOption> = when (categoryKey.uppercase()) {
            "ALBUMS" -> listOf(
                LibrarySortOption.NAME_ASC,
                LibrarySortOption.ARTIST,
                LibrarySortOption.YEAR_NEWEST,
                LibrarySortOption.TRACK_COUNT,
                LibrarySortOption.DURATION,
                LibrarySortOption.RECENTLY_ADDED
            )
            "ARTISTS" -> listOf(
                LibrarySortOption.NAME_ASC,
                LibrarySortOption.ALBUM_COUNT,
                LibrarySortOption.TRACK_COUNT
            )
            "GENRES" -> listOf(
                LibrarySortOption.NAME_ASC,
                LibrarySortOption.TRACK_COUNT,
                LibrarySortOption.ALBUM_COUNT,
                LibrarySortOption.DURATION
            )
            "FOLDERS" -> listOf(
                LibrarySortOption.NAME_ASC,
                LibrarySortOption.TRACK_COUNT
            )
            "SONGS", "FAVOURITES" -> listOf(
                LibrarySortOption.NAME_ASC,
                LibrarySortOption.ARTIST,
                LibrarySortOption.ALBUM,
                LibrarySortOption.DURATION,
                LibrarySortOption.TRACK_NUMBER,
                LibrarySortOption.YEAR_NEWEST,
                LibrarySortOption.MOST_PLAYED,
                LibrarySortOption.TOP_RATED
            )
            "PLAYLISTS" -> listOf(
                LibrarySortOption.NAME_ASC,
                LibrarySortOption.TRACK_COUNT,
                LibrarySortOption.DATE_MODIFIED
            )
            else -> listOf(
                LibrarySortOption.NAME_ASC,
                LibrarySortOption.YEAR_NEWEST,
                LibrarySortOption.TRACK_COUNT
            )
        }
    }
}

enum class ShuffleMode(val label: String, val shortLabel: String, val description: String) {
    OFF("Sequential Order", "Off", "Play tracks in sequential album or playlist order"),
    SMART("Smart Shuffle", "Smart", "Prioritizes high-rated (4–5★), starred, and frequently played tracks"),
    STANDARD("Standard Shuffle", "Random", "Uniform random order across all tracks in the queue")
}

enum class SmartShuffleProfile(val label: String, val description: String) {
    BALANCED("Balanced Affinity", "Blends 4–5★ ratings, starred favourites, and play frequency"),
    TOP_RATED("High-Rated & Starred", "Strongly prioritizes 4–5★ rated and starred tracks"),
    MOST_PLAYED("Heavy Rotation", "Strongly prioritizes your most frequently played tracks")
}

enum class DockStyle(val label: String, val description: String) {
    FLOATING("Floating", "Tonal capsule floating above the edge of the screen"),
    STATIC("Standard", "Full-width bar with no background, blending into the page")
}

enum class AppThemeMode(val label: String) {
    SYSTEM("Follow System"),
    DARK("Expressive Dark"),
    LIGHT("Expressive Light"),
    AMOLED("Pitch Black AMOLED")
}

enum class CuratedColorPalette(
    val label: String,
    val primarySeedHex: String,
    val accentSeedHex: String
) {
    DEFAULT_EXPRESSIVE("Ondalune Coral & Cyan", "#FF6B5B", "#70F5FF"),
    OCEAN_CYAN("Abyssal Cyan & Azure", "#00D2FF", "#7B61FF"),
    SUNSET_EMBER("Warm Analog Amber", "#FF8C42", "#FFD166"),
    EMERALD_STUDIO("Mastering Studio Emerald", "#10B981", "#38BDF8"),
    VIOLET_NEBULA("Midnight Nebula Violet", "#A855F7", "#EC4899"),
    MONOCHROME_SLATE("Monochrome Titanium", "#94A3B8", "#E2E8F0")
}

/** Overall look: Ondalune's expressive signature, or a quiet stock Material 3 look. */
enum class InterfaceStyle(val label: String, val description: String) {
    SIGNATURE("Signature", "Wide atomic type, morphing squircles, vivid artwork colour"),
    STANDARD("Standard", "System font, neutral surfaces, classic Material 3 shapes"),
    BLOOM("Bloom", "Rounded playful type, flower-cut artwork, tinted pill buttons"),
    POSTER("Poster", "Huge wide headlines, circles and pills, pure black canvas"),
    LOUNGE("Lounge", "Soft lavender cards, editorial labels, calm and roomy"),
    CLASSIC("Classic", "Dense flat lists, square art, quick actions up top")
}

/** A typeface the user can assign to headings or to body text. AUTO follows the theme. */
enum class AppFont(val label: String) {
    AUTO("Theme"),
    SYSTEM("System"),
    FIGTREE("Figtree"),
    UNBOUNDED("Unbounded"),
    FLEX_WIDE("Flex Wide"),
    FLEX("Roboto Flex"),
    BRICOLAGE("Bricolage"),
    SYNE("Syne"),
    FRAUNCES("Fraunces"),
    SPACE_GROTESK("Space Grotesk"),
    FREDOKA("Fredoka"),
    MONO("Mono")
}

/** How artwork is cut everywhere in the app. AUTO follows the theme; MIXED varies per album. */
enum class ArtworkCut(val label: String) {
    AUTO("Theme"),
    SQUIRCLE("Squircle"),
    ROUNDED("Rounded"),
    SQUARE("Square"),
    CIRCLE("Circle"),
    PILL("Pill"),
    FLOWER("Flower"),
    COOKIE("Cookie"),
    CLOVER("Clover"),
    SUNNY("Sunny"),
    PUFFY("Puffy"),
    GEM("Gem"),
    ARCH("Arch"),
    MIXED("Mixed")
}

/** Rows the Home screen can show, in the user's order. */
enum class HomeSection(val label: String, val description: String) {
    YOUR_MIX("Your Mix", "Animated artwork collage that plays a smart mix"),
    QUICK_ACTIONS("Quick actions", "Shortcuts to history, new music, top tracks and shuffle"),
    FEATURED("Featured album", "A large card for your newest album"),
    JUMP_BACK_IN("Jump back in", "Tracks you played recently"),
    RECENTLY_ADDED("Recently added", "Newest albums on the server"),
    FAVOURITE_TRACKS("Favourite tracks", "Songs you hearted"),
    MOST_PLAYED("Most played", "Your top tracks by play count"),
    TOP_ARTISTS("Top artists", "Artists you play the most"),
    RECENTLY_PLAYED("Recently played albums", "Albums the server saw you play"),
    REDISCOVER("Rediscover", "A daily pick of albums to revisit")
}

enum class CornerStylePreset(val label: String, val scaleMultiplier: Float) {
    EXPRESSIVE_ROUNDED("Expressive Rounded", 1.0f),
    PILL_ORGANIC("Soft Pill Capsule", 1.35f),
    SHARP_STUDIO("Crisp Studio", 0.55f)
}

enum class SeekBarStyle(val label: String, val description: String) {
    SQUIGGLY_WAVE("Animated Squiggly Waveform", "Expressive sinusoidal wave scrubber with live playback motion"),
    CLASSIC_LINEAR("Precision Linear Studio Bar", "Clean, zero-oscillation studio progress bar")
}

enum class WidgetLayoutMode(val label: String, val description: String) {
    AUTO_SMART("Smart Dynamic (Auto-Adapt)", "Adapts between compact and expanded controls based on widget dimensions"),
    COMPACT("Compact Transport Strip", "Streamlined artwork, track title, artist, and core transport buttons"),
    EXPANDED("Expanded Studio Deck", "Full artwork, elapsed/total time, progress bar, star action, and transport")
}

enum class WidgetThemeMode(val label: String) {
    DYNAMIC("Dynamic Palette"),
    DARK("Obsidian Dark"),
    LIGHT("Alabaster Light"),
    TRANSPARENT("Frosted Glass")
}

enum class ReplayGainMode(val label: String) {
    OFF("Off (play songs as mastered)"),
    TRACK("Track gain (every song equally loud)"),
    ALBUM("Album gain (keeps an album's dynamics)")
}

enum class DownloadSpeedLimit(val label: String, val mbpsValue: Int) {
    UNLIMITED("Unlimited (Max Line Speed)", 0),
    FAST_25("25 MB/s High-Speed", 25),
    BALANCED_10("10 MB/s Balanced", 10),
    SAVER_3("3 MB/s Background Saver", 3)
}

/**
 * Pitch & Speed DSP modes:
 * 1. TIME_STRETCH_PRESERVE_PITCH: Dynamic Pitch Correction ON — changing speed (0.8x..2.0x)
 *    dynamically time-stretches audio to preserve the original musical pitch (1.00x).
 * 2. VARISPEED_NATURAL_LINKED: Analog Vinyl / Tape deck mode — pitch scales proportionally with
 *    speed (e.g. 0.80x speed -> 0.80x pitch / -3.86 semitones) for zero-artifact resampling.
 * 3. INDEPENDENT_SEMITONES: Unlocks independent speed (0.8x..2.0x) and pitch/semitone tuning
 *    (-12 to +12 semitones or 432Hz concert pitch).
 */
enum class PitchCorrectionMode(val label: String, val description: String) {
    TIME_STRETCH_PRESERVE_PITCH(
        "Dynamic Pitch Correction (Preserve Key)",
        "Time-stretches audio from 0.8x to 2.0x while keeping vocals & instruments in their original pitch"
    ),
    VARISPEED_NATURAL_LINKED(
        "Analog Varispeed (Link Pitch to Speed)",
        "Studio tape/vinyl resampling — pitch shifts naturally with speed (0.8x–2.0x) with zero phase artifacts"
    ),
    INDEPENDENT_SEMITONES(
        "Independent Speed & Semitone Tuning",
        "Control playback speed (0.8x–2.0x) and musical pitch (-12 to +12 semitones) independently"
    )
}

data class SpeedPitchState(
    val speed: Float = 1.0f, // 0.80f .. 2.00f
    val pitchMode: PitchCorrectionMode = PitchCorrectionMode.TIME_STRETCH_PRESERVE_PITCH,
    val independentPitchRatio: Float = 1.0f // 0.50f .. 2.00f
) {
    /**
     * Effective pitch ratio sent to the Android audio engine based on the selected mode.
     */
    val effectivePitch: Float
        get() = when (pitchMode) {
            PitchCorrectionMode.TIME_STRETCH_PRESERVE_PITCH -> 1.0f
            PitchCorrectionMode.VARISPEED_NATURAL_LINKED -> speed.coerceIn(0.80f, 2.00f)
            PitchCorrectionMode.INDEPENDENT_SEMITONES -> independentPitchRatio.coerceIn(0.50f, 2.00f)
        }

    /**
     * Semitone offset corresponding to [effectivePitch] (`12 * log2(pitch)`).
     */
    val effectiveSemitones: Float
        get() = (12.0 * (ln(effectivePitch.toDouble()) / ln(2.0))).toFloat()

    companion object {
        fun semitonesToRatio(semitones: Float): Float {
            return 2.0.pow((semitones / 12f).toDouble()).toFloat().coerceIn(0.50f, 2.00f)
        }
    }
}

/**
 * Acoustic Studio Reverb Presets for the real-time Freeverb/Schroeder & EnvironmentalReverb DSP engine.
 */
enum class ReverbPreset(
    val label: String,
    val description: String,
    val defaultWetDryMix: Float,
    val defaultRoomSize: Float,
    val defaultDecay: Float,
    val defaultDamping: Float
) {
    OFF("Bypass (Dry)", "Unprocessed direct signal path with zero reverberation", 0.0f, 0.30f, 0.25f, 0.50f),
    SMALL_ROOM("Small Booth", "Tight acoustic vocal booth with fast early reflections", 0.18f, 0.32f, 0.28f, 0.65f),
    STUDIO_ROOM("Mastering Studio", "Balanced live control room with natural warmth and controlled tail", 0.28f, 0.52f, 0.46f, 0.52f),
    HALL("Symphony Hall", "Spacious concert hall with rich midrange bloom and smooth decay", 0.38f, 0.76f, 0.72f, 0.40f),
    LARGE_HALL("Cathedral Arena", "Expansive acoustic arena with deep, enveloping sustain", 0.48f, 0.90f, 0.86f, 0.32f),
    AMBIENT("Dreamscape Wash", "Lush, atmospheric shimmer tail for ambient and slowed/reverb listening", 0.60f, 0.95f, 0.92f, 0.24f),
    CUSTOM("Custom Acoustic", "User-sculpted room size, decay tail, HF damping, and wet/dry balance", 0.32f, 0.60f, 0.55f, 0.45f)
}

/**
 * Real-time Live Reverb DSP state that operates simultaneously with Playback Speed & Dynamic Pitch.
 */
data class ReverbState(
    val enabled: Boolean = false,
    val preset: ReverbPreset = ReverbPreset.OFF,
    val wetDryMix: Float = 0.0f,   // 0.0f (100% Dry) .. 1.0f (100% Wet)
    val roomSize: Float = 0.52f,   // 0.0f (Tiny Booth) .. 1.0f (Vast Cathedral)
    val decay: Float = 0.46f,      // 0.05f (Short Tail ~0.2s) .. 1.0f (Long Tail ~7.5s)
    val damping: Float = 0.50f     // 0.0f (Bright/Airy) .. 1.0f (Warm/Dark High-Frequency Roll-off)
) {
    val isActivelyProcessing: Boolean
        get() = enabled && preset != ReverbPreset.OFF && wetDryMix > 0.001f

    val badgeSummary: String
        get() = if (!isActivelyProcessing) {
            "Off"
        } else {
            "${preset.label} (${(wetDryMix * 100).toInt()}%)"
        }

    fun sanitized(): ReverbState = copy(
        wetDryMix = if (wetDryMix.isNaN()) 0f else wetDryMix.coerceIn(0f, 1f),
        roomSize = if (roomSize.isNaN()) 0.5f else roomSize.coerceIn(0f, 1f),
        decay = if (decay.isNaN()) 0.45f else decay.coerceIn(0.05f, 1f),
        damping = if (damping.isNaN()) 0.5f else damping.coerceIn(0f, 1f)
    )

    companion object {
        fun fromPreset(preset: ReverbPreset): ReverbState {
            return if (preset == ReverbPreset.OFF) {
                ReverbState(
                    enabled = false,
                    preset = ReverbPreset.OFF,
                    wetDryMix = 0f,
                    roomSize = preset.defaultRoomSize,
                    decay = preset.defaultDecay,
                    damping = preset.defaultDamping
                )
            } else {
                ReverbState(
                    enabled = true,
                    preset = preset,
                    wetDryMix = preset.defaultWetDryMix,
                    roomSize = preset.defaultRoomSize,
                    decay = preset.defaultDecay,
                    damping = preset.defaultDamping
                )
            }
        }
    }
}

/**
 * Sleep Timer state supporting either a countdown duration OR stopping at the end of the current track.
 */
data class SleepTimerState(
    val isActive: Boolean = false,
    val stopAtEndOfCurrentTrack: Boolean = false,
    val finishLastTrackOnExpiry: Boolean = true,
    val gentleFadeOut: Boolean = true,
    val totalDurationMinutes: Int = 30,
    val remainingMs: Long = 0L,
    val waitingForTrackEnd: Boolean = false
) {
    val statusBadgeText: String
        get() = when {
            !isActive -> "Off"
            stopAtEndOfCurrentTrack || waitingForTrackEnd -> "End of Track"
            else -> {
                val totalSec = (remainingMs / 1000L).coerceAtLeast(0L)
                val mins = totalSec / 60L
                val secs = totalSec % 60L
                "%d:%02d".format(mins, secs)
            }
        }
}

/**
 * Selectable background animation styles for the full-screen Now Playing studio.
 */
enum class PlayerBackgroundAnimationStyle(val label: String, val description: String) {
    ARTWORK_GLOW("Living Artwork", "The cover, blurred and drifting, with its own colours flowing around it"),
    APPLE_GLITTER_GRAIN("Velvet Mesh", "A flowing mesh of the cover's colours with a soft sheen and film grain"),
    LIQUID_AURORA("Aurora", "Curtains of the cover's colours rippling across the screen"),
    VINYL_HALO_PULSE("Halo", "A turning ring of cover colours with pulses radiating outward"),
    MINIMAL_TONAL("Still", "A calm, motionless gradient of the cover's colours")
}

/**
 * Adaptive icon mask shapes for the App Icon Settings Preview Gallery.
 */
enum class AppIconPreviewFrameShape(val label: String) {
    SQUIRCLE("Squircle"),
    CIRCLE_ROUND("Round"),
    ROUNDED_SQUARE("Square"),
    TEARDROP("Teardrop")
}

/**
 * Launcher icons: a crescent moon cradling a waveform. Moonlight is the shaded main icon; the
 * "Minimal" ones are flat two-tone tiles for light, grey and dark home screens.
 * Every variant ships a monochrome layer, so Android 13+ themed icons work with any of them.
 */
enum class AppIconVariant(
    val label: String,
    val shortLabel: String,
    val subtitle: String,
    val accentHex: String,
    val aliasClassName: String,
    val isMinimal: Boolean = false
) {
    // Launcher alias names are permanent: renaming one drops the icon from home screens on
    // update. Moonlight keeps 1.0.0's default alias name for that reason.
    MOONLIGHT("Moonlight", "Moonlight", "White moon and waveform on black", "#E8E8EC", "io.github.roggzuay.ondalune.LauncherAliasDusk"),
    PAPER("Paper", "Paper", "Black moon and waveform on white", "#0E0E10", "io.github.roggzuay.ondalune.LauncherAliasPaper", isMinimal = true),
    SLATE("Slate", "Slate", "Charcoal moon and waveform on grey", "#2A2B2F", "io.github.roggzuay.ondalune.LauncherAliasSlate", isMinimal = true),
    GRAPHITE("Graphite", "Graphite", "Grey moon and waveform on graphite", "#A3A4A9", "io.github.roggzuay.ondalune.LauncherAliasGraphite", isMinimal = true)
}

data class PlayerCustomizationSettings(
    val appIconVariant: AppIconVariant = AppIconVariant.MOONLIGHT,
    /** Re-pick the launcher icon whose colour is closest to the theme whenever the theme changes. */
    val iconFollowsTheme: Boolean = false,
    val dockStyle: DockStyle = DockStyle.FLOATING,
    val themeMode: AppThemeMode = AppThemeMode.DARK,
    val useMaterialYouDynamicColor: Boolean = true,
    val unifiedMaterialYouColor: Boolean = false,
    val harmonizePlayerWithArtwork: Boolean = true,
    val curatedPalette: CuratedColorPalette = CuratedColorPalette.DEFAULT_EXPRESSIVE,
    val cornerStylePreset: CornerStylePreset = CornerStylePreset.EXPRESSIVE_ROUNDED,
    val interfaceStyle: InterfaceStyle = InterfaceStyle.SIGNATURE,
    val headerFont: AppFont = AppFont.AUTO,
    val bodyFont: AppFont = AppFont.AUTO,
    val artworkCut: ArtworkCut = ArtworkCut.AUTO,
    /** Custom Home order (visible sections only); empty = follow the theme's default layout. */
    val homeSections: List<HomeSection> = emptyList(),
    val playerBackgroundStyle: PlayerBackgroundAnimationStyle = PlayerBackgroundAnimationStyle.ARTWORK_GLOW,
    val seekBarStyle: SeekBarStyle = SeekBarStyle.SQUIGGLY_WAVE,
    val showRemainingTime: Boolean = false,
    val showTechnicalFormatBadge: Boolean = true,
    val showPlayerStarButton: Boolean = true,
    val showCodecBadgesInLists: Boolean = true,
    val showStarRatingsInLists: Boolean = true,
    val fallbackArtistToAlbumArt: Boolean = false,
    val defaultGridColumns: Int = 2,
    val resumePlaybackOnLaunch: Boolean = true,
    val pauseOnHeadphoneDisconnect: Boolean = true,
    val widgetLayoutMode: WidgetLayoutMode = WidgetLayoutMode.AUTO_SMART,
    val widgetThemeMode: WidgetThemeMode = WidgetThemeMode.DYNAMIC,
    val widgetShowArtwork: Boolean = true,
    val widgetShowFavoriteButton: Boolean = true,
    /** Tall widgets show the current lyric lines between the track and the transport. */
    val widgetShowLyrics: Boolean = true,
    val crossfadeSeconds: Int = 4,
    val gaplessPlayback: Boolean = true,
    val replayGainMode: ReplayGainMode = ReplayGainMode.ALBUM,
    val autoScrobble: Boolean = true,
    val animatedSquigglyWave: Boolean = true,
    val hapticWobbleButtons: Boolean = true,
    val highRefreshRate120Hz: Boolean = true,
    val wifiTranscodeFormat: String = "RAW",
    val wifiMaxBitRate: Int = 0, // 0 = Lossless FLAC
    val mobileTranscodeFormat: String = "OPUS",
    val mobileMaxBitRate: Int = 192,
    val downloadSpeedLimit: DownloadSpeedLimit = DownloadSpeedLimit.UNLIMITED,
    val readAheadTrackBuffer: Int = 5,
    val smartShuffleProfile: SmartShuffleProfile = SmartShuffleProfile.BALANCED
)

enum class EqualizerPreset(
    val label: String,
    val description: String,
    val bandsDb: List<Float> // 60Hz, 230Hz, 910Hz, 3.6kHz, 14kHz (-10f..+10f)
) {
    FLAT_STUDIO("Bit-Perfect Reference", "Uncolored OpenSubsonic direct studio path", listOf(0f, 0f, 0f, 0f, 0f)),
    WARM_ANALOG("Warm Analog Vinyl", "Lush low-mids and silky high-frequency roll-off", listOf(3.5f, 4.2f, 1.0f, -1.2f, -2.0f)),
    DEEP_SUB("Sub-Bass Kinetic", "Punchy 60Hz sub-bass shelf with crisp air", listOf(6.8f, 4.0f, -1.0f, 2.5f, 4.5f)),
    VOCAL_PRISM("Vocal & Acoustic Prism", "Forward midrange presence and acoustic detail", listOf(-1.5f, 1.2f, 4.8f, 4.0f, 2.0f)),
    SPATIAL_CLUB("Late Night Widestage", "V-shaped dynamic contour for low-volume listening", listOf(5.0f, 2.0f, -2.0f, 3.5f, 6.0f))
}

data class PingDiagnosticResult(
    val success: Boolean,
    val statusMessage: String,
    val serverType: String = "",
    val apiVersion: String = "1.16.1",
    val openSubsonicExtensions: List<String> = emptyList(),
    val latencyMs: Long = 0L
)

/**
 * Honest lyrics availability state for OpenSubsonic tracks:
 * - [SYNCED]: Time-aligned LRC or OpenSubsonic `structuredLyrics` lines.
 * - [PLAIN_UNSYNCED]: Plain text lyrics from `/rest/getLyrics.view` or unsynced tags (no fake timestamps).
 * - [UNAVAILABLE]: No lyrics returned by server or embedded metadata.
 */
enum class LyricsAvailability(val label: String) {
    SYNCED("Time-Synced Lyrics"),
    PLAIN_UNSYNCED("Unsynced Lyrics"),
    UNAVAILABLE("No Lyrics Available")
}

data class TrackLyricsResult(
    val availability: LyricsAvailability = LyricsAvailability.UNAVAILABLE,
    val syncedLines: List<LyricLine> = emptyList(),
    val plainLyrics: String = "",
    val sourceLabel: String = ""
) {
    val lines: List<LyricLine>
        get() = syncedLines

    val plainTextLines: List<String>
        get() = plainLyrics
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
}

/**
 * Offline download lifecycle state for tracks and album batches.
 */
enum class OfflineDownloadState(val label: String) {
    NOT_DOWNLOADED("Not Downloaded"),
    QUEUED("Queued"),
    DOWNLOADING("Downloading"),
    COMPLETED("Downloaded Offline"),
    FAILED("Download Failed"),
    CANCELLED("Cancelled")
}

/**
 * Persistent Room entity tracking offline track downloads stored under `filesDir/ondalune_offline_downloads/`.
 */
@Entity(tableName = "subsonic_downloads")
data class SubsonicDownloadEntity(
    @PrimaryKey val downloadKey: String, // "${serverId}_${trackId}"
    val trackId: String,
    val serverId: Long,
    val albumId: String,
    val title: String,
    val artist: String,
    val album: String,
    val suffix: String,
    val localFilePath: String,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = 0L,
    val progressPercent: Int = 0,
    val status: String = OfflineDownloadState.QUEUED.name,
    val errorMessage: String = "",
    val updatedAtMs: Long = System.currentTimeMillis()
) {
    val stateEnum: OfflineDownloadState
        get() = OfflineDownloadState.entries.firstOrNull { it.name == status }
            ?: OfflineDownloadState.NOT_DOWNLOADED

    val downloadState: OfflineDownloadState
        get() = stateEnum

    val isCompleted: Boolean
        get() = stateEnum == OfflineDownloadState.COMPLETED && localFilePath.isNotBlank()
}

/**
 * Storage metrics distinguishing persistent offline downloads from temporary playback/artwork cache.
 */
data class OfflineStorageSummary(
    val downloadedTrackCount: Int = 0,
    val activeDownloadCount: Int = 0,
    val persistentAudioBytes: Long = 0L,
    val temporaryCacheBytes: Long = 0L
) {
    val completedTrackCount: Int
        get() = downloadedTrackCount

    val persistentFormattedMb: String
        get() = "%.1f MB".format(persistentAudioBytes / (1024.0 * 1024.0))

    val temporaryCacheFormattedMb: String
        get() = "%.1f MB".format(temporaryCacheBytes / (1024.0 * 1024.0))

    val formattedOfflineSize: String
        get() = persistentFormattedMb

    val formattedCacheSize: String
        get() = temporaryCacheFormattedMb
}

/**
 * Real-time DSP reverb processing path indicator reported by [io.github.roggzuay.ondalune.audio.OndaluneAudioEngine].
 */
enum class ReverbProcessingMode(val label: String, val detail: String) {
    CUSTOM_REALTIME_PCM_DSP(
        "Live Stereo PCM Freeverb DSP",
        "Real-time 64-bit internal Schroeder/Freeverb stereo PCM pipeline active on audio stream"
    ),
    BYPASSED_DRY(
        "Bit-Perfect Dry Path",
        "Direct uncompressed PCM output with zero reverberation coloration"
    ),
    HARDWARE_AUX_FALLBACK(
        "Platform Auxiliary Effect",
        "Android EnvironmentalReverb / PresetReverb auxiliary session fallback"
    ),
    UNSUPPORTED_STREAM_ERROR(
        "Reverb Unavailable",
        "Codec or output device rejected real-time DSP insertion"
    )
}

/** Individual genres from a tag such as "Electronic; Deep House" or "Hip-Hop/Rap". */
fun splitGenres(raw: String): List<String> = raw
    .split(';', ',', '/', '|')
    .map { it.trim().removePrefix("& ").trim() }
    .filter { it.isNotEmpty() }
    .distinctBy { it.lowercase() }

fun SubsonicTrack.genreList(): List<String> = splitGenres(genre)

fun SubsonicTrack.hasGenre(name: String): Boolean = genreList().any { it.equals(name, ignoreCase = true) }

/** Lower-cased genres plus their family ("deep house" also yields "house") for similarity scoring. */
fun SubsonicTrack.genreTokens(): Set<String> = genreList().flatMapTo(HashSet()) { g ->
    val lower = g.lowercase()
    listOfNotNull(lower, lower.substringAfterLast(' ').takeIf { ' ' in lower && it.length > 2 })
}
