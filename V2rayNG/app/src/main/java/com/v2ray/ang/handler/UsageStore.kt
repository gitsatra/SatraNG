package com.v2ray.ang.handler

import com.google.gson.Gson
import com.tencent.mmkv.MMKV
import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.LogUtil

data class DayUsage(
    val dayKey: String,
    val startMs: Long,
    val upBytes: Long,
    val downBytes: Long,
    val connectedSec: Long,
) {
    val totalBytes: Long get() = upBytes + downBytes
}

data class SessionRecord(
    val startMs: Long = 0L,
    val endMs: Long = 0L,
    val upBytes: Long = 0L,
    val downBytes: Long = 0L,
    val server: String = "",
)

/** Live numbers published by the daemon's [UsageSampler] once per second. */
data class LiveUsage(
    val running: Boolean = false,
    val upBps: Long = 0L,
    val downBps: Long = 0L,
    val sessionStartMs: Long = 0L,
    val sessionUp: Long = 0L,
    val sessionDown: Long = 0L,
    val updatedAt: Long = 0L,
)

/**
 * Multi-process ledger for everything the usage screens show. The daemon process is the only
 * writer of traffic counters; the UI reads them. Keys are small and flat on purpose.
 */
object UsageStore {
    private const val ID = "satra_usage"
    private const val MAX_SESSIONS = 100
    private val gson = Gson()
    private val kv: MMKV by lazy { MMKV.mmkvWithID(ID, MMKV.MULTI_PROCESS_MODE) }

    fun getString(key: String): String? = kv.decodeString(key)
    fun putString(key: String, value: String) { kv.encode(key, value) }
    fun getLong(key: String, def: Long = 0L): Long = kv.decodeLong(key, def)
    fun putLong(key: String, value: Long) { kv.encode(key, value) }
    fun getBool(key: String, def: Boolean = false): Boolean = kv.decodeBool(key, def)
    fun putBool(key: String, value: Boolean) { kv.encode(key, value) }
    fun remove(key: String) { kv.removeValueForKey(key) }

    // ---------- ledger ----------

    fun addTraffic(nowMs: Long, up: Long, down: Long) {
        if (up <= 0L && down <= 0L) return
        val day = UsagePeriods.dayKey(nowMs)
        val hour = UsagePeriods.hourOfDay(nowMs)
        if (up > 0) kv.encode("d:$day:up", kv.decodeLong("d:$day:up", 0L) + up)
        if (down > 0) kv.encode("d:$day:dn", kv.decodeLong("d:$day:dn", 0L) + down)
        val hk = "h:$day:$hour"
        kv.encode(hk, kv.decodeLong(hk, 0L) + up.coerceAtLeast(0L) + down.coerceAtLeast(0L))
    }

    /** Bytes that went through the proxy while [guid] was the active config (all time, approximate). */
    fun addServerTraffic(guid: String, bytes: Long) {
        if (guid.isBlank() || bytes <= 0L) return
        val k = "s:$guid"
        kv.encode(k, kv.decodeLong(k, 0L) + bytes)
    }

    /** Per-config, per-day up/down (keys sd:<day>:<guid>:u|d). */
    fun addServerDay(nowMs: Long, guid: String, up: Long, down: Long) {
        if (guid.isBlank()) return
        val day = UsagePeriods.dayKey(nowMs)
        if (up > 0) kv.encode("sd:$day:$guid:u", kv.decodeLong("sd:$day:$guid:u", 0L) + up)
        if (down > 0) kv.encode("sd:$day:$guid:d", kv.decodeLong("sd:$day:$guid:d", 0L) + down)
    }

    /** (up, down) for [guid] over the last [n] days including today. */
    fun serverUsage(guid: String, n: Int, nowMs: Long = System.currentTimeMillis()): Pair<Long, Long> {
        var u = 0L; var d = 0L
        for (i in 0 until n) {
            val day = UsagePeriods.dayKey(nowMs - i * 86_400_000L)
            u += kv.decodeLong("sd:$day:$guid:u", 0L); d += kv.decodeLong("sd:$day:$guid:d", 0L)
        }
        return u to d
    }

    fun serverTraffic(guid: String): Long = if (guid.isBlank()) 0L else kv.decodeLong("s:$guid", 0L)

    /** Per-app bytes seen while the VPN was up (see [VpnAppUsageTracker]); keyed by day and uid. */
    fun addAppTraffic(nowMs: Long, uid: Int, rx: Long, tx: Long) {
        if (rx <= 0L && tx <= 0L) return
        val day = UsagePeriods.dayKey(nowMs)
        if (rx > 0) kv.encode("a:$day:$uid:r", kv.decodeLong("a:$day:$uid:r", 0L) + rx)
        if (tx > 0) kv.encode("a:$day:$uid:t", kv.decodeLong("a:$day:$uid:t", 0L) + tx)
        // Lifetime through the VPN — independent of day charts and of any limit counter.
        addAppLifetime(uid, rx, tx)
    }

    /**
     * All-time per-UID VPN bytes. Survives day pruning and limit reset; only [clearLedger] clears it.
     * Never seeds a usage-limit period counter.
     */
    fun addAppLifetime(uid: Int, rx: Long, tx: Long) {
        if (rx <= 0L && tx <= 0L) return
        if (rx > 0) kv.encode("lt:$uid:r", kv.decodeLong("lt:$uid:r", 0L) + rx)
        if (tx > 0) kv.encode("lt:$uid:t", kv.decodeLong("lt:$uid:t", 0L) + tx)
    }

    fun appLifetime(uid: Int): AppUsage {
        val rx = kv.decodeLong("lt:$uid:r", 0L)
        val tx = kv.decodeLong("lt:$uid:t", 0L)
        return AppUsage(uid, null, rx, tx)
    }

    fun appLifetimeAll(): Map<Int, AppUsage> {
        val rx = HashMap<Int, Long>()
        val tx = HashMap<Int, Long>()
        kv.allKeys()?.forEach { k ->
            if (!k.startsWith("lt:")) return@forEach
            val parts = k.split(':')
            if (parts.size != 3) return@forEach
            val uid = parts[1].toIntOrNull() ?: return@forEach
            val v = kv.decodeLong(k, 0L)
            if (parts[2] == "r") rx[uid] = (rx[uid] ?: 0L) + v else tx[uid] = (tx[uid] ?: 0L) + v
        }
        return (rx.keys + tx.keys).associateWith { AppUsage(it, null, rx[it] ?: 0L, tx[it] ?: 0L) }
    }

    /** Per-uid VPN-session totals over the given local days. */
    fun appTraffic(dayKeys: Collection<String>): List<AppUsage> {
        val days = dayKeys.toHashSet()
        val rx = HashMap<Int, Long>()
        val tx = HashMap<Int, Long>()
        kv.allKeys()?.forEach { k ->
            if (!k.startsWith("a:")) return@forEach
            val parts = k.split(':')
            if (parts.size != 4 || parts[1] !in days) return@forEach
            val uid = parts[2].toIntOrNull() ?: return@forEach
            val v = kv.decodeLong(k, 0L)
            if (parts[3] == "r") rx[uid] = (rx[uid] ?: 0L) + v else tx[uid] = (tx[uid] ?: 0L) + v
        }
        return (rx.keys + tx.keys).map { AppUsage(it, null, rx[it] ?: 0L, tx[it] ?: 0L) }
    }

    fun addConnectedSeconds(nowMs: Long, seconds: Long) {
        if (seconds <= 0L) return
        val k = "d:${UsagePeriods.dayKey(nowMs)}:s"
        kv.encode(k, kv.decodeLong(k, 0L) + seconds)
    }

    fun day(dayKey: String, startMs: Long = 0L) = DayUsage(
        dayKey, startMs,
        kv.decodeLong("d:$dayKey:up", 0L),
        kv.decodeLong("d:$dayKey:dn", 0L),
        kv.decodeLong("d:$dayKey:s", 0L),
    )

    /** The last [n] days including today, oldest first (empty days included). */
    fun days(n: Int, nowMs: Long = System.currentTimeMillis()): List<DayUsage> {
        val keys = UsagePeriods.lastDayKeys(n, nowMs)
        val starts = UsagePeriods.lastDayStarts(n, nowMs)
        return keys.mapIndexed { i, k -> day(k, starts[i]) }
    }

    /** Bytes per hour of the local day containing [nowMs]. */
    fun hourly(nowMs: Long = System.currentTimeMillis()): LongArray {
        val day = UsagePeriods.dayKey(nowMs)
        return LongArray(24) { kv.decodeLong("h:$day:$it", 0L) }
    }

    /** Total tunnelled bytes since [startMs] (local midnight aligned) up to now. */
    fun totalSince(startMs: Long, nowMs: Long = System.currentTimeMillis()): Long {
        var sum = 0L
        var t = UsagePeriods.startOfDay(startMs)
        var guard = 0
        while (t <= nowMs && guard++ < 400) {
            sum += day(UsagePeriods.dayKey(t)).totalBytes
            t = UsagePeriods.nextReset(LimitPeriod.DAILY, t)
        }
        return sum
    }

    // ---------- sessions ----------

    fun sessions(): List<SessionRecord> = try {
        getString("sessions")?.let { gson.fromJson(it, Array<SessionRecord>::class.java)?.toList() }.orEmpty()
    } catch (e: Exception) {
        LogUtil.e(AppConfig.TAG, "UsageStore: sessions parse", e)
        emptyList()
    }

    fun appendSession(record: SessionRecord) {
        if (record.endMs - record.startMs < 3_000L) return
        val list = (sessions() + record).takeLast(MAX_SESSIONS)
        putString("sessions", gson.toJson(list))
    }

    // ---------- live ----------

    fun publishLive(live: LiveUsage) {
        kv.encode("live:run", live.running)
        kv.encode("live:up", live.upBps)
        kv.encode("live:dn", live.downBps)
        kv.encode("live:ss", live.sessionStartMs)
        kv.encode("live:su", live.sessionUp)
        kv.encode("live:sd", live.sessionDown)
        kv.encode("live:ts", live.updatedAt)
    }

    fun live(nowMs: Long = System.currentTimeMillis()): LiveUsage {
        val ts = kv.decodeLong("live:ts", 0L)
        val running = kv.decodeBool("live:run", false) && nowMs - ts < 6_000L
        if (!running) return LiveUsage(running = false, updatedAt = ts)
        return LiveUsage(
            true,
            kv.decodeLong("live:up", 0L), kv.decodeLong("live:dn", 0L),
            kv.decodeLong("live:ss", 0L), kv.decodeLong("live:su", 0L), kv.decodeLong("live:sd", 0L), ts,
        )
    }

    // ---------- rule runtime ----------

    fun ruleRuntime(id: String): RuleRuntime? {
        val pk = kv.decodeString("r:$id:pk") ?: return null
        return RuleRuntime(
            pk,
            kv.decodeLong("r:$id:used", 0L),
            kv.decodeBool("r:$id:blk", false),
            kv.decodeBool("r:$id:warn", false),
            kv.decodeLong("r:$id:bonus", 0L),
        )
    }

    fun putRuleRuntime(id: String, rt: RuleRuntime) {
        kv.encode("r:$id:pk", rt.periodKey)
        kv.encode("r:$id:used", rt.used)
        kv.encode("r:$id:blk", rt.blocked)
        kv.encode("r:$id:warn", rt.warned)
        kv.encode("r:$id:bonus", rt.bonus)
    }

    /** Called by the notification action (UI process). The daemon sees the bonus on its next tick. */
    /** Bumped by the UI when it resets a rule, so a daemon tick that raced with it doesn't undo it. */
    fun ruleGeneration(id: String): Int = kv.decodeInt("r:$id:gen", 0)

    fun resetRuleRuntime(id: String, rt: RuleRuntime) {
        kv.encode("r:$id:gen", ruleGeneration(id) + 1)
        putRuleRuntime(id, rt)
    }

    fun addBonus(id: String, bytes: Long) {
        val k = "r:$id:bonus"
        // Bump the generation first so a daemon tick computed from the old bonus is dropped.
        kv.encode("r:$id:gen", ruleGeneration(id) + 1)
        kv.encode(k, kv.decodeLong(k, 0L) + bytes)
    }

    fun clearRule(id: String) {
        listOf("pk", "used", "blk", "warn", "bonus", "gen").forEach { kv.removeValueForKey("r:$id:$it") }
    }

    // ---------- housekeeping ----------

    /** Drops ledger keys older than [keepDays]. Cheap; run occasionally from the daemon. */
    fun prune(keepDays: Int = 400, nowMs: Long = System.currentTimeMillis()) {
        val cutoff = UsagePeriods.dayKey(nowMs - keepDays * 86_400_000L)
        kv.allKeys()?.filter { it.startsWith("d:") || it.startsWith("h:") || it.startsWith("a:") }?.forEach { k ->
            val day = k.split(':').getOrNull(1) ?: return@forEach
            if (day < cutoff) kv.removeValueForKey(k)
        }
    }

    fun clearLedger() {
        kv.allKeys()?.filter {
            it.startsWith("d:") || it.startsWith("h:") || it.startsWith("a:") ||
                it.startsWith("s:") || it.startsWith("lt:")
        }?.forEach { kv.removeValueForKey(it) }
        kv.removeValueForKey("sessions")
    }
}
