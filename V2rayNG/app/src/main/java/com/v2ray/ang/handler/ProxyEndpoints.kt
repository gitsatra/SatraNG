package com.v2ray.ang.handler

import com.v2ray.ang.AppConfig

/** A local proxy address other apps can use in Proxy-only mode. */
data class ProxyEndpoint(val kind: Kind, val host: String, val port: Int) {
    enum class Kind { Socks, Http, Mixed }
    val address: String get() = "$host:$port"
}

object ProxyEndpoints {
    const val LOOPBACK = "127.0.0.1"

    /** One "SOCKS5 + HTTP" entry when both share a port (Xray mixed inbound), else one of each. */
    fun build(host: String, socksPort: Int, httpPort: Int): List<ProxyEndpoint> =
        if (socksPort == httpPort) listOf(ProxyEndpoint(ProxyEndpoint.Kind.Mixed, host, socksPort))
        else listOf(ProxyEndpoint(ProxyEndpoint.Kind.Socks, host, socksPort), ProxyEndpoint(ProxyEndpoint.Kind.Http, host, httpPort))

    fun current(): List<ProxyEndpoint> = runCatching {
        build(LOOPBACK, SettingsManager.getSocksPort(), SettingsManager.getHttpPort())
    }.getOrDefault(emptyList())

    fun isProxyOnlyMode(): Boolean =
        runCatching { (MmkvManager.decodeSettingsString(AppConfig.PREF_MODE, AppConfig.VPN) ?: AppConfig.VPN) != AppConfig.VPN }.getOrDefault(false)

    fun sharedOnLan(): Boolean = runCatching { MmkvManager.decodeSettingsBool(AppConfig.PREF_PROXY_SHARING, false) }.getOrDefault(false)

    fun username(): String? = runCatching { SettingsManager.getSocksUsername() }.getOrNull()
}
