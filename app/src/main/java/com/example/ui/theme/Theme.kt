package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF67DC9C),
    onPrimary = Color(0xFF00381F),
    primaryContainer = EmeraldDark,
    onPrimaryContainer = Color(0xFFA5F4C7),
    secondary = Color(0xFFE9C369),
    onSecondary = Color(0xFF3F2E00),
    secondaryContainer = Color(0xFF5A4300),
    onSecondaryContainer = Color(0xFFFFDF9E),
    tertiary = Color(0xFFA6C8FF),
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    onBackground = Color(0xFFE1E5E2),
    onSurface = Color(0xFFE1E5E2),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

private val LightColorScheme = lightColorScheme(
    primary = EmeraldPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA5F4C7),
    onPrimaryContainer = Color(0xFF002111),
    secondary = GoldAccent,
    onSecondary = Color.White,
    secondaryContainer = GoldLight,
    onSecondaryContainer = Color(0xFF3B2900),
    tertiary = NavySecondary,
    onTertiary = Color.White,
    background = LightBackground,
    surface = LightSurface,
    surfaceVariant = LightSurfaceVariant,
    onBackground = SlateText,
    onSurface = SlateText,
    error = DangerRed,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Use our refined brand identity
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
