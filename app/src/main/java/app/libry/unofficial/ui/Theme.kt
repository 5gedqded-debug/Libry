package app.libry.unofficial.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Accent = Color(0xFF7C4DFF)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB69DFF),
    onPrimary = Color(0xFF2A0F73),
    primaryContainer = Color(0xFF4A2CB3),
    secondary = Color(0xFFCBC0E8),
    background = Color(0xFF0F0D14),
    surface = Color(0xFF0F0D14),
    surfaceVariant = Color(0xFF26222F),
    secondaryContainer = Color(0xFF3B3550),
)

private val LightColors = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6DEFF),
    background = Color(0xFFFCFAFF),
    surface = Color(0xFFFCFAFF),
    surfaceVariant = Color(0xFFE8E2F0),
    secondaryContainer = Color(0xFFE6DEFF),
)

@Composable
fun LibryTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}
