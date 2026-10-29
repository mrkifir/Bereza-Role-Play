package ua.bereza.launcher.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ua.bereza.launcher.ui.theme.Bz
import ua.bereza.launcher.ui.theme.BzDims
import ua.bereza.launcher.ui.theme.BzText
import kotlin.random.Random

/* ------------------------------------------------------------------ */
/*  Прогрес «Кора»: сегменти різної довжини, як насічки на березі.     */
/*  Заповнення — рівно fraction від ширини (реальні дані).              */
/* ------------------------------------------------------------------ */

@Composable
fun BarkProgress(
    fraction: Float?,
    modifier: Modifier = Modifier,
    height: Dp = 10.dp,
    color: Color = Bz.Leaf,
) {
    val animated by animateFloatAsState(fraction?.coerceIn(0f, 1f) ?: 0f, tween(450), label = "bark")
    val shimmer = rememberInfiniteTransition(label = "indet")
    val sweep by shimmer.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "sweep")
    val segments = remember { barkSegments() }

    Canvas(modifier.fillMaxWidth().height(height)) {
        val gap = 3.dp.toPx()
        val r = CornerRadius(1.5.dp.toPx())
        // розкласти сегменти на всю ширину
        val totalUnits = segments.sum()
        val usable = size.width - gap * (segments.size - 1)
        var x = 0f
        val fillX = size.width * animated
        segments.forEach { unitW ->
            val w = usable * unitW / totalUnits
            drawRoundRect(Bz.Notch, Offset(x, 0f), Size(w, size.height), r)
            if (fraction != null) {
                if (fillX > x) clipRect(right = fillX) { drawRoundRect(color, Offset(x, 0f), Size(w, size.height), r) }
            }
            x += w + gap
        }
        if (fraction == null) {
            // невизначений прогрес: світла смуга біжить по корі (без вигаданих відсотків)
            val band = size.width * 0.22f
            val start = -band + (size.width + band) * sweep
            clipRect(left = start, right = start + band) {
                var xx = 0f
                segments.forEach { unitW ->
                    val w = usable * unitW / totalUnits
                    drawRoundRect(color.copy(alpha = 0.55f), Offset(xx, 0f), Size(w, size.height), r)
                    xx += w + gap
                }
            }
        }
    }
}

private fun barkSegments(): List<Float> {
    val rnd = Random(42)
    return List(22) { 0.6f + rnd.nextFloat() * 1.9f }
}

@Composable
fun BarkDivider(modifier: Modifier = Modifier) {
    val segs = remember { barkSegments() }
    Canvas(modifier.fillMaxWidth().height(2.dp)) {
        var x = 0f
        var i = 0
        while (x < size.width) {
            val w = segs[i % segs.size] * 10.dp.toPx()
            drawRect(Bz.Line, Offset(x, 0f), Size(w.coerceAtMost(size.width - x), size.height))
            x += w + (4 + (i % 3) * 3).dp.toPx()
            i++
        }
    }
}

/* ------------------------------------------------------------------ */
/*  Поверхні                                                           */
/* ------------------------------------------------------------------ */

@Composable
fun BzCard(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    onClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(BzDims.radius)
    var m = modifier
        .clip(shape)
        .background(Bz.Graphite)
        .border(BorderStroke(BzDims.hairline, Bz.Line), shape)
    if (onClick != null) m = m.clickable(onClick = onClick)
    Box(m) {
        if (accent != null) {
            Box(Modifier.width(3.dp).height(28.dp).background(accent).align(Alignment.TopStart))
        }
        Column(Modifier.padding(padding), content = content)
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, trailing: (@Composable RowScope.() -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp, 2.dp).background(Bz.Leaf))
        Spacer(Modifier.width(8.dp))
        Text(text.uppercase(), style = BzText.t.label, modifier = Modifier.weight(1f))
        trailing?.invoke(this)
    }
}

@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier, pulse: Boolean = false) {
    val t = rememberInfiniteTransition(label = "dot")
    val a by t.animateFloat(1f, if (pulse) 0.35f else 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a")
    Box(modifier.size(8.dp).clip(CircleShape).background(color.copy(alpha = a)))
}

@Composable
fun KeyValue(key: String, value: String, modifier: Modifier = Modifier, valueColor: Color = Bz.Bark) {
    Row(modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(key, style = BzText.t.small, modifier = Modifier.weight(1f))
        Text(value, style = BzText.t.data.copy(color = valueColor), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/* ------------------------------------------------------------------ */
/*  Кнопки                                                             */
/* ------------------------------------------------------------------ */

enum class PlayVariant { Play, Install, Update, Busy, Disabled }

/**
 * Головна кнопка. Форма — прямокутник зі «зрізаним» правим верхнім кутом, як надріз на корі;
 * ліворуч — три насічки. Під натисканням трохи стискається.
 */
@Composable
fun PlayButton(
    label: String,
    variant: PlayVariant,
    modifier: Modifier = Modifier,
    caption: String? = null,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.975f else 1f, tween(120), label = "press")
    val bg by animateColorAsState(
        when (variant) {
            PlayVariant.Play, PlayVariant.Install -> Bz.Leaf
            PlayVariant.Update -> Bz.Amber
            PlayVariant.Busy, PlayVariant.Disabled -> Bz.Raised
        },
        tween(250), label = "bg",
    )
    val fg = if (variant == PlayVariant.Busy || variant == PlayVariant.Disabled) Bz.BarkDim else Bz.OnLeaf
    val enabled = variant != PlayVariant.Disabled
    val shape = CutCornerShape(topEnd = 18.dp)

    Box(
        modifier
            .scale(scale)
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clip(shape)
            .background(bg)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.align(Alignment.CenterStart).padding(start = 18.dp).size(14.dp, 30.dp)) {
            val h = 2.dp.toPx()
            drawRect(fg.copy(alpha = 0.55f), Offset(0f, size.height * 0.15f), Size(size.width, h))
            drawRect(fg.copy(alpha = 0.55f), Offset(size.width * 0.35f, size.height * 0.5f), Size(size.width * 0.65f, h))
            drawRect(fg.copy(alpha = 0.55f), Offset(0f, size.height * 0.85f), Size(size.width * 0.6f, h))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 12.dp, horizontal = 44.dp)) {
            Text(label.uppercase(), style = BzText.t.button.copy(color = fg), maxLines = 1)
            if (caption != null) {
                Text(caption, style = BzText.t.small.copy(color = fg.copy(alpha = 0.75f)), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun GhostButton(
    text: String,
    modifier: Modifier = Modifier,
    glyph: Glyph? = null,
    color: Color = Bz.Bark,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(BzDims.radius)
    Row(
        modifier
            .clip(shape)
            .border(BorderStroke(BzDims.hairline, if (enabled) Bz.Line else Bz.Raised), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (glyph != null) {
            BzGlyph(glyph, size = 18.dp, color = if (enabled) color else Bz.Muted)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = BzText.t.title.copy(fontSize = 15.sp, color = if (enabled) color else Bz.Muted, letterSpacing = 1.2.sp), maxLines = 1)
    }
}


@Composable
fun IconTap(glyph: Glyph, contentDescription: String, modifier: Modifier = Modifier, tint: Color = Bz.Bark, onClick: () -> Unit) {
    Box(
        modifier
            .size(44.dp)
            .clip(RoundedCornerShape(BzDims.radius))
            .clickable(onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { BzGlyph(glyph, color = tint) }
}

/* ------------------------------------------------------------------ */
/*  Верхня панель, рядки налаштувань, поле вводу                       */
/* ------------------------------------------------------------------ */

@Composable
fun BzTopBar(title: String, onBack: (() -> Unit)?, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) IconTap(Glyph.Back, "Назад", onClick = onBack) else Spacer(Modifier.width(12.dp))
        Text(title.uppercase(), style = BzText.t.title, modifier = Modifier.weight(1f).padding(start = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        actions()
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = BzText.t.body.copy(color = Bz.Bark))
            if (subtitle != null) Text(subtitle, style = BzText.t.small)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Bz.OnLeaf, checkedTrackColor = Bz.Leaf,
                uncheckedThumbColor = Bz.BarkDim, uncheckedTrackColor = Bz.Raised, uncheckedBorderColor = Bz.Line,
            ),
        )
    }
}

@Composable
fun NavRow(title: String, subtitle: String?, glyph: Glyph, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BzGlyph(glyph, color = Bz.BarkDim)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = BzText.t.body.copy(color = Bz.Bark))
            if (subtitle != null) Text(subtitle, style = BzText.t.small, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        BzGlyph(Glyph.ArrowRight, size = 18.dp, color = Bz.Muted)
    }
}

@Composable
fun BzTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: String?,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            isError = error != null,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
            textStyle = BzText.t.data.copy(fontSize = BzText.t.body.fontSize),
            shape = RoundedCornerShape(BzDims.radius),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Bz.Leaf, unfocusedBorderColor = Bz.Line, cursorColor = Bz.Leaf,
                focusedLabelColor = Bz.Leaf, unfocusedLabelColor = Bz.Muted,
                focusedTextColor = Bz.Bark, unfocusedTextColor = Bz.Bark,
                errorBorderColor = Bz.Ember, errorLabelColor = Bz.Ember,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        if (error != null) Text(error, style = BzText.t.small.copy(color = Bz.Ember), modifier = Modifier.padding(top = 6.dp, start = 4.dp))
    }
}

/** Повідомлення-банер (попередження / помилка / інфо). */
@Composable
fun Banner(text: String, color: Color, glyph: Glyph = Glyph.Alert, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(BzDims.radius)
    Row(
        modifier.fillMaxWidth().clip(shape).background(color.copy(alpha = 0.10f))
            .border(BorderStroke(BzDims.hairline, color.copy(alpha = 0.35f)), shape).padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        BzGlyph(glyph, size = 18.dp, color = color)
        Spacer(Modifier.width(10.dp))
        Text(text, style = BzText.t.small.copy(color = Bz.Bark))
    }
}
