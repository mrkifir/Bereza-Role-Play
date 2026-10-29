package ua.bereza.launcher.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ua.bereza.launcher.R
import ua.bereza.launcher.ui.theme.BerezaColors

/** Глобальний SnackbarHost для коротких повідомлень з будь-якого екрана. */
val LocalSnackbar = staticCompositionLocalOf<SnackbarHostState> { error("SnackbarHostState not provided") }

/** Масштаб «вдавлювання» при натисканні. */
@Composable
fun rememberPressScale(interaction: MutableInteractionSource, pressed: Float = 0.97f): Float {
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressed else 1f,
        animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing),
        label = "pressScale",
    )
    return scale
}

/** Каркас екрана: заголовок + прокручуваний список з обмеженням ширини для планшетів. */
@Composable
fun ScreenScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "header") {
                ScreenHeader(title = title, onBack = onBack, actions = actions)
            }
            content()
        }
    }
}

@Composable
fun ScreenHeader(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        actions()
    }
}

/** Основна картка: великі округлені кути, тонка рамка, анімація натискання. */
@Composable
fun BerezaCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    highlighted: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    val border = BorderStroke(1.dp, if (highlighted) BerezaColors.GreenDeep else BerezaColors.Outline)
    if (onClick != null) {
        val interaction = remember { MutableInteractionSource() }
        val scale = rememberPressScale(interaction)
        Surface(
            onClick = onClick,
            modifier = modifier.graphicsLayer { scaleX = scale; scaleY = scale },
            shape = shape,
            color = BerezaColors.Surface,
            border = border,
            interactionSource = interaction,
        ) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    } else {
        Surface(modifier = modifier, shape = shape, color = BerezaColors.Surface, border = border) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    }
}

/** Велика фірмова кнопка з градієнтом (напр. «ГРАТИ»). */
@Composable
fun PrimaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
    height: Dp = 60.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interaction, pressed = 0.96f)
    val shape = RoundedCornerShape(20.dp)
    val brush = if (enabled) {
        Brush.horizontalGradient(listOf(BerezaColors.GreenDeep, BerezaColors.Green))
    } else {
        Brush.horizontalGradient(listOf(BerezaColors.SurfaceHigh, BerezaColors.SurfaceHighest))
    }
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        interactionSource = interaction,
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = BerezaColors.OnGreen,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = BerezaColors.TextFaint,
        ),
        contentPadding = PaddingValues(horizontal = 20.dp),
        modifier = modifier
            .height(height)
            .scale(scale)
            .clip(shape)
            .background(brush, shape),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.5.dp,
                color = BerezaColors.OnGreen,
            )
        } else {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(10.dp))
            }
            Text(text, style = MaterialTheme.typography.labelLarge.copy(fontSize = 18.sp, letterSpacing = 3.sp))
        }
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interaction)
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if (enabled) BerezaColors.Outline else BerezaColors.OutlineSoft),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = BerezaColors.TextPrimary),
        modifier = modifier
            .height(48.dp)
            .scale(scale),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = BerezaColors.Green)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Індикатор статусу; для «онлайн» — м'яка пульсація. */
@Composable
fun StatusDot(color: Color, pulsing: Boolean, size: Dp = 10.dp) {
    val transition = rememberInfiniteTransition(label = "dot")
    val glow by transition.animateFloat(
        initialValue = 1f,
        targetValue = 2.2f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label = "glow",
    )
    Box(Modifier.size(size * 2.4f), contentAlignment = Alignment.Center) {
        if (pulsing) {
            Box(
                Modifier
                    .size(size)
                    .scale(glow)
                    .graphicsLayer { alpha = (2.2f - glow) / 1.2f * 0.35f }
                    .background(color, CircleShape)
            )
        }
        Box(
            Modifier
                .size(size)
                .background(color, CircleShape)
        )
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = BerezaColors.TextMuted,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

/** Рядок «назва — значення». */
@Composable
fun InfoRow(label: String, value: String, valueColor: Color = BerezaColors.TextPrimary) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = BerezaColors.TextMuted, modifier = Modifier.weight(0.45f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = valueColor, fontWeight = FontWeight.Medium, modifier = Modifier.weight(0.55f))
    }
}

/** Невелика плашка (тег, версія, статус). */
@Composable
fun Chip(text: String, color: Color = BerezaColors.Green, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

/** Іконка у круглій підкладці для карток-дій. */
@Composable
fun IconBadge(icon: ImageVector, tint: Color = BerezaColors.Green, size: Dp = 42.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(14.dp))
            .background(tint.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.52f))
    }
}

/** Фірмовий знак: стовбур берези з корою і зелений листок. Малюється кодом, без растрових файлів. */
@Composable
fun BerezaLogoMark(size: Dp = 56.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF1B2A22), Color(0xFF0B100D)),
                start = Offset(0f, 0f),
                end = Offset(w, h),
            ),
            cornerRadius = CornerRadius(w * 0.28f, w * 0.28f),
        )
        val trunkWidth = w * 0.16f
        val trunkX = w * 0.30f
        drawRoundRect(
            color = BerezaColors.Bark,
            topLeft = Offset(trunkX, h * 0.18f),
            size = Size(trunkWidth, h * 0.66f),
            cornerRadius = CornerRadius(trunkWidth / 2, trunkWidth / 2),
        )
        val marks = listOf(0.30f to 0.6f, 0.45f to 0.45f, 0.60f to 0.7f, 0.72f to 0.4f)
        marks.forEach { (y, length) ->
            drawRoundRect(
                color = Color(0xFF151A17),
                topLeft = Offset(trunkX, h * y),
                size = Size(trunkWidth * length, h * 0.035f),
                cornerRadius = CornerRadius(h * 0.02f, h * 0.02f),
            )
        }
        val leaf = Path().apply {
            moveTo(w * 0.52f, h * 0.62f)
            cubicTo(w * 0.50f, h * 0.36f, w * 0.70f, h * 0.22f, w * 0.84f, h * 0.20f)
            cubicTo(w * 0.84f, h * 0.42f, w * 0.72f, h * 0.60f, w * 0.52f, h * 0.62f)
            close()
        }
        drawPath(leaf, BerezaColors.Green)
        drawLine(
            color = Color(0xFF1E5C40),
            start = Offset(w * 0.53f, h * 0.61f),
            end = Offset(w * 0.80f, h * 0.24f),
            strokeWidth = w * 0.018f,
            cap = StrokeCap.Round,
        )
    }
}

/** Логотип + назва. */
@Composable
fun BerezaWordmark(markSize: Dp = 52.dp) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        BerezaLogoMark(size = markSize)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(
                text = "BEREZA",
                style = MaterialTheme.typography.displaySmall.copy(fontSize = 28.sp, letterSpacing = 4.sp),
                color = BerezaColors.TextPrimary,
            )
            Text(
                text = "ROLE PLAY",
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 6.sp),
                color = BerezaColors.Green,
            )
        }
    }
}

/** Порожній/інформаційний стан у картці. */
@Composable
fun EmptyNote(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = BerezaColors.TextMuted,
        modifier = modifier.padding(vertical = 4.dp),
    )
}

@Composable
fun VerticalGap(height: Dp) = Spacer(Modifier.height(height))
