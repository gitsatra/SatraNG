package com.v2ray.ang.ui.main

import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import android.content.ActivityNotFoundException
import android.provider.Settings
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.AngApplication
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.core.LauncherManager
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.enums.PermissionType
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.ui.AboutActivity
import com.v2ray.ang.ui.backup.BackupActivity
import com.v2ray.ang.ui.base.HelperBaseComponentActivity
import com.v2ray.ang.ui.checkupdate.CheckUpdateActivity
import com.v2ray.ang.ui.logcat.LogcatActivity
import com.v2ray.ang.ui.perappproxy.PerAppProxyActivity
import com.v2ray.ang.ui.routing.RoutingSettingActivity
import com.v2ray.ang.ui.server.ProfileEditorResult
import com.v2ray.ang.ui.server.ServerCustomConfigActivity
import com.v2ray.ang.ui.server.ServerGroupActivity
import com.v2ray.ang.ui.server.ServerHttpActivity
import com.v2ray.ang.ui.server.ServerHysteria2Activity
import com.v2ray.ang.ui.server.ServerProxyChainActivity
import com.v2ray.ang.ui.server.ServerShadowsocksActivity
import com.v2ray.ang.ui.server.ServerSocksActivity
import com.v2ray.ang.ui.server.ServerTrojanActivity
import com.v2ray.ang.ui.server.ServerVlessActivity
import com.v2ray.ang.ui.server.ServerVmessActivity
import com.v2ray.ang.ui.server.ServerWireguardActivity
import com.v2ray.ang.ui.settings.SettingsActivity
import com.v2ray.ang.handler.AppUsageReader
import com.v2ray.ang.handler.UsageLimitEngine
import com.v2ray.ang.handler.UsageNotifier
import com.v2ray.ang.ui.app.AppNav
import com.v2ray.ang.ui.app.SatraApp
import com.v2ray.ang.ui.app.Tabs
import com.v2ray.ang.ui.settings.SettingsDestination
import com.v2ray.ang.ui.subscription.SubEditActivity
import com.v2ray.ang.ui.subs.SubsViewModel
import com.v2ray.ang.ui.usage.UsageViewModel
import kotlinx.coroutines.delay
import com.v2ray.ang.ui.userasset.UserAssetActivity
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : HelperBaseComponentActivity(), AppNav {

    private val mainViewModel: MainViewModel by viewModels {
        MainViewModel.Factory(application, MainRepository(application as AngApplication))
    }

    private val subsViewModel: SubsViewModel by viewModels()
    private val usageViewModel: UsageViewModel by viewModels()
    private var openTab by mutableIntStateOf(Tabs.Home)

    private val requestVpnPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) startV2Ray() else mainViewModel.cancelConnect()
        }

    private val subEditLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            subsViewModel.reload()
            mainViewModel.onAction(MainAction.RefreshGroups)
        }

    private val profileEditorLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != RESULT_OK) return@registerForActivityResult
            val data = result.data ?: return@registerForActivityResult
            val action = data.getStringExtra(ProfileEditorResult.EXTRA_ACTION)
                ?: return@registerForActivityResult
            if (action != ProfileEditorResult.ACTION_SAVED &&
                action != ProfileEditorResult.ACTION_DELETED
            ) return@registerForActivityResult
            val restartService = data.getBooleanExtra(
                ProfileEditorResult.EXTRA_RESTART_SERVICE, false
            )
            val selectedProfileSaved = action == ProfileEditorResult.ACTION_SAVED &&
                    data.getStringExtra(ProfileEditorResult.EXTRA_GUID) == mainViewModel.uiState.value.selectedGuid
            mainViewModel.onAction(MainAction.RefreshGroups)
            if (restartService || selectedProfileSaved) LauncherManager.restartService(this)
        }

    private val settingsActivityLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            val restartService = SettingsChangeManager.consumeRestartService()
            val refreshGroups = SettingsChangeManager.consumeSetupGroupTab()
            mainViewModel.refreshUiSettings()
            if (refreshGroups) mainViewModel.onAction(MainAction.RefreshGroups)
            if (restartService) LauncherManager.restartService(this)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        // Brand splash stays a short beat (logo still readable) instead of a fixed 0.5s stall.
        val keepUntil = SystemClock.elapsedRealtime() + 220L
        splash.setKeepOnScreenCondition { SystemClock.elapsedRealtime() < keepUntil }
        super.onCreate(savedInstanceState)
        handleModeIntent(intent)
        mainViewModel.onAction(MainAction.Initialize)

        checkAndRequestPermission(PermissionType.POST_NOTIFICATIONS) {}
        applyOpenTab(intent)
        if (savedInstanceState == null && MmkvManager.decodeSettingsBool(AppConfig.PREF_QUICK_CONNECT, false)) {
            lifecycleScope.launch {
                delay(900)
                val st = mainViewModel.uiState.value
                if (!st.isRunning && st.phase == ConnectPhase.Idle && !st.selectedGuid.isNullOrEmpty()) handleFabAction()
            }
        }

        lifecycleScope.launch {
            var last = mainViewModel.smartConnectRequest.value
            mainViewModel.smartConnectRequest.collect { tick ->
                if (tick > last) {
                    last = tick
                    if (mainViewModel.uiState.value.isRunning) LauncherManager.restartService(this@MainActivity)
                    else requestServiceStart()
                }
            }
        }

        lifecycleScope.launch {
            var last = mainViewModel.autoReconnectRequest.value
            mainViewModel.autoReconnectRequest.collect { tick ->
                if (tick > last) {
                    last = tick
                    if (!mainViewModel.uiState.value.isRunning) {
                        requestServiceStart()
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleModeIntent(intent)
        setIntent(intent)
        applyOpenTab(intent)
    }

    private fun applyOpenTab(intent: Intent?) {
        if (intent?.getStringExtra(UsageNotifier.EXTRA_OPEN_TAB) == "usage") openTab = Tabs.Usage
        handleToggleIntent(intent)
    }

    /** Tile / widget / shortcut asked to connect (usually because VPN consent was needed). */
    private fun handleToggleIntent(intent: Intent?) {
        val action = intent?.action ?: return
        if (action != com.v2ray.ang.core.ToggleSurfaces.ACTION_CONNECT && action != com.v2ray.ang.core.ToggleSurfaces.ACTION_SMART_CONNECT) return
        intent.action = Intent.ACTION_MAIN // handle once (not again on rotation)
        openTab = Tabs.Home
        lifecycleScope.launch {
            delay(350L)
            if (action == com.v2ray.ang.core.ToggleSurfaces.ACTION_SMART_CONNECT) {
                if (!mainViewModel.uiState.value.isRunning) action(MainAction.SmartConnect)
            } else if (!mainViewModel.uiState.value.isRunning) {
                requestServiceStart()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // The selected config may have changed: keep the widget/tile label in step.
        com.v2ray.ang.core.ToggleSurfaces.refresh(applicationContext, mainViewModel.uiState.value.isRunning)
    }

    private fun handleModeIntent(i: Intent?) {
        if (i?.getBooleanExtra(com.v2ray.ang.handler.NotificationManager.EXTRA_OPEN_MODES, false) == true) {
            i.removeExtra(com.v2ray.ang.handler.NotificationManager.EXTRA_OPEN_MODES)
            com.v2ray.ang.ui.home.ModeSheetRequest.open.value = true
        }
    }

    override fun onResume() {
        super.onResume()
        com.v2ray.ang.handler.ModeEngine.current()
        // Language can also change from Android Settings (per-app language); keep launcher shortcuts in step.
        runCatching { com.v2ray.ang.handler.AppLocaleManager.syncFromSystem(this) }
        val restart = SettingsChangeManager.consumeRestartService()
        val refresh = SettingsChangeManager.consumeSetupGroupTab()
        mainViewModel.refreshUiSettings()
        if (refresh) {
            mainViewModel.onAction(MainAction.RefreshGroups)
            subsViewModel.reload()
        }
        if (restart) LauncherManager.restartService(this)
        if (mainViewModel.uiState.value.isRunning) {
            mainViewModel.refreshExitGeoForSelectedServer(delayMs = 0L)
        }
    }

    @Composable
    override fun ScreenContent() {
        BackHandler { moveTaskToBack(false) }
        SatraApp(
            mainVm = mainViewModel,
            subsVm = subsViewModel,
            usageVm = usageViewModel,
            nav = this,
            initialTab = openTab,
        )
    }

    // ---- AppNav ----

    override fun action(action: MainAction) {
        when (action) {
            MainAction.ToggleService -> handleFabAction()
            MainAction.TestCurrentServer -> handleLayoutTestClick()
            MainAction.ImportQRcode -> importQRcode()
            MainAction.ImportClipboard -> importClipboard()
            MainAction.ImportConfigLocal -> importConfigLocal()
            is MainAction.ImportManually -> importManually(action.type)
            MainAction.RestartService -> LauncherManager.restartServiceOrStart(this, ::requestServiceStart)
            MainAction.LocateSelectedServer -> mainViewModel.triggerLocateSelectedServer()
            is MainAction.SelectServer -> setSelectServer(action.guid)
            is MainAction.EditServer -> editServer(action.guid, action.profile)
            is MainAction.ShareClipboard -> {
                if (shareToClipboard(action.guid)) toastSuccess(R.string.toast_success) else toastError(R.string.toast_failure)
            }
            is MainAction.ShareFullContent -> shareFullContentAsync(action.guid)
            else -> mainViewModel.onAction(action)
        }
    }

    override fun navigate(destination: MainDestination) = navigateTo(destination)

    override fun openSettingsPage(destination: SettingsDestination) {
        settingsActivityLauncher.launch(
            Intent(this, SettingsActivity::class.java).putExtra(SettingsActivity.EXTRA_DESTINATION, destination.name),
        )
    }

    override fun editSubscription(subId: String?) {
        subEditLauncher.launch(Intent(this, SubEditActivity::class.java).apply { subId?.let { putExtra("subId", it) } })
    }

    override fun scanQr(onResult: (String) -> Unit) {
        launchQRCodeScanner { result -> if (!result.isNullOrBlank()) onResult(result) }
    }

    override fun openUsageAccess() {
        try {
            startActivity(AppUsageReader.accessSettingsIntent(this))
        } catch (e: ActivityNotFoundException) {
            LogUtil.e(AppConfig.TAG, "Usage access settings unavailable", e)
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    override fun enableAppEnforcement() {
        UsageLimitEngine.enableAppEnforcement()
        usageViewModel.refreshApps()
        LauncherManager.restartIfRunning(this) { toast(R.string.sx_engine_applied) }
    }

    override fun openSystemVpnSettings() {
        try {
            startActivity(Intent(Settings.ACTION_VPN_SETTINGS))
        } catch (e: ActivityNotFoundException) {
            LogUtil.e(AppConfig.TAG, "Cannot open system VPN settings", e)
            toastError(R.string.toast_system_vpn_settings_unavailable)
        }
    }

    override fun share(text: String) {
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text),
                null,
            ),
        )
    }

    override fun copy(text: String) {
        Utils.setClipboard(this, text)
        toastSuccess(R.string.toast_success)
    }

    override fun reconnectForModeChange() {
        // The prompt replaces the silent restart-on-resume for this change.
        SettingsChangeManager.consumeRestartService()
        if (!mainViewModel.uiState.value.isRunning) return
        if (SettingsManager.isVpnMode() && VpnService.prepare(this) != null) {
            // Proxy-only -> VPN without VPN permission yet: stop, then go through the permission flow.
            LauncherManager.stopService(this)
            lifecycleScope.launch {
                delay(700L)
                requestServiceStart()
            }
        } else {
            LauncherManager.restartIfRunning(this) { toast(R.string.sx_reconnected) }
        }
    }

    private fun shareToClipboard(guid: String): Boolean =
        AngConfigManager.share2Clipboard(this, guid) == 0

    private fun shareFullContentAsync(guid: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val result = AngConfigManager.shareFullContent2Clipboard(this@MainActivity, guid)
            withContext(Dispatchers.Main) {
                if (result == 0) toastSuccess(R.string.toast_success)
                else toastError(R.string.toast_failure)
            }
        }
    }

    private fun navigateTo(destination: MainDestination) {
        val intent = when (destination) {
            MainDestination.PerAppProxy -> Intent(this, PerAppProxyActivity::class.java)
            MainDestination.Routing -> Intent(this, RoutingSettingActivity::class.java)
            MainDestination.UserAssets -> Intent(this, UserAssetActivity::class.java)
            MainDestination.Settings -> Intent(this, SettingsActivity::class.java)
            MainDestination.Logcat -> Intent(this, LogcatActivity::class.java)
            MainDestination.CheckUpdate -> Intent(this, CheckUpdateActivity::class.java)
            MainDestination.BackupRestore -> Intent(this, BackupActivity::class.java)
            MainDestination.About -> Intent(this, AboutActivity::class.java)
            MainDestination.Promotion -> {
                Utils.openUri(
                    this,
                    "${Utils.decode(AppConfig.APP_PROMOTION_URL)}?t=${System.currentTimeMillis()}"
                )
                return
            }
        }
        settingsActivityLauncher.launch(intent)
    }

    private fun handleFabAction() {
        val state = mainViewModel.uiState.value
        when {
            state.isRunning -> {
                mainViewModel.beginDisconnect()
                mainViewModel.markUserRequestedStop()
                LauncherManager.stopService(this)
            }
            state.phase == ConnectPhase.Connecting -> {
                // Second tap while connecting cancels the attempt.
                mainViewModel.cancelConnect()
                mainViewModel.markUserRequestedStop()
                LauncherManager.stopService(this)
            }
            state.phase == ConnectPhase.Disconnecting -> Unit
            else -> {
                mainViewModel.beginConnect()
                mainViewModel.markUserRequestedStart()
                mainViewModel.prepareConnectGuid()
                requestServiceStart()
            }
        }
    }

    private fun requestServiceStart() {
        if (!SettingsManager.isVpnMode()) {
            startV2Ray()
            return
        }
        val intent = VpnService.prepare(this)
        if (intent == null) startV2Ray() else requestVpnPermission.launch(intent)
    }

    private fun handleLayoutTestClick() {
        mainViewModel.pingSelected()
    }

    private fun startV2Ray() {
        if (mainViewModel.uiState.value.selectedGuid.isNullOrEmpty()) {
            toast(R.string.title_file_chooser)
            mainViewModel.cancelConnect()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN
        ) {
            checkAndRequestPermission(PermissionType.ACCESS_LOCAL_NETWORK) {}
        }
        LauncherManager.startService(this)
    }

    private fun importManually(createConfigType: Int) {
        val intent = when (createConfigType) {
            EConfigType.POLICYGROUP.value -> Intent(this, ServerGroupActivity::class.java)
            EConfigType.PROXYCHAIN.value -> Intent(this, ServerProxyChainActivity::class.java)
            EConfigType.VMESS.value -> Intent(this, ServerVmessActivity::class.java)
            EConfigType.VLESS.value -> Intent(this, ServerVlessActivity::class.java)
            EConfigType.SHADOWSOCKS.value -> Intent(this, ServerShadowsocksActivity::class.java)
            EConfigType.SOCKS.value -> Intent(this, ServerSocksActivity::class.java)
            EConfigType.HTTP.value -> Intent(this, ServerHttpActivity::class.java)
            EConfigType.TROJAN.value -> Intent(this, ServerTrojanActivity::class.java)
            EConfigType.WIREGUARD.value -> Intent(this, ServerWireguardActivity::class.java)
            EConfigType.HYSTERIA2.value -> Intent(this, ServerHysteria2Activity::class.java)
            else -> Intent(this, ServerHttpActivity::class.java).apply {
                putExtra("createConfigType", createConfigType)
            }
        }.apply {
            putExtra("subscriptionId", mainViewModel.uiState.value.selectedGroupId)
        }
        profileEditorLauncher.launch(intent)
    }

    private fun importQRcode() {
        launchQRCodeScanner { scanResult ->
            if (scanResult != null) {
                mainViewModel.onAction(MainAction.ImportBatchConfig(scanResult))
            }
        }
    }

    private fun importClipboard() {
        try {
            val text = Utils.getClipboard(this)
            mainViewModel.onAction(MainAction.ImportBatchConfig(text))
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to import config from clipboard", e)
        }
    }

    private fun importConfigLocal() {
        launchFileChooser { uri ->
            if (uri == null) return@launchFileChooser
            try {
                contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                    mainViewModel.onAction(MainAction.ImportBatchConfig(reader.readText()))
                }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to read content from URI", e)
            }
        }
    }

    private fun editServer(guid: String, profile: ProfileItem) {
        val activityClass = when (profile.configType) {
            EConfigType.CUSTOM -> ServerCustomConfigActivity::class.java
            EConfigType.POLICYGROUP -> ServerGroupActivity::class.java
            EConfigType.PROXYCHAIN -> ServerProxyChainActivity::class.java
            EConfigType.VMESS -> ServerVmessActivity::class.java
            EConfigType.VLESS -> ServerVlessActivity::class.java
            EConfigType.SHADOWSOCKS -> ServerShadowsocksActivity::class.java
            EConfigType.SOCKS -> ServerSocksActivity::class.java
            EConfigType.HTTP -> ServerHttpActivity::class.java
            EConfigType.TROJAN -> ServerTrojanActivity::class.java
            EConfigType.WIREGUARD -> ServerWireguardActivity::class.java
            EConfigType.HYSTERIA2 -> ServerHysteria2Activity::class.java
            else -> ServerHttpActivity::class.java
        }
        val intent = Intent(this, activityClass).apply {
            putExtra("guid", guid)
            putExtra("isRunning", mainViewModel.uiState.value.isRunning)
            putExtra("createConfigType", profile.configType.value)
            putExtra("subscriptionId", mainViewModel.uiState.value.selectedGroupId)
        }
        profileEditorLauncher.launch(intent)
    }

    private fun setSelectServer(guid: String) {
        val selected = mainViewModel.uiState.value.selectedGuid
        mainViewModel.markManualPick()
        if (guid != selected) {
            mainViewModel.updateSelectedGuid(guid)
            LauncherManager.restartService(this)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BUTTON_B) {
            moveTaskToBack(false)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
