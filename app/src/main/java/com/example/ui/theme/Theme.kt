package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accentColorHex: String = "#00F5FF", // Default AccentCyan
    content: @Composable () -> Unit
) {
    // Safely parse the user's custom accent color hex
    val parsedAccent = try {
        Color(android.graphics.Color.parseColor(accentColorHex))
    } catch (e: Exception) {
        AccentCyan
    }

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = parsedAccent,
            onPrimary = Color.Black,
            secondary = parsedAccent.copy(alpha = 0.7f),
            onSecondary = Color.White,
            background = CosmicBackground,
            onBackground = TextPrimaryDark,
            surface = CosmicSurface,
            onSurface = TextPrimaryDark,
            surfaceVariant = CosmicSurfaceVariant,
            onSurfaceVariant = TextSecondaryDark,
            error = Color(0xFFCF6679),
            onError = Color.Black
        )
    } else {
        lightColorScheme(
            primary = parsedAccent,
            onPrimary = Color.White,
            secondary = parsedAccent.copy(alpha = 0.8f),
            onSecondary = Color.Black,
            background = LightBackground,
            onBackground = TextPrimaryLight,
            surface = LightSurface,
            onSurface = TextPrimaryLight,
            surfaceVariant = LightSurfaceVariant,
            onSurfaceVariant = TextSecondaryLight,
            error = Color(0xFFB00020),
            onError = Color.White
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
