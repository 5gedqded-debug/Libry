package app.libry.unofficial.ui

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Токены дизайна (из макета IRATOON): оранжевый акцент, светлая подложка, серый вторичный текст. */
object Brand {
    val Primary = Color(0xFFE8793B)
    val Secondary = Color(0xFF6A7282)
    val Dust = Color(0xFFC4C4C4)
    val Background = Color(0xFFF1F1F1)
    val Splash = Color(0xFFFBAF56)
    val Star = Color(0xFFE8793B)
}

/** Шрифты: системные гарнитуры, близкие к Poppins / Be Vietnam Pro / Lilita One. */
object LibryFonts {
    val poppins: FontFamily = FontFamily.SansSerif
    val vietnam: FontFamily = FontFamily.SansSerif
    val lilita: FontFamily = FontFamily.SansSerif
    fun init(@Suppress("UNUSED_PARAMETER") ctx: Context) {}
}

private fun libryTypography(): Typography {
    val p = LibryFonts.poppins
    val v = LibryFonts.vietnam
    return Typography(
        headlineMedium = TextStyle(fontFamily = p, fontWeight = FontWeight.SemiBold, fontSize = 24.sp),
        titleLarge = TextStyle(fontFamily = v, fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
        titleMedium = TextStyle(fontFamily = v, fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
        titleSmall = TextStyle(fontFamily = p, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
        bodyLarge = TextStyle(fontFamily = p, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 22.sp),
        bodyMedium = TextStyle(fontFamily = p, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 23.sp, letterSpacing = 0.28.sp),
        bodySmall = TextStyle(fontFamily = p, fontWeight = FontWeight.Normal, fontSize = 12.sp),
        labelLarge = TextStyle(fontFamily = v, fontWeight = FontWeight.Normal, fontSize = 12.sp),
        labelMedium = TextStyle(fontFamily = p, fontWeight = FontWeight.Normal, fontSize = 12.sp),
        labelSmall = TextStyle(fontFamily = v, fontWeight = FontWeight.Normal, fontSize = 10.sp),
        displayMedium = TextStyle(fontFamily = p, fontWeight = FontWeight.Bold, fontSize = 40.sp),
    )
}

private val LightColors: ColorScheme = lightColorScheme(
    primary = Brand.Primary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE3D1),
    onPrimaryContainer = Color(0xFF5A2300),
    secondary = Brand.Secondary,
    onSecondary = Color.White,
    secondaryContainer = Color.White,
    onSecondaryContainer = Brand.Secondary,
    background = Brand.Background,
    onBackground = Color.Black,
    surface = Brand.Background,
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFE2E2E2),
    onSurfaceVariant = Brand.Secondary,
    outline = Brand.Dust,
    outlineVariant = Color(0xFFDDDDDD),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color.White,
)

private val DarkColors: ColorScheme = darkColorScheme(
    primary = Brand.Primary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF5A2D12),
    onPrimaryContainer = Color(0xFFFFDBC8),
    secondary = Color(0xFFA0A7B4),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF1F1F23),
    onSecondaryContainer = Color(0xFFA0A7B4),
    background = Color(0xFF121214),
    onBackground = Color(0xFFF2F2F2),
    surface = Color(0xFF121214),
    onSurface = Color(0xFFF2F2F2),
    surfaceVariant = Color(0xFF2A2A2E),
    onSurfaceVariant = Color(0xFFA0A7B4),
    outline = Color(0xFF55555B),
    outlineVariant = Color(0xFF3A3A3F),
    surfaceContainerLowest = Color(0xFF1F1F23),
    surfaceContainerLow = Color(0xFF1F1F23),
    surfaceContainer = Color(0xFF1F1F23),
    surfaceContainerHigh = Color(0xFF26262B),
    surfaceContainerHighest = Color(0xFF1F1F23),
)

@Composable
fun LibryTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = libryTypography(),
        content = content,
    )
}
