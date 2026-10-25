package com.v2ray.ang.handler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AutoConnectControllerTest {

    @Test
    fun userStopBlocksReconnect() {
        assertFalse(
            AutoConnectController.shouldReconnect(
                autoConnect = true,
                userRequestedStop = true,
                unexpectedDrop = true,
            )
        )
    }

    @Test
    fun unexpectedDropSchedulesWhenAutoOn() {
        assertTrue(
            AutoConnectController.shouldReconnect(
                autoConnect = true,
                userRequestedStop = false,
                unexpectedDrop = true,
            )
        )
    }

    @Test
    fun autoOffUsesSelectedGuidOnly() {
        val best = AutoConnectController.selectBestGuid(
            candidates = listOf("a", "b", "c"),
            samples = listOf(
                AutoConnectController.PingSample("a", 50, System.currentTimeMillis()),
                AutoConnectController.PingSample("b", 10, System.currentTimeMillis()),
            ),
            selectedGuid = "c",
            autoConnect = false,
        )
        assertEquals("c", best)
    }

    @Test
    fun autoOnPicksLowestFreshPing() {
        val now = System.currentTimeMillis()
        val best = AutoConnectController.selectBestGuid(
            candidates = listOf("a", "b", "c"),
            samples = listOf(
                AutoConnectController.PingSample("a", 80, now),
                AutoConnectController.PingSample("b", 20, now),
                AutoConnectController.PingSample("c", 40, now),
            ),
            selectedGuid = "a",
            autoConnect = true,
        )
        assertEquals("b", best)
    }

    @Test
    fun backoffCapsAtThreeAttempts() {
        assertEquals(1000L, AutoConnectController.backoffMsForAttempt(0))
        assertEquals(3000L, AutoConnectController.backoffMsForAttempt(1))
        assertEquals(8000L, AutoConnectController.backoffMsForAttempt(2))
        assertNull(AutoConnectController.backoffMsForAttempt(3))
    }

    @Test
    fun clearStopOnUserStart() {
        assertFalse(AutoConnectController.clearStopOnUserStart(true, true))
        assertTrue(AutoConnectController.clearStopOnUserStart(false, true))
    }
}
