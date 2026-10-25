package com.v2ray.ang.handler

import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ProfileReplacementTest {

    @Test
    fun `prefers a full match over a remarks-only match`() {
        val profiles = linkedMapOf(
            "remarks" to profile(remarks = "selected", server = "other"),
            "full" to profile(remarks = "selected", server = "host", port = "443", password = "secret"),
        )

        val result = ProfileReplacement.findSelectedReplacement(
            profiles = profiles,
            currentSelection = "old",
            selectedProfile = profile(
                remarks = " selected ",
                server = "HOST",
                port = "443",
                password = "secret",
            ),
        )

        assertEquals("full", result)
    }

    @Test
    fun `uses a remarks match when no full match exists`() {
        val profiles = linkedMapOf(
            "other" to profile(remarks = "other", server = "host"),
            "remarks" to profile(remarks = "selected", server = "other"),
        )

        val result = ProfileReplacement.findSelectedReplacement(
            profiles = profiles,
            currentSelection = "old",
            selectedProfile = profile(remarks = "selected", server = "host"),
        )

        assertEquals("remarks", result)
    }

    @Test
    fun `matches endpoint and password when remarks are unavailable`() {
        val profiles = linkedMapOf(
            "endpoint" to profile(server = "host", port = "443", password = "secret"),
            "other" to profile(server = "other", port = "443", password = "secret"),
        )

        val result = ProfileReplacement.findSelectedReplacement(
            profiles = profiles,
            currentSelection = "old",
            selectedProfile = profile(server = "HOST", port = "443", password = "secret"),
        )

        assertEquals("endpoint", result)
    }

    @Test
    fun `falls back to the first replacement profile`() {
        val profiles = linkedMapOf(
            "first" to profile(server = "first"),
            "second" to profile(server = "second"),
        )

        val result = ProfileReplacement.findSelectedReplacement(
            profiles = profiles,
            currentSelection = "old",
            selectedProfile = profile(server = "unmatched"),
        )

        assertEquals("first", result)
    }

    @Test
    fun `selects the first profile when there is no current selection`() {
        val result = ProfileReplacement.findSelectedReplacement(
            profiles = linkedMapOf(
                "first" to profile(server = "first"),
                "second" to profile(server = "second"),
            ),
            currentSelection = null,
            selectedProfile = null,
        )

        assertEquals("first", result)
    }

    @Test
    fun `keeps an existing selection when it is outside the replaced group`() {
        val result = ProfileReplacement.findSelectedReplacement(
            profiles = mapOf("candidate" to profile(server = "host")),
            currentSelection = "other-group",
            selectedProfile = null,
        )

        assertNull(result)
    }

    @Test
    fun `removes only unreferenced superseded payloads`() {
        val result = ProfileReplacement.findRemovablePayloads(
            replacedServers = listOf("orphan", "selected", "replacement", "cross-group"),
            replacementServers = setOf("replacement"),
            protectedServer = "selected",
            serversReferencedByOtherGroups = setOf("cross-group"),
        )

        assertEquals(setOf("orphan"), result)
    }

    @Test
    fun `keeps all payloads when another group index is unreadable`() {
        val result = ProfileReplacement.findRemovablePayloads(
            replacedServers = listOf("candidate"),
            replacementServers = emptySet(),
            protectedServer = null,
            serversReferencedByOtherGroups = null,
        )

        assertEquals(emptySet<String>(), result)
    }

    @Test
    fun refreshKeepsGuidsOfUnchangedConfigsEvenWhenRenamed() {
        val old = mapOf(
            "g-a" to profile("A 99 GB", "a.example", "443", "id-a"),
            "g-b" to profile("B", "b.example", "443", "id-b"),
            "g-gone" to profile("Gone", "gone.example", "443", "id-x"),
        )
        val fresh = mapOf(
            "n1" to profile("A 98 GB", "a.example", "443", "id-a"), // traffic in name changed
            "n2" to profile("B", "b.example", "443", "id-b"),
            "n3" to profile("New", "new.example", "443", "id-n"),
        )
        assertEquals(mapOf("n1" to "g-a", "n2" to "g-b"), ProfileReplacement.matchExisting(fresh, old))
    }

    @Test
    fun duplicateEndpointsPreferSameNameAndUseEachOldGuidOnce() {
        val old = mapOf(
            "g1" to profile("Fast", "x.example", "443", "id"),
            "g2" to profile("Slow", "x.example", "443", "id"),
        )
        val fresh = mapOf(
            "n1" to profile("Slow", "x.example", "443", "id"),
            "n2" to profile("Fast", "x.example", "443", "id"),
            "n3" to profile("Third", "x.example", "443", "id"),
        )
        assertEquals(mapOf("n1" to "g2", "n2" to "g1"), ProfileReplacement.matchExisting(fresh, old))
    }

    @Test
    fun changedCredentialsAreANewConfig() {
        val old = mapOf("g" to profile("A", "a.example", "443", "old-id"))
        val fresh = mapOf("n" to profile("A", "a.example", "443", "new-id"))
        assertEquals(emptyMap<String, String>(), ProfileReplacement.matchExisting(fresh, old))
    }

    private fun profile(
        remarks: String = "",
        server: String = "",
        port: String = "",
        password: String = "",
    ) = ProfileItem.create(EConfigType.VMESS).apply {
        this.remarks = remarks
        this.server = server
        this.serverPort = port
        this.password = password
    }
}
