package com.v2ray.ang.ui.settings

import android.app.Application
import android.content.Intent
import android.provider.Settings
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SatraLocalPortPolicy
import com.v2ray.ang.root.RootManager
import com.v2ray.ang.ui.base.BaseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

data class SettingsHubUiState(
    val perAppProxyEnabled: Boolean = false,
    val mode: String = AppConfig.VPN,
    val socksPort: String = AppConfig.PORT_SOCKS,
    val localDnsEnabled: Boolean = AppConfig.DEFAULT_LOCAL_DNS_ENABLED,
    val useHevTun: Boolean = AppConfig.DEFAULT_USE_HEV_TUNNEL,
    val ipv6Enabled: Boolean = AppConfig.DEFAULT_IPV6_ENABLED,
)

class SettingsViewModel(application: Application) : BaseViewModel(application) {

    private val _systemVpnSettingsAvailable = MutableStateFlow(false)
    val systemVpnSettingsAvailable = _systemVpnSettingsAvailable.asStateFlow()

    private val _hubState = MutableStateFlow(readHubState())
    val hubState: StateFlow<SettingsHubUiState> = _hubState.asStateFlow()

    fun refreshHubState() {
        _hubState.value = readHubState()
    }

    suspend fun refreshSystemVpnSettingsAvailability() {
        _systemVpnSettingsAvailable.value = withContext(Dispatchers.IO) {
            // Android exposes the VPN page, not a direct link to the Always-on VPN switch.
            Intent(Settings.ACTION_VPN_SETTINGS).resolveActivity(getApplication<Application>().packageManager) != null
        }
    }

    /**
     * Checks for root access and requests it if necessary.
     * Updates [isLoading] during the process.
     */
    fun checkAndRequestRoot(onSuccess: () -> Unit) {
        launchLoading {
            val hasRoot = withContext(Dispatchers.IO) {
                RootManager.refresh()
            }
            if (hasRoot) {
                onSuccess()
            } else {
                toastError(R.string.toast_root_required)
            }
        }
    }

    fun setPerAppProxyEnabled(enabled: Boolean) {
        MmkvManager.encodeSettings(AppConfig.PREF_PER_APP_PROXY, enabled)
        _hubState.update { it.copy(perAppProxyEnabled = enabled) }
    }

    /**
     * Re-apply Satra tunnel defaults (ports, MTU, HEV/local DNS/IPv6/UDP) for users who want a reset.
     * Does not wipe unrelated preferences.
     */
    fun applySatraTunnelPresets() {
        MmkvManager.encodeSettings(AppConfig.PREF_SOCKS_PORT, AppConfig.PORT_SOCKS)
        MmkvManager.encodeSettings(AppConfig.PREF_LOCAL_DNS_PORT, AppConfig.PORT_LOCAL_DNS)
        MmkvManager.encodeSettings(AppConfig.PREF_VPN_MTU, AppConfig.VPN_MTU.toString())
        MmkvManager.encodeSettings(AppConfig.PREF_LOCAL_DNS_ENABLED, AppConfig.DEFAULT_LOCAL_DNS_ENABLED)
        MmkvManager.encodeSettings(AppConfig.PREF_SNIFFING_ENABLED, AppConfig.DEFAULT_SNIFFING_ENABLED)
        MmkvManager.encodeSettings(AppConfig.PREF_USE_HEV_TUNNEL, AppConfig.DEFAULT_USE_HEV_TUNNEL)
        MmkvManager.encodeSettings(AppConfig.PREF_SOCKS_ENABLE_UDP, AppConfig.DEFAULT_SOCKS_ENABLE_UDP)
        MmkvManager.encodeSettings(AppConfig.PREF_APPEND_HTTP_PROXY, AppConfig.DEFAULT_APPEND_HTTP_PROXY)
        MmkvManager.encodeSettings(AppConfig.PREF_IPV6_ENABLED, AppConfig.DEFAULT_IPV6_ENABLED)
        MmkvManager.encodeSettings(AppConfig.PREF_PER_APP_PROXY, false)
        MmkvManager.encodeSettings(AppConfig.PREF_MUX_ENABLED, false)
        MmkvManager.encodeSettings(AppConfig.PREF_PROXY_SHARING, false)
        MmkvManager.encodeSettings(AppConfig.PREF_MODE, AppConfig.VPN)
        MmkvManager.encodeSettings(AppConfig.PREF_SATRA_LOCAL_PORTS_MIGRATED, true)
        MmkvManager.encodeSettings(AppConfig.PREF_SATRA_TUNNEL_DEFAULTS_SEEDED, true)
        refreshHubState()
        toastSuccess(R.string.satra_settings_tunnel_reset_done)
    }

    /**
     * Validates if the given string is a valid observatory duration.
     * Shows error toast if invalid.
     * @return The trimmed value if valid, null otherwise.
     */
    fun validateObservatoryDuration(value: String): String? {
        val duration = value.trim()
        return if (AppConfig.OBSERVATORY_DURATION_PATTERN.matches(duration)) {
            duration
        } else {
            toastError(R.string.toast_invalid_observatory_duration)
            null
        }
    }

    /**
     * Validates if the given string is a valid observatory sampling value.
     * Shows error toast if invalid.
     * @return The value if valid, null otherwise.
     */
    fun validateObservatorySampling(value: String): String? {
        val sampling = value.trim().toIntOrNull()?.takeIf { it > 0 }
        return if (sampling != null) {
            sampling.toString()
        } else {
            toastError(R.string.toast_invalid_observatory_sampling)
            null
        }
    }

    private fun readHubState(): SettingsHubUiState {
        val socksStored = MmkvManager.decodeSettingsString(AppConfig.PREF_SOCKS_PORT)
        val socksPort = if (SatraLocalPortPolicy.shouldReplaceWithDefault(socksStored, AppConfig.LEGACY_PORT_SOCKS)) {
            AppConfig.PORT_SOCKS
        } else {
            socksStored ?: AppConfig.PORT_SOCKS
        }
        return SettingsHubUiState(
            perAppProxyEnabled = MmkvManager.decodeSettingsBool(AppConfig.PREF_PER_APP_PROXY, false),
            mode = MmkvManager.decodeSettingsString(AppConfig.PREF_MODE, AppConfig.VPN) ?: AppConfig.VPN,
            socksPort = socksPort,
            localDnsEnabled = MmkvManager.decodeSettingsBool(
                AppConfig.PREF_LOCAL_DNS_ENABLED,
                AppConfig.DEFAULT_LOCAL_DNS_ENABLED,
            ),
            useHevTun = MmkvManager.decodeSettingsBool(
                AppConfig.PREF_USE_HEV_TUNNEL,
                AppConfig.DEFAULT_USE_HEV_TUNNEL,
            ),
            ipv6Enabled = MmkvManager.decodeSettingsBool(
                AppConfig.PREF_IPV6_ENABLED,
                AppConfig.DEFAULT_IPV6_ENABLED,
            ),
        )
    }
}
