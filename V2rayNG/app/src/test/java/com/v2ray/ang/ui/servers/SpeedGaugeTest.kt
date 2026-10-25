package com.v2ray.ang.ui.servers

import com.v2ray.ang.ui.main.SpeedSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedGaugeTest {
    @Test fun gaugeIsMonotonicAndClamped() {
        assertEquals(0f, gaugeFraction(0.0))
        assertEquals(1f, gaugeFraction(5000.0))
        assertTrue(gaugeFraction(10.0) < gaugeFraction(50.0))
        assertEquals(2f / 7f, gaugeFraction(10.0), 1e-6f)
        assertEquals(4.5f / 7f, gaugeFraction(75.0), 1e-6f)
    }

    @Test fun mbpsFromBytesPerSecond() = assertEquals(8.0, mbps(1_000_000), 1e-9)

    @Test fun resultsSortedFastestFirstFailuresLast() {
        val s = SpeedSession(queue = listOf("a", "b", "c"), results = listOf("a" to 100L, "b" to -1L, "c" to 900L))
        assertEquals(listOf("c", "a", "b"), s.sorted.map { it.first })
    }
}
