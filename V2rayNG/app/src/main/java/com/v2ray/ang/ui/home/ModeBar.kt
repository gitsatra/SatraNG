package com.v2ray.ang.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.v2ray.ang.R
import com.v2ray.ang.handler.ModeEngine
import com.v2ray.ang.handler.ModeEngine.Mode
import com.v2ray.ang.ui.compose.satra.Satra
import com.v2ray.ang.ui.compose.satra.SatraIcons
import com.v2ray.ang.ui.compose.satra.SatraShapes
import com.v2ray.ang.ui.compose.satra.satraClickable
import kotlinx.coroutines.delay

internal fun modeIcon(m: Mode): ImageVector = when (m) {
    Mode.NORMAL -> SatraIcons.Globe
    Mode.GAME -> SatraIcons.Flash
    Mode.SECURE -> SatraIcons.ShieldCheck
    Mode.FORTRESS -> SatraIcons.Lock
    Mode.STREAM -> SatraIcons.Play
    Mode.ECO -> SatraIcons.Heart
}

/**
 * Home mode picker: one calm capsule of six segments. Picking a mode plays a short reveal of
 * what it changes (ticks appear one by one), then the line "<Mode> mode is on" stays above.
 */
@Composable
internal fun ModeBar(current: Mode, onPick: (Mode) -> Unit, modifier: Modifier = Modifier) {
    val c = Satra.colors
    var reveal by remember { mutableIntStateOf(-1) } // ordinal being revealed, -1 = none
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.mode_title), style = Satra.type.caption.copy(fontWeight = FontWeight.SemiBold), color = c.textSecondary)
            Spacer(Modifier.weight(1f))
            val name = stringResource(current.title)
            Text(
                if (current == Mode.NORMAL) stringResource(current.desc) else stringResource(R.string.mode_on, name),
                style = Satra.type.caption.copy(fontWeight = if (current == Mode.NORMAL) FontWeight.Normal else FontWeight.SemiBold),
                color = if (current == Mode.NORMAL) c.textTertiary else c.accent,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            Modifier.fillMaxWidth().clip(SatraShapes.Large).background(c.surface).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Mode.entries.forEach { m ->
                val sel = m == current
                val bg by animateColorAsState(if (sel) c.accent.copy(alpha = 0.16f) else c.surface, tween(260), label = "modeBg")
                val fg by animateColorAsState(if (sel) c.accent else c.textSecondary, tween(260), label = "modeFg")
                val label = stringResource(m.title)
                Column(
                    Modifier.weight(1f).clip(SatraShapes.Medium).background(bg)
                        .semantics(mergeDescendants = true) { selected = sel; role = Role.RadioButton }
                        .satraClickable { if (!sel) { onPick(m); reveal = m.ordinal } }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(modeIcon(m), null, tint = fg, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.height(2.dp))
                    var scale by remember(label) { androidx.compose.runtime.mutableFloatStateOf(1f) }
                    val base = Satra.type.caption
                    Text(
                        label, style = base.copy(fontSize = base.fontSize * scale), color = fg, maxLines = 1, softWrap = false,
                        textAlign = TextAlign.Center,
                        onTextLayout = { if (it.hasVisualOverflow && scale > 0.7f) scale *= 0.92f },
                    )
                }
            }
        }
    }
    if (reveal >= 0) {
        ModeReveal(Mode.entries[reveal]) { reveal = -1 }
    }
}

/** Floating card: "Applying Game…" then each change ticks in (180 ms apart), then "Game mode is on". */
@Composable
private fun ModeReveal(mode: Mode, onDone: () -> Unit) {
    val c = Satra.colors
    val changes = remember(mode) { ModeEngine.changes(mode) }
    var shown by remember(mode) { mutableIntStateOf(0) }
    var visible by remember(mode) { androidx.compose.runtime.mutableStateOf(false) }
    LaunchedEffect(mode) {
        visible = true
        delay(260)
        for (i in 1..changes.size) { shown = i; delay(180) }
        delay(1300)
        visible = false
        delay(260)
        onDone()
    }
    Popup(alignment = Alignment.Center, onDismissRequest = onDone, properties = PopupProperties(focusable = false)) {
        AnimatedVisibility(visible, enter = fadeIn(tween(220)) + scaleIn(tween(260), initialScale = 0.92f), exit = fadeOut(tween(220)) + scaleOut(tween(220), targetScale = 0.96f)) {
            Column(
                Modifier.padding(32.dp).width(280.dp).clip(SatraShapes.Large).background(c.surfaceRaised).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(c.accent.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                    Icon(modeIcon(mode), null, tint = c.accent, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.height(10.dp))
                val name = stringResource(mode.title)
                Text(
                    if (shown >= changes.size) stringResource(R.string.mode_on, name) else stringResource(R.string.mode_applying, name),
                    style = Satra.type.headline, color = c.textPrimary, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(10.dp))
                changes.forEachIndexed { i, ch ->
                    AnimatedVisibility(i < shown, enter = fadeIn(tween(200)) + slideInVertically(tween(220)) { it / 3 }) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(18.dp).clip(CircleShape).background(c.success.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                                Icon(SatraIcons.Check, null, tint = c.success, modifier = Modifier.size(12.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(
                                if (ch.arg != null) stringResource(ch.label, ch.arg) else stringResource(ch.label),
                                style = Satra.type.subhead, color = c.textPrimary,
                            )
                        }
                    }
                }
            }
        }
    }
}
