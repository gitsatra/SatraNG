package com.v2ray.ang.handler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
// ProcNetUidScanner + AppUsageReader helpers

class UsageLimitEngineTest {

    @Test
    fun expandSitePresetDomains_addsYoutubeCdnAndGeosite() {
        val out = UsageLimitEngine.expandSitePresetDomains(listOf("youtube.com"))
        assertTrue(out.any { it == "domain:youtube.com" || it.contains("youtube.com") })
        assertTrue(out.any { it.contains("googlevideo.com") })
        assertTrue(out.any { it.contains("ytimg.com") })
        assertTrue(out.any { it.contains("youtubei.googleapis.com") })
        assertTrue(out.contains("geosite:youtube"))
    }

    @Test
    fun expandSitePresetDomains_leavesUnrelatedSitesAlone() {
        val out = UsageLimitEngine.expandSitePresetDomains(listOf("example.com"))
        assertTrue(out.size == 1)
        assertTrue(out.single().contains("example.com"))
        assertTrue(out.none { it.contains("geosite:youtube") })
        assertTrue(out.none { it.contains("geosite:facebook") })
    }

    @Test
    fun expandSitePresetDomains_addsFacebookCdnAndGeosite() {
        val out = UsageLimitEngine.expandSitePresetDomains(listOf("facebook.com"))
        assertTrue(out.any { it.contains("facebook.com") })
        assertTrue(out.any { it.contains("fbcdn.net") })
        assertTrue(out.any { it.contains("instagram.com") })
        assertTrue(out.contains("geosite:facebook"))
    }

    @Test
    fun mergeMaxByUid_takesLargerCountersPerUid() {
        val a = listOf(AppUsage(10, null, 100, 50), AppUsage(20, null, 5, 5))
        val b = listOf(AppUsage(10, null, 80, 90), AppUsage(30, null, 1, 2))
        val merged = AppUsageReader.mergeMaxByUid(a, b).associateBy { it.uid }
        assertEquals(100L, merged[10]!!.rxBytes)
        assertEquals(90L, merged[10]!!.txBytes)
        assertEquals(5L, merged[20]!!.rxBytes)
        assertEquals(1L, merged[30]!!.rxBytes)
        assertEquals(2L, merged[30]!!.txBytes)
    }

    @Test
    fun distributeByWeight_splitsAllBytesAcrossUids() {
        val shares = ProcNetUidScanner.distributeByWeight(1000L, mapOf(10 to 1, 20 to 3))
        assertEquals(1000L, shares.values.sum())
        assertEquals(250L, shares[10])
        assertEquals(750L, shares[20])
    }

    @Test
    fun distributeByWeight_emptyWeightsYieldsEmpty() {
        assertTrue(ProcNetUidScanner.distributeByWeight(100L, emptyMap()).isEmpty())
        assertTrue(ProcNetUidScanner.distributeByWeight(0L, mapOf(1 to 1)).isEmpty())
    }

    @Test
    fun uidTunnelMeter_tagRoundTrip() {
        assertEquals("appuid-10561", UidTunnelMeter.tagForUid(10561))
        assertEquals(10561, UidTunnelMeter.parseUid("appuid-10561"))
        assertEquals(null, UidTunnelMeter.parseUid("proxy"))
    }

    @Test
    fun mergeMaxGrowth_takesLargerPerUid() {
        val a = mapOf(1 to 100L, 2 to 50L)
        val b = mapOf(1 to 80L, 3 to 10L)
        val m = VpnAppUsageTracker.mergeMaxGrowth(a, b)
        assertEquals(100L, m[1])
        assertEquals(50L, m[2])
        assertEquals(10L, m[3])
    }

    @Test
    fun processUidResolver_antiBypassOnlyWhenDestOwnedByBlockedUid() {
        assertEquals(-1, ProcessUidResolver.antiBypassUid(emptySet(), "1.2.3.4", 443, destOwner = 10643 to 0L))
        assertEquals(
            10643,
            ProcessUidResolver.antiBypassUid(
                blockedUids = setOf(10643),
                destIp = "1.2.3.4",
                destPort = 443,
                nowMs = 1_000L,
                destOwner = 10643 to 900L,
            ),
        )
        assertEquals(
            -1,
            ProcessUidResolver.antiBypassUid(
                blockedUids = setOf(10643),
                destIp = "1.2.3.4",
                destPort = 443,
                nowMs = 1_000L,
                destOwner = 99999 to 900L,
            ),
        )
    }

    @Test
    fun distributeByWeight_remainderGoesToHeaviest() {
        val shares = ProcNetUidScanner.distributeByWeight(10L, mapOf(1 to 1, 2 to 1, 3 to 1))
        assertEquals(10L, shares.values.sum())
        assertEquals(3, shares.size)
    }
}
