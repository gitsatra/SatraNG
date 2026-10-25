package com.v2ray.ang.handler

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When an APP/CATEGORY quota is exceeded, keep-alives must die (TUN recreate).
 * DOMAIN-only soft reload is enough; thrashing kills are rate-limited in the sampler.
 */
class PolicySocketKillTest {

    @Test
    fun appExceedNeedsSocketKill() {
        assertTrue(PolicyEnforcement.needsSocketKill(matchesApps = true, stillFlowingBlocked = false))
    }

    @Test
    fun domainExceedSoftReloadOnly() {
        assertFalse(PolicyEnforcement.needsSocketKill(matchesApps = false, stillFlowingBlocked = false))
    }

    @Test
    fun blockedUidStillFlowingNeedsSocketKill() {
        assertTrue(PolicyEnforcement.needsSocketKill(matchesApps = false, stillFlowingBlocked = true))
    }
}
