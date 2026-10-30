package io.github.roggzuay.ondalune.ui.theme

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.roggzuay.ondalune.data.model.AppFont
import io.github.roggzuay.ondalune.data.model.AppThemeMode
import io.github.roggzuay.ondalune.data.model.CornerStylePreset
import io.github.roggzuay.ondalune.data.model.CuratedColorPalette
import io.github.roggzuay.ondalune.data.model.InterfaceStyle
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import com.materialkolor.dynamiccolor.ColorSpec

/**
 * CompositionLocal indicating whether system-level reduced motion (`ANIMATOR_DURATION_SCALE == 0f`
 * or `TRANSITION_ANIMATION_SCALE == 0f`) is enabled.
 */
val LocalReducedMotion = compositionLocalOf { false }

fun isSystemReducedMotionEnabled(context: Context): Boolean {
    return try {
        val animScale = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1.0f
        )
        val transScale = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.TRANSITION_ANIMATION_SCALE,
            1.0f
        )
        animScale <= 0.01f || transScale <= 0.01f
    } catch (_: Throwable) {
        false
    }
}

/**
 * Centralized high-refresh-rate motion tokens for Ondalune.
 * Uses critically damped (`DampingRatioNoBouncy`) springs for structural navigation and sheets so
 * routine tab and screen transitions never overshoot or jitter at 120Hz.
 */
object OndaluneMotionSpec {
    fun navigationOffsetSpring(): FiniteAnimationSpec<IntOffset> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium
    )

    fun navigationFadeIn(): FiniteAnimationSpec<Float> = tween(
        durationMillis = 200,
        easing = FastOutSlowInEasing
    )

    fun navigationFadeOut(): FiniteAnimationSpec<Float> = tween(
        durationMillis = 140,
        easing = FastOutLinearInEasing
    )

    fun sheetExpansionSpring(): FiniteAnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    fun interactivePressSpring(): FiniteAnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMedium
    )
}

/**
 * M3 Expressive shape scale, multiplied by the user's corner preset. Corners stay on the spec's
 * ladder (6/10/16/24/32 + increased steps) so containers read as distinct families instead of
 * one uniform pill.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun buildShapesForPreset(preset: CornerStylePreset): Shapes {
    fun r(base: Float) = RoundedCornerShape((base * preset.scaleMultiplier).dp)
    return Shapes(
        extraSmall = r(6f),
        small = r(10f),
        medium = r(16f),
        large = r(24f),
        largeIncreased = r(28f),
        extraLarge = r(32f),
        extraLargeIncreased = r(40f),
        extraExtraLarge = r(48f)
    )
}

val ExpressiveShapes = buildShapesForPreset(CornerStylePreset.EXPRESSIVE_ROUNDED)

/** Stock Material 3 shape scale (4/8/12/16/20/28/32/48), scaled by the corner preset. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun buildStandardShapes(preset: CornerStylePreset): Shapes {
    fun r(base: Float) = RoundedCornerShape((base * preset.scaleMultiplier).dp)
    return Shapes(
        extraSmall = r(4f),
        small = r(8f),
        medium = r(12f),
        large = r(16f),
        largeIncreased = r(20f),
        extraLarge = r(28f),
        extraLargeIncreased = r(32f),
        extraExtraLarge = r(48f)
    )
}

val LocalInterfaceStyle = staticCompositionLocalOf { InterfaceStyle.SIGNATURE }

/**
 * Multiplier applied to every hand-tuned corner radius in the UI ([cornerShape],
 * SpringPressableSurface), so the corner preset and interface style reshape the whole app.
 */
val LocalCornerScale = staticCompositionLocalOf { 1f }

/** A rounded rectangle whose [radius] follows the active corner preset and interface style. */
@Composable
@ReadOnlyComposable
fun cornerShape(radius: Dp) = RoundedCornerShape(radius * LocalCornerScale.current)

/**
 * Builds the full tonal scheme (every role, correct on-colors) from one seed using the
 * Material Color Utilities 2025 spec, the same pipeline Android uses for wallpaper color.
 *
 * Priority: unified Material You > artwork harmonization > system dynamic color > curated palette.
 */
fun buildOndaluneColorScheme(
    context: Context,
    seedHex: String,
    isDark: Boolean,
    isAmoled: Boolean,
    useMaterialYouDynamic: Boolean,
    unifiedMaterialYouColor: Boolean,
    harmonizeWithArtwork: Boolean,
    curatedPalette: CuratedColorPalette,
    interfaceStyle: InterfaceStyle = InterfaceStyle.SIGNATURE
): ColorScheme {
    // Each theme picks its palette character (Standard/Classic keep surfaces near-neutral).
    val paletteStyle = styleSpecFor(interfaceStyle).paletteStyle
    val system = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else null

    // Pure Material You: hand the system palette through untouched (only AMOLED-blacked if asked).
    if (system != null && (unifiedMaterialYouColor || (useMaterialYouDynamic && !harmonizeWithArtwork))) {
        return if (isAmoled) system.copy(background = Color.Black, surface = Color.Black, surfaceContainerLowest = Color.Black) else system
    }

    val curatedSeed = parseHexColor(curatedPalette.primarySeedHex)
    val curatedAccent = parseHexColor(curatedPalette.accentSeedHex)
    return if (harmonizeWithArtwork) {
        val artworkSeed = parseHexColor(seedHex, curatedSeed)
        dynamicColorScheme(
            seedColor = artworkSeed,
            isDark = isDark,
            isAmoled = isAmoled,
            // A grey seed means black-and-white artwork: every style except Monochrome would
            // invent a colour for it (grey's hue is 0, so it came out red/pink).
            style = if (artworkSeed.isNeutral()) PaletteStyle.Monochrome else paletteStyle,
            specVersion = ColorSpec.SpecVersion.SPEC_2025
        )
    } else {
        // Curated palettes keep their signature accent as the tertiary family.
        dynamicColorScheme(
            seedColor = curatedSeed,
            isDark = isDark,
            isAmoled = isAmoled,
            tertiary = curatedAccent,
            style = paletteStyle,
            specVersion = ColorSpec.SpecVersion.SPEC_2025
        )
    }
}

private fun Color.isNeutral(): Boolean {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(toArgb(), hsv)
    return hsv[1] < 0.06f
}

/**
 * Resolved ARGB palette used by the Media Notification and Home Screen AppWidget so they
 * recolor from the same tonal scheme the app renders with.
 */
data class DynamicSurfacePalette(
    val primaryArgb: Int,
    val accentArgb: Int,
    val containerArgb: Int,
    val containerHighArgb: Int,
    val onContainerArgb: Int,
    val onSurfaceVariantArgb: Int,
    val seedHex: String,
    val accentHex: String
)

fun resolveDynamicSurfacePalette(
    context: Context,
    seedHex: String,
    accentHex: String,
    unifiedMaterialYouColor: Boolean,
    useMaterialYouDynamic: Boolean = true,
    harmonizeWithArtwork: Boolean = true,
    curatedPalette: CuratedColorPalette = CuratedColorPalette.DEFAULT_EXPRESSIVE
): DynamicSurfacePalette {
    val scheme = buildOndaluneColorScheme(
        context, seedHex, isDark = true, isAmoled = false,
        useMaterialYouDynamic = useMaterialYouDynamic,
        unifiedMaterialYouColor = unifiedMaterialYouColor,
        harmonizeWithArtwork = harmonizeWithArtwork,
        curatedPalette = curatedPalette
    )
    return DynamicSurfacePalette(
        primaryArgb = scheme.primary.toArgb(),
        accentArgb = scheme.tertiary.toArgb(),
        containerArgb = scheme.surfaceContainer.toArgb(),
        containerHighArgb = scheme.surfaceContainerHighest.toArgb(),
        onContainerArgb = scheme.onSurface.toArgb(),
        onSurfaceVariantArgb = scheme.onSurfaceVariant.toArgb(),
        seedHex = colorToHex(scheme.primary),
        accentHex = colorToHex(scheme.tertiary)
    )
}

private fun colorToHex(color: Color): String = "#%06X".format(color.toArgb() and 0xFFFFFF)

/**
 * Material 3 Expressive theme: tonal color from artwork / Material You / curated palette,
 * expressive spring motion (standard when the system asks for reduced motion), emphasized type,
 * and the expressive shape scale scaled by the corner preset.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OndaluneExpressiveTheme(
    seedHex: String = "#FF6B5B",
    @Suppress("UNUSED_PARAMETER") accentHex: String = "#70F5FF",
    themeMode: AppThemeMode = AppThemeMode.DARK,
    useMaterialYouDynamic: Boolean = true,
    unifiedMaterialYouColor: Boolean = false,
    harmonizeWithArtwork: Boolean = true,
    curatedPalette: CuratedColorPalette = CuratedColorPalette.DEFAULT_EXPRESSIVE,
    cornerStylePreset: CornerStylePreset = CornerStylePreset.EXPRESSIVE_ROUNDED,
    interfaceStyle: InterfaceStyle = InterfaceStyle.SIGNATURE,
    headerFont: AppFont = AppFont.AUTO,
    bodyFont: AppFont = AppFont.AUTO,
    artworkCut: io.github.roggzuay.ondalune.data.model.ArtworkCut = io.github.roggzuay.ondalune.data.model.ArtworkCut.AUTO,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        AppThemeMode.SYSTEM -> systemDark
        AppThemeMode.DARK, AppThemeMode.AMOLED -> true
        AppThemeMode.LIGHT -> false
    }
    val spec = remember(interfaceStyle, artworkCut) { styleSpecFor(interfaceStyle).withArtworkCut(artworkCut) }
    val isAmoled = themeMode == AppThemeMode.AMOLED || (spec.pureBlack && isDark)

    val standard = spec.standardShapes
    // Applied instantly: animating the scheme recomposed every color reader on every frame of the
    // fade, which was the stutter on track change. The artwork crossfade carries the transition.
    val colorScheme = remember(seedHex, isDark, isAmoled, useMaterialYouDynamic, unifiedMaterialYouColor, harmonizeWithArtwork, curatedPalette, interfaceStyle) {
        buildOndaluneColorScheme(
            context, seedHex, isDark, isAmoled,
            useMaterialYouDynamic, unifiedMaterialYouColor, harmonizeWithArtwork, curatedPalette, interfaceStyle
        )
    }
    val resolvedShapes = remember(cornerStylePreset, standard) {
        if (standard) buildStandardShapes(cornerStylePreset) else buildShapesForPreset(cornerStylePreset)
    }
    val cornerScale = cornerStylePreset.scaleMultiplier * spec.cornerFactor
    val resolvedHeader = if (headerFont == AppFont.AUTO) spec.headerFont else headerFont
    val resolvedBody = if (bodyFont == AppFont.AUTO) spec.bodyFont else bodyFont
    val typography = remember(resolvedHeader, resolvedBody) {
        if (resolvedHeader == AppFont.SYSTEM && resolvedBody == AppFont.SYSTEM) StandardTypography
        else buildTypography(appFontFamily(resolvedHeader, display = true), appFontFamily(resolvedBody, display = false))
    }
    val reducedMotion = remember(context) { isSystemReducedMotionEnabled(context) }

    CompositionLocalProvider(
        LocalReducedMotion provides reducedMotion,
        LocalInterfaceStyle provides interfaceStyle,
        LocalStyleSpec provides spec,
        LocalCornerScale provides cornerScale
    ) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            motionScheme = if (reducedMotion) MotionScheme.standard() else MotionScheme.expressive(),
            shapes = resolvedShapes,
            typography = typography,
            content = content
        )
    }
}

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    OndaluneExpressiveTheme(content = content)
}

/** Technical readouts (codec, bitrate): monospace where the theme wants it, else the label style. */
@Composable
@ReadOnlyComposable
fun monoLabelSmall() = MaterialTheme.typography.labelSmall.let {
    if (LocalStyleSpec.current.monoBadges) it.copy(fontFamily = MonoFontFamily) else it
}
