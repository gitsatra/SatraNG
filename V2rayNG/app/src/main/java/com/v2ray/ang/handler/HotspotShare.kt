package com.v2ray.ang.handler

import com.v2ray.ang.AppConfig
import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.SecureRandom

/**
 * "Share VPN over hotspot": the core's SOCKS/HTTP inbounds listen on all interfaces (no root),
 * protected by a username/password by default. Turns itself off when the connection stops.
 */
object HotspotShare {
    const val PREF_ON = "pref_satra_hotspot_share"
    /** True when we generated the credentials (removed again on turn-off). */
    const val PREF_GEN = "pref_satra_hotspot_gen_creds"
    const val USER = "satra"

    fun isOn(): Boolean = MmkvManager.decodeSettingsBool(PREF_ON, false)

    fun enable(auth: Boolean = true) {
        MmkvManager.encodeSettings(PREF_ON, true)
        MmkvManager.encodeSettings(AppConfig.PREF_PROXY_SHARING, true)
        if (auth) {
            if (SettingsManager.getSocksUsername() == null || SettingsManager.getSocksPassword() == null) {
                MmkvManager.encodeSettings(AppConfig.PREF_SOCKS_USERNAME, USER)
                MmkvManager.encodeSettings(AppConfig.PREF_SOCKS_PASSWORD, randomPassword())
                MmkvManager.encodeSettings(PREF_GEN, true)
            }
        } else {
            clearGenerated()
        }
        SettingsChangeManager.makeRestartService()
    }

    fun disable() {
        if (!isOn()) return
        MmkvManager.encodeSettings(PREF_ON, false)
        MmkvManager.encodeSettings(AppConfig.PREF_PROXY_SHARING, false)
        clearGenerated()
        SettingsChangeManager.makeRestartService()
    }

    private fun clearGenerated() {
        if (MmkvManager.decodeSettingsBool(PREF_GEN, false)) {
            MmkvManager.encodeSettings(AppConfig.PREF_SOCKS_USERNAME, null as String?)
            MmkvManager.encodeSettings(AppConfig.PREF_SOCKS_PASSWORD, null as String?)
            MmkvManager.encodeSettings(PREF_GEN, false)
        }
    }

    /** Called when the core stops: sharing never outlives the connection. */
    fun onDisconnect() = runCatching { disable() }

    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789"
    fun randomPassword(len: Int = 12): String {
        val r = SecureRandom()
        return String(CharArray(len) { ALPHABET[r.nextInt(ALPHABET.length)] })
    }

    /** Best LAN address: hotspot first (ap/swlan/softap), then Wi-Fi, then any private IPv4. Pure. */
    fun pickLanIp(candidates: List<Pair<String, String>>): String? {
        fun private(ip: String) = ip.startsWith("192.168.") || ip.startsWith("10.") ||
            Regex("^172\\.(1[6-9]|2\\d|3[01])\\.").containsMatchIn(ip)
        val ok = candidates.filter { (n, ip) -> private(ip) && !n.startsWith("tun") && !n.startsWith("rmnet") }
        val rank = listOf("ap", "swlan", "softap", "wlan1", "wlan", "eth", "rndis", "usb")
        return ok.minByOrNull { (n, _) -> rank.indexOfFirst { n.startsWith(it) }.let { if (it < 0) rank.size else it } }?.second
    }

    fun lanIp(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }.flatMap { ni ->
            ni.inetAddresses.toList().filterIsInstance<Inet4Address>().map { ni.name to (it.hostAddress ?: "") }
        }.let { pickLanIp(it) }
    }.getOrNull()

    /** Share link for the QR code, e.g. socks5://satra:pw@192.168.43.1:10808 (credentials URL-encoded). */
    fun shareUri(scheme: String, ip: String, port: Int, user: String?, pass: String?): String {
        fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")
        val auth = if (!user.isNullOrEmpty() && !pass.isNullOrEmpty()) "${enc(user)}:${enc(pass)}@" else ""
        return "$scheme://$auth$ip:$port"
    }
}
