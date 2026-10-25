package com.v2ray.ang.ui.main

import kotlinx.coroutines.flow.first
import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.ConnectionTestResult
import com.v2ray.ang.dto.GroupMapItem
import com.v2ray.ang.dto.LocateTarget
import com.v2ray.ang.dto.RealPingResult
import com.v2ray.ang.dto.TestServiceMessage
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.ServersCache
import com.v2ray.ang.dto.entities.SubscriptionCache
import com.v2ray.ang.extension.delay
import com.v2ray.ang.extension.isComplexType
import com.v2ray.ang.extension.matchesPattern
import com.v2ray.ang.extension.moveItem
import com.v2ray.ang.handler.AutoConnectController
import com.v2ray.ang.core.LauncherManager
import com.v2ray.ang.handler.GeoIpResolver
import com.v2ray.ang.handler.LiveUsage
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.ImportClassifier
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.handler.SmartPick
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.handler.UsagePeriods
import com.v2ray.ang.handler.UsageStore
import com.v2ray.ang.ui.base.BaseViewModel
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.PatternSyntaxException

private fun applyTestDelayResults(
    servers: List<ServersCache>,
    updates: Map<String, Long>,
): List<ServersCache> = servers.map { server ->
    val delayMillis = updates[server.guid]
    if (delayMillis == null || delayMillis == server.testDelayMillis) {
        server
    } else {
        server.copy(testDelayMillis = delayMillis)
    }
}

private fun applyTestDelayResultsToRows(
    rows: List<ServerRowUiModel>,
    updates: Map<String, Long>,
): List<ServerRowUiModel> = rows.map { row ->
    val delayMillis = updates[row.guid]
    if (delayMillis == null || delayMillis == row.testDelayMillis) {
        row
    } else {
        row.copy(testDelayMillis = delayMillis)
    }
}

/** Live numbers for Home: current speeds, a rolling history for the graph, and today's total. */
data class LiveUiState(
    val running: Boolean = false,
    val upBps: Long = 0L,
    val downBps: Long = 0L,
    val sessionStartMs: Long = 0L,
    val sessionBytes: Long = 0L,
    val todayBytes: Long = 0L,
    val history: List<Float> = emptyList(),
    val sessionUp: Long = 0L,
    val sessionDown: Long = 0L,
    val todayUp: Long = 0L,
    val todayDown: Long = 0L,
) {
    fun next(live: LiveUsage): LiveUiState {
        val day = UsageStore.day(UsagePeriods.dayKey(System.currentTimeMillis()))
        val today = day.totalBytes
        if (!live.running) return LiveUiState(todayBytes = today, todayUp = day.upBytes, todayDown = day.downBytes)
        val point = (live.upBps + live.downBps).toFloat()
        return LiveUiState(
            running = true,
            upBps = live.upBps,
            downBps = live.downBps,
            sessionStartMs = live.sessionStartMs,
            sessionBytes = live.sessionUp + live.sessionDown,
            todayBytes = today,
            history = (history + point).takeLast(HISTORY_POINTS),
            sessionUp = live.sessionUp,
            sessionDown = live.sessionDown,
            todayUp = day.upBytes,
            todayDown = day.downBytes,
        )
    }

    companion object { const val HISTORY_POINTS = 48 }
}

class MainViewModel(
    application: Application,
    private val dataSource: MainDataSource
) : BaseViewModel(application) {

    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
    private val preloadDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1)

    // ---------- UI state ----------
    private val _uiState = MutableStateFlow(
        MainUiState(
            selectedGroupId = dataSource.getSelectedSubscriptionId(),
            selectedGuid = dataSource.getSelectServer(),
            confirmRemove = dataSource.getConfirmRemove(),
            doubleColumnDisplay = dataSource.getDoubleColumnDisplay()
        )
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _autoReconnectRequest = MutableStateFlow(0)
    /** Increments when Auto policy wants Activity to restart the service. */
    val autoReconnectRequest: StateFlow<Int> = _autoReconnectRequest.asStateFlow()

    /** Bumped when Smart connect picked a server: the Activity starts the VPN or restarts it. */
    private val _smartConnectRequest = MutableStateFlow(0)
    val smartConnectRequest: StateFlow<Int> = _smartConnectRequest.asStateFlow()

    // ---------- Live stats (published by the daemon's UsageSampler) ----------
    val liveState: StateFlow<LiveUiState> = flow {
        var n = 0
        while (true) {
            emit(UsageStore.live())
            if (++n % 10 == 0 && uiState.value.isRunning) refreshSelectedRow()
            // Live numbers only move while connected; disconnected = no polling at all, wake on connect.
            if (uiState.value.isRunning) delay(1000) else uiState.first { it.isRunning }
        }
    }
        .flowOn(Dispatchers.IO)
        .scan(LiveUiState()) { acc, live -> acc.next(live) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LiveUiState())

    // ---------- Connect phase ----------
    private var phaseJob: Job? = null
    private val geoDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val geoInFlight: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private var pendingDeleteJob: Job? = null
    private var pendingDeleteGuid: String? = null

    /** The user pressed connect: animate immediately, give the core a bounded time to come up. */
    fun beginConnect() {
        phaseJob?.cancel()
        _uiState.update { it.copy(phase = ConnectPhase.Connecting, errorMessage = null) }
        phaseJob = viewModelScope.launch {
            delay(CONNECT_TIMEOUT_MS)
            if (uiState.value.phase == ConnectPhase.Connecting) {
                LauncherManager.stopService(getApplication())
                failConnect(dataSource.getString(R.string.sx_connect_timeout))
            }
        }
    }

    /** VPN permission refused or the start was abandoned. */
    fun cancelConnect() {
        phaseJob?.cancel()
        _uiState.update { it.copy(phase = if (it.isRunning) ConnectPhase.Connected else ConnectPhase.Idle) }
    }

    fun beginDisconnect() {
        phaseJob?.cancel()
        _uiState.update { it.copy(phase = ConnectPhase.Disconnecting, errorMessage = null) }
        phaseJob = viewModelScope.launch {
            delay(DISCONNECT_TIMEOUT_MS)
            if (uiState.value.phase == ConnectPhase.Disconnecting) {
                _uiState.update { it.copy(phase = if (it.isRunning) ConnectPhase.Connected else ConnectPhase.Idle) }
            }
        }
    }

    private fun failConnect(message: String?) {
        phaseJob?.cancel()
        _uiState.update {
            it.copy(
                phase = ConnectPhase.Failed,
                errorMessage = message?.takeIf { m -> m.isNotBlank() },
                autoReconnecting = false,
            )
        }
        phaseJob = viewModelScope.launch {
            delay(FAILED_SHOWN_MS)
            if (uiState.value.phase == ConnectPhase.Failed) {
                _uiState.update { it.copy(phase = ConnectPhase.Idle) }
            }
        }
    }

    // ---------- Keyword filtering ----------
    @Volatile
    private var keywordFilter: String = ""
    private var filterJob: Job? = null

    // ---------- Groups & cache ----------
    private val cacheMutex = Mutex()
    private val groupDataCache = mutableMapOf<String, List<ServersCache>>()
    private val groupUiFlows = ConcurrentHashMap<String, MutableStateFlow<ServerGroupUiState>>()
    private val groupServerFlows = ConcurrentHashMap<String, StateFlow<List<ServersCache>>>()
    private val groupLoadMutexes = ConcurrentHashMap<String, Mutex>()
    private val serverOrderPersistenceJobs = mutableMapOf<String, Job>()

    private var setupGroupJob: Job? = null
    private var preloadJob: Job? = null
    private var selectedGroupLoadJob: Job? = null
    private var reloadJob: Job? = null
    private var testResultFlushJob: Job? = null
    private val pendingTestResults = linkedMapOf<String, Long>()

    private val testRequests = MainTestRequests()
    private var bulkTestJob: Job? = null

    private val initialPageReady = CompletableDeferred<Unit>()

    // ---------- Service events ----------
    init {
        collectServiceEvents()
        setupGroupTab()
        collectDataChanges()
    }

    /**
     * Subscriptions can change from anywhere (Subscriptions tab, background worker, import):
     * refresh the lists as soon as configs land instead of waiting for the next resume, and
     * start finding exit countries for configs that have none yet.
     */
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    private fun collectDataChanges() {
        viewModelScope.launch {
            SettingsChangeManager.dataChanges
                .drop(1)
                .debounce(250)
                .collect {
                    SettingsChangeManager.consumeSetupGroupTab()
                    setupGroupTab(forceRefresh = true).join()
                    recheckUpdatedSubscriptions()
                    ensureActiveSelection()
                    locateMissing()
                }
        }
    }

    /**
     * The first subscription added (from any screen) becomes the active one with its best server;
     * also moves off a config whose subscription was just disabled. Never while connected.
     */
    private suspend fun ensureActiveSelection() {
        val active = withContext(ioDispatcher) { MmkvManager.decodeActiveServerList() }
        if (active.isEmpty()) return
        val subId = withContext(ioDispatcher) {
            dataSource.decodeServerConfig(active.first())?.subscriptionId?.takeIf { it.isNotBlank() }
        }
        val list = subId?.let { id -> MmkvManager.decodeServerList(id).filter { it in active.toSet() } }
            ?.takeIf { it.isNotEmpty() } ?: active
        adoptFirstServers(subId, list)
    }

    /**
     * Re-measures exits of subscriptions that were updated since we last looked, wherever the
     * update ran (this screen, the background worker in another process). A subscription seen for
     * the first time is only marked: its new configs are located by [locateMissing].
     */
    private fun recheckUpdatedSubscriptions() {
        viewModelScope.launch(ioDispatcher) {
            for (sub in MmkvManager.decodeSubscriptions()) {
                if (!sub.subscription.enabled) continue
                val updated = sub.subscription.lastUpdated
                if (updated <= 0L) continue
                val key = GEO_RECHECK_PREFIX + sub.guid
                val seen = MmkvManager.decodeSettingsLong(key, -1L)
                if (seen == updated) continue
                MmkvManager.encodeSettings(key, updated)
                if (seen > 0L) locateServers(MmkvManager.decodeServerList(sub.guid), recheck = true)
            }
        }
    }

    // ---------- Exit-country detection in the background ----------

    private val locating: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** Configs without a measured exit country (new imports first), excluding recent failures. */
    fun locateMissing() {
        viewModelScope.launch(ioDispatcher) {
            locateServers(MmkvManager.decodeActiveServerList().asReversed(), recheck = false)
        }
    }

    /**
     * Measures exit countries in the background. Without [recheck], configs with a known exit are
     * skipped and recent failures wait for the backoff; with it (subscription updated, Retry) the
     * listed configs are measured again. A failed recheck keeps the old flag.
     */
    fun locateServers(guids: Collection<String>, recheck: Boolean) {
        viewModelScope.launch(ioDispatcher) {
            val now = System.currentTimeMillis()
            val todo = guids.filter { g ->
                g !in locating && GeoIpResolver.needsLookup(dataSource.decodeAffiliationInfo(g), now, recheck)
            }.take(LOCATE_MAX)
            if (todo.isEmpty()) return@launch
            locating.addAll(todo)
            todo.forEach { g -> patchRowEverywhere(g) { it.copy(geoState = GeoState.Locating) } }
            dataSource.sendMsg2TestService(
                TestServiceMessage(key = AppConfig.MSG_LOCATE_START, serverGuids = locating.toList()),
                null,
            )
            // Watchdog: if the test process dies, rows must not stay "Locating…" forever.
            val budget = ((locating.size + 3) / 4) * 110_000L + 30_000L
            delay(budget)
            todo.filter { it in locating }.forEach { onLocateResult(it) }
        }
    }

    private fun onLocateResult(guid: String) {
        locating.remove(guid)
        viewModelScope.launch(ioDispatcher) {
            val aff = dataSource.decodeAffiliationInfo(guid)
            val geo = GeoIpResolver.display(aff)
            patchRowEverywhere(guid) { row ->
                row.copy(
                    countryCode = geo.countryCode ?: row.countryCode,
                    countryName = geo.countryName ?: row.countryName,
                    exitVerified = geo.exitVerified,
                    geoState = when {
                        geo.exitVerified -> GeoState.Located
                        (geo.countryCode ?: row.countryCode) == null -> GeoState.Failed
                        else -> GeoState.Unknown
                    },
                )
            }
            if (guid == uiState.value.selectedGuid) refreshSelectedRow()
        }
    }

    private fun patchRowEverywhere(guid: String, transform: (ServerRowUiModel) -> ServerRowUiModel) {
        groupUiFlows.values.forEach { flow ->
            flow.update { current ->
                if (current.rows.none { it.guid == guid }) current
                else current.copy(rows = current.rows.map { if (it.guid == guid) transform(it) else it })
            }
        }
    }

    private fun collectServiceEvents() {
        viewModelScope.launch {
            dataSource.mainServiceEvent.collect { event ->
                handleServiceEvent(event)
            }
        }
    }

    private var reconnectJob: Job? = null
    private var reconnectAttempt = 0
    @Volatile private var expectUserStop = false

    private fun handleServiceEvent(event: MainServiceEvent) {
        when (event) {
            MainServiceEvent.StateRunning -> updateRunningState(true, clearTestingText = false)
            MainServiceEvent.StateNotRunning -> {
                val wasRunning = uiState.value.isRunning
                updateRunningState(false, clearTestingText = false)
                if (wasRunning) maybeScheduleAutoReconnect()
            }
            MainServiceEvent.StateStartSuccess -> {
                updateRunningState(true)
                reconnectAttempt = 0
                expectUserStop = false
                MmkvManager.encodeSettings(AppConfig.PREF_USER_REQUESTED_STOP, false)
                _uiState.update { it.copy(autoReconnecting = false) }
                refreshExitGeoForSelectedServer()
            }

            is MainServiceEvent.StateStartFailure -> {
                updateRunningState(false)
                failConnect(event.message?.takeIf { it.isNotBlank() } ?: dataSource.getString(R.string.toast_services_failure))
                maybeScheduleAutoReconnect()
            }

            MainServiceEvent.StateStopSuccess -> {
                updateRunningState(false)
                maybeScheduleAutoReconnect()
            }
            is MainServiceEvent.MeasureDelayResult -> {
                if (!uiState.value.isRunning || !testRequests.completeCurrent(event.requestId)) return
                _uiState.update { it.copy(isTesting = testRequests.isTesting, status = MainStatus.ConnectionTest(event.result), pinging = it.pinging - (it.selectedGuid ?: "")) }
                // Measured through the running core: show it on the active config's row and on Home.
                val guid = uiState.value.selectedGuid
                if (!guid.isNullOrBlank()) {
                    val delay = event.result.delayMillis.let { if (it >= 0L) it else -1L }
                    viewModelScope.launch(ioDispatcher) {
                        MmkvManager.encodeServerTestDelayMillis(guid, delay)
                        cacheMutex.withLock { groupDataCache.clear() }
                        patchRowEverywhere(guid) { it.copy(testDelayMillis = delay) }
                        refreshSelectedRow()
                    }
                }
            }

            is MainServiceEvent.MeasureConfigSuccess -> {
                val request = testRequests.bulk?.takeIf { it.id == event.requestId } ?: return
                queueTestResult(event.result, request)
            }

            is MainServiceEvent.MeasureConfigNotify -> {
                if (event.requestId == testRequests.bulk?.id) {
                    _uiState.update { it.copy(status = MainStatus.TestProgress(event.progress)) }
                }
            }

            is MainServiceEvent.MeasureConfigFinish -> {
                val request = testRequests.bulk?.takeIf { it.id == event.requestId } ?: return
                val scheduledFlush = testResultFlushJob
                testResultFlushJob = viewModelScope.launch {
                    scheduledFlush?.cancelAndJoin()
                    if (testRequests.bulk?.id != request.id) return@launch
                    flushPendingTestResults(request)
                    onTestsFinished(request.id)
                }
            }

            is MainServiceEvent.MeasureDelayCancelled -> {
                if (testRequests.completeCurrent(event.requestId)) resetTestStatus()
            }

            is MainServiceEvent.LocateResult -> onLocateResult(event.guid)

            is MainServiceEvent.SpeedResult -> {
                _uiState.update { st ->
                    st.copy(
                        speedTesting = st.speedTesting - event.guid,
                        speedSession = st.speedSession?.let { s -> s.copy(results = s.results.filterNot { it.first == event.guid } + (event.guid to event.bps), liveBps = 0L, fraction = 0f) },
                    )
                }
                patchRowEverywhere(event.guid) { it.copy(speedBps = event.bps) }
            }

            is MainServiceEvent.SpeedProgress -> {
                _uiState.update { st -> st.copy(speedSession = st.speedSession?.copy(current = event.guid, liveBps = event.bps, fraction = event.fraction)) }
            }
            MainServiceEvent.SpeedFinished -> {
                _uiState.update { it.copy(speedTesting = emptySet(), speedSession = it.speedSession?.copy(finished = true, current = null)) }
                if (sortBySpeedAfter) {
                    sortBySpeedAfter = false
                    onAction(MainAction.SetSort(ServerSort.Speed))
                }
            }

            MainServiceEvent.LocateFinished -> {
                val left = locating.toList()
                locating.clear()
                left.forEach { onLocateResult(it) }
            }

            is MainServiceEvent.MeasureConfigCancelled -> {
                if (testRequests.completeBulk(event.requestId) != null) {
                    cancelPendingTestResults()
                    resetTestStatus()
                }
            }
        }
    }

    private fun queueTestResult(result: RealPingResult, request: MainTestRequests.Bulk) {
        pendingTestResults[result.guid] = result.delayMillis
        if (testResultFlushJob?.isActive == true) return

        testResultFlushJob = viewModelScope.launch {
            while (pendingTestResults.isNotEmpty()) {
                delay(TEST_RESULT_FLUSH_INTERVAL_MS)
                flushPendingTestResults(request)
            }
        }
    }

    private suspend fun flushPendingTestResults(request: MainTestRequests.Bulk) {
        if (pendingTestResults.isEmpty()) return
        if (testRequests.bulk?.id != request.id) return

        val updates = cacheMutex.withLock {
            val drained = pendingTestResults.toMap()
            pendingTestResults.clear()
            groupDataCache[request.groupId]?.let { cached ->
                groupDataCache[request.groupId] = applyTestDelayResults(cached, drained)
            }
            drained
        }
        if (updates.isEmpty()) return
        if (testRequests.bulk?.id != request.id) return
        mutableServerGroupState(request.groupId).update { current ->
            current.copy(
                servers = applyTestDelayResults(current.servers, updates),
                rows = applyTestDelayResultsToRows(current.rows, updates),
            )
        }
    }

    internal fun formatStatus(status: MainStatus): String = when (status) {
        MainStatus.Disconnected -> dataSource.getString(R.string.connection_not_connected)
        MainStatus.Connected -> dataSource.getString(R.string.connection_connected)
        MainStatus.Testing -> dataSource.getString(R.string.connection_test_testing)
        is MainStatus.TestProgress -> dataSource.getString(
            R.string.connection_running_task_left,
            status.progress
        )

        is MainStatus.ConnectionTest -> formatConnectionTestResult(status.result)
    }

    private fun formatConnectionTestResult(result: ConnectionTestResult): String {
        val status = if (result.delayMillis >= 0) {
            val delay = dataSource.getString(R.string.server_test_delay_value, result.delayMillis)
            dataSource.getString(R.string.connection_test_available, delay)
        } else {
            val detail = result.errorMessage.ifBlank {
                dataSource.getString(R.string.connection_test_empty_message)
            }
            dataSource.getString(R.string.connection_test_error, detail)
        }

        if (result.delayMillis < 0 || (result.country == null && result.ipAddress == null)) {
            return status
        }

        val unknown = dataSource.getString(R.string.value_unknown)
        return "$status\n(${result.country ?: unknown}) ${result.ipAddress ?: unknown}"
    }

    // ---------- Public state accessors ----------
    fun serversForGroup(groupId: String): StateFlow<List<ServersCache>> =
        groupServerFlows.computeIfAbsent(groupId) {
            val groupState = mutableServerGroupState(groupId)
            groupState
                .map { it.servers }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
                    initialValue = groupState.value.servers,
                )
        }

    internal fun serverGroupState(groupId: String): StateFlow<ServerGroupUiState> =
        mutableServerGroupState(groupId).asStateFlow()

    private fun mutableServerGroupState(groupId: String): MutableStateFlow<ServerGroupUiState> =
        groupUiFlows.computeIfAbsent(groupId) { MutableStateFlow(ServerGroupUiState()) }

    private fun currentServers(): List<ServersCache> =
        mutableServerGroupState(uiState.value.selectedGroupId).value.servers

    // ---------- Action handler ----------
    fun onAction(action: MainAction) {
        when (action) {
            MainAction.Initialize -> initialize()
            MainAction.RefreshGroups -> setupGroupTab(forceRefresh = true)
            MainAction.TestAllServers -> testAllRealPing(true)
            MainAction.TestRealAllServers -> testAllRealPing()
            MainAction.CancelTesting -> cancelAllPing()
            MainAction.RemoveAllServers -> removeAllServerAsync()
            MainAction.RemoveDuplicateServers -> removeDuplicateServerAsync()
            MainAction.RemoveInvalidServers -> removeInvalidServerAsync()
            MainAction.SortByTestResults -> sortByTestResultsAsync()
            MainAction.UpdateSubscriptions -> importConfigViaSub()
            MainAction.ExportAll -> exportAllAsync()
            is MainAction.SelectGroup -> subscriptionIdChanged(action.groupId)
            is MainAction.SelectServer -> updateSelectedGuid(action.guid)
            is MainAction.RemoveServer -> removeServerAndRefresh(action.guid)
            is MainAction.Search -> filterConfig(action.query)
            is MainAction.ImportBatchConfig -> importBatchConfig(action.configText)
            MainAction.LocateHandled -> consumeLocateTarget()
            is MainAction.ToggleFavorite -> toggleFavorite(action.guid)
            is MainAction.SetSort -> {
                _uiState.update { it.copy(sort = action.sort) }
                reapplyViewOptions()
            }
            is MainAction.SetFavoritesOnly -> {
                _uiState.update { it.copy(favoritesOnly = action.enabled) }
                reapplyViewOptions()
            }
            is MainAction.DeleteWithUndo -> deleteWithUndo(action.guid)
            MainAction.UndoDelete -> undoDelete()
            MainAction.UndoHandled -> _uiState.update { it.copy(undoName = null) }
            is MainAction.RecheckServer -> recheckServer(action.guid)
            is MainAction.LocateServer -> locateServers(listOf(action.guid), recheck = true)
            is MainAction.PingServer -> pingServer(action.guid)
            is MainAction.SpeedTest -> startSpeedTest(listOf(action.guid), sortAfter = false)
            MainAction.SpeedTestAll -> speedTestAll()
            MainAction.CloseSpeedSheet -> _uiState.update { it.copy(speedSession = null) }
            MainAction.CancelSpeedTest -> {
                dataSource.sendMsg2TestService(TestServiceMessage(key = AppConfig.MSG_SPEED_CANCEL), null)
                _uiState.update { it.copy(speedTesting = emptySet(), speedSession = it.speedSession?.copy(finished = true, current = null)) }
            }
            MainAction.SmartConnect -> smartConnect()
            is MainAction.ShowAddSheet -> _uiState.update { it.copy(addSheet = action.show) }
            is MainAction.ShareQRCode -> {
                val bitmap = dataSource.share2QRCode(action.guid)
                _uiState.update { it.copy(shareQRCodeBitmap = bitmap) }
            }

            MainAction.DismissQRCodeDialog -> {
                _uiState.update { it.copy(shareQRCodeBitmap = null) }
            }

            MainAction.ToggleService,
            MainAction.TestCurrentServer,
            MainAction.ImportQRcode,
            MainAction.ImportClipboard,
            MainAction.ImportConfigLocal,
            is MainAction.ImportManually,
            MainAction.RestartService,
            MainAction.LocateSelectedServer,
            is MainAction.EditServer,
            is MainAction.ShareClipboard,
            is MainAction.ShareFullContent -> {
                // Handled by Activity via its onAction lambda
            }
        }
    }

    // ---------- Initialization ----------
    fun initialize() {
        viewModelScope.launch(preloadDispatcher) {
            try {
                initialPageReady.await()
                delay(32)
                dataSource.initAssets()
                dataSource.syncSubscriptions()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                LogUtil.e(AppConfig.TAG, "Main background initialization failed", error)
            }
        }
    }

    fun refreshUiSettings() {
        _uiState.update {
            it.copy(
                confirmRemove = dataSource.getConfirmRemove(),
                doubleColumnDisplay = dataSource.getDoubleColumnDisplay()
            )
        }
    }

    // ---------- Group & server loading ----------
    private suspend fun buildServersCache(guids: List<String>): List<ServersCache> =
        guids.mapNotNull { guid ->
            currentCoroutineContext().ensureActive()
            val profile = dataSource.decodeServerConfig(guid) ?: return@mapNotNull null
            val affiliation = dataSource.decodeAffiliationInfo(guid)
            ServersCache(
                guid = guid,
                profile = profile.copy(),
                testDelayMillis = affiliation?.testDelayMillis ?: 0L
            )
        }

    private suspend fun loadGroup(
        groupId: String,
        forceRefresh: Boolean = false
    ): List<ServersCache> {
        val loadMutex = groupLoadMutexes.computeIfAbsent(groupId) { Mutex() }
        return loadMutex.withLock {
            if (!forceRefresh) {
                cacheMutex.withLock { groupDataCache[groupId]?.let { return@withLock it } }
            }
            val servers = buildServersCache(dataSource.getServerGuidList(groupId))
            currentCoroutineContext().ensureActive()
            cacheMutex.withLock { groupDataCache[groupId] = servers }
            servers
        }
    }

    private fun applyKeywordFilter(servers: List<ServersCache>): List<ServersCache> {
        val keyword = keywordFilter.trim()
        if (keyword.isEmpty()) return servers
        val regex = try {
            Regex(keyword, RegexOption.IGNORE_CASE)
        } catch (_: PatternSyntaxException) {
            return servers
        }
        return servers.filter { cache ->
            val profile = cache.profile
            profile.remarks.matchesPattern(regex, keyword) ||
                    profile.description.orEmpty().matchesPattern(regex, keyword) ||
                    profile.server.orEmpty().matchesPattern(regex, keyword) ||
                    profile.configType.name.matchesPattern(regex, keyword)
        }
    }

    private fun updateGroupUi(groupId: String, servers: List<ServersCache>) {
        val filteredServers = applyKeywordFilter(servers)
        val rows = buildServerRows(groupId, filteredServers).map { row ->
            if (row.guid in locating && row.geoState != GeoState.Located) row.copy(geoState = GeoState.Locating) else row
        }
        val state = uiState.value
        val order = rows.indices
            .filter { !state.favoritesOnly || rows[it].favorite }
            .sortedWith(rowComparator(rows, state.sort))
        mutableServerGroupState(groupId).value = ServerGroupUiState(
            servers = order.map { filteredServers[it] },
            rows = order.map { rows[it] },
        )
        scheduleGeoFill(groupId, order.map { rows[it] })
        refreshSelectedRow()
    }

    private fun rowComparator(rows: List<ServerRowUiModel>, sort: ServerSort): Comparator<Int> {
        // Starred configs are always pinned to the top; the chosen sort orders each block.
        val byFavorite = compareByDescending<Int> { rows[it].favorite }
        return when (sort) {
            ServerSort.Default -> byFavorite
            ServerSort.Ping -> byFavorite.thenBy {
                val d = rows[it].testDelayMillis
                if (d > 0L) d else Long.MAX_VALUE
            }
            ServerSort.Name -> byFavorite.thenBy { rows[it].remarks.lowercase() }
            ServerSort.Speed -> byFavorite.thenByDescending { rows[it].speedBps }
        }
    }

    /**
     * Looks up the *entry* country (server address) lazily, for tunnel detection only. The row's
     * flag is the measured exit and is never replaced by this.
     */
    private fun scheduleGeoFill(groupId: String, rows: List<ServerRowUiModel>) {
        val todo = rows.filter { it.guid !in geoInFlight }.take(GEO_FILL_BATCH)
        if (todo.isEmpty()) return
        viewModelScope.launch(geoDispatcher) {
            val now = System.currentTimeMillis()
            for (row in todo) {
                ensureActive()
                val aff = MmkvManager.decodeServerAffiliationInfo(row.guid)
                if (!GeoIpResolver.needsEntryLookup(aff, now)) continue
                if (!geoInFlight.add(row.guid)) continue
                try {
                    val port = if (uiState.value.isRunning) SettingsManager.getHttpPort() else 0
                    val entry = GeoIpResolver.resolveEntry(row.guid, row.profile, port)
                    if (entry != null && entry != row.entryCountryCode) {
                        patchRowEverywhere(row.guid) { it.copy(entryCountryCode = entry) }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    LogUtil.w(AppConfig.TAG, "Geo fill failed: ${e.message}")
                } finally {
                    geoInFlight.remove(row.guid)
                }
            }
        }
    }

    private fun patchRowGeo(groupId: String, guid: String, geo: GeoIpResolver.GeoResult) {
        mutableServerGroupState(groupId).update { current ->
            current.copy(
                rows = current.rows.map { row ->
                    if (row.guid != guid) row
                    else row.copy(countryCode = geo.countryCode, countryName = geo.countryName, exitVerified = geo.exitVerified)
                },
            )
        }
        if (guid == uiState.value.selectedGuid) refreshSelectedRow()
    }

    /** Rebuilds the row for the active config (Home hero card). */
    fun refreshSelectedRow() {
        viewModelScope.launch(ioDispatcher) {
            val guid = dataSource.getSelectServer()
            val row = guid?.let { g ->
                val profile = dataSource.decodeServerConfig(g) ?: return@let null
                val aff = dataSource.decodeAffiliationInfo(g)
                buildServerRowUiModel(
                    ServersCache(g, profile.copy(), aff?.testDelayMillis ?: 0L),
                    subscriptionRemarks = dataSource.getSubscriptionItem(profile.subscriptionId)?.remarks.orEmpty(),
                )
            }
            val quota = guid?.let { g ->
                val subId = dataSource.decodeServerConfig(g)?.subscriptionId?.takeIf { it.isNotBlank() } ?: return@let null
                val sub = dataSource.getSubscriptionItem(subId) ?: return@let null
                if (sub.total <= 0L && sub.expire <= 0L) return@let null
                val used = com.v2ray.ang.handler.HybridUsageTracker.displayFor(subId, sub)
                QuotaUi(
                    name = sub.remarks,
                    usedBytes = used.displayUsed,
                    totalBytes = sub.total,
                    expireMs = if (sub.expire <= 0L) 0L else if (sub.expire > 10_000_000_000L) sub.expire else sub.expire * 1000L,
                    startMs = sub.addedTime,
                )
            }
            _uiState.update { it.copy(selectedRow = row, quota = quota) }
        }
    }

    private fun reapplyViewOptions() {
        viewModelScope.launch(defaultDispatcher) {
            val snapshot = cacheMutex.withLock { groupDataCache.toMap() }
            snapshot.forEach { (groupId, servers) -> updateGroupUi(groupId, servers) }
        }
    }

    fun toggleFavorite(guid: String) {
        viewModelScope.launch(ioDispatcher) {
            MmkvManager.setServerFavorite(guid, !MmkvManager.isServerFavorite(guid))
            reapplyViewOptions()
        }
    }

    fun deleteWithUndo(guid: String) {
        if (guid == uiState.value.selectedGuid && uiState.value.isRunning) {
            toast(R.string.toast_action_not_allowed)
            return
        }
        commitPendingDelete()
        val name = dataSource.decodeServerConfig(guid)?.remarks.orEmpty()
        pendingDeleteGuid = guid
        _uiState.update { it.copy(hiddenGuids = it.hiddenGuids + guid, undoName = name.ifBlank { "·" }) }
        pendingDeleteJob = viewModelScope.launch {
            delay(UNDO_WINDOW_MS)
            commitPendingDelete()
        }
    }

    fun undoDelete() {
        val guid = pendingDeleteGuid ?: return
        pendingDeleteJob?.cancel()
        pendingDeleteGuid = null
        pendingDeleteJob = null
        _uiState.update { it.copy(hiddenGuids = it.hiddenGuids - guid, undoName = null) }
    }

    /** Actually removes a config whose undo window has closed (or is being superseded). */
    private fun commitPendingDelete() {
        val guid = pendingDeleteGuid ?: return
        pendingDeleteJob?.cancel()
        pendingDeleteGuid = null
        pendingDeleteJob = null
        viewModelScope.launch(ioDispatcher) {
            if (guid == uiState.value.selectedGuid && uiState.value.isRunning) {
                _uiState.update { it.copy(hiddenGuids = it.hiddenGuids - guid, undoName = null) }
                return@launch
            }
            dataSource.removeServer(guid)
            cacheMutex.withLock { groupDataCache.clear() }
            setupGroupTab(forceRefresh = true).join()
            _uiState.update { it.copy(hiddenGuids = it.hiddenGuids - guid, undoName = null) }
        }
    }

    fun recheckServer(guid: String) {
        // Re-ping one config; the test worker also refreshes its exit country.
        val request = testRequests.beginBulk(uiState.value.selectedGroupId)
        _uiState.update { it.copy(isTesting = true, status = MainStatus.Testing) }
        viewModelScope.launch {
            withContext(ioDispatcher) { dataSource.clearAllTestDelayResults(listOf(guid)) }
            dataSource.sendMsg2TestService(
                TestServiceMessage(
                    key = AppConfig.MSG_MEASURE_CONFIG_START,
                    subscriptionId = "",
                    serverGuids = listOf(guid),
                    onlyTcp = false,
                ),
                request.id,
            )
        }
    }

    private fun buildServerRows(groupId: String, servers: List<ServersCache>): List<ServerRowUiModel> {
        val subscriptionRemarks = if (groupId.isEmpty()) {
            servers.asSequence()
                .map { it.profile.subscriptionId }
                .filter { it.isNotEmpty() }
                .distinct()
                .associateWith { subscriptionId ->
                    dataSource.getSubscriptionItem(subscriptionId)?.remarks.orEmpty()
                }
        } else {
            emptyMap()
        }
        return servers.map { server ->
            buildServerRowUiModel(
                server = server,
                subscriptionRemarks = subscriptionRemarks[server.profile.subscriptionId].orEmpty()
            )
        }
    }

    fun getSubscriptions(): List<SubscriptionCache> = dataSource.getSubscriptions()

    private fun resolveSelectedGroup(groups: List<GroupMapItem>): String {
        val current = uiState.value.selectedGroupId
        val resolved = when {
            groups.isEmpty() -> ""
            groups.any { it.id == current } -> current
            else -> groups.first().id
        }
        if (resolved != current) {
            dataSource.setSelectedSubscriptionId(resolved)
        }
        return resolved
    }

    private fun radialPreloadOrder(groups: List<GroupMapItem>, selectedIndex: Int): List<String> {
        if (groups.isEmpty()) return emptyList()
        val result = ArrayList<String>((groups.size - 1).coerceAtLeast(0))
        for (distance in 1 until groups.size) {
            val right = selectedIndex + distance
            val left = selectedIndex - distance
            if (right in groups.indices) result += groups[right].id
            if (left in groups.indices) result += groups[left].id
        }
        return result
    }

    fun setupGroupTab(forceRefresh: Boolean = false): Job {
        setupGroupJob?.cancel()
        preloadJob?.cancel()
        selectedGroupLoadJob?.cancel()

        return viewModelScope.launch(ioDispatcher) {
            try {
                if (forceRefresh) {
                    cacheMutex.withLock { groupDataCache.clear() }
                }
                val groups = dataSource.getSubscriptions().map {
                    GroupMapItem(id = it.guid, remarks = it.subscription.remarks)
                }
                val selectedGroup = resolveSelectedGroup(groups)
                val validIds = groups.mapTo(HashSet()) { it.id }
                groupUiFlows.keys.removeAll { it !in validIds }
                groupServerFlows.keys.removeAll { it !in validIds }
                groupLoadMutexes.keys.removeAll { it !in validIds }

                _uiState.update {
                    it.copy(
                        groups = groups,
                        selectedGroupId = selectedGroup,
                        selectedGuid = dataSource.getSelectServer(),
                    )
                }
                groups.forEach { mutableServerGroupState(it.id) }

                if (groups.isEmpty()) {
                    cacheMutex.withLock { groupDataCache.clear() }
                    return@launch
                }

                val selectedServers = loadGroup(selectedGroup, forceRefresh)
                updateGroupUi(selectedGroup, selectedServers)

                if (!initialPageReady.isCompleted) {
                    initialPageReady.complete(Unit)
                }

                val selectedIndex =
                    groups.indexOfFirst { it.id == selectedGroup }.coerceAtLeast(0)
                val preloadOrder = radialPreloadOrder(groups, selectedIndex)
                preloadJob = viewModelScope.launch(preloadDispatcher) {
                    preloadOrder.forEach { groupId ->
                        ensureActive()
                        delay(32)
                        val servers = loadGroup(groupId, forceRefresh)
                        updateGroupUi(groupId, servers)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to set up group tabs", error)
            } finally {
                if (!initialPageReady.isCompleted) {
                    initialPageReady.complete(Unit)
                }
            }
        }.also { setupGroupJob = it }
    }

    // ---------- Business actions (coroutine-based) ----------
    /**
     * One-tap Add: works out what the pasted text is, imports it and says what happened.
     * Nothing recognisable opens the "more ways to add" sheet instead of failing silently.
     */
    private fun importBatchConfig(configText: String) {
        val kind = ImportClassifier.classify(configText)
        if (kind.isEmpty) {
            toast(R.string.sx_add_nothing)
            _uiState.update { it.copy(addSheet = true) }
            return
        }
        if (kind.subscriptions > 0) toast(R.string.sx_add_sub_fetching)
        _uiState.update { it.copy(importing = true) }
        launchLoading {
            withContext(ioDispatcher) {
                try {
                    val subsBefore = MmkvManager.decodeSubscriptions().map { it.guid }.toSet()
                    val serversBefore = MmkvManager.decodeAllServerList().toSet()
                    // Configs added into a remote subscription's group would be wiped by its next refresh;
                    // store them as manual ones (no subscription id) so refreshes keep them.
                    val target = uiState.value.selectedGroupId
                    val targetIsRemote = target.isNotEmpty() && !MmkvManager.decodeSubscription(target)?.url.isNullOrBlank()
                    val (count, countSub) = dataSource.importBatchConfig(
                        configText, if (targetIsRemote) "" else target, true
                    )
                    val added = MmkvManager.decodeAllServerList().filter { it !in serversBefore }
                    when {
                        countSub > 0 && added.isNotEmpty() -> toastSuccess(getQuantityString(R.plurals.sx_add_sub_done, added.size, added.size))
                        countSub > 0 -> toastError(R.string.sx_add_sub_empty)
                        count > 0 -> toastSuccess(getQuantityString(R.plurals.sx_add_configs_done, count, count))
                        kind.subscriptions > 0 -> {
                            // Link already known: refresh that subscription instead of failing.
                            val r = AngConfigManager.updateConfigViaSubAll()
                            if (r.successCount > 0) toast(R.string.sx_add_sub_exists) else toastError(R.string.sx_subs_failed)
                        }
                        else -> toastError(R.string.toast_failure)
                    }
                    if (count > 0 || countSub > 0 || added.isNotEmpty()) {
                        val newSub = MmkvManager.decodeSubscriptions().firstOrNull { it.guid !in subsBefore }?.guid
                        setupGroupTab(forceRefresh = true).join()
                        adoptFirstServers(newSub, added)
                        locateServers(added, recheck = false)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Failed to import batch config", e)
                    toastError(R.string.toast_failure)
                } finally {
                    _uiState.update { it.copy(importing = false) }
                }
            }
        }
    }

    /** Group whose fastest server should be picked once the current test batch finishes. */
    @Volatile private var pendingBestPick: String? = null

    /**
     * When nothing usable was selected (first import), the new subscription becomes the current
     * list, its first server is selected right away, and after a quick real-ping pass the fastest
     * one replaces it (unless the user picked something or connected meanwhile).
     */
    private suspend fun adoptFirstServers(newSubId: String?, added: List<String>) {
        val selected = dataSource.getSelectServer()
        val selectedValid = selected != null && dataSource.decodeServerConfig(selected) != null &&
            MmkvManager.isServerActive(selected)
        if (selectedValid || added.isEmpty() || uiState.value.isRunning) return
        val groupId = newSubId ?: dataSource.decodeServerConfig(added.first())?.subscriptionId.orEmpty()
        withContext(Dispatchers.Main) {
            if (groupId.isNotEmpty()) onAction(MainAction.SelectGroup(groupId))
            updateSelectedGuid(added.first())
            pendingBestPick = groupId
            testAllRealPing()
        }
    }

    private fun applyPendingBestPick() {
        val groupId = pendingBestPick ?: return
        pendingBestPick = null
        if (uiState.value.isRunning) return
        viewModelScope.launch(ioDispatcher) {
            val list = if (groupId.isNotEmpty()) MmkvManager.decodeServerList(groupId) else MmkvManager.decodeActiveServerList()
            val best = list.mapNotNull { g -> MmkvManager.decodeServerAffiliationInfo(g)?.testDelayMillis?.takeIf { it > 0L }?.let { g to it } }
                .minByOrNull { it.second }?.first ?: return@launch
            withContext(Dispatchers.Main) { if (!uiState.value.isRunning) updateSelectedGuid(best) }
        }
    }

    private fun importConfigViaSub() {
        launchLoading {
            withContext(ioDispatcher) {
                try {
                    // Always refresh every enabled subscription so Home quota cards
                    // (usage/expiry headers) update even when the selected tab is empty/"All".
                    val result = dataSource.updateConfigViaSubAll()
                    when {
                        result.successCount + result.failureCount + result.skipCount == 0 ->
                            toast(R.string.title_update_subscription_no_subscription)

                        result.successCount > 0 && result.failureCount == 0 ->
                            toast(
                                getQuantityString(
                                    R.plurals.title_update_config_count,
                                    result.configCount.coerceAtLeast(result.successCount),
                                    result.configCount.coerceAtLeast(result.successCount),
                                )
                            )

                        else ->
                            toast(dataSource.getString(R.string.title_update_subscription_result, result.configCount, result.successCount, result.failureCount, result.skipCount))
                    }
                    if (result.successCount > 0 || result.configCount > 0) {
                        setupGroupTab(forceRefresh = true)
                        refreshSelectedGuid()
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Subscription update failed", e)
                    toastError(R.string.toast_failure)
                }
            }
        }
    }

    private fun exportAllAsync() {
        launchLoading {
            withContext(ioDispatcher) {
                try {
                    val groupId = uiState.value.selectedGroupId
                    val list = if (groupId.isEmpty() && keywordFilter.isEmpty()) {
                        dataSource.getServerGuidList("")
                    } else {
                        currentServers().map { it.guid }
                    }
                    val ret = dataSource.shareNonCustomConfigsToClipboard(list)
                    if (ret > 0) {
                        toast(dataSource.getString(R.string.title_export_config_count, ret))
                    } else {
                        toastError(R.string.toast_failure)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Export failed", e)
                    toastError(R.string.toast_failure)
                }
            }
        }
    }

    private fun removeAllServerAsync() {
        launchLoading {
            withContext(ioDispatcher) {
                try {
                    val count =
                        if (uiState.value.selectedGroupId.isEmpty() && keywordFilter.isEmpty()) {
                            dataSource.removeAllServer()
                        } else {
                            val guids = currentServers().map { it.guid }
                            guids.forEach { dataSource.removeServer(it) }
                            guids.size
                        }
                    viewModelScope.launch(ioDispatcher) {
                        cacheMutex.withLock { groupDataCache.clear() }
                    }
                    setupGroupTab(forceRefresh = true)
                    toast(dataSource.getString(R.string.title_del_config_count, count))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Delete all failed", e)
                    toastError(R.string.toast_failure)
                }
            }
        }
    }

    private fun removeDuplicateServerAsync() {
        launchLoading {
            withContext(ioDispatcher) {
                try {
                    val seen = HashSet<ProfileItem>()
                    val duplicates = ArrayList<String>()
                    currentServers().forEach { server ->
                        val profile = server.profile
                        if (!profile.configType.isComplexType()) {
                            val identity = profile.duplicateIdentity()
                            if (!seen.add(identity)) duplicates += server.guid
                        }
                    }
                    duplicates.forEach { dataSource.removeServer(it) }
                    setupGroupTab(forceRefresh = true)
                    toast(dataSource.getString(R.string.title_del_duplicate_config_count, duplicates.size))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Delete duplicate failed", e)
                    toastError(R.string.toast_failure)
                }
            }
        }
    }

    private fun removeInvalidServerAsync() {
        launchLoading {
            withContext(ioDispatcher) {
                try {
                    val count = removeInvalidServerInternal()
                    viewModelScope.launch(ioDispatcher) {
                        cacheMutex.withLock { groupDataCache.clear() }
                        setupGroupTab(forceRefresh = true)
                    }
                    toast(dataSource.getString(R.string.title_del_config_count, count))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Delete invalid failed", e)
                    toastError(R.string.toast_failure)
                }
            }
        }
    }

    private fun removeInvalidServerInternal(): Int {
        val visibleServersOnly =
            uiState.value.selectedGroupId.isNotEmpty() || keywordFilter.isNotBlank()
        return if (visibleServersOnly) {
            currentServers().sumOf { server ->
                dataSource.removeInvalidServerByGuid(server.guid)
            }
        } else {
            dataSource.removeInvalidServersInGroup("")
        }
    }

    private fun sortByTestResultsAsync() {
        launchLoading {
            withContext(ioDispatcher) {
                try {
                    sortByTestResultsInternal()
                    cacheMutex.withLock { groupDataCache.clear() }
                    setupGroupTab(forceRefresh = true)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Sort by test results failed", e)
                    toastError(R.string.toast_failure)
                }
            }
        }
    }

    private fun sortByTestResultsInternal() {
        val subs = if (uiState.value.selectedGroupId.isEmpty()) {
            dataSource.getSubsList()
        } else {
            listOf(uiState.value.selectedGroupId)
        }
        subs.forEach { dataSource.sortByTestResultsForSub(it) }
    }

    fun subscriptionIdChanged(id: String) {
        if (_uiState.value.groups.none { it.id == id }) return
        mutableServerGroupState(id)
        if (uiState.value.selectedGroupId != id) {
            dataSource.setSelectedSubscriptionId(id)
            _uiState.update { it.copy(selectedGroupId = id) }
        }
        selectedGroupLoadJob?.cancel()
        selectedGroupLoadJob = viewModelScope.launch(ioDispatcher) {
            try {
                updateGroupUi(id, loadGroup(id))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to load selected group: $id", error)
            }
        }
    }

    fun reloadServerList() {
        val groupId = uiState.value.selectedGroupId
        selectedGroupLoadJob?.cancel()
        selectedGroupLoadJob = viewModelScope.launch(ioDispatcher) {
            updateGroupUi(groupId, loadGroup(groupId, forceRefresh = true))
        }
    }

    fun reloadAllGroups(groupIds: List<String>) {
        reloadJob?.cancel()
        reloadJob = viewModelScope.launch(preloadDispatcher) {
            val selected = uiState.value.selectedGroupId
            val order = buildList {
                if (selected in groupIds) add(selected)
                addAll(groupIds.filter { it != selected })
            }
            order.forEachIndexed { index, groupId ->
                ensureActive()
                if (index > 0) delay(32)
                updateGroupUi(groupId, loadGroup(groupId, forceRefresh = true))
            }
        }
    }

    fun filterConfig(keyword: String) {
        if (keyword == keywordFilter) return
        keywordFilter = keyword
        filterJob?.cancel()
        filterJob = viewModelScope.launch(defaultDispatcher) {
            delay(300)
            val snapshot = cacheMutex.withLock { groupDataCache.toMap() }
            ensureActive()
            snapshot.forEach { (groupId, servers) ->
                ensureActive()
                updateGroupUi(groupId, servers)
            }
        }
    }

    fun updateSelectedGuid(guid: String) {
        dataSource.setSelectServer(guid)
        _uiState.update { it.copy(selectedGuid = guid) }
        refreshSelectedRow()
    }

    fun refreshSelectedGuid() {
        _uiState.update { it.copy(selectedGuid = dataSource.getSelectServer()) }
    }

    fun removeServerAndRefresh(guid: String) {
        if (guid == uiState.value.selectedGuid) {
            toast(R.string.toast_action_not_allowed)
            return
        }
        viewModelScope.launch(ioDispatcher) {
            dataSource.removeServer(guid)
            cacheMutex.withLock { groupDataCache.clear() }
            setupGroupTab(forceRefresh = true).join()
        }
    }

    fun moveServer(groupId: String, fromPosition: Int, toPosition: Int) {
        val groupState = mutableServerGroupState(groupId).value
        val servers = groupState.servers.toMutableList()
        if (!servers.moveItem(fromPosition, toPosition)) return
        val rows = groupState.rows.toMutableList()
        rows.moveItem(fromPosition, toPosition)
        val guids = servers.map { it.guid }
        mutableServerGroupState(groupId).value = ServerGroupUiState(servers, rows)
        // A drag emits several moves; serialize writes so an older order cannot overwrite a newer one.
        val previousPersistenceJob = serverOrderPersistenceJobs[groupId]
        serverOrderPersistenceJobs[groupId] = viewModelScope.launch(ioDispatcher) {
            previousPersistenceJob?.join()
            dataSource.encodeServerList(guids, groupId)
            cacheMutex.withLock { groupDataCache[groupId] = servers }
        }
    }

    // ---------- Testing ----------
    fun cancelAllPing() {
        bulkTestJob?.cancel()
        bulkTestJob = null
        testRequests.cancelBulk()
        testRequests.invalidateCurrent()
        cancelPendingTestResults()
        resetTestStatus()
        dataSource.cancelAllPing()
    }

    private fun resetTestStatus() {
        _uiState.update {
            it.copy(
                isTesting = testRequests.isTesting,
                status = if (testRequests.isTesting) MainStatus.Testing
                else if (it.isRunning) MainStatus.Connected else MainStatus.Disconnected
            )
        }
    }

    fun testAllRealPing(onlyTcp: Boolean = false) {
        cancelAllPing()
        val groupId = uiState.value.selectedGroupId
        val selectedServers = currentServers()
        val crossGroup = selectedServers.isEmpty()
        val servers = if (crossGroup) {
            // Home pull-to-refresh / empty selected group: ping every loaded group.
            uiState.value.groups.flatMap { group ->
                mutableServerGroupState(group.id).value.servers
            }
        } else {
            selectedServers
        }
        if (servers.isEmpty()) {
            return
        }
        val serverGuids = servers.map { it.guid }
        if (!crossGroup) {
            mutableServerGroupState(groupId).update { current ->
                current.copy(
                    servers = current.servers.map { server ->
                        if (server.testDelayMillis == 0L) server
                        else server.copy(testDelayMillis = 0L)
                    },
                    rows = current.rows.map { row ->
                        if (row.testDelayMillis == 0L) row
                        else row.copy(testDelayMillis = 0L)
                    }
                )
            }
        }
        val request = testRequests.beginBulk(groupId)
        val message = TestServiceMessage(
            key = AppConfig.MSG_MEASURE_CONFIG_START,
            subscriptionId = if (crossGroup) "" else groupId,
            // Explicit guids when filtering or when spanning every group.
            serverGuids = if (keywordFilter.isNotEmpty() || crossGroup) serverGuids else emptyList(),
            onlyTcp = onlyTcp
        )
        _uiState.update {
            it.copy(
                isTesting = true,
                status = MainStatus.Testing
            )
        }
        bulkTestJob = viewModelScope.launch {
            withContext(ioDispatcher) {
                dataSource.clearAllTestDelayResults(serverGuids)
                val resetGuids = serverGuids.toHashSet()
                cacheMutex.withLock {
                    groupDataCache[groupId]?.let { cached ->
                        groupDataCache[groupId] = cached.map { server ->
                            if (server.guid !in resetGuids || server.testDelayMillis == 0L) server
                            else server.copy(testDelayMillis = 0L)
                        }
                    }
                }
            }
            dataSource.sendMsg2TestService(message, request.id)
        }
    }

    private fun cancelPendingTestResults() {
        testResultFlushJob?.cancel()
        testResultFlushJob = null
        pendingTestResults.clear()
    }

    /**
     * Ping the active config: through the running core when connected, otherwise with a private
     * test core like the list test does.
     */
    fun pingSelected() {
        if (uiState.value.isRunning) return testCurrentServerRealPing()
        val guid = uiState.value.selectedGuid?.takeIf { it.isNotBlank() } ?: return
        val groupId = dataSource.decodeServerConfig(guid)?.subscriptionId.orEmpty()
        viewModelScope.launch {
            patchRowEverywhere(guid) { it.copy(testDelayMillis = 0L) }
            pingGuids(groupId, listOf(guid))
        }
    }

    /**
     * Ping one config by itself. Uses a private test core, so it works whether or not the VPN is
     * connected (the running config is pinged through the live core instead). Never locates.
     */
    fun pingServer(guid: String) {
        val st = uiState.value
        // Only a whole-list test blocks this; the quick re-ping of the active config does not.
        if (!canPingSingle(guid, st.pinging, bulkTesting = testRequests.bulk != null && st.pinging.isEmpty())) {
            LogUtil.i(AppConfig.TAG, "MainViewModel: single ping skipped, list test running")
            return
        }
        if (st.isRunning && guid == st.selectedGuid) {
            _uiState.update { it.copy(pinging = it.pinging + guid) }
            viewModelScope.launch { delay(SINGLE_PING_TIMEOUT_MS); _uiState.update { it.copy(pinging = it.pinging - guid) } }
            return testCurrentServerRealPing()
        }
        val groupId = dataSource.decodeServerConfig(guid)?.subscriptionId.orEmpty()
        _uiState.update { it.copy(pinging = it.pinging + guid) }
        viewModelScope.launch {
            patchRowEverywhere(guid) { it.copy(testDelayMillis = 0L) }
            pingGuids(groupId, listOf(guid))
            // Safety net: never leave a spinner if the worker dies.
            delay(SINGLE_PING_TIMEOUT_MS)
            _uiState.update { it.copy(pinging = it.pinging - guid) }
        }
    }

    fun testCurrentServerRealPing() {
        if (!uiState.value.isRunning) return
        val requestId = testRequests.beginCurrent()
        _uiState.update { it.copy(isTesting = true, status = MainStatus.Testing) }
        dataSource.testCurrentServerRealPing(requestId)
    }

    private fun onTestsFinished(requestId: String) {
        if (testRequests.completeBulk(requestId) == null) return
        _uiState.update { it.copy(pinging = emptySet()) }
        resetTestStatus()
        applyPendingBestPick()
        val groupId = uiState.value.selectedGroupId
        viewModelScope.launch(ioDispatcher) {
            // Ping tests never touch exit countries (flags stay stable).
            cacheMutex.withLock { groupDataCache.clear() }
            reloadAllGroups(_uiState.value.groups.map { it.id })
        }
    }

    fun triggerLocateSelectedServer() {
        val selected = dataSource.getSelectServer() ?: return
        val profile = dataSource.decodeServerConfig(selected) ?: return
        val groupId = profile.subscriptionId
        if (_uiState.value.groups.none { it.id == groupId }) return
        viewModelScope.launch(ioDispatcher) {
            updateGroupUi(groupId, loadGroup(groupId))
            if (_uiState.value.selectedGroupId != groupId) {
                dataSource.setSelectedSubscriptionId(groupId)
            }
            val target = LocateTarget(groupId, selected)
            _uiState.update {
                it.copy(selectedGroupId = groupId, locateTarget = target)
            }
        }
    }

    private fun consumeLocateTarget() {
        _uiState.update { it.copy(locateTarget = null) }
    }

    // ---------- Auto-connect / user stop ----------
    fun isAutoConnectEnabled(): Boolean =
        MmkvManager.decodeSettingsBool(AppConfig.PREF_AUTO_CONNECT, true)

    fun setAutoConnectEnabled(enabled: Boolean) {
        MmkvManager.encodeSettings(AppConfig.PREF_AUTO_CONNECT, enabled)
    }

    fun markUserRequestedStop() {
        expectUserStop = true
        reconnectJob?.cancel()
        reconnectAttempt = 0
        MmkvManager.encodeSettings(AppConfig.PREF_USER_REQUESTED_STOP, true)
    }

    fun markUserRequestedStart() {
        expectUserStop = false
        reconnectJob?.cancel()
        reconnectAttempt = 0
        MmkvManager.encodeSettings(AppConfig.PREF_USER_REQUESTED_STOP, false)
    }

    private var smartJob: Job? = null

    /**
     * Smart connect: tests the servers of the current list (all lists when "All" is empty), waits
     * until the test finishes or a few quick answers are in, then connects to the fastest one.
     */
    fun smartConnect() {
        if (smartJob?.isActive == true) return
        smartJob = viewModelScope.launch {
            _uiState.update { it.copy(smartConnecting = true) }
            try {
                val groupId = uiState.value.selectedGroupId
                val candidates = withContext(ioDispatcher) {
                    (if (groupId.isNotBlank()) MmkvManager.decodeServerList(groupId) else MmkvManager.decodeActiveServerList())
                }
                if (candidates.isEmpty()) return@launch
                val started = System.currentTimeMillis()
                testAllRealPing()
                delay(400)
                while (true) {
                    delay(400)
                    val elapsed = System.currentTimeMillis() - started
                    if (!uiState.value.isTesting || elapsed > SMART_MAX_WAIT_MS) break
                    if (elapsed > SMART_EARLY_MS) {
                        val answered = withContext(ioDispatcher) {
                            candidates.count { (MmkvManager.decodeServerAffiliationInfo(it)?.testDelayMillis ?: 0L) > 0L }
                        }
                        if (answered >= SMART_EARLY_ANSWERS) break
                    }
                }
                val delayOf: (String) -> Long = { g -> MmkvManager.decodeServerAffiliationInfo(g)?.testDelayMillis ?: 0L }
                val first = withContext(ioDispatcher) { candidates.associateWith(delayOf) }
                // Second look at the quickest few: drops flaky servers and measures jitter.
                val retest = SmartPick.retestSet(first)
                val second = if (retest.size >= 2) {
                    pingGuids(groupId, retest)
                    val t0 = System.currentTimeMillis()
                    while (System.currentTimeMillis() - t0 < SMART_RETEST_MS) {
                        delay(300)
                        val done = withContext(ioDispatcher) { retest.all { delayOf(it) != 0L } }
                        if (done || !uiState.value.isTesting) break
                    }
                    withContext(ioDispatcher) { retest.associateWith { delayOf(it).let { d -> if (d == 0L) -1L else d } } }
                } else emptyMap()
                val now = System.currentTimeMillis()
                val chosen = withContext(ioDispatcher) {
                    SmartPick.pick(
                        first.map { (g, d) ->
                            SmartPick.Candidate(g, d, second[g], MmkvManager.decodeSettingsLong(LAST_OK_PREFIX + g, 0L))
                        },
                        now,
                    )
                }
                if (second.isNotEmpty()) {
                    // Put back the first-pass value for retests that failed, so rows are not left blank.
                    withContext(ioDispatcher) {
                        second.filterValues { it <= 0L }.keys.forEach { g ->
                            first[g]?.takeIf { it > 0L }?.let { MmkvManager.encodeServerTestDelayMillis(g, it) }
                        }
                    }
                }
                val best = chosen?.guid
                if (best == null) {
                    toastError(R.string.sx_smart_none)
                    return@launch
                }
                val name = withContext(ioDispatcher) { MmkvManager.decodeServerConfig(best)?.remarks.orEmpty() }
                val ms = second[best]?.takeIf { it > 0L }?.let { (it + chosen.firstMs) / 2 } ?: chosen.firstMs
                toast(dataSource.getString(R.string.sx_smart_chosen, name, ms))
                updateSelectedGuid(best)
                markUserRequestedStart()
                _smartConnectRequest.update { it + 1 }
            } finally {
                _uiState.update { it.copy(smartConnecting = false) }
            }
        }
    }

    private var sortBySpeedAfter = false

    private fun startSpeedTest(guids: List<String>, sortAfter: Boolean) {
        if (guids.isEmpty()) return
        sortBySpeedAfter = sortAfter
        _uiState.update { it.copy(speedTesting = guids.toSet(), speedSession = SpeedSession(queue = guids, current = guids.first())) }
        dataSource.sendMsg2TestService(TestServiceMessage(key = AppConfig.MSG_SPEED_START, serverGuids = guids), null)
    }

    /**
     * Bulk speed test: the configs that answered the last ping (quickest first), else the list
     * order, at most [com.v2ray.ang.service.CoreTestService.SPEED_MAX]; then the list is sorted by speed.
     */
    private fun speedTestAll() {
        viewModelScope.launch(ioDispatcher) {
            val groupId = uiState.value.selectedGroupId
            val list = if (groupId.isNotBlank()) MmkvManager.decodeServerList(groupId) else MmkvManager.decodeActiveServerList()
            val pinged = list.mapNotNull { g -> MmkvManager.decodeServerAffiliationInfo(g)?.testDelayMillis?.takeIf { it > 0L }?.let { g to it } }
                .sortedBy { it.second }.map { it.first }
            val pick = (pinged.ifEmpty { list }).take(com.v2ray.ang.service.CoreTestService.SPEED_MAX)
            withContext(Dispatchers.Main) {
                if (pick.isNotEmpty()) toast(R.string.sx_speed_all_started)
                startSpeedTest(pick, sortAfter = true)
            }
        }
    }

    /** Real-delay test of just these configs (Smart Connect's retest). */
    private suspend fun pingGuids(groupId: String, guids: List<String>) {
        cancelAllPing()
        withContext(ioDispatcher) { dataSource.clearAllTestDelayResults(guids) }
        val request = testRequests.beginBulk(groupId)
        _uiState.update { it.copy(isTesting = true, status = MainStatus.Testing) }
        dataSource.sendMsg2TestService(
            TestServiceMessage(key = AppConfig.MSG_MEASURE_CONFIG_START, subscriptionId = groupId, serverGuids = guids),
            request.id,
        )
    }

    /** The user picked a server by hand: Smart/Auto-connect must not swap it on the next connect. */
    fun markManualPick() {
        lastManualPickMs = System.currentTimeMillis()
    }

    private var lastManualPickMs = 0L

    fun isAutoSelectEnabled(): Boolean =
        MmkvManager.decodeSettingsBool(AppConfig.PREF_AUTO_SELECT, true)

    fun prepareConnectGuid(): String? {
        if (!isAutoSelectEnabled()) {
            android.util.Log.i("SatraConnect", "auto-select OFF, using selected ${uiState.value.selectedGuid}")
            return uiState.value.selectedGuid
        }
        if (honorManualPick(lastManualPickMs, System.currentTimeMillis())) return uiState.value.selectedGuid
        val groupId = uiState.value.selectedGroupId
        val candidates = if (groupId.isNotBlank()) {
            MmkvManager.decodeServerList(groupId)
        } else {
            uiState.value.groups.flatMap { MmkvManager.decodeServerList(it.id) }
        }
        val now = System.currentTimeMillis()
        val samples = candidates.mapNotNull { guid ->
            val aff = MmkvManager.decodeServerAffiliationInfo(guid) ?: return@mapNotNull null
            val delay = aff.testDelayMillis
            if (delay <= 0L) return@mapNotNull null
            // Prefer geoUpdatedAt as a proxy for last measurement age when available.
            val measuredAt = aff.geoUpdatedAt.takeIf { it > 0L } ?: now
            AutoConnectController.PingSample(guid, delay, measuredAt)
        }
        val fresh = samples.any { now - it.measuredAtMs <= AutoConnectController.PING_FRESH_MS }
        if (isAutoConnectEnabled() && !fresh && candidates.isNotEmpty()) {
            // Kick a real-ping pass so the next Best selection has fresh data.
            testAllRealPing()
        }
        val best = AutoConnectController.selectBestGuid(
            candidates = candidates,
            samples = samples,
            nowMs = now,
            selectedGuid = uiState.value.selectedGuid,
            autoConnect = isAutoConnectEnabled(),
        )
        android.util.Log.i("SatraConnect", "auto-select ON, selected=${uiState.value.selectedGuid} best=$best")
        if (best != null && best != uiState.value.selectedGuid) {
            updateSelectedGuid(best)
        }
        return best ?: uiState.value.selectedGuid
    }

    private fun maybeScheduleAutoReconnect() {
        val userStop = expectUserStop ||
            MmkvManager.decodeSettingsBool(AppConfig.PREF_USER_REQUESTED_STOP, false)
        if (!AutoConnectController.shouldReconnect(
                autoConnect = isAutoConnectEnabled(),
                userRequestedStop = userStop,
                unexpectedDrop = true,
            )
        ) {
            return
        }
        val delayMs = AutoConnectController.backoffMsForAttempt(reconnectAttempt) ?: return
        reconnectJob?.cancel()
        reconnectJob = viewModelScope.launch {
            _uiState.update { it.copy(autoReconnecting = true, status = MainStatus.Testing, phase = ConnectPhase.Connecting) }
            delay(delayMs)
            reconnectAttempt++
            prepareConnectGuid()
            _autoReconnectRequest.update { it + 1 }
        }
    }

    // ---------- Running state ----------
    private fun updateRunningState(running: Boolean, clearTestingText: Boolean = true) {
        if (!running || clearTestingText) testRequests.invalidateCurrent()
        if (running) phaseJob?.cancel()
        _uiState.update { state ->
            state.copy(
                isRunning = running,
                isTesting = testRequests.isTesting,
                status = if (!clearTestingText && state.isRunning == running) state.status
                else if (running) MainStatus.Connected else MainStatus.Disconnected,
                phase = when {
                    running -> ConnectPhase.Connected
                    state.phase == ConnectPhase.Connecting || state.phase == ConnectPhase.Failed -> state.phase
                    else -> ConnectPhase.Idle
                },
                errorMessage = if (running) null else state.errorMessage,
            )
        }
    }

    /**
     * After connect, resolve the public exit IP through the local proxy and store country
     * for the selected profile (tunnel configs must not use the inbound server IP).
     */
    /** Resolve exit-IP flag after connect, or on resume while the tunnel is up. */
    fun refreshExitGeoForSelectedServer(delayMs: Long = 900L) {
        viewModelScope.launch(Dispatchers.IO) {
            if (delayMs > 0L) delay(delayMs)
            if (!uiState.value.isRunning) return@launch
            val guid = dataSource.getSelectServer()?.takeIf { it.isNotBlank() } ?: return@launch
            val result = GeoIpResolver.resolveExitAfterConnect(guid)
            if (result.exitVerified) {
                // Traffic really flowed through this config: Smart Connect remembers it.
                MmkvManager.encodeSettings(LAST_OK_PREFIX + guid, System.currentTimeMillis())
                reloadServerList()
            }
        }
    }

    override fun onCleared() {
        setupGroupJob?.cancel()
        preloadJob?.cancel()
        selectedGroupLoadJob?.cancel()
        reloadJob?.cancel()
        filterJob?.cancel()
        reconnectJob?.cancel()
        cancelAllPing()
        if (locating.isNotEmpty()) {
            dataSource.sendMsg2TestService(TestServiceMessage(key = AppConfig.MSG_LOCATE_CANCEL), null)
        }
        dataSource.close()
        super.onCleared()
    }

    // ---------- Factory ----------
    class Factory(private val application: Application, private val dataSource: MainDataSource) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                return MainViewModel(application, dataSource) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 30_000L
        const val DISCONNECT_TIMEOUT_MS = 6_000L
        const val FAILED_SHOWN_MS = 3_600L
        const val UNDO_WINDOW_MS = 5_000L
        const val GEO_FILL_BATCH = 80
        private const val GEO_RECHECK_PREFIX = "satra_geo_recheck_"
        const val LOCATE_MAX = 80
        const val SMART_MAX_WAIT_MS = 20_000L
        const val SMART_EARLY_MS = 6_000L
        const val SMART_EARLY_ANSWERS = 3
        const val SMART_RETEST_MS = 6_000L
        private const val LAST_OK_PREFIX = "satra_last_ok_"
        const val TEST_RESULT_FLUSH_INTERVAL_MS = 500L
        const val SINGLE_PING_TIMEOUT_MS = 20_000L
    }
}
