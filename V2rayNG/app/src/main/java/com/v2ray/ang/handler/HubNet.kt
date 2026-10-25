package com.v2ray.ang.handler

import java.net.InetAddress
import java.util.concurrent.TimeUnit
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Hub access that survives a blocked CDN front: p.iivt.ir resolves to Cloudflare IPs that time
 * out on some Iranian networks, while the origin (same TLS certificate) is reachable directly.
 * OkHttp tries the resolved addresses in order, so the origin IP is appended as a last route;
 * TLS SNI and hostname verification still use p.iivt.ir.
 */
object HubNet {
    const val HUB_HOST = "p.iivt.ir"
    const val HUB_ORIGIN = "107.189.24.195"

    private val dns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val sys = runCatching { Dns.SYSTEM.lookup(hostname) }.getOrDefault(emptyList())
            if (!hostname.equals(HUB_HOST, true)) return sys
            val origin = InetAddress.getByAddress(HUB_HOST, byteArrayOf(107, 189.toByte(), 24, 195.toByte()))
            // Origin goes second: one try at the CDN, then straight to the server (a filtered CDN
            // with 4 addresses x timeout used to delay the update banner by ~30 s).
            val v4 = sys.filterIsInstance<java.net.Inet4Address>()
            return (v4.take(1) + origin + v4.drop(1)).distinct()
        }
    }

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(dns)
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .fastFallback(false)
            .build()
    }

    fun isHub(url: String): Boolean = runCatching { java.net.URI(url).host.equals(HUB_HOST, true) }.getOrDefault(false)

    fun getText(url: String): String? = runCatching {
        client.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (r.isSuccessful) r.body.string().takeIf { it.isNotBlank() } else null
        }
    }.getOrNull()
}
