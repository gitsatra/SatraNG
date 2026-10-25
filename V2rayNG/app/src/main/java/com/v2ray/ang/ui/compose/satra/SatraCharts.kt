package com.v2ray.ang.ui.compose.satra

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.math.max

/**
 * Animated bar chart. Bars grow in with a stagger, the selected bar is highlighted and
 * shows its formatted value above it. Tapping a bar selects it.
 */
@Composable
fun SatraBarChart(
    values: List<Float>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    height: Dp = 170.dp,
    formatValue: (Float) -> String,
    labelEvery: Int = 1,
    accessibilitySummary: String? = null,
) {
    val c = Satra.colors
    val measurer = rememberTextMeasurer()
    val labelStyle = Satra.type.caption.copy(color = c.textTertiary)
    val valueStyle = Satra.type.caption.copy(color = c.textPrimary)
    var selected by rememberSaveable(values.size) { mutableIntStateOf(values.lastIndex) }
    val progress = remember { Animatable(0f) }
    val reduce = rememberReduceMotion()
    // The grow-in plays once per dataset shape (first appearance, or switching 7 ⇄ 30 days).
    // Live value changes only glide each bar to its new height; nothing restarts from zero.
    LaunchedEffect(values.size) {
        if (reduce) progress.snapTo(1f) else {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(900))
        }
    }
    val animated = values.mapIndexed { i, v ->
        key(i) { animateFloatAsState(v, if (reduce) snap() else SatraMotion.smooth(), label = "barValue").value }
    }
    val maxAnimated by animateFloatAsState(max(values.maxOrNull() ?: 0f, 1f), if (reduce) snap() else SatraMotion.smooth(), label = "barMax")
    val selFraction by animateFloatAsState(selected.toFloat(), SatraMotion.soft(), label = "selBar")
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics { if (accessibilitySummary != null) contentDescription = accessibilitySummary }
            .pointerInput(values.size) {
                detectTapGestures { pos ->
                    if (values.isEmpty()) return@detectTapGestures
                    val slot = size.width / values.size.toFloat()
                    selected = (pos.x / slot).toInt().coerceIn(0, values.lastIndex)
                }
            },
    ) {
        if (values.isEmpty()) return@Canvas
        val labelH = 22.dp.toPx()
        val topPad = 26.dp.toPx()
        val chartH = size.height - labelH - topPad
        val maxV = max(maxAnimated, 1f)
        val slot = size.width / values.size
        val barW = (slot * 0.56f).coerceAtMost(26.dp.toPx())
        // grid lines
        for (i in 0..2) {
            val y = topPad + chartH * i / 2f
            drawLine(c.separator, Offset(0f, y), Offset(size.width, y), strokeWidth = 0.6.dp.toPx())
        }
        values.forEachIndexed { i, target ->
            val v = animated.getOrElse(i) { target }
            val delay = i * 0.35f / values.size
            val p = ((progress.value - delay) / (1f - 0.35f)).coerceIn(0f, 1f)
            val eased = 1f - (1f - p) * (1f - p) * (1f - p)
            val h = (v / maxV) * chartH * eased
            val x = slot * i + (slot - barW) / 2
            val y = topPad + chartH - h
            val isSel = i == selected
            val minH = if (v > 0f) 4.dp.toPx() else 0f
            val barH = max(h, minH * eased)
            drawRoundRect(
                brush = if (isSel) Brush.verticalGradient(listOf(SatraBrand.OrangeLight, c.accent), startY = y, endY = y + barH)
                else Brush.verticalGradient(listOf(c.accent.copy(alpha = .38f), c.accent.copy(alpha = .22f)), startY = y, endY = y + barH),
                topLeft = Offset(x, topPad + chartH - barH),
                size = Size(barW, barH),
                cornerRadius = CornerRadius(barW * 0.32f, barW * 0.32f),
            )
            if (isSel || (i % labelEvery == 0 && kotlin.math.abs(i - selected) * 2 >= labelEvery)) {
                val label = labels.getOrNull(i).orEmpty()
                val layout = measurer.measure(label, labelStyle.copy(color = if (isSel) c.textPrimary else c.textTertiary), maxLines = 1)
                drawText(layout, topLeft = Offset(x + barW / 2 - layout.size.width / 2f, size.height - labelH + 4.dp.toPx()))
            }
        }
        // value bubble for selected bar
        val si = selected.coerceIn(0, values.lastIndex)
        val text = formatValue(values[si])
        val layout = measurer.measure(text, valueStyle, maxLines = 1)
        val cx = slot * selFraction + slot / 2
        val bx = (cx - layout.size.width / 2f).coerceIn(0f, size.width - layout.size.width)
        drawText(layout, topLeft = Offset(bx, 2.dp.toPx()))
    }
}

/** Smooth live line chart with gradient fill. */
@Composable
fun SatraSparkline(
    values: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = Satra.colors.accent,
    height: Dp = 56.dp,
) {
    val transition = rememberInfiniteTransition(label = "sparkPulse")
    val pulse by transition.animateFloat(
        0f, 1f, infiniteRepeatable(tween(1600), RepeatMode.Restart), label = "pulse",
    )
    Canvas(modifier.fillMaxWidth().height(height)) {
        if (values.size < 2) {
            drawLine(color.copy(alpha = .25f), Offset(0f, size.height - 2.dp.toPx()), Offset(size.width, size.height - 2.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
            return@Canvas
        }
        val maxV = max(values.max(), 1f) * 1.15f
        val stepX = size.width / (values.size - 1)
        val pad = 6.dp.toPx()
        fun yOf(v: Float) = pad + (size.height - pad * 2) * (1f - v / maxV)
        val path = Path()
        path.moveTo(0f, yOf(values[0]))
        for (i in 1 until values.size) {
            val x0 = stepX * (i - 1); val y0 = yOf(values[i - 1])
            val x1 = stepX * i; val y1 = yOf(values[i])
            val mx = (x0 + x1) / 2
            path.cubicTo(mx, y0, mx, y1, x1, y1)
        }
        val fill = Path().apply {
            addPath(path)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = .32f), color.copy(alpha = 0f))))
        drawPath(path, color, style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        val lx = size.width; val ly = yOf(values.last())
        drawCircle(color.copy(alpha = (1f - pulse) * .35f), radius = 4.dp.toPx() + pulse * 9.dp.toPx(), center = Offset(lx - 2.dp.toPx(), ly))
        drawCircle(color, radius = 3.5.dp.toPx(), center = Offset(lx - 2.dp.toPx(), ly))
    }
}

/** Circular gauge with animated sweep. */
@Composable
fun SatraRing(
    fraction: Float,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    stroke: Dp = 7.dp,
    color: Color = Satra.colors.accent,
    track: Color = Satra.colors.fill,
    content: @Composable () -> Unit = {},
) {
    val anim by animateFloatAsState(fraction.coerceIn(0f, 1f), SatraMotion.smooth(), label = "ring")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = stroke.toPx()
            val tl = Offset(w / 2, w / 2)
            val sz = Size(this.size.width - w, this.size.height - w)
            drawArc(track, 0f, 360f, false, tl, sz, style = Stroke(w))
            if (anim > 0.001f) {
                drawArc(color, -90f, 360f * anim, false, tl, sz, style = Stroke(w, cap = StrokeCap.Round))
            }
        }
        content()
    }
}

/** Slim animated progress bar. */
@Composable
fun SatraProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color = Satra.colors.accent,
    height: Dp = 8.dp,
) {
    val anim by animateFloatAsState(fraction.coerceIn(0f, 1f), SatraMotion.smooth(), label = "bar")
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(Satra.colors.fill),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(anim.coerceIn(0.0001f, 1f))
                .clip(CircleShape)
                .background(color),
        )
    }
}


/** Draws helper kept internal so tests can verify label thinning rules. */
internal fun labelStep(count: Int, maxLabels: Int = 8): Int =
    if (count <= maxLabels) 1 else kotlin.math.ceil(count / maxLabels.toDouble()).toInt()

