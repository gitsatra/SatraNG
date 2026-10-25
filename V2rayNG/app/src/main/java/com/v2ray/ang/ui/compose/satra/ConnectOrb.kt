package com.v2ray.ang.ui.compose.satra

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.isActive

enum class OrbPhase { Idle, Connecting, Connected, Disconnecting, Failed }

/**
 * The hero VPN button.
 *
 *  Idle          neutral disc, power glyph
 *  Connecting    sweeping progress ring + expanding ripples, glyph breathes
 *  Connected     orange disc, ring closes, power glyph morphs into a check, particle burst,
 *                slow breathing halo
 *  Disconnecting ring winds down while the disc cools back to neutral
 *  Failed        horizontal shake, red ring flash, reject haptic
 */
@Composable
fun ConnectOrb(
    phase: OrbPhase,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    stateLabel: String,
    actionLabel: String,
) {
    val c = Satra.colors
    val reduce = rememberReduceMotion()
    val haptics = rememberSatraHaptics()
    val source = remember { MutableInteractionSource() }

    // Press feedback
    val pressedState by source.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        if (pressedState && !reduce) 0.93f else 1f, SatraMotion.press(), label = "orbPress",
    )

    // Phase driven values
    val fill by animateFloatAsState(
        if (phase == OrbPhase.Connected) 1f else 0f,
        if (reduce) tween(150) else tween(650), label = "orbFill",
    )
    val glyph by animateFloatAsState(
        if (phase == OrbPhase.Connected) 1f else 0f,
        if (reduce) tween(150) else SatraMotion.bouncy(), label = "orbGlyph",
    )
    val ringAlpha by animateFloatAsState(
        when (phase) {
            OrbPhase.Connecting, OrbPhase.Disconnecting, OrbPhase.Failed -> 1f
            else -> 0f
        },
        tween(350), label = "orbRingAlpha",
    )
    val failMix by animateFloatAsState(if (phase == OrbPhase.Failed) 1f else 0f, tween(250), label = "orbFail")
    // Orange while idle/connecting, easing to green once the tunnel is up.
    val okMix by animateFloatAsState(
        if (phase == OrbPhase.Connected) 1f else 0f,
        if (reduce) tween(150) else tween(900, delayMillis = 120), label = "orbOk",
    )

    val sweep = remember { Animatable(0f) }
    val spin = remember { Animatable(0f) }
    val clock = remember { Animatable(0f) }
    val burst = remember { Animatable(1f) }
    val shake = remember { Animatable(0f) }
    val closeRing = remember { Animatable(0f) }

    LaunchedEffect(phase, reduce) {
        when (phase) {
            OrbPhase.Connecting -> {
                closeRing.snapTo(0f)
                sweep.animateTo(if (reduce) 270f else 290f, tween(if (reduce) 100 else 700))
            }
            OrbPhase.Disconnecting -> sweep.animateTo(60f, tween(500))
            OrbPhase.Connected -> {
                haptics.confirm()
                if (!reduce) {
                    burst.snapTo(0f)
                    burst.animateTo(1f, tween(1000))
                }
            }
            OrbPhase.Failed -> {
                haptics.reject()
                if (!reduce) {
                    val steps = listOf(-16f, 14f, -10f, 7f, -3f, 0f)
                    for (s in steps) shake.animateTo(s, tween(70))
                }
            }
            OrbPhase.Idle -> {
                sweep.snapTo(0f)
            }
        }
    }
    // Spinner: only runs while a transition is in flight.
    LaunchedEffect(phase, reduce) {
        if (reduce) return@LaunchedEffect
        if (phase == OrbPhase.Connecting || phase == OrbPhase.Disconnecting) {
            while (isActive) {
                spin.snapTo(0f)
                spin.animateTo(360f, tween(if (phase == OrbPhase.Connecting) 1150 else 1500, easing = LinearEasing))
            }
        }
    }
    // Slow clock: ripples while connecting, breathing halo while connected.
    LaunchedEffect(phase, reduce) {
        if (reduce) return@LaunchedEffect
        if (phase == OrbPhase.Connecting || phase == OrbPhase.Connected) {
            val period = if (phase == OrbPhase.Connecting) 1900 else 3200
            // Connected: a few breaths to celebrate, then the halo rests as a still glow.
            // An endless 60-120 fps halo kept the RenderThread at ~50% CPU for nothing.
            var cycles = 0
            while (isActive && (phase == OrbPhase.Connecting || cycles++ < 3)) {
                clock.snapTo(0f)
                clock.animateTo(1f, tween(period, easing = LinearEasing))
            }
            if (phase == OrbPhase.Connected) {
                clock.snapTo(0f)
                clock.animateTo(0.25f, tween(800, easing = LinearEasing))
            }
        }
    }

    val colorOff = if (c.dark) Color(0xFF1B1B1F) else Color.White
    val colorOffEdge = if (c.dark) Color(0xFF2A2A30) else Color(0xFFE6E6EC)
    val glyphOff = if (c.dark) Color(0xFFB4B4BD) else Color(0xFF5B5B66)

    Box(
        modifier
            .graphicsLayer { translationX = shake.value.dp.toPx() }
            .semantics {
                contentDescription = actionLabel
                stateDescription = stateLabel
                role = Role.Button
            }
            .selectable(
                selected = phase == OrbPhase.Connected,
                role = Role.Button,
                interactionSource = source, indication = null,
                onClick = { haptics.heavy(); onClick() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer { scaleX = pressScale; scaleY = pressScale },
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val r = size.minDimension * 0.30f
            val accent = lerp(c.accent, SatraBrand.Green, okMix)
            val accentLight = lerp(SatraBrand.OrangeLight, SatraBrand.GreenLight, okMix)
            val accentDeep = lerp(SatraBrand.OrangeDeep, SatraBrand.GreenDeep, okMix)
            val danger = c.danger
            val ringColor = lerp(accent, danger, failMix)

            // 1. Ambient glow under the disc
            if (fill > 0.01f) {
                drawCircle(
                    Brush.radialGradient(
                        listOf(accent.copy(alpha = 0.40f * fill), accent.copy(alpha = 0f)),
                        center = center, radius = r * 1.9f,
                    ),
                    radius = r * 1.9f, center = center,
                )
            } else if (phase == OrbPhase.Failed) {
                drawCircle(
                    Brush.radialGradient(
                        listOf(danger.copy(alpha = 0.22f * failMix), danger.copy(alpha = 0f)),
                        center = center, radius = r * 1.7f,
                    ),
                    radius = r * 1.7f, center = center,
                )
            }

            // 2. Ripples while connecting / breathing halos while connected
            val t = clock.value
            if (phase == OrbPhase.Connecting && !reduce) {
                for (i in 0 until 3) {
                    val p = (t + i / 3f) % 1f
                    val rr = r * (1.08f + 0.62f * p)
                    drawCircle(
                        color = accent.copy(alpha = (1f - p) * 0.34f),
                        radius = rr, center = center,
                        style = Stroke(width = (1.5f + (1f - p) * 2f).dp.toPx()),
                    )
                }
            }
            if (phase == OrbPhase.Connected && !reduce) {
                for (i in 0 until 2) {
                    val p = (t + i * 0.5f) % 1f
                    val rr = r * (1.0f + 0.5f * p)
                    drawCircle(
                        color = accent.copy(alpha = (1f - p) * (1f - p) * 0.30f),
                        radius = rr, center = center,
                    )
                }
            }

            // 3. Disc
            val discTop = lerp(colorOff, accentLight, fill)
            val discBottom = lerp(colorOff, lerp(accent, accentDeep, 0.55f), fill)
            drawCircle(
                Brush.linearGradient(
                    listOf(discTop, discBottom),
                    start = Offset(center.x - r, center.y - r),
                    end = Offset(center.x + r, center.y + r),
                ),
                radius = r, center = center,
            )
            drawCircle(
                color = lerp(lerp(colorOffEdge, danger, failMix * 0.9f), Color.White.copy(alpha = 0.25f), fill),
                radius = r - 0.5.dp.toPx(), center = center,
                style = Stroke(width = (1.2f + failMix).dp.toPx()),
            )
            // specular highlight
            if (fill > 0.01f) {
                drawArc(
                    color = Color.White.copy(alpha = 0.28f * fill),
                    startAngle = 200f, sweepAngle = 70f, useCenter = false,
                    topLeft = Offset(center.x - r + 5.dp.toPx(), center.y - r + 5.dp.toPx()),
                    size = androidx.compose.ui.geometry.Size((r - 5.dp.toPx()) * 2, (r - 5.dp.toPx()) * 2),
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                )
            }

            // 4. Progress ring
            val ringR = r + 13.dp.toPx()
            val ringW = 4.dp.toPx()
            if (ringAlpha > 0.01f) {
                drawCircle(
                    color = ringColor.copy(alpha = 0.14f * ringAlpha),
                    radius = ringR, center = center, style = Stroke(ringW),
                )
                val sw = if (phase == OrbPhase.Failed) 360f else sweep.value
                val start = if (phase == OrbPhase.Disconnecting) -spin.value else spin.value
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(ringColor.copy(alpha = 0.1f), ringColor, ringColor.copy(alpha = 0.1f)),
                        center = center,
                    ),
                    startAngle = if (phase == OrbPhase.Failed) -90f else start,
                    sweepAngle = sw, useCenter = false,
                    topLeft = Offset(center.x - ringR, center.y - ringR),
                    size = androidx.compose.ui.geometry.Size(ringR * 2, ringR * 2),
                    alpha = ringAlpha,
                    style = Stroke(ringW, cap = StrokeCap.Round),
                )
            }

            // 5. Success burst
            val b = burst.value
            if (b < 1f) {
                val shock = b
                drawCircle(
                    color = accent.copy(alpha = (1f - shock) * 0.55f),
                    radius = r * (1.05f + 0.9f * shock), center = center,
                    style = Stroke(width = (4f * (1f - shock) + 1f).dp.toPx()),
                )
                val n = 16
                for (i in 0 until n) {
                    val ang = (i.toFloat() / n) * 2f * PI.toFloat() + (i % 2) * 0.12f
                    val dist = r * (1.15f + (0.45f + 0.35f * (i % 3) / 2f) * shock)
                    val px = center.x + cos(ang) * dist
                    val py = center.y + sin(ang) * dist
                    val rad = (3.2f - 2.2f * shock + (i % 3)).coerceAtLeast(0.6f).dp.toPx()
                    drawCircle(
                        color = (if (i % 3 == 0) accentLight else accent).copy(alpha = (1f - shock)),
                        radius = rad, center = Offset(px, py),
                    )
                }
            }

            // 6. Glyph
            val glyphColor = lerp(lerp(glyphOff, danger, failMix), Color.White, fill)
            drawGlyph(center, r * 0.46f, glyph, glyphColor, (r * 0.13f))
        }
    }
}

/** Power symbol that closes into a ring and draws a check mark as [t] goes 0 → 1. */
private fun DrawScope.drawGlyph(center: Offset, radius: Float, t: Float, color: Color, strokeW: Float) {
    val tt = t.coerceIn(0f, 1f)
    val stroke = Stroke(strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
    // Arc: gap shrinks as t -> 1
    val gap = 60f * (1f - tt)
    val start = -90f + gap / 2f
    val sweep = 360f - gap
    drawArc(
        color = color, startAngle = start, sweepAngle = sweep, useCenter = false,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
        style = stroke,
    )
    // Power stem fades / shortens
    val stemAlpha = (1f - tt * 2.2f).coerceIn(0f, 1f)
    if (stemAlpha > 0f) {
        val top = center.y - radius * 1.12f
        val bottom = center.y - radius * (0.05f + 0.5f * tt)
        drawLine(color.copy(alpha = color.alpha * stemAlpha), Offset(center.x, top), Offset(center.x, bottom), strokeW, StrokeCap.Round)
    }
    // Check draws in after t > .35
    val cp = ((tt - 0.35f) / 0.65f).coerceIn(0f, 1f)
    if (cp > 0f) {
        val path = Path().apply {
            moveTo(center.x - radius * 0.42f, center.y + radius * 0.04f)
            lineTo(center.x - radius * 0.1f, center.y + radius * 0.36f)
            lineTo(center.x + radius * 0.46f, center.y - radius * 0.30f)
        }
        val measure = PathMeasure().apply { setPath(path, false) }
        val seg = Path()
        measure.getSegment(0f, measure.length * cp, seg, true)
        drawPath(seg, color, style = stroke)
    }
}

/** Convenience size helper. */
val OrbSize: Dp = 280.dp
