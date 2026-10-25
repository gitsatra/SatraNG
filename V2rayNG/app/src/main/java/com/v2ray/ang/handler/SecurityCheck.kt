package com.v2ray.ang.handler

import com.google.gson.JsonParser
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.UrlContentRequest
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.util.HttpUtil
import com.v2ray.ang.util.LogUtil

/**
 * Connection Security Check: measures what an app inside the tunnel really exposes.
 * The app itself is excluded from its own VPN, so every "through the tunnel" request goes via
 * the core's local HTTP inbound; the "direct" request shows what the network sees without it.
 */
object SecurityCheck {

    enum class Status { Pass, Warn, Fail, Unknown }

    enum class Item { ExitIp, DnsLeak, Ipv6, WebRtc, Encryption, KillSwitch }

    data class Finding(val item: Item, val status: Status, val detail: String = "")

    data class Report(val connected: Boolean, val findings: List<Finding>, val score: Int, val fixes: List<Fix>, val facts: Facts? = null)

    enum class Fix { Connect, ChooseOtherServer, UseSecureConfig, DisableInsecure, EnableKillSwitch, UseRemoteDns, EnableIpv6Tunnel }

    /** Facts gathered by [run]; kept separate so the scoring is pure and unit-tested. */
    data class Facts(
        val connected: Boolean,
        val vpnMode: Boolean,
        val exitIp: String?,
        val exitCountry: String?,
        val directCountry: String?,
        val resolverIp: String?,
        val resolverCountry: String?,
        val ipv6Tunneled: Boolean,
        val ipv6Exit: String?,
        val configType: EConfigType?,
        val security: String?,
        val insecure: Boolean,
        val alwaysOn: Boolean?,
        val lockdown: Boolean?,
        /** uTLS fingerprint as configured (blank = the app default, chrome). */
        val fingerprint: String? = null,
        /** Resolver description as reported, e.g. "Netherlands - Cloudflare". */
        val resolverGeo: String? = null,
    )

    private val PLAIN_OK = setOf(EConfigType.WIREGUARD, EConfigType.HYSTERIA2, EConfigType.HYSTERIA, EConfigType.SHADOWSOCKS)
    private val COMPLEX = setOf(EConfigType.CUSTOM, EConfigType.POLICYGROUP, EConfigType.PROXYCHAIN)

    fun evaluate(f: Facts): Report {
        if (!f.connected) {
            return Report(false, Item.entries.map { Finding(it, Status.Unknown) }, 0, listOf(Fix.Connect))
        }
        val out = mutableListOf<Finding>()
        val fixes = linkedSetOf<Fix>()

        // Exit IP: traffic must leave somewhere other than the user's own network.
        val exit = when {
            f.exitIp.isNullOrBlank() -> Status.Fail.also { fixes += Fix.ChooseOtherServer }
            f.directCountry != null && f.exitCountry != null && f.exitCountry.equals(f.directCountry, true) -> Status.Warn
            else -> Status.Pass
        }
        out += Finding(Item.ExitIp, exit, listOfNotNull(f.exitIp, f.exitCountry).joinToString(" · "))

        // DNS: the resolver should sit with the exit, never in the user's own country.
        val dns = when {
            f.resolverIp.isNullOrBlank() -> Status.Unknown
            f.resolverCountry != null && f.directCountry != null &&
                f.resolverCountry.equals(f.directCountry, true) &&
                !f.directCountry.equals(f.exitCountry, true) -> Status.Fail.also { fixes += Fix.UseRemoteDns }
            // Without the direct comparison: a resolver away from the exit is worth a look.
            f.directCountry == null && f.resolverCountry != null && f.exitCountry != null &&
                !f.resolverCountry.equals(f.exitCountry, true) -> Status.Warn
            else -> Status.Pass
        }
        out += Finding(Item.DnsLeak, dns, listOfNotNull(f.resolverIp, f.resolverCountry).joinToString(" · "))

        // IPv6: the VPN claims ::/0, so IPv6 either rides the tunnel or is dropped; never leaks.
        val v6 = when {
            !f.vpnMode -> Status.Warn
            f.ipv6Tunneled -> Status.Pass
            else -> Status.Fail.also { fixes += Fix.EnableIpv6Tunnel }
        }
        out += Finding(Item.Ipv6, v6, f.ipv6Exit ?: "")

        // WebRTC/STUN is UDP: in VPN mode all UDP enters the tunnel (full routes), so a STUN
        // server only ever sees the exit address. Proxy-only mode cannot cover other apps' UDP.
        out += Finding(Item.WebRtc, if (f.vpnMode) Status.Pass else Status.Warn)

        // Encryption of the config itself.
        val sec = f.security?.lowercase().orEmpty()
        val enc = when {
            f.configType == null || f.configType in COMPLEX -> Status.Unknown
            f.insecure -> Status.Fail.also { fixes += Fix.DisableInsecure }
            sec == "reality" || sec == "tls" -> Status.Pass
            f.configType in PLAIN_OK -> Status.Pass // encrypted by the protocol itself
            f.configType == EConfigType.VMESS -> Status.Warn.also { fixes += Fix.UseSecureConfig }
            else -> Status.Fail.also { fixes += Fix.UseSecureConfig } // vless/trojan/socks/http without TLS
        }
        out += Finding(Item.Encryption, enc, listOfNotNull(f.configType?.name, sec.ifBlank { null }?.uppercase()).joinToString(" · "))

        val ks = when {
            !f.vpnMode -> Status.Warn
            f.alwaysOn == true && f.lockdown == true -> Status.Pass
            f.alwaysOn == null -> Status.Unknown
            else -> Status.Warn
        }
        out += Finding(Item.KillSwitch, ks)

        return Report(true, out, score(out), fixes.toList(), f)
    }

    private val weight = mapOf(
        // Kill switch is optional: shown as info, never scored.
        Item.ExitIp to 25, Item.DnsLeak to 30, Item.Encryption to 25,
        Item.Ipv6 to 10, Item.WebRtc to 10, Item.KillSwitch to 0,
    )

    fun maxPoints(item: Item): Int = weight[item] ?: 0

    fun points(f: Finding): Int = when (f.status) {
        Status.Pass -> maxPoints(f.item)
        Status.Warn, Status.Unknown -> maxPoints(f.item) / 2
        Status.Fail -> 0
    }

    /** Fixes the app can apply itself (settings / this config only); the rest open a place to act. */
    fun isOneTap(fix: Fix): Boolean = fix == Fix.UseRemoteDns || fix == Fix.DisableInsecure

    /** Order in which [run] reports items done. */
    val ORDER = listOf(Item.ExitIp, Item.DnsLeak, Item.Ipv6, Item.WebRtc, Item.Encryption, Item.KillSwitch)

    /** 0..100. Pass = full points, Warn/Unknown = half, Fail = none. */
    fun score(findings: List<Finding>): Int = findings.sumOf { f ->
        val w = weight[f.item] ?: 0
        when (f.status) {
            Status.Pass -> w
            Status.Warn, Status.Unknown -> w / 2
            Status.Fail -> 0
        }
    }.coerceIn(0, 100)

    // ---------- measurement (IO thread) ----------

    /** Exit (ip, country) seen through the running tunnel, or null. IO thread. */
    fun liveExit(): Pair<String, String?>? {
        val port = SettingsManager.getHttpPort()
        val user = SettingsManager.getSocksUsername()
        val pass = SettingsManager.getSocksPassword()
        for (url in listOf(AppConfig.IP_API_URL, "http://ip-api.com/json/?fields=countryCode,query,lat,lon", "https://api.ip.sb/geoip")) {
            val body = try {
                HttpUtil.getUrlContent(UrlContentRequest(url = url, timeout = 8000, httpPort = port, proxyUsername = user, proxyPassword = pass))
            } catch (e: Exception) {
                null
            }
            val r = parseIpCountry(body)
            val ip = r?.first
            if (!ip.isNullOrBlank()) { lastExitLatLon = parseLatLon(body); return ip to r.second }
        }
        return null
    }

    /** [compareDirect]: also ask once outside the tunnel (manual runs only; reveals the real IP to ip-api.com). */
    /** Last connected result. The check never runs by itself (battery); only on page open or tap. */
    @Volatile var last: Report? = null
    @Volatile var lastAtMs = 0L

    fun run(connected: Boolean, compareDirect: Boolean = true, onDone: (Item) -> Unit = {}): Report =
        runInner(connected, compareDirect, onDone).also { if (it.connected) { last = it; lastAtMs = System.currentTimeMillis() } }

    private fun runInner(connected: Boolean, compareDirect: Boolean, onDone: (Item) -> Unit): Report {
        val vpnMode = SettingsManager.isVpnMode()
        val guid = MmkvManager.getSelectServer()
        val profile = guid?.let { MmkvManager.decodeServerConfig(it) }
        if (!connected) return evaluate(emptyFacts(vpnMode))
        val port = SettingsManager.getHttpPort()
        val user = SettingsManager.getSocksUsername()
        val pass = SettingsManager.getSocksPassword()
        fun get(url: String, viaTunnel: Boolean): String? = try {
            HttpUtil.getUrlContent(
                UrlContentRequest(
                    url = url, timeout = 8000,
                    httpPort = if (viaTunnel) port else 0,
                    proxyUsername = if (viaTunnel) user else null,
                    proxyPassword = if (viaTunnel) pass else null,
                ),
            )
        } catch (e: Exception) {
            null
        }

        val exit = parseIpCountry(get(AppConfig.IP_API_URL, true) ?: get("http://ip-api.com/json/?fields=countryCode,query", true))
        onDone(Item.ExitIp)
        val direct = if (compareDirect) parseIpCountry(get("http://ip-api.com/json/?fields=countryCode,query", false)) else null
        // edns.ip-api.com answers with the resolver that looked its random name up.
        val dnsBody = get("http://${System.nanoTime()}.edns.ip-api.com/json", true)
        val dns = parseResolver(dnsBody)
        onDone(Item.DnsLeak)
        val v6 = get("https://api6.ipify.org", true)?.trim()?.takeIf { it.contains(':') }
        onDone(Item.Ipv6)
        LogUtil.i(AppConfig.TAG, "SecurityCheck: exit=${exit?.second} dns=${dns?.second} v6=${v6 != null}")
        return evaluate(
            Facts(
                connected = true, vpnMode = vpnMode,
                exitIp = exit?.first, exitCountry = exit?.second, directCountry = direct?.second,
                resolverIp = dns?.first, resolverCountry = dns?.second,
                // CoreVpnService always routes ::/0 into the tunnel (see configureNetwork).
                ipv6Tunneled = vpnMode, ipv6Exit = v6,
                configType = profile?.configType, security = profile?.security,
                insecure = profile?.insecure == true && profile.pinnedCA256.isNullOrEmpty(),
                alwaysOn = MmkvManager.decodeSettingsString(PREF_ALWAYS_ON)?.toBooleanStrictOrNull(),
                lockdown = MmkvManager.decodeSettingsString(PREF_LOCKDOWN)?.toBooleanStrictOrNull(),
                fingerprint = profile?.fingerPrint,
                resolverGeo = resolverGeo(dnsBody),
            ),
        ).also { listOf(Item.WebRtc, Item.Encryption, Item.KillSwitch).forEach(onDone) }
    }

    private fun emptyFacts(vpnMode: Boolean) = Facts(false, vpnMode, null, null, null, null, null, false, null, null, null, false, null, null)

    /** Set by CoreVpnService once the tunnel is up (VpnService.isAlwaysOn / isLockdownEnabled). */
    const val PREF_ALWAYS_ON = "pref_satra_vpn_always_on"
    const val PREF_LOCKDOWN = "pref_satra_vpn_lockdown"

    /** (ip, 2-letter country) from api.ip.sb / ip-api.com style JSON. */
    /** (lat, lon) of the last [liveExit] answer, when the service reports it (ip.sb, ip-api). */
    @Volatile var lastExitLatLon: Pair<Double, Double>? = null

    internal fun parseLatLon(body: String?): Pair<Double, Double>? = try {
        val o = JsonParser.parseString(body).asJsonObject
        fun d(vararg k: String) = k.firstNotNullOfOrNull { o.get(it)?.takeIf { e -> e.isJsonPrimitive }?.asString?.toDoubleOrNull() }
        val la = d("latitude", "lat"); val lo = d("longitude", "lon")
        if (la != null && lo != null && la in -90.0..90.0 && lo in -180.0..180.0) la to lo else null
    } catch (e: Exception) { null }

    internal fun parseIpCountry(body: String?): Pair<String, String?>? {
        if (body.isNullOrBlank()) return null
        return try {
            val o = JsonParser.parseString(body).asJsonObject
            val ip = listOf("ip", "query").firstNotNullOfOrNull { k -> o.get(k)?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() } }
                ?: return null
            val cc = listOf("country_code", "countryCode", "country").firstNotNullOfOrNull { k ->
                o.get(k)?.takeIf { it.isJsonPrimitive }?.asString?.trim()?.takeIf { it.length == 2 }?.uppercase()
            }
            ip to cc
        } catch (e: Exception) {
            null
        }
    }

    /** (resolver ip, resolver country name) from edns.ip-api.com: {"dns":{"geo":"Netherlands - Cloudflare","ip":"…"}}. */
    internal fun parseResolver(body: String?): Pair<String, String?>? {
        if (body.isNullOrBlank()) return null
        return try {
            val dns = JsonParser.parseString(body).asJsonObject.getAsJsonObject("dns") ?: return null
            val ip = dns.get("ip")?.asString?.takeIf { it.isNotBlank() } ?: return null
            val geo = dns.get("geo")?.asString.orEmpty()
            ip to geo.substringBefore(" - ").trim().ifBlank { null }?.let { countryNameToCode(it) ?: it }
        } catch (e: Exception) {
            null
        }
    }

    internal fun resolverGeo(body: String?): String? = try {
        JsonParser.parseString(body ?: return null).asJsonObject.getAsJsonObject("dns")?.get("geo")?.asString?.takeIf { it.isNotBlank() }
    } catch (e: Exception) {
        null
    }

    /** One-tap fixes. Returns true when applied (a reconnect makes it take effect). */
    fun applyFix(fix: Fix): Boolean = try {
        when (fix) {
            Fix.UseRemoteDns -> {
                MmkvManager.encodeSettings(AppConfig.PREF_REMOTE_DNS, DOH_DEFAULT); true
            }
            Fix.DisableInsecure -> {
                val guid = MmkvManager.getSelectServer()
                val p = guid?.let { MmkvManager.decodeServerConfig(it) }
                if (guid != null && p != null) { p.insecure = false; MmkvManager.encodeServerConfig(guid, p); true } else false
            }
            else -> false
        }
    } catch (e: Exception) {
        false
    }

    const val DOH_DEFAULT = "https://1.1.1.1/dns-query"

    internal fun countryNameToCode(name: String): String? =
        java.util.Locale.getISOCountries().firstOrNull { java.util.Locale("", it).getDisplayCountry(java.util.Locale.ENGLISH).equals(name, true) }
}
