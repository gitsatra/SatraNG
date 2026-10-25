package com.v2ray.ang.handler

import com.v2ray.ang.dto.entities.RulesetItem
import com.v2ray.ang.enums.RoutingType
import java.util.Locale

/**
 * SatraNG offers two routing presets: Iran and Russia (sites of that country go direct, everything
 * else through the tunnel). Older installs may still carry an untouched upstream China/Global
 * preset; those are swapped once for the regional default, while any list the user edited is kept.
 */
object RoutingPresets {
    val offered = listOf(RoutingType.WHITE_IRAN, RoutingType.WHITE_RUSSIA, RoutingType.WHITE_CHINA)
    val legacy = listOf(RoutingType.WHITE, RoutingType.BLACK, RoutingType.GLOBAL)

    /** Russia for Russian-language or RU/BY/KZ devices, Iran otherwise. */
    fun defaultFor(locale: Locale): RoutingType =
        if (locale.language == "ru" || locale.country in setOf("RU", "BY", "KZ")) RoutingType.WHITE_RUSSIA
        else RoutingType.WHITE_IRAN

    private fun signature(items: List<RulesetItem>): List<List<Any?>> =
        items.filter { it.locked != true }.map {
            listOf(it.remarks, it.outboundTag, it.ip, it.domain, it.process, it.port, it.network, it.protocol, it.enabled)
        }

    /** True when [current] (ignoring locked rules) is exactly [preset] as shipped. */
    fun isUnmodified(current: List<RulesetItem>, preset: List<RulesetItem>): Boolean =
        signature(current) == signature(preset)

    /** Which offered preset [current] matches, or null for a custom list. */
    fun matching(current: List<RulesetItem>, presets: Map<RoutingType, List<RulesetItem>>): RoutingType? =
        offered.firstOrNull { t -> presets[t]?.let { isUnmodified(current, it) } == true }
}
