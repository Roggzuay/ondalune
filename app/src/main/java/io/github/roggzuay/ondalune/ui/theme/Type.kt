package io.github.roggzuay.ondalune.ui.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import io.github.roggzuay.ondalune.R
import io.github.roggzuay.ondalune.data.model.AppFont

/**
 * Variable fonts: each weight is a real instance of the `wght` axis, not synthetic bolding.
 * Unbounded (wide, atomic-age geometric) carries display/headline voice; Figtree carries
 * everything people actually read; JetBrains Mono is reserved for technical readouts
 * (codecs, bitrates, timecodes).
 */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun variableFamily(resId: Int, vararg weights: FontWeight) = FontFamily(
    weights.map { w ->
        Font(resId, w, variationSettings = FontVariation.Settings(FontVariation.weight(w.weight)))
    }
)

val DisplayFontFamily = variableFamily(
    R.font.unbounded,
    FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold, FontWeight.ExtraBold, FontWeight.Black
)

val TextFontFamily = variableFamily(
    R.font.figtree,
    FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold, FontWeight.ExtraBold
)

val MonoFontFamily = FontFamily(Font(R.font.jetbrains_mono, FontWeight.Normal))

private fun style(family: FontFamily, size: Int, line: Int, weight: FontWeight, tracking: TextUnit) = TextStyle(
    fontFamily = family, fontWeight = weight,
    fontSize = size.sp, lineHeight = line.sp, letterSpacing = tracking
)

/**
 * The Ondalune type scale with [header] on display/headline roles and [body] on title, body and
 * label roles, so headings and track text can wear different typefaces.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun buildTypography(header: FontFamily, body: FontFamily): Typography {
    fun display(size: Int, line: Int, weight: FontWeight, tracking: TextUnit = 0.sp) = style(header, size, line, weight, tracking)
    fun text(size: Int, line: Int, weight: FontWeight, tracking: TextUnit = 0.sp) = style(body, size, line, weight, tracking)
    return Typography(
        displayLarge = display(48, 54, FontWeight.Medium, (-1.2).sp),
        displayMedium = display(38, 44, FontWeight.Medium, (-0.8).sp),
        displaySmall = display(30, 36, FontWeight.Medium, (-0.5).sp),
        headlineLarge = display(26, 32, FontWeight.Medium, (-0.4).sp),
        headlineMedium = display(22, 28, FontWeight.Medium, (-0.3).sp),
        headlineSmall = display(18, 24, FontWeight.Medium, (-0.2).sp),
        titleLarge = text(21, 28, FontWeight.Bold, (-0.2).sp),
        titleMedium = text(16, 22, FontWeight.SemiBold, 0.sp),
        titleSmall = text(14, 20, FontWeight.SemiBold, 0.05.sp),
        bodyLarge = text(16, 24, FontWeight.Normal, 0.1.sp),
        bodyMedium = text(14, 20, FontWeight.Normal, 0.15.sp),
        bodySmall = text(12, 16, FontWeight.Medium, 0.2.sp),
        labelLarge = text(14, 20, FontWeight.SemiBold, 0.1.sp),
        labelMedium = text(12, 16, FontWeight.SemiBold, 0.4.sp),
        labelSmall = text(11, 16, FontWeight.Bold, 0.6.sp),
        // Expressive emphasized roles: heavier cuts of the same scale for hero moments.
        displayLargeEmphasized = display(48, 54, FontWeight.ExtraBold, (-1.4).sp),
        displayMediumEmphasized = display(38, 44, FontWeight.ExtraBold, (-1.0).sp),
        displaySmallEmphasized = display(30, 36, FontWeight.Bold, (-0.6).sp),
        headlineLargeEmphasized = display(26, 32, FontWeight.Bold, (-0.5).sp),
        headlineMediumEmphasized = display(22, 28, FontWeight.Bold, (-0.4).sp),
        headlineSmallEmphasized = display(18, 24, FontWeight.Bold, (-0.2).sp),
        titleLargeEmphasized = text(21, 28, FontWeight.ExtraBold, (-0.3).sp),
        titleMediumEmphasized = text(16, 22, FontWeight.Bold, 0.sp),
        titleSmallEmphasized = text(14, 20, FontWeight.Bold, 0.sp),
        bodyLargeEmphasized = text(16, 24, FontWeight.SemiBold, 0.1.sp),
        bodyMediumEmphasized = text(14, 20, FontWeight.SemiBold, 0.1.sp),
        bodySmallEmphasized = text(12, 16, FontWeight.Bold, 0.2.sp),
        labelLargeEmphasized = text(14, 20, FontWeight.Bold, 0.1.sp),
        labelMediumEmphasized = text(12, 16, FontWeight.Bold, 0.4.sp),
        labelSmallEmphasized = text(11, 16, FontWeight.ExtraBold, 0.6.sp)
    )
}

val Typography = buildTypography(DisplayFontFamily, TextFontFamily)

private val AllWeights = arrayOf(
    FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold, FontWeight.ExtraBold, FontWeight.Black
)

/** A variable font where each weight also pins extra axes (width, optical size, softness). */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun axisFamily(resId: Int, vararg axes: Pair<String, Float>) = FontFamily(
    AllWeights.map { w ->
        Font(
            resId, w,
            variationSettings = FontVariation.Settings(
                FontVariation.weight(w.weight), *axes.map { FontVariation.Setting(it.first, it.second) }.toTypedArray()
            )
        )
    }
)

private val familyCache = HashMap<Pair<AppFont, Boolean>, FontFamily>()

/**
 * The [FontFamily] for [font]; [display] selects the large optical size and personality axes
 * (Flex's widest cut, Fraunces' soft "wonky" forms) used on headings.
 */
fun appFontFamily(font: AppFont, display: Boolean): FontFamily = familyCache.getOrPut(font to display) {
    when (font) {
        AppFont.AUTO, AppFont.SYSTEM -> FontFamily.Default
        AppFont.FIGTREE -> TextFontFamily
        AppFont.UNBOUNDED -> DisplayFontFamily
        AppFont.FLEX_WIDE -> axisFamily(R.font.roboto_flex, "wdth" to 151f, "opsz" to if (display) 72f else 14f)
        AppFont.FLEX -> axisFamily(R.font.roboto_flex, "opsz" to if (display) 48f else 14f)
        AppFont.BRICOLAGE -> axisFamily(R.font.bricolage, "opsz" to if (display) 96f else 14f, "wdth" to if (display) 85f else 100f)
        AppFont.SYNE -> axisFamily(R.font.syne)
        AppFont.FRAUNCES -> axisFamily(R.font.fraunces, "opsz" to if (display) 144f else 14f, "SOFT" to if (display) 100f else 50f, "WONK" to if (display) 1f else 0f)
        AppFont.SPACE_GROTESK -> axisFamily(R.font.space_grotesk)
        AppFont.FREDOKA -> axisFamily(R.font.fredoka)
        AppFont.MONO -> MonoFontFamily
    }
}

/**
 * Standard interface style: the stock Material 3 type scale in the system sans (Roboto, or the
 * OEM's own UI font), so the app reads like the rest of the phone.
 */
val StandardTypography = Typography()
