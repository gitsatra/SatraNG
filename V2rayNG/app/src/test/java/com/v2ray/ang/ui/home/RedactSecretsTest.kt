package com.v2ray.ang.ui.home

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RedactSecretsTest {

    @Test
    fun redactsProxyUrlsAndPasswords() {
        val line = "open vless://uuid@host:443?security=reality password=supersecret"
        val out = redactSecrets(line)
        assertTrue(out.contains("vless://***"))
        assertTrue(out.contains("password=***"))
        assertFalse(out.contains("supersecret"))
        assertFalse(out.contains("uuid@host"))
    }
}
