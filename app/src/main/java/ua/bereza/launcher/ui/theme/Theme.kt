package ua.bereza.launcher.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Фірмова палітра Bereza RP: чорний/графіт, білий текст, стриманий зелений «березового листя». */
object BerezaColors {
    val Black = Color(0xFF070908)
    val Graphite = Color(0xFF101312)
    val Surface = Color(0xFF161A18)
    val SurfaceHigh = Color(0xFF1E2421)
    val SurfaceHighest = Color(0xFF262D29)
    val Outline = Color(0xFF29312D)
    val OutlineSoft = Color(0xFF1F2623)
    val Green = Color(0xFF4CC38A)
    val GreenBright = Color(0xFF6FD9A4)
    val GreenDeep = Color(0xFF2C8A5C)
    val GreenGlow = Color(0x334CC38A)
    val OnGreen = Color(0xFF03140B)
    val Bark = Color(0xFFF2F2EE)
    val TextPrimary = Color(0xFFF3F5F4)
    val TextMuted = Color(0xFF9AA39F)
    val TextFaint = Color(0xFF66706B)
    val Error = Color(0xFFF06A6A)
    val Warning = Color(0xFFE9B85A)
}

private val ColorScheme = darkColorScheme(
    primary = BerezaColors.Green,
    onPrimary = BerezaColors.OnGreen,
    primaryContainer = BerezaColors.GreenDeep,
    onPrimaryContainer = BerezaColors.TextPrimary,
    secondary = BerezaColors.GreenBright,
    onSecondary = BerezaColors.OnGreen,
    secondaryContainer = BerezaColors.SurfaceHighest,
    onSecondaryContainer = BerezaColors.TextPrimary,
    tertiary = BerezaColors.Warning,
    background = BerezaColors.Black,
    onBackground = BerezaColors.TextPrimary,
    surface = BerezaColors.Graphite,
    onSurface = BerezaColors.TextPrimary,
    surfaceVariant = BerezaColors.SurfaceHigh,
    onSurfaceVariant = BerezaColors.TextMuted,
    surfaceContainerLowest = BerezaColors.Black,
    surfaceContainerLow = BerezaColors.Graphite,
    surfaceContainer = BerezaColors.Surface,
    surfaceContainerHigh = BerezaColors.SurfaceHigh,
    surfaceContainerHighest = BerezaColors.SurfaceHighest,
    outline = BerezaColors.Outline,
    outlineVariant = BerezaColors.OutlineSoft,
    error = BerezaColors.Error,
    onError = BerezaColors.Black,
)

private val Base = FontFamily.SansSerif

private val BerezaTypography = Typography(
    displaySmall = TextStyle(fontFamily = Base, fontWeight = FontWeight.Black, fontSize = 32.sp, lineHeight = 36.sp, letterSpacing = 1.5.sp),
    headlineSmall = TextStyle(fontFamily = Base, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = 0.2.sp),
    titleLarge = TextStyle(fontFamily = Base, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = Base, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.1.sp),
    titleSmall = TextStyle(fontFamily = Base, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Base, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Base, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Base, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = Base, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = 1.2.sp),
    labelMedium = TextStyle(fontFamily = Base, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.8.sp),
    labelSmall = TextStyle(fontFamily = Base, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 1.sp),
)

private val BerezaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun BerezaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ColorScheme,
        typography = BerezaTypography,
        shapes = BerezaShapes,
        content = content,
    )
}
