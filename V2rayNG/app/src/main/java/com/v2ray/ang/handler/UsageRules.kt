package com.v2ray.ang.handler

import com.google.gson.Gson
import java.util.Locale
import java.util.UUID

/** APP: one package. DOMAIN: websites. CATEGORY: every installed app of an [AppCategory]. */
enum class RuleKind { APP, DOMAIN, CATEGORY }

/**
 * A traffic rule. [limitBytes] == 0 means "always block"; otherwise traffic is counted per
 * [period] and blocked once the quota (plus any [bonusBytes] the user added) is used up.
 *
 * APP rules identify traffic by Android package (UID routing in the core); DOMAIN rules by
 * routing domain patterns (`domain:youtube.com`, `googlevideo.com`...).
 */
data class UsageRule(
    val id: String = "",
    val kind: RuleKind = RuleKind.DOMAIN,
    val label: String = "",
    /** Package name for [RuleKind.APP]. */
    val packageName: String = "",
    /** Domain patterns for [RuleKind.DOMAIN]. */
    val domains: List<String> = emptyList(),
    val limitBytes: Long = 0L,
    val period: LimitPeriod = LimitPeriod.DAILY,
    val enabled: Boolean = true,
    val createdAt: Long = 0L,
    /** [AppCategory] name for [RuleKind.CATEGORY]. */
    val category: String = "",
) {
    val alwaysBlock: Boolean get() = limitBytes <= 0L

    /** App and category rules match by app (UID), so they need the Xray tunnel engine. */
    val matchesApps: Boolean get() = kind == RuleKind.APP || kind == RuleKind.CATEGORY

    val appCategory: AppCategory? get() = AppCategory.entries.firstOrNull { it.name == category }

    /** Tag of the counting outbound clone for this rule. */
    val outboundTag: String get() = "$COUNT_TAG_PREFIX${id.take(8)}"

    companion object {
        const val COUNT_TAG_PREFIX = "lim-"

        fun newId(): String = UUID.randomUUID().toString().replace("-", "")
    }
}

/** Optional device-wide cap. When exceeded the VPN is stopped and the user is notified. */
data class GlobalLimit(
    val enabled: Boolean = false,
    val limitBytes: Long = 0L,
    val period: LimitPeriod = LimitPeriod.MONTHLY,
)

data class RuleRuntime(
    val periodKey: String = "",
    val used: Long = 0L,
    val blocked: Boolean = false,
    val warned: Boolean = false,
    /** Extra bytes granted for the current period ("+100 MB"). */
    val bonus: Long = 0L,
)

enum class LimitEvent { None, Warned, Exceeded, Released }

data class LimitStep(val runtime: RuleRuntime, val event: LimitEvent)

/** Pure state machine for one rule's quota window. */
object LimitEvaluator {
    const val WARN_FRACTION = 0.8

    fun step(
        limitBytes: Long,
        period: LimitPeriod,
        runtime: RuleRuntime?,
        deltaBytes: Long,
        nowMs: Long,
        periodKey: String = UsagePeriods.periodKey(period, nowMs),
    ): LimitStep {
        var rt = runtime ?: RuleRuntime(periodKey = periodKey)
        var event = LimitEvent.None
        if (rt.periodKey != periodKey) {
            val wasBlocked = rt.blocked
            rt = RuleRuntime(periodKey = periodKey)
            if (wasBlocked) event = LimitEvent.Released
        }
        rt = rt.copy(used = rt.used + deltaBytes.coerceAtLeast(0L))
        val cap = limitBytes + rt.bonus
        if (limitBytes > 0L) {
            if (!rt.blocked && rt.used >= cap) {
                rt = rt.copy(blocked = true, warned = true)
                event = LimitEvent.Exceeded
            } else if (rt.blocked && rt.used < cap) {
                // user raised the limit / granted a bonus
                rt = rt.copy(blocked = false)
                event = LimitEvent.Released
            } else if (!rt.warned && !rt.blocked && rt.used >= cap * WARN_FRACTION) {
                rt = rt.copy(warned = true)
                if (event == LimitEvent.None) event = LimitEvent.Warned
            }
        }
        return LimitStep(rt, event)
    }
}

/** Persistence of rules (JSON in the shared usage store; readable from every process). */
object UsageRulesRepository {
    private const val KEY_RULES = "rules_v1"
    private const val KEY_GLOBAL = "global_v1"
    private val gson = Gson()

    @Volatile private var cachedRaw: String? = null
    @Volatile private var cachedRules: List<UsageRule> = emptyList()

    fun rules(): List<UsageRule> {
        val raw = UsageStore.getString(KEY_RULES)
        if (raw == cachedRaw) return cachedRules
        val parsed = parse(raw)
        cachedRaw = raw
        cachedRules = parsed
        return parsed
    }

    internal fun parse(raw: String?): List<UsageRule> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            gson.fromJson(raw, Array<UsageRule>::class.java)
                ?.filter { r -> r.id.isNotBlank() && (r.kind as RuleKind?) != null && (r.kind != RuleKind.CATEGORY || r.appCategory != null) }
                ?.map { it.sanitized() }
                .orEmpty()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun UsageRule.sanitized(): UsageRule = copy(
        domains = domains.mapNotNull { normalizeDomain(it) }.distinct(),
        limitBytes = limitBytes.coerceAtLeast(0L),
    )

    fun save(rules: List<UsageRule>) {
        UsageStore.putString(KEY_RULES, gson.toJson(rules))
    }

    fun upsert(rule: UsageRule) {
        val list = rules().toMutableList()
        val idx = list.indexOfFirst { it.id == rule.id }
        if (idx >= 0) list[idx] = rule else list += rule
        save(list)
    }

    fun delete(id: String) {
        save(rules().filterNot { it.id == id })
        UsageStore.clearRule(id)
    }

    fun global(): GlobalLimit = try {
        UsageStore.getString(KEY_GLOBAL)?.let { gson.fromJson(it, GlobalLimit::class.java) } ?: GlobalLimit()
    } catch (_: Exception) { GlobalLimit() }

    fun saveGlobal(limit: GlobalLimit) = UsageStore.putString(KEY_GLOBAL, gson.toJson(limit))

    /**
     * Accepts `youtube.com`, `https://www.youtube.com/watch`, `domain:youtube.com`, `*.youtube.com`
     * and returns a core routing pattern, or null when it is not a domain.
     */
    fun normalizeDomain(input: String): String? {
        var s = input.trim().lowercase(Locale.ROOT)
        if (s.isEmpty()) return null
        val explicit = listOf("domain:", "full:", "keyword:", "regexp:", "geosite:").any { s.startsWith(it) }
        if (explicit) return s
        s = s.removePrefix("http://").removePrefix("https://")
        s = s.substringBefore('/').substringBefore('?').substringBefore('#').substringBefore(':')
        s = s.removePrefix("*.").removePrefix("www.")
        if (!Regex("^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$").matches(s)) return null
        return "domain:$s"
    }
}
