package com.v2ray.ang

import com.v2ray.ang.dto.entities.RulesetItem
import com.v2ray.ang.handler.RoutingPolicy
import com.v2ray.ang.ui.routing.ExceptionKind
import com.v2ray.ang.ui.routing.validateException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutingExceptionsTest {
    @Test fun domainKindsNormalize() {
        assertEquals("bank.ir", validateException(ExceptionKind.VpnDomain, "https://bank.ir/"))
        assertEquals("shaparak.ir", validateException(ExceptionKind.DirectDomain, "*.shaparak.ir"))
        assertNull(validateException(ExceptionKind.DirectDomain, "bad domain!"))
    }

    @Test fun appKindNeedsPackage() {
        assertEquals("org.telegram.messenger", validateException(ExceptionKind.VpnApp, " org.telegram.messenger "))
        assertNull(validateException(ExceptionKind.VpnApp, "https://bank.ir"))
    }

    @Test fun regionFromPreset() {
        assertTrue(RoutingPolicy.isRussia(listOf(RulesetItem(ip = listOf("geoip:ru"), outboundTag = "direct"))))
        assertTrue(!RoutingPolicy.isRussia(listOf(RulesetItem(ip = listOf("geoip:ir"), outboundTag = "direct"))))
        assertTrue(!RoutingPolicy.isRussia(null))
    }
}
