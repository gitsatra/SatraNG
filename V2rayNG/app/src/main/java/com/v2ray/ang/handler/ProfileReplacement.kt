package com.v2ray.ang.handler

import com.v2ray.ang.dto.entities.ProfileItem

internal object ProfileReplacement {

    /**
     * Finds the profile that should become selected after publishing a replacement batch.
     * The first profile becomes selected when the store has no current selection.
     */
    fun findSelectedReplacement(
        profiles: Map<String, ProfileItem>,
        currentSelection: String?,
        selectedProfile: ProfileItem?,
    ): String? {
        if (profiles.isEmpty()) return null
        if (currentSelection.isNullOrBlank()) return profiles.keys.first()
        if (selectedProfile == null) return null

        if (selectedProfile.remarks.isNotBlank()) {
            profiles.entries.firstOrNull { (_, candidate) ->
                isSameText(candidate.remarks, selectedProfile.remarks) &&
                        isSameText(candidate.server, selectedProfile.server) &&
                        isSameText(candidate.serverPort, selectedProfile.serverPort) &&
                        isSameText(candidate.password, selectedProfile.password)
            }?.key?.let { return it }

            profiles.entries.firstOrNull { (_, candidate) ->
                isSameText(candidate.remarks, selectedProfile.remarks)
            }?.key?.let { return it }
        }

        profiles.entries.firstOrNull { (_, candidate) ->
            isSameText(candidate.server, selectedProfile.server) &&
                    isSameText(candidate.serverPort, selectedProfile.serverPort) &&
                    isSameText(candidate.password, selectedProfile.password)
        }?.key?.let { return it }

        profiles.entries.firstOrNull { (_, candidate) ->
            isSameText(candidate.server, selectedProfile.server) &&
                    isSameText(candidate.serverPort, selectedProfile.serverPort)
        }?.key?.let { return it }

        profiles.entries.firstOrNull { (_, candidate) ->
            isSameText(candidate.server, selectedProfile.server)
        }?.key?.let { return it }

        return profiles.keys.firstOrNull()
    }

    /**
     * Finds replaced payloads that are safe to remove.
     *
     * A null cross-group reference set means that at least one raw group index could not
     * be read. In that case deletion fails closed.
     */
    fun findRemovablePayloads(
        replacedServers: Collection<String>,
        replacementServers: Set<String>,
        protectedServer: String?,
        serversReferencedByOtherGroups: Set<String>?,
    ): Set<String> {
        if (serversReferencedByOtherGroups == null) return emptySet()

        return replacedServers.filterTo(linkedSetOf()) { guid ->
            guid != protectedServer &&
                    guid !in replacementServers &&
                    guid !in serversReferencedByOtherGroups
        }
    }

    /** What makes two profiles "the same config" across a subscription update (names may change). */
    internal fun endpointKey(p: ProfileItem): String = listOf(
        p.configType.name, p.server, p.serverPort, p.password, p.method, p.network, p.headerType,
        p.host, p.path, p.mode, p.serviceName, p.authority, p.security, p.sni, p.publicKey, p.shortId,
    ).joinToString("|") { it?.trim()?.lowercase().orEmpty() }

    /**
     * Maps each new profile key to the guid of the old profile it replaces, so a refresh keeps the
     * guid (and with it the measured exit country, favourite, speed and usage). Exact matches
     * (same endpoint and same name) win, then same endpoint alone; every old guid is used once.
     */
    fun matchExisting(newProfiles: Map<String, ProfileItem>, old: Map<String, ProfileItem>): Map<String, String> {
        val result = linkedMapOf<String, String>()
        val used = hashSetOf<String>()
        val oldByEndpoint = old.entries.groupBy { endpointKey(it.value) }
        fun pass(sameName: Boolean) {
            for ((newKey, np) in newProfiles) {
                if (newKey in result) continue
                val match = oldByEndpoint[endpointKey(np)].orEmpty().firstOrNull { (g, op) ->
                    g !in used && (!sameName || op.remarks.trim() == np.remarks.trim())
                } ?: continue
                result[newKey] = match.key
                used += match.key
            }
        }
        pass(sameName = true)
        pass(sameName = false)
        return result
    }

    private fun isSameText(left: String?, right: String?): Boolean {
        if (left.isNullOrBlank() || right.isNullOrBlank()) return false
        return left.trim().equals(right.trim(), ignoreCase = true)
    }
}
