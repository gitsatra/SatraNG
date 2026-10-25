package com.v2ray.ang.handler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ImportClassifierTest {
    @Test fun emptyAndNoise() {
        assertTrue(ImportClassifier.classify(null).isEmpty)
        assertTrue(ImportClassifier.classify("   ").isEmpty)
        assertTrue(ImportClassifier.classify("hello there, this is not a config").isEmpty)
    }

    @Test fun subscriptionLink() {
        val k = ImportClassifier.classify("https://sub.example.com/api/v1/client/subscribe?token=abc")
        assertEquals(1, k.subscriptions)
        assertEquals(0, k.configs)
    }

    @Test fun singleAndMultipleConfigs() {
        assertEquals(1, ImportClassifier.classify("vless://uuid@1.2.3.4:443?security=tls#DE").configs)
        val many = "vmess://eyJhZGQiOiIxIn0=\ntrojan://pw@h.example:443#x\nss://YWVzOnB3@h:8388#y"
        assertEquals(3, ImportClassifier.classify(many).configs)
    }

    @Test fun httpProxyWithCredentialsIsAConfigNotASubscription() {
        val k = ImportClassifier.classify("http://user:pass@1.2.3.4:8080")
        assertEquals(0, k.subscriptions)
        assertEquals(1, k.configs)
    }

    @Test fun base64Bundle() {
        val bundle = java.util.Base64.getEncoder().encodeToString("vless://a@b:1#x\ntrojan://p@c:2#y".toByteArray())
        assertEquals(2, ImportClassifier.classify(bundle).configs)
    }

    @Test fun jsonConfig() {
        assertTrue(ImportClassifier.classify("{\"outbounds\":[{\"protocol\":\"freedom\"}]}").json)
    }

    @Test fun mixedSubAndConfigs() {
        val k = ImportClassifier.classify("https://a.example/sub\nvless://u@h:443#n")
        assertEquals(1, k.subscriptions)
        assertEquals(1, k.configs)
    }
}
