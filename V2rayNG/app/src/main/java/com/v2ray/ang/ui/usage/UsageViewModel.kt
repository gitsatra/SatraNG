package com.v2ray.ang.ui.usage

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.core.LauncherManager
import com.v2ray.ang.handler.AppUsage
import com.v2ray.ang.handler.AppUsageReader
import com.v2ray.ang.handler.DayUsage
import com.v2ray.ang.handler.GlobalLimit
import com.v2ray.ang.handler.RuleKind
import com.v2ray.ang.handler.UsageLimitEngine
import com.v2ray.ang.handler.UsagePeriods
import com.v2ray.ang.handler.UsageRule
import com.v2ray.ang.handler.UsageRulesRepository
import com.v2ray.ang.handler.UsageStore
import com.v2ray.ang.handler.LimitPeriod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppUsageRow(
    val uid: Int,
    val packageName: String?,
    val label: String,
    val rxBytes: Long,
    val txBytes: Long,
    /** All-time VPN bytes for this UID; independent of the selected day range and of any limit. */
    val lifetimeBytes: Long = 0L,
    /** True when split tunneling currently keeps this app off the VPN. */
    val bypassingVpn: Boolean = false,
) {
    val totalBytes: Long get() = rxBytes + txBytes
}

data class RuleRow(
    val rule: UsageRule,
    val usedBytes: Long,
    val bonusBytes: Long,
    val blocked: Boolean,
    val nextResetMs: Long,
    /** Packages are shown with their launcher icon; domain rules use a generic glyph. */
    val iconPackage: String?,
    /** App rule that the core cannot enforce yet (needs Xray TUN engine). */
    val needsEngine: Boolean,
) {
    val capBytes: Long get() = rule.limitBytes + bonusBytes
    val fraction: Float get() = if (rule.limitBytes > 0L) (usedBytes.toDouble() / capBytes).toFloat().coerceIn(0f, 1f) else 1f
}

data class GlobalRow(val limit: GlobalLimit, val usedBytes: Long, val nextResetMs: Long)

data class UsageUiState(
    val rangeDays: Int = 7,
    val days: List<DayUsage> = emptyList(),
    val totalBytes: Long = 0L,
    val averageBytes: Long = 0L,
    val connectedSeconds: Long = 0L,
    val apps: List<AppUsageRow> = emptyList(),
    val hasUsageAccess: Boolean = false,
    val appsLoading: Boolean = false,
    val rules: List<RuleRow> = emptyList(),
    val global: GlobalRow = GlobalRow(GlobalLimit(), 0L, 0L),
    val appRulesSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q,
    val engineReady: Boolean = false,
    /** Remembered answer to the engine sheet; null = never asked. */
    val engineChoice: Boolean? = null,
)

interface UsageActions {
    fun setRange(days: Int)
    fun saveRule(rule: UsageRule)
    fun deleteRule(id: String)
    /** Counter back to zero for the current period; lifts a block. */
    fun resetRule(id: String)
    fun setRuleEnabled(id: String, enabled: Boolean)
    fun saveGlobal(limit: GlobalLimit)
    fun clearHistory()
    fun setEngineChoice(on: Boolean)
}

class UsageViewModel(app: Application) : AndroidViewModel(app), UsageActions {

    private val _state = MutableStateFlow(UsageUiState(hasUsageAccess = AppUsageReader.hasAccess(app)))
    val state: StateFlow<UsageUiState> = _state.asStateFlow()

    /** Last 7 days for the Home card. */
    val week: StateFlow<List<DayUsage>> = flow {
        while (true) {
            emit(UsageStore.days(7))
            delay(5_000)
        }
    }.flowOn(Dispatchers.IO).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var appsJob: Job? = null
    private var tickJob: Job? = null
    private val labelCache = HashMap<String, String>()

    fun onVisible() {
        if (tickJob?.isActive == true) return
        // Charts and rule counters refresh every few seconds (live speed lives on Home and is
        // per second there). Unchanged data produces an equal state, so nothing recomposes.
        tickJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                refreshLedger()
                // While Usage is visible, refresh app meter every tick (~5s).
                loadApps()
                delay(LEDGER_REFRESH_MS)
            }
        }
    }

    fun onHidden() {
        tickJob?.cancel()
        tickJob = null
    }

    override fun setRange(days: Int) {
        if (_state.value.rangeDays == days) return
        _state.update { it.copy(rangeDays = days) }
        viewModelScope.launch(Dispatchers.IO) { refreshLedger() }
        refreshApps()
    }

    fun onAccessMaybeChanged() {
        val has = AppUsageReader.hasAccess(getApplication())
        _state.update { it.copy(hasUsageAccess = has) }
        if (has) refreshApps()
    }

    private fun refreshLedger() {
        val now = System.currentTimeMillis()
        val range = _state.value.rangeDays
        val days = UsageStore.days(range, now)
        val total = days.sumOf { it.totalBytes }
        val rules = UsageRulesRepository.rules().map { rule ->
            val used = UsageLimitEngine.usedNow(rule, now)
            val blocked = UsageLimitEngine.isBlockedNow(rule, now)
            RuleRow(
                rule = rule,
                usedBytes = used,
                bonusBytes = UsageLimitEngine.bonusNow(rule, now),
                blocked = blocked,
                nextResetMs = UsagePeriods.nextReset(rule.period, now),
                iconPackage = rule.packageName.takeIf { rule.kind == RuleKind.APP },
                needsEngine = rule.matchesApps && rule.enabled && !UsageLimitEngine.canEnforceAppRules(),
            )
        }
        val g = UsageRulesRepository.global()
        val gUsed = if (g.enabled) UsageStore.totalSince(UsagePeriods.periodStart(g.period, now), now) else 0L
        _state.update {
            it.copy(
                days = days,
                totalBytes = total,
                averageBytes = if (range > 0) total / range else 0L,
                connectedSeconds = days.sumOf { d -> d.connectedSec },
                rules = rules,
                global = GlobalRow(g, gUsed, UsagePeriods.nextReset(g.period, now)),
                engineReady = UsageLimitEngine.canEnforceAppRules(),
                engineChoice = UsageLimitEngine.engineChoice(),
            )
        }
    }

    fun refreshApps() {
        appsJob?.cancel()
        val ctx = getApplication<Application>()
        if (_state.value.apps.isEmpty()) {
            _state.update { it.copy(hasUsageAccess = AppUsageReader.hasAccess(ctx), appsLoading = true) }
        }
        appsJob = viewModelScope.launch(Dispatchers.IO) { loadApps() }
    }

    /**
     * Per-app rows come only from traffic recorded while the VPN was connected
     * ([com.v2ray.ang.handler.VpnAppUsageTracker]); Android's lifetime totals are never shown.
     * Metering does not require Usage Access (TrafficStats fallback); with access, NetworkStats+VPN is preferred.
     */
    private fun loadApps() {
        val ctx = getApplication<Application>()
        val has = AppUsageReader.hasAccess(ctx)
        // Sample while the Usage screen is open so numbers move without waiting for the daemon cadence.
        runCatching { com.v2ray.ang.handler.VpnAppUsageTracker.sample(ctx) }
        val keys = UsagePeriods.lastDayKeys(_state.value.rangeDays, System.currentTimeMillis())
        val lifetime = UsageStore.appLifetimeAll()
        val rows = AppUsageReader.toRows(ctx, UsageStore.appTraffic(keys)).take(60).map { u ->
            u.toRow(lifetime[u.uid]?.totalBytes ?: 0L)
        }
        _state.update { it.copy(hasUsageAccess = has, apps = rows, appsLoading = false) }
    }

    private fun AppUsage.toRow(lifetimeBytes: Long = 0L): AppUsageRow {
        val pm = getApplication<Application>().packageManager
        val pkg = packageName
        val label = if (pkg == null) UNLABELED else labelCache.getOrPut(pkg) {
            runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
        }
        val bypass = pkg != null && UsageLimitEngine.isPackageBypassingVpn(pkg)
        return AppUsageRow(uid, packageName, label, rxBytes, txBytes, lifetimeBytes, bypass)
    }

    // ---- rules ----

    override fun saveRule(rule: UsageRule) {
        viewModelScope.launch(Dispatchers.IO) {
            val isNew = rule.id.isBlank()
            var saved = if (isNew) rule.copy(id = UsageRule.newId(), createdAt = System.currentTimeMillis()) else rule
            if (saved.kind == RuleKind.DOMAIN) {
                saved = saved.copy(domains = UsageLimitEngine.expandSitePresetDomains(saved.domains))
            }
            val needsVpnRestart = UsageLimitEngine.prepareForRule(getApplication(), saved)
            UsageRulesRepository.upsert(saved)
            // A new rule's period counter starts at zero — never seeded from lifetime app meter.
            if (isNew) UsageLimitEngine.resetRule(saved) else UsageLimitEngine.reevaluate(saved)
            if (needsVpnRestart) {
                withContext(Dispatchers.Main) {
                    LauncherManager.restartIfRunning(getApplication())
                }
            } else {
                applyPolicy()
            }
            refreshLedger()
        }
    }

    override fun deleteRule(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            UsageRulesRepository.delete(id)
            applyPolicy()
            refreshLedger()
        }
    }

    override fun setEngineChoice(on: Boolean) {
        UsageLimitEngine.setEngineChoice(on)
        _state.update { it.copy(engineChoice = on) }
    }

    override fun resetRule(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            UsageRulesRepository.rules().firstOrNull { it.id == id }?.let {
                UsageLimitEngine.resetRule(it)
                applyPolicy()
                refreshLedger()
            }
        }
    }

    override fun setRuleEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            UsageRulesRepository.rules().firstOrNull { it.id == id }?.let {
                UsageRulesRepository.upsert(it.copy(enabled = enabled))
                applyPolicy()
                refreshLedger()
            }
        }
    }

    override fun saveGlobal(limit: GlobalLimit) {
        viewModelScope.launch(Dispatchers.IO) {
            UsageRulesRepository.saveGlobal(limit)
            refreshLedger()
        }
    }

    override fun clearHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            UsageStore.clearLedger()
            refreshLedger()
        }
    }

    /** Tells a running core to rebuild its routing so rule edits take effect without reconnecting. */
    private suspend fun applyPolicy() = withContext(Dispatchers.Main) {
        LauncherManager.reloadPolicy(getApplication())
    }

    companion object {
        const val UNLABELED = "\u0000other"
        const val LEDGER_REFRESH_MS = 5_000L
    }
}
