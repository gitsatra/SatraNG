package com.v2ray.ang.handler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class HybridUsageTrackerTest {

    @Test
    fun computeDisplayUsed_sumsBaselineAndDelta() {
        assertEquals(150L, HybridUsageTracker.computeDisplayUsed(100L, 50L))
        assertEquals(0L, HybridUsageTracker.computeDisplayUsed(-10L, -5L))
        assertEquals(40L, HybridUsageTracker.computeDisplayUsed(0L, 40L))
    }

    @Test
    fun computeDisplay_remainingAndFraction() {
        val usage = HybridUsageTracker.computeDisplay(
            baselineUsed = 8L * 1024 * 1024 * 1024,
            localDelta = 1L * 1024 * 1024 * 1024,
            total = 10L * 1024 * 1024 * 1024,
        )
        assertEquals(9L * 1024 * 1024 * 1024, usage.displayUsed)
        assertEquals(1L * 1024 * 1024 * 1024, usage.remaining)
        assertEquals(0.9f, usage.fractionUsed!!, 0.0001f)
    }

    @Test
    fun computeDisplay_unlimitedTotal() {
        val usage = HybridUsageTracker.computeDisplay(100L, 20L, 0L)
        assertEquals(120L, usage.displayUsed)
        assertNull(usage.remaining)
        assertNull(usage.fractionUsed)
    }
}
