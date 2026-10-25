package com.v2ray.ang.handler

import android.content.Context
import androidx.annotation.StringRes
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * Connection modes (shared spec with desktop SatraN: MODES-SPEC v1).
 *
 * A mode is an overlay applied only while the core config is generated: the user's stored
 * settings are never written, so going back to [Mode.NORMAL] restores exactly what they had.
 * Two layers: (1) setting overrides read through [MmkvManager] inside [during], and
 * (2) a JSON post-pass on the finished config for things no setting expresses
 * (QUIC block, ad block, uTLS fingerprint, insecure TLS off, no-bypass routing).
 */
object ModeEngine {
    const val PREF_MODE = "satra.mode"

    // Declared first: read() below goes through MmkvManager → override() during class init.
    private val scope = ThreadLocal<Mode?>()

    enum class Mode(val id: String, @StringRes val title: Int, @StringRes val desc: Int) {
        NORMAL("normal", R.string.mode_normal, R.string.mode_normal_d),
        GAME("game", R.string.mode_game, R.string.mode_game_d),
        SECURE("secure", R.string.mode_secure, R.string.mode_secure_d),
        FORTRESS("fortress", R.string.mode_fortress, R.string.mode_fortress_d),
        STREAM("stream", R.string.mode_stream, R.string.mode_stream_d),
        ECO("eco", R.string.mode_eco, R.string.mode_eco_d);

        companion object {
            fun fromId(id: String?): Mode = entries.firstOrNull { it.id == id } ?: NORMAL
        }
    }

    /** One human-readable change, shown by the activation animation. */
    data class Change(@StringRes val label: Int, val arg: String? = null)

    private val _mode = MutableStateFlow(read())
    val mode: StateFlow<Mode> = _mode

    fun read(): Mode = Mode.fromId(MmkvManager.decodeSettingsString(PREF_MODE))

    fun current(): Mode = read().also { if (_mode.value != it) _mode.value = it }

    /** Persists the mode; the caller reloads the core when connected (never connects by itself). */
    fun set(mode: Mode) {
        MmkvManager.encodeSettings(PREF_MODE, mode.id)
        _mode.value = mode
    }

    /** Notification quick switch: Normal → Game → Secure → Normal (any other mode → Normal). */
    fun cycleNext(m: Mode): Mode = when (m) {
        Mode.NORMAL -> Mode.GAME
        Mode.GAME -> Mode.SECURE
        else -> Mode.NORMAL
    }

    fun changes(mode: Mode): List<Change> = when (mode) {
        Mode.NORMAL -> listOf(Change(R.string.ch_none))
        Mode.GAME -> listOf(
            Change(R.string.ch_mux_off), Change(R.string.ch_sniff_light),
            Change(R.string.ch_dns_fast), Change(R.string.ch_quic_allow), Change(R.string.ch_mtu, "1400"),
        )
        Mode.SECURE -> listOf(
            Change(R.string.ch_dns_proxy), Change(R.string.ch_quic_block), Change(R.string.ch_utls),
            Change(R.string.ch_fragment), Change(R.string.ch_ipv6_off),
        )
        Mode.FORTRESS -> changes(Mode.SECURE) + Change(R.string.ch_strict)
        Mode.STREAM -> listOf(Change(R.string.ch_mux_off), Change(R.string.ch_quic_allow), Change(R.string.ch_mtu, "1500"))
        Mode.ECO -> listOf(Change(R.string.ch_ads), Change(R.string.ch_mux_on), Change(R.string.ch_quic_block))
    }

    // ---- layer 1: setting overrides, active only inside during{} on this thread -------------

    fun <T> during(block: () -> T): T {
        val prev = scope.get()
        scope.set(read())
        try { return block() } finally { scope.set(prev) }
    }

    /** Non-null when the active mode overrides [key] (only inside [during]). */
    internal fun override(key: String): Any? {
        val m = scope.get() ?: return null
        return overrides(m)[key]
    }

    /** Also used outside config generation for the VPN interface (MTU/IPv6). */
    fun overrideFor(key: String): Any? = overrides(read())[key]

    private fun overrides(m: Mode): Map<String, Any> = when (m) {
        Mode.NORMAL -> emptyMap()
        Mode.GAME -> mapOf(
            AppConfig.PREF_MUX_ENABLED to false,
            AppConfig.PREF_SNIFFING_ENABLED to true,
            AppConfig.PREF_ROUTE_ONLY_ENABLED to true,
            AppConfig.PREF_ROUTING_DOMAIN_STRATEGY to "AsIs",
            AppConfig.PREF_VPN_MTU to "1400",
        )
        Mode.SECURE, Mode.FORTRESS -> mapOf(
            AppConfig.PREF_SNIFFING_ENABLED to true,
            AppConfig.PREF_ROUTE_ONLY_ENABLED to false,
            AppConfig.PREF_FRAGMENT_ENABLED to true,
            AppConfig.PREF_PREFER_IPV6 to false,
            AppConfig.PREF_IPV6_ENABLED to false,
            AppConfig.PREF_REMOTE_DNS to "https://1.1.1.1/dns-query,https://8.8.8.8/dns-query",
        )
        Mode.STREAM -> mapOf(
            AppConfig.PREF_MUX_ENABLED to false,
            AppConfig.PREF_SNIFFING_ENABLED to true,
            AppConfig.PREF_VPN_MTU to "1500",
        )
        Mode.ECO -> mapOf(
            AppConfig.PREF_MUX_ENABLED to true,
            AppConfig.PREF_MUX_CONCURRENCY to "8",
        )
    }

    // ---- layer 2: JSON post-pass --------------------------------------------------------------

    fun postProcess(context: Context, json: String): String {
        val m = read()
        if (m == Mode.NORMAL || json.isBlank()) return json
        return try {
            val root = JsonParser.parseString(json).asJsonObject
            val secure = m == Mode.SECURE || m == Mode.FORTRESS
            if (secure) hardenTls(root)
            if (m == Mode.FORTRESS) dropDirectBypass(root)
            val pre = JsonArray()
            if (secure || m == Mode.ECO) pre.add(rule("network" to "udp", "port" to "443", "outboundTag" to "block"))
            if (m == Mode.ECO && geositeHas(context, "CATEGORY-ADS-ALL")) {
                pre.add(rule("outboundTag" to "block").apply {
                    add("domain", JsonArray().apply { add("geosite:category-ads-all") })
                })
            }
            if (pre.size() > 0) prependRules(root, pre)
            val mux = root.getAsJsonArray("outbounds")?.firstOrNull()?.asJsonObject?.getAsJsonObject("mux")?.get("enabled")
            LogUtil.i(AppConfig.TAG, "ModeEngine: applied ${m.id} (+${pre.size()} rules, mux=$mux, rules=${root.getAsJsonObject("routing")?.getAsJsonArray("rules")?.size()})")
            root.toString()
        } catch (e: Exception) {
            LogUtil.w(AppConfig.TAG, "ModeEngine: post-pass skipped: ${e.javaClass.simpleName}")
            json
        }
    }

    private fun rule(vararg kv: Pair<String, String>) = JsonObject().apply {
        addProperty("type", "field"); kv.forEach { addProperty(it.first, it.second) }
    }

    private fun prependRules(root: JsonObject, pre: JsonArray) {
        val routing = root.getAsJsonObject("routing") ?: JsonObject().also { root.add("routing", it) }
        val old = routing.getAsJsonArray("rules") ?: JsonArray()
        // Keep DNS-hijack / api rules that must stay first (inboundTag-scoped) ahead of ours.
        val head = JsonArray(); val tail = JsonArray()
        old.forEach { r -> if (r.isJsonObject && r.asJsonObject.has("inboundTag")) head.add(r) else tail.add(r) }
        val merged = JsonArray(); merged.addAll(head); merged.addAll(pre); merged.addAll(tail)
        routing.add("rules", merged)
    }

    private fun hardenTls(root: JsonObject) {
        root.getAsJsonArray("outbounds")?.forEach { ob ->
            val ss = ob.asJsonObject.getAsJsonObject("streamSettings") ?: return@forEach
            listOf("tlsSettings", "realitySettings").forEach { k ->
                val t = ss.getAsJsonObject(k) ?: return@forEach
                if (!t.has("fingerprint") || t.get("fingerprint").asString.isBlank()) t.addProperty("fingerprint", "chrome")
                if (k == "tlsSettings") t.addProperty("allowInsecure", false)
            }
        }
    }

    /** Fortress: nothing goes direct except LAN/private ranges. */
    private fun dropDirectBypass(root: JsonObject) {
        val routing = root.getAsJsonObject("routing") ?: return
        val rules = routing.getAsJsonArray("rules") ?: return
        val kept = JsonArray()
        rules.forEach { r ->
            val o = r.asJsonObject
            val direct = o.get("outboundTag")?.asString == "direct"
            val privateOnly = o.getAsJsonArray("ip")?.all { it.asString == "geoip:private" } == true && !o.has("domain")
            if (!direct || privateOnly || o.has("inboundTag")) kept.add(r)
        }
        routing.add("rules", kept)
    }

    private val geoCache = HashMap<String, Boolean>()
    private fun geositeHas(context: Context, code: String): Boolean {
        val f = File(Utils.userAssetPath(context), "geosite.dat")
        val key = "$code:${f.length()}:${f.lastModified()}"
        geoCache[key]?.let { return it }
        val found = runCatching { f.readBytes().let { indexOf(it, code.toByteArray()) >= 0 } }.getOrDefault(false)
        geoCache[key] = found
        return found
    }

    private fun indexOf(h: ByteArray, n: ByteArray): Int {
        outer@ for (i in 0..h.size - n.size) { for (j in n.indices) if (h[i + j] != n[j]) continue@outer; return i }
        return -1
    }
}
