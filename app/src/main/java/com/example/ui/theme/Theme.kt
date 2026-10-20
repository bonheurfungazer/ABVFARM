package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Organic Color Palette
val Terracotta = Color(0xFFD47A60)
val SageGreen = Color(0xFF3E5A47)
val SoftSage = Color(0xFFE2EDE4)
val DarkSage = Color(0xFF233527)
val SoftCream = Color(0xFFFAF7F2)
val PureWarmWhite = Color(0xFFFCFAF7)
val EarthCharcoal = Color(0xFF2C3530)
val SoftSand = Color(0xFFECE5D8)

// Gender specific high contrast eye-friendly pastels
val SoftMaleBlue = Color(0xFFDFECF7)
val DarkMaleBlue = Color(0xFF27435F)
val DarkMaleSurface = Color(0xFF152636)
val DarkMaleOnSurface = Color(0xFFB8D8F8)

val SoftFemalePink = Color(0xFFF7DFEB)
val DarkFemalePink = Color(0xFF632B4C)
val DarkFemaleSurface = Color(0xFF331826)
val DarkFemaleOnSurface = Color(0xFFF7BBD9)

// Dark Mode Organic Color Palette - Designed for low light & early morning farm rounds
val DarkBackground = Color(0xFF121714)
val DarkSurface = Color(0xFF1B221E)
val DarkSurfaceVariant = Color(0xFF26322B)
val DarkPrimary = Color(0xFF7CB88F)
val DarkPrimaryContainer = Color(0xFF243B2C)
val DarkOnPrimaryContainer = Color(0xFFD6EDE0)
val DarkSecondary = Color(0xFFE89882)
val DarkSecondaryContainer = Color(0xFF562D22)
val DarkOnSecondaryContainer = Color(0xFFFFDBD2)
val DarkOnSurface = Color(0xFFE5EDE8)
val DarkOnSurfaceVariant = Color(0xFFA5B7AC)
val DarkBorder = Color(0xFF2F3E35)

private val DarkColors = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = Color(0xFF0F2C18),
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = Color(0xFF4C1E12),
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = Color(0xFF90A4AE),
    onTertiary = Color(0xFF1E293B),
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val LightColors = lightColorScheme(
    primary = SageGreen,
    onPrimary = Color.White,
    primaryContainer = SoftSage,
    onPrimaryContainer = DarkSage,
    secondary = Terracotta,
    onSecondary = Color.White,
    secondaryContainer = SoftSand,
    onSecondaryContainer = EarthCharcoal,
    tertiary = Color(0xFF6B7280), // Slate gray for subtle metadata tags
    onTertiary = Color.White,
    background = Color(0xFFF9F9F6), // Extremely polished eggshell white background
    onBackground = EarthCharcoal,
    surface = Color.White, // Pure crisp white for elevated cards
    onSurface = EarthCharcoal,
    surfaceVariant = Color(0xFFF2EFEA), // Very light beige for grouped dividers
    onSurfaceVariant = Color(0xFF4A5551), // Elegant dark gray for descriptions
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

tailrec fun android.content.Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun ABVFarmTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            // Adjust status & navigation bar icons according to dark/light theme
            WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

@Composable
fun getRabbitGenderColors(isMale: Boolean, isActive: Boolean): Pair<Color, Color> {
    val isDark = MaterialTheme.colorScheme.background == DarkBackground
    if (!isActive) {
        return Pair(
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    return if (isMale) {
        if (isDark) Pair(DarkMaleSurface, DarkMaleOnSurface) else Pair(SoftMaleBlue, DarkMaleBlue)
    } else {
        if (isDark) Pair(DarkFemaleSurface, DarkFemaleOnSurface) else Pair(SoftFemalePink, DarkFemalePink)
    }
}
