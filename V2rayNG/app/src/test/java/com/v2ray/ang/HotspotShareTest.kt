package com.v2ray.ang

import com.v2ray.ang.handler.HotspotShare
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HotspotShareTest {
    @Test fun prefersHotspotInterface() {
        assertEquals("192.168.43.1", HotspotShare.pickLanIp(listOf("wlan0" to "192.168.1.20", "ap0" to "192.168.43.1", "rmnet0" to "10.1.2.3")))
        assertEquals("10.42.0.1", HotspotShare.pickLanIp(listOf("swlan0" to "10.42.0.1", "wlan0" to "192.168.1.20")))
    }

    @Test fun ignoresTunnelCellularAndPublic() {
        assertNull(HotspotShare.pickLanIp(listOf("tun0" to "10.0.0.2", "rmnet_data0" to "10.55.1.1", "wlan0" to "8.8.8.8")))
        assertEquals("172.20.10.1", HotspotShare.pickLanIp(listOf("eth0" to "172.20.10.1")))
        assertNull(HotspotShare.pickLanIp(listOf("eth0" to "172.40.0.1")))
    }

    @Test fun shareUriEncodesCredentials() {
        assertEquals("socks5://satra:a%40b%3Ac@192.168.43.1:10808", HotspotShare.shareUri("socks5", "192.168.43.1", 10808, "satra", "a@b:c"))
        assertEquals("http://192.168.43.1:10809", HotspotShare.shareUri("http", "192.168.43.1", 10809, null, null))
    }

    @Test fun randomPasswordsAreStrong() {
        val a = HotspotShare.randomPassword(); val b = HotspotShare.randomPassword()
        assertEquals(12, a.length); assertTrue(a != b); assertTrue(a.all { it.isLetterOrDigit() })
    }
}
