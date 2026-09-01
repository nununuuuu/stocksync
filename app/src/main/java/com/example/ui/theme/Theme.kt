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
    primary = StockPrimary,
    onPrimary = Color.Black,
    primaryContainer = StockPrimaryDark,
    onPrimaryContainer = Color.White,
    secondary = StockSecondary,
    onSecondary = Color.White,
    tertiary = StockTertiary,
    background = StockNavyDark,
    onBackground = TextPrimaryDark,
    surface = StockSurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = StockSurfaceVariantDark,
    onSurfaceVariant = TextSecondaryDark,
    outline = StockBorderDark
)

private val LightColorScheme = lightColorScheme(
    primary = StockPrimaryDark,
    onPrimary = Color.White,
    secondary = StockSecondary,
    onSecondary = Color.White,
    tertiary = StockTertiary,
    background = StockSurfaceLight,
    onBackground = TextPrimaryLight,
    surface = StockCardLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = TextSecondaryLight,
    outline = StockBorderLight
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Keep consistent high-contrast financial theme by default
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}

