package com.v2ray.ang.handler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TopUpTest {
    @Test
    fun megabyteLimitsGetMegabyteSteps() {
        assertEquals(listOf(5 * TopUp.MB, 50 * TopUp.MB, 100 * TopUp.MB), TopUp.options(300 * TopUp.MB))
    }

    @Test
    fun gigabyteLimitsGetGigabyteSteps() {
        assertEquals(listOf(TopUp.GB / 2, TopUp.GB, 2 * TopUp.GB), TopUp.options(TopUp.GB))
        assertEquals(listOf(TopUp.GB / 2, TopUp.GB, 2 * TopUp.GB), TopUp.options(20 * TopUp.GB))
    }

    @Test
    fun labelsUseTheRightUnit() {
        assertTrue(TopUp.isGb(TopUp.GB / 2))
        assertFalse(TopUp.isGb(100 * TopUp.MB))
    }
}
