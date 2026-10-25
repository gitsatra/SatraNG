package com.v2ray.ang.ui.geo

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.R
import com.v2ray.ang.core.CoreNativeManager
import com.v2ray.ang.handler.GeoUpdater
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.satra.InsetGroup
import com.v2ray.ang.ui.compose.satra.Satra
import com.v2ray.ang.ui.compose.satra.SatraButton
import com.v2ray.ang.ui.compose.satra.SatraButtonStyle
import com.v2ray.ang.ui.compose.satra.SatraIcons
import com.v2ray.ang.ui.compose.satra.SatraRow
import com.v2ray.ang.ui.compose.satra.SatraScrollPage
import com.v2ray.ang.ui.compose.satra.SatraShapes
import com.v2ray.ang.ui.compose.satra.SatraSwitchRow
import kotlinx.coroutines.launch
import java.io.File
import java.text.DateFormat
import java.util.Date

/** Settings > Updates: rules (geoip/geosite) versions, check, update with progress, rollback, core version. */
class GeoUpdatesActivity : BaseComponentActivity() {
    @Composable
    override fun ScreenContent() = GeoUpdatesScreen(onBack = { finish() })
}

@Composable
private fun GeoUpdatesScreen(onBack: () -> Unit) {
    val c = Satra.colors
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val phase by GeoUpdater.phase.collectAsState()
    var tick by remember { mutableStateOf(0) }
    var auto by remember { mutableStateOf(GeoUpdater.autoOn()) }
    val busy = phase is GeoUpdater.Phase.Checking || phase is GeoUpdater.Phase.Downloading
    val repo = remember(tick) { GeoUpdater.currentRepo() }
    val installed = remember(tick, phase) { GeoUpdater.installedTag() }
    val latest = remember(tick, phase) { GeoUpdater.latestTag() }
    val checked = remember(tick, phase) { GeoUpdater.lastChecked() }
    val core = remember { runCatching { CoreNativeManager.getLibVersion() }.getOrDefault("—") }
    val builtIn = stringResource(R.string.sx_geo_builtin)

    SatraScrollPage(title = stringResource(R.string.sx_geo_title), onBack = onBack, subtitle = stringResource(R.string.sx_geo_sub)) {
        InsetGroup(header = stringResource(R.string.sx_geo_rules), footer = "\u2066github.com/$repo\u2069") {
            row { SatraRow(stringResource(R.string.sx_geo_installed), value = installed ?: builtIn, icon = SatraIcons.File, iconColor = c.info) }
            row { SatraRow(stringResource(R.string.sx_geo_latest), value = latest ?: "—", icon = SatraIcons.Download, iconColor = c.success) }
            GeoUpdater.FILES.forEach { name ->
                row {
                    val f = File(GeoUpdater.dir(ctx), name)
                    SatraRow(name, value = if (f.exists()) "%.1f MB · %s".format(f.length() / 1e6, DateFormat.getDateInstance(DateFormat.SHORT).format(Date(f.lastModified()))) else "—", icon = SatraIcons.Layers, iconColor = c.textSecondary)
                }
            }
            if (checked > 0) row { SatraRow(stringResource(R.string.sx_geo_checked), value = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(checked)), icon = SatraIcons.Refresh, iconColor = c.textSecondary) }
        }
        Spacer(Modifier.height(14.dp))
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (val p = phase) {
                is GeoUpdater.Phase.Downloading -> {
                    val frac by animateFloatAsState(if (p.total > 0) p.done.toFloat() / p.total else 0f, label = "geo")
                    Text("${p.file} · %.1f / %.1f MB".format(p.done / 1e6, p.total / 1e6), style = Satra.type.footnote, color = c.textSecondary)
                    Column(Modifier.fillMaxWidth().height(6.dp).clip(SatraShapes.Pill).background(c.textSecondary.copy(alpha = 0.15f))) {
                        Spacer(Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).height(6.dp).clip(SatraShapes.Pill).background(c.accent))
                    }
                }
                is GeoUpdater.Phase.Done -> Text(stringResource(if (p.updated) R.string.sx_geo_updated else R.string.sx_geo_uptodate), style = Satra.type.footnote, color = c.success)
                is GeoUpdater.Phase.Failed -> Text(stringResource(R.string.sx_geo_upd_failed, p.reason), style = Satra.type.footnote, color = c.warning)
                else -> {}
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SatraButton(stringResource(R.string.sx_geo_check), onClick = { scope.launch { GeoUpdater.check(); tick++ } }, modifier = Modifier.weight(1f), style = SatraButtonStyle.Tinted, enabled = !busy)
                SatraButton(stringResource(R.string.sx_geo_update), onClick = { scope.launch { GeoUpdater.update(ctx.applicationContext, force = true); tick++ } }, modifier = Modifier.weight(1f), loading = busy, enabled = !busy)
            }
            if (GeoUpdater.hasRollback(ctx)) {
                SatraButton(stringResource(R.string.sx_geo_rollback), onClick = { GeoUpdater.rollback(ctx); tick++ }, modifier = Modifier.fillMaxWidth(), style = SatraButtonStyle.Plain, enabled = !busy)
            }
        }
        Spacer(Modifier.height(14.dp))
        InsetGroup(footer = stringResource(R.string.sx_geo_auto_note)) {
            row { SatraSwitchRow(stringResource(R.string.sx_geo_auto), auto, { v -> auto = v; GeoUpdater.setAuto(ctx, v) }, icon = SatraIcons.Refresh, iconColor = c.accent) }
        }
        Spacer(Modifier.height(14.dp))
        InsetGroup(header = stringResource(R.string.sx_geo_core), footer = stringResource(R.string.sx_geo_core_note)) {
            row { SatraRow("Xray", value = "\u2066$core\u2069", icon = SatraIcons.Gear, iconColor = c.textSecondary) }
            row { SatraRow("SatraNG", value = "\u2066v${BuildConfig.VERSION_NAME}\u2069", icon = SatraIcons.Info, iconColor = c.textSecondary) }
        }
    }
}
