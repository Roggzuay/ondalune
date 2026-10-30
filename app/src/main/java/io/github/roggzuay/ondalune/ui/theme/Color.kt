package io.github.roggzuay.ondalune.ui.theme

import androidx.compose.ui.graphics.Color

// Material 3 Expressive Dark & Light Base Palettes
val ExpressiveDarkBackground = Color(0xFF0E0D15)
val ExpressiveDarkSurface = Color(0xFF161521)
val ExpressiveDarkSurfaceContainerLow = Color(0xFF1B1928)
val ExpressiveDarkSurfaceContainer = Color(0xFF211F30)
val ExpressiveDarkSurfaceContainerHigh = Color(0xFF2B293D)
val ExpressiveDarkSurfaceContainerHighest = Color(0xFF36334B)

val ExpressivePrimaryDefault = Color(0xFFFF6B5B)
val ExpressiveOnPrimaryDefault = Color(0xFF3B0904)
val ExpressivePrimaryContainerDefault = Color(0xFF6B1B12)
val ExpressiveOnPrimaryContainerDefault = Color(0xFFFFDAD5)

val ExpressiveSecondaryDefault = Color(0xFFD0BCFF)
val ExpressiveOnSecondaryDefault = Color(0xFF381E72)
val ExpressiveSecondaryContainerDefault = Color(0xFF4F378B)
val ExpressiveOnSecondaryContainerDefault = Color(0xFFEADDFF)

val ExpressiveTertiaryDefault = Color(0xFF6FF7FF)
val ExpressiveOnTertiaryDefault = Color(0xFF00363A)
val ExpressiveTertiaryContainerDefault = Color(0xFF004F54)
val ExpressiveOnTertiaryContainerDefault = Color(0xFFA6Faff)

val ExpressiveLightBackground = Color(0xFFFFF8F6)
val ExpressiveLightSurface = Color(0xFFFFF8F6)
val ExpressiveLightSurfaceContainer = Color(0xFFFCEAE6)
val ExpressiveLightSurfaceContainerHigh = Color(0xFFF6E4E0)

/**
 * Parses a hex string like "#FF6B5B" into a Compose [Color] safely.
 */
fun parseHexColor(hex: String, fallback: Color = ExpressivePrimaryDefault): Color {
    return try {
        val clean = hex.trim().removePrefix("#")
        val value = when (clean.length) {
            6 -> ("FF$clean").toLong(16)
            8 -> clean.toLong(16)
            else -> return fallback
        }
        Color(value)
    } catch (_: Exception) {
        fallback
    }
}
