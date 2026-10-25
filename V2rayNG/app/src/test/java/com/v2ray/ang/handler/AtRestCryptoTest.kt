package com.v2ray.ang.handler

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.KeyGenerator

class AtRestCryptoTest {
    private val ids = listOf("MAIN", "SUB")

    /** In-memory stores: data[id] is what a reader with [keys][id] sees. */
    private class FakeStores(var corruptOnReKey: Boolean = false) : AtRestCrypto.Stores {
        val data = mutableMapOf("MAIN" to mapOf("a" to "1", "b" to "2"), "SUB" to mapOf("s" to "x"))
        val keys = mutableMapOf<String, String?>("MAIN" to null, "SUB" to null)
        var backup: Map<String, Map<String, String>>? = null
        var setAside = false
        override fun snapshot(id: String, key: String?) = if (keys[id] == key) data[id] else null
        override fun reKey(id: String, from: String?, to: String?): Boolean {
            if (keys[id] != from) return false
            keys[id] = to
            if (corruptOnReKey) data[id] = emptyMap()
            return true
        }
        override fun backup(ids: List<String>): Boolean { backup = data.toMap(); return true }
        override fun restore(ids: List<String>): Boolean {
            val b = backup ?: return false
            data.putAll(b); ids.forEach { keys[it] = null }; return true
        }
        override fun hasBackup() = backup != null
        override fun dropBackup() { backup = null }
        override fun setAsideUnreadable(ids: List<String>) { setAside = true }
        var scrubs = 0; var scrubBreaks = false; var sole = true
        override fun soleProcess() = sole
        override fun scrubTail(id: String, key: String): Boolean {
            scrubs++
            if (scrubBreaks) { val saved = data[id]; data[id] = emptyMap(); data[id] = saved!! }
            return true
        }
    }

    private class FakeVault(var key: String? = null, val usable: Boolean = true) : AtRestCrypto.KeyVault {
        override fun load() = key
        override fun loadOrCreate() = key ?: if (usable) "K".repeat(16).also { key = it } else null
    }

    private class MemState : AtRestCrypto.StateStore {
        override var state = AtRestCrypto.STATE_PLAIN
        override var migratedAt = 0L
        override var scrubbed = false
    }

    @Test fun migratesWithBackupAndVerifiedReadBack() {
        val s = FakeStores(); val st = MemState()
        val r = AtRestCrypto.ensure(FakeVault(), s, st, ids, 1000)
        assertTrue(r is AtRestCrypto.Outcome.Encrypted)
        assertEquals(AtRestCrypto.STATE_ENCRYPTED, st.state)
        assertEquals("KKKKKKKKKKKKKKKK", s.keys["MAIN"])
        assertEquals(mapOf("a" to "1", "b" to "2"), s.data["MAIN"])
        assertTrue(s.hasBackup()) // kept for the grace period
    }

    @Test fun keystoreUnavailableStaysPlain() {
        val s = FakeStores(); val st = MemState()
        val r = AtRestCrypto.ensure(FakeVault(usable = false), s, st, ids, 0)
        assertTrue(r is AtRestCrypto.Outcome.Plain)
        assertNull(s.keys["MAIN"])
        assertEquals(AtRestCrypto.STATE_PLAIN, st.state)
    }

    @Test fun readBackMismatchRestoresPlaintext() {
        val s = FakeStores(corruptOnReKey = true); val st = MemState()
        val r = AtRestCrypto.ensure(FakeVault(), s, st, ids, 0)
        assertTrue(r is AtRestCrypto.Outcome.Plain)
        assertEquals(mapOf("a" to "1", "b" to "2"), s.data["MAIN"])
        assertNull(s.keys["MAIN"])
        assertEquals(AtRestCrypto.STATE_PLAIN, st.state)
    }

    @Test fun lostKeyFallsBackToPlaintextBackup() {
        val s = FakeStores(); val st = MemState(); val v = FakeVault()
        AtRestCrypto.ensure(v, s, st, ids, 0)
        v.key = null // Keystore wiped
        val r = AtRestCrypto.ensure(v, s, st, ids, 10)
        assertTrue(r is AtRestCrypto.Outcome.Plain)
        assertEquals(mapOf("s" to "x"), s.data["SUB"])
        assertNull(s.keys["SUB"])
    }

    @Test fun backupDroppedOnlyAfterGraceAndGoodOpen() {
        val s = FakeStores(); val st = MemState(); val v = FakeVault()
        AtRestCrypto.ensure(v, s, st, ids, 0)
        AtRestCrypto.ensure(v, s, st, ids, 1000)
        assertTrue(s.hasBackup())
        AtRestCrypto.ensure(v, s, st, ids, AtRestCrypto.BACKUP_GRACE_MS + 1)
        assertTrue(!s.hasBackup())
    }

    @Test fun lostKeyWithoutBackupSetsFilesAsideNeverDeletes() {
        val s = FakeStores(); val st = MemState(); val v = FakeVault()
        AtRestCrypto.ensure(v, s, st, ids, 0)
        s.dropBackup(); v.key = null
        AtRestCrypto.ensure(v, s, st, ids, 5)
        assertTrue(s.setAside)
        assertEquals(mapOf("a" to "1", "b" to "2"), s.data["MAIN"])
    }

    @Test fun keyWrapRoundTripAndTamper() {
        val sk = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val w = KeyWrap { sk }
        val blob = w.wrap("abcdefghijklmnop".toByteArray())!!
        assertArrayEquals("abcdefghijklmnop".toByteArray(), w.unwrap(blob))
        blob[blob.size - 1] = (blob[blob.size - 1].toInt() xor 1).toByte()
        assertNull(w.unwrap(blob))
    }

    @Test fun randomKeysAre16CharsAndDiffer() {
        val a = KeystoreVault.randomKey(); val b = KeystoreVault.randomKey()
        assertEquals(16, a.length); assertTrue(a != b)
    }

    @Test fun scrubRunsOnceAfterMigration() {
        val s = FakeStores(); val st = MemState(); val v = FakeVault()
        AtRestCrypto.ensure(v, s, st, ids, 0)
        assertTrue(st.scrubbed); assertEquals(2, s.scrubs)
        AtRestCrypto.ensure(v, s, st, ids, 10)
        assertEquals(2, s.scrubs)
    }

    @Test fun scrubWaitsWhenOtherProcessesRun() {
        val s = FakeStores().apply { sole = false }; val st = MemState(); val v = FakeVault()
        AtRestCrypto.ensure(v, s, st, ids, 0)
        assertTrue(!st.scrubbed); assertEquals(0, s.scrubs)
        s.sole = true
        AtRestCrypto.ensure(v, s, st, ids, 10)
        assertTrue(st.scrubbed)
    }
}
