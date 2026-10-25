package com.v2ray.ang.ui.routing


import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.handler.IranApps
import com.v2ray.ang.ui.compose.satra.SatraSwitch
import com.v2ray.ang.handler.RoutingPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.ui.compose.satra.Satra
import com.v2ray.ang.ui.compose.satra.SatraButton
import com.v2ray.ang.ui.compose.satra.SatraIcons
import com.v2ray.ang.ui.compose.satra.SatraShapes
import com.v2ray.ang.ui.compose.satra.SatraSpace
import com.v2ray.ang.ui.compose.satra.SatraTextField
import com.v2ray.ang.ui.compose.satra.satraClickable

/** Kind of exception list; [key] is the settings key. */
internal enum class ExceptionKind(val key: String, val isApp: Boolean) {
    VpnDomain(RoutingPolicy.PREF_VPN_DOMAINS, false),
    VpnApp(RoutingPolicy.PREF_VPN_APPS, true),
    DirectDomain(RoutingPolicy.PREF_DIRECT_DOMAINS, false),
}

/** Validated value to store, or null. Pure; unit-tested. */
internal fun validateException(kind: ExceptionKind, input: String): String? =
    if (kind.isApp) input.trim().takeIf { RoutingPolicy.isValidPackage(it) } else RoutingPolicy.normalizeDomain(input)

/** Smart-routing exceptions: always through the VPN (sites, apps) and always direct (sites). */
@Composable
internal fun RoutingExceptionsSection() {
    val c = Satra.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = SatraSpace.Gutter).padding(top = 22.dp)) {
        Text(
            stringResource(R.string.sx_rx_title), style = Satra.type.headline, color = c.textPrimary,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Text(
            stringResource(R.string.sx_rx_note), style = Satra.type.footnote, color = c.textSecondary,
            modifier = Modifier.padding(horizontal = 16.dp).padding(top = 2.dp, bottom = 10.dp),
        )
        ExceptionBox(ExceptionKind.VpnDomain, stringResource(R.string.sx_rx_vpn_sites), stringResource(R.string.sx_rx_vpn_sites_hint), c.accent)
        Spacer(Modifier.height(10.dp))
        ExceptionBox(ExceptionKind.VpnApp, stringResource(R.string.sx_rx_vpn_apps), stringResource(R.string.sx_rx_vpn_apps_hint), c.accent)
        Spacer(Modifier.height(10.dp))
        ExceptionBox(ExceptionKind.DirectDomain, stringResource(R.string.sx_rx_direct_sites), stringResource(R.string.sx_rx_direct_sites_hint), c.success)
        if (RoutingPolicy.isIran(com.v2ray.ang.handler.MmkvManager.decodeRoutingRulesets())) {
            Spacer(Modifier.height(10.dp))
            IranAppsBox()
        }
        ReconnectPrompt()
    }
}

/** Shown after any change here while connected; the change only applies on reconnect. */
@Composable
private fun ReconnectPrompt() {
    val c = Satra.colors
    val ctx = LocalContext.current
    val tick by SettingsChangeManager.dataChanges.collectAsState()
    var dismissedAt by remember { mutableStateOf(tick) }
    val pending = tick != dismissedAt && com.v2ray.ang.core.CoreServiceManager.isRunning()
    if (!pending) return
    Spacer(Modifier.height(10.dp))
    Row(
        Modifier.fillMaxWidth().clip(SatraShapes.Large).background(c.accent.copy(alpha = 0.12f)).padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.sx_reconnect_to_apply), style = Satra.type.footnote, color = c.textPrimary, modifier = Modifier.weight(1f))
        SatraButton(stringResource(R.string.sx_reconnect), onClick = {
            SettingsChangeManager.consumeRestartService()
            com.v2ray.ang.core.LauncherManager.restartIfRunning(ctx)
            dismissedAt = tick
        }, compact = true)
    }
}

/** Marks a change that needs a reconnect (also restarts silently when Home resumes). */
private fun changed() {
    SettingsChangeManager.makeRestartService()
    SettingsChangeManager.makeSetupGroupTab()
}

/** Iranian apps excluded from the VPN by smart routing: tap to keep one on the VPN, or add one. */
@Composable
private fun IranAppsBox() {
    val c = Satra.colors
    val ctx = LocalContext.current
    var version by remember { mutableStateOf(0) }
    val apps by produceState<List<Pair<String, String>>?>(null, version) {
        value = withContext(Dispatchers.IO) {
            val pm = ctx.packageManager
            IranApps.current(ctx).map { p ->
                p to (runCatching { pm.getApplicationLabel(pm.getApplicationInfo(p, 0)).toString() }.getOrDefault(p))
            }.sortedBy { it.second.lowercase() }
        }
    }
    var removed by remember { mutableStateOf(IranApps.removed()) }
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val invalid = stringResource(R.string.sx_rx_bad_app)
    val notInstalled = stringResource(R.string.sx_rx_not_installed)
    Column(Modifier.fillMaxWidth().clip(SatraShapes.Large).background(c.surface).padding(14.dp)) {
        Text(stringResource(R.string.sx_ir_apps_title) + (apps?.let { "  ·  ${it.size}" } ?: ""), style = Satra.type.subhead, color = c.textPrimary)
        Text(stringResource(R.string.sx_ir_apps_note), style = Satra.type.footnote, color = c.textSecondary, modifier = Modifier.padding(top = 2.dp))
        val list = apps
        if (!list.isNullOrEmpty()) {
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                list.forEach { (pkg, label) ->
                    Row(
                        Modifier.clip(SatraShapes.Pill).background(c.success.copy(alpha = 0.14f))
                            .satraClickable {
                                IranApps.setAdded(IranApps.added() - pkg)
                                IranApps.setRemoved(IranApps.removed() + pkg); removed = IranApps.removed()
                                version++; changed()
                            }
                            .padding(start = 10.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(label, style = Satra.type.footnote, color = c.textPrimary)
                        Spacer(Modifier.width(4.dp))
                        Icon(SatraIcons.Close, stringResource(R.string.sx_rx_remove, label), tint = c.textSecondary, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
        if (removed.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.sx_ir_apps_restore), style = Satra.type.footnote, color = c.textSecondary)
            Spacer(Modifier.height(4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                removed.forEach { pkg ->
                    Text(
                        "\u2066$pkg\u2069", style = Satra.type.footnote, color = c.textSecondary,
                        modifier = Modifier.clip(SatraShapes.Pill).background(c.textSecondary.copy(alpha = 0.10f))
                            .satraClickable {
                                IranApps.setRemoved(IranApps.removed() - pkg); removed = IranApps.removed()
                                version++; changed()
                            }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        AppPicker(version) { version++; removed = IranApps.removed(); changed() }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SatraTextField(
                value = input,
                onValueChange = { input = it; error = null },
                label = stringResource(R.string.sx_ir_apps_add),
                modifier = Modifier.weight(1f),
                placeholder = "ir.example.bank",
                isError = error != null,
                supporting = error,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done,
                    capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false,
                ),
            )
            Spacer(Modifier.width(8.dp))
            SatraButton(stringResource(R.string.sx_add), onClick = {
                val v = input.trim()
                error = when {
                    !RoutingPolicy.isValidPackage(v) -> invalid
                    runCatching { ctx.packageManager.getApplicationInfo(v, 0) }.isFailure -> notInstalled
                    else -> null
                }
                if (error == null) {
                    IranApps.setAdded(IranApps.added() + v)
                    IranApps.setRemoved(IranApps.removed() - v); removed = IranApps.removed()
                    input = ""; version++; changed()
                }
            }, compact = true, enabled = input.isNotBlank())
        }
    }
}


@Composable
private fun ExceptionBox(kind: ExceptionKind, title: String, hint: String, tone: androidx.compose.ui.graphics.Color) {
    val c = Satra.colors
    val ctx = LocalContext.current
    var items by remember { mutableStateOf(RoutingPolicy.values(kind.key)) }
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val invalid = stringResource(if (kind.isApp) R.string.sx_rx_bad_app else R.string.sx_rx_bad_domain)
    val notInstalled = stringResource(R.string.sx_rx_not_installed)
    val dup = stringResource(R.string.sx_rx_dup)
    fun add() {
        val v = validateException(kind, input)
        error = when {
            v == null -> invalid
            v in items -> dup
            kind.isApp && runCatching { ctx.packageManager.getApplicationInfo(v, 0) }.isFailure -> notInstalled
            else -> null
        }
        if (error == null && v != null) {
            RoutingPolicy.add(kind.key, v); items = items + v; input = ""
            changed()
        }
    }
    Column(Modifier.fillMaxWidth().clip(SatraShapes.Large).background(c.surface).padding(14.dp)) {
        Text(title, style = Satra.type.subhead, color = c.textPrimary)
        if (items.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items.forEach { v ->
                    Row(
                        Modifier.clip(SatraShapes.Pill).background(tone.copy(alpha = 0.14f))
                            .satraClickable {
                                RoutingPolicy.remove(kind.key, v); items = items - v
                                changed()
                            }
                            .padding(start = 10.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("\u2066$v\u2069", style = Satra.type.footnote, color = c.textPrimary)
                        Spacer(Modifier.width(4.dp))
                        Icon(SatraIcons.Close, stringResource(R.string.sx_rx_remove, v), tint = c.textSecondary, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SatraTextField(
                value = input,
                onValueChange = { input = it; error = null },
                label = hint,
                modifier = Modifier.weight(1f),
                placeholder = if (kind.isApp) "org.telegram.messenger" else "example.ir",
                isError = error != null,
                supporting = error,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done,
                    capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false,
                ),
            )
            Spacer(Modifier.width(8.dp))
            SatraButton(stringResource(R.string.sx_add), onClick = { add() }, compact = true, enabled = input.isNotBlank())
        }
    }
}


/** Searchable picker of every installed app: detected Iranian apps are pre-ticked; tick = bypass the VPN. */
@Composable
private fun AppPicker(version: Int, onChange: () -> Unit) {
    val c = Satra.colors
    val ctx = LocalContext.current
    var query by remember { mutableStateOf("") }
    var open by remember { mutableStateOf(false) }
    data class Row3(val pkg: String, val label: String, val on: Boolean, val reason: String?)
    val rows by produceState<List<Row3>>(emptyList(), version, open) {
        if (!open) return@produceState
        value = withContext(Dispatchers.IO) {
            val pm = ctx.packageManager
            val bypass = IranApps.current(ctx)
            IranApps.installed(ctx).filter { it.first != ctx.packageName }.map { (p, dl) ->
                val label = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(p, 0)).toString() }.getOrDefault(p)
                Row3(p, label, p in bypass, IranApps.reason(p, dl, IranApps.installerOf(pm, p)))
            }.sortedWith(compareByDescending<Row3> { it.on }.thenBy { it.label.lowercase() })
        }
    }
    SatraButton(stringResource(if (open) R.string.sx_ir_apps_pick_hide else R.string.sx_ir_apps_pick), onClick = { open = !open }, compact = true)
    if (!open) return
    Spacer(Modifier.height(8.dp))
    SatraTextField(value = query, onValueChange = { query = it }, label = stringResource(R.string.sx_ir_apps_search), modifier = Modifier.fillMaxWidth())
    val q = query.trim().lowercase()
    val shown = rows.filter { q.isEmpty() || it.label.lowercase().contains(q) || it.pkg.contains(q) }.take(150)
    Spacer(Modifier.height(6.dp))
    shown.forEach { r ->
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(r.label, style = Satra.type.footnote, color = c.textPrimary)
                Text("\u2066${r.pkg}\u2069" + (r.reason?.let { "  ·  " + it } ?: ""), style = Satra.type.caption, color = c.textSecondary)
            }
            SatraSwitch(checked = r.on, onCheckedChange = { v ->
                if (v) { IranApps.setRemoved(IranApps.removed() - r.pkg); if (r.reason == null) IranApps.setAdded(IranApps.added() + r.pkg) }
                else { IranApps.setAdded(IranApps.added() - r.pkg); IranApps.setRemoved(IranApps.removed() + r.pkg) }
                onChange()
            })
        }
    }
}
