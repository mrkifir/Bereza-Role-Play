package ua.bereza.launcher.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import ua.bereza.launcher.R

/** Палітра «нічний березовий гай»: чорнило, біла кора, молодий листок. */
object B {
    val Ink = Color(0xFF0D0F0E)
    val Surface = Color(0xFF151816)
    val Card = Color(0xFF1B1F1C)
    val CardHi = Color(0xFF232824)
    val Line = Color(0xFF2C322E)
    val Bark = Color(0xFFF0EDE4)
    val Muted = Color(0xFF8E968F)
    val Faint = Color(0xFF5A625C)
    val Leaf = Color(0xFFC9DC5C)
    val LeafDeep = Color(0xFF8FA33A)
    val Amber = Color(0xFFE8B04B)
    val Danger = Color(0xFFE5675A)
    val OnLeaf = Color(0xFF141A08)
}

@OptIn(ExperimentalTextApi::class)
private fun variable(res: Int, weight: Int) =
    Font(res, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

val Display = FontFamily(
    variable(R.font.unbounded, 500),
    variable(R.font.unbounded, 700),
    variable(R.font.unbounded, 800),
)

val Body = FontFamily(
    variable(R.font.manrope, 400),
    variable(R.font.manrope, 500),
    variable(R.font.manrope, 600),
    variable(R.font.manrope, 700),
)

private val typography = Typography(
    displaySmall = TextStyle(fontFamily = Display, fontWeight = FontWeight(800), fontSize = 30.sp, letterSpacing = 1.sp),
    headlineSmall = TextStyle(fontFamily = Display, fontWeight = FontWeight(700), fontSize = 20.sp),
    titleMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight(700), fontSize = 16.sp),
    titleSmall = TextStyle(fontFamily = Body, fontWeight = FontWeight(700), fontSize = 14.sp),
    bodyLarge = TextStyle(fontFamily = Body, fontWeight = FontWeight(500), fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight(500), fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Body, fontWeight = FontWeight(500), fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight(700), fontSize = 14.sp, letterSpacing = 1.sp),
    labelMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight(700), fontSize = 12.sp, letterSpacing = 0.6.sp),
    labelSmall = TextStyle(fontFamily = Body, fontWeight = FontWeight(700), fontSize = 10.sp, letterSpacing = 1.2.sp),
)

@Composable
fun BerezaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = B.Leaf, onPrimary = B.OnLeaf,
            secondary = B.Amber, background = B.Ink, onBackground = B.Bark,
            surface = B.Surface, onSurface = B.Bark, surfaceVariant = B.Card, onSurfaceVariant = B.Muted,
            outline = B.Line, error = B.Danger,
        ),
        typography = typography,
        content = content,
    )
}
