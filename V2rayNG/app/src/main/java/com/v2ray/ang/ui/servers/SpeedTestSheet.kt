package com.v2ray.ang.ui.servers

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.drawText
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.satra.Satra
import com.v2ray.ang.ui.compose.satra.SatraButton
import com.v2ray.ang.ui.compose.satra.SatraButtonStyle
import com.v2ray.ang.ui.compose.satra.SatraSheet
import com.v2ray.ang.ui.main.SpeedSession

/** Gauge position for a speed: log scale so 1–500 Mbps all read well. Pure; unit-tested. */
internal val GAUGE_MARKS = listOf(0.0, 5.0, 10.0, 25.0, 50.0, 100.0, 250.0, 500.0)

/** Stretched scale: the marks are evenly spaced, linear between neighbours. Pure; unit-tested. */
internal fun gaugeFraction(mbps: Double): Float {
    if (mbps <= 0.0) return 0f
    val m = GAUGE_MARKS
    if (mbps >= m.last()) return 1f
    val i = m.indexOfLast { it <= mbps }
    val part = (mbps - m[i]) / (m[i + 1] - m[i])
    return ((i + part) / (m.size - 1)).toFloat()
}

internal fun mbps(bps: Long): Double = bps * 8 / 1_000_000.0

/** Live download-only speed test: animated Mbps gauge, per-config progress arc, sorted results. */
@Composable
internal fun SpeedTestSheet(session: SpeedSession, names: Map<String, String>, onStop: () -> Unit, onClose: () -> Unit) {
    val c = Satra.colors
    val live = mbps(session.liveBps)
    // Spring k=90, c=19 (mass 1): dampingRatio = c / (2*sqrt(k)).
    val spring = androidx.compose.animation.core.spring<Float>(dampingRatio = 19f / (2f * kotlin.math.sqrt(90f)), stiffness = 90f)
    val needle by animateFloatAsState(gaugeFraction(live), spring, label = "gauge")
    val shown by animateFloatAsState(live.toFloat(), tween(380), label = "mbps")
    val progress by animateFloatAsState(session.fraction, tween(220), label = "prog")
    val done = session.results.size
    val total = session.queue.size
    SatraSheet(onDismiss = onClose) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.sx_speed_test), style = Satra.type.headline, color = c.textPrimary)
            Text(
                if (session.finished) stringResource(R.string.sx_speed_done, done) else stringResource(R.string.sx_speed_progress, (done + 1).coerceAtMost(total), total),
                style = Satra.type.footnote, color = c.textSecondary,
            )
            Spacer(Modifier.height(10.dp))
            Box(Modifier.size(220.dp, 170.dp), contentAlignment = Alignment.Center) {
                val track = androidx.compose.ui.graphics.Color(0xFF2A2A2E)
                val grad = if (session.finished) listOf(androidx.compose.ui.graphics.Color(0xFF4ADE80), androidx.compose.ui.graphics.Color(0xFF16A34A))
                    else listOf(androidx.compose.ui.graphics.Color(0xFFFF9A1A), androidx.compose.ui.graphics.Color(0xFFFF4D00))
                val brush = androidx.compose.ui.graphics.Brush.sweepGradient(grad + grad.first())
                val labelColor = c.textTertiary
                val measurer = androidx.compose.ui.text.rememberTextMeasurer()
                Canvas(Modifier.size(200.dp)) {
                    val start = 150f; val sweep = 240f
                    // Thin outer progress ring (this server's 8 s window).
                    val wp = 3.dp.toPx()
                    val sp = Size(size.width - wp, size.height - wp)
                    drawArc(track, start, sweep, false, Offset(wp / 2, wp / 2), sp, style = Stroke(wp, cap = StrokeCap.Round))
                    if (!session.finished) drawArc(brush, start, sweep * progress, false, Offset(wp / 2, wp / 2), sp, style = Stroke(wp, cap = StrokeCap.Round))
                    // Main gauge.
                    val w = 14.dp.toPx(); val inset = wp + 8.dp.toPx() + w / 2
                    val arcSize = Size(size.width - 2 * inset, size.height - 2 * inset)
                    drawArc(track, start, sweep, false, Offset(inset, inset), arcSize, style = Stroke(w, cap = StrokeCap.Round))
                    drawArc(brush, start, sweep * needle.coerceIn(0f, 1f), false, Offset(inset, inset), arcSize, style = Stroke(w, cap = StrokeCap.Round))
                    // Scale marks.
                    val cx = size.width / 2; val cy = size.height / 2; val rl = arcSize.width / 2 - w - 8.dp.toPx()
                    GAUGE_MARKS.forEachIndexed { i, mk ->
                        val a = Math.toRadians((start + sweep * i / (GAUGE_MARKS.size - 1)).toDouble())
                        val t = measurer.measure(mk.toInt().toString(), androidx.compose.ui.text.TextStyle(fontSize = 9.sp, color = labelColor))
                        drawText(t, topLeft = Offset((cx + rl * kotlin.math.cos(a)).toFloat() - t.size.width / 2, (cy + rl * kotlin.math.sin(a)).toFloat() - t.size.height / 2))
                    }
                    // Needle.
                    val na = Math.toRadians((start + sweep * needle.coerceIn(0f, 1f)).toDouble())
                    val nr = arcSize.width / 2 - w / 2
                    drawLine(grad.last(), Offset(cx, cy), Offset((cx + nr * kotlin.math.cos(na)).toFloat(), (cy + nr * kotlin.math.sin(na)).toFloat()), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
                    drawCircle(grad.last(), 5.dp.toPx(), Offset(cx, cy))
                }
                Column(Modifier.padding(top = 96.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("%.1f".format(shown), fontSize = 40.sp, color = c.textPrimary, style = Satra.type.title)
                    Text("Mbps", style = Satra.type.footnote, color = c.textSecondary)
                }
            }
            val cur = session.current
            Text(
                if (cur != null && !session.finished) "\u2068${names[cur] ?: cur}\u2069 · ↓" else "",
                style = Satra.type.subhead, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            val sorted = session.sorted
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 240.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed(sorted, key = { _, it -> it.first }) { i, (guid, bps) ->
                    Row(Modifier.fillMaxWidth().animateItem().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (i == 0 && bps > 0) "★" else "${i + 1}", style = Satra.type.footnote, color = c.textTertiary, modifier = Modifier.padding(end = 10.dp))
                        Text("\u2068${names[guid] ?: guid}\u2069", style = Satra.type.body, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(
                            if (bps > 0) "%.1f Mbps".format(mbps(bps)) else stringResource(R.string.sx_speed_failed_short),
                            style = Satra.type.subhead, color = if (bps > 0) (if (i == 0) c.success else c.textPrimary) else c.textTertiary,
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            if (session.finished) SatraButton(stringResource(R.string.sx_done), onClick = onClose, modifier = Modifier.fillMaxWidth())
            else SatraButton(stringResource(R.string.sx_speed_cancel), onClick = onStop, modifier = Modifier.fillMaxWidth(), style = SatraButtonStyle.Plain)
            Spacer(Modifier.height(12.dp))
        }
    }
}
