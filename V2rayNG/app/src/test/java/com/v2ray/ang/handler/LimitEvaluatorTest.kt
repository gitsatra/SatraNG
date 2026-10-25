package com.v2ray.ang.handler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LimitEvaluatorTest {
    private val mb = 1024L * 1024L
    private fun step(limit: Long, rt: RuleRuntime?, delta: Long, key: String = "20261019") =
        LimitEvaluator.step(limit, LimitPeriod.DAILY, rt, delta, 0L, key)

    @Test
    fun accumulatesAndWarnsAtEightyPercent() {
        var s = step(500 * mb, null, 100 * mb)
        assertEquals(LimitEvent.None, s.event)
        s = step(500 * mb, s.runtime, 310 * mb) // 410 MB = 82 %
        assertEquals(LimitEvent.Warned, s.event)
        assertTrue(s.runtime.warned)
        s = step(500 * mb, s.runtime, 1 * mb)
        assertEquals(LimitEvent.None, s.event) // warns once
    }

    @Test
    fun blocksExactlyOnceWhenQuotaIsUsedUp() {
        var s = step(500 * mb, null, 499 * mb)
        s = step(500 * mb, s.runtime, 1 * mb)
        assertEquals(LimitEvent.Exceeded, s.event)
        assertTrue(s.runtime.blocked)
        s = step(500 * mb, s.runtime, 5 * mb)
        assertEquals(LimitEvent.None, s.event)
        assertTrue(s.runtime.blocked)
    }

    @Test
    fun jumpingStraightPastTheLimitBlocks() {
        val s = step(100 * mb, null, 900 * mb)
        assertEquals(LimitEvent.Exceeded, s.event)
    }

    @Test
    fun bonusReleasesAndReblocksLater() {
        var s = step(100 * mb, null, 100 * mb)
        assertTrue(s.runtime.blocked)
        s = step(100 * mb, s.runtime.copy(bonus = 100 * mb), 0)
        assertEquals(LimitEvent.Released, s.event)
        assertFalse(s.runtime.blocked)
        s = step(100 * mb, s.runtime, 100 * mb)
        assertEquals(LimitEvent.Exceeded, s.event)
    }

    @Test
    fun raisingTheLimitReleases() {
        var s = step(100 * mb, null, 100 * mb)
        s = step(300 * mb, s.runtime, 0)
        assertEquals(LimitEvent.Released, s.event)
        assertFalse(s.runtime.blocked)
    }

    @Test
    fun newPeriodResetsUsageAndReleasesABlock() {
        var s = step(100 * mb, null, 100 * mb, "20261019")
        assertTrue(s.runtime.blocked)
        s = step(100 * mb, s.runtime, 3 * mb, "20261020")
        assertEquals(LimitEvent.Released, s.event)
        assertEquals(3 * mb, s.runtime.used)
        assertEquals("20261020", s.runtime.periodKey)
        assertFalse(s.runtime.blocked)
    }

    @Test
    fun negativeDeltasAreIgnored() {
        val s = step(100 * mb, null, -50)
        assertEquals(0L, s.runtime.used)
    }
}

class UsageRulesParsingTest {
    @Test
    fun normalizeDomain_handlesUrlsAndPatterns() {
        assertEquals("domain:youtube.com", UsageRulesRepository.normalizeDomain("YouTube.com"))
        assertEquals("domain:youtube.com", UsageRulesRepository.normalizeDomain("https://www.youtube.com/watch?v=1"))
        assertEquals("domain:youtube.com", UsageRulesRepository.normalizeDomain("*.youtube.com"))
        assertEquals("domain:googlevideo.com", UsageRulesRepository.normalizeDomain(" googlevideo.com:443 "))
        assertEquals("keyword:insta", UsageRulesRepository.normalizeDomain("keyword:insta"))
        assertNull(UsageRulesRepository.normalizeDomain("not a domain"))
        assertNull(UsageRulesRepository.normalizeDomain("localhost"))
        assertNull(UsageRulesRepository.normalizeDomain(""))
    }

    @Test
    fun parse_roundTripKeepsDefaultsAndDropsGarbage() {
        val json = """[{"id":"abc12345xyz","kind":"DOMAIN","label":"YT","domains":["youtube.com","bad domain"],"limitBytes":1073741824},
            {"id":"","kind":"APP"},{"id":"zzz","kind":"WHAT"}]"""
        val rules = UsageRulesRepository.parse(json)
        assertEquals(1, rules.size)
        val r = rules.first()
        assertEquals(listOf("domain:youtube.com"), r.domains)
        assertEquals(LimitPeriod.DAILY, r.period)
        assertTrue(r.enabled)
        assertEquals("lim-abc12345", r.outboundTag)
        assertFalse(r.alwaysBlock)
    }

    @Test
    fun parse_survivesBrokenJson() {
        assertEquals(0, UsageRulesRepository.parse("{{{").size)
        assertEquals(0, UsageRulesRepository.parse(null).size)
    }

    @Test
    fun zeroLimitMeansAlwaysBlock() {
        assertTrue(UsageRule(id = "a", limitBytes = 0).alwaysBlock)
    }
}
