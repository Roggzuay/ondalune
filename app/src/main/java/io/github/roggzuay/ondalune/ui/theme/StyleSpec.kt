package io.github.roggzuay.ondalune.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import io.github.roggzuay.ondalune.data.model.AppFont
import io.github.roggzuay.ondalune.data.model.HomeSection
import com.materialkolor.PaletteStyle

/**
 * Container behind the playing track's row. FIXED is the same vivid tone in light and dark mode,
 * so the row reads as a lit tile; SOFT is a translucent wash with only the title and bars in the accent.
 */
enum class NowPlayingRow { FIXED, TONAL, TERTIARY, SOLID, SOFT }

/** Fill, text colour and accent (title, bars, heart) of the playing row. */
data class NowPlayingColors(val container: Color, val content: Color, val accent: Color)

fun NowPlayingRow.colors(scheme: ColorScheme): NowPlayingColors = when (this) {
    NowPlayingRow.FIXED -> scheme.onPrimaryFixedVariant.let { NowPlayingColors(scheme.primaryFixed, it, it) }
    NowPlayingRow.TONAL -> scheme.onPrimaryContainer.let { NowPlayingColors(scheme.primaryContainer, it, it) }
    NowPlayingRow.TERTIARY -> scheme.onTertiaryContainer.let { NowPlayingColors(scheme.tertiaryContainer, it, it) }
    NowPlayingRow.SOLID -> scheme.onPrimary.let { NowPlayingColors(scheme.primary, it, it) }
    NowPlayingRow.SOFT -> NowPlayingColors(scheme.primaryContainer.copy(alpha = 0.45f), scheme.onSurface, scheme.primary)
}

/** Silhouette used to cut artwork. */
enum class ArtShape { SQUIRCLE, ROUNDED, SQUARE, CIRCLE, PILL, FLOWER, COOKIE, CLOVER, SUNNY, PUFFY, GEM, ARCH, MIXED }

/** The shapes MIXED rotates through, one per album. */
private val MixedShapes = listOf(
    ArtShape.FLOWER, ArtShape.COOKIE, ArtShape.CLOVER, ArtShape.SUNNY,
    ArtShape.PUFFY, ArtShape.CIRCLE, ArtShape.GEM, ArtShape.ARCH
)

/**
 * Everything the look sets beyond colour: typefaces, palette character, corner family, how
 * artwork is cut, and whether lists are cards or flat rows. Screens read [LocalStyleSpec].
 */
data class StyleSpec(
    val headerFont: AppFont,
    val bodyFont: AppFont,
    val paletteStyle: PaletteStyle,
    /** Multiplier on the app's hand-tuned corner radii. */
    val cornerFactor: Float,
    /** Stock Material 3 shape ladder instead of the larger expressive one. */
    val standardShapes: Boolean,
    /** Album and card artwork. */
    val artShape: ArtShape,
    /** Small artwork in track rows. */
    val rowArtShape: ArtShape,
    /** Album, artist and playlist list rows sit on tonal cards (false = flat rows on the page). */
    val cardRows: Boolean,
    /** How the playing track's row stands out; every other track row is flat on the page. */
    val nowPlayingRow: NowPlayingRow,
    /** Album cards sit on a tonal container (false = artwork and text straight on the page). */
    val cardTiles: Boolean,
    val artBorder: Boolean,
    /** Codec badges and readouts in monospace. */
    val monoBadges: Boolean,
    /** Small uppercase kicker above section titles ("FEATURED FOR YOU"). */
    val eyebrows: Boolean,
    /** True-black canvas in dark mode regardless of the AMOLED setting. */
    val pureBlack: Boolean,
    /** Shelves show a play pill beside the title. */
    val shelfPlayButtons: Boolean,
    val defaultHome: List<HomeSection>
)

/** Ondalune's one look: wide atomic type, squircle artwork, the vivid playing-row tile. */
val OndaluneStyle = StyleSpec(
    AppFont.UNBOUNDED, AppFont.FIGTREE, PaletteStyle.Vibrant, 1f, false,
    ArtShape.SQUIRCLE, ArtShape.SQUIRCLE, cardRows = false, nowPlayingRow = NowPlayingRow.FIXED,
    cardTiles = true, artBorder = true,
    monoBadges = true, eyebrows = false, pureBlack = false, shelfPlayButtons = false,
    defaultHome = listOf(
        HomeSection.YOUR_MIX, HomeSection.MIXES, HomeSection.JUMP_BACK_IN, HomeSection.RECENTLY_ADDED,
        HomeSection.FAVOURITE_TRACKS, HomeSection.MOST_PLAYED, HomeSection.TOP_ARTISTS,
        HomeSection.RECENTLY_PLAYED, HomeSection.REDISCOVER
    )
)

val LocalStyleSpec = staticCompositionLocalOf { OndaluneStyle }

private val SquircleShape = RoundedCornerShape(percent = 15)
private val RoundedArtShape = RoundedCornerShape(percent = 8)
private val PillShape = RoundedCornerShape(percent = 34)

/** Static clip shape for [shape]; [key] picks the variant when the shape is MIXED. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun artShape(shape: ArtShape, key: Int = 0): Shape = when (shape) {
    ArtShape.SQUIRCLE -> SquircleShape
    ArtShape.ROUNDED -> RoundedArtShape
    ArtShape.SQUARE -> RoundedCornerShape(percent = 3)
    ArtShape.CIRCLE -> CircleShape
    ArtShape.PILL -> PillShape
    ArtShape.MIXED -> artShape(MixedShapes[Math.floorMod(key, MixedShapes.size)])
    else -> polygonShapes.getOrPut(shape) {
        PolygonShape(
            when (shape) {
                ArtShape.FLOWER -> MaterialShapes.Flower
                ArtShape.COOKIE -> MaterialShapes.Cookie9Sided
                ArtShape.CLOVER -> MaterialShapes.Clover4Leaf
                ArtShape.SUNNY -> MaterialShapes.Sunny
                ArtShape.PUFFY -> MaterialShapes.Puffy
                ArtShape.GEM -> MaterialShapes.Gem
                else -> MaterialShapes.Arch
            }
        )
    }
}

private val polygonShapes = HashMap<ArtShape, Shape>()

/** Clips to a normalized (0..1) Material shape polygon, scaled to the layout size. */
class PolygonShape(private val polygon: RoundedPolygon) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = polygon.toPath(android.graphics.Path())
        path.transform(android.graphics.Matrix().apply { setScale(size.width, size.height) })
        return Outline.Generic(path.asComposePath())
    }
}
