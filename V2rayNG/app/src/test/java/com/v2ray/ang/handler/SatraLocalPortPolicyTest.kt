package com.v2ray.ang.handler

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SatraLocalPortPolicyTest {

    @Test
    fun shouldReplaceWithDefault_replacesMissingAndLegacy() {
        assertTrue(SatraLocalPortPolicy.shouldReplaceWithDefault(null, "10808"))
        assertTrue(SatraLocalPortPolicy.shouldReplaceWithDefault("", "10808"))
        assertTrue(SatraLocalPortPolicy.shouldReplaceWithDefault("10808", "10808"))
    }

    @Test
    fun shouldReplaceWithDefault_keepsCustomPort() {
        assertFalse(SatraLocalPortPolicy.shouldReplaceWithDefault("28888", "10808"))
        assertFalse(SatraLocalPortPolicy.shouldReplaceWithDefault("12345", "10808"))
    }

    @Test
    fun shouldReplaceMtu_replacesStockDefault() {
        assertTrue(SatraLocalPortPolicy.shouldReplaceMtu(null))
        assertTrue(SatraLocalPortPolicy.shouldReplaceMtu(""))
        assertTrue(SatraLocalPortPolicy.shouldReplaceMtu("1500"))
        assertFalse(SatraLocalPortPolicy.shouldReplaceMtu("1420"))
        assertFalse(SatraLocalPortPolicy.shouldReplaceMtu("1280"))
    }
}
