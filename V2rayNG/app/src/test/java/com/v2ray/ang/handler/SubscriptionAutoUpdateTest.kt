package com.v2ray.ang.handler

import com.v2ray.ang.dto.entities.SubscriptionItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SubscriptionAutoUpdateTest {
    private val now = 1_800_000_000_000L

    @Test
    fun newSubscriptionsRefreshEvery15MinutesByDefault() {
        val s = SubscriptionItem(url = "https://x")
        assertTrue(s.autoUpdate)
        assertEquals(15L, s.updateInterval)
    }

    @Test
    fun launchRefreshOnlyForEnabledAutoSubsOlderThanFiveMinutes() {
        val base = SubscriptionItem(remarks = "a", url = "https://x", autoUpdate = true, enabled = true)
        assertTrue(SubscriptionUpdater.launchRefreshDue(base.copy(lastUpdated = now - 6 * 60_000L), now))
        assertTrue(SubscriptionUpdater.launchRefreshDue(base.copy(lastUpdated = -1), now))
        assertFalse(SubscriptionUpdater.launchRefreshDue(base.copy(lastUpdated = now - 60_000L), now))
        assertFalse(SubscriptionUpdater.launchRefreshDue(base.copy(enabled = false), now))
        assertFalse(SubscriptionUpdater.launchRefreshDue(base.copy(autoUpdate = false), now))
        assertFalse(SubscriptionUpdater.launchRefreshDue(base.copy(url = " "), now))
    }

    @Test
    fun providerIntervalHintNeverOverridesTheUsersChoice() {
        val s = SubscriptionItem(url = "https://x", updateInterval = 720)
        SubscriptionUserinfoParser.applyHeaders(s, mapOf("Profile-Update-Interval" to "1"))
        assertEquals(720L, s.updateInterval)
        assertEquals(1L, s.profileUpdateInterval)
    }
}
