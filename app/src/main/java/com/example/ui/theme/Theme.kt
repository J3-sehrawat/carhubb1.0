package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.example.data.DEFAULT_PRIMARY_COLOR_HEX
import java.util.Locale

data class BrandColorPreset(
    val name: String,
    val tagline: String,
    val hex: String,
    val primary: Color,
    val primaryDark: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color
)

val BRAND_COLOR_PRESETS: List<BrandColorPreset> = listOf(
    BrandColorPreset(
        name = "Racing Crimson",
        tagline = "Signature AutoTrade",
        hex = "#E11D48",
        primary = Crimson600,
        primaryDark = Crimson700,
        primaryContainer = Crimson100,
        onPrimaryContainer = Crimson700
    ),
    BrandColorPreset(
        name = "Cobalt Drive",
        tagline = "Pro Wholesale Blue",
        hex = "#2563EB",
        primary = Cobalt600,
        primaryDark = Color(0xFF1D4ED8),
        primaryContainer = Cobalt100,
        onPrimaryContainer = Color(0xFF1E40AF)
    ),
    BrandColorPreset(
        name = "Emerald Alloy",
        tagline = "Fresh Fleet Green",
        hex = "#059669",
        primary = Emerald600,
        primaryDark = Color(0xFF047857),
        primaryContainer = Emerald100,
        onPrimaryContainer = Color(0xFF065F46)
    ),
    BrandColorPreset(
        name = "Royal Indigo",
        tagline = "Executive Showroom",
        hex = "#4F46E5",
        primary = Color(0xFF4F46E5),
        primaryDark = Color(0xFF4338CA),
        primaryContainer = Color(0xFFE0E7FF),
        onPrimaryContainer = Color(0xFF3730A3)
    ),
    BrandColorPreset(
        name = "Turbo Orange",
        tagline = "Sport Performance",
        hex = "#EA580C",
        primary = Color(0xFFEA580C),
        primaryDark = Color(0xFFC2410C),
        primaryContainer = Color(0xFFFFEDD5),
        onPrimaryContainer = Color(0xFF9A3412)
    ),
    BrandColorPreset(
        name = "Petrol Teal",
        tagline = "Modern Garage",
        hex = "#0D9488",
        primary = Color(0xFF0D9488),
        primaryDark = Color(0xFF0F766E),
        primaryContainer = Color(0xFFCCFBF1),
        onPrimaryContainer = Color(0xFF115E59)
    ),
    BrandColorPreset(
        name = "Midnight Violet",
        tagline = "Luxury Detailing",
        hex = "#7C3AED",
        primary = Color(0xFF7C3AED),
        primaryDark = Color(0xFF6D28D9),
        primaryContainer = Color(0xFFEDE9FE),
        onPrimaryContainer = Color(0xFF5B21B6)
    ),
    BrandColorPreset(
        name = "Amber Gold",
        tagline = "Premium Metallic",
        hex = "#D97706",
        primary = Amber600,
        primaryDark = Color(0xFFB45309),
        primaryContainer = Amber100,
        onPrimaryContainer = Color(0xFF92400E)
    )
)

data class ResolvedBrandPalette(
    val hex: String,
    val primary: Color,
    val onPrimary: Color,
    val primaryDark: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color
)

private val HEX_PATTERN = Regex("^#[0-9A-F]{6}$")

fun parseBrandHexOrNull(raw: String): Color? {
    val clean = raw.trim().uppercase(Locale.US).let { if (it.startsWith("#")) it else "#$it" }
    if (!HEX_PATTERN.matches(clean)) return null
    return try {
        val rgb = clean.substring(1).toLong(16)
        Color(0xFF000000L or rgb)
    } catch (_: Exception) {
        null
    }
}

fun resolveBrandPalette(primaryColorHex: String): ResolvedBrandPalette {
    val normalized = primaryColorHex.trim().uppercase(Locale.US)
        .let { if (it.startsWith("#")) it else "#$it" }

    val preset = BRAND_COLOR_PRESETS.firstOrNull { it.hex.equals(normalized, ignoreCase = true) }
    if (preset != null) {
        return ResolvedBrandPalette(
            hex = preset.hex,
            primary = preset.primary,
            onPrimary = if (preset.primary.luminance() > 0.52f) Slate900 else PureWhite,
            primaryDark = preset.primaryDark,
            primaryContainer = preset.primaryContainer,
            onPrimaryContainer = preset.onPrimaryContainer
        )
    }

    val customColor = parseBrandHexOrNull(normalized)
        ?: return resolveBrandPalette(DEFAULT_PRIMARY_COLOR_HEX)

    val onPrimary = if (customColor.luminance() > 0.52f) Slate900 else PureWhite
    val primaryContainer = lerp(PureWhite, customColor, 0.14f)
    val primaryDark = lerp(customColor, Slate950, 0.22f)
    val onPrimaryContainer = lerp(customColor, Slate950, 0.32f)

    return ResolvedBrandPalette(
        hex = normalized,
        primary = customColor,
        onPrimary = onPrimary,
        primaryDark = primaryDark,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer
    )
}

private fun buildLightColorScheme(palette: ResolvedBrandPalette): ColorScheme = lightColorScheme(
    primary = palette.primary,
    onPrimary = palette.onPrimary,
    primaryContainer = palette.primaryContainer,
    onPrimaryContainer = palette.onPrimaryContainer,
    secondary = Cobalt600,
    onSecondary = PureWhite,
    secondaryContainer = Cobalt100,
    onSecondaryContainer = Slate900,
    tertiary = Slate900,
    onTertiary = PureWhite,
    tertiaryContainer = Slate800,
    onTertiaryContainer = PureWhite,
    background = Slate50,
    onBackground = Slate900,
    surface = PureWhite,
    onSurface = Slate900,
    surfaceVariant = Slate100,
    onSurfaceVariant = Slate600,
    outline = Slate300,
    outlineVariant = Slate200,
    error = Danger600,
    onError = PureWhite,
    errorContainer = Danger100,
    onErrorContainer = Danger600
)

private fun buildDarkColorScheme(palette: ResolvedBrandPalette): ColorScheme = darkColorScheme(
    primary = palette.primary,
    onPrimary = palette.onPrimary,
    primaryContainer = palette.primaryDark,
    onPrimaryContainer = palette.primaryContainer,
    secondary = Cobalt600,
    onSecondary = PureWhite,
    secondaryContainer = Slate800,
    onSecondaryContainer = Cobalt100,
    tertiary = Slate100,
    onTertiary = Slate900,
    background = Slate950,
    onBackground = Slate50,
    surface = Slate900,
    onSurface = Slate50,
    surfaceVariant = Slate800,
    onSurfaceVariant = Slate300,
    outline = Slate700,
    outlineVariant = Slate800,
    error = Danger600,
    onError = PureWhite
)

val AutoTradeShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp)
)

@Composable
fun AutoTradeTheme(
    darkTheme: Boolean = false,
    primaryColorHex: String = DEFAULT_PRIMARY_COLOR_HEX,
    content: @Composable () -> Unit
) {
    val palette = remember(primaryColorHex) { resolveBrandPalette(primaryColorHex) }
    val colorScheme = remember(darkTheme, palette) {
        if (darkTheme) buildDarkColorScheme(palette) else buildLightColorScheme(palette)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AutoTradeShapes,
        content = content
    )
}
