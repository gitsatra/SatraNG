package com.v2ray.ang.ui.security

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.handler.SecurityCheck
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.satra.InsetGroup
import com.v2ray.ang.ui.compose.satra.Satra
import com.v2ray.ang.ui.compose.satra.SatraButton
import com.v2ray.ang.ui.compose.satra.SatraIcons
import com.v2ray.ang.ui.compose.satra.SatraRow
import com.v2ray.ang.ui.compose.satra.SatraScrollPage
import com.v2ray.ang.ui.compose.satra.SatraSpinner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

/** Connection Security Check: exit IP, DNS/IPv6/WebRTC leaks, encryption and kill switch, with a score. */
class SecurityCheckActivity : BaseComponentActivity() {

    @Composable
    override fun ScreenContent() {
        SecurityCheckScreen(
            onBack = { finish() },
            onFix = { fix -> openFix(fix) },
        )
    }

    private fun openFix(fix: SecurityCheck.Fix) {
        try {
            when (fix) {
                SecurityCheck.Fix.EnableKillSwitch, SecurityCheck.Fix.EnableIpv6Tunnel -> startActivity(Intent(Settings.ACTION_VPN_SETTINGS))
                else -> finish() // back to the app: connect / choose a server there
            }
        } catch (_: Exception) {
        }
    }
}

/** The core is up when its local HTTP inbound answers (works for VPN and Proxy-only modes). */
private fun coreListening(): Boolean = try {
    Socket().use { it.connect(InetSocketAddress("127.0.0.1", SettingsManager.getHttpPort()), 400); true }
} catch (_: Exception) {
    false
}

/** Per-row phase in the live checklist. */
internal enum class RowPhase { Pending, Running, Done }

/** Phase of [item] given what has been measured and revealed so far. Pure; unit-tested. */
internal fun rowPhase(item: SecurityCheck.Item, measured: Set<SecurityCheck.Item>, revealed: Set<SecurityCheck.Item>, running: Boolean): RowPhase = when {
    item in revealed -> RowPhase.Done
    !running -> RowPhase.Pending
    item == SecurityCheck.ORDER.firstOrNull { it !in measured } -> RowPhase.Running
    else -> RowPhase.Pending
}

@Composable
private fun SecurityCheckScreen(onBack: () -> Unit, onFix: (SecurityCheck.Fix) -> Unit) {
    val c = Satra.colors
    var report by remember { mutableStateOf<SecurityCheck.Report?>(null) }
    var running by remember { mutableStateOf(false) }
    var runId by remember { mutableIntStateOf(0) }
    var measured by remember { mutableStateOf(emptySet<SecurityCheck.Item>()) }
    var revealed by remember { mutableStateOf(emptySet<SecurityCheck.Item>()) }
    var applied by remember { mutableStateOf(emptySet<SecurityCheck.Fix>()) }
    var offline by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(runId) {
        report = null; measured = emptySet(); revealed = emptySet()
        // Needs the VPN: when disconnected, say so instead of running a meaningless scan.
        offline = !withContext(Dispatchers.IO) { coreListening() }
        if (offline) { running = false; return@LaunchedEffect }
        running = true
        val r = withContext(Dispatchers.IO) {
            SecurityCheck.run(connected = true) { item -> scope.launch { measured = measured + item } }
        }
        report = r
        // Reveal one by one so the ring builds up with each result.
        for (item in SecurityCheck.ORDER) {
            measured = measured + item
            kotlinx.coroutines.delay(260)
            revealed = revealed + item
        }
        running = false
    }
    val r = report
    val earned = r?.findings?.filter { it.item in revealed }?.sumOf { SecurityCheck.points(it) } ?: 0
    SatraScrollPage(title = stringResource(R.string.sx_sec_title), onBack = onBack, subtitle = stringResource(R.string.sx_sec_sub)) {
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            ScoreRing(earned, running, !offline && r?.connected != false, revealed.size / SecurityCheck.ORDER.size.toFloat())
            Spacer(Modifier.height(10.dp))
            Text(
                when {
                    running -> stringResource(R.string.sx_sec_running)
                    r == null || !r.connected -> stringResource(R.string.sx_sec_connect_first)
                    r.score >= 85 -> stringResource(R.string.sx_sec_great)
                    r.score >= 60 -> stringResource(R.string.sx_sec_ok)
                    else -> stringResource(R.string.sx_sec_weak)
                },
                style = Satra.type.title2, color = c.textPrimary, textAlign = TextAlign.Center,
            )
        }
        if (offline) {
            Text(
                stringResource(R.string.sx_sec_connect_first_note), style = Satra.type.body, color = c.textSecondary, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 8.dp),
            )
        } else InsetGroup {
            SecurityCheck.ORDER.forEach { item ->
                row {
                    val f = r?.findings?.firstOrNull { it.item == item }
                    LiveRow(item, rowPhase(item, measured, revealed, running), f, r?.facts)
                }
            }
        }
        if (r != null && !running && r.fixes.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            InsetGroup(header = stringResource(R.string.sx_sec_fixes)) {
                r.fixes.forEach { fix ->
                    row {
                        val oneTap = SecurityCheck.isOneTap(fix)
                        val done = fix in applied
                        SatraRow(
                            fixTitle(fix),
                            subtitle = if (done) stringResource(R.string.sx_sec_applied) else null,
                            icon = if (done) SatraIcons.Check else SatraIcons.Warning,
                            iconColor = if (done) c.success else c.warning,
                            chevron = !oneTap && fix != SecurityCheck.Fix.UseSecureConfig,
                            trailing = if (oneTap && !done) ({
                                SatraButton(stringResource(R.string.sx_sec_apply), onClick = {
                                    scope.launch {
                                        if (withContext(Dispatchers.IO) { SecurityCheck.applyFix(fix) }) applied = applied + fix
                                    }
                                }, compact = true)
                            }) else null,
                            onClick = if (!oneTap) ({ onFix(fix) }) else null,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            SatraButton(
                stringResource(if (offline) R.string.sx_sec_try_again else R.string.sx_sec_run_again), onClick = { runId++ },
                modifier = Modifier.fillMaxWidth(), loading = running, enabled = !running,
            )
        }
        Text(
            stringResource(R.string.sx_sec_footer), style = Satra.type.footnote, color = c.textSecondary,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
        )
    }
}

@Composable
private fun ScoreRing(score: Int, running: Boolean, connected: Boolean, progress: Float) {
    val c = Satra.colors
    val anim by androidx.compose.animation.core.animateFloatAsState(score / 100f, tween(380), label = "ring")
    val track by androidx.compose.animation.core.animateFloatAsState(progress, tween(380), label = "track")
    val color = when {
        !connected -> c.textTertiary
        running -> c.accent
        score >= 85 -> c.success
        score >= 60 -> c.warning
        else -> c.danger
    }
    val tone by androidx.compose.animation.animateColorAsState(color, tween(400), label = "ringTone")
    Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(150.dp)) {
            val w = 12.dp.toPx()
            drawArc(tone.copy(alpha = 0.12f), 0f, 360f, false, style = Stroke(w))
            // Checked share (faint), then points earned (solid) on top.
            drawArc(tone.copy(alpha = 0.28f), -90f, 360f * track, false, style = Stroke(w, cap = StrokeCap.Round))
            drawArc(tone, -90f, 360f * anim, false, style = Stroke(w, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(if (connected) "${(anim * 100).toInt()}" else "—", style = Satra.type.largeTitle.copy(fontWeight = FontWeight.Bold), color = tone)
            Text(stringResource(R.string.sx_sec_score), style = Satra.type.footnote, color = c.textSecondary)
        }
    }
}

@Composable
private fun LiveRow(item: SecurityCheck.Item, phase: RowPhase, f: SecurityCheck.Finding?, facts: SecurityCheck.Facts?) {
    val c = Satra.colors
    var open by remember { mutableStateOf(false) }
    val info = SecurityCheck.maxPoints(item) == 0
    val status = if (phase == RowPhase.Done) f?.status?.let { if (info && it != SecurityCheck.Status.Pass) SecurityCheck.Status.Unknown else it } else null
    val tint = when (status) {
        SecurityCheck.Status.Pass -> c.success
        SecurityCheck.Status.Warn -> c.warning
        SecurityCheck.Status.Fail -> c.danger
        SecurityCheck.Status.Unknown -> c.textTertiary
        null -> if (phase == RowPhase.Running) c.accent else c.textTertiary
    }
    val (title, icon) = itemTitle(item)
    Column(Modifier.fillMaxWidth().animateContentSize(tween(220))) {
        SatraRow(
            title,
            subtitle = when {
                phase == RowPhase.Running -> stringResource(R.string.sx_sec_live_step, title)
                status != null && f != null && SecurityCheck.maxPoints(item) == 0 -> stringResource(R.string.sx_sec_info_only)
                status != null && f != null -> stringResource(R.string.sx_sec_points, SecurityCheck.points(f), SecurityCheck.maxPoints(item))
                else -> null
            },
            icon = icon, iconColor = tint,
            onClick = if (phase == RowPhase.Done) ({ open = !open }) else null,
            trailing = {
                androidx.compose.animation.AnimatedContent(status to phase, label = "st") { (st, ph) ->
                    when {
                        ph == RowPhase.Running -> SatraSpinner(size = 18.dp, color = c.accent, strokeWidth = 2.dp)
                        st == null -> Box(Modifier.size(10.dp).clip(CircleShape).background(c.fillStrong))
                        else -> Icon(
                            when (st) {
                                SecurityCheck.Status.Pass -> SatraIcons.Check
                                else -> SatraIcons.Warning
                            },
                            statusLabel(st), tint = tint, modifier = Modifier.size(20.dp),
                        )
                    }
                }
            },
        )
        if (open && f != null) DetailBlock(item, f, facts)
    }
}

@Composable
private fun DetailBlock(item: SecurityCheck.Item, f: SecurityCheck.Finding, facts: SecurityCheck.Facts?) {
    val c = Satra.colors
    val none = stringResource(R.string.sx_sec_r_none)
    val on = stringResource(R.string.sx_sec_on); val off = stringResource(R.string.sx_sec_off)
    fun yn(b: Boolean?) = when (b) { true -> on; false -> off; null -> "?" }
    val vpn = facts?.vpnMode != false
    val checked = stringResource(
        when (item) {
            SecurityCheck.Item.ExitIp -> R.string.sx_sec_d_exit
            SecurityCheck.Item.DnsLeak -> R.string.sx_sec_d_dns
            SecurityCheck.Item.Ipv6 -> R.string.sx_sec_d_ipv6
            SecurityCheck.Item.WebRtc -> R.string.sx_sec_d_webrtc
            SecurityCheck.Item.Encryption -> R.string.sx_sec_d_tls
            SecurityCheck.Item.KillSwitch -> R.string.sx_sec_d_ks
        },
    )
    val result = when (item) {
        SecurityCheck.Item.ExitIp -> facts?.exitIp?.let { stringResource(R.string.sx_sec_r_exit, "\u2066$it\u2069", facts.exitCountry ?: "?") } ?: none
        SecurityCheck.Item.DnsLeak -> facts?.resolverIp?.let {
            stringResource(R.string.sx_sec_r_dns, "\u2066$it\u2069", facts.resolverGeo ?: facts.resolverCountry ?: "?", facts.exitCountry ?: "?")
        } ?: none
        SecurityCheck.Item.Ipv6 -> facts?.ipv6Exit?.let { stringResource(R.string.sx_sec_r_ipv6, "\u2066$it\u2069") } ?: stringResource(R.string.sx_sec_r_ipv6_none)
        SecurityCheck.Item.WebRtc -> stringResource(if (vpn) R.string.sx_sec_r_webrtc else R.string.sx_sec_r_webrtc_proxy)
        SecurityCheck.Item.Encryption -> stringResource(
            R.string.sx_sec_r_tls,
            facts?.configType?.name ?: "?",
            facts?.security?.ifBlank { null }?.uppercase() ?: stringResource(R.string.sx_sec_none),
            facts?.fingerprint?.ifBlank { null } ?: stringResource(R.string.sx_sec_fp_default),
            yn(facts?.insecure),
        )
        SecurityCheck.Item.KillSwitch -> stringResource(R.string.sx_sec_r_ks, yn(facts?.alwaysOn), yn(facts?.lockdown))
    }
    val why = stringResource(whyRes(item, f.status, vpn))
    Column(Modifier.fillMaxWidth().padding(start = 60.dp, end = 16.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        DetailLine(stringResource(R.string.sx_sec_checked), checked)
        DetailLine(stringResource(R.string.sx_sec_result), result)
        DetailLine(stringResource(R.string.sx_sec_why), why)
        Text(
            if (SecurityCheck.maxPoints(item) == 0) stringResource(R.string.sx_sec_info_only)
            else stringResource(R.string.sx_sec_points, SecurityCheck.points(f), SecurityCheck.maxPoints(item)),
            style = Satra.type.caption.copy(fontWeight = FontWeight.SemiBold), color = c.accent,
        )
    }
}

@Composable
private fun DetailLine(label: String, text: String) {
    val c = Satra.colors
    Column {
        Text(label, style = Satra.type.caption, color = c.textTertiary)
        Text(text, style = Satra.type.footnote, color = c.textPrimary)
    }
}

internal fun whyRes(item: SecurityCheck.Item, st: SecurityCheck.Status, vpn: Boolean): Int {
    if (st == SecurityCheck.Status.Unknown) return R.string.sx_sec_y_unknown
    val pass = st == SecurityCheck.Status.Pass
    return when (item) {
        SecurityCheck.Item.ExitIp -> when (st) { SecurityCheck.Status.Pass -> R.string.sx_sec_y_exit_pass; SecurityCheck.Status.Warn -> R.string.sx_sec_y_exit_warn; else -> R.string.sx_sec_y_exit_fail }
        SecurityCheck.Item.DnsLeak -> when (st) { SecurityCheck.Status.Pass -> R.string.sx_sec_y_dns_pass; SecurityCheck.Status.Warn -> R.string.sx_sec_y_dns_warn; else -> R.string.sx_sec_y_dns_fail }
        SecurityCheck.Item.Ipv6, SecurityCheck.Item.WebRtc -> if (!vpn) R.string.sx_sec_y_proxy else if (pass) R.string.sx_sec_y_ipv6_pass else R.string.sx_sec_y_unknown
        SecurityCheck.Item.Encryption -> when (st) { SecurityCheck.Status.Pass -> R.string.sx_sec_y_tls_pass; SecurityCheck.Status.Warn -> R.string.sx_sec_y_tls_warn; else -> R.string.sx_sec_y_tls_fail }
        SecurityCheck.Item.KillSwitch -> if (!vpn) R.string.sx_sec_y_proxy else if (pass) R.string.sx_sec_y_ks_pass else R.string.sx_sec_y_ks_warn
    }
}

@Composable
private fun statusLabel(st: SecurityCheck.Status) = stringResource(
    when (st) {
        SecurityCheck.Status.Pass -> R.string.sx_sec_pass
        SecurityCheck.Status.Warn -> R.string.sx_sec_warn
        SecurityCheck.Status.Fail -> R.string.sx_sec_fail
        SecurityCheck.Status.Unknown -> R.string.sx_sec_unknown
    },
)

@Composable
private fun itemTitle(item: SecurityCheck.Item) = when (item) {
    SecurityCheck.Item.ExitIp -> stringResource(R.string.sx_sec_exit) to SatraIcons.Globe
    SecurityCheck.Item.DnsLeak -> stringResource(R.string.sx_sec_dns) to SatraIcons.Shield
    SecurityCheck.Item.Ipv6 -> stringResource(R.string.sx_sec_ipv6) to SatraIcons.Globe
    SecurityCheck.Item.WebRtc -> stringResource(R.string.sx_sec_webrtc) to SatraIcons.Shield
    SecurityCheck.Item.Encryption -> stringResource(R.string.sx_sec_tls) to SatraIcons.Lock
    SecurityCheck.Item.KillSwitch -> stringResource(R.string.sx_sec_killswitch) to SatraIcons.Lock
}

@Composable
private fun fixTitle(fix: SecurityCheck.Fix): String = stringResource(
    when (fix) {
        SecurityCheck.Fix.Connect -> R.string.sx_sec_fix_connect
        SecurityCheck.Fix.ChooseOtherServer -> R.string.sx_sec_fix_other
        SecurityCheck.Fix.UseSecureConfig -> R.string.sx_sec_fix_secure
        SecurityCheck.Fix.DisableInsecure -> R.string.sx_sec_fix_insecure
        SecurityCheck.Fix.EnableKillSwitch -> R.string.sx_sec_fix_killswitch
        SecurityCheck.Fix.UseRemoteDns -> R.string.sx_sec_fix_dns
        SecurityCheck.Fix.EnableIpv6Tunnel -> R.string.sx_sec_fix_ipv6
    },
)
