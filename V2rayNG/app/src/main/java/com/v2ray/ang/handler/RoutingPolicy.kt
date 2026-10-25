package com.v2ray.ang.handler

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.RulesetItem
import com.v2ray.ang.util.JsonUtil

/**
 * Smart routing (on: the regional preset sends local sites direct) plus user exceptions.
 * Off is a real full tunnel: every direct rule is dropped except the LAN/private ones.
 * Exceptions always sit first, so they win over the preset (Xray takes the first match).
 */
object RoutingPolicy {
    const val PREF_SMART = "pref_satra_smart_routing"
    const val PREF_VPN_DOMAINS = "pref_satra_always_vpn_domains"
    const val PREF_VPN_APPS = "pref_satra_always_vpn_apps"
    const val PREF_DIRECT_DOMAINS = "pref_satra_always_direct_domains"
    const val PREF_TIP_SEEN = "pref_satra_smart_routing_tip_seen"

    data class Exceptions(
        val vpnDomains: List<String> = emptyList(),
        val vpnApps: List<String> = emptyList(),
        val directDomains: List<String> = emptyList(),
    )

    /** Iranian banking/payment/services hosts outside .ir (the .ir TLD, geosite:category-ir and geoip:ir are in the preset). */
    val IRAN_SERVICES = listOf(
        "shaparak.ir", "shetab.ir", "sep.ir", "asanpardakht.ir", "bmi.ir", "bankmellat.ir", "bsi.ir", "banksepah.ir", "tejaratbank.ir",
        "parsian-bank.ir", "bpi.ir", "refah-bank.ir", "bki.ir", "bank-maskan.ir", "shahr-bank.ir", "ba24.ir", "sb24.ir",
        "blubank.com", "bale.ai", "eitaa.com", "rubika.ir", "splus.ir", "snapp.taxi", "snapp.ir", "snappfood.ir", "tapsi.ir", "tapsi.cab",
        "digikala.com", "dkstatics-public.digikala.com", "divar.ir", "aparat.com", "namava.ir", "filimo.com", "telewebion.com",
        "cafebazaar.ir", "myket.ir", "balad.ir", "neshan.org", "torob.com", "digipay.ir", "mydigipay.com", "ghesta.ir", "tara360.ir",
        "sheypoor.com", "basalam.com", "okala.com", "irancell.ir", "mci.ir", "rightel.ir", "my.gov.ir", "arvancloud.ir", "arvancloud.com",
    )

    private val PRIVATE = setOf(AppConfig.GEOIP_PRIVATE, "geosite:private")

    private fun isLanOnly(r: RulesetItem): Boolean {
        val all = r.ip.orEmpty() + r.domain.orEmpty()
        return all.isNotEmpty() && all.all { it in PRIVATE } && r.process.isNullOrEmpty()
    }

    /** The rules the core really uses. Pure; unit-tested. */
    fun effective(stored: List<RulesetItem>, smart: Boolean, ex: Exceptions, appsSupported: Boolean = true, iran: Boolean = false): List<RulesetItem> {
        val base = if (smart) stored else stored.filter { !(it.enabled && it.outboundTag == AppConfig.TAG_DIRECT) || isLanOnly(it) }
        val head = mutableListOf<RulesetItem>()
        if (ex.vpnDomains.isNotEmpty()) {
            head += RulesetItem(id = "satra-exc-vpn", remarks = "Always VPN", domain = ex.vpnDomains.map { "domain:$it" }, outboundTag = AppConfig.TAG_PROXY, locked = true)
        }
        if (ex.vpnApps.isNotEmpty() && appsSupported) {
            head += RulesetItem(id = "satra-exc-vpn-apps", remarks = "Always VPN apps", process = ex.vpnApps, outboundTag = AppConfig.TAG_PROXY, locked = true)
        }
        if (smart && ex.directDomains.isNotEmpty()) {
            head += RulesetItem(id = "satra-exc-direct", remarks = "Always direct", domain = ex.directDomains.map { "domain:$it" }, outboundTag = AppConfig.TAG_DIRECT, locked = true)
        }
        if (smart && iran) {
            head += RulesetItem(id = "satra-ir-services", remarks = "Iranian services (non-.ir)", domain = IRAN_SERVICES.map { "domain:$it" }, outboundTag = AppConfig.TAG_DIRECT, locked = true)
        }
        // LAN rules stay first, then the user's exceptions, then the preset.
        val lan = base.filter { isLanOnly(it) }
        return lan + head + base.filterNot { isLanOnly(it) }
    }

    private val HOST = Regex("^(?=.{1,253}$)(?!-)[a-z0-9-]{1,63}(?<!-)(\\.(?!-)[a-z0-9-]{1,63}(?<!-))*\\.[a-z]{2,63}$")
    private val PKG = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$")

    /** "https://www.Bank.ir/login" -> "www.bank.ir"; null when not a valid host name. */
    fun normalizeDomain(input: String): String? {
        var s = input.trim().lowercase()
        s = s.removePrefix("domain:").removePrefix("full:")
        s = s.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore('#').substringBefore(':')
        s = s.trimStart('*', '.').trimEnd('.')
        if (s.isEmpty() || s.length > 253) return null
        // IDN (e.g. Persian domains) -> punycode, so Xray matches the real name.
        s = try { java.net.IDN.toASCII(s, java.net.IDN.ALLOW_UNASSIGNED).lowercase() } catch (e: Exception) { return null }
        return s.takeIf { HOST.matches(it) }
    }

    fun isValidPackage(p: String): Boolean = PKG.matches(p.trim())

    // ---------- storage ----------

    fun smartOn(): Boolean = MmkvManager.decodeSettingsBool(PREF_SMART, true)

    fun setSmart(on: Boolean) {
        MmkvManager.encodeSettings(PREF_SMART, on)
    }

    private fun list(key: String): List<String> =
        MmkvManager.decodeSettingsString(key)?.let { JsonUtil.fromJsonSafe(it, Array<String>::class.java)?.toList() }.orEmpty()

    private fun save(key: String, v: List<String>) {
        MmkvManager.encodeSettings(key, JsonUtil.toJson(v.distinct()))
    }

    fun exceptions() = Exceptions(list(PREF_VPN_DOMAINS), list(PREF_VPN_APPS), list(PREF_DIRECT_DOMAINS))

    fun add(key: String, value: String) = save(key, list(key) + value)
    fun remove(key: String, value: String) = save(key, list(key) - value)
    fun values(key: String) = list(key)

    /** Iran unless the stored preset is the Russian one. */
    fun isRussia(stored: List<RulesetItem>?): Boolean =
        stored.orEmpty().any { r -> r.ip.orEmpty().any { it == "geoip:ru" } || r.domain.orEmpty().any { it == "domain:ru" || it == "geosite:category-ru" } }

    /** China preset (upstream "white": geosite:cn / geoip:cn direct). */
    fun isChina(stored: List<RulesetItem>?): Boolean =
        stored.orEmpty().any { r -> r.ip.orEmpty().any { it == "geoip:cn" } || r.domain.orEmpty().any { it == "geosite:cn" } }

    fun isIran(stored: List<RulesetItem>?): Boolean = !isRussia(stored) && !isChina(stored)

    fun current(stored: List<RulesetItem>?): List<RulesetItem> =
        effective(stored.orEmpty(), smartOn(), exceptions(), SettingsManager.canUseProcessRouting(), isIran(stored))
}
