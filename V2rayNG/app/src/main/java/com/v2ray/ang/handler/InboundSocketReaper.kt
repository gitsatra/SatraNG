package com.v2ray.ang.handler

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.LogUtil
import java.io.File
import java.net.InetSocketAddress

/**
 * Closes client connections that apps opened to the core's local HTTP/SOCKS inbounds.
 *
 * Why: Android hands the VPN's HTTP proxy (127.0.0.1:<http port>) to every app, and browsers
 * such as Chrome use it. Those connections are loopback sockets inside this process, not packets
 * on the TUN, so recreating the TUN does not touch them, and stopping/restarting the core loop
 * leaves already-accepted sessions running on the old instance. A download that was running when
 * its app hit its limit therefore kept going (and its bytes landed on the old instance's counters,
 * which nobody reads any more) until SatraNG itself was killed.
 *
 * [reap] walks this process's own fds, finds accepted sockets whose local end is one of our
 * inbound ports, and shuts them down (shutdown, not close — the fd stays owned by the core, which
 * then sees EOF and cleans up). The app reconnects through the fresh routing, where an exhausted
 * limit now matches `block`.
 */
object InboundSocketReaper {

    /**
     * @param onlyUids shut down only connections owned by these app UIDs; null = every client.
     * @return number of connections shut down.
     */
    fun reap(context: Context, onlyUids: Set<Int>?): Int {
        if (onlyUids != null && onlyUids.isEmpty()) return 0
        val ports = runCatching {
            setOf(SettingsManager.getHttpPort(), SettingsManager.getSocksPort())
        }.getOrDefault(emptySet()).filter { it in 1..65535 }.toSet()
        if (ports.isEmpty()) return 0
        val cm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.getSystemService(ConnectivityManager::class.java)
        } else null
        val fds = File("/proc/self/fd").listFiles() ?: return 0
        var count = 0
        for (f in fds) {
            val n = f.name.toIntOrNull() ?: continue
            val link = runCatching { Os.readlink(f.path) }.getOrNull() ?: continue
            if (!link.startsWith("socket:")) continue
            val pfd = runCatching { ParcelFileDescriptor.fromFd(n) }.getOrNull() ?: continue
            try {
                val fd = pfd.fileDescriptor
                val local = runCatching { Os.getsockname(fd) }.getOrNull() as? InetSocketAddress ?: continue
                if (local.port !in ports || local.address?.isLoopbackAddress != true) continue
                // Listening sockets have no peer: getpeername throws, so they are never touched.
                val peer = runCatching { Os.getpeername(fd) }.getOrNull() as? InetSocketAddress ?: continue
                if (onlyUids != null) {
                    if (cm == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) continue
                    val uid = runCatching {
                        cm.getConnectionOwnerUid(OsConstants.IPPROTO_TCP, peer, local)
                    }.getOrDefault(-1)
                    if (uid !in onlyUids) continue
                }
                if (runCatching { Os.shutdown(fd, OsConstants.SHUT_RDWR) }.isSuccess) count++
            } finally {
                runCatching { pfd.close() }
            }
        }
        if (count > 0) LogUtil.w(AppConfig.TAG, "InboundSocketReaper: shut down $count inbound connection(s) uids=${onlyUids ?: "all"}")
        return count
    }
}
