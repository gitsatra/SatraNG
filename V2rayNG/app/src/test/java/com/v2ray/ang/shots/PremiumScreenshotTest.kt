package com.v2ray.ang.shots

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.v2ray.ang.dto.GroupMapItem
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.handler.DayUsage
import com.v2ray.ang.handler.GlobalLimit
import com.v2ray.ang.handler.LimitPeriod
import com.v2ray.ang.handler.RuleKind
import com.v2ray.ang.handler.UsageRule
import com.v2ray.ang.ui.app.AppNav
import com.v2ray.ang.ui.compose.satra.LocalInlineSheets
import com.v2ray.ang.ui.compose.satra.SatraBackground
import com.v2ray.ang.ui.compose.satra.SatraTheme
import com.v2ray.ang.ui.home.AddMethod
import com.v2ray.ang.ui.home.HomeScreen
import com.v2ray.ang.ui.main.ConnectPhase
import com.v2ray.ang.ui.main.LiveUiState
import com.v2ray.ang.ui.main.MainAction
import com.v2ray.ang.ui.main.MainDestination
import com.v2ray.ang.ui.main.MainUiState
import com.v2ray.ang.ui.main.QuotaUi
import com.v2ray.ang.ui.main.ServerRowUiModel
import com.v2ray.ang.ui.more.MoreActions
import com.v2ray.ang.ui.more.MoreContent
import com.v2ray.ang.ui.more.MoreUiState
import com.v2ray.ang.ui.servers.ServersContent
import com.v2ray.ang.ui.settings.SettingsDestination
import com.v2ray.ang.ui.subs.SubRow
import com.v2ray.ang.ui.subs.SubsActions
import com.v2ray.ang.ui.subs.SubsContent
import com.v2ray.ang.ui.subs.SubsMessage
import com.v2ray.ang.ui.subs.SubsUiState
import com.v2ray.ang.ui.usage.EditorTarget
import com.v2ray.ang.ui.usage.LimitEditorSheet
import com.v2ray.ang.ui.subs.EditSheet
import com.v2ray.ang.ui.servers.ServerActionsSheet
import com.v2ray.ang.ui.usage.GlobalRow
import com.v2ray.ang.ui.usage.RuleRow
import com.v2ray.ang.ui.usage.UsageActions
import com.v2ray.ang.ui.usage.UsageContent
import com.v2ray.ang.ui.usage.UsageUiState
import org.junit.Rule
import org.junit.Test
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.activity.compose.setContent
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Offscreen renders of every main screen (dark/light, en/fa-RTL, font scales) into preview/premium/.
 * Records only; there is no golden comparison. Run: ./gradlew :app:testPlaystoreDebugUnitTest --tests '*PremiumScreenshotTest*'
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = android.app.Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
@OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
class PremiumScreenshotTest {

    private object NoNav : AppNav {
        override fun action(action: MainAction) = Unit
        override fun navigate(destination: MainDestination) = Unit
        override fun openSettingsPage(destination: SettingsDestination) = Unit
        override fun editSubscription(subId: String?) = Unit
        override fun scanQr(onResult: (String) -> Unit) = Unit
        override fun openUsageAccess() = Unit
        override fun enableAppEnforcement() = Unit
        override fun openSystemVpnSettings() = Unit
        override fun share(text: String) = Unit
        override fun copy(text: String) = Unit
    }

    private val noSubs = object : SubsActions {
        override fun reload() = Unit
        override fun updateAll() = Unit
        override fun updateOne(guid: String) = Unit
        override fun setEnabled(guid: String, enabled: Boolean) = Unit
        override fun delete(guid: String) = Unit
        override fun save(guid: String?, name: String, url: String, autoUpdate: Boolean, intervalHours: Int, enabled: Boolean): SubsMessage? = null
        override fun showQr(row: SubRow) = Unit
        override fun dismissQr() = Unit
        override fun messageShown() = Unit
    }
    private val noUsage = object : UsageActions {
        override fun setRange(days: Int) = Unit
        override fun saveRule(rule: UsageRule) = Unit
        override fun deleteRule(id: String) = Unit
        override fun resetRule(id: String) = Unit
        override fun setRuleEnabled(id: String, enabled: Boolean) = Unit
        override fun saveGlobal(limit: GlobalLimit) = Unit
        override fun clearHistory() = Unit
        override fun setEngineChoice(on: Boolean) = Unit
    }
    private val noMore = object : MoreActions {
        override fun setTheme(mode: String) = Unit
        override fun setLanguage(code: String) = Unit
        override fun setVpnMode(vpn: Boolean) = Unit
        override fun setSpeedNotification(on: Boolean) = Unit
        override fun setSmartConnect(on: Boolean) = Unit
        override fun setQuickConnect(on: Boolean) = Unit
        override fun setShowAdvanced(on: Boolean) = Unit
    }

    // ---------- fixtures ----------

    private fun server(guid: String, name: String, cc: String?, type: String, ms: Long, fav: Boolean = false, verified: Boolean = true) =
        ServerRowUiModel(
            guid = guid,
            profile = ProfileItem.create(EConfigType.VLESS).apply { remarks = name; server = "$guid.example.com" },
            remarks = name,
            statistics = "$guid.example.com:443",
            typeDescription = type,
            testDelayMillis = ms,
            subscriptionBadge = "S",
            countryCode = cc,
            countryName = cc?.let { java.util.Locale("", it).getDisplayCountry(java.util.Locale.ENGLISH) },
            favorite = fav,
            exitVerified = verified,
        )

    private val rows = listOf(
        server("a1", "Frankfurt • Fast 01", "DE", "VLESS / ws / tls", 86, fav = true).copy(speedBps = 3_100_000),
        server("a2", "Amsterdam premium with a very long name that must truncate nicely", "NL", "VLESS / grpc / reality", 143),
        server("a3", "Istanbul", "TR", "VMESS / tcp", 312).copy(entryCountryCode = "IR", usedBytes = 1_480_000_000L),
        server("a4", "Tokyo", "JP", "TROJAN / tls", 780, verified = false),
        server("a5", "Unknown location", null, "VLESS / tcp / tls", 0),
        server("a6", "Blocked node", "US", "SHADOWSOCKS", -1),
    )

    private val baseState = MainUiState(
        groups = listOf(GroupMapItem("g1", "My provider"), GroupMapItem("g2", "Free")),
        selectedGroupId = "g1",
        selectedGuid = "a1",
        selectedRow = rows[0],
        quota = QuotaUi("My provider", 18L * 1024 * 1024 * 1024, 50L * 1024 * 1024 * 1024, System.currentTimeMillis() + 12L * 86_400_000, System.currentTimeMillis() - 18L * 86_400_000),
    )

    private val week = (0 until 7).map { i ->
        DayUsage("d$i", System.currentTimeMillis() - (6 - i) * 86_400_000L, (i * 40L + 30) * 1_048_576, (i * 300L + 400) * 1_048_576, (i + 1) * 2400L)
    }
    private val month = (0 until 30).map { i ->
        DayUsage("d$i", System.currentTimeMillis() - (29 - i) * 86_400_000L, ((i * 37) % 90 + 10L) * 1_048_576, ((i * 131) % 900 + 100L) * 1_048_576, ((i * 977) % 20000 + 1500L))
    }
    private val live = LiveUiState(
        running = true, upBps = 182_000, downBps = 2_450_000, sessionStartMs = System.currentTimeMillis() - 754_000,
        sessionBytes = 312L * 1_048_576, todayBytes = 1_240L * 1_048_576,
        sessionDown = 290L * 1_048_576, sessionUp = 22L * 1_048_576, todayDown = 1_150L * 1_048_576, todayUp = 90L * 1_048_576,
        history = listOf(0.1f, 0.3f, 0.2f, 0.6f, 0.9f, 0.5f, 0.7f, 0.4f, 0.8f, 1f, 0.6f, 0.9f),
    )

    private fun subs(): SubsUiState = SubsUiState(
        rows = listOf(
            SubRow("s1", SubscriptionItem(remarks = "My provider", url = "https://sub.example.com/abc", autoUpdate = true, lastUpdated = System.currentTimeMillis() - 3_600_000,
                upload = 2L * 1024 * 1024 * 1024, download = 16L * 1024 * 1024 * 1024, total = 50L * 1024 * 1024 * 1024, expire = System.currentTimeMillis() / 1000 + 12 * 86_400,
                announce = "Server maintenance tonight 02:00–03:00.", supportUrl = "https://t.me/support"), 24),
            SubRow("s2", SubscriptionItem(remarks = "Free nodes", url = "https://free.example.com/x", total = 10L * 1024 * 1024 * 1024, download = 9_600L * 1_048_576, expire = System.currentTimeMillis() / 1000 + 86_400 * 2), 8),
            SubRow("s3", SubscriptionItem(remarks = "Old plan", url = "https://old.example.com/y", enabled = false, expire = System.currentTimeMillis() / 1000 - 86_400), 0),
        ),
        updating = setOf("s2"),
    )

    private fun usage(range: Int = 7): UsageUiState {
        val days = if (range == 7) week else month
        val now = System.currentTimeMillis()
        val rules = listOf(
            RuleRow(UsageRule("r1", RuleKind.APP, "Instagram", "com.instagram.android", emptyList(), 500L * 1_048_576, LimitPeriod.DAILY, true), 410L * 1_048_576, 0, false, now + 3_600_000, "com.instagram.android", false),
            RuleRow(UsageRule("r2", RuleKind.DOMAIN, "YouTube", "", listOf("domain:youtube.com"), 1024L * 1_048_576, LimitPeriod.DAILY, true), 1024L * 1_048_576, 0, true, now + 7_200_000, null, false),
            RuleRow(UsageRule("r3", RuleKind.DOMAIN, "TikTok", "", listOf("domain:tiktok.com"), 0, LimitPeriod.WEEKLY, true), 0, 0, true, now, null, false),
        )
        return UsageUiState(
            rangeDays = range, days = days, totalBytes = days.sumOf { it.totalBytes }, averageBytes = days.sumOf { it.totalBytes } / range,
            connectedSeconds = days.sumOf { it.connectedSec },
            apps = emptyList(), hasUsageAccess = false, rules = rules,
            global = GlobalRow(GlobalLimit(true, 20L * 1024 * 1024 * 1024, LimitPeriod.MONTHLY), 8L * 1024 * 1024 * 1024, now + 86_400_000L * 9),
            engineReady = true,
        )
    }

    // ---------- harness ----------

    @get:Rule(order = 0)
    val registerActivity = org.junit.rules.TestRule { base, _ ->
        object : org.junit.runners.model.Statement() {
            override fun evaluate() {
                val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
                org.robolectric.Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(
                    android.content.ComponentName(app.packageName, androidx.activity.ComponentActivity::class.java.name),
                )
                base.evaluate()
            }
        }
    }

    @get:Rule(order = 1)
    val composeRule = createEmptyComposeRule()

    private fun shot(
        name: String,
        dark: Boolean,
        lang: String = "en",
        font: Float = 1f,
        heightDp: Int = 891,
        widthDp: Int = 411,
        inlineSheets: Boolean = false,
        still: Boolean = false,
        content: @Composable () -> Unit,
    ) {
        val tag = "${name}${if (widthDp != 411) "_w$widthDp" else ""}_${if (dark) "dark" else "light"}_${lang}${if (font != 1f) "_x${(font * 100).toInt()}" else ""}"
        val out = java.io.File(System.getProperty("roborazzi.output.dir") ?: "build/shots").apply { mkdirs() }
        java.util.Locale.setDefault(java.util.Locale.forLanguageTag(lang))
        org.robolectric.RuntimeEnvironment.setQualifiers("w${widthDp}dp-h${heightDp}dp-xxhdpi")
        android.provider.Settings.Global.putFloat(
            androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>().contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, if (still) 0f else 1f,
        )
        composeRule.mainClock.autoAdvance = false
        val scenario = androidx.test.core.app.ActivityScenario.launch(androidx.activity.ComponentActivity::class.java)
        scenario.onActivity { act -> act.setContent {
            val base = LocalContext.current
            val locale = java.util.Locale.forLanguageTag(lang)
            val cfg = Configuration(LocalConfiguration.current).apply {
                setLocale(locale)
                fontScale = font
                setLayoutDirection(locale)
            }
            val ctx = androidx.compose.runtime.remember(base) { base.createConfigurationContext(cfg) }
            val rtl = lang == "fa"
            CompositionLocalProvider(
                LocalContext provides ctx,
                LocalConfiguration provides cfg,
                LocalDensity provides Density(base.resources.displayMetrics.density, font),
                LocalLayoutDirection provides (if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr),
                LocalInlineSheets provides inlineSheets,
            ) {
                SatraTheme(dark = dark) {
                    Box(Modifier.requiredSize(widthDp.dp, heightDp.dp)) { SatraBackground { content() } }
                }
            }
        } }
        composeRule.mainClock.advanceTimeBy(4000)
        composeRule.onRoot().captureRoboImage(java.io.File(out, "$tag.png").path)
        scenario.close()
    }

    private fun home(state: MainUiState, l: LiveUiState = live) = @Composable {
        HomeScreen(state, l, week, {}, {}, {}, {}, { _: AddMethod -> })
    }

    // ---------- Home: every connect phase ----------

    @Test fun homeIdle() = shot("home_idle", true, content = home(baseState.copy(phase = ConnectPhase.Idle), LiveUiState()))
    @Test fun homeConnecting() = shot("home_connecting", true, content = home(baseState.copy(phase = ConnectPhase.Connecting), LiveUiState()))
    @Test fun homeConnected() = shot("home_connected", true, content = home(baseState.copy(phase = ConnectPhase.Connected, isRunning = true)))
    @Test fun homeDisconnecting() = shot("home_disconnecting", true, content = home(baseState.copy(phase = ConnectPhase.Disconnecting, isRunning = true)))
    @Test fun homeFailed() = shot("home_failed", true, content = home(baseState.copy(phase = ConnectPhase.Failed, errorMessage = "Connection timed out"), LiveUiState()))
    @Test fun homeEmpty() = shot("home_empty", true, content = home(MainUiState(), LiveUiState()))
    @Test fun homeConnectedLightFa() = shot("home_connected", false, "fa", content = home(baseState.copy(phase = ConnectPhase.Connected, isRunning = true)))
    @Test fun homeIdleLightEn() = shot("home_idle", false, content = home(baseState.copy(phase = ConnectPhase.Idle), LiveUiState()))
    @Test fun homeIdleFaDark() = shot("home_idle", true, "fa", content = home(baseState.copy(phase = ConnectPhase.Idle), LiveUiState()))
    @Test fun homeConnectedSmall() = shot("home_connected_small", false, heightDp = 640, content = home(baseState.copy(phase = ConnectPhase.Connected, isRunning = true)))
    @Test fun homeIdleSmallFa() = shot("home_idle_small", true, "fa", heightDp = 640, content = home(baseState.copy(phase = ConnectPhase.Idle), LiveUiState()))
    @Test fun home360ConnectedEn() = shot("home360_connected", true, "en", heightDp = 640, widthDp = 360, content = home(baseState.copy(phase = ConnectPhase.Connected, isRunning = true)))
    @Test fun home360IdleEn() = shot("home360_idle", false, "en", heightDp = 640, widthDp = 360, content = home(baseState.copy(phase = ConnectPhase.Idle), LiveUiState()))
    @Test fun home360ConnectedFa() = shot("home360_connected", false, "fa", heightDp = 640, widthDp = 360, content = home(baseState.copy(phase = ConnectPhase.Connected, isRunning = true)))
    @Test fun home360IdleFa() = shot("home360_idle", true, "fa", heightDp = 640, widthDp = 360, content = home(baseState.copy(phase = ConnectPhase.Idle), LiveUiState()))
    @Test fun home360ConnectedRu() = shot("home360_connected", true, "ru", heightDp = 640, widthDp = 360, content = home(baseState.copy(phase = ConnectPhase.Connected, isRunning = true)))
    @Test fun home360IdleRu() = shot("home360_idle", false, "ru", heightDp = 640, widthDp = 360, content = home(baseState.copy(phase = ConnectPhase.Idle), LiveUiState()))
    @Test fun home360TipEn() = shot("home360_tip", true, "en", heightDp = 640, widthDp = 360) {
        HomeScreen(baseState.copy(phase = ConnectPhase.Idle), LiveUiState(), week, {}, {}, {}, {}, { _: AddMethod -> }, smartTip = true)
    }
    @Test fun home360SmartOffFa() = shot("home360_smartoff", false, "fa", heightDp = 640, widthDp = 360) {
        HomeScreen(baseState.copy(phase = ConnectPhase.Connected, isRunning = true), live, week, {}, {}, {}, {}, { _: AddMethod -> }, smartRoutingOn = false)
    }
    @Test fun homeConnectedLarge() = shot("home_connected", true, font = 1.4f, content = home(baseState.copy(phase = ConnectPhase.Connected, isRunning = true)))

    // ---------- Servers ----------

    private fun servers(l: String = "") = @Composable { ServersContent(baseState, rows, NoNav, {}, initialQuery = l) }

    @Test fun serversDark() = shot("servers", true, content = servers())
    @Test fun serversLightFa() = shot("servers", false, "fa", content = servers())
    @Test fun serversLarge() = shot("servers", true, font = 1.4f, content = servers())
    @Test fun serversEmpty() = shot("servers_empty", true, content = { ServersContent(MainUiState(), emptyList(), NoNav, {}) })
    @Test fun serversEmptyFaLight() = shot("servers_empty", false, "fa", content = { ServersContent(MainUiState(), emptyList(), NoNav, {}) })

    // ---------- Subscriptions ----------

    @Test fun subsDark() = shot("subs", true, heightDp = 1100) { SubsContent(subs(), noSubs, NoNav) }
    @Test fun subsLightFa() = shot("subs", false, "fa", heightDp = 1100) { SubsContent(subs(), noSubs, NoNav) }
    @Test fun subsEmpty() = shot("subs_empty", true) { SubsContent(SubsUiState(), noSubs, NoNav) }
    @Test fun subsLarge() = shot("subs", false, font = 1.4f, heightDp = 1400) { SubsContent(subs(), noSubs, NoNav) }

    // ---------- Usage ----------

    @Test fun usage7Dark() = shot("usage7", true, heightDp = 1500, still = true) { UsageContent(usage(7), live, NoNav, noUsage) }
    @Test fun usage30LightFa() = shot("usage30", false, "fa", heightDp = 1500, still = true) { UsageContent(usage(30), live, NoNav, noUsage) }
    @Test fun usageEmptyDark() = shot("usage_empty", true, heightDp = 1300, still = true) {
        UsageContent(UsageUiState(hasUsageAccess = true), LiveUiState(), NoNav, noUsage)
    }
    @Test fun usageLarge() = shot("usage7", false, font = 1.4f, heightDp = 2600, still = true) { UsageContent(usage(7), live, NoNav, noUsage) }

    // ---------- Settings ----------

    private val more = MoreUiState(theme = "0", language = "en", vpnMode = true, speedNotification = true, smartConnect = true, quickConnect = false, showAdvanced = true)

    private val eps = com.v2ray.ang.handler.ProxyEndpoints.build("127.0.0.1", 10808, 10808)
    private val eps2 = com.v2ray.ang.handler.ProxyEndpoints.build("127.0.0.1", 10808, 10809)
    @Test fun homeConnectedProxyDark() = shot("home_connected_proxy", true) {
        HomeScreen(baseState.copy(phase = ConnectPhase.Connected, isRunning = true), live, week, {}, {}, {}, {}, { _: AddMethod -> }, proxyEndpoints = eps)
    }
    @Test fun homeConnectedProxyFaLight() = shot("home_connected_proxy", false, "fa") {
        HomeScreen(baseState.copy(phase = ConnectPhase.Connected, isRunning = true), live, week, {}, {}, {}, {}, { _: AddMethod -> }, proxyEndpoints = eps2)
    }
    @Test fun moreProxyModeDark() = shot("settings_proxy", true, heightDp = 1500) { MoreContent(more.copy(vpnMode = false), NoNav, noMore, running = true, proxyEndpoints = eps2) }
    @Test fun moreReconnectPromptFaLight() = shot("settings_reconnect", false, "fa") { MoreContent(more.copy(vpnMode = false, language = "fa"), NoNav, noMore, running = true, proxyEndpoints = eps, initialReconnectPrompt = true) }
    @Test fun moreDark() = shot("settings", true, heightDp = 1500) { MoreContent(more, NoNav, noMore) }
    @Test fun moreLightFa() = shot("settings", false, "fa", heightDp = 1500) { MoreContent(more.copy(language = "fa"), NoNav, noMore) }
    @Test fun moreLarge() = shot("settings", false, font = 1.4f, heightDp = 2200) { MoreContent(more.copy(showAdvanced = false), NoNav, noMore) }

    // ---------- Sheets (rendered inline) ----------

    @Test fun sheetLimitSiteDark() = shot("sheet_limit_site", true, heightDp = 900, inlineSheets = true, still = true) {
        LimitEditorSheet(EditorTarget.NewSite, usage(), {}, {}, {}, {}, {})
    }
    @Test fun sheetLimitSiteFaLight() = shot("sheet_limit_site", false, "fa", heightDp = 900, inlineSheets = true, still = true) {
        LimitEditorSheet(EditorTarget.NewSite, usage(), {}, {}, {}, {}, {})
    }
    @Test fun sheetLimitEditAppDark() = shot("sheet_limit_app", true, heightDp = 900, inlineSheets = true, still = true) {
        LimitEditorSheet(EditorTarget.Edit(usage().rules[0].rule), usage(), {}, {}, {}, {}, {})
    }
    private val pickerApps = listOf(
        com.v2ray.ang.dto.AppInfo("Instagram", "com.instagram.android", false, 0, com.v2ray.ang.handler.AppCategory.SOCIAL),
        com.v2ray.ang.dto.AppInfo("Telegram", "org.telegram.messenger", false, 0, com.v2ray.ang.handler.AppCategory.MESSAGING),
        com.v2ray.ang.dto.AppInfo("WhatsApp", "com.whatsapp", false, 0, com.v2ray.ang.handler.AppCategory.MESSAGING),
        com.v2ray.ang.dto.AppInfo("YouTube", "com.google.android.youtube", true, 0, com.v2ray.ang.handler.AppCategory.VIDEO),
        com.v2ray.ang.dto.AppInfo("Chrome", "com.android.chrome", true, 0, com.v2ray.ang.handler.AppCategory.BROWSERS),
        com.v2ray.ang.dto.AppInfo("Clash of Clans", "com.supercell.clashofclans", false, 0, com.v2ray.ang.handler.AppCategory.GAMES),
        com.v2ray.ang.dto.AppInfo("Settings extra", "com.vendor.extra", true, 0, com.v2ray.ang.handler.AppCategory.SYSTEM),
    )
    @Test fun sheetAppPickerDark() = shot("sheet_app_picker", true, heightDp = 900, inlineSheets = true, still = true) {
        com.v2ray.ang.ui.compose.satra.SatraSheet(onDismiss = {}) {
            com.v2ray.ang.ui.compose.satra.AppPickerContent("Choose an app", pickerApps, false, emptySet(), false, {}, setOf("com.whatsapp"), onPickCategory = {})
        }
    }
    @Test fun sheetAppPickerFaLight() = shot("sheet_app_picker", false, "fa", heightDp = 900, inlineSheets = true, still = true) {
        com.v2ray.ang.ui.compose.satra.SatraSheet(onDismiss = {}) {
            com.v2ray.ang.ui.compose.satra.AppPickerContent("انتخاب برنامه", pickerApps, false, emptySet(), false, {}, onPickCategory = {})
        }
    }
    @Test fun sheetLimitCategoryRuDark() = shot("sheet_limit_category", true, "ru", heightDp = 900, inlineSheets = true, still = true) {
        LimitEditorSheet(EditorTarget.NewCategory(com.v2ray.ang.handler.AppCategory.VIDEO), usage(), {}, {}, {}, {}, {})
    }
    @Test fun sheetLimitGlobalLightEn() = shot("sheet_limit_global", false, heightDp = 900, inlineSheets = true, still = true) {
        LimitEditorSheet(EditorTarget.Global, usage(), {}, {}, {}, {}, {})
    }
    @Test fun sheetLimitGlobalFaDarkLarge() = shot("sheet_limit_global", true, "fa", font = 1.3f, heightDp = 1100, inlineSheets = true, still = true) {
        LimitEditorSheet(EditorTarget.Global, usage(), {}, {}, {}, {}, {})
    }
    @Test fun sheetSubEditDark() = shot("sheet_sub_edit", true, heightDp = 900, inlineSheets = true, still = true) {
        EditSheet(subs().rows[0], "", "", noSubs, NoNav, {}, {})
    }
    @Test fun sheetSubEditFaLight() = shot("sheet_sub_edit", false, "fa", heightDp = 900, inlineSheets = true, still = true) {
        EditSheet(subs().rows[0], "", "", noSubs, NoNav, {}, {})
    }
    @Test fun sheetSubNewDark() = shot("sheet_sub_new", true, heightDp = 800, inlineSheets = true, still = true) {
        EditSheet(null, "", "", noSubs, NoNav, {}, {})
    }
    @Test fun sheetServerActionsDark() = shot("sheet_server_actions", true, heightDp = 800, inlineSheets = true, still = true) {
        ServerActionsSheet(rows[0], NoNav, {}, {})
    }
    @Test fun sheetServerActionsFaLight() = shot("sheet_server_actions", false, "fa", heightDp = 800, inlineSheets = true, still = true) {
        ServerActionsSheet(rows[0], NoNav, {}, {})
    }

    // ---------- Legacy page kit (advanced settings, editors, per-app, about …) ----------

    @Composable
    private fun LegacyKit() {
        androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
            com.v2ray.ang.ui.compose.AppTopBar(title = androidx.compose.ui.res.stringResource(com.v2ray.ang.R.string.title_core_settings), onBackClick = {}, isLoading = true)
            androidx.compose.foundation.layout.Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                com.v2ray.ang.ui.compose.PreferenceGroupHeader("Sniffing")
                var a by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(true) }
                var b by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                com.v2ray.ang.ui.compose.SettingsSwitchItem(title = "Enable sniffing", summary = "Detect the destination domain from traffic", checked = a, onCheckedChange = { a = it })
                com.v2ray.ang.ui.compose.SettingsSwitchItem(title = "Route only", summary = "Use the sniffed domain only for routing", checked = b, onCheckedChange = { b = it })
                com.v2ray.ang.ui.compose.SettingsSwitchItem(title = "Disabled option", summary = "Not available in this mode", checked = false, onCheckedChange = {}, enabled = false)
                com.v2ray.ang.ui.compose.PreferenceGroupHeader("DNS")
                com.v2ray.ang.ui.compose.SettingsEditItem(title = "Remote DNS", value = "https://dns.google/dns-query", onValueChanged = {})
                com.v2ray.ang.ui.compose.SettingsListItem(title = "Log level", entries = listOf("warning", "info", "debug"), values = listOf("w", "i", "d"), selectedValue = "w", onSelected = {})
                com.v2ray.ang.ui.compose.SettingsMenuItem(title = "Routing rules", subtitle = "Direct, proxy and block lists", onClick = {})
                com.v2ray.ang.ui.compose.PreferenceGroupHeader("Fields")
                com.v2ray.ang.ui.compose.FormTextField(label = "Remarks", value = "Frankfurt", onValueChange = {})
                com.v2ray.ang.ui.compose.FormTextField(label = "Address", value = "", onValueChange = {}, placeholder = "example.com", isError = true, supportingText = "Required")
                com.v2ray.ang.ui.compose.PreferenceGroupHeader("Apps")
                com.v2ray.ang.ui.compose.AppListItem("Telegram", "org.telegram.messenger", null, true, {})
                com.v2ray.ang.ui.compose.AppListItem("Browser", "com.android.chrome", null, false, {})
            }
        }
    }

    @Test fun legacyKitDark() = shot("legacy_kit", true, heightDp = 1100) { LegacyKit() }
    @Test fun legacyKitLightFa() = shot("legacy_kit", false, "fa", heightDp = 1100) { LegacyKit() }
    @Test fun legacyKitLargeEn() = shot("legacy_kit", true, font = 1.4f, heightDp = 1500) { LegacyKit() }

    // ---------- pushed pages (page system) ----------

    private val sampleLogs = com.v2ray.ang.ui.logcat.createLogcatEntries(listOf(
        "10-19 12:01:02.123 I/GoLog( 1234): [Info] Xray 26.3.1 started",
        "10-19 12:01:02.456 W/GoLog( 1234): [Warning] failed to resolve dns.google: i/o timeout",
        "10-19 12:01:03.001 E/AndroidRuntime( 999): FATAL EXCEPTION: main",
        "10-19 12:01:03.001 E/AndroidRuntime( 999): FATAL EXCEPTION: main",
        "10-19 12:01:04.777 D/com.satra.ng( 1234): tunnel up via [tun >> proxy]",
    ))
    @Test fun pageLogsDark() = shot("page_logs", true, still = true) {
        com.v2ray.ang.ui.logcat.LogcatContent(sampleLogs, false, "", {}, {}, {}, {}, {}, {}, {})
    }
    @Test fun pageLogsEmptyFaLight() = shot("page_logs_empty", false, "fa", still = true) {
        com.v2ray.ang.ui.logcat.LogcatContent(emptyList(), false, "", {}, {}, {}, {}, {}, {}, {})
    }
    private val pageApps = listOf(
        com.v2ray.ang.dto.AppInfo("Telegram", "org.telegram.messenger", false, 0),
        com.v2ray.ang.dto.AppInfo("Instagram", "com.instagram.android", false, 0),
        com.v2ray.ang.dto.AppInfo("A very long application name that has to truncate", "com.example.long.name.app", false, 0),
    )
    @Test fun pageAppPickerDark() = shot("page_app_picker", true, still = true) {
        com.v2ray.ang.ui.apppicker.AppPickerScreen("Apps", pageApps, false, setOf("org.telegram.messenger"), {}, {}, {}, {}, {})
    }
    @Test fun pageAppPickerFaLargeLight() = shot("page_app_picker", false, "fa", font = 1.3f, still = true) {
        com.v2ray.ang.ui.apppicker.AppPickerScreen("برنامه‌ها", pageApps, false, emptySet(), {}, {}, {}, {}, {})
    }
    @Test fun pageTaskerDark() = shot("page_tasker", true, still = true) {
        com.v2ray.ang.ui.shortcut.TaskerScreen(
            listOf(com.v2ray.ang.ui.shortcut.TaskerItem("Current selection", "d"), com.v2ray.ang.ui.shortcut.TaskerItem("Frankfurt", "a"), com.v2ray.ang.ui.shortcut.TaskerItem("Tokyo", "b")),
            androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(true) },
            androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(1) }, {}, {})
    }
    @Test fun pageChainDark() = shot("page_chain", true, still = true) {
        com.v2ray.ang.ui.server.ProxyChainScreen("g", false, "Two hops", listOf("Frankfurt", "Tokyo"), listOf("Frankfurt", "Tokyo"), {}, { _, _ -> true }, {})
    }
    @Test fun pageChainFaLight() = shot("page_chain", false, "fa", still = true) {
        com.v2ray.ang.ui.server.ProxyChainScreen("", false, "", listOf(""), listOf("Frankfurt"), {}, { _, _ -> true }, {})
    }
    @Test fun pageAssetsDark() = shot("page_assets", true, still = true) {
        com.v2ray.ang.ui.userasset.UserAssetScreen(
            com.v2ray.ang.ui.userasset.UserAssetUiState(
                listOf(
                    com.v2ray.ang.dto.entities.AssetUrlCache("1", com.v2ray.ang.dto.entities.AssetUrlItem("geosite.dat", "https://x", locked = true)),
                    com.v2ray.ang.dto.entities.AssetUrlCache("2", com.v2ray.ang.dto.entities.AssetUrlItem("iran.dat", "https://github.com/x/iran.dat")),
                    com.v2ray.ang.dto.entities.AssetUrlCache("3", com.v2ray.ang.dto.entities.AssetUrlItem("missing.dat", "file")),
                ),
                mapOf("1" to com.v2ray.ang.ui.userasset.AssetFileMetadata(5_400_000, 1_760_000_000_000), "2" to com.v2ray.ang.ui.userasset.AssetFileMetadata(820_000, 1_760_000_000_000)),
            ),
            false, "Loyalsoldier", listOf("Loyalsoldier", "Chocolate4U"), {}, {}, {}, {}, {}, {}, {}, { _, _ -> },
        )
    }
    @Test fun pageTranslatorsLight() = shot("page_translators", false, still = true) {
        com.v2ray.ang.ui.TranslatorsScreen {}
    }
    @Test fun serversPullHintLightFa() = shot("servers_pull_hint", false, "fa", still = true) {
        ServersContent(baseState, rows, NoNav, {}, pullHint = true)
    }
}
