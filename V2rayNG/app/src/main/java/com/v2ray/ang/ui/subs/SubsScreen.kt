package com.v2ray.ang.ui.subs

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.ui.app.AppNav
import com.v2ray.ang.ui.compose.satra.FormLabel
import com.v2ray.ang.ui.compose.satra.IconTile
import com.v2ray.ang.ui.compose.satra.InsetGroup
import com.v2ray.ang.ui.compose.satra.Satra
import com.v2ray.ang.ui.compose.satra.SatraAlert
import com.v2ray.ang.ui.compose.satra.SatraBadge
import com.v2ray.ang.ui.compose.satra.SatraButton
import com.v2ray.ang.ui.compose.satra.SatraButtonStyle
import com.v2ray.ang.ui.compose.satra.SatraCard
import com.v2ray.ang.ui.compose.satra.SatraChip
import com.v2ray.ang.ui.compose.satra.SatraEmptyState
import com.v2ray.ang.ui.compose.satra.SatraIconButton
import com.v2ray.ang.ui.compose.satra.SatraIcons
import com.v2ray.ang.ui.compose.satra.SatraLargeTitleScaffold
import com.v2ray.ang.ui.compose.satra.SatraProgressBar
import com.v2ray.ang.ui.compose.satra.SatraRow
import com.v2ray.ang.ui.compose.satra.SatraSheet
import com.v2ray.ang.ui.compose.satra.SatraSpace
import com.v2ray.ang.ui.compose.satra.SatraSwitch
import com.v2ray.ang.ui.compose.satra.SatraSwitchRow
import com.v2ray.ang.ui.compose.satra.SatraTextField
import com.v2ray.ang.ui.compose.satra.SheetTitle
import com.v2ray.ang.ui.compose.satra.formatBytes
import com.v2ray.ang.ui.compose.satra.rememberSatraHaptics
import com.v2ray.ang.ui.main.MainViewModel
import com.v2ray.ang.util.Utils
import java.util.concurrent.TimeUnit

private sealed interface SubSheet {
    data class Edit(val guid: String?, val url: String = "", val name: String = "") : SubSheet
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubsScreen(vm: SubsViewModel, mainVm: MainViewModel, nav: AppNav) {
    val state by vm.state.collectAsState()
    SubsContent(state, vm, nav)
}

/** Stateless body of the Subscriptions tab (also rendered by the screenshot tests). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubsContent(state: SubsUiState, vm: SubsActions, nav: AppNav) {
    val ctx = LocalContext.current
    val haptics = rememberSatraHaptics()
    var sheet by remember { mutableStateOf<SubSheet?>(null) }
    var deleting by remember { mutableStateOf<SubRow?>(null) }
    val c = Satra.colors

    LaunchedEffect(Unit) { vm.reload() }

    fun addFromClipboard() {
        val text = runCatching { Utils.getClipboard(ctx).trim() }.getOrDefault("")
        sheet = if (SubsViewModel.looksLikeUrl(text)) {
            SubSheet.Edit(null, text, SubsViewModel.suggestName(text))
        } else SubSheet.Edit(null)
    }

    PullToRefreshBox(isRefreshing = state.updatingAll, onRefresh = { vm.updateAll() }) {
        SatraLargeTitleScaffold(
            title = stringResource(R.string.sx_subs_title),
            subtitle = if (state.rows.isNotEmpty()) pluralStringResource(R.plurals.sx_subs_count, state.rows.size, state.rows.size) else null,
            actions = {
                if (state.rows.isNotEmpty()) {
                    SatraIconButton(SatraIcons.Refresh, stringResource(R.string.sx_subs_update_all), { vm.updateAll() }, enabled = !state.updatingAll)
                }
                SatraIconButton(SatraIcons.Plus, stringResource(R.string.sx_subs_add), { sheet = SubSheet.Edit(null) })
            },
        ) {
            if (state.rows.isEmpty()) {
                item(key = "empty") {
                    SatraEmptyState(
                        icon = SatraIcons.Layers,
                        title = stringResource(R.string.sx_subs_empty_title),
                        message = stringResource(R.string.sx_subs_empty_msg),
                        actionText = stringResource(R.string.sx_subs_add),
                        onAction = { sheet = SubSheet.Edit(null) },
                        secondaryText = stringResource(R.string.sx_add_clipboard),
                        onSecondary = { addFromClipboard() },
                        modifier = Modifier.padding(top = 48.dp),
                    )
                }
            } else {
                items(state.rows, key = { it.guid }) { row ->
                    SubCard(
                        row = row,
                        updating = row.guid in state.updating,
                        onUpdate = { vm.updateOne(row.guid) },
                        onEdit = { sheet = SubSheet.Edit(row.guid) },
                        onToggle = { vm.setEnabled(row.guid, it) },
                        onSupport = { row.item.supportUrl?.let { Utils.openUri(ctx, it) } },
                        modifier = Modifier.padding(horizontal = SatraSpace.Gutter).padding(bottom = 12.dp),
                    )
                }
                item(key = "hint") {
                    Text(
                        stringResource(R.string.sx_subs_hint),
                        style = Satra.type.footnote, color = c.textSecondary,
                        modifier = Modifier.padding(horizontal = SatraSpace.Gutter + 4.dp).padding(top = 4.dp),
                    )
                }
            }
        }
    }

    state.message?.let { SubsBanner(it, onDone = vm::messageShown) }

    (sheet as? SubSheet.Edit)?.let { edit ->
        val row = state.rows.firstOrNull { it.guid == edit.guid }
        EditSheet(
            sub = row, initialUrl = edit.url, initialName = edit.name,
            vm = vm, nav = nav,
            onDismiss = { sheet = null },
            onDelete = { r -> sheet = null; deleting = r },
        )
    }
    deleting?.let { row ->
        SatraAlert(
            title = stringResource(R.string.sx_subs_delete_title, row.title),
            message = stringResource(R.string.sx_subs_delete_msg),
            confirmText = stringResource(R.string.sx_delete),
            dismissText = stringResource(R.string.sx_cancel),
            destructive = true,
            onConfirm = { vm.delete(row.guid); deleting = null; haptics.confirm() },
            onDismiss = { deleting = null },
        )
    }
    state.qr?.let { bmp ->
        SatraSheet(onDismiss = vm::dismissQr) {
            SheetTitle(state.qrTitle)
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                Image(
                    bmp.asImageBitmap(), stringResource(R.string.sx_qr),
                    modifier = Modifier.size(260.dp).clip(RoundedCornerShape(16.dp)).background(androidx.compose.ui.graphics.Color.White).padding(10.dp),
                )
            }
        }
    }
}

@Composable
private fun SubsBanner(message: SubsMessage, onDone: () -> Unit) {
    val text = when (message) {
        is SubsMessage.Updated -> pluralStringResource(R.plurals.sx_subs_updated, message.configs, message.name, message.configs)
        is SubsMessage.UpdatedAll -> stringResource(R.string.sx_subs_updated_all, message.ok, message.failed)
        is SubsMessage.Failed -> stringResource(R.string.sx_subs_failed)
        SubsMessage.NothingToUpdate -> stringResource(R.string.sx_subs_nothing)
        SubsMessage.Saved -> stringResource(R.string.sx_saved)
        SubsMessage.Deleted -> stringResource(R.string.sx_deleted)
        SubsMessage.InvalidUrl, SubsMessage.InvalidName, SubsMessage.InvalidInterval -> null
    }
    val ctx = LocalContext.current
    LaunchedEffect(message) {
        if (text != null) android.widget.Toast.makeText(ctx, text, android.widget.Toast.LENGTH_SHORT).show()
        onDone()
    }
}

// ───────────────────────────── Card ─────────────────────────────

@Composable
private fun SubCard(
    row: SubRow,
    updating: Boolean,
    onUpdate: () -> Unit,
    onEdit: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onSupport: () -> Unit,
    modifier: Modifier,
) {
    val c = Satra.colors
    val item = row.item
    val fraction = item.usageFraction()
    val barColor = when {
        fraction == null -> c.accent
        fraction >= 0.9f -> c.danger
        fraction >= 0.75f -> c.warning
        else -> c.accent
    }
    SatraCard(modifier, onClick = onEdit) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(SatraIcons.Layers, if (item.enabled) c.accent else c.textTertiary, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(row.title, style = Satra.type.headline, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    pluralStringResource(R.plurals.sx_configs_count, row.configCount, row.configCount) + " · " + lastUpdatedText(item.lastUpdated),
                    style = Satra.type.footnote, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            SubUpdateButton(updating, onUpdate, enabled = item.url.isNotBlank())
            SatraSwitch(item.enabled, onToggle, contentDescription = row.title)
        }
        if (item.showUsage && item.total > 0L) {
            Spacer(Modifier.height(12.dp))
            SatraProgressBar(fraction ?: 0f, color = barColor)
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.sx_quota_left, formatBytes(item.remainingBytes() ?: 0L), formatBytes(item.total)),
                style = Satra.type.footnote, color = c.textSecondary,
            )
        } else if (item.showUsage && item.usedBytes() > 0L) {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.sx_subs_used, formatBytes(item.usedBytes())), style = Satra.type.footnote, color = c.textSecondary)
        }
        val badges = buildList<@Composable () -> Unit> {
            if (item.showExpire && item.expire > 0L) add { ExpiryBadge(item.expire) }
            if (item.autoUpdate) add { SatraBadge(stringResource(R.string.sx_subs_auto), c.info, c.infoSoft) }
            if (!item.enabled) add { SatraBadge(stringResource(R.string.sx_off), c.textSecondary, c.fill) }
        }
        if (badges.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { badges.forEach { it() } }
        }
        val announce = com.v2ray.ang.handler.SubscriptionUserinfoParser.displayText(item.announce)
        if (item.showAnnounce && announce.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(announce, style = Satra.type.footnote, color = c.textSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        if (item.showSupportLinks && !item.supportUrl.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            SatraButton(stringResource(R.string.sx_subs_support), onSupport, style = SatraButtonStyle.Plain, compact = true, icon = SatraIcons.Link)
        }
    }
}

@Composable
private fun SubUpdateButton(updating: Boolean, onClick: () -> Unit, enabled: Boolean) {
    val t = rememberInfiniteTransition(label = "subSpin")
    val angle by t.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "angle")
    SatraIconButton(
        SatraIcons.Refresh, stringResource(R.string.sx_subs_update),
        onClick = { if (!updating) onClick() },
        enabled = enabled,
        tint = Satra.colors.accent,
        modifier = Modifier.rotate(if (updating) angle else 0f),
    )
}

@Composable
private fun lastUpdatedText(lastUpdated: Long): String {
    if (lastUpdated <= 0L) return stringResource(R.string.sx_subs_never)
    val rel = DateUtils.getRelativeTimeSpanString(lastUpdated, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE)
    return stringResource(R.string.sx_subs_updated_ago, rel.toString())
}

@Composable
private fun ExpiryBadge(expireSec: Long) {
    val c = Satra.colors
    val ms = if (expireSec > 10_000_000_000L) expireSec else expireSec * 1000L
    val left = ms - System.currentTimeMillis()
    val days = TimeUnit.MILLISECONDS.toDays(left)
    when {
        left <= 0L -> SatraBadge(stringResource(R.string.sx_quota_expired), c.danger, c.dangerSoft)
        days < 1 -> SatraBadge(stringResource(R.string.sx_quota_expires_today), c.warning, c.warningSoft)
        else -> SatraBadge(
            pluralStringResource(R.plurals.sx_quota_expires_days, days.toInt(), days.toInt()),
            if (days < 3) c.warning else c.textSecondary,
            if (days < 3) c.warningSoft else c.fill,
        )
    }
}

// ───────────────────────────── Edit sheet ─────────────────────────────

/** Refresh choices, minutes. */
private val intervalChoices = listOf(15, 60, 360, 720, 1440)

@Composable
private fun intervalLabel(m: Int): String = when {
    m % 1440 == 0 -> pluralStringResource(R.plurals.sx_days, m / 1440, m / 1440)
    m % 60 == 0 -> pluralStringResource(R.plurals.sx_hours, m / 60, m / 60)
    else -> pluralStringResource(R.plurals.sx_minutes, m, m)
}

@Composable
internal fun EditSheet(
    sub: SubRow?,
    initialUrl: String,
    initialName: String,
    vm: SubsActions,
    nav: AppNav,
    onDismiss: () -> Unit,
    onDelete: (SubRow) -> Unit,
) {
    val ctx = LocalContext.current
    val haptics = rememberSatraHaptics()
    val item = sub?.item
    var name by remember { mutableStateOf(item?.remarks ?: initialName) }
    var url by remember { mutableStateOf(item?.url ?: initialUrl) }
    var auto by remember { mutableStateOf(item?.autoUpdate ?: true) }
    var minutes by remember { mutableStateOf((item?.updateInterval ?: com.v2ray.ang.AppConfig.DEFAULT_SUB_INTERVAL_MINUTES).toInt().coerceAtLeast(15)) }
    val choices = remember(minutes) { (intervalChoices + minutes).distinct().sorted() }
    var enabled by remember { mutableStateOf(item?.enabled ?: true) }
    var error by remember { mutableStateOf<SubsMessage?>(null) }

    SatraSheet(onDismiss = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            SheetTitle(stringResource(if (sub == null) R.string.sx_subs_add else R.string.sx_subs_edit))
            Box(Modifier.padding(horizontal = 20.dp)) {
                SatraTextField(
                    name, { name = it; error = null }, stringResource(R.string.sx_subs_name),
                    isError = error == SubsMessage.InvalidName,
                    supporting = if (error == SubsMessage.InvalidName) stringResource(R.string.sx_subs_err_name) else null,
                )
            }
            Spacer(Modifier.height(10.dp))
            Box(Modifier.padding(horizontal = 20.dp)) {
                SatraTextField(
                    url, { url = it; error = null }, stringResource(R.string.sx_subs_url),
                    placeholder = "https://",
                    isError = error == SubsMessage.InvalidUrl,
                    supporting = if (error == SubsMessage.InvalidUrl) stringResource(R.string.sx_subs_err_url) else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    trailing = {
                        Row {
                            SatraIconButton(SatraIcons.Clipboard, stringResource(R.string.sx_add_clipboard), {
                                val t = runCatching { Utils.getClipboard(ctx).trim() }.getOrDefault("")
                                if (t.isNotEmpty()) {
                                    url = t
                                    if (name.isBlank()) name = SubsViewModel.suggestName(t)
                                }
                            }, size = 20.dp)
                            SatraIconButton(SatraIcons.Scan, stringResource(R.string.sx_add_scan), {
                                nav.scanQr { scanned ->
                                    url = scanned.trim()
                                    if (name.isBlank()) name = SubsViewModel.suggestName(scanned)
                                }
                            }, size = 20.dp)
                        }
                    },
                )
            }
            Spacer(Modifier.height(14.dp))
            InsetGroup(footer = stringResource(R.string.sx_subs_auto_footer)) {
                row { SatraSwitchRow(stringResource(R.string.sx_subs_auto_update), auto, { auto = it }, icon = SatraIcons.Refresh, iconColor = Satra.colors.info) }
                row { SatraSwitchRow(stringResource(R.string.sx_subs_enabled), enabled, { enabled = it }, icon = SatraIcons.Power, iconColor = Satra.colors.success) }
            }
            AnimatedVisibility(auto) {
                Column {
                    FormLabel(stringResource(R.string.sx_subs_interval))
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        choices.forEach { m ->
                            SatraChip(
                                intervalLabel(m),
                                selected = minutes == m, onClick = { minutes = m },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SatraButton(
                    stringResource(R.string.sx_save),
                    {
                        val err = vm.save(sub?.guid, name, url, auto, minutes, enabled)
                        if (err == null) { haptics.confirm(); onDismiss() } else { error = err; haptics.reject() }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
            if (sub != null) {
                Spacer(Modifier.height(8.dp))
                InsetGroup {
                    if (item?.url?.isNotBlank() == true) {
                        row { SatraRow(stringResource(R.string.sx_subs_copy_link), icon = SatraIcons.Copy, iconColor = Satra.colors.info, onClick = { nav.copy(item.url) }) }
                        row { SatraRow(stringResource(R.string.sx_subs_show_qr), icon = SatraIcons.Qr, iconColor = Satra.colors.info, onClick = { vm.showQr(sub); onDismiss() }) }
                    }
                    row { SatraRow(stringResource(R.string.sx_subs_advanced), icon = SatraIcons.Sliders, iconColor = Satra.colors.textSecondary, chevron = true, onClick = { onDismiss(); nav.editSubscription(sub.guid) }) }
                    row { SatraRow(stringResource(R.string.sx_delete), icon = SatraIcons.Trash, iconColor = Satra.colors.danger, destructive = true, onClick = { onDelete(sub) }) }
                }
            }
        }
    }
}
