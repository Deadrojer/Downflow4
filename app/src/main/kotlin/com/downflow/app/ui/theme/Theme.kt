package com.downflow.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary          = Color(0xFF1565C0),
    onPrimary        = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6E4FF),
    secondary        = Color(0xFF0277BD),
    tertiary         = Color(0xFF00695C),
    background       = Color(0xFFFCFCFF),
    surface          = Color(0xFFFCFCFF),
    surfaceVariant   = Color(0xFFE1E2EC),
    outline          = Color(0xFF74777F),
    error            = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary          = Color(0xFFADC6FF),
    onPrimary        = Color(0xFF002E6E),
    primaryContainer = Color(0xFF00429B),
    secondary        = Color(0xFF7FC8FF),
    tertiary         = Color(0xFF80CBC4),
    background       = Color(0xFF1C1B1F),
    surface          = Color(0xFF1C1B1F),
    surfaceVariant   = Color(0xFF44474F),
    outline          = Color(0xFF8E9099),
    error            = Color(0xFFFFB4AB),
)

@Composable
fun DownFlowTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colors,
        typography = Typography(),
        content = content,
    )
}
