package com.v2ray.ang.ui.usage

import android.text.format.DateFormat
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.v2ray.ang.R
import com.v2ray.ang.handler.GlobalLimit
import com.v2ray.ang.handler.LimitPeriod
import com.v2ray.ang.handler.RuleKind
import com.v2ray.ang.handler.UsageLimitEngine
import com.v2ray.ang.handler.UsageRule
import com.v2ray.ang.handler.UsageRulesRepository
import com.v2ray.ang.handler.UsagePeriods
import com.v2ray.ang.handler.GeoIpResolver
import com.v2ray.ang.ui.app.AppNav
import com.v2ray.ang.ui.compose.satra.AppIcon
import com.v2ray.ang.ui.compose.satra.SatraIconButton
import com.v2ray.ang.ui.compose.satra.SatraMenu
import com.v2ray.ang.ui.compose.satra.SatraMenuItem
import com.v2ray.ang.ui.compose.satra.AppPickerSheet
import com.v2ray.ang.ui.compose.satra.ChipRow
import com.v2ray.ang.ui.compose.satra.FormLabel
import com.v2ray.ang.ui.compose.satra.InsetGroup
import com.v2ray.ang.ui.compose.satra.IconTile
import com.v2ray.ang.ui.compose.satra.Satra
import com.v2ray.ang.ui.compose.satra.SatraAlert
import com.v2ray.ang.ui.compose.satra.SatraBadge
import com.v2ray.ang.ui.compose.satra.SatraBarChart
import com.v2ray.ang.ui.compose.satra.SatraButton
import com.v2ray.ang.ui.compose.satra.SatraButtonStyle
import com.v2ray.ang.ui.compose.satra.SatraCard
import com.v2ray.ang.ui.compose.satra.SatraChip
import com.v2ray.ang.ui.compose.satra.SatraIcons
import com.v2ray.ang.ui.compose.satra.SatraLargeTitleScaffold
import com.v2ray.ang.ui.compose.satra.SatraProgressBar
import com.v2ray.ang.ui.compose.satra.SatraRow
import com.v2ray.ang.ui.compose.satra.SatraSegmented
import com.v2ray.ang.ui.compose.satra.SatraSheet
import com.v2ray.ang.ui.compose.satra.SatraSwitch
import com.v2ray.ang.ui.compose.satra.SatraSwitchRow
import com.v2ray.ang.ui.compose.satra.SatraTextField
import com.v2ray.ang.ui.compose.satra.SectionHeader
import com.v2ray.ang.ui.compose.satra.SheetTitle
import com.v2ray.ang.ui.compose.satra.SatraSpace
import com.v2ray.ang.ui.compose.satra.formatBytes
import com.v2ray.ang.ui.compose.satra.formatHoursMinutes
import com.v2ray.ang.ui.compose.satra.labelStep
import com.v2ray.ang.ui.main.LiveUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal sealed interface EditorTarget {
    data class Edit(val rule: UsageRule) : EditorTarget
    data class NewApp(val packageName: String? = null, val label: String = "") : EditorTarget
    data object NewSite : EditorTarget
    data class NewCategory(val category: com.v2ray.ang.handler.AppCategory) : EditorTarget
    data object Global : EditorTarget
}

@Composable
fun UsageScreen(vm: UsageViewModel, live: LiveUiState, nav: AppNav) {
    val state by vm.state.collectAsState()
    LifecycleResumeEffect(Unit) {
        vm.onAccessMaybeChanged()
        vm.onVisible()
        onPauseOrDispose { vm.onHidden() }
    }
    UsageContent(state, live, nav, vm)
}

/** Stateless body of the Usage tab (also rendered by the screenshot tests). */
@Composable
fun UsageContent(state: UsageUiState, live: LiveUiState, nav: AppNav, vm: UsageActions) {
    var editor by remember { mutableStateOf<EditorTarget?>(null) }
    var pickingApp by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<UsageRule?>(null) }
    val c = Satra.colors

    SatraLargeTitleScaffold(title = stringResource(R.string.sx_usage_title), bottomPadding = 28.dp) {
        item(key = "range") {
            Box(Modifier.padding(horizontal = SatraSpace.Gutter).padding(bottom = 14.dp)) {
                SatraSegmented(
                    options = listOf(stringResource(R.string.sx_range_7), stringResource(R.string.sx_range_30)),
                    selectedIndex = if (state.rangeDays == 7) 0 else 1,
                    onSelect = { vm.setRange(if (it == 0) 7 else 30) },
                )
            }
        }
        item(key = "summary") {
            SummaryCard(state, live, Modifier.padding(horizontal = SatraSpace.Gutter))
        }
        item(key = "chart-data") {
            Spacer(Modifier.height(14.dp))
            DataChartCard(state, Modifier.padding(horizontal = SatraSpace.Gutter))
        }
        item(key = "chart-hours") {
            Spacer(Modifier.height(14.dp))
            HoursChartCard(state, Modifier.padding(horizontal = SatraSpace.Gutter))
        }

        // ── Limits
        item(key = "limits-h") {
            Spacer(Modifier.height(8.dp))
            SectionHeader(stringResource(R.string.sx_limits))
        }
        if (state.rules.isEmpty()) {
            item(key = "limits-empty") {
                SatraCard(Modifier.padding(horizontal = SatraSpace.Gutter)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconTile(SatraIcons.Shield, c.accent, size = 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(stringResource(R.string.sx_limit_none), style = Satra.type.headline, color = c.textPrimary)
                            Text(stringResource(R.string.sx_limit_none_msg), style = Satra.type.footnote, color = c.textSecondary)
                        }
                    }
                }
            }
        } else {
            items(state.rules, key = { it.rule.id }) { row ->
                RuleCard(
                    row,
                    onClick = { editor = EditorTarget.Edit(row.rule) },
                    onToggle = { vm.setRuleEnabled(row.rule.id, it) },
                    onReset = { vm.resetRule(row.rule.id) },
                    onDelete = { confirmDelete = row.rule },
                    modifier = Modifier.padding(horizontal = SatraSpace.Gutter).padding(bottom = 10.dp).animateItem(),
                )
            }
        }
        item(key = "limits-add") {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = SatraSpace.Gutter).padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SatraButton(
                    stringResource(R.string.sx_limit_add_app), { pickingApp = true },
                    modifier = Modifier.weight(1f), style = SatraButtonStyle.Tinted, icon = SatraIcons.Apps, compact = true,
                )
                SatraButton(
                    stringResource(R.string.sx_limit_add_site), { editor = EditorTarget.NewSite },
                    modifier = Modifier.weight(1f), style = SatraButtonStyle.Tinted, icon = SatraIcons.Globe, compact = true,
                )
            }
            Spacer(Modifier.height(14.dp))
            GlobalLimitRow(state.global, onClick = { editor = EditorTarget.Global })
        }

        // ── Apps
        item(key = "apps-h") {
            Spacer(Modifier.height(14.dp))
            SectionHeader(stringResource(R.string.sx_usage_apps))
            Text(
                stringResource(R.string.sx_usage_lifetime_vs_limit),
                style = Satra.type.footnote,
                color = c.textSecondary,
                modifier = Modifier.padding(horizontal = SatraSpace.Gutter + 4.dp, vertical = 4.dp),
            )
        }
        // Metering works without Usage Access (TrafficStats fallback); with access, NetworkStats+VPN is preferred.
        if (!state.hasUsageAccess) {
            item(key = "access") {
                SatraCard(Modifier.padding(horizontal = SatraSpace.Gutter)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconTile(SatraIcons.Eye, c.info, size = 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(stringResource(R.string.sx_usage_access_title), style = Satra.type.headline, color = c.textPrimary)
                            Text(stringResource(R.string.sx_usage_access_msg), style = Satra.type.footnote, color = c.textSecondary)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    SatraButton(stringResource(R.string.sx_usage_access_open), { nav.openUsageAccess() }, compact = true)
                }
            }
        }
        if (state.apps.isEmpty()) {
            item(key = "apps-empty") {
                Text(
                    stringResource(if (state.appsLoading) R.string.sx_loading else R.string.sx_usage_apps_none_yet),
                    style = Satra.type.callout, color = c.textSecondary,
                    modifier = Modifier.padding(horizontal = SatraSpace.Gutter + 4.dp),
                )
            }
        } else {
            val maxBytes = state.apps.maxOf { it.totalBytes }.coerceAtLeast(1L)
            item(key = "apps") {
                InsetGroup(footer = stringResource(R.string.sx_usage_apps_footer)) {
                    state.apps.take(25).forEach { app ->
                        // An app that already has a limit opens that limit instead of creating a duplicate.
                        val existingRule = state.rules.firstOrNull { it.rule.kind == RuleKind.APP && it.rule.packageName == app.packageName }?.rule
                        row {
                            AppUsageRowView(
                                app, maxBytes,
                                limited = existingRule != null,
                                onClick = app.packageName?.let { pkg ->
                                    { editor = existingRule?.let { EditorTarget.Edit(it) } ?: EditorTarget.NewApp(pkg, app.label) }
                                },
                            )
                        }
                    }
                }
            }
        }
        item(key = "clear") {
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                SatraButton(stringResource(R.string.sx_usage_clear), { confirmClear = true }, style = SatraButtonStyle.Plain, compact = true)
            }
        }
    }

    // ── Sheets / dialogs
    if (pickingApp) {
        AppPickerSheet(
            title = stringResource(R.string.sx_limit_choose_app),
            selected = emptySet(), multi = false,
            disabled = state.rules.filter { it.rule.kind == RuleKind.APP }.map { it.rule.packageName }.toSet(),
            onDismiss = { pickingApp = false },
            onResult = { picked ->
                pickingApp = false
                picked.firstOrNull()?.let { editor = EditorTarget.NewApp(it) }
            },
            onPickCategory = { cat -> pickingApp = false; editor = EditorTarget.NewCategory(cat) },
            disabledCategories = state.rules.mapNotNull { it.rule.takeIf { r -> r.kind == RuleKind.CATEGORY }?.appCategory }.toSet(),
        )
    }
    editor?.let { target ->
        LimitEditorSheet(
            target = target,
            state = state,
            onDismiss = { editor = null },
            onSaveRule = { rule ->
                vm.saveRule(rule)
                editor = null
                if (rule.matchesApps && rule.enabled && state.appRulesSupported && !state.engineReady) {
                    // Limits should simply work: switch engines quietly (only happens if the user
                    // turned the Xray engine off in Advanced settings).
                    nav.enableAppEnforcement()
                }
            },
            onDelete = { id -> vm.deleteRule(id); editor = null },
            onReset = { id -> vm.resetRule(id); editor = null },
            onSaveGlobal = { vm.saveGlobal(it); editor = null },
        )
    }
    confirmDelete?.let { rule ->
        SatraAlert(
            title = stringResource(R.string.sx_subs_delete_title, rule.label),
            message = stringResource(R.string.sx_limit_delete_msg),
            confirmText = stringResource(R.string.sx_delete),
            dismissText = stringResource(R.string.sx_cancel),
            destructive = true,
            onConfirm = { confirmDelete = null; vm.deleteRule(rule.id) },
            onDismiss = { confirmDelete = null },
        )
    }
    if (confirmClear) {
        SatraAlert(
            title = stringResource(R.string.sx_usage_clear_title),
            message = stringResource(R.string.sx_usage_clear_msg),
            confirmText = stringResource(R.string.sx_delete),
            dismissText = stringResource(R.string.sx_cancel),
            destructive = true,
            onConfirm = { confirmClear = false; vm.clearHistory() },
            onDismiss = { confirmClear = false },
        )
    }
}

// ───────────────────────────── Summary + charts ─────────────────────────────

@Composable
private fun SummaryCard(state: UsageUiState, live: LiveUiState, modifier: Modifier) {
    val c = Satra.colors
    SatraCard(modifier) {
        Text(stringResource(R.string.sx_usage_total), style = Satra.type.footnote, color = c.textSecondary)
        Text(formatBytes(state.totalBytes), style = Satra.type.numberHuge, color = c.textPrimary)
        Spacer(Modifier.height(10.dp))
        Row {
            MiniStat(stringResource(R.string.sx_usage_avg), formatBytes(state.averageBytes), Modifier.weight(1f))
            MiniStat(
                stringResource(R.string.sx_usage_connected),
                formatHoursMinutes(state.connectedSeconds, stringResource(R.string.sx_unit_h), stringResource(R.string.sx_unit_m)),
                Modifier.weight(1f),
            )
            MiniStat(stringResource(R.string.sx_home_today), formatBytes(live.todayBytes), Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        com.v2ray.ang.ui.home.TrafficTableHeader()
        if (live.running) {
            com.v2ray.ang.ui.home.TrafficRow(stringResource(R.string.sx_live_session), live.sessionDown, live.sessionUp)
        }
        com.v2ray.ang.ui.home.TrafficRow(stringResource(R.string.sx_home_today), live.todayDown, live.todayUp)
        com.v2ray.ang.ui.home.TrafficRow(
            stringResource(if (state.rangeDays <= 7) R.string.sx_range_7 else R.string.sx_range_30),
            state.days.sumOf { it.downBytes }, state.days.sumOf { it.upBytes },
        )
    }
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = Satra.type.caption, color = Satra.colors.textSecondary)
        Text(value, style = Satra.type.numberSmall, color = Satra.colors.textPrimary, maxLines = 1)
    }
}

@Composable
private fun dayLabels(state: UsageUiState): List<String> {
    val locale = com.v2ray.ang.ui.compose.satra.currentLocale()
    val weekday = remember(locale) { SimpleDateFormat("EEE", locale) }
    val dayNum = remember(locale) { SimpleDateFormat("d", locale) }
    return state.days.map { d ->
        val date = Date(d.startMs)
        if (state.rangeDays <= 7) weekday.format(date) else dayNum.format(date)
    }
}

@Composable
private fun DataChartCard(state: UsageUiState, modifier: Modifier) {
    val c = Satra.colors
    SatraCard(modifier) {
        Text(stringResource(R.string.sx_usage_chart_data), style = Satra.type.headline, color = c.textPrimary)
        Spacer(Modifier.height(8.dp))
        if (state.days.none { it.totalBytes > 0L }) {
            Text(stringResource(R.string.sx_usage_chart_empty), style = Satra.type.callout, color = c.textSecondary, modifier = Modifier.padding(vertical = 18.dp))
        } else {
            SatraBarChart(
                values = state.days.map { it.totalBytes.toFloat() },
                labels = dayLabels(state),
                labelEvery = labelStep(state.days.size),
                formatValue = { formatBytes(it.toLong()) },
                accessibilitySummary = stringResource(R.string.sx_usage_chart_data),
            )
        }
    }
}

@Composable
private fun HoursChartCard(state: UsageUiState, modifier: Modifier) {
    val c = Satra.colors
    val h = stringResource(R.string.sx_unit_h)
    val m = stringResource(R.string.sx_unit_m)
    SatraCard(modifier) {
        Text(stringResource(R.string.sx_usage_chart_hours), style = Satra.type.headline, color = c.textPrimary)
        Spacer(Modifier.height(8.dp))
        if (state.days.none { it.connectedSec > 0L }) {
            Text(stringResource(R.string.sx_usage_chart_empty), style = Satra.type.callout, color = c.textSecondary, modifier = Modifier.padding(vertical = 18.dp))
        } else {
            SatraBarChart(
                values = state.days.map { it.connectedSec.toFloat() },
                labels = dayLabels(state),
                labelEvery = labelStep(state.days.size),
                height = 130.dp,
                formatValue = { formatHoursMinutes(it.toLong(), h, m) },
                accessibilitySummary = stringResource(R.string.sx_usage_chart_hours),
            )
        }
    }
}

// ───────────────────────────── Apps ─────────────────────────────

@Composable
private fun AppUsageRowView(app: AppUsageRow, maxBytes: Long, limited: Boolean, onClick: (() -> Unit)?) {
    val c = Satra.colors
    val label = if (app.label == UsageViewModel.UNLABELED) stringResource(R.string.sx_other_apps) else app.label
    val subtitle = buildString {
        if (app.lifetimeBytes > 0L && app.lifetimeBytes != app.totalBytes) {
            append(stringResource(R.string.sx_usage_app_lifetime, formatBytes(app.lifetimeBytes)))
        }
        if (app.bypassingVpn) {
            if (isNotEmpty()) append(" · ")
            append(stringResource(R.string.sx_usage_app_bypass))
        }
    }.ifBlank { null }
    SatraRow(
        title = label,
        leading = { AppIcon(app.packageName, size = 40.dp) },
        subtitle = subtitle,
        trailing = {
            Column(horizontalAlignment = Alignment.End) {
                Text(formatBytes(app.totalBytes), style = Satra.type.numberSmall, color = c.textPrimary)
                when {
                    app.bypassingVpn -> SatraBadge(stringResource(R.string.sx_usage_app_bypass_badge), c.warning, c.warningSoft)
                    limited -> SatraBadge(stringResource(R.string.sx_limited), c.accent, c.accentSoft)
                }
            }
        },
        onClick = onClick,
    )
    Box(Modifier.padding(start = 68.dp, end = 16.dp, bottom = 10.dp)) {
        SatraProgressBar(fraction = app.totalBytes.toFloat() / maxBytes, height = 4.dp)
    }
}

// ───────────────────────────── Limits ─────────────────────────────


@Composable
private fun periodNoun(period: LimitPeriod): String = stringResource(
    when (period) {
        LimitPeriod.DAILY -> R.string.sx_period_today
        LimitPeriod.WEEKLY -> R.string.sx_period_this_week
        LimitPeriod.MONTHLY -> R.string.sx_period_this_month
    },
)

@Composable
private fun formatReset(ms: Long): String {
    val ctx = LocalContext.current
    val locale = com.v2ray.ang.ui.compose.satra.currentLocale()
    val pattern = DateFormat.getBestDateTimePattern(locale, if (DateFormat.is24HourFormat(ctx)) "MMMd Hm" else "MMMd jm")
    return SimpleDateFormat(pattern, locale).format(Date(ms))
}

@Composable
private fun RuleCard(
    row: RuleRow,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onReset: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier,
) {
    val c = Satra.colors
    val rule = row.rule
    var menu by remember { mutableStateOf(false) }
    SatraCard(modifier, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            when (rule.kind) {
                RuleKind.APP -> AppIcon(row.iconPackage, size = 40.dp)
                RuleKind.CATEGORY -> IconTile(SatraIcons.Apps, c.accent, size = 40.dp)
                RuleKind.DOMAIN -> IconTile(SatraIcons.Globe, c.info, size = 40.dp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    rule.appCategory?.let { com.v2ray.ang.ui.compose.satra.categoryLabel(it) } ?: rule.label,
                    style = Satra.type.headline, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    when {
                        rule.alwaysBlock -> stringResource(R.string.sx_limit_always_block)
                        else -> stringResource(R.string.sx_limit_used_of, formatBytes(row.usedBytes), formatBytes(row.capBytes)) + " · " + periodNoun(rule.period)
                    },
                    style = Satra.type.footnote, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Box {
                SatraIconButton(SatraIcons.More, stringResource(R.string.sx_more_options), { menu = true }, tint = c.textSecondary)
                SatraMenu(menu, { menu = false }) {
                    if (!rule.alwaysBlock) {
                        SatraMenuItem(stringResource(R.string.sx_limit_reset), { menu = false; onReset() }, SatraIcons.Refresh)
                    }
                    SatraMenuItem(stringResource(R.string.sx_delete), { menu = false; onDelete() }, SatraIcons.Trash, destructive = true)
                }
            }
            SatraSwitch(rule.enabled, onToggle, contentDescription = rule.label)
        }
        if (!rule.alwaysBlock && rule.enabled) {
            Spacer(Modifier.height(10.dp))
            SatraProgressBar(
                fraction = row.fraction,
                color = when {
                    row.blocked -> c.danger
                    row.fraction >= 0.8f -> c.warning
                    else -> c.accent
                },
            )
        }
        if (rule.enabled && row.blocked) {
            Spacer(Modifier.height(8.dp))
            SatraBadge(
                if (rule.alwaysBlock) stringResource(R.string.sx_limit_always_block)
                else stringResource(R.string.sx_limit_blocked_until, formatReset(row.nextResetMs)),
                c.danger, c.dangerSoft,
            )
        } else if (row.needsEngine) {
            Spacer(Modifier.height(8.dp))
            SatraBadge(stringResource(R.string.sx_limit_needs_engine), c.warning, c.warningSoft)
        }
    }
}

@Composable
private fun GlobalLimitRow(global: GlobalRow, onClick: () -> Unit) {
    val c = Satra.colors
    InsetGroup {
        row {
            SatraRow(
                title = stringResource(R.string.sx_limit_global),
                subtitle = if (global.limit.enabled && global.limit.limitBytes > 0L) {
                    stringResource(R.string.sx_limit_used_of, formatBytes(global.usedBytes), formatBytes(global.limit.limitBytes)) + " · " + periodNoun(global.limit.period)
                } else stringResource(R.string.sx_limit_global_sub),
                icon = SatraIcons.Speed,
                iconColor = c.accent,
                value = if (global.limit.enabled) null else stringResource(R.string.sx_off),
                chevron = true,
                onClick = onClick,
            )
        }
    }
}

// ───────────────────────────── Editor ─────────────────────────────

private data class SitePreset(val labelRes: Int, val domains: List<String>)

private val sitePresets = listOf(
    SitePreset(R.string.sx_preset_youtube, UsageLimitEngine.YOUTUBE_DOMAINS),
    SitePreset(R.string.sx_preset_instagram, listOf("instagram.com", "cdninstagram.com")),
    SitePreset(R.string.sx_preset_tiktok, listOf("tiktok.com", "tiktokcdn.com", "byteoversea.com")),
    SitePreset(R.string.sx_preset_netflix, listOf("netflix.com", "nflxvideo.net", "nflximg.net")),
    SitePreset(R.string.sx_preset_telegram, listOf("telegram.org", "t.me", "telesco.pe")),
)

private val amountPresetsMb = listOf(2L, 100L, 250L, 500L, 1024L, 2048L, 5120L)

@Composable
internal fun LimitEditorSheet(
    target: EditorTarget,
    state: UsageUiState,
    onDismiss: () -> Unit,
    onSaveRule: (UsageRule) -> Unit,
    onDelete: (String) -> Unit,
    onReset: (String) -> Unit,
    onSaveGlobal: (GlobalLimit) -> Unit,
) {
    val c = Satra.colors
    val ctx = LocalContext.current
    val existing = (target as? EditorTarget.Edit)?.rule
    val isGlobal = target is EditorTarget.Global
    val kind = when (target) {
        is EditorTarget.Edit -> target.rule.kind
        is EditorTarget.NewApp -> RuleKind.APP
        is EditorTarget.NewCategory -> RuleKind.CATEGORY
        else -> RuleKind.DOMAIN
    }
    val category = existing?.appCategory ?: (target as? EditorTarget.NewCategory)?.category
    val categoryName = category?.let { com.v2ray.ang.ui.compose.satra.categoryLabel(it) }.orEmpty()
    val initialMb: Long = when {
        existing != null -> existing.limitBytes / (1024 * 1024)
        isGlobal -> state.global.limit.limitBytes / (1024 * 1024)
        else -> 500L
    }
    var pkg by remember { mutableStateOf(existing?.packageName ?: (target as? EditorTarget.NewApp)?.packageName.orEmpty()) }
    var label by remember {
        mutableStateOf(
            existing?.label ?: (target as? EditorTarget.NewApp)?.let { it.label.ifBlank { appLabel(ctx, it.packageName.orEmpty()) } }.orEmpty(),
        )
    }
    var domainsText by remember { mutableStateOf(existing?.domains?.joinToString(", ") { it.removePrefix("domain:") }.orEmpty()) }
    val useGb = initialMb >= 1024 && initialMb % 1024 == 0L
    var unit by remember { mutableStateOf(if (useGb) 1 else 0) }
    var amountText by remember {
        mutableStateOf(
            (if (useGb) initialMb / 1024 else initialMb).takeIf { it > 0 }?.toString().orEmpty(),
        )
    }
    var periodIndex by remember {
        mutableStateOf(
            when (existing?.period ?: if (isGlobal) state.global.limit.period else LimitPeriod.DAILY) {
                LimitPeriod.DAILY -> 0; LimitPeriod.WEEKLY -> 1; LimitPeriod.MONTHLY -> 2
            },
        )
    }
    var blockAll by remember { mutableStateOf(existing?.alwaysBlock == true && existing.enabled) }
    var globalOn by remember { mutableStateOf(state.global.limit.enabled) }
    var pickingApp by remember { mutableStateOf(false) }
    var attempted by remember { mutableStateOf(false) }

    val amountMb: Long = (amountText.toLongOrNull() ?: 0L) * (if (unit == 1) 1024L else 1L)
    val parsedDomains = remember(domainsText) {
        domainsText.split(',', '\n', ' ', ';').mapNotNull { UsageRulesRepository.normalizeDomain(it) }.distinct()
    }
    val domainsValid = parsedDomains.isNotEmpty()
    val amountValid = blockAll || amountMb > 0L
    val appOk = kind != RuleKind.APP || pkg.isNotBlank()
    val canSave = when {
        isGlobal -> !globalOn || amountMb > 0L
        kind == RuleKind.APP -> appOk && amountValid
        kind == RuleKind.CATEGORY -> category != null && amountValid
        else -> domainsValid && label.isNotBlank() && amountValid
    }

    SatraSheet(onDismiss = onDismiss, skipPartiallyExpanded = true) {
        val scroll = androidx.compose.foundation.rememberScrollState()
        Column(Modifier.androidxVerticalScroll(scroll)) {
            SheetTitle(
                text = when {
                    isGlobal -> stringResource(R.string.sx_limit_global)
                    kind == RuleKind.APP -> stringResource(R.string.sx_limit_edit_app_title)
                    kind == RuleKind.CATEGORY -> stringResource(R.string.sx_limit_edit_cat_title)
                    else -> stringResource(R.string.sx_limit_edit_site_title)
                },
                subtitle = if (isGlobal) stringResource(R.string.sx_limit_global_sub) else null,
            )
            if (isGlobal) {
                Box(Modifier.padding(horizontal = 0.dp)) {
                    InsetGroup { row { SatraSwitchRow(stringResource(R.string.sx_limit_global_enable), globalOn, { globalOn = it }) } }
                }
            }
            if (kind == RuleKind.APP && !isGlobal) {
                if (!state.appRulesSupported) {
                    Notice(stringResource(R.string.sx_limit_android_old), warning = true)
                }
                Box(Modifier.padding(horizontal = 0.dp).padding(top = 6.dp)) {
                    InsetGroup {
                        row {
                            SatraRow(
                                title = label.ifBlank { stringResource(R.string.sx_limit_choose_app) },
                                subtitle = pkg.takeIf { it.isNotBlank() },
                                leading = { AppIcon(pkg.takeIf { it.isNotBlank() }, size = 40.dp) },
                                chevron = existing == null,
                                onClick = if (existing == null) ({ pickingApp = true }) else null,
                            )
                        }
                    }
                }
            }
            if (kind == RuleKind.CATEGORY && !isGlobal) {
                if (!state.appRulesSupported) {
                    Notice(stringResource(R.string.sx_limit_android_old), warning = true)
                }
                Box(Modifier.padding(top = 6.dp)) {
                    InsetGroup {
                        row {
                            SatraRow(
                                title = categoryName,
                                subtitle = stringResource(R.string.sx_limit_cat_note),
                                leading = { IconTile(SatraIcons.Apps, c.accent, size = 40.dp) },
                            )
                        }
                    }
                }
            }
            if (kind == RuleKind.DOMAIN && !isGlobal) {
                FormLabel(stringResource(R.string.sx_limit_quick))
                Row(
                    Modifier.fillMaxWidth().androidxHScroll().padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    sitePresets.forEach { p ->
                        val name = stringResource(p.labelRes)
                        SatraChip(name, selected = false, onClick = {
                            label = name
                            domainsText = p.domains.joinToString(", ")
                        })
                    }
                }
                Spacer(Modifier.height(10.dp))
                Box(Modifier.padding(horizontal = 20.dp)) {
                    SatraTextField(label, { label = it }, stringResource(R.string.sx_limit_site_name))
                }
                Spacer(Modifier.height(10.dp))
                Box(Modifier.padding(horizontal = 20.dp)) {
                    SatraTextField(
                        domainsText, { domainsText = it }, stringResource(R.string.sx_limit_domains),
                        placeholder = "youtube.com, googlevideo.com",
                        supporting = if (attempted && !domainsValid) stringResource(R.string.sx_limit_domains_error) else stringResource(R.string.sx_limit_domains_support),
                        isError = attempted && !domainsValid,
                        singleLine = false, minLines = 2,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    )
                }
            }
            if (!isGlobal || globalOn) {
                if (!isGlobal) {
                    Box(Modifier.padding(horizontal = 0.dp).padding(top = 14.dp)) {
                        InsetGroup {
                            row { SatraSwitchRow(stringResource(R.string.sx_limit_block_all), blockAll, { blockAll = it }, subtitle = stringResource(R.string.sx_limit_block_all_sub)) }
                        }
                    }
                }
                if (!blockAll) {
                    FormLabel(stringResource(R.string.sx_limit_amount))
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        SatraTextField(
                            amountText, { amountText = it.filter { ch -> ch.isDigit() }.take(6) },
                            stringResource(R.string.sx_limit_amount),
                            isError = attempted && !amountValid,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                        SatraSegmented(listOf(stringResource(R.string.sx_unit_mb), stringResource(R.string.sx_unit_gb)), unit, { unit = it })
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.fillMaxWidth().androidxHScroll().padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        amountPresetsMb.forEach { mb ->
                            val gb = mb >= 1024
                            SatraChip(
                                text = String.format(com.v2ray.ang.ui.compose.satra.currentLocale(), "%d ", if (gb) mb / 1024 else mb) + stringResource(if (gb) R.string.sx_unit_gb else R.string.sx_unit_mb),
                                selected = amountMb == mb,
                                onClick = { if (gb) { unit = 1; amountText = (mb / 1024).toString() } else { unit = 0; amountText = mb.toString() } },
                            )
                        }
                    }
                    FormLabel(stringResource(R.string.sx_limit_period))
                    Box(Modifier.padding(horizontal = 20.dp)) {
                        SatraSegmented(
                            listOf(stringResource(R.string.sx_period_daily), stringResource(R.string.sx_period_weekly), stringResource(R.string.sx_period_monthly)),
                            periodIndex, { periodIndex = it },
                        )
                    }
                    Text(
                        stringResource(R.string.sx_limit_reset_note),
                        style = Satra.type.caption, color = c.textSecondary,
                        modifier = Modifier.padding(horizontal = 26.dp).padding(top = 6.dp),
                    )
                }
            }
            if (existing != null && !existing.alwaysBlock) {
                val used = state.rules.firstOrNull { it.rule.id == existing.id }?.usedBytes ?: 0L
                Spacer(Modifier.height(14.dp))
                InsetGroup {
                    row {
                        SatraRow(
                            title = stringResource(R.string.sx_limit_reset),
                            subtitle = stringResource(R.string.sx_limit_reset_sub, formatBytes(used)),
                            icon = SatraIcons.Refresh,
                            iconColor = c.info,
                            onClick = { onReset(existing.id) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (existing != null) {
                    SatraButton(
                        stringResource(R.string.sx_delete), { onDelete(existing.id) },
                        style = SatraButtonStyle.Danger, modifier = Modifier.weight(1f),
                    )
                }
                SatraButton(
                    stringResource(R.string.sx_save),
                    {
                        attempted = true
                        if (!canSave) return@SatraButton
                        val period = LimitPeriod.entries[periodIndex]
                        if (isGlobal) {
                            onSaveGlobal(GlobalLimit(globalOn, amountMb * 1024 * 1024, period))
                        } else {
                            val base = existing ?: UsageRule(kind = kind)
                            onSaveRule(
                                base.copy(
                                    kind = kind,
                                    label = if (kind == RuleKind.CATEGORY) categoryName else label.ifBlank { pkg },
                                    packageName = if (kind == RuleKind.APP) pkg else "",
                                    category = if (kind == RuleKind.CATEGORY) category?.name.orEmpty() else "",
                                    domains = if (kind == RuleKind.DOMAIN) parsedDomains else emptyList(),
                                    limitBytes = if (blockAll) 0L else amountMb * 1024 * 1024,
                                    period = period,
                                    enabled = true,
                                ),
                            )
                        }
                    },
                    modifier = Modifier.weight(if (existing != null) 1f else 1f),
                    enabled = true,
                )
            }
        }
    }
    if (pickingApp) {
        AppPickerSheet(
            title = stringResource(R.string.sx_limit_choose_app),
            selected = setOf(pkg), multi = false,
            disabled = state.rules.filter { it.rule.kind == RuleKind.APP }.map { it.rule.packageName }.toSet(),
            onDismiss = { pickingApp = false },
            onResult = { picked ->
                pickingApp = false
                picked.firstOrNull()?.let { pkg = it; label = appLabel(ctx, it) }
            },
        )
    }
}

@Composable
private fun Notice(text: String, warning: Boolean = false, action: String? = null, onAction: (() -> Unit)? = null) {
    val c = Satra.colors
    SatraCard(
        Modifier.padding(horizontal = 20.dp).padding(top = 10.dp),
        containerColor = if (warning || action != null) c.warningSoft else c.infoSoft,
        padding = androidx.compose.foundation.layout.PaddingValues(14.dp),
    ) {
        Text(text, style = Satra.type.footnote, color = c.textPrimary)
        if (action != null && onAction != null) {
            Spacer(Modifier.height(8.dp))
            SatraButton(action, onAction, compact = true)
        }
    }
}

private fun appLabel(ctx: android.content.Context, pkg: String): String = runCatching {
    ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg, 0)).toString()
}.getOrDefault(pkg)

private fun Modifier.androidxVerticalScroll(state: androidx.compose.foundation.ScrollState): Modifier =
    this.then(Modifier.verticalScroll(state))

@Composable
private fun Modifier.androidxHScroll(): Modifier = this.then(Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()))
