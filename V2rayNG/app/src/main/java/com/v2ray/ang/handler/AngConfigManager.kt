package com.v2ray.ang.handler

import android.content.Context
import android.graphics.Bitmap
import android.text.TextUtils
import com.v2ray.ang.AppConfig
import com.v2ray.ang.core.CoreConfigManager
import com.v2ray.ang.dto.SubscriptionUpdateResult
import com.v2ray.ang.dto.UrlContentRequest
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.SubscriptionCache
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.isNotNullEmpty
import com.v2ray.ang.fmt.CustomFmt
import com.v2ray.ang.fmt.Hysteria2Fmt
import com.v2ray.ang.fmt.ShadowsocksFmt
import com.v2ray.ang.fmt.SocksFmt
import com.v2ray.ang.fmt.TrojanFmt
import com.v2ray.ang.fmt.V2rayNFmt
import com.v2ray.ang.fmt.VlessFmt
import com.v2ray.ang.fmt.VmessFmt
import com.v2ray.ang.fmt.WireguardFmt
import com.v2ray.ang.util.HttpUtil
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.QRCodeDecoder
import com.v2ray.ang.util.Utils
import java.net.URI

object AngConfigManager {

    private data class ParsedProfile(
        val profile: ProfileItem,
        val rawConfig: String? = null,
    )

    // Parser mapping for different config types (lazy initialized)
    private val configFmtParsers: Map<String, (String) -> ProfileItem?> by lazy {
        mapOf(
            EConfigType.VMESS.protocolScheme to VmessFmt::parse,
            EConfigType.SHADOWSOCKS.protocolScheme to ShadowsocksFmt::parse,
            EConfigType.SOCKS.protocolScheme to SocksFmt::parse,
            AppConfig.SOCKS4 to SocksFmt::parse,
            AppConfig.SOCKS5 to SocksFmt::parse,
            EConfigType.TROJAN.protocolScheme to TrojanFmt::parse,
            EConfigType.VLESS.protocolScheme to VlessFmt::parse,
            EConfigType.WIREGUARD.protocolScheme to WireguardFmt::parse,
            EConfigType.HYSTERIA2.protocolScheme to Hysteria2Fmt::parse,
            AppConfig.HY2 to Hysteria2Fmt::parse,
        )
    }

    /**
     * Shares the configuration to the clipboard.
     *
     * @param context The context.
     * @param guid The GUID of the configuration.
     * @return The result code.
     */
    fun share2Clipboard(context: Context, guid: String): Int {
        try {
            val conf = shareConfig(guid)
            if (TextUtils.isEmpty(conf)) {
                return -1
            }

            Utils.setClipboard(context, conf)

        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to share config to clipboard", e)
            return -1
        }
        return 0
    }

    /**
     * Shares non-custom configurations to the clipboard.
     *
     * @param context The context.
     * @param serverList The list of server GUIDs.
     * @return The number of configurations shared.
     */
    fun shareNonCustomConfigsToClipboard(context: Context, serverList: List<String>): Int {
        try {
            val sb = StringBuilder()
            for (guid in serverList) {
                val url = shareConfig(guid)
                if (TextUtils.isEmpty(url)) {
                    continue
                }
                sb.append(url)
                sb.appendLine()
            }
            if (sb.count() > 0) {
                Utils.setClipboard(context, sb.toString())
            }
            return sb.lines().count() - 1
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to share non-custom configs to clipboard", e)
            return -1
        }
    }

    /**
     * Shares the configuration as a QR code.
     *
     * @param guid The GUID of the configuration.
     * @return The QR code bitmap.
     */
    fun share2QRCode(guid: String): Bitmap? {
        try {
            val conf = shareConfig(guid)
            if (TextUtils.isEmpty(conf)) {
                return null
            }
            return QRCodeDecoder.createQRCode(conf)

        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to share config as QR code", e)
            return null
        }
    }

    /**
     * Shares the full content of the configuration to the clipboard.
     *
     * @param context The context.
     * @param guid The GUID of the configuration.
     * @return The result code.
     */
    fun shareFullContent2Clipboard(context: Context, guid: String?): Int {
        try {
            if (guid == null) return -1
            val result = CoreConfigManager.getV2rayConfig(context, guid)
            if (result.status) {
                Utils.setClipboard(context, result.content)
            } else {
                return -1
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to share full content to clipboard", e)
            return -1
        }
        return 0
    }

    /**
     * Shares the configuration.
     *
     * @param guid The GUID of the configuration.
     * @return The configuration string.
     */
    private fun shareConfig(guid: String): String {
        try {
            val config = MmkvManager.decodeServerConfig(guid) ?: return ""

            return config.configType.protocolScheme + when (config.configType) {
                EConfigType.VMESS -> VmessFmt.toUri(config)
                EConfigType.SHADOWSOCKS -> ShadowsocksFmt.toUri(config)
                EConfigType.SOCKS -> SocksFmt.toUri(config)
                EConfigType.VLESS -> VlessFmt.toUri(config)
                EConfigType.TROJAN -> TrojanFmt.toUri(config)
                EConfigType.WIREGUARD -> WireguardFmt.toUri(config)
                EConfigType.HYSTERIA2 -> Hysteria2Fmt.toUri(config)
                else -> {}
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to share config for GUID: $guid", e)
            return ""
        }
    }

    /**
     * Imports a batch of configurations.
     *
     * @param server The server string.
     * @param subid The subscription ID.
     * @param append Whether to append the configurations.
     * @return A pair containing the number of configurations and subscriptions imported.
     */
    /**
     * Imports text shared into the app (share sheet, deep link, QR shortcut). Always appends:
     * replacing here would wipe the user's existing configs in the default group.
     */
    fun importShared(text: String?): Pair<Int, Int> =
        importBatchConfig(text, "", append = true).also { (count, countSub) ->
            // Imported from outside the main screen: tell the lists to reload (they cache per group).
            if (count + countSub > 0) SettingsChangeManager.makeSetupGroupTab()
        }

    fun importBatchConfig(server: String?, subid: String, append: Boolean): Pair<Int, Int> {
        return try {
            var count = parseBatchConfig(Utils.decode(server), subid, append)
            if (count <= 0) {
                count = parseBatchConfig(server, subid, append)
            }
            if (count <= 0) {
                count = parseCustomConfigServer(server, subid, append)
            }

            val newUrls = mutableListOf<String>()
            var countSub = parseBatchSubscription(server, newUrls)
            if (countSub <= 0) {
                countSub = parseBatchSubscription(Utils.decode(server), newUrls)
            }
            if (countSub > 0) {
                // Fetch only what was just added, right away.
                updateConfigViaSubs(MmkvManager.decodeSubscriptions().filter { it.subscription.url in newUrls })
            }

            count to countSub
        } catch (e: ProfileStorageException) {
            LogUtil.e(AppConfig.TAG, "Failed to store imported profiles", e)
            0 to 0
        }
    }

    /**
     * Parses a batch of subscriptions.
     *
     * @param servers The servers string.
     * @return The number of subscriptions parsed.
     */
    private fun parseBatchSubscription(servers: String?, added: MutableList<String>? = null): Int {
        try {
            if (servers == null) {
                return 0
            }

            var count = 0
            servers.lines()
                .distinct()
                .forEach { str ->
                    if (Utils.isValidSubUrl(str)) {
                        val n = importUrlAsSubscription(str)
                        if (n > 0) added?.add(str)
                        count += n
                    }
                }
            return count
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to parse batch subscription", e)
        }
        return 0
    }

    /**
     * Parses a batch of configurations.
     *
     * @param servers The servers string.
     * @param subid The subscription ID.
     * @param append Whether to append the configurations.
     * @return The number of configurations parsed.
     */
    private fun parseBatchConfig(servers: String?, subid: String, append: Boolean): Int {
        try {
            if (servers == null) {
                return 0
            }
            val subItem = MmkvManager.decodeSubscription(subid)

            // Parse all configs first (no I/O during parsing)
            val configs = mutableListOf<ProfileItem>()
            val v2raynLines = mutableListOf<String>()

            servers.lines()
                .distinct()
                .reversed()
                .forEach {
                    if (it.startsWith(AppConfig.V2RAYNFMTS, ignoreCase = true)) {
                        v2raynLines.add(it)
                    } else {
                        val config = parseConfig(it, subid, subItem)
                        if (config != null) {
                            configs.add(config)
                        }
                    }
                }

            val v2raynConfigs = V2rayNFmt.parse(v2raynLines, subid)
            val allConfigs = v2raynConfigs + configs

            if (allConfigs.isNotEmpty()) {
                commitProfiles(
                    configs = allConfigs.map(::ParsedProfile),
                    subid = subid,
                    append = append,
                )
            }

            return allConfigs.size
        } catch (e: ProfileStorageException) {
            throw e
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to parse batch config", e)
        }
        return 0
    }

    /**
     * Commits parsed profiles before removing the profiles they replace.
     *
     * @param configs The parsed profiles to save.
     * @param subid The subscription ID.
     * @param append Whether to append to the existing server list.
     */
    private fun commitProfiles(
        configs: List<ParsedProfile>,
        subid: String,
        append: Boolean,
    ) {
        val keyToProfile = linkedMapOf<String, ProfileItem>()
        val rawConfigs = mutableMapOf<String, String>()

        configs.forEach { parsed ->
            val key = Utils.getUuid()
            keyToProfile[key] = parsed.profile
            parsed.rawConfig?.let { raw -> rawConfigs[key] = raw }
        }

        MmkvManager.saveServerProfiles(
            profiles = keyToProfile,
            rawConfigs = rawConfigs,
            subscriptionId = subid,
            append = append,
        )
    }

    /**
     * Parses a custom configuration server.
     *
     * @param server The server string.
     * @param subid The subscription ID.
     * @param append Whether to append the configurations.
     * @return The number of configurations parsed.
     */
    private fun parseCustomConfigServer(server: String?, subid: String, append: Boolean): Int {
        if (server == null) {
            return 0
        }
        if (server.contains("inbounds")
            && server.contains("outbounds")
            && server.contains("routing")
        ) {
            try {
                val serverList: Array<Any> =
                    JsonUtil.fromJson(server, Array<Any>::class.java) ?: arrayOf()

                if (serverList.isNotEmpty()) {
                    val configs = serverList.reversed().map { srv ->
                        val config = CustomFmt.parse(JsonUtil.toJson(srv))
                        config.subscriptionId = subid
                        config.description = generateDescription(config)
                        ParsedProfile(
                            profile = config,
                            rawConfig = JsonUtil.toJsonPretty(srv) ?: "",
                        )
                    }
                    commitProfiles(configs, subid, append)
                    return configs.size
                }
            } catch (e: ProfileStorageException) {
                throw e
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to parse custom config server JSON array", e)
            }

            try {
                // For compatibility
                val config = CustomFmt.parse(server)
                config.subscriptionId = subid
                config.description = generateDescription(config)
                commitProfiles(
                    configs = listOf(ParsedProfile(config, server)),
                    subid = subid,
                    append = append,
                )
                return 1
            } catch (e: ProfileStorageException) {
                throw e
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to parse custom config server as single config", e)
            }
            return 0
        } else if (server.startsWith("[Interface]") && server.contains("[Peer]")) {
            try {
                val config = WireguardFmt.parseWireguardConfFile(server)
                config.subscriptionId = subid
                config.description = generateDescription(config)
                commitProfiles(
                    configs = listOf(ParsedProfile(config, server)),
                    subid = subid,
                    append = append,
                )
                return 1
            } catch (e: ProfileStorageException) {
                throw e
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to parse WireGuard config file", e)
            }
            return 0
        } else {
            return 0
        }
    }

    /**
     * Parses the configuration from a QR code or string.
     * Only parses and returns ProfileItem, does not save.
     *
     * @param str The configuration string.
     * @param subid The subscription ID.
     * @param subItem The subscription item.
     * @return The parsed ProfileItem or null if parsing fails or filtered out.
     */
    private fun parseConfig(
        str: String?,
        subid: String,
        subItem: SubscriptionItem?
    ): ProfileItem? {
        try {
            if (str == null || TextUtils.isEmpty(str)) {
                return null
            }

            val config = configFmtParsers.firstNotNullOfOrNull { (scheme, parser) ->
                if (str.startsWith(scheme)) parser(str) else null
            }

            if (config == null) {
                return null
            }

            // Apply filter
            if (subItem?.filter.isNotNullEmpty() && config.remarks.isNotNullEmpty()) {
                val matched = Regex(pattern = subItem?.filter.orEmpty())
                    .containsMatchIn(input = config.remarks)
                if (!matched) return null
            }

            config.subscriptionId = subid
            config.description = generateDescription(config)

            return config
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to parse config", e)
            return null
        }
    }

    /**
     * Updates the configuration via all subscriptions.
     *
     * @return Detailed result of the subscription update operation.
     */
    fun updateConfigViaSubAll(): SubscriptionUpdateResult = updateConfigViaSubs(MmkvManager.decodeSubscriptions())

    /** Updates [subscriptions] with up to [SUB_PARALLEL] downloads at once. */
    fun updateConfigViaSubs(subscriptions: List<SubscriptionCache>): SubscriptionUpdateResult {
        if (subscriptions.isEmpty()) return SubscriptionUpdateResult()
        return try {
            if (subscriptions.size == 1) return updateConfigViaSub(subscriptions[0])
            val pool = java.util.concurrent.Executors.newFixedThreadPool(minOf(SUB_PARALLEL, subscriptions.size))
            try {
                subscriptions
                    .map { sub -> pool.submit<SubscriptionUpdateResult> { updateConfigViaSub(sub) } }
                    .fold(SubscriptionUpdateResult()) { acc, f ->
                        acc + runCatching { f.get() }.getOrDefault(SubscriptionUpdateResult(failureCount = 1))
                    }
            } finally {
                pool.shutdown()
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to update config via all subscriptions", e)
            SubscriptionUpdateResult()
        }
    }

    private val storeLock = Any()
    private const val SUB_PARALLEL = 4
    private const val SUB_TIMEOUT_MS = 12_000

    /**
     * Downloads a subscription. With the tunnel down there is only the direct route. With the
     * tunnel up, the direct request and the request through the local proxy race; the first
     * answer that looks like a subscription wins, so a filtered direct route or a slow proxy no
     * longer costs the full timeout of the other one.
     */
    private fun fetchSubscription(url: String, userAgent: String?, requestHeaders: String?): com.v2ray.ang.dto.UrlContentResult? {
        val direct = UrlContentRequest(url = url, userAgent = userAgent, requestHeaders = requestHeaders, timeout = SUB_TIMEOUT_MS)
        val live = UsageStore.live()
        val tunnelUp = live.running && System.currentTimeMillis() - live.updatedAt < 5_000L
        if (!tunnelUp) return fetchOnce(direct) ?: fetchViaBestConfigs(direct)
        val viaProxy = direct.copy(
            httpPort = SettingsManager.getHttpPort(),
            proxyUsername = SettingsManager.getSocksUsername(),
            proxyPassword = SettingsManager.getSocksPassword(),
        )
        val answers = java.util.concurrent.LinkedBlockingQueue<Any>()
        val none = Any()
        val threads = listOf(direct, viaProxy).map { req ->
            Thread({ answers.put(fetchOnce(req) ?: none) }, "sub-fetch").apply { isDaemon = true; start() }
        }
        repeat(threads.size) {
            val r = answers.poll(SUB_TIMEOUT_MS * 3L, java.util.concurrent.TimeUnit.MILLISECONDS) ?: return null
            if (r !== none) return r as com.v2ray.ang.dto.UrlContentResult
        }
        return null
    }

    /**
     * Direct failed with the tunnel down (the sub host is filtered): quietly retry through the best
     * pinging configs, each in a short-lived private core on loopback. Invisible to the user.
     */
    private fun fetchViaBestConfigs(direct: UrlContentRequest): com.v2ray.ang.dto.UrlContentResult? {
        val ctx = com.v2ray.ang.AngApplication.application
        val best = MmkvManager.decodeAllServerList()
            .mapNotNull { g -> MmkvManager.decodeServerAffiliationInfo(g)?.testDelayMillis?.takeIf { it > 0 }?.let { g to it } }
            .sortedBy { it.second }.map { it.first }
            .let { listOfNotNull(MmkvManager.getSelectServer()) + it }.distinct().take(3)
        for (guid in best) {
            val r = runCatching { ExitIpProbe.throughConfig(ctx, guid) { port -> fetchOnce(direct.copy(httpPort = port)) } }.getOrNull()
            if (r != null) { LogUtil.i(AppConfig.TAG, "Subscription fetched via config $guid"); return r }
        }
        return null
    }

    private fun fetchOnce(request: UrlContentRequest): com.v2ray.ang.dto.UrlContentResult? = try {
        HttpUtil.getUrlContentWithUserAgentResult(request).takeIf { looksLikeSubscription(it.body) }
    } catch (e: Exception) {
        LogUtil.w(AppConfig.TAG, "Subscription fetch failed (proxy=${request.httpPort != 0}): ${e.message}")
        null
    }

    /** Rejects empty bodies and HTML pages (ISP block pages, captive portals) so the race never picks one. */
    internal fun looksLikeSubscription(body: String?): Boolean {
        val text = body?.trim().orEmpty()
        if (text.isEmpty()) return false
        val head = text.take(64).lowercase()
        return !(head.startsWith("<!doctype html") || head.startsWith("<html") || head.startsWith("<head") || head.startsWith("<body"))
    }

    /**
     * Updates the configuration via a subscription.
     *
     * @param it The subscription item.
     * @return Subscription update result.
     */
    fun updateConfigViaSub(it: SubscriptionCache): SubscriptionUpdateResult {
        try {
            // Check if disabled
            if (!it.subscription.enabled) {
                return SubscriptionUpdateResult(skipCount = 1)
            }

            // Validate subscription info
            if (TextUtils.isEmpty(it.guid)
                || TextUtils.isEmpty(it.subscription.remarks)
                || TextUtils.isEmpty(it.subscription.url)
            ) {
                return SubscriptionUpdateResult(skipCount = 1)
            }

            val url = HttpUtil.toIdnUrl(it.subscription.url)
            if (!Utils.isValidUrl(url)) {
                return SubscriptionUpdateResult(failureCount = 1)
            }
            if (!it.subscription.allowInsecureUrl) {
                if (!Utils.isValidSubUrl(url)) {
                    return SubscriptionUpdateResult(failureCount = 1)
                }
            }
            LogUtil.i(AppConfig.TAG, url)
            val userAgent = it.subscription.userAgent
            val requestHeaders = it.subscription.requestHeaders
            val fetchResult = fetchSubscription(url, userAgent, requestHeaders)
            val configText = fetchResult?.body.orEmpty()
            if (configText.isEmpty()) {
                return SubscriptionUpdateResult(failureCount = 1)
            }

            fetchResult?.headers?.let { headers ->
                SubscriptionUserinfoParser.applyHeaders(it.subscription, headers)
            }

            // Fetching runs in parallel across subscriptions; storing stays one at a time.
            val count = synchronized(storeLock) {
                val n = parseConfigViaSub(configText, it.guid, false)
                // Always persist userinfo/usage so Home quota cards refresh on pull even if
                // the config body did not change (count can still be > 0 from replacement).
                it.subscription.lastUpdated = System.currentTimeMillis()
                MmkvManager.encodeSubscription(it.guid, it.subscription)
                HybridUsageTracker.onRemoteSync(it.guid, it.subscription)
                n
            }
            // Lists observing this refresh right away, one subscription at a time (partial results).
            SettingsChangeManager.makeSetupGroupTab()
            if (count > 0) {
                LogUtil.i(AppConfig.TAG, "Subscription updated: ${it.subscription.remarks}, $count configs")
                return SubscriptionUpdateResult(
                    configCount = count,
                    successCount = 1
                )
            } else {
                // Body had no new configs, but quota/expiry headers were saved above.
                LogUtil.i(AppConfig.TAG, "Subscription userinfo refreshed: ${it.subscription.remarks}")
                return SubscriptionUpdateResult(successCount = 1)
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to update config via subscription", e)
            return SubscriptionUpdateResult(failureCount = 1)
        }
    }

    /**
     * Removes invalid server configurations for a subscription.
     *
     * @param subId The subscription ID.
     */
    fun removeInvalidServer(subId: String) {
        val serverList = MmkvManager.decodeServerList(subId)
        val invalidServers = serverList.filter {
            val aff = MmkvManager.decodeServerAffiliationInfo(it)
            aff != null && aff.testDelayMillis < 0L
        }
        MmkvManager.removeServers(invalidServers, subId)
    }

    /**
     * Sorts servers by test results for a subscription.
     *
     * @param subId The subscription ID.
     */
    fun sortByTestResultsForSub(subId: String) {
        val serverList = MmkvManager.decodeServerList(subId)
        if (serverList.isEmpty()) return

        val sorted = serverList
            .map { guid ->
                val delay =
                    MmkvManager.decodeServerAffiliationInfo(guid)?.testDelayMillis ?: 0L
                guid to if (delay <= 0L) Long.MAX_VALUE else delay
            }
            .sortedBy { it.second }
            .map { it.first }
            .toMutableList()
        MmkvManager.encodeServerList(sorted, subId)
    }

    /**
     * Parses the configuration via a subscription.
     *
     * @param server The server string.
     * @param subid The subscription ID.
     * @param append Whether to append the configurations.
     * @return The number of configurations parsed.
     */
    private fun parseConfigViaSub(server: String?, subid: String, append: Boolean): Int {
        var count = parseBatchConfig(Utils.decode(server), subid, append)
        if (count <= 0) {
            count = parseBatchConfig(server, subid, append)
        }
        if (count <= 0) {
            count = parseCustomConfigServer(server, subid, append)
        }
        return count
    }

    /**
     * Imports a URL as a subscription.
     *
     * @param url The URL.
     * @return The number of subscriptions imported.
     */
    private fun importUrlAsSubscription(url: String): Int {
        val subscriptions = MmkvManager.decodeSubscriptions()
        subscriptions.forEach {
            if (it.subscription.url == url) {
                return 0
            }
        }
        val uri = URI(Utils.fixIllegalUrl(url))
        val subItem = SubscriptionItem()
        subItem.remarks = uri.fragment ?: "import sub"
        subItem.url = url
        MmkvManager.encodeSubscription("", subItem)
        SettingsChangeManager.makeSetupGroupTab()
        return 1
    }

    /** Generates a description for the profile.
     *
     * @param profile The profile item.
     * @return The generated description.
     */
    fun generateDescription(profile: ProfileItem): String {
        // Hide xxx:xxx:***/xxx.xxx.xxx.***
        val server = profile.server
        val port = profile.serverPort
        if (server.isNullOrBlank() && port.isNullOrBlank()) return ""

        val addrPart = server?.let {
            if (it.contains(":"))
                it.split(":").take(2).joinToString(":", postfix = ":***")
            else
                it.split('.').dropLast(1).joinToString(".", postfix = ".***")
        } ?: ""

        return "$addrPart : ${port ?: ""}"
    }
}
