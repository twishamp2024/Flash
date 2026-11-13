package org.cutwire.drift.core.designsystem.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = DriftAccent,
    onPrimary = DriftOnAccent,
    primaryContainer = DriftSurfaceVariant,
    onPrimaryContainer = DriftAccent,
    secondary = DriftAccentDim,
    onSecondary = DriftOnAccent,
    background = DriftBackground,
    onBackground = DriftTextPrimary,
    surface = DriftSurface,
    onSurface = DriftTextPrimary,
    surfaceVariant = DriftSurfaceVariant,
    onSurfaceVariant = DriftTextSecondary,
    outline = DriftBorder,
    error = DriftError
)

@Composable
fun DriftTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = DarkColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = DriftTypography,
        content = content
    )
}
