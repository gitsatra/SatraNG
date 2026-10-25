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
import androidx.compose.runtime.collectAsState
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
import com.v2ray.ang.handler.SecurityAudit
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
        AuditScreen(onBack = { finish() })
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
private fun AuditScreen(onBack: () -> Unit) {
    val c = Satra.colors
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val results by SecurityAudit.live.collectAsState()
    val running by SecurityAudit.running.collectAsState()
    val started by SecurityAudit.started.collectAsState()
    var offline by remember { mutableStateOf(false) }
    val homeTor by com.v2ray.ang.ui.home.ExitIpChipCache.tor.collectAsState()
    val tor = homeTor || (!running && SecurityAudit.torExit && results.isNotEmpty())
    val scroll = androidx.compose.foundation.rememberScrollState()
    LaunchedEffect(running, tor) { if (!running && tor && results.isNotEmpty()) scroll.animateScrollTo(scroll.maxValue) }
    val scope = rememberCoroutineScope()
    fun start() {
        if (running) return
        scope.launch {
            offline = !withContext(Dispatchers.IO) { coreListening() }
            if (offline) { SecurityAudit.started.value = true; SecurityAudit.live.value = emptyMap(); return@launch }
            SecurityAudit.start(ctx)
        }
    }
    val done = results.values.toList()
    val score = SecurityAudit.score(done)
    val progress = done.size / SecurityAudit.CHECKS.size.toFloat()
    val nextId = if (running) SecurityAudit.CHECKS.firstOrNull { it.id !in results }?.id else null
    SatraScrollPage(title = stringResource(R.string.sx_sec_title), onBack = onBack, scrollState = scroll) {
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            ScoreRing(if (started) score else 0, running, started && !offline, progress)
            Spacer(Modifier.height(10.dp))
            Text(
                when {
                    !started -> stringResource(R.string.sa_ready)
                    offline -> stringResource(R.string.sx_sec_connect_first)
                    running -> stringResource(R.string.sx_sec_running)
                    score >= 85 -> stringResource(R.string.sx_sec_great)
                    score >= 60 -> stringResource(R.string.sx_sec_ok)
                    else -> stringResource(R.string.sx_sec_weak)
                },
                style = Satra.type.title2, color = c.textPrimary, textAlign = TextAlign.Center,
            )
            Text(
                stringResource(if (offline) R.string.sx_sec_connect_first_note else R.string.sa_intro),
                style = Satra.type.footnote, color = c.textSecondary, textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
            )
            Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
                SatraButton(
                    stringResource(if (started && !running) R.string.sa_again else R.string.sa_start), onClick = { start() },
                    modifier = Modifier.fillMaxWidth(), loading = running, enabled = !running,
                )
            }
        }
        androidx.compose.animation.AnimatedVisibility(tor) { TorBanner(showButton = !running && results.isEmpty()) { start() } }
        if (started && !offline) {
            SecurityAudit.Cat.entries.forEach { cat ->
                val checks = SecurityAudit.CHECKS.filter { it.cat == cat }
                val got = checks.mapNotNull { results[it.id] }
                val max = got.filter { it.scored }.sumOf { it.check.max }
                val header = catTitle(cat) + if (max > 0) "  ·  " + stringResource(R.string.sa_cat_points, got.sumOf { it.points }, max) else ""
                Spacer(Modifier.height(14.dp))
                InsetGroup(header = header) {
                    checks.forEach { ck -> row { AuditRow(ck, results[ck.id], ck.id == nextId) } }
                }
            }
        }
        Text(
            stringResource(R.string.sx_sec_footer), style = Satra.type.footnote, color = c.textSecondary,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
        )
    }
}

@Composable
private fun TorBanner(showButton: Boolean, onCheck: () -> Unit) {
    val c = Satra.colors
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
            .clip(com.v2ray.ang.ui.compose.satra.SatraShapes.Large).background(c.warningSoft).padding(16.dp),
    ) {
        Text(stringResource(R.string.sa_tor_on), style = Satra.type.headline, color = c.warning)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.sa_tor_on_sub), style = Satra.type.footnote, color = c.textPrimary)
        if (showButton) {
            Spacer(Modifier.height(10.dp))
            SatraButton(stringResource(R.string.sa_tor_check), onClick = onCheck, style = com.v2ray.ang.ui.compose.satra.SatraButtonStyle.Tinted, compact = true)
        }
    }
}

@Composable
private fun catTitle(cat: SecurityAudit.Cat) = stringResource(
    when (cat) {
        SecurityAudit.Cat.Connection -> R.string.sa_cat_connection
        SecurityAudit.Cat.Leaks -> R.string.sa_cat_leaks
        SecurityAudit.Cat.Device -> R.string.sa_cat_device
        SecurityAudit.Cat.Tor -> R.string.sa_cat_tor
    },
)

@Composable
private fun sa(id: String, part: String): String {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val res = remember(id, part) { ctx.resources.getIdentifier("sa_${id}_$part", "string", ctx.packageName) }
    return if (res == 0) "" else stringResource(res)
}

@Composable
private fun AuditRow(ck: SecurityAudit.Check, r: SecurityAudit.Result?, active: Boolean) {
    val c = Satra.colors
    var open by remember { mutableStateOf(false) }
    val tint = when (r?.st) {
        SecurityAudit.St.Pass -> c.success
        SecurityAudit.St.Warn -> c.warning
        SecurityAudit.St.Fail -> c.danger
        SecurityAudit.St.Info -> c.accent
        SecurityAudit.St.Skip, null -> if (active) c.accent else c.textTertiary
    }
    val title = sa(ck.id, "title")
    Column(Modifier.fillMaxWidth().animateContentSize(tween(240))) {
        SatraRow(
            title,
            subtitle = when {
                r == null -> null
                r.st == SecurityAudit.St.Skip -> stringResource(R.string.sa_skip)
                ck.max == 0 -> listOf(stringResource(R.string.sa_info), r.detail).filter { it.isNotBlank() }.joinToString(" · ")
                else -> listOf(stringResource(R.string.sa_pts, r.points, ck.max), r.detail).filter { it.isNotBlank() }.joinToString(" · ")
            },
            icon = null,
            onClick = if (r != null) ({ open = !open }) else null,
            trailing = {
                androidx.compose.animation.AnimatedContent(r?.st to active, label = "st") { (st, act) ->
                    when {
                        st == null && act -> SatraSpinner(size = 18.dp, color = c.accent, strokeWidth = 2.dp)
                        st == null -> Box(Modifier.size(10.dp).clip(CircleShape).background(c.fillStrong))
                        else -> Icon(
                            when (st) {
                                SecurityAudit.St.Pass -> SatraIcons.Check
                                SecurityAudit.St.Info, SecurityAudit.St.Skip -> SatraIcons.Info
                                else -> SatraIcons.Warning
                            },
                            null, tint = tint, modifier = Modifier.size(20.dp),
                        )
                    }
                }
            },
        )
        if (open && r != null) {
            val ok = r.st == SecurityAudit.St.Pass || (r.st == SecurityAudit.St.Info && r.detail == "on")
            Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (r.st != SecurityAudit.St.Skip) DetailLine(stringResource(R.string.sa_why), sa(ck.id, if (ok) "ok" else "bad"))
                if (r.detail.isNotBlank()) DetailLine(stringResource(R.string.sa_measured), "\u2068${r.detail}\u2069")
                if (!ok && r.st != SecurityAudit.St.Skip) DetailLine(stringResource(R.string.sa_fix), sa(ck.id, "fix"))
                DetailLine(stringResource(R.string.sa_how), sa(ck.id, "how"))
            }
        }
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

