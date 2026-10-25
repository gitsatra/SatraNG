package com.v2ray.ang.util

/**
 * Strips secrets from log lines before they reach logcat: share links (vless://…, vmess://…),
 * credentials in URLs, subscription query tokens and UUIDs (user ids / passwords).
 */
object LogRedactor {
    private val shareLink = Regex("""\b(vless|vmess|trojan|ss|ssr|socks|hysteria2|hy2|tuic|wireguard|wg)://\S+""", RegexOption.IGNORE_CASE)
    private val userInfo = Regex("""(https?://)[^/\s@]+@""", RegexOption.IGNORE_CASE)
    private val query = Regex("""(https?://[^\s?#]+)\?[^\s]+""", RegexOption.IGNORE_CASE)
    private val uuid = Regex("""\b([0-9a-fA-F]{4})[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\b""")
    private val pathToken = Regex("""(https?://[^/\s]+/)[A-Za-z0-9_\-]{20,}""")

    fun redact(message: String): String {
        if (message.isEmpty()) return message
        var s = shareLink.replace(message) { "${it.groupValues[1]}://***" }
        s = userInfo.replace(s, "$1***@")
        s = query.replace(s, "$1?***")
        s = pathToken.replace(s, "$1***")
        s = uuid.replace(s, "$1…")
        return s
    }
}
