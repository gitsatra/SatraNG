package com.v2ray.ang.handler

/**
 * Pure policy for SatraNG local SOCKS/DNS port defaults and legacy migration.
 */
object SatraLocalPortPolicy {

    /**
     * Whether a stored local port should be replaced with the SatraNG default.
     * Empty/missing and stock v2rayNG legacy values are replaced; any other custom value is kept.
     */
    fun shouldReplaceWithDefault(stored: String?, legacy: String): Boolean {
        return stored.isNullOrEmpty() || stored == legacy
    }

    /**
     * Whether a stored VPN MTU should be replaced with the SatraNG default.
     */
    fun shouldReplaceMtu(stored: String?, legacyMtu: String = "1500"): Boolean {
        return stored.isNullOrEmpty() || stored == legacyMtu
    }
}
