package com.v2ray.ang

import com.v2ray.ang.core.defaultFingerprint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FingerprintDefaultTest {
    @Test fun keepsConfigValue() = assertEquals("firefox", defaultFingerprint("firefox", "tls"))
    @Test fun blankTlsGetsChrome() = assertEquals("chrome", defaultFingerprint("", "tls"))
    @Test fun realityGetsChrome() = assertEquals("chrome", defaultFingerprint(null, "reality"))
    @Test fun plainGetsNone() = assertNull(defaultFingerprint(null, null))
}
