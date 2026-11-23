package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val ResendDarkColorScheme = darkColorScheme(
    primary = InkPrimary,
    onPrimary = VoidBlack,
    primaryContainer = SurfaceMuted,
    onPrimaryContainer = InkPrimary,
    secondary = EmeraldPrimary,
    onSecondary = VoidBlack,
    secondaryContainer = EmeraldDim,
    onSecondaryContainer = EmeraldGlow,
    tertiary = CyanInfo,
    onTertiary = VoidBlack,
    tertiaryContainer = CyanDim,
    onTertiaryContainer = CyanInfo,
    background = VoidBlack,
    onBackground = InkPrimary,
    surface = SurfaceElevated,
    onSurface = InkPrimary,
    surfaceVariant = SurfaceCard,
    onSurfaceVariant = InkSecondary,
    outline = BorderHairline,
    outlineVariant = BorderFocused,
    error = CrimsonError,
    onError = Color.White,
    errorContainer = CrimsonDim,
    onErrorContainer = CrimsonError
)

private val ResendLightColorScheme = lightColorScheme(
    primary = LightInkPrimary,
    onPrimary = LightSurface,
    primaryContainer = LightSurfaceMuted,
    onPrimaryContainer = LightInkPrimary,
    secondary = LightEmerald,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD1FAE5),
    onSecondaryContainer = Color(0xFF065F46),
    tertiary = Color(0xFF0284C7),
    onTertiary = Color.White,
    background = LightBackground,
    onBackground = LightInkPrimary,
    surface = LightSurface,
    onSurface = LightInkPrimary,
    surfaceVariant = LightSurfaceMuted,
    onSurfaceVariant = LightInkSecondary,
    outline = LightBorder,
    outlineVariant = Color(0xFFD4D4D8),
    error = CrimsonError,
    onError = Color.White
)

// Modern squared Resend-inspired corner geometry
val CronDeckShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(10.dp),
    extraLarge = RoundedCornerShape(12.dp)
)

@Composable
fun CronDeckTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) ResendDarkColorScheme else ResendLightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = CronDeckTypography,
        shapes = CronDeckShapes,
        content = content
    )
}
