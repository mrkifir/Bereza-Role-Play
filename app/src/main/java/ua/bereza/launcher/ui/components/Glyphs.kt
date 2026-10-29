package ua.bereza.launcher.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ua.bereza.launcher.ui.theme.Bz

/** Власний набір лінійних іконок Bereza: 24-сітка, штрих 1.7, прямі кінці кутів як «насічки». */
enum class Glyph { Back, Settings, News, Server, Pulse, Folder, Check, Alert, Refresh, Close, Download, Shield, Copy, ArrowRight, Users }

@Composable
fun BzGlyph(glyph: Glyph, modifier: Modifier = Modifier, size: Dp = 22.dp, color: Color = Bz.Bark) {
    Canvas(modifier.size(size)) {
        val u = this.size.minDimension / 24f
        val stroke = Stroke(width = 1.7f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun p(vararg pts: Float, closed: Boolean = false): Path = Path().apply {
            moveTo(pts[0] * u, pts[1] * u)
            var i = 2
            while (i < pts.size) { lineTo(pts[i] * u, pts[i + 1] * u); i += 2 }
            if (closed) close()
        }
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(color, Offset(x1 * u, y1 * u), Offset(x2 * u, y2 * u), strokeWidth = 1.7f * u, cap = StrokeCap.Round)

        when (glyph) {
            Glyph.Back -> drawPath(p(15f, 5f, 8f, 12f, 15f, 19f), color, style = stroke)
            Glyph.ArrowRight -> { line(5f, 12f, 19f, 12f); drawPath(p(13f, 6f, 19f, 12f, 13f, 18f), color, style = stroke) }
            Glyph.Settings -> {
                // три повзунки
                line(4f, 7f, 20f, 7f); line(4f, 12f, 20f, 12f); line(4f, 17f, 20f, 17f)
                drawCircle(Bz.Soot, 2.6f * u, Offset(9f * u, 7f * u)); drawCircle(color, 2.6f * u, Offset(9f * u, 7f * u), style = stroke)
                drawCircle(Bz.Soot, 2.6f * u, Offset(16f * u, 12f * u)); drawCircle(color, 2.6f * u, Offset(16f * u, 12f * u), style = stroke)
                drawCircle(Bz.Soot, 2.6f * u, Offset(7f * u, 17f * u)); drawCircle(color, 2.6f * u, Offset(7f * u, 17f * u), style = stroke)
            }
            Glyph.News -> {
                drawPath(p(5f, 4f, 19f, 4f, 19f, 20f, 5f, 20f, closed = true), color, style = stroke)
                line(8f, 8.5f, 16f, 8.5f); line(8f, 12.5f, 16f, 12.5f); line(8f, 16.5f, 12.5f, 16.5f)
            }
            Glyph.Server -> {
                drawPath(p(4f, 4f, 20f, 4f, 20f, 10f, 4f, 10f, closed = true), color, style = stroke)
                drawPath(p(4f, 14f, 20f, 14f, 20f, 20f, 4f, 20f, closed = true), color, style = stroke)
                drawCircle(color, 1.1f * u, Offset(7.5f * u, 7f * u)); drawCircle(color, 1.1f * u, Offset(7.5f * u, 17f * u))
            }
            Glyph.Pulse -> drawPath(p(3f, 12f, 8f, 12f, 10.5f, 6f, 13.5f, 18f, 16f, 12f, 21f, 12f), color, style = stroke)
            Glyph.Folder -> drawPath(p(3f, 6f, 9.5f, 6f, 11.5f, 8.5f, 21f, 8.5f, 21f, 19f, 3f, 19f, closed = true), color, style = stroke)
            Glyph.Check -> drawPath(p(5f, 12.5f, 10f, 17.5f, 19.5f, 7f), color, style = stroke)
            Glyph.Alert -> {
                drawPath(p(12f, 3.5f, 21f, 19.5f, 3f, 19.5f, closed = true), color, style = stroke)
                line(12f, 9.5f, 12f, 14f); drawCircle(color, 1.1f * u, Offset(12f * u, 16.8f * u))
            }
            Glyph.Refresh -> {
                drawArc(color, 30f, 280f, false, topLeft = Offset(5f * u, 5f * u), size = Size(14f * u, 14f * u), style = stroke)
                drawPath(p(17.5f, 3.5f, 18.6f, 8.2f, 14f, 9f), color, style = stroke)
            }
            Glyph.Close -> { line(6f, 6f, 18f, 18f); line(18f, 6f, 6f, 18f) }
            Glyph.Download -> { line(12f, 4f, 12f, 15f); drawPath(p(7f, 10.5f, 12f, 15.5f, 17f, 10.5f), color, style = stroke); line(5f, 20f, 19f, 20f) }
            Glyph.Shield -> {
                drawPath(p(12f, 3f, 19.5f, 6f, 19.5f, 12f, 12f, 21f, 4.5f, 12f, 4.5f, 6f, closed = true), color, style = stroke)
                drawPath(p(8.5f, 12f, 11f, 14.5f, 15.5f, 9.5f), color, style = stroke)
            }
            Glyph.Copy -> {
                drawPath(p(8f, 8f, 20f, 8f, 20f, 20f, 8f, 20f, closed = true), color, style = stroke)
                drawPath(p(4f, 16f, 4f, 4f, 16f, 4f), color, style = stroke)
            }
            Glyph.Users -> {
                drawCircle(color, 3.5f * u, Offset(9f * u, 8.5f * u), style = stroke)
                drawArc(color, 180f, 180f, false, topLeft = Offset(3f * u, 14f * u), size = Size(12f * u, 10f * u), style = stroke)
                drawCircle(color, 2.6f * u, Offset(16.5f * u, 9f * u), style = stroke)
                drawArc(color, 200f, 140f, false, topLeft = Offset(13f * u, 14.5f * u), size = Size(9f * u, 8f * u), style = stroke)
            }
        }
    }
}
