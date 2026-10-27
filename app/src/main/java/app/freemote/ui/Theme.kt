package app.freemote.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Accent = Color(0xFF7C9CFF)
private val scheme = darkColorScheme(
    primary = Accent, onPrimary = Color(0xFF0B1020),
    secondary = Color(0xFF64E3C1),
    background = Color(0xFF0E1116), onBackground = Color(0xFFE8EAF0),
    surface = Color(0xFF161A22), onSurface = Color(0xFFE8EAF0),
    surfaceVariant = Color(0xFF222836), onSurfaceVariant = Color(0xFFB8BFCF),
    error = Color(0xFFFF6B6B),
)

@Composable
fun FreeMoteTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = scheme, content = content)
