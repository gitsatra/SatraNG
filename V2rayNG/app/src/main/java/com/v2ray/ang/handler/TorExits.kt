package com.v2ray.ang.handler

import android.content.Context
import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.LogUtil
import java.io.File

/**
 * Tor exit addresses from the Tor Project's bulk exit list, cached on disk so detection works
 * offline. The list is only refreshed during a user-started security check, through the tunnel.
 */
object TorExits {
    const val LIST_URL = "https://check.torproject.org/torbulkexitlist"
    private const val MAX_AGE_MS = 24L * 3600_000L
    @Volatile private var mem: Set<String>? = null

    private fun file(ctx: Context) = File(ctx.filesDir, "tor_exits.txt")

    fun ageMs(ctx: Context): Long? = file(ctx).takeIf { it.exists() && it.length() > 0 }?.let { System.currentTimeMillis() - it.lastModified() }
    fun size(ctx: Context): Int = load(ctx).size

    fun load(ctx: Context): Set<String> = mem ?: runCatching {
        file(ctx).takeIf { it.exists() }?.readLines()?.map { it.trim() }?.filter { it.isNotEmpty() && !it.startsWith("#") }?.toHashSet()
    }.getOrNull().orEmpty().also { if (it.isNotEmpty()) mem = it }

    fun isExit(ctx: Context, ip: String?): Boolean = ip != null && ip in load(ctx)

    /** [fetch] gets the URL through the tunnel. Returns true when the cache is fresh afterwards. */
    fun refreshIfStale(ctx: Context, maxAgeMs: Long = MAX_AGE_MS, fetch: (String) -> String?): Boolean {
        val age = ageMs(ctx)
        if (age != null && age < maxAgeMs) return true
        val body = runCatching { fetch(LIST_URL) }.getOrNull()
        val ips = body?.lines()?.map { it.trim() }?.filter { it.matches(Regex("[0-9a-fA-F:.]{7,45}")) }.orEmpty()
        if (ips.size < 100) { LogUtil.w(AppConfig.TAG, "TorExits: refresh failed (${ips.size})"); return age != null }
        file(ctx).writeText(ips.joinToString("\n"))
        mem = ips.toHashSet()
        LogUtil.i(AppConfig.TAG, "TorExits: ${ips.size} exits cached")
        return true
    }
}
