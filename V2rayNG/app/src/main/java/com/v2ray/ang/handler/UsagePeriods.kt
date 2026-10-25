package com.v2ray.ang.handler

import java.util.Calendar
import java.util.TimeZone

enum class LimitPeriod { DAILY, WEEKLY, MONTHLY }

/**
 * Calendar maths for the usage ledger and limit windows. All boundaries are local midnight
 * (device time zone), so "today" resets exactly when the user's day rolls over.
 * Pure functions with an injectable zone so they can be unit-tested.
 */
object UsagePeriods {

    private fun cal(ms: Long, tz: TimeZone): Calendar =
        Calendar.getInstance(tz).apply { timeInMillis = ms }

    private fun Calendar.truncateToDay(): Calendar = apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }

    fun dayKey(ms: Long, tz: TimeZone = TimeZone.getDefault()): String {
        val c = cal(ms, tz)
        return "%04d%02d%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

    fun hourOfDay(ms: Long, tz: TimeZone = TimeZone.getDefault()): Int = cal(ms, tz).get(Calendar.HOUR_OF_DAY)

    fun startOfDay(ms: Long, tz: TimeZone = TimeZone.getDefault()): Long = cal(ms, tz).truncateToDay().timeInMillis

    /** The last [n] day keys ending at [endMs], oldest first. */
    fun lastDayKeys(n: Int, endMs: Long, tz: TimeZone = TimeZone.getDefault()): List<String> {
        val c = cal(endMs, tz).truncateToDay()
        val out = ArrayList<String>(n)
        c.add(Calendar.DAY_OF_YEAR, -(n - 1))
        repeat(n) {
            out += dayKey(c.timeInMillis, tz)
            c.add(Calendar.DAY_OF_YEAR, 1)
        }
        return out
    }

    /** Start-of-day millis for each key from [lastDayKeys]. */
    fun lastDayStarts(n: Int, endMs: Long, tz: TimeZone = TimeZone.getDefault()): List<Long> {
        val c = cal(endMs, tz).truncateToDay()
        c.add(Calendar.DAY_OF_YEAR, -(n - 1))
        return List(n) { val t = c.timeInMillis; c.add(Calendar.DAY_OF_YEAR, 1); t }
    }

    fun periodKey(
        period: LimitPeriod,
        ms: Long,
        tz: TimeZone = TimeZone.getDefault(),
        firstDayOfWeek: Int = Calendar.getInstance(tz).firstDayOfWeek,
    ): String = when (period) {
        LimitPeriod.DAILY -> dayKey(ms, tz)
        LimitPeriod.MONTHLY -> dayKey(ms, tz).substring(0, 6)
        LimitPeriod.WEEKLY -> {
            val c = cal(ms, tz).truncateToDay()
            while (c.get(Calendar.DAY_OF_WEEK) != firstDayOfWeek) c.add(Calendar.DAY_OF_YEAR, -1)
            "w" + dayKey(c.timeInMillis, tz)
        }
    }

    /** When the window that contains [ms] ends (exclusive), i.e. the next reset moment. */
    fun nextReset(
        period: LimitPeriod,
        ms: Long,
        tz: TimeZone = TimeZone.getDefault(),
        firstDayOfWeek: Int = Calendar.getInstance(tz).firstDayOfWeek,
    ): Long {
        val c = cal(ms, tz).truncateToDay()
        when (period) {
            LimitPeriod.DAILY -> c.add(Calendar.DAY_OF_YEAR, 1)
            LimitPeriod.MONTHLY -> { c.set(Calendar.DAY_OF_MONTH, 1); c.add(Calendar.MONTH, 1) }
            LimitPeriod.WEEKLY -> {
                c.add(Calendar.DAY_OF_YEAR, 1)
                while (c.get(Calendar.DAY_OF_WEEK) != firstDayOfWeek) c.add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        return c.timeInMillis
    }

    /** Start (inclusive) of the window that contains [ms]. */
    fun periodStart(
        period: LimitPeriod,
        ms: Long,
        tz: TimeZone = TimeZone.getDefault(),
        firstDayOfWeek: Int = Calendar.getInstance(tz).firstDayOfWeek,
    ): Long {
        val c = cal(ms, tz).truncateToDay()
        when (period) {
            LimitPeriod.DAILY -> Unit
            LimitPeriod.MONTHLY -> c.set(Calendar.DAY_OF_MONTH, 1)
            LimitPeriod.WEEKLY -> while (c.get(Calendar.DAY_OF_WEEK) != firstDayOfWeek) c.add(Calendar.DAY_OF_YEAR, -1)
        }
        return c.timeInMillis
    }
}
