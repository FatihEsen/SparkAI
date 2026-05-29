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

private val DarkColorScheme =
  darkColorScheme(
    primary = Color(0xFFD13438),       // Sporty Red
    onPrimary = Color.White,
    secondary = Color(0xFF4ADE80),     // Vibrant Link Green
    onSecondary = Color.Black,
    background = Color(0xFF0F1115),    // Immersive Deep Background
    surface = Color(0xFF1C1E26),       // Inactive Cards Surface
    onBackground = Color(0xFFE2E2E6),
    onSurface = Color(0xFFE2E2E6)
  )

private val LightColorScheme =
  lightColorScheme(
    primary = Color(0xFFD13438),
    secondary = Color(0xFF4ADE80),
    background = Color(0xFF0F1115),
    surface = Color(0xFF1C1E26),
    onBackground = Color(0xFFE2E2E6),
    onSurface = Color(0xFFE2E2E6)
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true, // Force dark theme for immersive look
  dynamicColor: Boolean = false, // Disable dynamic colors to preserve our sport cockpit theme
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}

