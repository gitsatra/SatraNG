package com.v2ray.ang.handler

import com.v2ray.ang.dto.entities.RulesetItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IranAppsTest {
    private val installed = listOf(
        "com.farsitel.bazaar" to "Bazaar",
        "ir.mci.ecareapp" to null,
        "com.example.bank" to "بانک نمونه",
        "org.telegram.messenger" to "تلگرام",
        "com.google.android.youtube" to "YouTube",
        "com.example.shop.ir" to "Shop",
        "com.satra.ng" to "Satra",
    )

    @Test fun heuristics() {
        assertTrue(IranApps.looksIranian("ir.bank.x", null))
        assertTrue(IranApps.looksIranian("com.x.ir", null))
        assertTrue(IranApps.looksIranian("com.ir.pay", null))
        assertTrue(IranApps.looksIranian("com.x", "بانک"))
        assertFalse(IranApps.looksIranian("com.iris.app", null))
        assertFalse(IranApps.looksIranian("org.telegram.messenger", "تلگرام"))
    }

    @Test fun bypassSetCombinesCuratedHeuristicAndEdits() {
        val s = IranApps.bypassSet(installed, added = listOf("com.google.android.youtube", "not.installed"), removed = listOf("com.example.shop.ir"), alwaysVpn = listOf("ir.mci.ecareapp"), self = "com.satra.ng")
        assertEquals(setOf("com.farsitel.bazaar", "com.example.bank"), s)
    }

    @Test fun selfAndTelegramNeverBypassed() {
        val s = IranApps.bypassSet(installed, listOf("com.satra.ng", "org.telegram.messenger"), emptyList(), emptyList(), "com.satra.ng")
        assertFalse("com.satra.ng" in s); assertFalse("org.telegram.messenger" in s)
    }

    @Test fun curatedHasBanksAndStores() {
        assertTrue("com.farsitel.bazaar" in IranApps.CURATED)
        assertTrue(IranApps.CURATED.size > 50)
    }

    @Test fun iranServicesRuleOnlyWhenSmartIran() {
        val stored = listOf(RulesetItem(remarks = "x", outboundTag = "direct", domain = listOf("domain:ir")))
        val on = RoutingPolicy.effective(stored, true, RoutingPolicy.Exceptions(), true, iran = true)
        assertTrue(on.any { it.id == "satra-ir-services" && "domain:shaparak.ir" in it.domain.orEmpty() })
        assertFalse(RoutingPolicy.effective(stored, false, RoutingPolicy.Exceptions(), true, iran = true).any { it.id == "satra-ir-services" })
        assertFalse(RoutingPolicy.effective(stored, true, RoutingPolicy.Exceptions(), true, iran = false).any { it.id == "satra-ir-services" })
    }

    @Test fun regionDetection() {
        val cn = listOf(RulesetItem(remarks = "c", outboundTag = "direct", ip = listOf("geoip:cn")))
        assertTrue(RoutingPolicy.isChina(cn)); assertFalse(RoutingPolicy.isIran(cn))
        assertTrue(RoutingPolicy.isIran(emptyList()))
    }
    @Test fun storeInstalledGlobalVendorsStayOnVpn() {
        org.junit.Assert.assertFalse(IranApps.looksIranian("com.google.android.keep", "Keep", "com.farsitel.bazaar"))
        org.junit.Assert.assertFalse(IranApps.looksIranian("com.microsoft.rdc.android", "Remote Desktop", "ir.mservices.market"))
        org.junit.Assert.assertTrue(IranApps.looksIranian("com.digikala", "Digikala", "com.farsitel.bazaar"))
    }
}
