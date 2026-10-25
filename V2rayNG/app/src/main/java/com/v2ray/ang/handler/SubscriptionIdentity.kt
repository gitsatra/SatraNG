package com.v2ray.ang.handler

import com.v2ray.ang.AppConfig.DEFAULT_SUBSCRIPTION_ID

/**
 * Decides which subscription GUID a newly created subscription should use.
 * The first real subscription claims [DEFAULT_SUBSCRIPTION_ID] instead of minting
 * a second group when only empty placeholders exist.
 */
object SubscriptionIdentity {
    data class ExistingSub(
        val id: String,
        val remarks: String,
        val url: String,
    )

    /**
     * @param requestedGuid non-blank when editing an existing subscription
     * @return id to write, or null when the caller should generate a fresh UUID
     */
    fun resolveNewId(
        requestedGuid: String,
        existing: List<ExistingSub>,
        defaultId: String = DEFAULT_SUBSCRIPTION_ID,
    ): String? {
        if (requestedGuid.isNotBlank()) return requestedGuid
        if (existing.isEmpty()) return defaultId
        if (existing.all { isPlaceholder(it, defaultId) }) {
            return if (existing.any { it.id == defaultId }) defaultId else existing.first().id
        }
        return null
    }

    fun isPlaceholder(sub: ExistingSub, defaultId: String = DEFAULT_SUBSCRIPTION_ID): Boolean {
        if (sub.url.isNotBlank()) return false
        if (sub.id == defaultId) return true
        return sub.remarks.isBlank() || sub.remarks.equals("Default", ignoreCase = true)
    }
}
