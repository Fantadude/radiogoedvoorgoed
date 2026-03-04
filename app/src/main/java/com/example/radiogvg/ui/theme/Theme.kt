package com.example.radiogvg.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightBlueColorScheme = lightColorScheme(
    primary = LightBluePrimary,
    secondary = LightBlueSecondary,
    tertiary = LightBlueDark,
    background = OffWhite,
    surface = White,
    onPrimary = White,
    onSecondary = TextDark,
    onTertiary = White,
    onBackground = TextDark,
    onSurface = TextDark,
    primaryContainer = LightBlueLight,
    onPrimaryContainer = LightBlueDark,
    secondaryContainer = LightGray,
    onSecondaryContainer = TextMedium
)

private val DarkBlueColorScheme = darkColorScheme(
    primary = LightBlueSecondary,
    secondary = LightBluePrimary,
    tertiary = LightBlueLight,
    background = TextDark,
    surface = LightBlueDark,
    onPrimary = TextDark,
    onSecondary = White,
    onTertiary = TextDark,
    onBackground = White,
    onSurface = White,
    primaryContainer = LightBlueDark,
    onPrimaryContainer = LightBlueLight,
    secondaryContainer = TextMedium,
    onSecondaryContainer = LightGray
)

@Composable
fun RadiogvgTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Disabled to use our custom colors
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        darkTheme -> DarkBlueColorScheme
        else -> LightBlueColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
