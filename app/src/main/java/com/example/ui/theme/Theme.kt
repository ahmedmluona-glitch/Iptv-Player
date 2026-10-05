package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val TvDarkColorScheme =
  darkColorScheme(
    primary = TvAccentGold,
    onPrimary = TvBackground,
    secondary = TvSurfaceHighlight,
    onSecondary = TvTextPrimary,
    background = TvBackground,
    onBackground = TvTextPrimary,
    surface = TvSurface,
    onSurface = TvTextPrimary,
    outline = TvBorder
  )

@Composable
fun MluonaTheme(content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = TvDarkColorScheme,
    typography = Typography,
    content = content
  )
}

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true,
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  MluonaTheme(content = content)
}

