package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val ClipperLightColorScheme = lightColorScheme(
    primary = ClipperBlue,
    onPrimary = Color.White,
    primaryContainer = ClipperBlueContainer,
    onPrimaryContainer = ClipperOnBlueContainer,
    secondary = ClipperIndigo,
    onSecondary = Color.White,
    secondaryContainer = ClipperIndigoContainer,
    onSecondaryContainer = ClipperIndigo,
    tertiary = ClipperGreen,
    onTertiary = Color.White,
    tertiaryContainer = ClipperGreenContainer,
    onTertiaryContainer = ClipperGreen,
    background = ClipperBackground,
    onBackground = ClipperTextPrimary,
    surface = ClipperSurface,
    onSurface = ClipperTextPrimary,
    surfaceVariant = ClipperSurfaceSubtle,
    onSurfaceVariant = ClipperTextSecondary,
    outline = ClipperOutlineStrong,
    outlineVariant = ClipperOutline,
    error = ClipperError,
    onError = Color.White,
    errorContainer = ClipperErrorContainer,
    onErrorContainer = ClipperError
)

val ClipperShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun ClipperTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ClipperLightColorScheme,
        typography = ClipperTypography,
        shapes = ClipperShapes,
        content = content
    )
}
