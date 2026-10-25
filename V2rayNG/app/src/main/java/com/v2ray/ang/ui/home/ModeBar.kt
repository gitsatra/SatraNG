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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.border
import androidx.compose.animation.animateContentSize
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

/** Set (e.g. from the notification's "Change mode") to open the picker sheet on Home. */
object ModeSheetRequest { val open = kotlinx.coroutines.flow.MutableStateFlow(false) }

/**
 * Home mode row: shows the current mode only. Nothing is switched from Home by a stray touch:
 * tapping opens a sheet where a mode is first previewed (what it changes) and only a deliberate
 * "Turn on" applies it. "Back to Normal" is the one-tap undo.
 */
@Composable
internal fun ModeBar(current: Mode, onPick: (Mode) -> Unit, modifier: Modifier = Modifier) {
    val c = Satra.colors
    var reveal by remember { mutableIntStateOf(-1) }
    val req by ModeSheetRequest.open.collectAsState()
    var sheet by remember { mutableStateOf(false) }
    LaunchedEffect(req) { if (req) { sheet = true; ModeSheetRequest.open.value = false } }
    val on = current != Mode.NORMAL
    Row(
        modifier.fillMaxWidth().height(60.dp).clip(SatraShapes.Large).background(c.surface)
            .semantics(mergeDescendants = true) { role = Role.Button }
            .satraClickable(pressedScale = 0.98f) { sheet = true }
            .padding(start = 14.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(if (on) c.accent.copy(alpha = 0.16f) else c.fill), contentAlignment = Alignment.Center) {
            Icon(modeIcon(current), null, tint = if (on) c.accent else c.textSecondary, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.mode_title), style = Satra.type.caption, color = c.textTertiary)
            Text(
                if (on) stringResource(R.string.mode_on, stringResource(current.title)) else stringResource(current.title),
                style = Satra.type.subhead.copy(fontWeight = FontWeight.SemiBold), color = if (on) c.accent else c.textPrimary,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (on) {
            Text(
                stringResource(R.string.mode_back_normal), style = Satra.type.footnote.copy(fontWeight = FontWeight.SemiBold), color = c.textPrimary,
                modifier = Modifier.clip(SatraShapes.Pill).background(c.fill).satraClickable { onPick(Mode.NORMAL); reveal = Mode.NORMAL.ordinal }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            stringResource(R.string.mode_change), style = Satra.type.footnote.copy(fontWeight = FontWeight.SemiBold), color = c.accent,
            modifier = Modifier.clip(SatraShapes.Pill).background(c.accent.copy(alpha = 0.12f)).padding(horizontal = 14.dp, vertical = 9.dp),
        )
    }
    if (sheet) ModeSheet(current, onDismiss = { sheet = false }) { m -> sheet = false; onPick(m); reveal = m.ordinal }
    if (reveal >= 0) ModeReveal(Mode.entries[reveal]) { reveal = -1 }
}

/** Picker: tap = preview only (highlight + what changes). Applying needs the "Turn on" button. */
@Composable
private fun ModeSheet(current: Mode, onDismiss: () -> Unit, onApply: (Mode) -> Unit) {
    val c = Satra.colors
    var sel by remember { mutableStateOf(current) }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.padding(16.dp).fillMaxWidth().clip(SatraShapes.XLarge).background(c.surfaceRaised).padding(18.dp),
        ) {
            Text(stringResource(R.string.mode_pick_title), style = Satra.type.title2, color = c.textPrimary)
            Text(stringResource(R.string.mode_pick_hint), style = Satra.type.footnote, color = c.textSecondary)
            Spacer(Modifier.height(12.dp))
            // Two columns of large, well-spaced tiles.
            Mode.entries.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { m ->
                        val isSel = m == sel
                        val bg by animateColorAsState(if (isSel) c.accent.copy(alpha = 0.14f) else c.fill, tween(200), label = "tile")
                        Row(
                            Modifier.weight(1f).height(58.dp).clip(SatraShapes.Medium).background(bg)
                                .then(if (isSel) Modifier.border(1.5.dp, c.accent, SatraShapes.Medium) else Modifier)
                                .semantics(mergeDescendants = true) { selected = isSel; role = Role.RadioButton }
                                .satraClickable(pressedScale = 0.97f) { sel = m }
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(modeIcon(m), null, tint = if (isSel) c.accent else c.textSecondary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(m.title), style = Satra.type.subhead.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (isSel) c.accent else c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (m == current) Text(stringResource(R.string.mode_current), style = Satra.type.caption, color = c.success)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            // Preview of the selected mode.
            Column(Modifier.fillMaxWidth().clip(SatraShapes.Medium).background(c.fill).padding(14.dp).animateContentSize(tween(220))) {
                Text(stringResource(sel.desc), style = Satra.type.footnote, color = c.textSecondary)
                val changes = remember(sel) { ModeEngine.changes(sel) }
                changes.forEach { ch ->
                    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(c.accent))
                        Spacer(Modifier.width(10.dp))
                        Text(if (ch.arg != null) stringResource(ch.label, ch.arg) else stringResource(ch.label), style = Satra.type.subhead, color = c.textPrimary)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                com.v2ray.ang.ui.compose.satra.SatraButton(stringResource(android.R.string.cancel), onClick = onDismiss,
                    modifier = Modifier.weight(1f), style = com.v2ray.ang.ui.compose.satra.SatraButtonStyle.Tinted)
                com.v2ray.ang.ui.compose.satra.SatraButton(
                    if (sel == current) stringResource(R.string.mode_current) else stringResource(R.string.mode_turn_on, stringResource(sel.title)),
                    onClick = { onApply(sel) }, enabled = sel != current, modifier = Modifier.weight(1.4f),
                )
            }
        }
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
