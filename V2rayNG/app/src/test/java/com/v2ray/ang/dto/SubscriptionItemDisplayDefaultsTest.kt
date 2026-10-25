package com.v2ray.ang.dto

import com.v2ray.ang.dto.entities.SubscriptionItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SubscriptionItemDisplayDefaultsTest {

    @Test
    fun legacyGsonFalseBooleansRestoreToOn() {
        // Simulate Gson missing-field defaults (false / 0)
        val legacy = SubscriptionItem(
            displayPrefsVersion = 0,
            showUsage = false,
            showExpire = false,
            showAnnounce = false,
            showSupportLinks = false,
        )
        legacy.ensureDisplayDefaults()
        assertTrue(legacy.showUsage)
        assertTrue(legacy.showExpire)
        assertTrue(legacy.showAnnounce)
        assertTrue(legacy.showSupportLinks)
        assertEquals(1, legacy.displayPrefsVersion)
    }

    @Test
    fun versionedPrefsPreserveUserOff() {
        val item = SubscriptionItem(
            displayPrefsVersion = 1,
            showUsage = false,
            showExpire = true,
            showAnnounce = false,
            showSupportLinks = true,
        )
        item.ensureDisplayDefaults()
        assertFalse(item.showUsage)
        assertTrue(item.showExpire)
        assertFalse(item.showAnnounce)
        assertTrue(item.showSupportLinks)
    }
}
