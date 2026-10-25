package com.v2ray.ang.handler

import android.content.Context
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.v2ray.ang.AppConfig
import com.v2ray.ang.core.CoreConfigManager
import com.v2ray.ang.core.CoreNativeManager
import com.v2ray.ang.dto.IPAPIInfo
import com.v2ray.ang.dto.UrlContentRequest
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.util.HttpUtil
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.LogUtil
import libv2ray.CoreCallbackHandler
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.Semaphore

/**
 * Finds the country a config really exits in by sending one HTTPS request through it.
 *
 * A private core instance (own local HTTP inbound on a free loopback port, only this config's
 * outbounds) is started, queried, and torn down. Works for CDN-fronted and relayed configs because
 * it measures the actual exit rather than the address in the link. Intended for the `:tasks`
 * process (it is excluded from the VPN, so the probe never loops through a running tunnel).
 */
object ExitIpProbe {

    /** Keep at most four private cores alive at once. */
    private val gate = Semaphore(4)

    /** IP-echo services tried in order (ipapi.co dropped: it answers 403 to most VPN exits). */
    internal val endpoints = listOf(
        AppConfig.IP_API_URL,
        "http://ip-api.com/json/?fields=status,countryCode,query",
        "https://api.country.is/",
        "https://ipwho.is/",
    )

    private val runner = java.util.concurrent.Executors.newCachedThreadPool { r ->
        Thread(r, "exit-probe").apply { isDaemon = true }
    }

    /**
     * [probe] with a hard deadline, so a stuck core or endpoint can never hang a test batch or the
     * background locator. On timeout the probe thread is interrupted and abandoned.
     */
    fun probeBounded(context: Context, guid: String, timeoutMs: Long = 50_000L): GeoIpResolver.GeoResult {
        val future = runner.submit<GeoIpResolver.GeoResult> { probe(context, guid) }
        return try {
            future.get(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
        } catch (e: java.util.concurrent.TimeoutException) {
            future.cancel(true)
            LogUtil.w(AppConfig.TAG, "ExitIpProbe: $guid timed out")
            MmkvManager.markServerGeoAttempt(guid)
            GeoIpResolver.GeoResult.UNKNOWN
        } catch (e: InterruptedException) {
            future.cancel(true)
            Thread.currentThread().interrupt()
            GeoIpResolver.GeoResult.UNKNOWN
        } catch (e: Exception) {
            GeoIpResolver.GeoResult.UNKNOWN
        }
    }

    /** @return the stored result, or [GeoIpResolver.GeoResult.UNKNOWN] if the probe could not run. */
    fun probe(context: Context, guid: String): GeoIpResolver.GeoResult {
        val found = throughConfig(context, guid) { port ->
            // Warm the fresh core up (first dial + TLS) on the same URL ping uses.
            val warm = HttpUtil.getUrlContent(UrlContentRequest(url = AppConfig.DELAY_TEST_URL, timeout = 10_000, httpPort = port))
            LogUtil.i(AppConfig.TAG, "ExitIpProbe: $guid warm-up ${if (warm != null) "ok" else "failed"}")
            for (url in endpoints) {
                if (Thread.currentThread().isInterrupted) break
                val body = HttpUtil.getUrlContent(UrlContentRequest(url = url, timeout = 10_000, httpPort = port))
                if (body == null) {
                    LogUtil.w(AppConfig.TAG, "ExitIpProbe: $guid no answer from $url")
                    continue
                }
                val (ip, country) = parseExitBody(body) ?: continue
                val result = GeoIpResolver.storeExit(guid, ip, country)
                if (result.exitVerified) {
                    LogUtil.i(AppConfig.TAG, "ExitIpProbe: $guid exit ${result.countryCode} via $url")
                    return@throughConfig result
                }
                LogUtil.w(AppConfig.TAG, "ExitIpProbe: $guid no country from $url")
            }
            null
        }
        if (found == null) MmkvManager.markServerGeoAttempt(guid)
        return found ?: GeoIpResolver.GeoResult.UNKNOWN
    }

    private val speedUrls = listOf(
        "https://speed.cloudflare.com/__down?bytes=50000000",
        "https://cachefly.cachefly.net/100mb.test",
        "http://speedtest.tele2.net/100MB.zip",
    )

    /**
     * Download speed through [guid]: streams up to [SPEED_MAX_BYTES] for at most [SPEED_MAX_MS]
     * and returns bytes per second measured from the first byte (connection setup excluded).
     * Returns -1 when nothing could be downloaded. Stores the result.
     */
    fun speed(context: Context, guid: String, onProgress: (bps: Long, fraction: Float) -> Unit = { _, _ -> }): Long {
        val bps = throughConfig(context, guid) { port ->
            for (url in speedUrls) {
                if (Thread.currentThread().isInterrupted) break
                val r = runCatching { download(url, port, onProgress) }.onFailure { LogUtil.w(AppConfig.TAG, "speed $url: $it") }.getOrNull()
                if (r != null && r > 0L) return@throughConfig r
            }
            null
        } ?: -1L
        MmkvManager.encodeServerSpeed(guid, bps)
        return bps
    }

    private fun download(url: String, port: Int, onProgress: (Long, Float) -> Unit = { _, _ -> }): Long? {
        val proxy = java.net.Proxy(java.net.Proxy.Type.HTTP, java.net.InetSocketAddress(AppConfig.LOOPBACK, port))
        val conn = (java.net.URL(url).openConnection(proxy) as java.net.HttpURLConnection).apply {
            connectTimeout = 6000; readTimeout = 6000; useCaches = false
            setRequestProperty("Accept-Encoding", "identity")
        }
        try {
            if (conn.responseCode !in 200..299) { LogUtil.w(AppConfig.TAG, "speed $url: HTTP ${conn.responseCode}"); return null }
            val buf = ByteArray(64 * 1024)
            conn.inputStream.use { input ->
                var first = 0L
                var total = 0L
                var lastTick = 0L
                var lastBytes = 0L
                var smooth = 0.0
                val deadline = System.currentTimeMillis() + SPEED_MAX_MS
                while (total < SPEED_MAX_BYTES && System.currentTimeMillis() < deadline) {
                    if (Thread.currentThread().isInterrupted) return null
                    val n = input.read(buf)
                    if (n < 0) break
                    if (first == 0L) first = System.nanoTime()
                    total += n
                    val now = System.nanoTime()
                    if (lastTick == 0L) { lastTick = now; lastBytes = total }
                    if (now - lastTick >= 200_000_000L) {
                        val inst = (total - lastBytes) / ((now - lastTick) / 1e9)
                        smooth = if (smooth <= 0.0) inst else smooth * 0.7 + inst * 0.3
                        lastTick = now; lastBytes = total
                        val s = (now - first) / 1e9
                        val frac = maxOf(total.toFloat() / SPEED_MAX_BYTES, (s * 1000 / SPEED_MAX_MS).toFloat()).coerceAtMost(1f)
                        onProgress(smooth.toLong(), frac)
                    }
                }
                val secs = (System.nanoTime() - first) / 1e9
                if (first == 0L || total < 64 * 1024 || secs <= 0.05) return null
                return (total / secs).toLong()
            }
        } finally {
            conn.disconnect()
        }
    }

    const val SPEED_MAX_BYTES = 200_000_000L
    const val SPEED_MAX_MS = 8_000L

    /** Starts a private core for [guid] with a loopback HTTP inbound, runs [block], tears it down. */
    internal fun <T> throughConfig(context: Context, guid: String, block: (port: Int) -> T?): T? {
        val profile = MmkvManager.decodeServerConfig(guid) ?: return null
        if (profile.configType == EConfigType.CUSTOM) return null
        if (!gate.tryAcquire(20, java.util.concurrent.TimeUnit.SECONDS)) return null
        var controller: libv2ray.CoreController? = null
        try {
            val built = CoreConfigManager.getV2rayConfig4Speedtest(context, guid)
            if (!built.status) return null
            val port = freeLoopbackPort() ?: return null
            val json = withProbeInbound(built.content, port) ?: return null
            controller = CoreNativeManager.newCoreController(object : CoreCallbackHandler {
                override fun startup(): Long = 0
                override fun shutdown(): Long = 0
                override fun onEmitStatus(l: Long, s: String?): Long = 0
            })
            controller.startLoop(json, 0)
            return block(port)
        } catch (e: Throwable) {
            LogUtil.w(AppConfig.TAG, "ExitIpProbe: $guid failed: ${e.message}")
            return null
        } finally {
            runCatching { controller?.stopLoop() }
            gate.release()
        }
    }

    private fun freeLoopbackPort(): Int? = try {
        ServerSocket(0, 1, InetAddress.getByName(AppConfig.LOOPBACK)).use { it.localPort }
    } catch (_: Exception) {
        null
    }

    /** Adds one loopback HTTP inbound and routes everything to the config's primary outbound. */
    internal fun withProbeInbound(content: String, port: Int): String? {
        val root = JsonUtil.parseString(content) ?: return null
        val inbound = JsonObject().apply {
            addProperty("tag", "probe-in")
            addProperty("listen", AppConfig.LOOPBACK)
            addProperty("port", port)
            addProperty("protocol", "http")
            add("settings", JsonObject())
        }
        root.add("inbounds", JsonArray().apply { add(inbound) })
        return JsonUtil.toJson(root)
    }
}

/** (exit ip, 2-letter country) from any endpoint's JSON, or null when the body isn't usable. */
internal fun parseExitBody(body: String): Pair<String?, String?>? {
    val info = JsonUtil.fromJsonSafe(body, IPAPIInfo::class.java) ?: return null
    val ip = listOf(info.ip, info.clientIp, info.ip_addr, info.query).firstOrNull { !it.isNullOrBlank() }
    val country = listOf(info.country_code, info.countryCode, info.country, info.location?.country_code)
        .firstOrNull { !it.isNullOrBlank() && it.trim().length == 2 }?.trim()?.uppercase()
    return ip to country
}
