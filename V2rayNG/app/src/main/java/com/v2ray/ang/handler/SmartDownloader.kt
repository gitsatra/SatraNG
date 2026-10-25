package com.v2ray.ang.handler

import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.core.CoreServiceManager
import com.v2ray.ang.util.LogUtil
import java.io.File
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * One downloader for app / geo / rules updates. Tries routes in order and silently falls through
 * when one fails or stalls:
 *   1. direct            2. the running config's local proxy
 *   3. Hub via its origin IP (HubNet)   4. GitHub (the caller's fallback URLs)
 * A route "stalls" when fewer than [STALL_BYTES] arrive in [STALL_MS]; it is abandoned and the
 * next one restarts the file from scratch, so a half-written temp file never becomes the result.
 */
object SmartDownloader {
    private const val STALL_MS = 12_000L
    private const val STALL_BYTES = 16 * 1024L

    data class Route(val url: String, val proxy: Proxy, val label: String)

    private fun localProxy(): Proxy? =
        if (CoreServiceManager.isRunning())
            Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", SettingsManager.getHttpPort()))
        else null

    /** Primary URL first; [fallbacks] are e.g. GitHub mirrors of the same file. */
    fun routes(primary: String, fallbacks: List<String> = emptyList()): List<Route> {
        val out = mutableListOf<Route>()
        val p = localProxy()
        val urls = (listOf(primary) + fallbacks).distinct()
        val first = urls.first()
        out += Route(first, Proxy.NO_PROXY, "direct")
        if (p != null) out += Route(first, p, "proxy")
        for (u in urls.drop(1)) {
            out += Route(u, Proxy.NO_PROXY, "direct")
            if (p != null) out += Route(u, p, "proxy")
        }
        return out
    }

    private fun client(proxy: Proxy, url: String): OkHttpClient {
        val base = if (HubNet.isHub(url) && proxy == Proxy.NO_PROXY) HubNet.client else OkHttpClient()
        return base.newBuilder().proxy(proxy)
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(STALL_MS, TimeUnit.MILLISECONDS)
            .followRedirects(true).build()
    }

    fun getText(primary: String, fallbacks: List<String> = emptyList(), headers: Map<String, String> = emptyMap()): String? {
        for (r in routes(primary, fallbacks)) {
            val t = runCatching {
                val rb = Request.Builder().url(r.url).header("User-Agent", ua())
                headers.forEach { (k, v) -> rb.header(k, v) }
                client(r.proxy, r.url).newCall(rb.build()).execute().use { res ->
                    if (res.isSuccessful) res.body.string().takeIf { it.isNotBlank() } else null
                }
            }.onFailure { LogUtil.w(AppConfig.TAG, "SmartDownloader text ${r.label} ${r.url}: ${it.message}") }.getOrNull()
            if (t != null) return t
        }
        return null
    }

    /**
     * Downloads to [dest] (via dest.part). [verify] may reject a finished file (size, tags...).
     * onProgress(done, total) – total may be null.
     */
    fun download(
        primary: String,
        dest: File,
        fallbacks: List<String> = emptyList(),
        verify: (File) -> Boolean = { it.length() > 0 },
        onProgress: (Long, Long?) -> Unit = { _, _ -> },
    ): File {
        val tmp = File(dest.absolutePath + ".part")
        var last: Exception? = null
        for (r in routes(primary, fallbacks)) {
            tmp.delete()
            try {
                val req = Request.Builder().url(r.url).header("User-Agent", ua()).build()
                client(r.proxy, r.url).newCall(req).execute().use { res ->
                    if (!res.isSuccessful) error("HTTP ${res.code}")
                    val total = res.body.contentLength().takeIf { it > 0 }
                    copyWatched(res.body.byteStream(), tmp, total, onProgress)
                    if (total != null && tmp.length() != total) error("short ${tmp.length()}/$total")
                }
                if (!verify(tmp)) error("verify failed")
                if (dest.exists()) dest.delete()
                if (!tmp.renameTo(dest)) { tmp.copyTo(dest, true); tmp.delete() }
                LogUtil.i(AppConfig.TAG, "SmartDownloader ok via ${r.label}: ${r.url}")
                return dest
            } catch (e: Exception) {
                last = e
                LogUtil.w(AppConfig.TAG, "SmartDownloader ${r.label} failed ${r.url}: ${e.message}")
            }
        }
        tmp.delete()
        throw last ?: IllegalStateException("no route")
    }

    private fun copyWatched(input: InputStream, out: File, total: Long?, onProgress: (Long, Long?) -> Unit) {
        input.use { ins ->
            out.outputStream().use { os ->
                val buf = ByteArray(64 * 1024)
                var done = 0L; var windowStart = System.currentTimeMillis(); var windowBytes = 0L
                while (true) {
                    val n = ins.read(buf)  // read timeout = STALL_MS catches total silence
                    if (n < 0) break
                    os.write(buf, 0, n); done += n; windowBytes += n
                    onProgress(done, total)
                    val now = System.currentTimeMillis()
                    if (now - windowStart >= STALL_MS) {
                        if (windowBytes < STALL_BYTES) error("stalled")
                        windowStart = now; windowBytes = 0
                    }
                }
            }
        }
    }

    private fun ua() = "${BuildConfig.APPLICATION_ID}/${BuildConfig.VERSION_NAME}"
}
