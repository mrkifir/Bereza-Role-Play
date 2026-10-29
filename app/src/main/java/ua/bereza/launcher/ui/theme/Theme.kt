package ua.bereza.launcher.ui.theme

import android.graphics.Typeface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Дизайн-система «Береста».
 * Ідея: кора берези — тепло-біла поверхня з чорними горизонтальними насічками — на тлі нічного
 * графітового міста. Зелений «свіжий лист» — єдиний акцент і завжди означає дію або «готово».
 */
object Bz {
    val Soot = Color(0xFF0B0C0C)       // фон
    val Graphite = Color(0xFF141716)   // поверхня
    val Raised = Color(0xFF1B1F1D)     // піднята поверхня / натиснутий стан
    val Line = Color(0xFF2A2F2C)       // тонкі лінії, рамки
    val Notch = Color(0xFF3A403C)      // неактивні насічки прогресу

    val Bark = Color(0xFFECE9E1)       // основний текст (тепло-білий, як кора)
    val BarkDim = Color(0xFFA9AAA2)    // другорядний текст
    val Muted = Color(0xFF6F746E)      // підписи, мета-дані

    val Leaf = Color(0xFF8CC63F)       // акцент / дія / онлайн
    val LeafDeep = Color(0xFF5F8E2B)
    val OnLeaf = Color(0xFF0D120A)

    val Amber = Color(0xFFE3A63B)      // оновлення, попередження
    val Ember = Color(0xFFE0574F)      // помилка, офлайн
}

@Immutable
data class BzType(
    val display: TextStyle,
    val title: TextStyle,
    val label: TextStyle,
    val body: TextStyle,
    val small: TextStyle,
    val data: TextStyle,
    val button: TextStyle,
)

private val Condensed = FontFamily(Typeface.create("sans-serif-condensed", Typeface.BOLD))
private val CondensedRegular = FontFamily(Typeface.create("sans-serif-condensed", Typeface.NORMAL))
private val Mono = FontFamily.Monospace

val BzTypography = BzType(
    display = TextStyle(fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 36.sp, letterSpacing = 0.5.sp, color = Bz.Bark),
    title = TextStyle(fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 24.sp, letterSpacing = 0.8.sp, color = Bz.Bark),
    label = TextStyle(fontFamily = CondensedRegular, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 14.sp, letterSpacing = 2.2.sp, color = Bz.Muted),
    body = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 15.sp, lineHeight = 22.sp, color = Bz.BarkDim),
    small = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 13.sp, lineHeight = 18.sp, color = Bz.Muted),
    data = TextStyle(fontFamily = Mono, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.sp, color = Bz.Bark),
    button = TextStyle(fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = 4.sp),
)

val LocalBzType = staticCompositionLocalOf { BzTypography }

object BzDims {
    val gutter = 20.dp
    val radius = 6.dp
    val radiusSmall = 3.dp
    val hairline = 1.dp
}

@Composable
fun BerezaTheme(content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = Bz.Leaf,
        onPrimary = Bz.OnLeaf,
        secondary = Bz.Bark,
        onSecondary = Bz.Soot,
        background = Bz.Soot,
        onBackground = Bz.Bark,
        surface = Bz.Graphite,
        onSurface = Bz.Bark,
        surfaceVariant = Bz.Raised,
        onSurfaceVariant = Bz.BarkDim,
        outline = Bz.Line,
        error = Bz.Ember,
        onError = Bz.Soot,
    )
    MaterialTheme(colorScheme = scheme, typography = Typography()) {
        androidx.compose.runtime.CompositionLocalProvider(LocalBzType provides BzTypography, content = content)
    }
}

object BzText {
    val t: BzType @Composable get() = LocalBzType.current
}
