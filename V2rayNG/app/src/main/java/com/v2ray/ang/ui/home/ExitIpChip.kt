package com.v2ray.ang.ui.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.size
import com.v2ray.ang.ui.compose.satra.SatraIcons
import com.v2ray.ang.ui.compose.satra.satraClickable
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.handler.GeoIpResolver
import com.v2ray.ang.handler.SecurityCheck
import com.v2ray.ang.ui.compose.satra.Satra
import com.v2ray.ang.ui.compose.satra.SatraShapes
import com.v2ray.ang.ui.compose.satra.SatraSpinner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Chip text: flag + IP wrapped in an LTR isolate so it never reorders inside RTL text. */
internal fun exitChipLabel(ip: String, country: String?): String {
    val flag = country?.takeIf { it.length == 2 }?.let { GeoIpResolver.flagEmoji(it) }.orEmpty()
    val isolated = "\u2066$ip\u2069"
    return if (flag.isBlank()) isolated else "$flag $isolated"
}

internal object ExitIpChipCache {
    @Volatile var sessionStartMs = 0L
    @Volatile var exit: Pair<String, String?>? = null
    /** True when the current exit IP is a known Tor exit (offline list; refreshed through the tunnel every 3 days at most). */
    val tor = kotlinx.coroutines.flow.MutableStateFlow(false)

    /** Runs once per exit-IP fetch (already done at connect): no extra polling. IO thread. */
    fun detectTor(ctx: Context, ip: String) {
        val app = ctx.applicationContext
        tor.value = com.v2ray.ang.handler.TorExits.isExit(app, ip)
        if (com.v2ray.ang.handler.TorExits.refreshIfStale(app, 3L * 86_400_000L) { com.v2ray.ang.handler.SecurityAudit.tunnelGet(it) })
            tor.value = com.v2ray.ang.handler.TorExits.isExit(app, ip)
    }
}

/** Exit IP chip + security chip in one compact row (no extra Home height beyond one line). */
@Composable
internal fun ConnectedChips(sessionStartMs: Long) {
    Row(
        Modifier.padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ExitIpChip(sessionStartMs)
        val tor by ExitIpChipCache.tor.collectAsState()
        if (tor) TorBadge() else SecurityChip(sessionStartMs, Modifier)
    }
}

/** Calm warning pill shown instead of the security chip when the exit is a Tor node; opens the Tor checks. */
@Composable
private fun TorBadge() {
    val c = Satra.colors
    val ctx = LocalContext.current
    Row(
        Modifier.clip(SatraShapes.Pill).background(c.warningSoft)
            .satraClickable(pressedScale = 0.95f) {
                ctx.startActivity(android.content.Intent(ctx, com.v2ray.ang.ui.security.SecurityCheckActivity::class.java).putExtra("tor", true))
            }
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(SatraIcons.Warning, null, tint = c.warning, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(5.dp))
        Text(stringResource(R.string.sa_tor_badge), style = Satra.type.footnote.copy(fontWeight = FontWeight.SemiBold), color = c.warning, maxLines = 1)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ExitIpChip(sessionStartMs: Long) {
    val c = Satra.colors
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var exit by remember(sessionStartMs) {
        mutableStateOf(ExitIpChipCache.exit.takeIf { ExitIpChipCache.sessionStartMs == sessionStartMs })
    }
    LaunchedEffect(sessionStartMs) {
        if (exit == null) ExitIpChipCache.tor.value = false
        var attempt = 0
        while (exit == null && attempt < 3) {
            delay(if (attempt == 0) 1_500L else 4_000L)
            val r = withContext(Dispatchers.IO) { runCatching { SecurityCheck.liveExit() }.getOrNull() }
            if (r != null) withContext(Dispatchers.IO) { runCatching { ExitIpChipCache.detectTor(ctx, r.first) } }
            if (r != null) {
                ExitIpChipCache.sessionStartMs = sessionStartMs
                ExitIpChipCache.exit = r
                exit = r
                val ll = SecurityCheck.lastExitLatLon
                ExitLocation.set(sessionStartMs, ll?.first, ll?.second, r.second)
            }
            attempt++
        }
    }
    val copiedMsg = stringResource(R.string.sx_exit_ip_copied)
    val e = exit
    val copy = {
        if (e != null) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("IP", e.first))
            Toast.makeText(ctx, copiedMsg, Toast.LENGTH_SHORT).show()
        }
    }
    Row(
        Modifier
            .clip(SatraShapes.Pill)
            .background(c.fill)
            .combinedClickable(enabled = e != null, onClick = copy, onLongClick = copy)
            .semantics { contentDescription = e?.first ?: "" }
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (e == null) {
            SatraSpinner(size = 12.dp, color = c.textSecondary, strokeWidth = 1.6.dp)
            Spacer(Modifier.width(6.dp))
        }
        Text(
            if (e == null) stringResource(R.string.sx_exit_ip_detecting) else exitChipLabel(e.first, e.second),
            style = Satra.type.footnote.copy(fontWeight = FontWeight.SemiBold),
            color = c.textPrimary,
            maxLines = 1,
        )
    }
}
