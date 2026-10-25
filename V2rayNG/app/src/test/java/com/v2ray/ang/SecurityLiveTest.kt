package com.v2ray.ang

import com.v2ray.ang.handler.SecurityCheck
import com.v2ray.ang.handler.SecurityCheck.Item
import com.v2ray.ang.handler.SecurityCheck.Status
import com.v2ray.ang.ui.security.RowPhase
import com.v2ray.ang.ui.security.rowPhase
import com.v2ray.ang.ui.security.whyRes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityLiveTest {
    @Test fun maxPointsSumTo100() = assertEquals(100, Item.entries.sumOf { SecurityCheck.maxPoints(it) })

    @Test fun pointsPerStatus() {
        assertEquals(30, SecurityCheck.points(SecurityCheck.Finding(Item.DnsLeak, Status.Pass)))
        assertEquals(15, SecurityCheck.points(SecurityCheck.Finding(Item.DnsLeak, Status.Warn)))
        assertEquals(0, SecurityCheck.maxPoints(Item.KillSwitch))
        assertEquals(0, SecurityCheck.points(SecurityCheck.Finding(Item.DnsLeak, Status.Fail)))
    }

    @Test fun runningRowIsFirstUnmeasured() {
        assertEquals(RowPhase.Running, rowPhase(Item.ExitIp, emptySet(), emptySet(), true))
        assertEquals(RowPhase.Pending, rowPhase(Item.DnsLeak, emptySet(), emptySet(), true))
        assertEquals(RowPhase.Running, rowPhase(Item.DnsLeak, setOf(Item.ExitIp), emptySet(), true))
        assertEquals(RowPhase.Done, rowPhase(Item.ExitIp, setOf(Item.ExitIp), setOf(Item.ExitIp), true))
        assertEquals(RowPhase.Pending, rowPhase(Item.Ipv6, emptySet(), emptySet(), false))
    }

    @Test fun orderCoversEveryItem() = assertEquals(Item.entries.toSet(), SecurityCheck.ORDER.toSet())

    @Test fun oneTapOnlyForSafeFixes() {
        assertTrue(SecurityCheck.isOneTap(SecurityCheck.Fix.UseRemoteDns))
        assertTrue(SecurityCheck.isOneTap(SecurityCheck.Fix.DisableInsecure))
        assertTrue(!SecurityCheck.isOneTap(SecurityCheck.Fix.EnableKillSwitch))
        assertTrue(!SecurityCheck.isOneTap(SecurityCheck.Fix.ChooseOtherServer))
    }

    @Test fun whyExplainsProxyMode() {
        assertEquals(R.string.sx_sec_y_proxy, whyRes(Item.WebRtc, Status.Warn, vpn = false))
        assertEquals(R.string.sx_sec_y_dns_fail, whyRes(Item.DnsLeak, Status.Fail, vpn = true))
        assertEquals(R.string.sx_sec_y_unknown, whyRes(Item.ExitIp, Status.Unknown, vpn = true))
    }
}
