package com.israrxy.raazi.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.israrxy.raazi.ui.theme.NeonPink

private val SonicColorScheme = darkColorScheme(
    primary = Emerald500,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF065F46),
    onPrimaryContainer = Color(0xFF6EE7B7),
    secondary = Emerald400,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF064E3B),
    onSecondaryContainer = Color(0xFFA7F3D0),
    tertiary = Zinc700,
    onTertiary = Zinc50,
    background = Zinc950,
    onBackground = Zinc50,
    surface = Zinc900,
    onSurface = Zinc50,
    surfaceVariant = Zinc800,
    onSurfaceVariant = Zinc400,
    outline = Zinc700,
    outlineVariant = Zinc800,
    inversePrimary = Emerald400,
    inverseSurface = Zinc100,
    inverseOnSurface = Zinc900,
    scrim = Color.Black
)

private val SonicLightColorScheme = androidx.compose.material3.lightColorScheme(
    primary = Emerald600,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA7F3D0),
    onPrimaryContainer = Color(0xFF064E3B),
    secondary = Emerald500,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFA7F3D0),
    onSecondaryContainer = Color(0xFF064E3B),
    tertiary = Zinc300,
    onTertiary = Zinc900,
    background = Color.White,
    onBackground = Zinc950,
    surface = Zinc50,
    onSurface = Zinc950,
    surfaceVariant = Zinc100,
    onSurfaceVariant = Zinc600,
    outline = Zinc300,
    outlineVariant = Zinc200,
    inversePrimary = Emerald600,
    inverseSurface = Zinc900,
    inverseOnSurface = Zinc50,
    scrim = Color.Black
)

/**
 * Applies a pastel accent to a base color scheme. Overrides not just [primary]/[secondary] but the
 * container + inverse variants too, so accent-driven surfaces (nav bar selected pill, chips, tinted
 * cards) actually change color when the accent changes. "Emerald"/"Green" keep the base scheme.
 */
private fun applyAccent(
    base: androidx.compose.material3.ColorScheme,
    accent: String,
    dark: Boolean
): androidx.compose.material3.ColorScheme {
    // Each accent: primary, secondary, container, onContainer (tuned per light/dark for contrast).
    val a = when (accent) {
        "Rose" -> if (dark)
            AccentColors(Color(0xFFF48FB1), Color(0xFFF8BBD0), Color(0xFF5C1130), Color(0xFFFFD9E2))
        else
            AccentColors(Color(0xFFD81B60), Color(0xFFE91E63), Color(0xFFFFD9E2), Color(0xFF3B0014))
        "Peach" -> if (dark)
            AccentColors(Color(0xFFFFCC80), Color(0xFFFFE0B2), Color(0xFF7A4A12), Color(0xFFFFEACC))
        else
            AccentColors(Color(0xFFF57C00), Color(0xFFFFB74D), Color(0xFFFFEACC), Color(0xFF2C1600))
        "Mint" -> if (dark)
            AccentColors(Color(0xFFA7F3D0), Color(0xFFD1FAE5), Color(0xFF064E3B), Color(0xFFD1FAE5))
        else
            AccentColors(Color(0xFF059669), Color(0xFF10B981), Color(0xFFD1FAE5), Color(0xFF022C22))
        "Sage" -> if (dark)
            AccentColors(Color(0xFF86EFAC), Color(0xFFBBF7D0), Color(0xFF14532D), Color(0xFFDCFCE7))
        else
            AccentColors(Color(0xFF16A34A), Color(0xFF22C55E), Color(0xFFDCFCE7), Color(0xFF052E16))
        "Lavender" -> if (dark)
            AccentColors(Color(0xFFB39DDB), Color(0xFFD1C4E9), Color(0xFF4A398C), Color(0xFFE7DEFF))
        else
            AccentColors(Color(0xFF673AB7), Color(0xFF9575CD), Color(0xFFE7DEFF), Color(0xFF21005D))
        "Sky" -> if (dark)
            AccentColors(Color(0xFF90CAF9), Color(0xFFBBDEFB), Color(0xFF0D47A1), Color(0xFFD6E9FF))
        else
            AccentColors(Color(0xFF2196F3), Color(0xFF64B5F6), Color(0xFFD6E9FF), Color(0xFF001C3B))
        "Lilac" -> if (dark)
            AccentColors(Color(0xFFD8B4FE), Color(0xFFE9D5FF), Color(0xFF581C87), Color(0xFFF3E8FF))
        else
            AccentColors(Color(0xFF9333EA), Color(0xFFA855F7), Color(0xFFF3E8FF), Color(0xFF3B0764))
        "Lemon" -> if (dark)
            AccentColors(Color(0xFFFDE047), Color(0xFFFEF08A), Color(0xFF713F12), Color(0xFFFEF9C3))
        else
            AccentColors(Color(0xFFCA8A04), Color(0xFFEAB308), Color(0xFFFEF9C3), Color(0xFF422006))
        "Coral" -> if (dark)
            AccentColors(Color(0xFFFF8A65), Color(0xFFFFCCBC), Color(0xFF661F00), Color(0xFFFFCCBC))
        else
            AccentColors(Color(0xFFE64A19), Color(0xFFFF5722), Color(0xFFFFCCBC), Color(0xFF3E1100))
        "Aqua" -> if (dark)
            AccentColors(Color(0xFF80DEEA), Color(0xFFB2EBF2), Color(0xFF004D40), Color(0xFFE0F7FA))
        else
            AccentColors(Color(0xFF00ACC1), Color(0xFF00BCD4), Color(0xFFE0F7FA), Color(0xFF00272B))
        "Periwinkle" -> if (dark)
            AccentColors(Color(0xFF9FA8DA), Color(0xFFC5CAE9), Color(0xFF1A237E), Color(0xFFE8EAF6))
        else
            AccentColors(Color(0xFF3F51B5), Color(0xFF5C6BC0), Color(0xFFE8EAF6), Color(0xFF0D1242))
        "Blush" -> if (dark)
            AccentColors(Color(0xFFF06292), Color(0xFFF8BBD0), Color(0xFF4A0020), Color(0xFFFCE4EC))
        else
            AccentColors(Color(0xFFC2185B), Color(0xFFD81B60), Color(0xFFFCE4EC), Color(0xFF370017))
        "Pistachio" -> if (dark)
            AccentColors(Color(0xFFA3E635), Color(0xFFD9F99D), Color(0xFF365314), Color(0xFFECFCCB))
        else
            AccentColors(Color(0xFF65A30D), Color(0xFF84CC16), Color(0xFFECFCCB), Color(0xFF1A2E05))
        else -> return base // Emerald / Green — base scheme already carries the accent
    }
    return base.copy(
        primary = a.primary,
        secondary = a.secondary,
        primaryContainer = a.container,
        onPrimaryContainer = a.onContainer,
        secondaryContainer = a.container,
        onSecondaryContainer = a.onContainer,
        inversePrimary = a.secondary,
        onPrimary = if (dark) Color.Black else Color.White
    )
}

private data class AccentColors(
    val primary: Color,
    val secondary: Color,
    val container: Color,
    val onContainer: Color
)

@Composable
fun RaaziTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+ — opt-in only; brand palette is the default
    dynamicColor: Boolean = false,
    pastelAccent: String = "Emerald",
    pureBlack: Boolean = false,
    content: @Composable () -> Unit
) {
    val baseScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> applyAccent(SonicColorScheme, pastelAccent, dark = true)
        else -> applyAccent(SonicLightColorScheme, pastelAccent, dark = false)
    }

    // Pure-black (AMOLED) mode — only meaningful in dark theme and when not using dynamic color.
    val colorScheme = if (pureBlack && darkTheme && !(dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)) {
        baseScheme.copy(
            background = Color.Black,
            surface = Color(0xFF0A0A0A),
            surfaceVariant = Color(0xFF141414),
            surfaceContainer = Color(0xFF101010),
            surfaceContainerHigh = Color(0xFF161616),
            surfaceContainerHighest = Color(0xFF1C1C1C),
            surfaceBright = Color(0xFF1A1A1A),
            surfaceDim = Color(0xFF050505),
            onSurface = Zinc50,
            onSurfaceVariant = Zinc400
        )
    } else {
        baseScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}