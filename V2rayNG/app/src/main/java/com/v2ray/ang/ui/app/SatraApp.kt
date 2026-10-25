package com.v2ray.ang.ui.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.dto.CheckUpdateResult
import com.v2ray.ang.handler.ApkUpdateCache
import com.v2ray.ang.handler.UpdateCheckerManager
import com.v2ray.ang.handler.UsageStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.v2ray.ang.ui.compose.satra.SatraBackground
import com.v2ray.ang.ui.compose.satra.SatraIcons
import com.v2ray.ang.ui.compose.satra.SatraShapes
import com.v2ray.ang.ui.compose.satra.SatraTab
import com.v2ray.ang.ui.compose.satra.SatraTabBar
import com.v2ray.ang.ui.compose.satra.Satra
import com.v2ray.ang.ui.compose.satra.satraClickable
import com.v2ray.ang.ui.home.AddMethod
import com.v2ray.ang.ui.home.HomeScreen
import com.v2ray.ang.ui.main.MainAction
import com.v2ray.ang.ui.main.MainDestination
import com.v2ray.ang.ui.main.MainViewModel
import com.v2ray.ang.ui.more.MoreScreen
import com.v2ray.ang.ui.servers.ServersScreen
import com.v2ray.ang.ui.subs.SubsScreen
import com.v2ray.ang.ui.subs.SubsViewModel
import com.v2ray.ang.ui.usage.UsageScreen
import com.v2ray.ang.ui.usage.UsageViewModel

/** Everything the tab UI needs from the hosting activity (permissions, launchers, other screens). */
interface AppNav {
    fun action(action: MainAction)
    fun navigate(destination: MainDestination)
    fun openSettingsPage(destination: com.v2ray.ang.ui.settings.SettingsDestination)
    fun editSubscription(subId: String?)
    fun scanQr(onResult: (String) -> Unit)
    fun openUsageAccess()
    /** Switch to the Xray TUN engine (needed for per-app limits) and restart the VPN. */
    fun enableAppEnforcement()
    fun openSystemVpnSettings()
    fun share(text: String)
    fun copy(text: String)
    /** Apply a VPN/Proxy-only mode change to a running connection (permission-aware). */
    fun reconnectForModeChange() {}
}

object Tabs { const val Home = 0; const val Servers = 1; const val Subs = 2; const val Usage = 3; const val More = 4 }

@Composable
fun SatraApp(
    mainVm: MainViewModel,
    subsVm: SubsViewModel,
    usageVm: UsageViewModel,
    nav: AppNav,
    initialTab: Int = Tabs.Home,
) {
    var tab by rememberSaveable { mutableIntStateOf(initialTab) }
    LaunchedEffect(initialTab) { tab = initialTab }
    val state by mainVm.uiState.collectAsState()
    val live by mainVm.liveState.collectAsState()
    val holder = rememberSaveableStateHolder()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var updateResult by remember { mutableStateOf<CheckUpdateResult?>(null) }
    var updateProgress by remember { mutableFloatStateOf(-1f) }
    LaunchedEffect(Unit) {
        runCatching {
            val r = withContext(Dispatchers.IO) { UpdateCheckerManager.checkForUpdate(false) }
            if (r.hasUpdate && !r.downloadUrl.isNullOrBlank()) updateResult = r
        }
    }
    val tabs = listOf(
        SatraTab(SatraIcons.Home, stringResource(R.string.sx_tab_home)),
        SatraTab(SatraIcons.Servers, stringResource(R.string.sx_tab_servers)),
        SatraTab(SatraIcons.Layers, stringResource(R.string.sx_tab_subs)),
        SatraTab(SatraIcons.Chart, stringResource(R.string.sx_tab_usage)),
        SatraTab(SatraIcons.Sliders, stringResource(R.string.sx_tab_more)),
    )
    SatraBackground {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Crossfade(targetState = tab, animationSpec = tween(180), label = "tabs") { current ->
                    holder.SaveableStateProvider(current) {
                        when (current) {
                            Tabs.Home -> {
                                val week by usageVm.week.collectAsState()
                                // Mode captured when the connection came up, so a later unapplied change doesn't lie.
                                // Keyed on the session start too: a quick reconnect (mode switch) may never flip isRunning.
                                val proxyOnly = rememberSaveable(state.isRunning, live.sessionStartMs) { state.isRunning && com.v2ray.ang.handler.ProxyEndpoints.isProxyOnlyMode() }
                                val endpoints = remember(proxyOnly, state.isRunning, live.sessionStartMs) {
                                    if (proxyOnly) com.v2ray.ang.handler.ProxyEndpoints.current() else emptyList()
                                }
                                var smartOn by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(com.v2ray.ang.handler.RoutingPolicy.smartOn()) }
                                var smartTip by androidx.compose.runtime.remember {
                                    androidx.compose.runtime.mutableStateOf(!com.v2ray.ang.handler.MmkvManager.decodeSettingsBool(com.v2ray.ang.handler.RoutingPolicy.PREF_TIP_SEEN, false))
                                }
                                val russia = androidx.compose.runtime.remember { com.v2ray.ang.handler.RoutingPolicy.isRussia(com.v2ray.ang.handler.MmkvManager.decodeRoutingRulesets()) }
                                val tipSeen = {
                                    smartTip = false
                                    com.v2ray.ang.handler.MmkvManager.encodeSettings(com.v2ray.ang.handler.RoutingPolicy.PREF_TIP_SEEN, true)
                                    Unit
                                }
                                val mode by com.v2ray.ang.handler.ModeEngine.mode.collectAsState()
                                LaunchedEffect(Unit) { com.v2ray.ang.handler.ModeEngine.current() }
                                HomeScreen(
                                    mode = mode,
                                    onMode = { m ->
                                        com.v2ray.ang.handler.ModeEngine.set(m)
                                        // Same path as the smart-routing switch: reconnect only if already connected.
                                        com.v2ray.ang.handler.SettingsChangeManager.makeRestartService()
                                        nav.reconnectForModeChange()
                                    },
                                    smartRoutingOn = smartOn,
                                    smartRegion = if (russia) com.v2ray.ang.ui.home.SmartRegion.Russia else if (com.v2ray.ang.handler.RoutingPolicy.isChina(com.v2ray.ang.handler.MmkvManager.decodeRoutingRulesets())) com.v2ray.ang.ui.home.SmartRegion.China else com.v2ray.ang.ui.home.SmartRegion.Iran,
                                    smartTip = smartTip,
                                    onSmartRouting = { on ->
                                        smartOn = on; tipSeen()
                                        com.v2ray.ang.handler.RoutingPolicy.setSmart(on)
                                        com.v2ray.ang.handler.SettingsChangeManager.makeRestartService()
                                        nav.reconnectForModeChange()
                                    },
                                    onRoutingInfo = { tipSeen(); nav.navigate(MainDestination.Routing) },
                                    onDismissSmartTip = tipSeen,
                                    proxyEndpoints = endpoints,
                                    onCopy = { nav.copy(it) },
                                    state = state, live = live, week = week,
                                    onToggle = { nav.action(MainAction.ToggleService) },
                                    onPing = { nav.action(MainAction.TestCurrentServer) },
                                    onOpenServers = { tab = Tabs.Servers },
                                    onOpenUsage = { tab = Tabs.Usage },
                                    onAdd = { method ->
                                        when (method) {
                                            AddMethod.Smart, AddMethod.Clipboard -> nav.action(MainAction.ImportClipboard)
                                            AddMethod.Scan -> nav.action(MainAction.ImportQRcode)
                                            AddMethod.File -> nav.action(MainAction.ImportConfigLocal)
                                            AddMethod.Subscription -> { tab = Tabs.Subs }
                                            AddMethod.More -> nav.action(MainAction.ShowAddSheet(true))
                                        }
                                    },
                                    onSmartConnect = { nav.action(MainAction.SmartConnect) },
                                    onLocate = { guid -> nav.action(MainAction.LocateServer(guid)) },
                                    onDismissAddSheet = { nav.action(MainAction.ShowAddSheet(false)) },
                                    updateVersion = updateResult?.latestVersion,
                                    updateProgress = updateProgress.takeIf { it >= 0f },
                                    onUpdate = {
                                        val r = updateResult ?: return@HomeScreen
                                        val ver = r.latestVersion ?: return@HomeScreen
                                        val url = r.downloadUrl ?: return@HomeScreen
                                        scope.launch {
                                            val cached = ApkUpdateCache.cachedApk(context, ver)
                                            if (ApkUpdateCache.isComplete(cached, null) && cached.length() > 1_000_000L) {
                                                ApkUpdateCache.openOrInstall(context, cached)
                                                return@launch
                                            }
                                            updateProgress = 0f
                                            runCatching {
                                                val apk = ApkUpdateCache.download(context, ver, url) { p ->
                                                    val frac = if (p.total != null && p.total > 0) {
                                                        p.downloaded.toFloat() / p.total.toFloat()
                                                    } else 0f
                                                    updateProgress = frac
                                                }
                                                updateProgress = -1f
                                                ApkUpdateCache.openOrInstall(context, apk)
                                            }.onFailure { updateProgress = -1f }
                                        }
                                    },
                                )
                            }
                            Tabs.Servers -> ServersScreen(mainVm, nav)
                            Tabs.Subs -> SubsScreen(subsVm, mainVm, nav)
                            Tabs.Usage -> UsageScreen(usageVm, live, nav)
                            else -> MoreScreen(mainVm, nav)
                        }
                    }
                }
                UndoBar(
                    name = state.undoName,
                    onUndo = { nav.action(MainAction.UndoDelete) },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
                )
            }
            SatraTabBar(tabs, tab, { tab = it })
        }
    }
}

@Composable
private fun UndoBar(name: String?, onUndo: () -> Unit, modifier: Modifier = Modifier) {
    val c = Satra.colors
    AnimatedVisibility(
        visible = name != null,
        modifier = modifier,
        enter = slideInVertically(tween(280)) { it } + fadeIn(tween(200)),
        exit = slideOutVertically(tween(220)) { it } + fadeOut(tween(160)),
    ) {
        var last by remember { androidx.compose.runtime.mutableStateOf(name.orEmpty()) }
        if (name != null) last = name
        Row(
            Modifier
                .padding(horizontal = 16.dp)
                .widthIn(max = 520.dp)
                .shadow(14.dp, SatraShapes.Large, ambientColor = c.shadow, spotColor = c.shadow)
                .clip(SatraShapes.Large)
                .background(c.surfaceRaised)
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.sx_deleted_named, last),
                style = Satra.type.subhead, color = c.textPrimary,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(R.string.sx_undo),
                style = Satra.type.headline, color = c.accent,
                modifier = Modifier.heightIn(min = 44.dp).satraClickable(onClick = onUndo).padding(horizontal = 16.dp).wrapContentHeight(Alignment.CenterVertically),
            )
        }
    }
}

