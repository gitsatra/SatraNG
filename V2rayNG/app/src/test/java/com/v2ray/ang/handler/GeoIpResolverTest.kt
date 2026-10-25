package com.v2ray.ang.handler

import com.v2ray.ang.dto.entities.ServerAffiliationInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GeoIpResolverTest {
    private val now = 1_000_000_000_000L
    private val day = 24L * 60 * 60 * 1000

    private fun aff(code: String?, verified: Boolean, ageMs: Long, attemptAgeMs: Long = 100 * day) = ServerAffiliationInfo(
        countryCode = code, exitIpVerified = verified, geoUpdatedAt = now - ageMs, geoAttemptAt = now - attemptAgeMs,
    )

    @Test
    fun flagEmoji_buildsRegionalIndicator() {
        assertEquals("🇩🇪", GeoIpResolver.flagEmoji("DE"))
        assertEquals("🌐", GeoIpResolver.flagEmoji(null))
        assertEquals("🌐", GeoIpResolver.flagEmoji("XYZ"))
    }

    @Test
    fun countryNameForCode_returnsLocalizedName() {
        val name = GeoIpResolver.countryNameForCode("DE")
        assertTrue(name != null && name.isNotBlank())
    }

    @Test
    fun display_neverGuessesFromRemarks_unknownIsGlobe() {
        assertNull(GeoIpResolver.display(null).countryCode)
        assertNull(GeoIpResolver.display(aff(null, false, 0)).countryCode)
    }

    @Test
    fun display_showsOnlyMeasuredExits_andKeepsThem() {
        // A measured exit stays, however old: flags are stable until re-measured.
        assertEquals("DE", GeoIpResolver.display(aff("DE", true, 400 * day)).countryCode)
        assertTrue(GeoIpResolver.display(aff("DE", true, day)).exitVerified)
        // Server-address guesses (older builds) are never shown as the flag.
        assertNull(GeoIpResolver.display(aff("NL", false, 1000)).countryCode)
    }

    @Test
    fun display_entryCountryNeverReplacesExit() {
        val a = aff("DE", true, day).apply { entryCountryCode = "IR" }
        assertEquals("DE", GeoIpResolver.display(a).countryCode)
        val b = aff(null, false, 0).apply { entryCountryCode = "IR" }
        assertNull(GeoIpResolver.display(b).countryCode)
    }

    @Test
    fun needsLookup_measuresOnceThenOnlyOnRecheck() {
        assertTrue(GeoIpResolver.needsLookup(null, now))
        // known exit: never in the background, even when old
        assertFalse(GeoIpResolver.needsLookup(aff("DE", true, 400 * day), now))
        // recheck (subscription updated / Retry) measures again
        assertTrue(GeoIpResolver.needsLookup(aff("DE", true, 1000), now, recheck = true))
        // failures wait for the backoff
        assertFalse(GeoIpResolver.needsLookup(aff(null, false, 0, attemptAgeMs = 60_000), now))
        assertTrue(GeoIpResolver.needsLookup(aff(null, false, 0, attemptAgeMs = day), now))
    }

    @Test
    fun needsEntryLookup_respectsTtlAndBackoff() {
        assertTrue(GeoIpResolver.needsEntryLookup(null, now))
        val fresh = ServerAffiliationInfo(entryCountryCode = "IR", entryUpdatedAt = now - day)
        assertFalse(GeoIpResolver.needsEntryLookup(fresh, now))
        val tried = ServerAffiliationInfo(entryAttemptAt = now - 60_000)
        assertFalse(GeoIpResolver.needsEntryLookup(tried, now))
    }

    @Test
    fun normalizeCountryCode_acceptsIsoAndEnglishNames() {
        assertEquals("DE", GeoIpResolver.normalizeCountryCode("de"))
        assertEquals("NL", GeoIpResolver.normalizeCountryCode("Netherlands"))
        assertNull(GeoIpResolver.normalizeCountryCode("ZZ"))
        assertNull(GeoIpResolver.normalizeCountryCode(""))
    }

    @Test
    fun isIpLiteral_handlesV4AndV6() {
        assertTrue(GeoIpResolver.isIpLiteral("1.2.3.4"))
        assertTrue(GeoIpResolver.isIpLiteral("2001:db8::1"))
        assertFalse(GeoIpResolver.isIpLiteral("example.com"))
    }

    @org.junit.jupiter.api.Test
    fun lanAndLoopbackHostsHaveNoCountry() {
        listOf("192.168.0.41", "10.0.0.1", "172.16.5.4", "127.0.0.1", "169.254.1.1", "100.64.0.1", "fd00::1", "::1")
            .forEach { org.junit.jupiter.api.Assertions.assertTrue(GeoIpResolver.isNonPublicIp(it), it) }
        listOf("8.8.8.8", "188.121.123.171", "2606:4700::1111", "example.com")
            .forEach { org.junit.jupiter.api.Assertions.assertFalse(GeoIpResolver.isNonPublicIp(it), it) }
    }
}
