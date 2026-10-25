package com.v2ray.ang.ui.apppicker

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.dto.AppInfo
import com.v2ray.ang.util.AppManagerUtil
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator

/** Launchable apps for the in-app pickers (limits, split tunnelling). Loaded once, sorted by name. */
class AppListViewModel(app: Application) : AndroidViewModel(app) {
    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps.asStateFlow()
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    fun ensureLoaded() {
        if (_apps.value.isNotEmpty() || _loading.value) return
        _loading.value = true
        viewModelScope.launch {
            try {
                val list = withContext(Dispatchers.IO) {
                    val collator = Collator.getInstance()
                    AppManagerUtil.loadNetworkAppList(getApplication())
                        .filter { it.packageName != getApplication<Application>().packageName }
                        .sortedWith { a, b -> collator.compare(a.appName, b.appName) }
                }
                _apps.value = list
            } catch (e: Exception) {
                LogUtil.e("AppListViewModel", "load failed", e)
            } finally {
                _loading.value = false
            }
        }
    }
}
