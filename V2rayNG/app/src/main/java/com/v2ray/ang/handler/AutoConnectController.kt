package com.v2ray.ang.handler

/**
 * Pure Auto-connect / reconnect policy for SatraNG Liquid Glass.
 * Owns decision logic only — callers perform IO / service start.
 */
object AutoConnectController {

    const val PING_FRESH_MS = 15L * 60 * 1000
    const val MAX_RECONNECT_ATTEMPTS = 3
    val BACKOFF_MS = longArrayOf(1_000L, 3_000L, 8_000L)

    data class PingSample(
        val guid: String,
        val delayMs: Long,
        val measuredAtMs: Long,
    )

    /**
     * Pick best guid among [candidates] using fresh positive pings; else first candidate.
     */
    fun selectBestGuid(
        candidates: List<String>,
        samples: List<PingSample>,
        nowMs: Long = System.currentTimeMillis(),
        selectedGuid: String? = null,
        autoConnect: Boolean,
    ): String? {
        if (candidates.isEmpty()) return null
        if (!autoConnect) {
            return selectedGuid?.takeIf { it in candidates } ?: candidates.firstOrNull()
        }
        val fresh = samples
            .filter { it.guid in candidates && it.delayMs > 0 && nowMs - it.measuredAtMs <= PING_FRESH_MS }
            .minByOrNull { it.delayMs }
        return fresh?.guid ?: candidates.firstOrNull()
    }

    /** User stop must block unexpected reconnect. */
    fun shouldReconnect(
        autoConnect: Boolean,
        userRequestedStop: Boolean,
        unexpectedDrop: Boolean,
    ): Boolean = autoConnect && unexpectedDrop && !userRequestedStop

    fun backoffMsForAttempt(attemptIndexZeroBased: Int): Long? {
        if (attemptIndexZeroBased < 0 || attemptIndexZeroBased >= MAX_RECONNECT_ATTEMPTS) return null
        return BACKOFF_MS[attemptIndexZeroBased]
    }

    fun nextUserStopFlag(userToggledOff: Boolean): Boolean = userToggledOff

    fun clearStopOnUserStart(userRequestedStart: Boolean, previousStop: Boolean): Boolean =
        if (userRequestedStart) false else previousStop
}
