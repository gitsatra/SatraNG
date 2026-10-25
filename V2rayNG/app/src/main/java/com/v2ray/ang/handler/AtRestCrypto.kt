package com.v2ray.ang.handler

/**
 * At-rest encryption of the MMKV stores that hold configs, subscriptions and credentials.
 *
 * Safety rules (configs must never be lost):
 *  - Migrate only when the Keystore works; otherwise stay plaintext.
 *  - Back up the plaintext files first, re-key, then read every value back and compare.
 *    Any mismatch or error restores the backup and stays plaintext.
 *  - The backup is kept until a later start (>= [BACKUP_GRACE_MS] after migration) opens the
 *    encrypted stores fine; if the key is ever unavailable while it exists, the backup is restored.
 *  - If the key is lost and no backup remains, the encrypted files are set aside, never deleted.
 *
 * Pure logic over small interfaces so it is unit-tested without Android or MMKV.
 */
object AtRestCrypto {
    const val STATE_PLAIN = "plain"
    const val STATE_ENCRYPTED = "encrypted"
    const val BACKUP_GRACE_MS = 24L * 60 * 60 * 1000

    interface KeyVault {
        /** The existing key, or null if none / not readable. */
        fun load(): String?
        /** Existing key or a new one; null when the Keystore can't be used. */
        fun loadOrCreate(): String?
    }

    interface Stores {
        /** Every key -> raw value (base64), or null when the store can't be read. */
        fun snapshot(id: String, key: String?): Map<String, String>?
        fun reKey(id: String, from: String?, to: String?): Boolean
        fun backup(ids: List<String>): Boolean
        fun restore(ids: List<String>): Boolean
        fun hasBackup(): Boolean
        fun dropBackup()
        fun setAsideUnreadable(ids: List<String>)
        /** Zero the bytes past the used data (old plaintext left by re-key). Backed up and reverted on failure. */
        fun scrubTail(id: String, key: String): Boolean = true
        /** True when no other app process can have the stores open. */
        fun soleProcess(): Boolean = true
    }

    interface StateStore {
        var state: String
        var migratedAt: Long
        var scrubbed: Boolean
            get() = true
            set(_) {}
    }

    /** One-time cleanup of plaintext remnants; verified by a full read-back per store. */
    fun scrub(stores: Stores, st: StateStore, ids: List<String>, key: String) {
        if (st.scrubbed || !stores.soleProcess()) return
        val ok = ids.all { id ->
            val before = stores.snapshot(id, key) ?: return@all false
            stores.scrubTail(id, key) && stores.snapshot(id, key) == before
        }
        if (ok) st.scrubbed = true
    }

    sealed interface Outcome {
        data class Encrypted(val key: String, val migratedNow: Boolean) : Outcome
        data class Plain(val reason: String) : Outcome
    }

    fun ensure(vault: KeyVault, stores: Stores, st: StateStore, ids: List<String>, nowMs: Long): Outcome {
        if (st.state == STATE_ENCRYPTED) {
            val key = vault.load()
            if (key != null) {
                // Prove the stores really open with this key before dropping the safety copy.
                val readable = ids.all { stores.snapshot(it, key) != null }
                if (readable && stores.hasBackup() && nowMs - st.migratedAt >= BACKUP_GRACE_MS) stores.dropBackup()
                if (readable) scrub(stores, st, ids, key)
                if (readable || !stores.hasBackup()) return Outcome.Encrypted(key, migratedNow = false)
            }
            // Key gone (or stores unreadable): fall back to the plaintext copy.
            if (stores.hasBackup() && stores.restore(ids)) {
                st.state = STATE_PLAIN
                return Outcome.Plain("key unavailable, restored plaintext backup")
            }
            stores.setAsideUnreadable(ids)
            st.state = STATE_PLAIN
            return Outcome.Plain("key unavailable, no backup; encrypted files kept aside")
        }

        val key = vault.loadOrCreate() ?: return Outcome.Plain("keystore unavailable")
        if (!stores.backup(ids)) return Outcome.Plain("backup failed")
        for (id in ids) {
            val before = stores.snapshot(id, null)
            val ok = before != null && stores.reKey(id, null, key)
            val after = if (ok) stores.snapshot(id, key) else null
            if (!ok || after != before) {
                stores.restore(ids)
                return Outcome.Plain("verify failed for $id, restored backup")
            }
        }
        st.migratedAt = nowMs
        st.state = STATE_ENCRYPTED
        scrub(stores, st, ids, key)
        return Outcome.Encrypted(key, migratedNow = true)
    }
}
