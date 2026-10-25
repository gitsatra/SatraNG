package com.v2ray.ang.handler

/**
 * Quick "allow more" amounts offered when a data limit is reached, in the unit the limit uses:
 * limits under 1 GB get +5 / +50 / +100 MB, larger ones +0.5 / +1 / +2 GB. Pure, unit tested.
 */
object TopUp {
    const val MB = 1024L * 1024
    const val GB = 1024L * MB

    fun options(limitBytes: Long): List<Long> =
        if (limitBytes in 1 until GB) listOf(5 * MB, 50 * MB, 100 * MB)
        else listOf(GB / 2, GB, 2 * GB)

    /** True when [bytes] should be labelled in GB. */
    fun isGb(bytes: Long): Boolean = bytes >= GB / 2
}
