package com.v2ray.ang.handler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.Base64

class SubscriptionHeaderTextTest {
    private val jvm: (String) -> ByteArray = { Base64.getMimeDecoder().decode(it) }
    private fun b64(s: String) = Base64.getEncoder().encodeToString(s.toByteArray())

    @Test
    fun emptyBase64PayloadIsNoText_neverTheRawPrefix() {
        assertEquals("", SubscriptionUserinfoParser.decodeMaybeBase64Title("base64:", jvm))
        assertEquals("", SubscriptionUserinfoParser.decodeMaybeBase64Title("  base64:  ", jvm))
    }

    @Test
    fun base64PayloadIsDecodedAsUtf8() {
        assertEquals("سلام دوستان", SubscriptionUserinfoParser.decodeMaybeBase64Title("base64:" + b64("سلام دوستان"), jvm))
        assertEquals("Hi", SubscriptionUserinfoParser.decodeMaybeBase64Title("BASE64:" + b64("Hi"), jvm))
    }

    @Test
    fun plainTextIsKeptAndBrokenPayloadIsDropped() {
        assertEquals("Welcome", SubscriptionUserinfoParser.decodeMaybeBase64Title(" Welcome ", jvm))
        assertEquals("", SubscriptionUserinfoParser.decodeMaybeBase64Title("base64:%%%", jvm))
    }
}
