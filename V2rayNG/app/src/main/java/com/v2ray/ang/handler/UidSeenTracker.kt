package com.v2ray.ang.handler

import java.util.concurrent.ConcurrentHashMap

/**
 * UIDs recently resolved by [ConnectivityManager.getConnectionOwnerUid] / ProcessFinder.
 *
 * On Android 10+ SELinux blocks `/proc/net` for untrusted apps, so connection-count weights from
 * [ProcNetUidScanner] are often empty. Successful owner-UID lookups are the universal signal of
 * which apps are currently using the tunnel — used to attribute shortfall proxy bytes.
 */
object UidSeenTracker {
    private const val TTL_MS = 60_000L
    private val lastSeen = ConcurrentHashMap<Int, Long>()

    fun note(uid: Int) {
        if (uid < 10000) return
        lastSeen[uid] = System.currentTimeMillis()
    }

    /** UID → weight (1 per recently seen UID). */
    fun recentWeights(nowMs: Long = System.currentTimeMillis()): Map<Int, Int> {
        val out = HashMap<Int, Int>()
        for ((uid, ts) in lastSeen) {
            if (nowMs - ts <= TTL_MS) out[uid] = 1 else lastSeen.remove(uid, ts)
        }
        return out
    }

    fun clear() = lastSeen.clear()
}
