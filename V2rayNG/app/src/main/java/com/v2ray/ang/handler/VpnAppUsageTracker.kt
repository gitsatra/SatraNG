package com.v2ray.ang.handler

import android.content.Context
import android.os.Process
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig

/**
 * Per-app traffic while the VPN is connected.
 *
 * Byte sources (universal — no per-app special cases), in order:
 * 1. Exact: core `appuid-*` / APP `lim-*` counters when process/UID routing matches
 *    ([UidTunnelMeter] + [ProcNetUidScanner] fallback inside ProcessFinder).
 * 2. Gap fill: any remaining core proxy bytes this interval are split across client UIDs that
 *    currently own sockets in `/proc/net` (same idea as RethinkDNS ConnectionTracker: know the
 *    UID of active flows, attribute tunnel volume to them).
 */
object VpnAppUsageTracker {
    const val SAMPLE_EVERY_MS = 30_000L
    const val UNATTRIBUTED_UID = -2

    private var last: HashMap<Int, LongArray>? = null
    private var anchorMs = 0L

    fun applies(): Boolean =
        (MmkvManager.decodeSettingsString(AppConfig.PREF_MODE) ?: AppConfig.VPN) == AppConfig.VPN

    @Synchronized
    fun begin(context: Context, nowMs: Long = System.currentTimeMillis()) {
        last = null
        if (!applies()) return
        anchorMs = nowMs - 3 * 3_600_000L
        last = snapshot(context, nowMs)
        if (last.isNullOrEmpty()) last = null
    }

    /**
     * Records per-UID bytes from the core datapath and/or gap-fill.
     * @return merged map for APP-limit accounting.
     */
    @Synchronized
    fun recordCoreUidBytes(nowMs: Long, byUid: Map<Int, Long>): Map<Int, Long> {
        if (byUid.isEmpty()) return emptyMap()
        val excluded = excludedUidsStatic()
        val out = HashMap<Int, Long>()
        for ((uid, bytes) in byUid) {
            if (bytes <= 0L || uid in excluded) continue
            out[uid] = (out[uid] ?: 0L) + bytes
            UsageStore.addAppTraffic(nowMs, uid, bytes, 0L)
        }
        return out
    }

    /**
     * Attribute [coreProxyBytes] that were not already counted in [alreadyAttributed].
     * Active socket UIDs from `/proc/net` share the remainder — works for every package.
     */
    @Synchronized
    fun fillUnattributed(
        nowMs: Long,
        coreProxyBytes: Long,
        alreadyAttributed: Map<Int, Long>,
        blockedUids: Set<Int> = emptySet(),
    ): Map<Int, Long> {
        val attributed = alreadyAttributed.values.sum()
        val shortfall = (coreProxyBytes - attributed).coerceAtLeast(0L)
        if (shortfall < 512L) return emptyMap()
        val excluded = excludedUidsStatic() + blockedUids
        // Prefer /proc/net socket counts; on Android 10+ SELinux often blocks that — fall back to
        // UIDs ProcessFinder recently resolved (same apps actively using the tunnel).
        val weights = ProcNetUidScanner.connectionCounts()
            .filterKeys { uid -> uid !in excluded && uid >= 10000 }
            .ifEmpty {
                UidSeenTracker.recentWeights(nowMs)
                    .filterKeys { uid -> uid !in excluded && uid >= 10000 }
            }
        if (weights.isEmpty()) {
            UsageStore.addAppTraffic(nowMs, UNATTRIBUTED_UID, shortfall, 0L)
            return mapOf(UNATTRIBUTED_UID to shortfall)
        }
        val shares = ProcNetUidScanner.distributeByWeight(shortfall, weights)
        return recordCoreUidBytes(nowMs, shares)
    }

    @Synchronized
    fun sample(context: Context, nowMs: Long = System.currentTimeMillis()): Map<Int, Long> {
        val prev = last ?: run {
            if (applies()) begin(context, nowMs)
            return emptyMap()
        }
        val cur = snapshot(context, nowMs)
        if (cur.isEmpty()) return emptyMap()
        val growth = fold(prev, cur, excludedUids(context))
        val os = HashMap<Int, Long>()
        for ((uid, rx, tx) in growth) {
            val total = rx + tx
            if (total > 0L) {
                os[uid] = total
                UsageStore.addAppTraffic(nowMs, uid, rx, tx)
            }
        }
        return os
    }

    /**
     * OS/VPN per-UID growth for APP/CATEGORY limit accounting (universal for every package).
     * Same snapshot pipeline as [sample], but does not write the Apps-list ledger again when
     * [recordToStore] is false — callers that already recorded exact datapath bytes pass false
     * and merge with max(exact, os) so block/proxy paths still advance the quota.
     */
    @Synchronized
    fun pollOsGrowth(
        context: Context,
        nowMs: Long = System.currentTimeMillis(),
        recordToStore: Boolean = true,
    ): Map<Int, Long> {
        val prev = last ?: run {
            if (applies()) begin(context, nowMs)
            return emptyMap()
        }
        val cur = snapshot(context, nowMs)
        if (cur.isEmpty()) return emptyMap()
        val growth = fold(prev, cur, excludedUids(context))
        val os = HashMap<Int, Long>()
        for ((uid, rx, tx) in growth) {
            val total = rx + tx
            if (total <= 0L) continue
            os[uid] = total
            if (recordToStore) UsageStore.addAppTraffic(nowMs, uid, rx, tx)
        }
        return os
    }

    /** Per-UID max of [a] and [b] (same interval, avoid double-counting overlapping meters). */
    fun mergeMaxGrowth(a: Map<Int, Long>, b: Map<Int, Long>): Map<Int, Long> {
        if (a.isEmpty()) return b
        if (b.isEmpty()) return a
        val out = HashMap<Int, Long>(a.size + b.size)
        for ((uid, bytes) in a) out[uid] = bytes
        for ((uid, bytes) in b) out[uid] = maxOf(out[uid] ?: 0L, bytes)
        return out
    }

    internal fun fold(prev: HashMap<Int, LongArray>, cur: Map<Int, LongArray>, excluded: Set<Int>): List<Triple<Int, Long, Long>> {
        val out = ArrayList<Triple<Int, Long, Long>>()
        for ((uid, v) in cur) {
            val p = prev[uid]
            if (p == null) {
                prev[uid] = v.copyOf()
                continue
            }
            val dRx = v[0] - p[0]
            val dTx = v[1] - p[1]
            if (dRx > 0) p[0] = v[0]
            if (dTx > 0) p[1] = v[1]
            if (uid in excluded) continue
            if (dRx > 0 || dTx > 0) out += Triple(uid, dRx.coerceAtLeast(0L), dTx.coerceAtLeast(0L))
        }
        return out
    }

    @Synchronized
    fun end(context: Context) {
        runCatching { sample(context) }
        last = null
    }

    private fun snapshot(context: Context, nowMs: Long): HashMap<Int, LongArray> {
        val map = HashMap<Int, LongArray>()
        for (u in AppUsageReader.queryRaw(context, anchorMs, nowMs + 86_400_000L)) {
            map[u.uid] = longArrayOf(u.rxBytes, u.txBytes)
        }
        return map
    }

    private fun excludedUidsStatic(): Set<Int> = setOf(Process.myUid())

    private fun excludedUids(context: Context): Set<Int> {
        val out = excludedUidsStatic().toMutableSet()
        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_PER_APP_PROXY)) {
            val apps = MmkvManager.decodeSettingsStringSet(AppConfig.PREF_PER_APP_PROXY_SET).orEmpty()
            if (apps.isNotEmpty()) {
                val pm = context.packageManager
                val bypass = MmkvManager.decodeSettingsBool(AppConfig.PREF_BYPASS_APPS)
                val listed = apps.mapNotNull { runCatching { pm.getApplicationInfo(it, 0).uid }.getOrNull() }.toSet()
                if (bypass) {
                    out += listed
                } else {
                    val all = last?.keys.orEmpty()
                    out += all.filterNot { it in listed }
                }
            }
        }
        runCatching { context.packageManager.getApplicationInfo(BuildConfig.APPLICATION_ID, 0).uid }.getOrNull()?.let { out += it }
        return out
    }
}
