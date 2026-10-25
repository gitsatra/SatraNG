package com.v2ray.ang.handler

import com.v2ray.ang.handler.SmartPick.Candidate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SmartPickTest {
    private val now = 1_000_000_000_000L

    @Test
    fun retestSet_takesQuickestAnswersOnly() {
        assertEquals(listOf("b", "c"), SmartPick.retestSet(mapOf("a" to -1L, "b" to 50L, "c" to 80L, "d" to 0L), 2))
    }

    @Test
    fun pick_prefersSteadyOverJumpy() {
        val steady = Candidate("steady", 120, 125)
        val jumpy = Candidate("jumpy", 60, 300)
        assertEquals("steady", SmartPick.pick(listOf(steady, jumpy), now)?.guid)
    }

    @Test
    fun pick_dropsFailedRetest() {
        val flaky = Candidate("flaky", 40, -1)
        val ok = Candidate("ok", 90, 95)
        assertEquals("ok", SmartPick.pick(listOf(flaky, ok), now)?.guid)
    }

    @Test
    fun pick_fallsBackToFirstPassWhenAllRetestsFail() {
        val a = Candidate("a", 70, -1)
        val b = Candidate("b", 50, -1)
        assertEquals("b", SmartPick.pick(listOf(a, b), now)?.guid)
    }

    @Test
    fun pick_recentSuccessBreaksNearTies() {
        val known = Candidate("known", 100, 100, lastOkAt = now - 3_600_000)
        val other = Candidate("other", 92, 92)
        assertEquals("known", SmartPick.pick(listOf(known, other), now)?.guid)
        val old = known.copy(lastOkAt = now - 10L * 86_400_000)
        assertEquals("other", SmartPick.pick(listOf(old, other), now)?.guid)
    }

    @Test
    fun pick_nothingAnswered() {
        assertNull(SmartPick.pick(listOf(Candidate("a", -1)), now))
        assertNull(SmartPick.pick(emptyList(), now))
    }
}
