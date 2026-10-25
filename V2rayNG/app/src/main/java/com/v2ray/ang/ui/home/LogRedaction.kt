package com.v2ray.ang.ui.home

/** Strip proxy URLs and credentials from Trace output. */
internal fun redactSecrets(line: String): String {
    var out = line
    out = Regex("""(?i)(password|passwd|pwd|token|secret|uuid)\s*[=:]\s*\S+""")
        .replace(out) { m -> "${m.groupValues[1]}=***" }
    out = Regex("""(?i)(vless|vmess|trojan|ss|socks|http)://\S+""")
        .replace(out, "$1://***")
    return out
}
