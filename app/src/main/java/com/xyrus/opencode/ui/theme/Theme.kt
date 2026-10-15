package com.xyrus.opencode.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Colors = darkColorScheme(
    primary = Color(0xFF7CC4FF),
    onPrimary = Color(0xFF00344F),
    background = Color(0xFF101418),
    onBackground = Color(0xFFE3E6EA),
    surface = Color(0xFF171B21),
    onSurface = Color(0xFFE3E6EA),
    surfaceVariant = Color(0xFF232A33),
    onSurfaceVariant = Color(0xFFAEB6C1),
    error = Color(0xFFFFB4AB),
)

@Composable
fun OpencodeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // Single dark palette for now; the parameter exists so light mode is a
    // one-line change later rather than a refactor.
    MaterialTheme(colorScheme = Colors, content = content)
}
