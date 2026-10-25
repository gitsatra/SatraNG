package com.v2ray.ang

import com.v2ray.ang.dto.entities.RulesetItem
import com.v2ray.ang.handler.RoutingPolicy
import com.v2ray.ang.handler.RoutingPolicy.Exceptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutingPolicyTest {
    private val preset = listOf(
        RulesetItem(id = "lan", ip = listOf("geoip:private"), outboundTag = "direct"),
        RulesetItem(id = "tg", domain = listOf("geosite:telegram"), outboundTag = "proxy"),
        RulesetItem(id = "ir", domain = listOf("domain:ir", "geosite:category-ir"), outboundTag = "direct"),
        RulesetItem(id = "irip", ip = listOf("geoip:ir"), outboundTag = "direct"),
    )

    @Test fun smartOnKeepsPreset() =
        assertEquals(listOf("lan", "tg", "ir", "irip"), RoutingPolicy.effective(preset, true, Exceptions()).map { it.id })

    @Test fun smartOffIsFullTunnelButKeepsLan() {
        val r = RoutingPolicy.effective(preset, false, Exceptions(directDomains = listOf("bank.ir")))
        assertEquals(listOf("lan", "tg"), r.map { it.id })
        assertTrue(r.none { it.outboundTag == "direct" && it.id != "lan" })
    }

    @Test fun exceptionsComeBeforePresetAndAfterLan() {
        val r = RoutingPolicy.effective(preset, true, Exceptions(vpnDomains = listOf("digikala.com", "my.ir"), vpnApps = listOf("com.x.y"), directDomains = listOf("bank.ir")))
        assertEquals(listOf("lan", "satra-exc-vpn", "satra-exc-vpn-apps", "satra-exc-direct", "tg", "ir", "irip"), r.map { it.id })
        assertEquals(listOf("domain:digikala.com", "domain:my.ir"), r[1].domain)
        assertEquals("proxy", r[1].outboundTag)
        assertEquals("direct", r[3].outboundTag)
    }

    @Test fun appsDroppedWhenUnsupported() =
        assertTrue(RoutingPolicy.effective(preset, true, Exceptions(vpnApps = listOf("com.x.y")), appsSupported = false).none { it.id == "satra-exc-vpn-apps" })

    @Test fun normalizesDomains() {
        assertEquals("www.bank.ir", RoutingPolicy.normalizeDomain(" https://WWW.Bank.ir/login?a=1 "))
        assertEquals("shaparak.ir", RoutingPolicy.normalizeDomain("*.shaparak.ir"))
        assertEquals("example.com", RoutingPolicy.normalizeDomain("domain:example.com:443"))
        assertEquals("xn--mgba3a4f16a.ir", RoutingPolicy.normalizeDomain("ایران.ir"))
        assertNull(RoutingPolicy.normalizeDomain("not a domain"))
        assertNull(RoutingPolicy.normalizeDomain("localhost"))
        assertNull(RoutingPolicy.normalizeDomain("-bad.com"))
        assertNull(RoutingPolicy.normalizeDomain(""))
    }

    @Test fun validatesPackages() {
        assertTrue(RoutingPolicy.isValidPackage("org.telegram.messenger"))
        assertTrue(!RoutingPolicy.isValidPackage("telegram"))
        assertTrue(!RoutingPolicy.isValidPackage("1bad.pkg"))
    }
}
