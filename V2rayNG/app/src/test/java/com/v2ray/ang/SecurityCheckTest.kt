package com.v2ray.ang

import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.handler.SecurityCheck
import com.v2ray.ang.handler.SecurityCheck.Item
import com.v2ray.ang.handler.SecurityCheck.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.v2ray.ang.ui.home.securityBand

class SecurityCheckTest {
    private val good = SecurityCheck.Facts(
        connected = true, vpnMode = true, exitIp = "5.6.7.8", exitCountry = "NL", directCountry = "IR",
        resolverIp = "172.70.1.1", resolverCountry = "NL", ipv6Tunneled = true, ipv6Exit = null,
        configType = EConfigType.VLESS, security = "reality", insecure = false, alwaysOn = true, lockdown = true,
    )

    private fun status(r: SecurityCheck.Report, i: Item) = r.findings.first { it.item == i }.status

    @Test fun cleanConnectionScoresFull() {
        val r = SecurityCheck.evaluate(good)
        assertEquals(100, r.score)
        assertTrue(r.fixes.isEmpty())
    }

    @Test fun resolverInHomeCountryIsDnsLeak() {
        val r = SecurityCheck.evaluate(good.copy(resolverCountry = "IR"))
        assertEquals(Status.Fail, status(r, Item.DnsLeak))
        assertTrue(SecurityCheck.Fix.UseRemoteDns in r.fixes)
    }

    @Test fun insecureTlsFails() {
        val r = SecurityCheck.evaluate(good.copy(security = "tls", insecure = true))
        assertEquals(Status.Fail, status(r, Item.Encryption))
        assertTrue(SecurityCheck.Fix.DisableInsecure in r.fixes)
    }

    @Test fun plainVlessFailsEncryption() =
        assertEquals(Status.Fail, status(SecurityCheck.evaluate(good.copy(security = "none")), Item.Encryption))

    @Test fun killSwitchIsInfoOnly() {
        val r = SecurityCheck.evaluate(good.copy(lockdown = false, alwaysOn = false))
        assertEquals(Status.Warn, status(r, Item.KillSwitch))
        assertTrue(SecurityCheck.Fix.EnableKillSwitch !in r.fixes)
        assertEquals(100, r.score) // optional: never costs points
    }

    @Test fun noExitFails() =
        assertEquals(Status.Fail, status(SecurityCheck.evaluate(good.copy(exitIp = null, exitCountry = null)), Item.ExitIp))

    @Test fun notConnectedAsksToConnect() {
        val r = SecurityCheck.evaluate(good.copy(connected = false))
        assertEquals(listOf(SecurityCheck.Fix.Connect), r.fixes)
        assertEquals(0, r.score)
    }

    @Test fun parsers() {
        assertEquals("1.2.3.4" to "NL", SecurityCheck.parseIpCountry("""{"ip":"1.2.3.4","country_code":"NL"}"""))
        assertEquals("1.2.3.4" to "SE", SecurityCheck.parseIpCountry("""{"countryCode":"SE","query":"1.2.3.4"}"""))
        assertEquals("9.9.9.9" to "NL", SecurityCheck.parseResolver("""{"dns":{"geo":"Netherlands - Cloudflare","ip":"9.9.9.9"}}"""))
    }

    @Test fun chipBands() {
        assertEquals(2, securityBand(100)); assertEquals(2, securityBand(85))
        assertEquals(1, securityBand(84)); assertEquals(1, securityBand(60))
        assertEquals(0, securityBand(59))
    }

    @Test fun autoRunWithoutDirectWarnsOnFarResolver() {
        val r = SecurityCheck.evaluate(good.copy(directCountry = null, resolverCountry = "DE"))
        assertEquals(Status.Warn, status(r, Item.DnsLeak))
        assertEquals(Status.Pass, status(SecurityCheck.evaluate(good.copy(directCountry = null)), Item.DnsLeak))
    }
}
