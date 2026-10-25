package com.v2ray.ang.handler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Calendar
import java.util.TimeZone

class UsagePeriodsTest {
    private val tz = TimeZone.getTimeZone("Asia/Tehran")

    private fun at(y: Int, m: Int, d: Int, h: Int = 12, min: Int = 0): Long =
        Calendar.getInstance(tz).apply { clear(); set(y, m - 1, d, h, min) }.timeInMillis

    @Test
    fun dayKey_usesLocalMidnight() {
        // 23:59 and 00:01 land on different local days.
        assertEquals("20261019", UsagePeriods.dayKey(at(2026, 10, 19, 23, 59), tz))
        assertEquals("20261020", UsagePeriods.dayKey(at(2026, 10, 20, 0, 1), tz))
    }

    @Test
    fun lastDayKeys_oldestFirst_acrossMonthBoundary() {
        val keys = UsagePeriods.lastDayKeys(4, at(2026, 11, 2), tz)
        assertEquals(listOf("20261030", "20261031", "20261101", "20261102"), keys)
    }

    @Test
    fun periodKey_dailyWeeklyMonthly() {
        val now = at(2026, 10, 21) // Wednesday
        assertEquals("20261021", UsagePeriods.periodKey(LimitPeriod.DAILY, now, tz, Calendar.SATURDAY))
        assertEquals("202610", UsagePeriods.periodKey(LimitPeriod.MONTHLY, now, tz, Calendar.SATURDAY))
        assertEquals("w20261017", UsagePeriods.periodKey(LimitPeriod.WEEKLY, now, tz, Calendar.SATURDAY))
        assertEquals("w20261019", UsagePeriods.periodKey(LimitPeriod.WEEKLY, now, tz, Calendar.MONDAY))
    }

    @Test
    fun weeklyKey_isStableAcrossTheWeekAndChangesOnFirstDay() {
        val sat = at(2026, 10, 24, 0, 5)
        val fri = at(2026, 10, 23, 23, 55)
        assertEquals(
            UsagePeriods.periodKey(LimitPeriod.WEEKLY, at(2026, 10, 18), tz, Calendar.SATURDAY),
            UsagePeriods.periodKey(LimitPeriod.WEEKLY, fri, tz, Calendar.SATURDAY),
        )
        assertTrue(
            UsagePeriods.periodKey(LimitPeriod.WEEKLY, fri, tz, Calendar.SATURDAY) !=
                UsagePeriods.periodKey(LimitPeriod.WEEKLY, sat, tz, Calendar.SATURDAY),
        )
    }

    @Test
    fun nextReset_isLocalMidnightOfNextWindow() {
        val now = at(2026, 10, 21, 15)
        assertEquals(at(2026, 10, 22, 0), UsagePeriods.nextReset(LimitPeriod.DAILY, now, tz))
        assertEquals(at(2026, 11, 1, 0), UsagePeriods.nextReset(LimitPeriod.MONTHLY, now, tz))
        assertEquals(at(2026, 10, 24, 0), UsagePeriods.nextReset(LimitPeriod.WEEKLY, now, tz, Calendar.SATURDAY))
    }

    @Test
    fun periodStart_matchesWindowStart() {
        val now = at(2026, 10, 21, 15)
        assertEquals(at(2026, 10, 21, 0), UsagePeriods.periodStart(LimitPeriod.DAILY, now, tz))
        assertEquals(at(2026, 10, 1, 0), UsagePeriods.periodStart(LimitPeriod.MONTHLY, now, tz))
        assertEquals(at(2026, 10, 17, 0), UsagePeriods.periodStart(LimitPeriod.WEEKLY, now, tz, Calendar.SATURDAY))
    }
}
