package ua.bereza.launcher.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ua.bereza.launcher.ui.theme.Bz
import ua.bereza.launcher.ui.theme.BzText
import kotlin.random.Random

/**
 * Знак Bereza RP: стовбур берези з насічками кори + два листки, які разом утворюють літеру «B».
 * Намальований векторно (координати в сітці 48×48), той самий знак, що й іконка застосунку.
 */
@Composable
fun BerezaMark(size: Dp = 40.dp, modifier: Modifier = Modifier, leaf: Color = Bz.Leaf, bark: Color = Bz.Bark, notch: Color = Bz.Soot) {
    Canvas(modifier.size(size)) { drawMark(leaf, bark, notch) }
}

fun DrawScope.drawMark(leaf: Color, bark: Color, notch: Color) {
    val u = this.size.minDimension / 48f
    fun o(x: Float, y: Float) = Offset(x * u, y * u)
    // стовбур
    drawRoundRect(bark, topLeft = o(10f, 4f), size = Size(8f * u, 40f * u), cornerRadius = CornerRadius(2f * u))
    // насічки
    listOf(Triple(10f, 10f, 4f), Triple(13f, 18f, 5f), Triple(10f, 28f, 3f), Triple(14f, 36f, 4f)).forEach { (x, y, w) ->
        drawRect(notch, topLeft = o(x, y), size = Size(w * u, 1.6f * u))
    }
    // листки
    val upper = Path().apply {
        moveTo(19f * u, 5f * u)
        quadraticBezierTo(37f * u, 5f * u, 37f * u, 14.5f * u)
        quadraticBezierTo(33.5f * u, 23f * u, 19f * u, 23f * u)
        close()
    }
    val lower = Path().apply {
        moveTo(19f * u, 25f * u)
        quadraticBezierTo(40f * u, 25f * u, 40f * u, 34.5f * u)
        quadraticBezierTo(36f * u, 44f * u, 19f * u, 44f * u)
        close()
    }
    drawPath(upper, leaf)
    drawPath(lower, leaf)
    drawLine(notch, o(21f, 14f), o(33f, 14f), strokeWidth = 1.1f * u, cap = StrokeCap.Round)
    drawLine(notch, o(21f, 34.4f), o(36f, 34.4f), strokeWidth = 1.1f * u, cap = StrokeCap.Round)
}

/** Логотип: знак + словесна частина BEREZA / ROLE PLAY. */
@Composable
fun BerezaLogo(modifier: Modifier = Modifier, markSize: Dp = 36.dp) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        BerezaMark(markSize)
        Spacer(Modifier.width(12.dp))
        Column(verticalArrangement = Arrangement.Center) {
            Text("BEREZA", style = BzText.t.title.copy(fontSize = 22.sp, letterSpacing = 3.sp, lineHeight = 22.sp))
            Text("ROLE PLAY", style = BzText.t.label.copy(color = Bz.Leaf, fontSize = 10.sp, letterSpacing = 4.sp, lineHeight = 12.sp))
        }
    }
}

/**
 * Фоновий мотив: кілька тонких стовбурів берези з насічками праворуч від головного блоку.
 * Дуже низька непрозорість — атмосфера без шуму. Детерміновано (той самий seed), без анімації.
 */
@Composable
fun BirchBackdrop(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val rnd = Random(1907)
        val trunks = listOf(0.70f to 0.055f, 0.82f to 0.035f, 0.91f to 0.07f, 0.98f to 0.03f)
        trunks.forEachIndexed { i, (xf, wf) ->
            val w = size.width * wf
            val x = size.width * xf
            val alpha = 0.05f + i * 0.012f
            drawRect(Bz.Bark.copy(alpha = alpha), topLeft = Offset(x, 0f), size = Size(w, size.height))
            var y = rnd.nextFloat() * 30f
            while (y < size.height) {
                val nw = w * (0.25f + rnd.nextFloat() * 0.6f)
                val nx = if (rnd.nextBoolean()) x else x + w - nw
                drawRect(Bz.Soot.copy(alpha = 0.55f), topLeft = Offset(nx, y), size = Size(nw, 2.2f + rnd.nextFloat() * 2f))
                y += 18f + rnd.nextFloat() * 46f
            }
        }
    }
}
