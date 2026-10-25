package com.v2ray.ang.ui.compose.satra

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.isActive

/** Latency colour scale shared by every ping surface. */
enum class PingGrade { Unknown, Great, Good, Slow, Failed }

fun pingGrade(ms: Long?): PingGrade = when {
    ms == null || ms == 0L -> PingGrade.Unknown
    ms < 0L -> PingGrade.Failed
    ms < 200L -> PingGrade.Great
    ms < 500L -> PingGrade.Good
    else -> PingGrade.Slow
}

@Composable
fun PingGrade.color(): Color = when (this) {
    PingGrade.Unknown -> Satra.colors.textTertiary
    PingGrade.Great -> Satra.colors.success
    PingGrade.Good -> Satra.colors.warning
    PingGrade.Slow -> Satra.colors.danger
    PingGrade.Failed -> Satra.colors.danger
}

@Composable
fun PingGrade.softColor(): Color = when (this) {
    PingGrade.Unknown -> Satra.colors.fill
    PingGrade.Great -> Satra.colors.successSoft
    PingGrade.Good -> Satra.colors.warningSoft
    PingGrade.Slow, PingGrade.Failed -> Satra.colors.dangerSoft
}

/** Compact latency text for list rows; digits count up to the new value. */
@Composable
fun PingPill(ms: Long?, modifier: Modifier = Modifier, testing: Boolean = false, failedLabel: String = "×") {
    val grade = pingGrade(ms)
    val color by animateColorAsState(grade.color(), tween(300), label = "pingPillColor")
    val shown by animateIntAsState(
        targetValue = if (ms != null && ms > 0) ms.toInt() else 0,
        animationSpec = tween(650), label = "pingPillValue",
    )
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr,
    ) {
        val pulse = if (testing) {
            val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "pingPulse")
            t.animateFloat(
                0.45f, 1f,
                androidx.compose.animation.core.infiniteRepeatable(tween(700), androidx.compose.animation.core.RepeatMode.Reverse),
                label = "pingPulseA",
            ).value
        } else 1f
        Row(
            modifier
                .clip(SatraShapes.Pill)
                .background(grade.softColor().copy(alpha = grade.softColor().alpha * pulse))
                .animateContentSize(tween(220))
                .padding(horizontal = 9.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (testing) {
                SatraSpinner(size = 11.dp, color = Satra.colors.textSecondary, strokeWidth = 1.6.dp)
                return@Row
            }
            Text(
                text = when (grade) {
                    PingGrade.Unknown -> "—"
                    PingGrade.Failed -> failedLabel
                    else -> String.format(currentLocale(), "%d", shown)
                },
                style = Satra.type.caption.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
                color = color,
                maxLines = 1,
            )
            if (grade != PingGrade.Unknown && grade != PingGrade.Failed) {
                Spacer(Modifier.width(2.dp))
                Text("ms", style = Satra.type.caption.copy(fontSize = 10.sp), color = color.copy(alpha = .75f), maxLines = 1)
            }
        }
    }
}

/**
 * Home latency button: concentric signal rings ripple out softly while testing; the result
 * lands as a colour-coded chip whose number counts up.
 */
@Composable
fun PingButton(
    ms: Long?,
    testing: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    idleLabel: String,
    testingLabel: String,
    failedLabel: String,
    contentDescription: String,
) {
    val grade = pingGrade(ms)
    val reduce = rememberReduceMotion()
    val clock = remember { Animatable(0f) }
    LaunchedEffect(testing, reduce) {
        if (testing && !reduce) {
            while (isActive) {
                clock.snapTo(0f)
                clock.animateTo(1f, tween(1500, easing = LinearEasing))
            }
        }
    }
    val bg by animateColorAsState(
        if (testing) Satra.colors.fill else grade.softColor(), tween(350), label = "pingBtnBg",
    )
    val fg by animateColorAsState(
        if (testing) Satra.colors.textSecondary else if (grade == PingGrade.Unknown) Satra.colors.textPrimary else grade.color(),
        tween(350), label = "pingBtnFg",
    )
    val shown by animateIntAsState(
        if (ms != null && ms > 0 && !testing) ms.toInt() else 0, tween(800), label = "pingBtnNumber",
    )
    val pop = rememberPop(if (testing) null else ms, from = 0.9f)
    val source = remember { MutableInteractionSource() }
    val haptics = rememberSatraHaptics()
    Row(
        modifier
            .heightIn(min = SatraSpace.MinTouch)
            .pressScale(source, 0.94f)
            .graphicsLayer { scaleX = pop; scaleY = pop }
            .clip(SatraShapes.Pill)
            .background(bg)
            .selectable(
                selected = false, enabled = enabled && !testing, role = Role.Button,
                interactionSource = source, indication = null,
                onClick = { haptics.tap(); onClick() },
            )
            .semantics { this.contentDescription = contentDescription }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Canvas(Modifier.size(26.dp)) {
            val center = Offset(size.width / 2, size.height / 2)
            val base = size.minDimension * 0.18f
            drawCircle(fg, radius = base * 0.55f, center = center)
            if (testing && !reduce) {
                for (i in 0 until 3) {
                    val p = (clock.value + i / 3f) % 1f
                    drawCircle(
                        fg.copy(alpha = (1f - p) * 0.75f),
                        radius = base + (size.minDimension / 2f - base) * p, center = center,
                        style = Stroke(1.6.dp.toPx()),
                    )
                }
            } else {
                drawCircle(fg.copy(alpha = 0.55f), radius = base * 1.9f, center = center, style = Stroke(1.6.dp.toPx()))
                drawCircle(fg.copy(alpha = 0.28f), radius = base * 2.9f, center = center, style = Stroke(1.6.dp.toPx()))
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = when {
                testing -> testingLabel
                grade == PingGrade.Unknown -> idleLabel
                grade == PingGrade.Failed -> failedLabel
                else -> ltr(String.format(currentLocale(), "%d ms", shown))
            },
            style = Satra.type.headline.copy(fontSize = 15.sp, fontFeatureSettings = "tnum"),
            color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}
