package com.v2ray.ang

import com.v2ray.ang.util.LogRedactor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LogRedactorTest {
    @Test fun shareLinkHidden() =
        assertEquals("got vless://*** ok", LogRedactor.redact("got vless://1b2c3d4e-0000-1111-2222-333344445555@1.2.3.4:443?security=reality#x ok"))

    @Test fun subTokenHidden() =
        assertEquals("Failed: https://sub.example.com/link?***", LogRedactor.redact("Failed: https://sub.example.com/link?token=abcdef123"))

    @Test fun pathTokenHidden() =
        assertEquals("url=https://sub.example.com/***", LogRedactor.redact("url=https://sub.example.com/Ab12Cd34Ef56Gh78Ij90KlMn"))

    @Test fun credentialsHidden() =
        assertFalse(LogRedactor.redact("http://user:pass@host/x").contains("pass"))

    @Test fun uuidShortened() =
        assertEquals("guid 1b2c…", LogRedactor.redact("guid 1b2c3d4e-0000-1111-2222-333344445555"))

    @Test fun plainTextUntouched() =
        assertEquals("ExitIpProbe: exit NL", LogRedactor.redact("ExitIpProbe: exit NL"))
}
