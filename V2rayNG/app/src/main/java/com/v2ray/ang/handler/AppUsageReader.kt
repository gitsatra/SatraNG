package com.v2ray.ang.handler

import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.TrafficStats
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.LogUtil

data class AppUsage(
    val uid: Int,
    val packageName: String?,
    val rxBytes: Long,
    val txBytes: Long,
) {
    val totalBytes: Long get() = rxBytes + txBytes
}

/**
 * Per-app network totals for the VPN usage meter and APP/CATEGORY limits.
 *
 * Under an active VPN the kernel often credits the *tunnel carrier* to the VPN app UID while
 * [TrafficStats] for client apps barely moves. Prefer [ConnectivityManager.TYPE_VPN] NetworkStats
 * (per-UID on the VPN network), merge-max with TrafficStats, and let [VpnAppUsageTracker] attribute
 * any leftover core bytes using active socket UIDs — one path for every package.
 */
object AppUsageReader {

    fun hasAccess(context: Context): Boolean {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun accessSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * Cumulative per-UID counters for VPN-session metering.
     * Callers must only ever use growth between two snapshots.
     */
    fun queryRaw(context: Context, startMs: Long, endMs: Long): List<AppUsage> {
        val traffic = queryTrafficStats(context)
        if (!hasAccess(context)) return traffic
        // VPN network only — Wi-Fi/mobile buckets under a tunnel often sit on the VPN app UID or
        // stay flat for clients; mixing them in hides real per-app VPN use.
        val vpn = queryNetworkStatsTypes(
            context,
            startMs,
            endMs,
            intArrayOf(ConnectivityManager.TYPE_VPN),
        )
        if (vpn.isEmpty()) {
            val all = queryNetworkStatsTypes(
                context,
                startMs,
                endMs,
                intArrayOf(
                    ConnectivityManager.TYPE_WIFI,
                    ConnectivityManager.TYPE_MOBILE,
                    ConnectivityManager.TYPE_VPN,
                ),
            )
            if (all.isEmpty()) return traffic
            if (traffic.isEmpty()) return all
            return mergeMaxByUid(traffic, all)
        }
        if (traffic.isEmpty()) return vpn
        return mergeMaxByUid(traffic, vpn)
    }

    /** Per UID, keep the larger rx and larger tx from [a] and [b]. */
    internal fun mergeMaxByUid(a: List<AppUsage>, b: List<AppUsage>): List<AppUsage> {
        val rx = HashMap<Int, Long>()
        val tx = HashMap<Int, Long>()
        for (u in a + b) {
            rx[u.uid] = maxOf(rx[u.uid] ?: 0L, u.rxBytes)
            tx[u.uid] = maxOf(tx[u.uid] ?: 0L, u.txBytes)
        }
        return rx.keys.map { uid -> AppUsage(uid, null, rx[uid] ?: 0L, tx[uid] ?: 0L) }
    }

    /** Cumulative rx/tx since boot per installed-app UID (shared UIDs collapsed). */
    fun queryTrafficStats(context: Context): List<AppUsage> {
        val rx = HashMap<Int, Long>()
        val tx = HashMap<Int, Long>()
        val pm = context.packageManager
        val apps = runCatching {
            @Suppress("DEPRECATION")
            pm.getInstalledApplications(0)
        }.getOrElse { emptyList() }
        for (info in apps) {
            val uid = info.uid
            val r = TrafficStats.getUidRxBytes(uid)
            val t = TrafficStats.getUidTxBytes(uid)
            if (r == TrafficStats.UNSUPPORTED.toLong() || t == TrafficStats.UNSUPPORTED.toLong()) continue
            if (r < 0L && t < 0L) continue
            if (uid !in rx) {
                rx[uid] = r.coerceAtLeast(0L)
                tx[uid] = t.coerceAtLeast(0L)
            }
        }
        return (rx.keys + tx.keys).map { uid ->
            AppUsage(uid, null, rx[uid] ?: 0L, tx[uid] ?: 0L)
        }
    }

    private fun queryNetworkStatsTypes(
        context: Context,
        startMs: Long,
        endMs: Long,
        types: IntArray,
    ): List<AppUsage> {
        if (!hasAccess(context)) return emptyList()
        val nsm = context.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
        val rx = HashMap<Int, Long>()
        val tx = HashMap<Int, Long>()
        for (type in types) {
            var stats: NetworkStats? = null
            try {
                stats = nsm.querySummary(type, null, startMs, endMs)
                val bucket = NetworkStats.Bucket()
                while (stats.hasNextBucket()) {
                    stats.getNextBucket(bucket)
                    val uid = bucket.uid
                    rx[uid] = (rx[uid] ?: 0L) + bucket.rxBytes
                    tx[uid] = (tx[uid] ?: 0L) + bucket.txBytes
                }
            } catch (e: Exception) {
                LogUtil.w(AppConfig.TAG, "AppUsageReader: query type=$type failed: ${e.message}")
            } finally {
                runCatching { stats?.close() }
            }
        }
        return (rx.keys + tx.keys).map { uid -> AppUsage(uid, null, rx[uid] ?: 0L, tx[uid] ?: 0L) }
    }

    /**
     * Groups per-uid byte counts into displayable rows: one per app with a package, the rest
     * (system uids) folded into one row with uid -1. Biggest first, zero rows dropped.
     */
    fun toRows(context: Context, usage: List<AppUsage>): List<AppUsage> {
        val pm = context.packageManager
        val result = usage.map { u ->
            val pkg = runCatching {
                val names = pm.getPackagesForUid(u.uid)
                names?.firstOrNull { pm.getLaunchIntentForPackage(it) != null } ?: names?.firstOrNull()
            }.getOrNull()
            u.copy(packageName = pkg)
        }
        val (named, anonymous) = result.partition { it.packageName != null }
        val merged = named.toMutableList()
        val rest = anonymous.filter { it.totalBytes > 0 }
        if (rest.isNotEmpty()) {
            merged += AppUsage(-1, null, rest.sumOf { it.rxBytes }, rest.sumOf { it.txBytes })
        }
        return merged.filter { it.totalBytes > 0 }.sortedByDescending { it.totalBytes }
    }
}
