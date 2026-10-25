package com.v2ray.ang.handler

import com.v2ray.ang.AppConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NotificationPrefState(
    val showSpeed: Boolean = true,
    val showLocation: Boolean = true,
    val actionStop: Boolean = true,
    val actionRestart: Boolean = true,
)

object NotificationPrefs {
    private val _state = MutableStateFlow(load())
    val state: StateFlow<NotificationPrefState> = _state.asStateFlow()

    private fun load() = NotificationPrefState(
        showSpeed = MmkvManager.decodeSettingsBool(AppConfig.PREF_NOTIF_SHOW_SPEED, true),
        showLocation = MmkvManager.decodeSettingsBool(AppConfig.PREF_NOTIF_SHOW_LOCATION, true),
        actionStop = MmkvManager.decodeSettingsBool(AppConfig.PREF_NOTIF_ACTION_STOP, true),
        actionRestart = MmkvManager.decodeSettingsBool(AppConfig.PREF_NOTIF_ACTION_RESTART, true),
    )

    fun update(transform: (NotificationPrefState) -> NotificationPrefState) {
        val next = transform(_state.value)
        MmkvManager.encodeSettings(AppConfig.PREF_NOTIF_SHOW_SPEED, next.showSpeed)
        MmkvManager.encodeSettings(AppConfig.PREF_NOTIF_SHOW_LOCATION, next.showLocation)
        MmkvManager.encodeSettings(AppConfig.PREF_NOTIF_ACTION_STOP, next.actionStop)
        MmkvManager.encodeSettings(AppConfig.PREF_NOTIF_ACTION_RESTART, next.actionRestart)
        _state.value = next
    }

    fun refresh() {
        _state.value = load()
    }
}
