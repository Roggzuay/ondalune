package io.github.roggzuay.ondalune.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import io.github.roggzuay.ondalune.data.model.AppFont
import io.github.roggzuay.ondalune.data.model.HomeSection
import io.github.roggzuay.ondalune.data.model.InterfaceStyle
import com.materialkolor.PaletteStyle

/** Silhouette used to cut artwork. */
enum class ArtShape { SQUIRCLE, ROUNDED, SQUARE, CIRCLE, PILL, FLOWER, COOKIE, CLOVER, SUNNY, PUFFY, GEM, ARCH, MIXED }

/** The shapes MIXED rotates through, one per album. */
private val MixedShapes = listOf(
    ArtShape.FLOWER, ArtShape.COOKIE, ArtShape.CLOVER, ArtShape.SUNNY,
    ArtShape.PUFFY, ArtShape.CIRCLE, ArtShape.GEM, ArtShape.ARCH
)

/**
 * Everything a theme changes beyond colour: typefaces, palette character, corner family, how
 * artwork is cut, and whether lists are cards or flat rows. Screens read [LocalStyleSpec]
 * instead of switching on [InterfaceStyle] so each theme is a single table row.
 */
data class StyleSpec(
    val style: InterfaceStyle,
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
    /** Track rows sit on tonal cards (false = flat rows on the page). */
    val cardRows: Boolean,
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

val LocalStyleSpec = staticCompositionLocalOf { styleSpecFor(InterfaceStyle.SIGNATURE) }

fun styleSpecFor(style: InterfaceStyle): StyleSpec = when (style) {
    InterfaceStyle.SIGNATURE -> StyleSpec(
        style, AppFont.UNBOUNDED, AppFont.FIGTREE, PaletteStyle.Vibrant, 1f, false,
        ArtShape.SQUIRCLE, ArtShape.SQUIRCLE, cardRows = true, cardTiles = true, artBorder = true,
        monoBadges = true, eyebrows = false, pureBlack = false, shelfPlayButtons = false,
        defaultHome = listOf(
            HomeSection.YOUR_MIX, HomeSection.JUMP_BACK_IN, HomeSection.RECENTLY_ADDED,
            HomeSection.FAVOURITE_TRACKS, HomeSection.MOST_PLAYED, HomeSection.TOP_ARTISTS,
            HomeSection.RECENTLY_PLAYED, HomeSection.REDISCOVER
        )
    )
    InterfaceStyle.STANDARD -> StyleSpec(
        style, AppFont.SYSTEM, AppFont.SYSTEM, PaletteStyle.TonalSpot, 0.5f, true,
        ArtShape.ROUNDED, ArtShape.ROUNDED, cardRows = false, cardTiles = false, artBorder = false,
        monoBadges = false, eyebrows = false, pureBlack = false, shelfPlayButtons = false,
        defaultHome = listOf(
            HomeSection.QUICK_ACTIONS, HomeSection.JUMP_BACK_IN, HomeSection.RECENTLY_ADDED,
            HomeSection.MOST_PLAYED, HomeSection.FAVOURITE_TRACKS, HomeSection.RECENTLY_PLAYED,
            HomeSection.TOP_ARTISTS, HomeSection.REDISCOVER
        )
    )
    InterfaceStyle.BLOOM -> StyleSpec(
        style, AppFont.FREDOKA, AppFont.FIGTREE, PaletteStyle.Expressive, 1.35f, false,
        ArtShape.MIXED, ArtShape.MIXED, cardRows = false, cardTiles = true, artBorder = false,
        monoBadges = false, eyebrows = false, pureBlack = false, shelfPlayButtons = true,
        defaultHome = listOf(
            HomeSection.RECENTLY_ADDED, HomeSection.YOUR_MIX, HomeSection.JUMP_BACK_IN,
            HomeSection.FAVOURITE_TRACKS, HomeSection.TOP_ARTISTS, HomeSection.MOST_PLAYED,
            HomeSection.REDISCOVER
        )
    )
    InterfaceStyle.POSTER -> StyleSpec(
        style, AppFont.FLEX_WIDE, AppFont.FLEX, PaletteStyle.TonalSpot, 1.6f, false,
        ArtShape.PILL, ArtShape.CIRCLE, cardRows = false, cardTiles = false, artBorder = false,
        monoBadges = false, eyebrows = false, pureBlack = true, shelfPlayButtons = true,
        defaultHome = listOf(
            HomeSection.YOUR_MIX, HomeSection.MOST_PLAYED, HomeSection.RECENTLY_ADDED,
            HomeSection.TOP_ARTISTS, HomeSection.FAVOURITE_TRACKS, HomeSection.JUMP_BACK_IN,
            HomeSection.RECENTLY_PLAYED
        )
    )
    InterfaceStyle.LOUNGE -> StyleSpec(
        style, AppFont.BRICOLAGE, AppFont.FIGTREE, PaletteStyle.Rainbow, 1.15f, false,
        ArtShape.ROUNDED, ArtShape.ROUNDED, cardRows = true, cardTiles = false, artBorder = false,
        monoBadges = false, eyebrows = true, pureBlack = false, shelfPlayButtons = false,
        defaultHome = listOf(
            HomeSection.FEATURED, HomeSection.RECENTLY_PLAYED, HomeSection.JUMP_BACK_IN,
            HomeSection.FAVOURITE_TRACKS, HomeSection.RECENTLY_ADDED, HomeSection.MOST_PLAYED,
            HomeSection.REDISCOVER
        )
    )
    InterfaceStyle.CLASSIC -> StyleSpec(
        style, AppFont.SYSTEM, AppFont.SYSTEM, PaletteStyle.Neutral, 0.4f, true,
        ArtShape.ROUNDED, ArtShape.ROUNDED, cardRows = false, cardTiles = false, artBorder = false,
        monoBadges = false, eyebrows = false, pureBlack = false, shelfPlayButtons = false,
        defaultHome = listOf(
            HomeSection.QUICK_ACTIONS, HomeSection.TOP_ARTISTS, HomeSection.RECENTLY_ADDED,
            HomeSection.MOST_PLAYED, HomeSection.JUMP_BACK_IN, HomeSection.FAVOURITE_TRACKS,
            HomeSection.RECENTLY_PLAYED
        )
    )
}

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

/** The theme's spec with the user's artwork cut applied, if they chose one. */
fun StyleSpec.withArtworkCut(cut: io.github.roggzuay.ondalune.data.model.ArtworkCut): StyleSpec =
    if (cut == io.github.roggzuay.ondalune.data.model.ArtworkCut.AUTO) this
    else ArtShape.valueOf(cut.name).let { copy(artShape = it, rowArtShape = it) }

/** Clips to a normalized (0..1) Material shape polygon, scaled to the layout size. */
class PolygonShape(private val polygon: RoundedPolygon) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = polygon.toPath(android.graphics.Path())
        path.transform(android.graphics.Matrix().apply { setScale(size.width, size.height) })
        return Outline.Generic(path.asComposePath())
    }
}
