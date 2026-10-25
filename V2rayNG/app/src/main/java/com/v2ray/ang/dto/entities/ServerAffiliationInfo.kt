package com.v2ray.ang.dto.entities

data class ServerAffiliationInfo(
    var testDelayMillis: Long = 0L,
    var countryCode: String? = null,
    var countryName: String? = null,
    var resolvedIp: String? = null,
    var geoUpdatedAt: Long = 0L,
    /**
     * True when [countryCode]/[resolvedIp] came from the live exit IP after a successful connect
     * (through the local proxy), not from resolving the config server host.
     */
    var exitIpVerified: Boolean = false,
    /** User starred this config (kept in the affiliation record so it survives list reloads). */
    var favorite: Boolean = false,
    /** Last time a geo lookup was attempted (success or not), used to rate-limit retries. */
    var geoAttemptAt: Long = 0L,
    /**
     * Where the config's *server address* is (entry point), kept apart from the exit country.
     * Never shown as the row's flag; used to tell tunnels (entry in Iran, exit abroad) apart.
     */
    var entryCountryCode: String? = null,
    var entryIp: String? = null,
    var entryUpdatedAt: Long = 0L,
    var entryAttemptAt: Long = 0L,
    /** Last download speed test through this config, bytes per second (<0 = failed, 0 = never). */
    var speedBps: Long = 0L,
    var speedAt: Long = 0L,
)
