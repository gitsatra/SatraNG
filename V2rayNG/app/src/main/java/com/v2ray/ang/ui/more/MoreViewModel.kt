package com.v2ray.ang.ui.more

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.AppLocaleManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.ui.compose.ThemeManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class MoreUiState(
    /** "0" follow system, "1" light, "2" dark. */
    val theme: String = "0",
    val language: String = "auto",
    val vpnMode: Boolean = true,
    val speedNotification: Boolean = false,
    val smartConnect: Boolean = true,
    val autoSelect: Boolean = true,
    val quickConnect: Boolean = false,
    val showAdvanced: Boolean = false,
    val subUpdateOnOpen: Boolean = true,
)

/** State + writes for the Settings tab; keeps MMKV out of the composables. */
interface MoreActions {
    fun setTheme(mode: String)
    fun setLanguage(code: String)
    fun setVpnMode(vpn: Boolean)
    fun setSpeedNotification(on: Boolean)
    fun setSmartConnect(on: Boolean)
    fun setAutoSelect(on: Boolean)
    fun setQuickConnect(on: Boolean)
    fun setShowAdvanced(on: Boolean)
    fun setSubUpdateOnOpen(on: Boolean) = Unit
}

class MoreViewModel(app: Application) : AndroidViewModel(app), MoreActions {
    private val _state = MutableStateFlow(read())
    val state: StateFlow<MoreUiState> = _state.asStateFlow()

    private fun read() = MoreUiState(
        theme = MmkvManager.decodeSettingsString(AppConfig.PREF_UI_MODE_NIGHT, "0") ?: "0",
        language = MmkvManager.decodeSettingsString(AppConfig.PREF_LANGUAGE, "auto") ?: "auto",
        vpnMode = (MmkvManager.decodeSettingsString(AppConfig.PREF_MODE, AppConfig.VPN) ?: AppConfig.VPN) == AppConfig.VPN,
        speedNotification = MmkvManager.decodeSettingsBool(AppConfig.PREF_SPEED_ENABLED, false),
        smartConnect = MmkvManager.decodeSettingsBool(AppConfig.PREF_AUTO_CONNECT, true),
        autoSelect = MmkvManager.decodeSettingsBool(AppConfig.PREF_AUTO_SELECT, true),
        quickConnect = MmkvManager.decodeSettingsBool(AppConfig.PREF_QUICK_CONNECT, false),
        showAdvanced = MmkvManager.decodeSettingsBool(AppConfig.PREF_SHOW_ADVANCED, false),
        subUpdateOnOpen = MmkvManager.decodeSettingsBool(AppConfig.PREF_SATRA_SUB_UPDATE_ON_OPEN, true),
    )

    fun refresh() { _state.value = read() }

    override fun setTheme(mode: String) {
        ThemeManager.setThemeMode(mode)
        _state.update { it.copy(theme = mode) }
    }

    override fun setLanguage(code: String) {
        _state.update { it.copy(language = code) }
        AppLocaleManager.setApplicationLanguage(code)
    }

    override fun setVpnMode(vpn: Boolean) {
        MmkvManager.encodeSettings(AppConfig.PREF_MODE, if (vpn) AppConfig.VPN else "Proxy only")
        SettingsChangeManager.notifySettingChanged(AppConfig.PREF_MODE)
        _state.update { it.copy(vpnMode = vpn) }
    }

    override fun setSpeedNotification(on: Boolean) {
        MmkvManager.encodeSettings(AppConfig.PREF_SPEED_ENABLED, on)
        SettingsChangeManager.notifySettingChanged(AppConfig.PREF_SPEED_ENABLED)
        _state.update { it.copy(speedNotification = on) }
    }

    override fun setSubUpdateOnOpen(on: Boolean) {
        MmkvManager.encodeSettings(AppConfig.PREF_SATRA_SUB_UPDATE_ON_OPEN, on)
        _state.update { it.copy(subUpdateOnOpen = on) }
    }

    override fun setAutoSelect(on: Boolean) {
        MmkvManager.encodeSettings(AppConfig.PREF_AUTO_SELECT, on)
        _state.update { it.copy(autoSelect = on) }
    }

    override fun setSmartConnect(on: Boolean) {
        MmkvManager.encodeSettings(AppConfig.PREF_AUTO_CONNECT, on)
        _state.update { it.copy(smartConnect = on) }
    }

    override fun setQuickConnect(on: Boolean) {
        MmkvManager.encodeSettings(AppConfig.PREF_QUICK_CONNECT, on)
        _state.update { it.copy(quickConnect = on) }
    }

    override fun setShowAdvanced(on: Boolean) {
        MmkvManager.encodeSettings(AppConfig.PREF_SHOW_ADVANCED, on)
        _state.update { it.copy(showAdvanced = on) }
    }
}
