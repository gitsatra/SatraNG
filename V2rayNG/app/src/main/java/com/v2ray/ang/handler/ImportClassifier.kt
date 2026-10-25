package com.v2ray.ang.handler

/**
 * Looks at pasted text before importing so the one-tap "Add" can say what it is adding:
 * subscription links, single configs (vless://, vmess://, trojan://, ss://, …), a base64 bundle
 * of configs, or a full JSON config. Pure Kotlin so it is unit tested.
 */
object ImportClassifier {

    data class Kind(val subscriptions: Int = 0, val configs: Int = 0, val json: Boolean = false) {
        val isEmpty: Boolean get() = subscriptions == 0 && configs == 0 && !json
    }

    private val schemes = listOf(
        "vmess", "vless", "trojan", "ss", "ssr", "socks", "socks5", "http", "https",
        "hysteria", "hysteria2", "hy2", "tuic", "wireguard", "wg", "anytls",
    )
    private val configLink = Regex("^(${schemes.joinToString("|")})://\\S+", RegexOption.IGNORE_CASE)
    private val webLink = Regex("^https?://[^\\s/]+\\S*$", RegexOption.IGNORE_CASE)

    fun classify(text: String?): Kind {
        val raw = text?.trim().orEmpty()
        if (raw.isEmpty()) return Kind()
        if ((raw.startsWith("{") || raw.startsWith("[")) && raw.contains("\"outbounds\"")) return Kind(json = true)
        val direct = countLines(raw)
        if (!direct.isEmpty) return direct
        val decoded = decodeBase64(raw) ?: return Kind()
        return countLines(decoded).let { Kind(configs = it.configs) } // a bundle never contains sub links
    }

    private fun countLines(text: String): Kind {
        var subs = 0
        var configs = 0
        text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.forEach { line ->
            when {
                // A plain web link is a subscription; proxy links with http(s) schemes carry a userinfo/port.
                webLink.matches(line) && !line.substringAfter("://").substringBefore('/').contains('@') -> subs++
                configLink.containsMatchIn(line) -> configs++
            }
        }
        return Kind(subscriptions = subs, configs = configs)
    }

    private fun decodeBase64(text: String): String? {
        val compact = text.filterNot { it.isWhitespace() }
        if (compact.length < 8 || !compact.all { it.isLetterOrDigit() || it in "+/=-_" }) return null
        val padded = compact.trimEnd('=').let { it + "=".repeat((4 - it.length % 4) % 4) }
        return runCatching { String(java.util.Base64.getDecoder().decode(padded)) }.getOrNull()
            ?: runCatching { String(java.util.Base64.getUrlDecoder().decode(padded)) }.getOrNull()
    }
}
