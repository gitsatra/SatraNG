package com.v2ray.ang

import com.v2ray.ang.ui.home.exitChipLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExitIpChipTest {
    @Test fun flagThenIsolatedIp() {
        val s = exitChipLabel("203.0.113.7", "NL")
        assertTrue(s.startsWith("\uD83C\uDDF3\uD83C\uDDF1 "))
        assertTrue(s.endsWith("\u2066203.0.113.7\u2069"))
    }
    @Test fun unknownCountryHasNoFlag() {
        assertEquals("\u2066203.0.113.7\u2069", exitChipLabel("203.0.113.7", null))
        assertEquals("\u2066203.0.113.7\u2069", exitChipLabel("203.0.113.7", "X"))
    }
    @Test fun ipv6StaysIsolated() {
        assertTrue(exitChipLabel("2001:db8::1", "DE").contains("\u20662001:db8::1\u2069"))
    }
}
