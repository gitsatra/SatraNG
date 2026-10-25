package com.v2ray.ang.handler

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.IPAPIInfo
import com.v2ray.ang.dto.UrlContentRequest
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.ServerAffiliationInfo
import com.v2ray.ang.util.HttpUtil
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.LogUtil
import java.net.InetAddress
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Country shown on a config row: only ever the **exit IP**, measured by sending a request
 * *through that config*. Never the remark text and never the server address.
 *
 * Stability rules:
 *  - The exit is measured once in the background when the config arrives (subscription added),
 *    then kept. It is measured again only when the user connects to that config (through the live
 *    tunnel), when its subscription is updated, or when the user taps Retry. Ping tests never probe.
 *  - A failed re-measurement keeps the known exit; it is never replaced by a guess.
 *  - The server address country is stored separately as *entry* info ([resolveEntry]); it is used to
 *    recognise tunnels (entry in Iran, exit abroad) and is never shown as the flag.
 *  - Unknown: the UI shows a neutral globe.
 *
 * Reads are pure MMKV (safe on the UI thread). All network work is explicit and off-thread.
 */
object GeoIpResolver {

    /** Entry (server address) lookups are refreshed after this; exits do not expire. */
    const val HOST_TTL_MS = 3L * 24 * 60 * 60 * 1000
    const val ATTEMPT_BACKOFF_MS = 10L * 60 * 1000

    data class GeoResult(
        val countryCode: String?,
        val countryName: String?,
        val ip: String?,
        val exitVerified: Boolean = false,
    ) {
        companion object { val UNKNOWN = GeoResult(null, null, null) }
    }

    private val attempts = ConcurrentHashMap<String, Long>()

    // ---- pure policy (unit tested) ----

    /** True when a measured exit country is known. Exits stay valid until re-measured. */
    fun hasExit(aff: ServerAffiliationInfo?): Boolean =
        aff != null && aff.exitIpVerified && !aff.countryCode.isNullOrBlank()

    /**
     * True when the background locator should measure this config's exit now.
     * [recheck] (subscription updated / user retry) measures again even when an exit is known.
     */
    fun needsLookup(aff: ServerAffiliationInfo?, nowMs: Long, recheck: Boolean = false): Boolean {
        if (recheck) return true
        if (hasExit(aff)) return false
        return nowMs - (aff?.geoAttemptAt ?: 0L) >= ATTEMPT_BACKOFF_MS
    }

    /** True when the entry (server address) country should be looked up in the background. */
    fun needsEntryLookup(aff: ServerAffiliationInfo?, nowMs: Long): Boolean {
        if (aff != null && !aff.entryCountryCode.isNullOrBlank() && nowMs - aff.entryUpdatedAt < HOST_TTL_MS) return false
        return nowMs - (aff?.entryAttemptAt ?: 0L) >= ATTEMPT_BACKOFF_MS
    }

    // ---- UI read path ----

    fun displayForProfile(guid: String, profile: ProfileItem): GeoResult = display(MmkvManager.decodeServerAffiliationInfo(guid))

    internal fun display(aff: ServerAffiliationInfo?): GeoResult {
        // Older builds stored server-address guesses in the same fields; those are not exits.
        if (!hasExit(aff)) return GeoResult.UNKNOWN
        aff!!
        return GeoResult(aff.countryCode, aff.countryName ?: countryNameForCode(aff.countryCode), aff.resolvedIp, true)
    }

    // ---- network paths (call off the main thread) ----

    /** Persists a measured exit IP for [guid]. */
    fun storeExit(guid: String, ip: String?, rawCountry: String?): GeoResult {
        val code = normalizeCountryCode(rawCountry.orEmpty())
        if (code == null) {
            markAttempt(guid)
            return GeoResult.UNKNOWN
        }
        val name = countryNameForCode(code)
        MmkvManager.encodeServerGeo(guid, code, name, ip, exitIpVerified = true)
        return GeoResult(code, name, ip, true)
    }

    /** After the VPN is up, ask through the local proxy where the traffic really leaves. */
    fun resolveExitAfterConnect(guid: String): GeoResult {
        if (guid.isBlank()) return GeoResult.UNKNOWN
        return try {
            val remote = SpeedtestManager.getRemoteIPInfo()
            storeExit(guid, remote?.ipAddress, remote?.country)
        } catch (e: Exception) {
            LogUtil.w(AppConfig.TAG, "Exit geo lookup failed: ${e.message}")
            GeoResult.UNKNOWN
        }
    }

    /**
     * Looks up where the config's server address is (entry point) and stores it as entry info.
     * Never touches the exit country. Returns the entry country code, or null.
     */
    fun resolveEntry(guid: String, profile: ProfileItem, viaHttpPort: Int = 0): String? {
        val now = System.currentTimeMillis()
        val aff = MmkvManager.decodeServerAffiliationInfo(guid)
        if (!needsEntryLookup(aff, now)) return aff?.entryCountryCode
        val last = attempts[guid] ?: 0L
        if (now - last < ATTEMPT_BACKOFF_MS) return aff?.entryCountryCode
        attempts[guid] = now

        val host = profile.server?.trim().orEmpty()
        if (host.isBlank()) return null
        return try {
            val ip = if (isIpLiteral(host)) host else InetAddress.getByName(host).hostAddress
            // LAN/loopback hosts have no country; looking them up would return the phone's own country.
            val code = if (ip.isNullOrBlank() || isNonPublicIp(ip)) null else lookupIp(ip, viaHttpPort)
            MmkvManager.encodeServerEntry(guid, code, ip)
            code ?: aff?.entryCountryCode
        } catch (e: Exception) {
            LogUtil.w(AppConfig.TAG, "Entry geo failed for ${host}: ${e.message}")
            MmkvManager.encodeServerEntry(guid, null, null)
            aff?.entryCountryCode
        }
    }

    private fun markAttempt(guid: String) {
        MmkvManager.markServerGeoAttempt(guid)
    }

    private val geoEndpoints = listOf(
        "https://ipwho.is/%s",
        "https://ipapi.co/%s/json/",
    )

    private fun lookupIp(ip: String, httpPort: Int): String? {
        for (pattern in geoEndpoints) {
            for (port in listOf(httpPort, 0).distinct()) {
                val body = HttpUtil.getUrlContent(UrlContentRequest(url = pattern.format(ip), timeout = 4000, httpPort = port))
                    ?: continue
                val info = JsonUtil.fromJsonSafe(body, IPAPIInfo::class.java) ?: continue
                val code = normalizeCountryCode(
                    listOf(info.country_code, info.countryCode, info.country).firstOrNull { !it.isNullOrBlank() }.orEmpty(),
                )
                if (code != null) return code
            }
        }
        return null
    }

    internal fun isNonPublicIp(ip: String): Boolean = runCatching {
        if (!isIpLiteral(ip)) return@runCatching false
        val a = InetAddress.getByName(ip)
        a.isSiteLocalAddress || a.isLoopbackAddress || a.isLinkLocalAddress || a.isAnyLocalAddress ||
            (a.address.size == 16 && (a.address[0].toInt() and 0xFE) == 0xFC) || // IPv6 unique-local fc00::/7
            (a.address.size == 4 && (a.address[0].toInt() and 0xFF) == 100 && (a.address[1].toInt() and 0xC0) == 64) // CGNAT 100.64/10
    }.getOrDefault(false)

    internal fun isIpLiteral(host: String): Boolean =
        host.matches(Regex("""^\d{1,3}(\.\d{1,3}){3}$""")) || (host.contains(':') && host.matches(Regex("^[0-9a-fA-F:.%]+$")))

    internal fun normalizeCountryCode(raw: String): String? {
        val s = raw.trim()
        if (s.isBlank()) return null
        val upper = s.uppercase(Locale.US)
        if (upper.length == 2 && Locale.getISOCountries().contains(upper)) return upper
        return Locale.getISOCountries().firstOrNull { code ->
            Locale("", code).getDisplayCountry(Locale.ENGLISH).equals(s, ignoreCase = true)
        }
    }

    fun countryNameForCode(code: String?): String? {
        if (code.isNullOrBlank()) return null
        return try {
            Locale("", code).displayCountry.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            code
        }
    }

    fun flagEmoji(countryCode: String?): String {
        val cc = countryCode?.uppercase(Locale.US) ?: return "🌐"
        if (cc.length != 2 || !cc.all { it in 'A'..'Z' }) return "🌐"
        val first = Character.codePointAt(cc, 0) - 0x41 + 0x1F1E6
        val second = Character.codePointAt(cc, 1) - 0x41 + 0x1F1E6
        return String(Character.toChars(first)) + String(Character.toChars(second))
    }
}
