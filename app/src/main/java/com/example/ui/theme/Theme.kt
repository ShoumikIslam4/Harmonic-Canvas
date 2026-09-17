package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CosmicColorScheme = darkColorScheme(
    primary = CrystalBlue,
    onPrimary = Color.Black,
    primaryContainer = CosmicSurfaceVariant,
    onPrimaryContainer = TextPrimary,
    secondary = NebulaViolet,
    onSecondary = Color.White,
    secondaryContainer = CosmicSurfaceVariant,
    onSecondaryContainer = TextPrimary,
    tertiary = CyanGlow,
    onTertiary = Color.Black,
    background = DeepCosmic,
    onBackground = TextPrimary,
    surface = CosmicSurface,
    onSurface = TextPrimary,
    surfaceVariant = CosmicSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = CosmicBorder
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = CosmicColorScheme,
        typography = Typography,
        content = content
    )
}

