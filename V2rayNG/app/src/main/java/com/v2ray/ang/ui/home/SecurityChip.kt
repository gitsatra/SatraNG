package com.v2ray.ang.ui.home

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.handler.SecurityCheck
import com.v2ray.ang.ui.compose.satra.Satra
import com.v2ray.ang.ui.compose.satra.SatraIcons
import com.v2ray.ang.ui.compose.satra.SatraShapes
import com.v2ray.ang.ui.compose.satra.SatraSpinner
import com.v2ray.ang.ui.compose.satra.satraClickable
import com.v2ray.ang.ui.security.SecurityCheckActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** One automatic check per connection session (survives recomposition and tab switches). */
internal object SecurityChipCache {
    @Volatile var sessionStartMs = 0L
    @Volatile var report: SecurityCheck.Report? = null
}

/** Chip colour band for a score: 85+ good, 60+ fair, else weak. */
internal fun securityBand(score: Int): Int = when {
    score >= 85 -> 2
    score >= 60 -> 1
    else -> 0
}

/** Small "Security 92" chip under the session clock; shows the last on-demand result, never runs by itself. */
@Composable
internal fun SecurityChip(sessionStartMs: Long, modifier: Modifier = Modifier) {
    val c = Satra.colors
    val ctx = LocalContext.current
    // No automatic run on connect (it costs battery and data): show the cached result, tap opens the check.
    val report = SecurityCheck.last
    val r = report
    val desc = if (r == null) stringResource(R.string.sx_sec_title) else stringResource(R.string.sx_sec_title) + " " + r.score
    val (fg, bg) = when (r?.let { securityBand(it.score) }) {
        2 -> c.success to c.successSoft
        1 -> c.warning to c.warningSoft
        0 -> c.danger to c.dangerSoft
        else -> c.textSecondary to c.fill
    }
    AnimatedVisibility(visible = true, enter = fadeIn(tween(250))) {
        Row(
            modifier
                .clip(SatraShapes.Pill)
                .background(bg)
                .satraClickable(pressedScale = 0.95f) { ctx.startActivity(Intent(ctx, SecurityCheckActivity::class.java)) }
                .semantics { contentDescription = desc }
                .padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (r == null) Icon(SatraIcons.ShieldCheck, null, tint = fg, modifier = Modifier.size(14.dp))
            else Icon(if (securityBand(r.score) == 2) SatraIcons.ShieldCheck else SatraIcons.Warning, null, tint = fg, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(5.dp))
            Text(
                if (r == null) stringResource(R.string.sx_sec_title) else stringResource(R.string.sx_sec_chip_score, r.score),
                style = Satra.type.footnote.copy(fontWeight = FontWeight.SemiBold), color = fg,
            )
        }
    }
}
