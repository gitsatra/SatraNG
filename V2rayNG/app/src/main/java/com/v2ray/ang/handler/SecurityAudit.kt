package com.v2ray.ang.handler

import android.app.KeyguardManager
import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.provider.Settings
import com.google.gson.JsonParser
import com.v2ray.ang.AppConfig
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.util.LogUtil
import java.net.InetSocketAddress
import java.net.Proxy
import java.security.KeyStore
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.launch

/**
 * Security check v2: real measurements grouped by category. Runs only when the user starts it.
 * Every check reports a status, the points it earned, a measured detail and (via strings named
 * sa_<id>_title/_ok/_bad/_fix/_how) why it passed or failed, how to fix it and how it is measured.
 * Items with maxPoints 0 (kill switch, Tor, Private DNS) are information only and never scored.
 */
object SecurityAudit {
    enum class Cat { Connection, Leaks, Device, Tor }
    enum class St { Pass, Warn, Fail, Info, Skip }

    data class Check(val id: String, val cat: Cat, val max: Int)
    data class Result(val check: Check, val st: St, val detail: String = "") {
        val points: Int get() = when (st) { St.Pass -> check.max; St.Warn -> check.max / 2; else -> 0 }
        val scored: Boolean get() = check.max > 0 && st != St.Skip && st != St.Info
    }

    val CHECKS = listOf(
        Check("exit", Cat.Connection, 15),
        Check("encrypt", Cat.Connection, 15),
        Check("certverify", Cat.Connection, 10),
        Check("fingerprint", Cat.Connection, 5),
        Check("mitm", Cat.Connection, 10),
        Check("latency", Cat.Connection, 5),
        Check("dnsleak", Cat.Leaks, 15),
        Check("dnsexit", Cat.Leaks, 5),
        Check("dnsenc", Cat.Leaks, 5),
        Check("ipv6", Cat.Leaks, 5),
        Check("udp", Cat.Leaks, 5),
        Check("lan", Cat.Leaks, 5),
        Check("killswitch", Cat.Device, 0),
        Check("lock", Cat.Device, 5),
        Check("adb", Cat.Device, 5),
        Check("patch", Cat.Device, 5),
        Check("userca", Cat.Device, 5),
        Check("privdns", Cat.Device, 0),
        Check("tor_api", Cat.Tor, 0),
        Check("tor_list", Cat.Tor, 0),
        Check("tor_relay", Cat.Tor, 0),
        Check("tor_cache", Cat.Tor, 0),
    )

    /** 0..100 over the scored items that could be measured. */
    fun score(rs: Collection<Result>): Int {
        val s = rs.filter { it.scored }
        val max = s.sumOf { it.check.max }
        return if (max == 0) 0 else (s.sumOf { it.points } * 100 / max)
    }

    @Volatile var lastScore: Int? = null

    /** Live state survives rotation / leaving the page; the run is user-started only. */
    val live = kotlinx.coroutines.flow.MutableStateFlow<Map<String, Result>>(emptyMap())
    val running = kotlinx.coroutines.flow.MutableStateFlow(false)
    val started = kotlinx.coroutines.flow.MutableStateFlow(false)
    private val bg = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)

    fun start(ctx: Context) {
        if (running.value) return
        running.value = true; started.value = true; live.value = emptyMap(); torExit = false
        bg.launch {
            try {
                run(ctx.applicationContext, compareDirect = true) { r -> live.value = live.value + (r.check.id to r); Thread.sleep(140) }
            } catch (e: Exception) { LogUtil.e(AppConfig.TAG, "SecurityAudit failed", e) }
            finally { running.value = false }
        }
    }
    @Volatile var torExit: Boolean = false

    private fun tunnel(timeoutMs: Long = 8000): OkHttpClient {
        val user = SettingsManager.getSocksUsername(); val pass = SettingsManager.getSocksPassword()
        return OkHttpClient.Builder()
            .proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", SettingsManager.getHttpPort())))
            .apply { if (!user.isNullOrBlank() && !pass.isNullOrBlank()) proxyAuthenticator { _, r -> r.request.newBuilder().header("Proxy-Authorization", Credentials.basic(user, pass)).build() } }
            .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS).readTimeout(timeoutMs, TimeUnit.MILLISECONDS).build()
    }

    private fun get(url: String, direct: Boolean = false, timeoutMs: Long = 8000): String {
        val c = if (direct) OkHttpClient.Builder().connectTimeout(timeoutMs, TimeUnit.MILLISECONDS).readTimeout(timeoutMs, TimeUnit.MILLISECONDS).build() else tunnel(timeoutMs)
        c.newCall(Request.Builder().url(url).header("User-Agent", "SatraNG").build()).execute().use { r ->
            if (!r.isSuccessful) error("HTTP ${r.code}")
            return r.body.string()
        }
    }
    fun tunnelGet(url: String): String? = runCatching { get(url) }.getOrNull()
    private fun tryGet(url: String, direct: Boolean = false) = runCatching { get(url, direct) }.getOrNull()

    /** Runs every check in order; [emit] is called on the caller's thread as each one finishes. IO thread. */
    fun run(ctx: Context, compareDirect: Boolean, emit: (Result) -> Unit): List<Result> {
        val out = mutableListOf<Result>()
        fun r(id: String, st: St, d: String = "") { val x = Result(CHECKS.first { it.id == id }, st, d); out += x; emit(x) }
        val vpn = SettingsManager.isVpnMode()
        val profile = MmkvManager.getSelectServer()?.let { MmkvManager.decodeServerConfig(it) }

        // ---- Connection
        val exit = SecurityCheck.parseIpCountry(tryGet(AppConfig.IP_API_URL) ?: tryGet("http://ip-api.com/json/?fields=countryCode,query"))
        val direct = if (compareDirect) SecurityCheck.parseIpCountry(tryGet("http://ip-api.com/json/?fields=countryCode,query", direct = true)) else null
        r("exit", when {
            exit == null -> St.Fail
            direct?.second != null && direct.second == exit.second -> St.Warn
            else -> St.Pass
        }, listOfNotNull(exit?.first, exit?.second).joinToString(" · "))

        val type = profile?.configType
        val sec = profile?.security?.lowercase().orEmpty()
        val plainOk = setOf(EConfigType.WIREGUARD, EConfigType.HYSTERIA2, EConfigType.HYSTERIA, EConfigType.SHADOWSOCKS)
        r("encrypt", when {
            type == null || type in setOf(EConfigType.CUSTOM, EConfigType.POLICYGROUP, EConfigType.PROXYCHAIN) -> St.Skip
            sec == "reality" || sec == "tls" || type in plainOk -> St.Pass
            type == EConfigType.VMESS -> St.Warn
            else -> St.Fail
        }, listOfNotNull(type?.name, sec.ifBlank { null }?.uppercase()).joinToString(" · "))

        val insecure = profile?.insecure == true && profile.pinnedCA256.isNullOrEmpty()
        r("certverify", when {
            profile == null || sec != "tls" -> if (sec == "reality") St.Pass else St.Skip
            insecure -> St.Fail
            else -> St.Pass
        }, if (sec == "reality") "REALITY" else if (insecure) "allowInsecure" else "")

        val fp = profile?.fingerPrint.orEmpty()
        r("fingerprint", when {
            sec != "tls" && sec != "reality" -> St.Skip
            fp.isBlank() || fp in setOf("chrome", "firefox", "safari", "ios", "edge", "random", "randomized") -> St.Pass
            else -> St.Warn
        }, fp.ifBlank { "chrome" })

        val mitm = try {
            val t = get("https://www.cloudflare.com/cdn-cgi/trace")
            val tls = Regex("tls=(\\S+)").find(t)?.groupValues?.get(1).orEmpty()
            Result(CHECKS.first { it.id == "mitm" }, if (tls.startsWith("TLSv1.3")) St.Pass else St.Warn, tls)
        } catch (e: SSLException) { Result(CHECKS.first { it.id == "mitm" }, St.Fail, e.javaClass.simpleName) }
        catch (e: Exception) { Result(CHECKS.first { it.id == "mitm" }, St.Skip, "") }
        out += mitm; emit(mitm)

        val t0 = System.nanoTime()
        val ok204 = runCatching { tunnel(5000).newCall(Request.Builder().url("https://cp.cloudflare.com/generate_204").build()).execute().use { it.code == 204 } }.getOrDefault(false)
        val ms = (System.nanoTime() - t0) / 1_000_000
        r("latency", when { !ok204 -> St.Fail; ms < 900 -> St.Pass; ms < 2500 -> St.Warn; else -> St.Fail }, if (ok204) "$ms ms" else "")

        // ---- Leaks
        val dnsBody = tryGet("http://${System.nanoTime()}.edns.ip-api.com/json")
        val dns = SecurityCheck.parseResolver(dnsBody)
        r("dnsleak", when {
            dns == null -> St.Skip
            direct?.second != null && dns.second == direct.second && direct.second != exit?.second -> St.Fail
            else -> St.Pass
        }, SecurityCheck.resolverGeo(dnsBody) ?: dns?.first.orEmpty())
        r("dnsexit", when {
            dns?.second == null || exit?.second == null -> St.Skip
            dns.second == exit.second -> St.Pass
            else -> St.Warn
        }, listOfNotNull(dns?.second, exit?.second).joinToString(" → "))
        val remote = MmkvManager.decodeSettingsString(AppConfig.PREF_REMOTE_DNS).orEmpty()
        val encDns = remote.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        r("dnsenc", when {
            encDns.isEmpty() -> St.Warn
            encDns.all { it.startsWith("https://") || it.startsWith("tls://") || it.startsWith("quic") } -> St.Pass
            else -> St.Warn // plain DNS still rides the tunnel, but the exit's network can read it
        }, encDns.firstOrNull().orEmpty())
        val v6 = tryGet("https://api6.ipify.org")?.trim()?.takeIf { it.contains(':') }
        r("ipv6", if (vpn) St.Pass else St.Warn, v6 ?: if (vpn) "::/0 → tunnel" else "")
        r("udp", if (vpn) St.Pass else St.Warn)
        val sharing = MmkvManager.decodeSettingsBool(AppConfig.PREF_PROXY_SHARING, false)
        val auth = !SettingsManager.getSocksUsername().isNullOrBlank()
        r("lan", when { !sharing -> St.Pass; auth -> St.Warn; else -> St.Fail }, if (sharing) "0.0.0.0" else "127.0.0.1")

        // ---- Device
        val ao = MmkvManager.decodeSettingsString(SecurityCheck.PREF_ALWAYS_ON)?.toBooleanStrictOrNull()
        val ld = MmkvManager.decodeSettingsString(SecurityCheck.PREF_LOCKDOWN)?.toBooleanStrictOrNull()
        r("killswitch", St.Info, when { ao == true && ld == true -> "on"; ao == null -> ""; else -> "off" })
        val km = ctx.getSystemService(KeyguardManager::class.java)
        r("lock", if (km?.isDeviceSecure == true) St.Pass else St.Fail)
        val adb = Settings.Global.getInt(ctx.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1 ||
            Settings.Global.getInt(ctx.contentResolver, "adb_wifi_enabled", 0) == 1
        r("adb", if (adb) St.Warn else St.Pass)
        val patch = Build.VERSION.SECURITY_PATCH
        val ageDays = runCatching { (System.currentTimeMillis() - java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(patch)!!.time) / 86_400_000L }.getOrNull()
        r("patch", when { ageDays == null -> St.Skip; ageDays <= 120 -> St.Pass; ageDays <= 365 -> St.Warn; else -> St.Fail }, patch)
        val userCas = runCatching { KeyStore.getInstance("AndroidCAStore").apply { load(null) }.aliases().toList().count { it.startsWith("user:") } }.getOrNull()
        r("userca", when { userCas == null -> St.Skip; userCas == 0 -> St.Pass; else -> St.Warn }, userCas?.toString().orEmpty())
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val pd = runCatching { cm.allNetworks.mapNotNull { cm.getLinkProperties(it) }.any { it.isPrivateDnsActive } }.getOrNull()
        r("privdns", St.Info, when (pd) { true -> "on"; false -> "off"; null -> "" })

        // ---- Tor (information: tells the user clearly, never scored)
        val api = tryGet("https://check.torproject.org/api/ip")
        val isTorApi = runCatching { JsonParser.parseString(api).asJsonObject.get("IsTor").asBoolean }.getOrNull()
        r("tor_api", when (isTorApi) { true -> St.Warn; false -> St.Pass; null -> St.Skip })
        val fresh = TorExits.refreshIfStale(ctx) { u -> tryGet(u) }
        val inList = exit?.first?.let { TorExits.isExit(ctx, it) }
        r("tor_list", when { !fresh && TorExits.size(ctx) == 0 -> St.Skip; inList == true -> St.Warn; inList == false -> St.Pass; else -> St.Skip },
            if (TorExits.size(ctx) > 0) "${TorExits.size(ctx)}" else "")
        val relayBody = exit?.first?.let { tryGet("https://onionoo.torproject.org/summary?limit=5&search=${it}") }
        val relays = runCatching { JsonParser.parseString(relayBody).asJsonObject.getAsJsonArray("relays").size() }.getOrNull()
        r("tor_relay", when (relays) { null -> St.Skip; 0 -> St.Pass; else -> St.Warn }, relays?.toString().orEmpty())
        val age = TorExits.ageMs(ctx)
        r("tor_cache", when { age == null -> St.Skip; age < 7L * 86_400_000L -> St.Pass; else -> St.Warn }, age?.let { "${it / 3_600_000L} h" }.orEmpty())

        torExit = isTorApi == true || inList == true
        lastScore = score(out)
        SecurityCheck.last = SecurityCheck.Report(true, emptyList(), lastScore!!, emptyList())
        SecurityCheck.lastAtMs = System.currentTimeMillis()
        LogUtil.i(AppConfig.TAG, "SecurityAudit: score=$lastScore tor=$torExit " + out.joinToString { "${it.check.id}=${it.st}" })
        return out
    }
}
