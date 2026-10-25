package com.v2ray.ang.handler

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.system.OsConstants
import com.v2ray.ang.AppConfig
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap

/**
 * Universal owning-UID resolution for every TUN / local-proxy flow (no per-app branches).
 *
 * APP/CATEGORY limits match Xray `process` rules by UID. When the platform cannot name the
 * owner, those rules miss and the packet falls through to `proxy` — a bypass of both metering
 * and block. This resolver closes that hole the same way for every package:
 *
 * 1. [ConnectivityManager.getConnectionOwnerUid] (API 29+)
 * 2. Local HTTP/SOCKS inbound probes (system proxy path)
 * 3. `/proc/net` port ownership ([ProcNetUidScanner]) when SELinux allows
 * 4. Short sticky cache by local port (same flow, later packets)
 * 5. While any APP/CATEGORY cap is exhausted: unidentified flows inherit a blocked UID so the
 *    existing `process → block` rules match — anti-bypass for every limited app at once
 */
object ProcessUidResolver {

    private const val CACHE_TTL_MS = 30_000L

    /** network:srcPort → (uid, timestamp) */
    private val portCache = ConcurrentHashMap<String, Pair<Int, Long>>()

    /** destIp:destPort → (uid, timestamp) — ties later unidentified packets to a known owner. */
    private val destCache = ConcurrentHashMap<String, Pair<Int, Long>>()

    fun clearCache() {
        portCache.clear()
        destCache.clear()
    }

    fun resolve(
        context: Context,
        network: String,
        srcIp: String,
        srcPort: Int,
        destIp: String,
        destPort: Int,
    ): Int {
        if (srcPort !in 1..65535) return -1
        val now = System.currentTimeMillis()
        val cacheKey = "${network.lowercase()}:$srcPort"
        portCache[cacheKey]?.let { (uid, ts) ->
            if (now - ts <= CACHE_TTL_MS && uid > 0) return uid
        }

        var uid = -1
        val proto = when (network.lowercase()) {
            "tcp" -> OsConstants.IPPROTO_TCP
            "udp" -> OsConstants.IPPROTO_UDP
            else -> null
        }
        val cm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.getSystemService(ConnectivityManager::class.java)
        } else {
            null
        }

        if (cm != null && proto != null) {
            if (destIp.isNotBlank() && destPort in 1..65535) {
                uid = ownerUid(cm, proto, srcIp, srcPort, destIp, destPort)
            }
            // System HTTP/SOCKS proxy: core often omits dest; owner is who dialed our inbound.
            if (uid <= 0) {
                uid = ownerViaLocalInbounds(cm, proto, srcIp, srcPort)
            }
            // Some OEMs only answer when local is the unspecified address.
            if (uid <= 0 && destIp.isNotBlank() && destPort in 1..65535) {
                uid = ownerUid(cm, proto, "0.0.0.0", srcPort, destIp, destPort)
                if (uid <= 0 && destIp.contains(':')) {
                    uid = ownerUid(cm, proto, "::", srcPort, destIp, destPort)
                }
            }
        }

        if (uid <= 0) {
            uid = ProcNetUidScanner.findUid(network, srcIp, srcPort, destIp, destPort)
        }

        // Dest sticky: unidentified keep-alives to a host recently owned by a blocked UID
        // still match process→block (complements the TUN routing catch-all).
        if (uid <= 0) {
            uid = antiBypassUid(
                blockedUids = UsageLimitEngine.blockedUids(context, now),
                destIp = destIp,
                destPort = destPort,
                nowMs = now,
            )
        }

        if (uid > 0) {
            portCache[cacheKey] = uid to now
            UidSeenTracker.note(uid)
            if (destIp.isNotBlank() && destPort in 1..65535) {
                destCache["$destIp:$destPort"] = uid to now
            }
        }
        return uid
    }

    /**
     * Reuse a cached destination owner only when that UID is currently blocked.
     * Prevents unidentified keep-alives to the same host from falling through to `proxy`.
     */
    internal fun antiBypassUid(
        blockedUids: Set<Int>,
        destIp: String,
        destPort: Int,
        nowMs: Long = System.currentTimeMillis(),
        destOwner: Pair<Int, Long>? = null,
    ): Int {
        if (blockedUids.isEmpty()) return -1
        if (destIp.isBlank() || destPort !in 1..65535) return -1
        val cached = destOwner ?: destCache["$destIp:$destPort"] ?: return -1
        val (uid, ts) = cached
        if (nowMs - ts > CACHE_TTL_MS || uid <= 0) return -1
        return if (uid in blockedUids) uid else -1
    }

    private fun ownerViaLocalInbounds(
        cm: ConnectivityManager,
        proto: Int,
        srcIp: String,
        srcPort: Int,
    ): Int {
        val ports = listOf(
            SettingsManager.getHttpPort(),
            SettingsManager.getSocksPort(),
        ).distinct().filter { it in 1..65535 }
        for (localPort in ports) {
            var uid = ownerUid(cm, proto, srcIp, srcPort, AppConfig.LOOPBACK, localPort)
            if (uid > 0) return uid
            uid = ownerUid(cm, proto, "0.0.0.0", srcPort, AppConfig.LOOPBACK, localPort)
            if (uid > 0) return uid
            uid = ownerUid(cm, proto, "::1", srcPort, AppConfig.LOOPBACK, localPort)
            if (uid > 0) return uid
            uid = ownerUid(cm, proto, "::1", srcPort, "::1", localPort)
            if (uid > 0) return uid
        }
        return -1
    }

    private fun ownerUid(
        cm: ConnectivityManager,
        proto: Int,
        srcIp: String,
        srcPort: Int,
        destIp: String,
        destPort: Int,
    ): Int {
        if (destIp.isBlank() || destPort !in 1..65535) return -1
        if (srcPort !in 0..65535) return -1
        if (android.os.Build.VERSION.SDK_INT < 29) return -1
        return try {
            cm.getConnectionOwnerUid(
                proto,
                InetSocketAddress(srcIp, srcPort),
                InetSocketAddress(destIp, destPort),
            )
        } catch (_: Exception) {
            -1
        }
    }
}
