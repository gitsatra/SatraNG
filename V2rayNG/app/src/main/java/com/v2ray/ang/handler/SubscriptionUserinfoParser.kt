package com.v2ray.ang.handler

import com.v2ray.ang.dto.entities.SubscriptionItem

/**
 * Parses 3x-ui / Clash-compatible subscription response headers into [SubscriptionItem] fields.
 *
 * Header format:
 * `Subscription-Userinfo: upload=111; download=222; total=322122547200; expire=1893456000`
 */
object SubscriptionUserinfoParser {

    fun applyHeaders(subscription: SubscriptionItem, headers: Map<String, String>) {
        val normalized = headers.mapKeys { it.key.lowercase() }

        normalized["subscription-userinfo"]?.let { raw ->
            parseUserinfo(raw).forEach { (key, value) ->
                when (key) {
                    "upload" -> subscription.upload = value
                    "download" -> subscription.download = value
                    "total" -> subscription.total = value
                    "expire" -> subscription.expire = value
                }
            }
        }

        normalized["profile-title"]?.let { decodeMaybeBase64Title(it) }?.takeIf { it.isNotBlank() }?.let {
            subscription.profileTitle = it
        }
        normalized["announce"]?.let { raw ->
            // Panels send "base64:<text>"; an empty payload means "no announcement", never the raw prefix.
            subscription.announce = decodeMaybeBase64Title(raw).trim().takeIf { it.isNotBlank() }
        }
        normalized["support-url"]?.takeIf { it.isNotBlank() }?.let { subscription.supportUrl = it }
        normalized["profile-web-page-url"]?.takeIf { it.isNotBlank() }?.let {
            subscription.profileWebPageUrl = it
            if (subscription.supportUrl.isNullOrBlank()) subscription.supportUrl = it
        }
        // The provider's hint is kept for reference only: the refresh interval is the user's choice.
        normalized["profile-update-interval"]?.toLongOrNull()?.let { subscription.profileUpdateInterval = it }
    }

    private fun parseUserinfo(raw: String): Map<String, Long> {
        val out = linkedMapOf<String, Long>()
        raw.split(';').forEach { part ->
            val kv = part.trim().split('=', limit = 2)
            if (kv.size == 2) {
                val key = kv[0].trim().lowercase()
                val value = kv[1].trim().toLongOrNull() ?: return@forEach
                out[key] = value
            }
        }
        return out
    }

    /** Text safe to show for a stored header value (older builds saved the raw "base64:" form). */
    fun displayText(raw: String?): String = raw?.let { decodeMaybeBase64Title(it) }.orEmpty()

    /** "base64:..." values are decoded; an undecodable or empty payload yields "" (never the raw prefix). */
    internal fun decodeMaybeBase64Title(
        value: String,
        decode: (String) -> ByteArray = { android.util.Base64.decode(it, android.util.Base64.DEFAULT) },
    ): String {
        val trimmed = value.trim()
        if (trimmed.startsWith("base64:", ignoreCase = true)) {
            val b64 = trimmed.substringAfter(':').trim()
            if (b64.isEmpty()) return ""
            return runCatching { String(decode(b64), Charsets.UTF_8).trim() }.getOrDefault("")
        }
        return trimmed
    }
}
