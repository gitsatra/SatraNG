package com.v2ray.ang.ui.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
        SecurityChip(sessionStartMs, Modifier)
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
        var attempt = 0
        while (exit == null && attempt < 3) {
            delay(if (attempt == 0) 1_500L else 4_000L)
            val r = withContext(Dispatchers.IO) { runCatching { SecurityCheck.liveExit() }.getOrNull() }
            if (r != null) {
                ExitIpChipCache.sessionStartMs = sessionStartMs
                ExitIpChipCache.exit = r
                exit = r
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
