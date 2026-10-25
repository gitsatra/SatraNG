package com.v2ray.ang.handler

import com.v2ray.ang.dto.entities.RulesetItem
import com.v2ray.ang.enums.RoutingType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Locale

class RoutingPresetsTest {
    private val china = listOf(
        RulesetItem(id = "a", remarks = "Direct cn", outboundTag = "direct", domain = listOf("geosite:cn")),
        RulesetItem(id = "b", remarks = "Proxy rest", outboundTag = "proxy", port = "0-65535"),
    )

    @Test
    fun untouchedLegacyPresetIsDetected_idsAndLockedRulesIgnored() {
        val current = listOf(RulesetItem(id = "lock", remarks = "mine", outboundTag = "block", locked = true)) +
            china.map { it.copy(id = it.id + "-x") }
        assertTrue(RoutingPresets.isUnmodified(current, china))
    }

    @Test
    fun editedListIsKept() {
        val edited = china.toMutableList().also { it[0] = it[0].copy(enabled = false) }
        assertFalse(RoutingPresets.isUnmodified(edited, china))
        assertFalse(RoutingPresets.isUnmodified(china + RulesetItem(remarks = "extra", outboundTag = "direct"), china))
    }

    @Test
    fun defaultFollowsLocale() {
        assertEquals(RoutingType.WHITE_RUSSIA, RoutingPresets.defaultFor(Locale("ru", "RU")))
        assertEquals(RoutingType.WHITE_RUSSIA, RoutingPresets.defaultFor(Locale("en", "KZ")))
        assertEquals(RoutingType.WHITE_IRAN, RoutingPresets.defaultFor(Locale("fa", "IR")))
        assertEquals(RoutingType.WHITE_IRAN, RoutingPresets.defaultFor(Locale("en", "US")))
    }
}
