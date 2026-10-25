package com.v2ray.ang.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.R
import com.v2ray.ang.handler.DayUsage
import com.v2ray.ang.handler.GeoIpResolver
import com.v2ray.ang.ui.compose.satra.ConnectOrb
import com.v2ray.ang.ui.compose.satra.OrbPhase
import com.v2ray.ang.ui.compose.satra.PingButton
import com.v2ray.ang.ui.compose.satra.Satra
import com.v2ray.ang.ui.compose.satra.SatraBarChart
import com.v2ray.ang.ui.compose.satra.SatraButton
import com.v2ray.ang.ui.compose.satra.SatraButtonStyle
import com.v2ray.ang.ui.compose.satra.SatraCard
import com.v2ray.ang.ui.compose.satra.SatraEmptyState
import com.v2ray.ang.ui.compose.satra.SatraIconButton
import com.v2ray.ang.ui.compose.satra.SatraIcons
import com.v2ray.ang.ui.compose.satra.SatraMenu
import com.v2ray.ang.ui.compose.satra.SatraMenuItem
import com.v2ray.ang.ui.compose.satra.SatraProgressBar
import com.v2ray.ang.ui.compose.satra.SatraRing
import com.v2ray.ang.ui.compose.satra.SatraShapes
import com.v2ray.ang.ui.compose.satra.SatraSparkline
import com.v2ray.ang.ui.compose.satra.SatraSpace
import com.v2ray.ang.ui.compose.satra.formatBytes
import com.v2ray.ang.ui.compose.satra.formatClock
import com.v2ray.ang.ui.compose.satra.formatSpeed
import com.v2ray.ang.ui.compose.satra.pingGrade
import com.v2ray.ang.ui.compose.satra.satraClickable
import com.v2ray.ang.ui.main.ConnectPhase
import com.v2ray.ang.ui.main.LiveUiState
import com.v2ray.ang.ui.main.MainUiState
import com.v2ray.ang.ui.main.QuotaUi
import com.v2ray.ang.ui.main.ServerRowUiModel
import kotlinx.coroutines.delay
import java.util.Locale
import java.util.concurrent.TimeUnit

enum class AddMethod { Smart, Scan, Clipboard, File, Subscription, More }

/**
 * Home fits on one screen: the orb shrinks with the available height so the server card, the
 * traffic card and the subscription card are always visible without scrolling (a scroll is only a
 * safety net for extreme font sizes).
 */
@Composable
fun HomeScreen(
    state: MainUiState,
    live: LiveUiState,
    week: List<DayUsage>,
    onToggle: () -> Unit,
    onPing: () -> Unit,
    onOpenServers: () -> Unit,
    onOpenUsage: () -> Unit,
    onAdd: (AddMethod) -> Unit,
    modifier: Modifier = Modifier,
    onSmartConnect: () -> Unit = {},
    onLocate: (String) -> Unit = {},
    onDismissAddSheet: () -> Unit = {},
    proxyEndpoints: List<com.v2ray.ang.handler.ProxyEndpoint> = emptyList(),
    onCopy: (String) -> Unit = {},
    smartRoutingOn: Boolean = true,
    smartRegion: SmartRegion = SmartRegion.Iran,
    smartTip: Boolean = false,
    onSmartRouting: (Boolean) -> Unit = {},
    onRoutingInfo: () -> Unit = {},
    onDismissSmartTip: () -> Unit = {},
    updateVersion: String? = null,
    updateProgress: Float? = null,
    onUpdate: () -> Unit = {},
    mode: com.v2ray.ang.handler.ModeEngine.Mode = com.v2ray.ang.handler.ModeEngine.Mode.NORMAL,
    onMode: (com.v2ray.ang.handler.ModeEngine.Mode) -> Unit = {},
) {
    val hasServers = state.groups.isNotEmpty() && state.selectedGuid != null
    Column(modifier.fillMaxSize()) {
        HomeTopBar(onAdd = onAdd, importing = state.importing, connected = state.isRunning)
        if (updateVersion != null) {
            SatraCard(Modifier.padding(horizontal = SatraSpace.Gutter).padding(top = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.sx_home_update_available, updateVersion),
                            style = Satra.type.headline,
                            color = Satra.colors.textPrimary,
                        )
                        if (updateProgress != null) {
                            Spacer(Modifier.height(8.dp))
                            SatraProgressBar(fraction = updateProgress.coerceIn(0f, 1f), height = 6.dp)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    SatraButton(
                        if (updateProgress != null) stringResource(R.string.sx_loading)
                        else stringResource(R.string.sx_home_update_action),
                        onClick = onUpdate,
                        compact = true,
                        enabled = updateProgress == null,
                    )
                }
            }
        }
        if (!hasServers) {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.Center,
            ) {
                SatraEmptyState(
                    icon = SatraIcons.Shield,
                    title = stringResource(R.string.sx_home_empty_title),
                    message = stringResource(R.string.sx_home_empty_add_msg),
                    actionText = stringResource(R.string.sx_add),
                    onAction = { onAdd(AddMethod.Smart) },
                    secondaryText = stringResource(R.string.sx_add_more_ways),
                    onSecondary = { onAdd(AddMethod.More) },
                )
            }
        } else {
            var details by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
                // Title + chips + server card + summary strip + margins; the orb takes what is left.
                val fixed = (64 + 40 + 76 + 64 + 36 + 62 + 78 + (if (smartTip) 46 else 0)) * fontScale
                val orb = (maxHeight.value - fixed).coerceIn(112f, 264f).dp
                val connected = state.phase == ConnectPhase.Connected && live.sessionStartMs > 0L
                val exitPoint = ExitLocation.point.value
                val mapPoint = when {
                    !connected -> null
                    exitPoint != null && ExitLocation.sessionStartMs == live.sessionStartMs -> exitPoint
                    else -> null
                }
                WorldMapBackdrop(mapPoint, Modifier.fillMaxSize())
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.weight(1f))
                    OrbSection(state = state, live = live, onToggle = onToggle, orbSize = orb)
                    Spacer(Modifier.weight(1f))
                    ServerCard(
                        row = state.selectedRow,
                        state = state,
                        onPing = onPing,
                        onOpen = onOpenServers,
                        onLocate = onLocate,
                        modifier = Modifier.padding(horizontal = SatraSpace.Gutter),
                    )
                    Spacer(Modifier.height(8.dp))
                    SmartRoutingRow(
                        on = smartRoutingOn, region = smartRegion, showTip = smartTip,
                        onToggle = onSmartRouting, onInfo = onRoutingInfo, onDismissTip = onDismissSmartTip,
                        modifier = Modifier.padding(horizontal = SatraSpace.Gutter),
                    )
                    Spacer(Modifier.height(6.dp))
                    ModeBar(current = mode, onPick = onMode, modifier = Modifier.padding(horizontal = SatraSpace.Gutter))
                    Spacer(Modifier.height(8.dp))
                    SummaryStrip(live, state.quota, onClick = { details = true }, modifier = Modifier.padding(horizontal = SatraSpace.Gutter))
                    Spacer(Modifier.height(12.dp))
                }
            }
            if (details) {
                com.v2ray.ang.ui.compose.satra.SatraSheet(onDismiss = { details = false }) {
                    Text(
                        stringResource(R.string.sx_home_details),
                        style = Satra.type.title2, color = Satra.colors.textPrimary,
                        modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 12.dp),
                    )
                    Column(Modifier.padding(horizontal = SatraSpace.Gutter), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (state.isRunning && proxyEndpoints.isNotEmpty()) {
                            ProxyEndpointCard(endpoints = proxyEndpoints, onCopy = onCopy)
                        } else {
                            com.v2ray.ang.ui.servers.SmartConnectCard(
                                busy = state.smartConnecting,
                                onClick = { details = false; onSmartConnect() },
                                compact = true,
                            )
                        }
                        val hsCtx = androidx.compose.ui.platform.LocalContext.current
                        com.v2ray.ang.ui.compose.satra.SatraCard(onClick = {
                            details = false
                            hsCtx.startActivity(android.content.Intent(hsCtx, com.v2ray.ang.ui.hotspot.HotspotShareActivity::class.java))
                        }) {
                            com.v2ray.ang.ui.compose.satra.SatraRow(
                                stringResource(R.string.sx_hs_title), subtitle = stringResource(R.string.sx_hs_sub),
                                icon = SatraIcons.Wifi, iconColor = Satra.colors.info, chevron = true,
                            )
                        }
                        TrafficCard(live, onOpenUsage = { details = false; onOpenUsage() })
                        state.quota?.let { QuotaCard(it) }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
    if (state.addSheet) {
        AddSheet(onAdd = { m -> onDismissAddSheet(); onAdd(m) }, onDismiss = onDismissAddSheet)
    }
}

/** The "more ways to add" sheet: everything beyond the one-tap paste. */
@Composable
internal fun AddSheet(onAdd: (AddMethod) -> Unit, onDismiss: () -> Unit) {
    val c = Satra.colors
    com.v2ray.ang.ui.compose.satra.SatraSheet(onDismiss = onDismiss) {
        Text(
            stringResource(R.string.sx_add_more_ways),
            style = Satra.type.title2, color = c.textPrimary,
            modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 4.dp),
        )
        Text(
            stringResource(R.string.sx_add_sheet_msg),
            style = Satra.type.footnote, color = c.textSecondary,
            modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 14.dp),
        )
        com.v2ray.ang.ui.compose.satra.InsetGroup {
            row { com.v2ray.ang.ui.compose.satra.SatraRow(stringResource(R.string.sx_add_clipboard), icon = SatraIcons.Clipboard, iconColor = c.accent, onClick = { onAdd(AddMethod.Clipboard) }) }
            row { com.v2ray.ang.ui.compose.satra.SatraRow(stringResource(R.string.sx_add_scan), icon = SatraIcons.Scan, iconColor = c.info, onClick = { onAdd(AddMethod.Scan) }) }
            row { com.v2ray.ang.ui.compose.satra.SatraRow(stringResource(R.string.sx_add_file), icon = SatraIcons.File, iconColor = c.success, onClick = { onAdd(AddMethod.File) }) }
            row { com.v2ray.ang.ui.compose.satra.SatraRow(stringResource(R.string.sx_add_subscription), subtitle = stringResource(R.string.sx_add_subscription_sub), icon = SatraIcons.Link, iconColor = c.warning, chevron = true, onClick = { onAdd(AddMethod.Subscription) }) }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun HomeTopBar(onAdd: (AddMethod) -> Unit, importing: Boolean, connected: Boolean) {
    val c = Satra.colors
    Row(
        Modifier
            .statusBarsPadding()
            .fillMaxWidth()
            .height(52.dp)
            .padding(start = SatraSpace.Gutter, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        com.v2ray.ang.ui.compose.satra.SatraLogo(connected = connected, size = 28.dp)
        Spacer(Modifier.width(10.dp))
        Text(
            stringResource(R.string.app_name),
            style = Satra.type.headline.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
            color = c.textPrimary,
            modifier = Modifier.weight(1f),
        )
        SatraIconButton(SatraIcons.Scan, stringResource(R.string.sx_add_scan), { onAdd(AddMethod.Scan) }, tint = c.textSecondary, size = 20.dp)
        Spacer(Modifier.width(4.dp))
        // One-tap add: reads the clipboard and works out what it is.
        Row(
            Modifier
                .clip(CircleShape)
                .background(c.accent)
                .satraClickable(pressedScale = 0.94f, onLongClick = { onAdd(AddMethod.More) }, onClick = { if (!importing) onAdd(AddMethod.Smart) })
                .padding(start = 10.dp, end = 14.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (importing) com.v2ray.ang.ui.compose.satra.SatraSpinner(size = 16.dp, color = androidx.compose.ui.graphics.Color.White, strokeWidth = 2.dp)
            else Icon(SatraIcons.Plus, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.sx_add), style = Satra.type.subhead.copy(fontWeight = FontWeight.SemiBold), color = androidx.compose.ui.graphics.Color.White)
        }
    }
}

private val RoundedCornerShapeSmall = androidx.compose.foundation.shape.RoundedCornerShape(7.dp)

// ───────────────────────────── Orb ─────────────────────────────

@Composable
private fun OrbSection(state: MainUiState, live: LiveUiState, onToggle: () -> Unit, orbSize: androidx.compose.ui.unit.Dp = 292.dp) {
    val c = Satra.colors
    val phase = when (state.phase) {
        ConnectPhase.Idle -> OrbPhase.Idle
        ConnectPhase.Connecting -> OrbPhase.Connecting
        ConnectPhase.Connected -> OrbPhase.Connected
        ConnectPhase.Disconnecting -> OrbPhase.Disconnecting
        ConnectPhase.Failed -> OrbPhase.Failed
    }
    val title = stringResource(
        when (state.phase) {
            ConnectPhase.Idle -> R.string.sx_state_idle
            ConnectPhase.Connecting -> R.string.sx_state_connecting
            ConnectPhase.Connected -> R.string.sx_state_connected
            ConnectPhase.Disconnecting -> R.string.sx_state_disconnecting
            ConnectPhase.Failed -> R.string.sx_state_failed
        },
    )
    val hint = stringResource(
        when (state.phase) {
            ConnectPhase.Idle -> R.string.sx_hint_connect
            ConnectPhase.Connecting -> R.string.sx_hint_cancel
            ConnectPhase.Connected -> R.string.sx_hint_disconnect
            ConnectPhase.Disconnecting -> R.string.sx_hint_wait
            ConnectPhase.Failed -> R.string.sx_hint_retry
        },
    )
    val titleColor by animateColorAsState(
        when (state.phase) {
            ConnectPhase.Connected -> c.success
            ConnectPhase.Failed -> c.danger
            else -> c.textPrimary
        },
        tween(700), label = "orbTitle",
    )
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        ConnectOrb(
            phase = phase,
            onClick = onToggle,
            stateLabel = title,
            actionLabel = hint,
            modifier = Modifier.size(orbSize),
        )
        Text(
            title,
            style = Satra.type.title.copy(fontWeight = FontWeight.Bold),
            color = titleColor,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        AnimatedContent(
            targetState = Triple(state.phase, state.errorMessage, hint),
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
            label = "orbSub",
        ) { (p, err, h) ->
            when {
                p == ConnectPhase.Connected && live.sessionStartMs > 0L -> SessionClock(live.sessionStartMs)
                p == ConnectPhase.Failed && !err.isNullOrBlank() -> Text(
                    err, style = Satra.type.callout, color = c.textSecondary, textAlign = TextAlign.Center,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 40.dp),
                )
                else -> Text(h, style = Satra.type.callout, color = c.textSecondary, textAlign = TextAlign.Center)
            }
        }
        if (state.phase == ConnectPhase.Connected && live.sessionStartMs > 0L) ConnectedChips(live.sessionStartMs)
    }
}

@Composable
private fun SessionClock(startMs: Long) {
    var elapsed by remember(startMs) { mutableLongStateOf(((System.currentTimeMillis() - startMs) / 1000L)) }
    LaunchedEffect(startMs) {
        while (true) {
            elapsed = (System.currentTimeMillis() - startMs) / 1000L
            delay(1000)
        }
    }
    Text(
        formatClock(elapsed),
        style = Satra.type.numberMedium,
        color = Satra.colors.textSecondary,
    )
}

// ───────────────────────────── Server card ─────────────────────────────

@Composable
private fun ServerCard(
    row: ServerRowUiModel?,
    state: MainUiState,
    onPing: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    onLocate: (String) -> Unit = {},
) {
    val c = Satra.colors
    SatraCard(modifier = modifier, onClick = onOpen, padding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FlagBadge(row?.countryCode, row?.exitVerified == true, size = 46.dp, locating = row?.geoState == com.v2ray.ang.ui.main.GeoState.Locating)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    row?.remarks?.takeIf { it.isNotBlank() } ?: stringResource(R.string.sx_home_choose_server),
                    style = Satra.type.headline, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                val location = row?.locationLabel.orEmpty()
                val sub = buildString {
                    if (location.isNotBlank()) append(location)
                    row?.typeDescription?.takeIf { it.isNotBlank() }?.let {
                        if (isNotEmpty()) append(" · ")
                        append(it)
                    }
                }
                if (row != null && location.isBlank() && row.geoState != com.v2ray.ang.ui.main.GeoState.Unknown) {
                    com.v2ray.ang.ui.servers.ServerSubtitle(row) { onLocate(row.guid) }
                } else if (sub.isNotBlank()) {
                    Text(sub, style = Satra.type.footnote, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                } else {
                    Text(
                        stringResource(R.string.sx_location_unknown),
                        style = Satra.type.footnote, color = c.textTertiary, maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            PingButton(
                ms = row?.testDelayMillis?.takeIf { it != 0L },
                testing = state.isTesting,
                // Connected: measured through the running tunnel. Otherwise a private test core.
                enabled = row != null && !state.isTesting,
                onClick = onPing,
                idleLabel = stringResource(R.string.sx_ping_test),
                testingLabel = stringResource(R.string.sx_ping_testing),
                failedLabel = stringResource(R.string.sx_ping_failed),
                contentDescription = stringResource(R.string.sx_ping_cd),
            )
        }
    }
}

/** Breathing alpha for the globe while the exit country is being measured (only composed then). */
@Composable
private fun rememberLocatePulse(): Float {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "locate")
    val v by transition.animateFloat(
        0.35f, 1f,
        androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(900),
            androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "locateAlpha",
    )
    return v
}

/** Circular flag. Unknown location shows a neutral globe; unverified (address-based) flags are slightly dimmed. */
@Composable
fun FlagBadge(
    countryCode: String?,
    verified: Boolean,
    size: androidx.compose.ui.unit.Dp = 40.dp,
    modifier: Modifier = Modifier,
    locating: Boolean = false,
) {
    val c = Satra.colors
    val reduce = com.v2ray.ang.ui.compose.satra.rememberReduceMotion()
    val pulse = if (locating && countryCode.isNullOrBlank() && !reduce) rememberLocatePulse() else 1f
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(c.fill),
        contentAlignment = Alignment.Center,
    ) {
        if (countryCode.isNullOrBlank()) {
            Icon(
                SatraIcons.Globe, null,
                tint = if (locating) c.accent.copy(alpha = pulse) else c.textTertiary,
                modifier = Modifier.size(size * 0.5f),
            )
        } else {
            Text(
                GeoIpResolver.flagEmoji(countryCode),
                fontSize = (size.value * 0.54f).sp,
                modifier = Modifier.semantics {
                    contentDescription = GeoIpResolver.countryNameForCode(countryCode).orEmpty()
                },
            )
        }
    }
}

// ───────────────────────────── Live / today / quota ─────────────────────────────

/**
 * Download / upload / total for this session and for today, plus live speed while connected.
 * Compact on purpose: Home must fit one screen.
 */
@Composable
private fun TrafficCard(live: LiveUiState, onOpenUsage: () -> Unit, modifier: Modifier = Modifier) {
    val c = Satra.colors
    SatraCard(modifier, onClick = onOpenUsage, padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
        if (live.running) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SpeedStat(SatraIcons.ArrowDown, stringResource(R.string.sx_live_download), formatSpeed(live.downBps), c.success, Modifier.weight(1f))
                SpeedStat(SatraIcons.ArrowUp, stringResource(R.string.sx_live_upload), formatSpeed(live.upBps), c.info, Modifier.weight(1f))
            }
            SatraSparkline(
                values = live.history.ifEmpty { listOf(0f, 0f) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                height = 30.dp,
            )
        }
        TrafficTableHeader()
        if (live.running) TrafficRow(stringResource(R.string.sx_live_session), live.sessionDown, live.sessionUp)
        TrafficRow(stringResource(R.string.sx_home_today), live.todayDown, live.todayUp)
    }
}

/** One calm line: live speeds (or today's total) and the plan left; opens the details sheet. */
@Composable
private fun SummaryStrip(live: LiveUiState, quota: QuotaUi?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Satra.colors
    SatraCard(modifier.fillMaxWidth(), onClick = onClick, padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (live.running) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("↓ " + formatSpeed(live.downBps), style = Satra.type.numberSmall, color = c.success, maxLines = 1)
                        Spacer(Modifier.width(12.dp))
                        Text("↑ " + formatSpeed(live.upBps), style = Satra.type.numberSmall, color = c.info, maxLines = 1)
                    }
                } else {
                    Text(stringResource(R.string.sx_home_today), style = Satra.type.caption, color = c.textSecondary)
                    Text(formatBytes(live.todayDown + live.todayUp), style = Satra.type.numberSmall, color = c.textPrimary, maxLines = 1)
                }
            }
            if (quota != null) {
                val left = quota.fraction?.let { 1f - it }
                val tone = quotaColor(listOfNotNull(left, quota.timeLeftFraction(System.currentTimeMillis())).minOrNull() ?: 1f, c.success, c.warning, c.danger)
                SatraRing(fraction = left ?: 1f, size = 26.dp, stroke = 3.dp, color = tone) {}
                Spacer(Modifier.width(8.dp))
                Text(
                    if (quota.totalBytes > 0L) formatBytes((quota.totalBytes - quota.usedBytes).coerceAtLeast(0L)) else "∞",
                    style = Satra.type.numberSmall, color = c.textPrimary, maxLines = 1,
                )
                Spacer(Modifier.width(6.dp))
            }
            Icon(SatraIcons.ChevronRight, null, tint = c.textTertiary, modifier = Modifier.size(16.dp).then(if (androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl) Modifier.scale(-1f, 1f) else Modifier))
        }
    }
}

/** Header of the download / upload / total table (Home and Usage). */
@Composable
internal fun TrafficTableHeader() {
    val c = Satra.colors
    Row(Modifier.fillMaxWidth().padding(bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.weight(1.1f))
        FitText("↓ " + stringResource(R.string.sx_live_download), Satra.type.caption, c.success, Modifier.weight(1f))
        FitText("↑ " + stringResource(R.string.sx_live_upload), Satra.type.caption, c.info, Modifier.weight(1f))
        FitText(stringResource(R.string.sx_total), Satra.type.caption, c.textSecondary, Modifier.weight(1f))
    }
}

@Composable
internal fun TrafficRow(label: String, down: Long, up: Long) {
    val c = Satra.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        FitText(label, Satra.type.footnote, c.textSecondary, Modifier.weight(1.1f), TextAlign.Start)
        FitText(formatBytes(down), Satra.type.numberSmall, c.textPrimary, Modifier.weight(1f))
        FitText(formatBytes(up), Satra.type.numberSmall, c.textPrimary, Modifier.weight(1f))
        FitText(formatBytes(down + up), Satra.type.numberSmall.copy(fontWeight = FontWeight.SemiBold), c.accent, Modifier.weight(1f))
    }
}

/** One line that shrinks (down to 60%) instead of cutting off, so large fonts stay readable. */
@Composable
private fun FitText(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier,
    align: TextAlign = TextAlign.End,
) {
    val max = style.fontSize
    androidx.compose.foundation.text.BasicText(
        text,
        modifier,
        style = style.copy(color = color, textAlign = align),
        maxLines = 1,
        autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = max * 0.6f, maxFontSize = max),
    )
}

@Composable
private fun SpeedStat(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, tint: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(tint.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(15.dp))
        }
        Spacer(Modifier.width(8.dp))
        Column {
            Text(label, style = Satra.type.caption, color = Satra.colors.textSecondary)
            Text(value, style = Satra.type.numberMedium, color = Satra.colors.textPrimary, maxLines = 1)
        }
    }
}

/**
 * Subscription card. Its colour follows whichever runs out first, data or time:
 * green while both have more than half left, through orange, to red under 15 %.
 */
@Composable
private fun QuotaCard(quota: QuotaUi, modifier: Modifier = Modifier) {
    val c = Satra.colors
    val now = System.currentTimeMillis()
    val dataLeft = quota.fraction?.let { 1f - it }
    val timeLeft = quota.timeLeftFraction(now)
    val health = listOfNotNull(dataLeft, timeLeft).minOrNull() ?: 1f
    val tone by animateColorAsState(quotaColor(health, c.success, c.warning, c.danger), tween(500), label = "quotaTone")
    SatraCard(modifier, padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SatraRing(fraction = dataLeft ?: 1f, size = 54.dp, stroke = 6.dp, color = tone) {
                Text(
                    if (dataLeft != null) String.format(com.v2ray.ang.ui.compose.satra.currentLocale(), "%d%%", (dataLeft * 100).toInt()) else "∞",
                    style = Satra.type.caption.copy(fontWeight = FontWeight.Bold), color = c.textPrimary,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(quota.name.ifBlank { stringResource(R.string.sx_quota_title) }, style = Satra.type.headline, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (quota.totalBytes > 0L) stringResource(
                        R.string.sx_quota_left,
                        formatBytes((quota.totalBytes - quota.usedBytes).coerceAtLeast(0L)),
                        formatBytes(quota.totalBytes),
                    ) else stringResource(R.string.sx_quota_unlimited),
                    style = Satra.type.footnote, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (quota.expireMs > 0L) {
                    val days = TimeUnit.MILLISECONDS.toDays(quota.expireMs - now)
                    Spacer(Modifier.height(5.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SatraProgressBar(fraction = timeLeft ?: 0f, color = tone, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (quota.expireMs <= now) stringResource(R.string.sx_quota_expired)
                            else if (days < 1) stringResource(R.string.sx_quota_expires_today)
                            else pluralStringResource(R.plurals.sx_days_left, days.toInt(), days.toInt()),
                            style = Satra.type.caption.copy(fontWeight = FontWeight.SemiBold),
                            color = if (health < 0.15f) c.danger else c.textSecondary,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/** Smoothly blends green → orange → red as [health] (share left of the scarcer resource) drops. */
internal fun quotaColor(
    health: Float,
    good: androidx.compose.ui.graphics.Color,
    warn: androidx.compose.ui.graphics.Color,
    bad: androidx.compose.ui.graphics.Color,
): androidx.compose.ui.graphics.Color {
    val h = health.coerceIn(0f, 1f)
    return when {
        h >= 0.5f -> good
        h >= 0.15f -> androidx.compose.ui.graphics.lerp(warn, good, ((h - 0.15f) / 0.35f).let { it * it })
        else -> androidx.compose.ui.graphics.lerp(bad, warn, h / 0.15f)
    }
}
