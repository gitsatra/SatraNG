package com.v2ray.ang.handler

import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Kernel socket tables under `/proc/net` (tcp, tcp6, udp, udp6).
 *
 * Used for:
 * 1. Resolving the owning UID of a flow by local port (and optional remote) when
 *    [android.net.ConnectivityManager.getConnectionOwnerUid] fails — the method PCAPdroid-era
 *    VPNs used before API 29, and still the reliable fallback for QUIC/UDP under a TUN.
 * 2. Connection-count weights as a last-resort byte split.
 *
 * Same path for every package; no per-app special cases.
 */
object ProcNetUidScanner {

    private val TABLES_TCP = listOf("/proc/net/tcp", "/proc/net/tcp6")
    private val TABLES_UDP = listOf("/proc/net/udp", "/proc/net/udp6")
    private val TABLES_ALL = TABLES_TCP + TABLES_UDP

    /** Short-lived cache: key to uid. */
    private val cache = ConcurrentHashMap<String, Pair<Int, Long>>()
    private const val CACHE_TTL_MS = 15_000L

    private data class SocketRow(val uid: Int, val localPort: Int, val remotePort: Int)

    /** UID to number of sockets currently listed. */
    fun connectionCounts(): Map<Int, Int> {
        val out = HashMap<Int, Int>()
        for (path in TABLES_ALL) {
            val rows = readRows(path)
            for (i in rows.indices) {
                val uid = rows[i].uid
                out[uid] = (out[uid] ?: 0) + 1
            }
        }
        return out
    }

    /**
     * Find the UID that owns a local socket.
     * Prefer an exact local+remote match; fall back to local-port-only (works when the core
     * passes a fake/resolved dest that does not appear in the kernel table).
     */
    fun findUid(network: String, srcIp: String, srcPort: Int, destIp: String = "", destPort: Int = 0): Int {
        if (srcPort <= 0 || srcPort > 65535) return -1
        val now = System.currentTimeMillis()
        val cacheKey = "$network:$srcIp:$srcPort:$destIp:$destPort"
        cache[cacheKey]?.let { (uid, ts) ->
            if (now - ts <= CACHE_TTL_MS) return uid
        }

        val tables = when (network.lowercase()) {
            "tcp" -> TABLES_TCP
            "udp" -> TABLES_UDP
            else -> TABLES_ALL
        }
        val wantRemotePort = destPort.takeIf { it in 1..65535 }
        var portOnlyUid = -1

        for (path in tables) {
            val rows = readRows(path)
            for (i in rows.indices) {
                val row = rows[i]
                if (row.localPort != srcPort) continue
                if (wantRemotePort != null && row.remotePort == wantRemotePort && row.uid >= 10000) {
                    cache[cacheKey] = row.uid to now
                    return row.uid
                }
                if (portOnlyUid < 0 && row.uid >= 10000) portOnlyUid = row.uid
            }
        }
        if (portOnlyUid >= 0) {
            cache[cacheKey] = portOnlyUid to now
            return portOnlyUid
        }
        return -1
    }

    internal fun distributeByWeight(bytes: Long, weights: Map<Int, Int>): Map<Int, Long> {
        if (bytes <= 0L || weights.isEmpty()) return emptyMap()
        val positive = weights.filterValues { it > 0 }
        if (positive.isEmpty()) return emptyMap()
        val totalW = positive.values.sum().toLong()
        if (totalW <= 0L) return emptyMap()
        val out = HashMap<Int, Long>(positive.size)
        var assigned = 0L
        val ordered = positive.entries.sortedByDescending { it.value }
        for ((uid, w) in ordered) {
            val share = bytes * w / totalW
            if (share > 0L) {
                out[uid] = share
                assigned += share
            }
        }
        val rem = bytes - assigned
        if (rem > 0L) {
            val top = ordered.first().key
            out[top] = (out[top] ?: 0L) + rem
        }
        return out
    }

    /**
     * Parse one proc-net table. Local/rem addresses are little-endian hex IP:PORT.
     * Field index 1 = local, 2 = rem, 7 = uid (see kernel net/ipv4/proc.c).
     */
    private fun readRows(path: String): List<SocketRow> {
        val lines = runCatching { File(path).readLines() }.getOrNull() ?: return emptyList()
        val out = ArrayList<SocketRow>(lines.size)
        for (i in 1 until lines.size) {
            val parts = lines[i].trim().split(Regex("\\s+"))
            if (parts.size < 8) continue
            val local = parts[1]
            val remote = parts[2]
            val uid = parts[7].toIntOrNull() ?: continue
            val lp = local.substringAfter(':', "").toIntOrNull(16) ?: continue
            val rp = remote.substringAfter(':', "").toIntOrNull(16) ?: 0
            out.add(SocketRow(uid, lp, rp))
        }
        return out
    }
}
