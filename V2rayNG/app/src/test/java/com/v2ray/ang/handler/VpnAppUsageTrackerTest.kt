package com.v2ray.ang.handler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VpnAppUsageTrackerTest {
    @Test
    fun startsAtZero_onlyGrowthAfterTheSnapshotCounts() {
        // Snapshot when the tunnel came up: app 1 already had 900 MB of history.
        val prev = hashMapOf(1 to longArrayOf(900_000_000, 50_000_000))
        val out = VpnAppUsageTracker.fold(prev, mapOf(1 to longArrayOf(900_001_000, 50_000_200)), emptySet())
        assertEquals(listOf(Triple(1, 1_000L, 200L)), out)
    }

    @Test
    fun appFirstSeenMidSessionOnlySetsBaseline() {
        val prev = hashMapOf<Int, LongArray>()
        assertTrue(VpnAppUsageTracker.fold(prev, mapOf(2 to longArrayOf(5_000, 5_000)), emptySet()).isEmpty())
        assertEquals(listOf(Triple(2, 10L, 0L)), VpnAppUsageTracker.fold(prev, mapOf(2 to longArrayOf(5_010, 5_000)), emptySet()))
    }

    @Test
    fun excludedAppsAndShrinkingCountersAreNotCounted() {
        val prev = hashMapOf(3 to longArrayOf(100, 100), 4 to longArrayOf(100, 100))
        val out = VpnAppUsageTracker.fold(prev, mapOf(3 to longArrayOf(500, 500), 4 to longArrayOf(50, 120)), setOf(3))
        assertEquals(listOf(Triple(4, 0L, 20L)), out)
    }
}
