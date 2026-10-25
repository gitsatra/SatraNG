package com.v2ray.ang.handler

import com.v2ray.ang.dto.entities.RulesetItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoUpdaterTest {
    private val json = """{"tag_name":"202610230931","assets":[
        {"name":"geoip.dat","size":10,"browser_download_url":"https://x/geoip.dat"},
        {"name":"geosite.dat","size":20,"browser_download_url":"https://x/geosite.dat"},
        {"name":"geoip.dat.sha256sum","size":1,"browser_download_url":"https://x/s"}]}"""

    @Test fun parsesRelease() {
        val r = GeoUpdater.parseRelease("a/b", json)!!
        assertEquals("202610230931", r.tag)
        assertEquals(20L, r.assets["geosite.dat"]!!.second)
        assertEquals(2, r.assets.size)
        assertNull(GeoUpdater.parseRelease("a/b", """{"tag_name":"x","assets":[]}"""))
        assertNull(GeoUpdater.parseRelease("a/b", "not json"))
    }

    @Test fun newerWhenTagOrRepoDiffers() {
        val r = GeoUpdater.parseRelease("a/b", json)!!
        assertFalse(GeoUpdater.isNewer("202610230931", "a/b", r))
        assertTrue(GeoUpdater.isNewer("1", "a/b", r))
        assertTrue(GeoUpdater.isNewer("202610230931", "c/d", r))
        assertTrue(GeoUpdater.isNewer(null, null, r))
    }

    @Test fun repoPerRegion() {
        assertEquals(GeoUpdater.REPO_IRAN, GeoUpdater.repoFor(emptyList()))
        assertEquals(GeoUpdater.REPO_RUSSIA, GeoUpdater.repoFor(listOf(RulesetItem(outboundTag = "direct", ip = listOf("geoip:ru")))))
        assertEquals(GeoUpdater.REPO_CHINA, GeoUpdater.repoFor(listOf(RulesetItem(outboundTag = "direct", domain = listOf("geosite:cn")))))
    }

    @Test fun requiredTagsAndValidation() {
        val rules = listOf(
            RulesetItem(outboundTag = "direct", domain = listOf("domain:ir", "geosite:category-ir", "geosite:google@cn")),
            RulesetItem(outboundTag = "direct", ip = listOf("geoip:ir"), enabled = false),
        )
        assertEquals(setOf("CATEGORY-IR", "GOOGLE"), GeoUpdater.requiredTags(rules, "geosite"))
        assertEquals(emptySet<String>(), GeoUpdater.requiredTags(rules, "geoip"))
        val data = "....\nCATEGORY-IR....google....".toByteArray()
        assertEquals(emptySet<String>(), GeoUpdater.missingTags(data.inputStream(), setOf("CATEGORY-IR", "GOOGLE")))
        assertEquals(setOf("CATEGORY-RU"), GeoUpdater.missingTags(data.inputStream(), setOf("CATEGORY-IR", "CATEGORY-RU")))
    }

    @Test fun findsTagAcrossChunkBoundary() {
        val pad = ByteArray((1 shl 20) - 4) { 'x'.code.toByte() }
        val data = pad + "CATEGORY-IR".toByteArray()
        assertEquals(emptySet<String>(), GeoUpdater.missingTags(data.inputStream(), setOf("CATEGORY-IR")))
    }
}
