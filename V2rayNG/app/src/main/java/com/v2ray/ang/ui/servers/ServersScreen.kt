package com.v2ray.ang.ui.servers

import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.ui.app.AppNav
import com.v2ray.ang.ui.compose.satra.GroupSeparator
import com.v2ray.ang.ui.compose.satra.InsetGroup
import com.v2ray.ang.ui.compose.satra.PingPill
import com.v2ray.ang.ui.compose.satra.Satra
import com.v2ray.ang.ui.compose.satra.SatraAlert
import com.v2ray.ang.ui.compose.satra.SatraChip
import com.v2ray.ang.ui.compose.satra.SatraEmptyState
import com.v2ray.ang.ui.compose.satra.SatraIconButton
import com.v2ray.ang.ui.compose.satra.SatraIcons
import com.v2ray.ang.ui.compose.satra.SatraLargeTitleScaffold
import com.v2ray.ang.ui.compose.satra.SatraMenu
import com.v2ray.ang.ui.compose.satra.SatraMenuItem
import com.v2ray.ang.ui.compose.satra.SatraRow
import com.v2ray.ang.ui.compose.satra.SatraSearchField
import com.v2ray.ang.ui.compose.satra.SatraShapes
import com.v2ray.ang.ui.compose.satra.SatraSheet
import com.v2ray.ang.ui.compose.satra.SatraSpace
import com.v2ray.ang.ui.compose.satra.SheetTitle
import com.v2ray.ang.ui.compose.satra.rememberSatraHaptics
import com.v2ray.ang.ui.compose.satra.satraClickable
import com.v2ray.ang.ui.home.FlagBadge
import com.v2ray.ang.ui.main.GeoState
import com.v2ray.ang.ui.main.MainAction
import com.v2ray.ang.ui.main.MainUiState
import com.v2ray.ang.ui.main.MainViewModel
import com.v2ray.ang.ui.main.ServerRowUiModel
import com.v2ray.ang.ui.main.ServerSort

private enum class ConfirmKind { RemoveAll, RemoveDuplicates, RemoveInvalid }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServersScreen(vm: MainViewModel, nav: AppNav) {
    val state by vm.uiState.collectAsState()
    val group by vm.serverGroupState(state.selectedGroupId).collectAsState()
    val rows = remember(group.rows, state.hiddenGuids) { group.rows.filter { it.guid !in state.hiddenGuids }.distinctBy { it.guid } }
    var pullHint by remember { mutableStateOf(!com.v2ray.ang.handler.MmkvManager.decodeSettingsBool(PULL_HINT_KEY, false)) }
    ServersContent(
        state = state, rows = rows, nav = nav, onAction = vm::onAction,
        pullHint = pullHint,
        onPullHintSeen = {
            pullHint = false
            com.v2ray.ang.handler.MmkvManager.encodeSettings(PULL_HINT_KEY, true)
        },
    )
    state.speedSession?.let { session ->
        val names = remember(group.rows) { group.rows.associate { it.guid to it.remarks } }
        SpeedTestSheet(session, names, onStop = { vm.onAction(MainAction.CancelSpeedTest) }, onClose = { vm.onAction(MainAction.CloseSpeedSheet) })
    }
}

private const val PULL_HINT_KEY = "pref_satra_coach_pull_ping_v1"

/** Stateless body of the Servers tab (also rendered by the screenshot tests). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServersContent(
    state: MainUiState,
    rows: List<ServerRowUiModel>,
    nav: AppNav,
    onAction: (MainAction) -> Unit,
    initialQuery: String = "",
    pullHint: Boolean = false,
    onPullHintSeen: () -> Unit = {},
) {
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    var menu by remember { mutableStateOf(false) }
    var addMenu by remember { mutableStateOf(false) }
    var manualSheet by remember { mutableStateOf(false) }
    var actionsFor by remember { mutableStateOf<ServerRowUiModel?>(null) }
    var confirm by remember { mutableStateOf<ConfirmKind?>(null) }
    val haptics = rememberSatraHaptics()
    val c = Satra.colors

    LaunchedEffect(query) { onAction(MainAction.Search(query)) }

    PullToRefreshBox(
        isRefreshing = state.isTesting,
        onRefresh = { if (pullHint) onPullHintSeen(); onAction(MainAction.TestRealAllServers) },
    ) {
        SatraLargeTitleScaffold(
            title = stringResource(R.string.sx_servers_title),
            subtitle = if (rows.isNotEmpty()) pluralStringResource(R.plurals.sx_configs_count, rows.size, rows.size) else null,
            actions = {
                SatraIconButton(
                    if (state.isTesting) SatraIcons.Stop else SatraIcons.Pulse,
                    stringResource(if (state.isTesting) R.string.sx_servers_stop_test else R.string.sx_servers_test_all),
                    { onAction(if (state.isTesting) MainAction.CancelTesting else MainAction.TestRealAllServers) },
                    tint = c.accent,
                )
                Box {
                    SatraIconButton(SatraIcons.Plus, stringResource(R.string.sx_add), { addMenu = true })
                    SatraMenu(addMenu, { addMenu = false }) {
                        SatraMenuItem(stringResource(R.string.sx_add_scan), { addMenu = false; nav.action(MainAction.ImportQRcode) }, SatraIcons.Scan)
                        SatraMenuItem(stringResource(R.string.sx_add_clipboard), { addMenu = false; nav.action(MainAction.ImportClipboard) }, SatraIcons.Clipboard)
                        SatraMenuItem(stringResource(R.string.sx_add_file), { addMenu = false; nav.action(MainAction.ImportConfigLocal) }, SatraIcons.File)
                        SatraMenuItem(stringResource(R.string.sx_add_manual), { addMenu = false; manualSheet = true }, SatraIcons.Edit)
                    }
                }
                Box {
                    SatraIconButton(SatraIcons.More, stringResource(R.string.sx_more_options), { menu = true })
                    SatraMenu(menu, { menu = false }) {
                        SatraMenuItem(stringResource(R.string.sx_sort_default), { menu = false; onAction(MainAction.SetSort(ServerSort.Default)) }, if (state.sort == ServerSort.Default) SatraIcons.Check else SatraIcons.Sort)
                        SatraMenuItem(stringResource(R.string.sx_sort_ping), { menu = false; onAction(MainAction.SetSort(ServerSort.Ping)) }, if (state.sort == ServerSort.Ping) SatraIcons.Check else SatraIcons.Pulse)
                        SatraMenuItem(stringResource(R.string.sx_sort_name), { menu = false; onAction(MainAction.SetSort(ServerSort.Name)) }, if (state.sort == ServerSort.Name) SatraIcons.Check else SatraIcons.Tag)
                        SatraMenuItem(stringResource(R.string.sx_sort_speed), { menu = false; onAction(MainAction.SetSort(ServerSort.Speed)) }, if (state.sort == ServerSort.Speed) SatraIcons.Check else SatraIcons.Bolt)
                        if (state.speedTesting.isEmpty()) {
                            SatraMenuItem(stringResource(R.string.sx_speed_test_all), { menu = false; onAction(MainAction.SpeedTestAll) }, SatraIcons.Bolt)
                        } else {
                            SatraMenuItem(stringResource(R.string.sx_speed_cancel), { menu = false; onAction(MainAction.CancelSpeedTest) }, SatraIcons.Close)
                        }
                        SatraMenuItem(stringResource(R.string.sx_remove_duplicates), { menu = false; confirm = ConfirmKind.RemoveDuplicates }, SatraIcons.Copy)
                        SatraMenuItem(stringResource(R.string.sx_remove_invalid), { menu = false; confirm = ConfirmKind.RemoveInvalid }, SatraIcons.Ban)
                        SatraMenuItem(stringResource(R.string.sx_remove_all), { menu = false; confirm = ConfirmKind.RemoveAll }, SatraIcons.Trash, destructive = true)
                    }
                }
            },
        ) {
            item(key = "search") {
                Box(Modifier.padding(horizontal = SatraSpace.Gutter).padding(bottom = 10.dp)) {
                    SatraSearchField(query, { query = it }, stringResource(R.string.sx_search), clearLabel = stringResource(R.string.sx_clear))
                }
            }
            item(key = "chips") {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = SatraSpace.Gutter).padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SatraChip(
                        stringResource(R.string.sx_favorites), selected = state.favoritesOnly,
                        icon = if (state.favoritesOnly) SatraIcons.StarFilled else SatraIcons.Star,
                        onClick = { onAction(MainAction.SetFavoritesOnly(!state.favoritesOnly)) },
                    )
                    if (state.groups.size > 1) {
                        state.groups.forEach { g ->
                            SatraChip(
                                g.remarks.ifBlank { stringResource(R.string.sx_group_default) },
                                selected = g.id == state.selectedGroupId,
                                onClick = { onAction(MainAction.SelectGroup(g.id)) },
                            )
                        }
                    }
                }
            }
            if (pullHint && rows.size >= 2 && query.isBlank()) {
                item(key = "pull_hint") {
                    PullToPingHint(onDismiss = onPullHintSeen, modifier = Modifier.animateItem().padding(horizontal = SatraSpace.Gutter).padding(bottom = 10.dp))
                }
            }
            if (rows.isNotEmpty() && query.isBlank()) {
                item(key = "smart") {
                    SmartConnectCard(
                        busy = state.smartConnecting,
                        onClick = { onAction(MainAction.SmartConnect) },
                        modifier = Modifier.padding(horizontal = SatraSpace.Gutter).padding(bottom = 10.dp),
                    )
                }
            }
            if (rows.isEmpty()) {
                item(key = "empty") {
                    val searching = query.isNotBlank() || state.favoritesOnly
                    SatraEmptyState(
                        icon = if (searching) SatraIcons.Search else SatraIcons.Servers,
                        title = stringResource(if (searching) R.string.sx_servers_none_found else R.string.sx_servers_empty_title),
                        message = stringResource(if (searching) R.string.sx_servers_none_found_msg else R.string.sx_servers_empty_msg),
                        actionText = if (searching) null else stringResource(R.string.sx_add_clipboard),
                        onAction = if (searching) null else ({ nav.action(MainAction.ImportClipboard) }),
                        secondaryText = if (searching) null else stringResource(R.string.sx_add_scan),
                        onSecondary = if (searching) null else ({ nav.action(MainAction.ImportQRcode) }),
                        modifier = Modifier.padding(top = 32.dp),
                    )
                }
            } else {
                items(rows, key = { it.guid }) { row ->
                    ServerItem(
                        row = row,
                        selected = row.guid == state.selectedGuid,
                        testing = com.v2ray.ang.ui.main.isRowPinging(row.guid, row.testDelayMillis, state.pinging, state.isTesting),
                        speedTesting = row.guid in state.speedTesting,
                        onSelect = { nav.action(MainAction.SelectServer(row.guid)) },
                        onMore = { actionsFor = row },
                        onPing = { onAction(MainAction.PingServer(row.guid)) },
                        onFavorite = { onAction(MainAction.ToggleFavorite(row.guid)) },
                        onRetryLocation = { onAction(MainAction.LocateServer(row.guid)) },
                        onDelete = { onAction(MainAction.DeleteWithUndo(row.guid)) },
                        modifier = Modifier.padding(horizontal = SatraSpace.Gutter).padding(bottom = 8.dp),
                    )
                }
            }
        }
    }

    actionsFor?.let { row ->
        ServerActionsSheet(row, nav, onAction, onDismiss = { actionsFor = null })
    }
    if (manualSheet) ManualSheet(onPick = { type -> manualSheet = false; nav.action(MainAction.ImportManually(type)) }, onDismiss = { manualSheet = false })
    state.shareQRCodeBitmap?.let { bmp ->
        SatraSheet(onDismiss = { onAction(MainAction.DismissQRCodeDialog) }) {
            SheetTitle(stringResource(R.string.sx_qr))
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                Image(
                    bmp.asImageBitmap(), stringResource(R.string.sx_qr),
                    modifier = Modifier.size(260.dp).clip(RoundedCornerShape(16.dp)).background(androidx.compose.ui.graphics.Color.White).padding(10.dp),
                )
            }
        }
    }
    confirm?.let { kind ->
        SatraAlert(
            title = stringResource(
                when (kind) {
                    ConfirmKind.RemoveAll -> R.string.sx_remove_all_title
                    ConfirmKind.RemoveDuplicates -> R.string.sx_remove_duplicates_title
                    ConfirmKind.RemoveInvalid -> R.string.sx_remove_invalid_title
                },
            ),
            message = stringResource(if (kind == ConfirmKind.RemoveAll) R.string.sx_remove_all_msg else R.string.sx_remove_other_msg),
            confirmText = stringResource(R.string.sx_delete),
            dismissText = stringResource(R.string.sx_cancel),
            destructive = true,
            onConfirm = {
                haptics.heavy()
                when (kind) {
                    ConfirmKind.RemoveAll -> onAction(MainAction.RemoveAllServers)
                    ConfirmKind.RemoveDuplicates -> onAction(MainAction.RemoveDuplicateServers)
                    ConfirmKind.RemoveInvalid -> onAction(MainAction.RemoveInvalidServers)
                }
                confirm = null
            },
            onDismiss = { confirm = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ServerItem(
    row: ServerRowUiModel,
    selected: Boolean,
    testing: Boolean,
    speedTesting: Boolean,
    onSelect: () -> Unit,
    onMore: () -> Unit,
    onPing: () -> Unit,
    onFavorite: () -> Unit,
    onDelete: () -> Unit,
    onRetryLocation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Satra.colors
    val haptics = rememberSatraHaptics()
    val pingCd = stringResource(R.string.sx_ping_this)
    val dismissState = rememberSwipeToDismissBoxState()
    LaunchedEffect(dismissState.currentValue) {
        when (dismissState.currentValue) {
            SwipeToDismissBoxValue.StartToEnd -> {
                haptics.confirm()
                onFavorite()
                dismissState.reset()
            }
            SwipeToDismissBoxValue.EndToStart -> {
                haptics.heavy()
                onDelete()
                // If the delete was refused (active server) the row stays: slide it back.
                kotlinx.coroutines.delay(300)
                dismissState.reset()
            }
            SwipeToDismissBoxValue.Settled -> Unit
        }
    }
    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier.clip(SatraShapes.Large),
        backgroundContent = {
            val dir = dismissState.dismissDirection
            val bg by animateColorAsState(
                when (dir) {
                    SwipeToDismissBoxValue.StartToEnd -> c.warning
                    SwipeToDismissBoxValue.EndToStart -> c.danger
                    else -> c.fill
                },
                tween(120), label = "swipeBg",
            )
            Row(
                Modifier.fillMaxSize().background(bg).padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (dir == SwipeToDismissBoxValue.EndToStart) Arrangement.End else Arrangement.Start,
            ) {
                Icon(
                    if (dir == SwipeToDismissBoxValue.EndToStart) SatraIcons.Trash else if (row.favorite) SatraIcons.Star else SatraIcons.StarFilled,
                    null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(24.dp),
                )
            }
        },
    ) {
        val border by animateColorAsState(if (selected) c.accent else androidx.compose.ui.graphics.Color.Transparent, tween(180), label = "selBorder")
        Row(
            Modifier
                .fillMaxWidth()
                .clip(SatraShapes.Large)
                .background(if (selected) c.accentSoft else c.surface)
                .satraClickable(pressedScale = 0.985f, onLongClick = { haptics.heavy(); onMore() }, onClick = onSelect)
                .padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FlagBadge(row.countryCode, row.exitVerified, size = 42.dp, locating = row.geoState == GeoState.Locating)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        row.remarks.ifBlank { row.profile.server.orEmpty() },
                        style = Satra.type.headline, color = c.textPrimary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                    )
                }
                ServerSubtitle(row, onRetryLocation)
                ServerTags(row, speedTesting)
            }
            PingPill(
                row.testDelayMillis, testing = testing,
                modifier = Modifier.clip(SatraShapes.Pill).satraClickable(enabled = !testing, pressedScale = 0.9f, onClick = onPing)
                    .semantics { contentDescription = pingCd },
            )
            com.v2ray.ang.ui.compose.satra.FavoriteStar(
                favorite = row.favorite,
                onToggle = onFavorite,
                contentDescription = stringResource(if (row.favorite) R.string.sx_server_unfavorite else R.string.sx_server_favorite),
            )
            SatraIconButton(SatraIcons.More, stringResource(R.string.sx_more_options), onMore, tint = c.textSecondary, size = 20.dp, modifier = Modifier.width(36.dp))
        }
    }
}

/** One-time coach mark: a gently bobbing arrow that teaches pull-down-to-ping. */
@Composable
private fun PullToPingHint(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val c = Satra.colors
    val reduce = com.v2ray.ang.ui.compose.satra.rememberReduceMotion()
    val bob = if (reduce) 0f else {
        val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "pullHint")
        t.animateFloat(
            0f, 1f,
            androidx.compose.animation.core.infiniteRepeatable(
                androidx.compose.animation.core.tween(900, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                androidx.compose.animation.core.RepeatMode.Reverse,
            ),
            label = "bob",
        ).value
    }
    Row(
        modifier
            .fillMaxWidth()
            .clip(SatraShapes.Large)
            .background(c.infoSoft)
            .padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            SatraIcons.ArrowDown, null, tint = c.info,
            modifier = Modifier.size(20.dp).graphicsLayer { translationY = (bob - 0.5f) * 10.dp.toPx() },
        )
        Spacer(Modifier.width(12.dp))
        Text(stringResource(R.string.sx_coach_pull_ping), style = Satra.type.subhead, color = c.textPrimary, modifier = Modifier.weight(1f))
        SatraIconButton(SatraIcons.Close, stringResource(R.string.sx_dismiss), onDismiss, tint = c.textSecondary, size = 18.dp)
    }
}

/** One tap: test the list, connect to the quickest server. Shown on top of Servers and on Home. */
@Composable
fun SmartConnectCard(busy: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, compact: Boolean = false) {
    val c = Satra.colors
    com.v2ray.ang.ui.compose.satra.SatraCard(modifier = modifier, onClick = if (busy) null else onClick, padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = if (compact) 10.dp else 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(if (compact) 34.dp else 40.dp).clip(SatraShapes.Medium)
                    .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(c.accent, com.v2ray.ang.ui.compose.satra.SatraBrand.OrangeLight))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(SatraIcons.Bolt, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(if (busy) R.string.sx_smart_finding else R.string.sx_smart_connect),
                    style = Satra.type.headline, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (!compact) {
                    Text(stringResource(R.string.sx_smart_connect_sub), style = Satra.type.footnote, color = c.textSecondary, maxLines = 2)
                }
            }
            if (busy) {
                com.v2ray.ang.ui.compose.satra.SatraSpinner(size = 20.dp)
            } else {
                Icon(
                    if (androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl) SatraIcons.ChevronLeft else SatraIcons.ChevronRight,
                    null, tint = c.textTertiary, modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** Small, quiet facts about a config: tunnel, CDN fronting, last speed test. Nothing when none. */
@Composable
internal fun ServerTags(row: ServerRowUiModel, speedTesting: Boolean) {
    val c = Satra.colors
    val speed = when {
        speedTesting -> stringResource(R.string.sx_speed_testing) to c.textTertiary
        row.speedBps > 0L -> stringResource(
            R.string.sx_mbps,
            java.text.NumberFormat.getNumberInstance(com.v2ray.ang.ui.compose.satra.currentLocale())
                .apply { maximumFractionDigits = 1; minimumFractionDigits = 1 }
                .format(row.speedBps * 8 / 1_000_000.0),
        ) to c.success
        row.speedBps < 0L -> stringResource(R.string.sx_speed_failed) to c.textTertiary
        else -> null
    }
    // Under 1 MB is noise (pings, a few requests); hide it.
    val used = row.usedBytes.takeIf { it >= 1_048_576L }
    if (speed == null && used == null) return
    Row(
        Modifier.padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        speed?.let { (text, color) -> Tag(text, color) }
        used?.let { Tag(stringResource(R.string.sx_server_used, com.v2ray.ang.ui.compose.satra.formatBytes(it)), c.textTertiary) }
    }
}

@Composable
private fun Tag(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text,
        style = Satra.type.caption,
        color = color,
        maxLines = 1,
        modifier = Modifier.clip(SatraShapes.Small).background(color.copy(alpha = 0.10f)).padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

/** "Germany · VLESS / ws / tls", or the detection state while the exit country is unknown. */
@Composable
internal fun ServerSubtitle(row: ServerRowUiModel, onRetryLocation: () -> Unit) {
    val c = Satra.colors
    val location = row.locationLabel
    Row(verticalAlignment = Alignment.CenterVertically) {
        val state = when {
            location.isNotBlank() -> null
            row.geoState == GeoState.Locating -> stringResource(R.string.sx_geo_locating)
            row.geoState == GeoState.Failed -> stringResource(R.string.sx_geo_failed)
            else -> null
        }
        Text(
            listOf(location.ifBlank { state.orEmpty() }, row.typeDescription).filter { it.isNotBlank() }.joinToString(" · "),
            style = Satra.type.footnote,
            color = if (state != null) c.textTertiary else c.textSecondary,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (location.isBlank() && row.geoState == GeoState.Failed) {
            Text(
                stringResource(R.string.sx_geo_retry),
                style = Satra.type.footnote.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                color = c.accent,
                maxLines = 1,
                modifier = Modifier
                    .padding(start = 6.dp)
                    .clip(SatraShapes.Small)
                    .satraClickable(pressedScale = 0.92f, onClick = onRetryLocation)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
internal fun ServerActionsSheet(server: ServerRowUiModel, nav: AppNav, onAction: (MainAction) -> Unit, onDismiss: () -> Unit) {
    val c = Satra.colors
    SatraSheet(onDismiss = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Row(Modifier.padding(horizontal = 24.dp).padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                FlagBadge(server.countryCode, server.exitVerified, size = 46.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(server.remarks, style = Satra.type.title2, color = c.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOf(server.locationLabel, server.typeDescription).filter { it.isNotBlank() }.joinToString(" · "),
                        style = Satra.type.footnote, color = c.textSecondary,
                    )
                }
            }
            InsetGroup {
                row { SatraRow(stringResource(R.string.sx_server_use), icon = SatraIcons.Power, iconColor = c.accent, onClick = { onDismiss(); nav.action(MainAction.SelectServer(server.guid)) }) }
                row { SatraRow(stringResource(if (server.favorite) R.string.sx_server_unfavorite else R.string.sx_server_favorite), icon = SatraIcons.Star, iconColor = c.warning, onClick = { onDismiss(); onAction(MainAction.ToggleFavorite(server.guid)) }) }
                row { SatraRow(stringResource(R.string.sx_ping_this), subtitle = stringResource(R.string.sx_ping_this_sub), icon = SatraIcons.Pulse, iconColor = c.accent, onClick = { onDismiss(); onAction(MainAction.PingServer(server.guid)) }) }
                row { SatraRow(stringResource(R.string.sx_speed_test), subtitle = stringResource(R.string.sx_speed_test_sub), icon = SatraIcons.Bolt, iconColor = c.success, onClick = { onDismiss(); onAction(MainAction.SpeedTest(server.guid)) }) }
                row {
                    val (u1, d1) = remember(server.guid) { com.v2ray.ang.handler.UsageStore.serverUsage(server.guid, 1) }
                    val (u30, d30) = remember(server.guid) { com.v2ray.ang.handler.UsageStore.serverUsage(server.guid, 30) }
                    SatraRow(
                        stringResource(R.string.sx_server_usage),
                        subtitle = stringResource(R.string.sx_server_usage_sub, com.v2ray.ang.ui.compose.satra.formatBytes(d1), com.v2ray.ang.ui.compose.satra.formatBytes(u1), com.v2ray.ang.ui.compose.satra.formatBytes(d30), com.v2ray.ang.ui.compose.satra.formatBytes(u30)),
                        icon = SatraIcons.Chart, iconColor = c.info, onClick = {},
                    )
                }
                row { SatraRow(stringResource(R.string.sx_server_recheck), icon = SatraIcons.Globe, iconColor = c.info, onClick = { onDismiss(); onAction(MainAction.RecheckServer(server.guid)) }) }
                row { SatraRow(stringResource(R.string.sx_server_edit), icon = SatraIcons.Edit, iconColor = c.textSecondary, onClick = { onDismiss(); nav.action(MainAction.EditServer(server.guid, server.profile)) }) }
            }
            Spacer(Modifier.height(12.dp))
            InsetGroup {
                row { SatraRow(stringResource(R.string.sx_server_copy), icon = SatraIcons.Copy, iconColor = c.info, onClick = { onDismiss(); nav.action(MainAction.ShareClipboard(server.guid)) }) }
                row { SatraRow(stringResource(R.string.sx_server_qr), icon = SatraIcons.Qr, iconColor = c.info, onClick = { onDismiss(); onAction(MainAction.ShareQRCode(server.guid)) }) }
                row { SatraRow(stringResource(R.string.sx_server_copy_full), icon = SatraIcons.File, iconColor = c.info, onClick = { onDismiss(); nav.action(MainAction.ShareFullContent(server.guid)) }) }
            }
            Spacer(Modifier.height(12.dp))
            InsetGroup {
                row { SatraRow(stringResource(R.string.sx_delete), icon = SatraIcons.Trash, iconColor = c.danger, destructive = true, onClick = { onDismiss(); onAction(MainAction.DeleteWithUndo(server.guid)) }) }
            }
        }
    }
}

private data class ManualType(val label: String, val type: EConfigType)

@Composable
private fun ManualSheet(onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val types = remember {
        listOf(
            ManualType("VLESS", EConfigType.VLESS), ManualType("VMess", EConfigType.VMESS),
            ManualType("Trojan", EConfigType.TROJAN), ManualType("Shadowsocks", EConfigType.SHADOWSOCKS),
            ManualType("Hysteria2", EConfigType.HYSTERIA2), ManualType("WireGuard", EConfigType.WIREGUARD),
            ManualType("SOCKS", EConfigType.SOCKS), ManualType("HTTP", EConfigType.HTTP),
        )
    }
    SatraSheet(onDismiss = onDismiss) {
        SheetTitle(stringResource(R.string.sx_add_manual), subtitle = stringResource(R.string.sx_add_manual_msg))
        Column(Modifier.verticalScroll(rememberScrollState())) {
            InsetGroup {
                types.forEach { t ->
                    row { SatraRow(t.label, icon = SatraIcons.Servers, iconColor = Satra.colors.accent, chevron = true, onClick = { onPick(t.type.value) }) }
                }
            }
        }
    }
}
