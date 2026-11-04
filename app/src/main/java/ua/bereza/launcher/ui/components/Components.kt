package ua.bereza.launcher.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ua.bereza.launcher.ui.theme.B
import ua.bereza.launcher.ui.theme.Display
import kotlin.random.Random

/** Фон: темний градієнт і ледь помітні стовбури берез із «сочевичками». */
@Composable
fun BarkBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxSize().background(B.Ink)) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF141A12), B.Ink, B.Ink)))
            drawCircle(
                Brush.radialGradient(listOf(B.Leaf.copy(alpha = 0.10f), Color.Transparent), center = Offset(size.width * 0.85f, 0f), radius = size.width * 0.9f),
                radius = size.width * 0.9f, center = Offset(size.width * 0.85f, 0f),
            )
            val rnd = Random(7)
            val trunks = listOf(0.06f to 14f, 0.2f to 8f, 0.9f to 18f, 0.76f to 6f)
            trunks.forEach { (x, w) ->
                val left = size.width * x
                val wpx = w.dp.toPx()
                drawRect(B.Bark.copy(alpha = 0.035f), Offset(left, 0f), Size(wpx, size.height))
                var y = rnd.nextFloat() * 60f
                while (y < size.height) {
                    val len = wpx * (0.4f + rnd.nextFloat() * 0.6f)
                    drawRect(B.Ink.copy(alpha = 0.5f), Offset(left + rnd.nextFloat() * (wpx - len), y), Size(len, 2.dp.toPx()))
                    y += 24.dp.toPx() + rnd.nextFloat() * 50.dp.toPx()
                }
            }
        }
        content()
    }
}

/** Логотип: стовбур берези + листок і напис BEREZA / ROLE PLAY. */
@Composable
fun BerezaLogo(modifier: Modifier = Modifier, compact: Boolean = false) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        BirchMark(Modifier.size(if (compact) 30.dp else 44.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text("BEREZA", fontFamily = Display, fontWeight = FontWeight(800), fontSize = if (compact) 18.sp else 26.sp, color = B.Bark, letterSpacing = 2.sp)
            Text("ROLE PLAY", fontFamily = Display, fontWeight = FontWeight(500), fontSize = if (compact) 9.sp else 11.sp, color = B.Leaf, letterSpacing = 5.sp)
        }
    }
}

@Composable
fun BirchMark(modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val trunkW = w * 0.3f
        val left = w * 0.3f
        drawRoundRect(B.Bark, Offset(left, h * 0.12f), Size(trunkW, h * 0.78f), CornerRadius(trunkW * 0.25f))
        val marks = listOf(0.25f to 0.55f, 0.42f to 0.75f, 0.58f to 0.45f, 0.74f to 0.7f)
        marks.forEachIndexed { i, (y, len) ->
            val l = trunkW * len
            val x = if (i % 2 == 0) left else left + trunkW - l
            drawRect(B.Ink, Offset(x, h * y), Size(l, h * 0.045f))
        }
        val leaf = Path().apply {
            moveTo(left + trunkW, h * 0.26f)
            cubicTo(w * 0.72f, h * 0.02f, w * 0.98f, h * 0.02f, w, h * 0.08f)
            cubicTo(w * 0.96f, h * 0.3f, w * 0.78f, h * 0.36f, left + trunkW, h * 0.3f)
            close()
        }
        drawPath(leaf, B.Leaf)
        drawRect(B.Leaf, Offset(w * 0.18f, h * 0.9f), Size(w * 0.56f, h * 0.06f))
    }
}

@Composable
fun BCard(
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
    onClick: (() -> Unit)? = null,
    padding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .clip(shape)
            .background(if (highlight) B.CardHi else B.Card)
            .border(1.dp, if (highlight) B.Leaf.copy(alpha = 0.35f) else B.Line, shape)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(padding),
        content = content,
    )
}

@Composable
fun Kicker(text: String, color: Color = B.Muted) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = color)
}

@Composable
fun StatusDot(color: Color, pulse: Boolean) {
    val t = rememberInfiniteTransition(label = "dot")
    val a by t.animateFloat(0.25f, 0.7f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "a")
    Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
        if (pulse) Box(Modifier.size(18.dp).clip(CircleShape).background(color.copy(alpha = a * 0.5f)))
        Box(Modifier.size(9.dp).clip(CircleShape).background(color))
    }
}

@Composable
fun Pill(text: String, color: Color = B.Leaf) {
    Text(
        text, style = MaterialTheme.typography.labelSmall, color = color,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.12f)).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
fun LeafProgress(fraction: Float, modifier: Modifier = Modifier, indeterminate: Boolean = false) {
    val anim by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(300), label = "p")
    val t = rememberInfiniteTransition(label = "ind")
    val x by t.animateFloat(-0.4f, 1f, infiniteRepeatable(tween(1200)), label = "x")
    Canvas(modifier.fillMaxWidth().height(8.dp)) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(B.Line, cornerRadius = r)
        if (indeterminate) {
            drawRoundRect(B.Leaf, Offset(size.width * x.coerceAtLeast(0f), 0f), Size(size.width * 0.4f, size.height), r)
        } else if (anim > 0f) {
            drawRoundRect(Brush.horizontalGradient(listOf(B.LeafDeep, B.Leaf)), size = Size(size.width * anim, size.height), cornerRadius = r)
        }
    }
}

/**
 * Головна кнопка. Якщо [enabled] = false — виглядає вимкненою і показує причину,
 * натискання лише відкриває пояснення (жодного фальшивого запуску).
 */
@Composable
fun PlayButton(
    label: String,
    sublabel: String?,
    enabled: Boolean,
    icon: ImageVector?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier
            .fillMaxWidth()
            .height(76.dp)
            .clip(shape)
            .then(
                if (enabled) Modifier.background(Brush.horizontalGradient(listOf(B.Leaf, Color(0xFFDDEB86))))
                else Modifier.background(B.Card).border(1.5.dp, B.Line, shape)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (enabled) B.OnLeaf else B.Muted, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontFamily = Display, fontWeight = FontWeight(800), fontSize = 22.sp, letterSpacing = 3.sp,
                color = if (enabled) B.OnLeaf else B.Muted)
            if (sublabel != null) Text(sublabel, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
                color = if (enabled) B.OnLeaf.copy(alpha = 0.75f) else B.Faint)
        }
    }
}

@Composable
fun SecondaryButton(text: String, icon: ImageVector?, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = B.Bark, enabled: Boolean = true) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier.clip(shape).border(1.dp, if (enabled) color.copy(alpha = 0.3f) else B.Line, shape)
            .let { if (enabled) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) { Icon(icon, null, tint = if (enabled) color else B.Faint, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, style = MaterialTheme.typography.titleSmall, color = if (enabled) color else B.Faint)
    }
}

@Composable
fun SettingRow(title: String, subtitle: String? = null, onClick: (() -> Unit)? = null, trailing: @Composable (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it }.padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = B.Bark)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = B.Muted)
        }
        if (trailing != null) { Spacer(Modifier.width(12.dp)); trailing() }
    }
}

@Composable
fun BSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    Switch(
        checked, onChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = B.OnLeaf, checkedTrackColor = B.Leaf,
            uncheckedThumbColor = B.Muted, uncheckedTrackColor = B.Surface, uncheckedBorderColor = B.Line,
        ),
    )
}

@Composable
fun KeyValue(key: String, value: String, valueColor: Color = B.Bark) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(key, style = MaterialTheme.typography.bodyMedium, color = B.Muted, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = valueColor, textAlign = TextAlign.End)
    }
}
