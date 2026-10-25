package com.v2ray.ang.handler

/**
 * Chooses the server Smart Connect uses. Pure, unit tested.
 *
 * Every config gets a first real-delay measurement; the quickest few are measured again right
 * away. A candidate that fails the retest is dropped (it is flaky right now). The score is the
 * average of both measurements plus half their difference, so a steady 120 ms beats a server that
 * jumps between 60 and 300 ms. Servers that recently carried a working connection get a small
 * bonus, so we do not hop between near-equal servers.
 */
object SmartPick {
    const val RETEST_COUNT = 3
    const val RECENT_OK_MS = 3L * 24 * 60 * 60 * 1000
    private const val RECENT_OK_BONUS = 0.85

    data class Candidate(
        val guid: String,
        val firstMs: Long,
        /** Null when not retested; <= 0 when the retest failed. */
        val retestMs: Long? = null,
        val lastOkAt: Long = 0L,
    )

    /** The configs worth a second measurement: the quickest answers of the first pass. */
    fun retestSet(first: Map<String, Long>, count: Int = RETEST_COUNT): List<String> =
        first.filterValues { it > 0L }.entries.sortedBy { it.value }.take(count).map { it.key }

    fun score(c: Candidate, nowMs: Long): Double? {
        if (c.firstMs <= 0L) return null
        val base = when (val r = c.retestMs) {
            null -> c.firstMs.toDouble()
            else -> if (r <= 0L) return null else (c.firstMs + r) / 2.0 + kotlin.math.abs(c.firstMs - r) / 2.0
        }
        val recent = c.lastOkAt > 0L && nowMs - c.lastOkAt in 0..RECENT_OK_MS
        return if (recent) base * RECENT_OK_BONUS else base
    }

    /**
     * Best candidate. Retested ones are preferred when any of them passed; if every retest failed,
     * the best first-pass answer is used rather than giving up.
     */
    fun pick(candidates: List<Candidate>, nowMs: Long): Candidate? {
        val retested = candidates.filter { it.retestMs != null }
        val pool = retested.filter { score(it, nowMs) != null }.ifEmpty { candidates.map { it.copy(retestMs = null) } }
        return pool.mapNotNull { c -> score(c, nowMs)?.let { c to it } }.minByOrNull { it.second }?.first
    }
}
