package com.v2ray.ang.handler

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Process
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.V2rayConfig
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.LogUtil

/**
 * Industry-style per-app VPN metering on the Xray TUN datapath.
 *
 * For every client UID, traffic is routed to its own clone of the proxy outbound (`appuid-<uid>`).
 * The core resolves the owning UID via [ConnectivityManager.getConnectionOwnerUid] (process rules)
 * and [UsageSampler] reads those outbound counters — bytes are counted when they traverse the
 * tunnel with a known UID, not guessed later from TrafficStats.
 *
 * Requires Xray TUN + route-only ([SettingsManager.canUseProcessRouting]); [ensureEngine] turns
 * that on for VPN sessions on Android 10+.
 */
object UidTunnelMeter {

    const val TAG_PREFIX = "appuid-"

    /** Soft cap so huge system images do not explode the config; user/launchable apps preferred. */
    private const val MAX_UID_RULES = 180

    fun tagForUid(uid: Int): String = "$TAG_PREFIX$uid"

    fun parseUid(tag: String): Int? {
        if (!tag.startsWith(TAG_PREFIX)) return null
        return tag.removePrefix(TAG_PREFIX).toIntOrNull()
    }

    /** Turn on the engine that can resolve per-connection UIDs. */
    fun ensureEngine() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        UsageLimitEngine.enableAppEnforcement()
    }

    /**
     * Injects per-UID process → `appuid-*` outbound clones.
     * Call **before** [UsageLimitEngine.applyToConfig] so limit rules are inserted in front and win.
     */
    fun applyToConfig(context: Context, config: V2rayConfig) {
        if (!SettingsManager.canUseProcessRouting()) {
            LogUtil.i(AppConfig.TAG, "UidTunnelMeter: skipped (process routing off)")
            return
        }
        val proxy = config.outbounds.firstOrNull { it.tag == AppConfig.TAG_PROXY }
        if (proxy == null) {
            LogUtil.w(AppConfig.TAG, "UidTunnelMeter: no plain proxy outbound to clone")
            return
        }

        val selfUid = Process.myUid()
        val blocked = UsageLimitEngine.blockedUids(context)
        val uids = clientUids(context).filter { it != selfUid && it !in blocked }.take(MAX_UID_RULES)
        if (uids.isEmpty()) return

        val proxyJson = JsonUtil.toJson(proxy)
        val rules = ArrayList<V2rayConfig.RoutingBean.RulesBean>(uids.size)
        for (uid in uids) {
            val tag = tagForUid(uid)
            if (config.outbounds.any { it.tag == tag }) continue
            val copy = JsonUtil.fromJson(proxyJson, V2rayConfig.OutboundBean::class.java) ?: continue
            copy.tag = tag
            config.outbounds.add(copy)
            rules += V2rayConfig.RoutingBean.RulesBean(
                process = listOf(uid.toString()),
                outboundTag = tag,
            )
        }
        if (rules.isNotEmpty()) {
            config.routing.rules.addAll(0, rules)
            LogUtil.i(AppConfig.TAG, "UidTunnelMeter: ${rules.size} per-UID metering rules")
        }
    }

    /**
     * UIDs we meter: launchable apps first, then other non-system packages.
     * Same selection for every device — no per-app special cases.
     */
    internal fun clientUids(context: Context): List<Int> {
        val pm = context.packageManager
        val apps = runCatching {
            @Suppress("DEPRECATION")
            pm.getInstalledApplications(0)
        }.getOrElse { emptyList() }

        val launchable = LinkedHashSet<Int>()
        val other = LinkedHashSet<Int>()
        for (info in apps) {
            val uid = info.uid
            if (uid < 10000) continue
            val isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val canLaunch = runCatching { pm.getLaunchIntentForPackage(info.packageName) != null }.getOrDefault(false)
            when {
                canLaunch -> launchable += uid
                !isSystem -> other += uid
            }
        }
        return (launchable + other).toList()
    }
}
