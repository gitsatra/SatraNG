package com.v2ray.ang.dto.entities

data class SubscriptionItem(
    var remarks: String = "",
    var url: String = "",
    var enabled: Boolean = true,
    val addedTime: Long = System.currentTimeMillis(),
    var lastUpdated: Long = -1,
    var autoUpdate: Boolean = true,
    var updateInterval: Long = 15, // in minutes; SatraNG refreshes every 15 minutes by default
    var prevProfile: String? = null,
    var nextProfile: String? = null,
    var filter: String? = null,
    var allowInsecureUrl: Boolean = false,
    var userAgent: String? = null,
    var requestHeaders: String? = null,

    // 3x-ui / standard subscription headers (Subscription-Userinfo)
    var upload: Long = 0L,
    var download: Long = 0L,
    var total: Long = 0L, // 0 = unlimited
    var expire: Long = 0L, // unix seconds; 0 = no expiry
    var profileTitle: String? = null,
    var announce: String? = null,
    var supportUrl: String? = null,
    var profileWebPageUrl: String? = null,
    var profileUpdateInterval: Long = 0L, // hours from Profile-Update-Interval

    /**
     * Per-sub Home display toggles (Liquid Glass 2D).
     * Gson defaults missing booleans to false; [displayPrefsVersion] gates migration
     * so older stored JSON keeps prior “show everything” UX.
     */
    var displayPrefsVersion: Int = 0,
    var showUsage: Boolean = true,
    var showExpire: Boolean = true,
    var showAnnounce: Boolean = true,
    var showSupportLinks: Boolean = true,
) {
    /** Apply after Gson decode so missing toggles stay ON for legacy records. */
    fun ensureDisplayDefaults(): SubscriptionItem {
        if (displayPrefsVersion < 1) {
            showUsage = true
            showExpire = true
            showAnnounce = true
            showSupportLinks = true
            displayPrefsVersion = 1
        }
        return this
    }

    fun usedBytes(): Long = (upload + download).coerceAtLeast(0L)

    fun remainingBytes(): Long? {
        if (total <= 0L) return null
        return (total - usedBytes()).coerceAtLeast(0L)
    }

    fun usageFraction(): Float? {
        if (total <= 0L) return null
        return (usedBytes().toDouble() / total.toDouble()).toFloat().coerceIn(0f, 1f)
    }

    fun isNearQuota(threshold: Float = 0.9f): Boolean {
        val f = usageFraction() ?: return false
        return f >= threshold
    }

    fun isNearExpire(withinMs: Long = 3L * 24 * 60 * 60 * 1000): Boolean {
        if (expire <= 0L) return false
        val expireMs = if (expire > 10_000_000_000L) expire else expire * 1000L
        val left = expireMs - System.currentTimeMillis()
        return left in 1 until withinMs
    }

    fun isExpired(): Boolean {
        if (expire <= 0L) return false
        val expireMs = if (expire > 10_000_000_000L) expire else expire * 1000L
        return System.currentTimeMillis() >= expireMs
    }
}
