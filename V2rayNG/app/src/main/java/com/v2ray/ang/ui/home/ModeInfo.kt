package com.v2ray.ang.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.v2ray.ang.R
import com.v2ray.ang.handler.ModeEngine.Mode
import com.v2ray.ang.ui.compose.satra.Satra
import com.v2ray.ang.ui.compose.satra.SatraButton
import com.v2ray.ang.ui.compose.satra.SatraButtonStyle
import com.v2ray.ang.ui.compose.satra.SatraShapes

/** Mode identity — keep identical with the desktop app (MODES-INFO.md). */
internal fun modeColor(m: Mode): Color = when (m) {
    Mode.NORMAL -> Color(0xFF8E8E93)
    Mode.GAME -> Color(0xFFBF5AF2)
    Mode.SECURE -> Color(0xFF0A84FF)
    Mode.FORTRESS -> Color(0xFFFF453A)
    Mode.STREAM -> Color(0xFFFF375F)
    Mode.ECO -> Color(0xFF30D158)
}

fun modeEmoji(m: Mode): String = when (m) {
    Mode.NORMAL -> "🌐"; Mode.GAME -> "🎮"; Mode.SECURE -> "🛡️"
    Mode.FORTRESS -> "🏰"; Mode.STREAM -> "🎬"; Mode.ECO -> "🍃"
}

/** One setting the mode touches: name, the value the overlay sets, why. Mirrors ModeEngine overrides + JSON pass. */
internal data class ModeSetting(val name: Int, val value: Int, val why: Int, val raw: String? = null)

internal fun modeSettings(m: Mode): List<ModeSetting> {
    val secure = listOf(
        ModeSetting(R.string.mi_s_dns, 0, R.string.mi_w_sec_dns, "DoH 1.1.1.1, 8.8.8.8"),
        ModeSetting(R.string.mi_s_ipv6, R.string.mi_v_off, R.string.mi_w_sec_ipv6),
        ModeSetting(R.string.mi_s_quic, R.string.mi_v_blocked, R.string.mi_w_sec_quic),
        ModeSetting(R.string.mi_s_utls, R.string.mi_v_chrome, R.string.mi_w_sec_utls),
        ModeSetting(R.string.mi_s_sniff, R.string.mi_v_on, R.string.mi_w_sec_sniff),
    )
    return when (m) {
        Mode.NORMAL -> listOf(ModeSetting(R.string.mode_normal, R.string.mi_v_none, R.string.mi_w_none))
        Mode.GAME -> listOf(
            ModeSetting(R.string.mi_s_mux, R.string.mi_v_off, R.string.mi_w_game_mux),
            ModeSetting(R.string.mi_s_sniff, R.string.mi_v_routeonly, R.string.mi_w_game_sniff),
            ModeSetting(R.string.mi_s_strategy, 0, R.string.mi_w_game_strategy, "AsIs"),
            ModeSetting(R.string.mi_s_mtu, 0, R.string.mi_w_game_mtu, "1400"),
            ModeSetting(R.string.mi_s_quic, R.string.mi_v_allowed, R.string.mi_w_quic_allow),
        )
        Mode.SECURE -> secure
        Mode.FORTRESS -> secure + ModeSetting(R.string.mi_s_bypass, R.string.mi_v_removed, R.string.mi_w_fort_bypass)
        Mode.STREAM -> listOf(
            ModeSetting(R.string.mi_s_mux, R.string.mi_v_off, R.string.mi_w_st_mux),
            ModeSetting(R.string.mi_s_mtu, 0, R.string.mi_w_st_mtu, "1500"),
            ModeSetting(R.string.mi_s_sniff, R.string.mi_v_on, R.string.mi_w_st_sniff),
            ModeSetting(R.string.mi_s_quic, R.string.mi_v_allowed, R.string.mi_w_quic_allow),
        )
        Mode.ECO -> listOf(
            ModeSetting(R.string.mi_s_mux, R.string.mi_v_mux8, R.string.mi_w_eco_mux),
            ModeSetting(R.string.mi_s_ads, R.string.mi_v_on, R.string.mi_w_eco_ads),
            ModeSetting(R.string.mi_s_quic, R.string.mi_v_blocked, R.string.mi_w_eco_quic),
        )
    }
}

internal fun modePros(m: Mode): List<Int> = when (m) {
    Mode.NORMAL -> listOf(R.string.mi_p_normal_1)
    Mode.GAME -> listOf(R.string.mi_p_game_1, R.string.mi_p_game_2)
    Mode.SECURE -> listOf(R.string.mi_p_secure_1, R.string.mi_p_secure_2)
    Mode.FORTRESS -> listOf(R.string.mi_p_fortress_1, R.string.mi_p_fortress_2)
    Mode.STREAM -> listOf(R.string.mi_p_stream_1, R.string.mi_p_stream_2)
    Mode.ECO -> listOf(R.string.mi_p_eco_1, R.string.mi_p_eco_2)
}

internal fun modeCons(m: Mode): List<Int> = when (m) {
    Mode.NORMAL -> listOf(R.string.mi_c_normal_1)
    Mode.GAME -> listOf(R.string.mi_c_game_1, R.string.mi_c_game_2)
    Mode.SECURE -> listOf(R.string.mi_c_secure_1, R.string.mi_c_secure_2)
    Mode.FORTRESS -> listOf(R.string.mi_c_fortress_1, R.string.mi_c_fortress_2)
    Mode.STREAM -> listOf(R.string.mi_c_stream_1, R.string.mi_c_stream_2)
    Mode.ECO -> listOf(R.string.mi_c_eco_1, R.string.mi_c_eco_2)
}

/** Read-only info panel. Has no activation control at all. */
@Composable
internal fun ModeInfoDialog(m: Mode, onDismiss: () -> Unit) {
    val c = Satra.colors
    val tint = modeColor(m)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.padding(16.dp).fillMaxWidth().clip(SatraShapes.XLarge).background(c.surfaceRaised).padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(tint.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                    Text(modeEmoji(m), fontSize = 20.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(stringResource(m.title), style = Satra.type.title2, color = tint)
                    Text(stringResource(m.desc), style = Satra.type.footnote, color = c.textSecondary)
                }
            }
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                Spacer(Modifier.height(14.dp))
                Text(stringResource(R.string.mi_settings).uppercase(), style = Satra.type.caption, color = c.textTertiary)
                Spacer(Modifier.height(6.dp))
                Column(Modifier.fillMaxWidth().clip(SatraShapes.Medium).background(c.fill)) {
                    modeSettings(m).forEach { s ->
                        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(s.name), style = Satra.type.subhead.copy(fontWeight = FontWeight.SemiBold), color = c.textPrimary, modifier = Modifier.weight(1f))
                                Text(s.raw ?: stringResource(s.value), style = Satra.type.footnote.copy(fontWeight = FontWeight.SemiBold), color = tint,
                                    modifier = Modifier.clip(SatraShapes.Pill).background(tint.copy(alpha = 0.12f)).padding(horizontal = 10.dp, vertical = 4.dp))
                            }
                            Text(stringResource(s.why), style = Satra.type.footnote, color = c.textSecondary, modifier = Modifier.padding(top = 3.dp))
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ProCon(Modifier.weight(1f), R.string.mi_pros, "+", c.success, modePros(m))
                    ProCon(Modifier.weight(1f), R.string.mi_cons, "−", c.warning, modeCons(m))
                }
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.mi_overlay), style = Satra.type.caption, color = c.textTertiary)
            }
            Spacer(Modifier.height(12.dp))
            SatraButton(stringResource(R.string.mi_close), onClick = onDismiss, modifier = Modifier.fillMaxWidth(), style = SatraButtonStyle.Tinted)
        }
    }
}

@Composable
private fun ProCon(modifier: Modifier, title: Int, sign: String, tint: Color, items: List<Int>) {
    val c = Satra.colors
    Column(modifier.clip(SatraShapes.Medium).background(tint.copy(alpha = 0.08f)).padding(12.dp)) {
        Text(stringResource(title), style = Satra.type.footnote.copy(fontWeight = FontWeight.SemiBold), color = tint)
        items.forEach {
            Row(Modifier.padding(top = 6.dp)) {
                Text(sign, style = Satra.type.footnote.copy(fontWeight = FontWeight.Bold), color = tint)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(it), style = Satra.type.footnote, color = c.textPrimary)
            }
        }
    }
}
