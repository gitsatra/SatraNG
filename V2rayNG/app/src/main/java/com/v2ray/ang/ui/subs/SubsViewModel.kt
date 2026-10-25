package com.v2ray.ang.ui.subs

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.SubscriptionCache
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.handler.SubscriptionUpdater
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.QRCodeDecoder
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SubRow(
    val guid: String,
    val item: SubscriptionItem,
    val configCount: Int,
) {
    /** The name the user gave it; the provider's own title only when there is none. */
    val title: String get() = item.remarks.takeIf { it.isNotBlank() } ?: item.profileTitle.orEmpty()
}

/** Outcome of the last update, shown once as a snackbar-like banner. */
sealed interface SubsMessage {
    data class Updated(val name: String, val configs: Int) : SubsMessage
    data class UpdatedAll(val ok: Int, val failed: Int, val configs: Int) : SubsMessage
    data class Failed(val name: String) : SubsMessage
    data object NothingToUpdate : SubsMessage
    data object Saved : SubsMessage
    data object Deleted : SubsMessage
    data object InvalidUrl : SubsMessage
    data object InvalidName : SubsMessage
    data object InvalidInterval : SubsMessage
}

data class SubsUiState(
    val rows: List<SubRow> = emptyList(),
    val updating: Set<String> = emptySet(),
    val updatingAll: Boolean = false,
    val message: SubsMessage? = null,
    val qr: Bitmap? = null,
    val qrTitle: String = "",
)

/** What the Subscriptions screen can ask for; lets the screen render without a ViewModel (previews, tests). */
interface SubsActions {
    fun reload()
    fun updateAll()
    fun updateOne(guid: String)
    fun setEnabled(guid: String, enabled: Boolean)
    fun delete(guid: String)
    fun save(guid: String?, name: String, url: String, autoUpdate: Boolean, intervalMinutes: Int, enabled: Boolean): SubsMessage?
    fun showQr(row: SubRow)
    fun dismissQr()
    fun messageShown()
}

class SubsViewModel(app: Application) : AndroidViewModel(app), SubsActions {
    private val _state = MutableStateFlow(SubsUiState())
    val state: StateFlow<SubsUiState> = _state.asStateFlow()

    init {
        reload()
        // Reload whenever subscriptions/servers change anywhere in the app (imports, updates).
        viewModelScope.launch {
            SettingsChangeManager.dataChanges.collect { reload() }
        }
    }

    override fun reload() {
        viewModelScope.launch(Dispatchers.IO) {
            val rows = MmkvManager.decodeSubscriptions().map { it.toRow() }
            _state.update { it.copy(rows = rows) }
        }
    }

    private fun SubscriptionCache.toRow() = SubRow(guid, subscription.ensureDisplayDefaults(), MmkvManager.decodeServerList(guid).size)

    override fun messageShown() = _state.update { it.copy(message = null) }

    override fun updateOne(guid: String) {
        if (guid in _state.value.updating) return
        val cache = MmkvManager.decodeSubscriptions().firstOrNull { it.guid == guid } ?: return
        _state.update { it.copy(updating = it.updating + guid) }
        viewModelScope.launch {
            val msg = try {
                val result = withContext(Dispatchers.IO) { AngConfigManager.updateConfigViaSub(cache) }
                if (result.successCount > 0) {
                    SettingsChangeManager.makeSetupGroupTab()
                    SubsMessage.Updated(cache.subscription.remarks, result.configCount)
                } else if (result.skipCount > 0) SubsMessage.NothingToUpdate
                else SubsMessage.Failed(cache.subscription.remarks)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Subscription update failed", e)
                SubsMessage.Failed(cache.subscription.remarks)
            }
            val rows = withContext(Dispatchers.IO) { MmkvManager.decodeSubscriptions().map { it.toRow() } }
            _state.update { it.copy(updating = it.updating - guid, rows = rows, message = msg) }
        }
    }

    override fun updateAll() {
        if (_state.value.updatingAll) return
        _state.update { it.copy(updatingAll = true) }
        viewModelScope.launch {
            val msg = try {
                val r = withContext(Dispatchers.IO) { AngConfigManager.updateConfigViaSubAll() }
                if (r.successCount + r.failureCount == 0) SubsMessage.NothingToUpdate
                else {
                    SettingsChangeManager.makeSetupGroupTab()
                    SubsMessage.UpdatedAll(r.successCount, r.failureCount, r.configCount)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Subscription update all failed", e)
                SubsMessage.Failed("")
            }
            val rows = withContext(Dispatchers.IO) { MmkvManager.decodeSubscriptions().map { it.toRow() } }
            _state.update { it.copy(updatingAll = false, rows = rows, message = msg) }
        }
    }

    override fun setEnabled(guid: String, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val item = MmkvManager.decodeSubscription(guid) ?: return@launch
            item.enabled = enabled
            MmkvManager.encodeSubscription(guid, item)
            SettingsChangeManager.makeSetupGroupTab()
            reload()
        }
    }

    /**
     * Creates or edits a subscription. Returns a validation message, or null when saved.
     * [intervalMinutes] is only used when [autoUpdate] is on.
     */
    override fun save(
        guid: String?,
        name: String,
        url: String,
        autoUpdate: Boolean,
        intervalMinutes: Int,
        enabled: Boolean,
    ): SubsMessage? {
        val cleanName = name.trim()
        val cleanUrl = url.trim()
        if (cleanName.isEmpty()) return SubsMessage.InvalidName
        val existing = guid?.let { MmkvManager.decodeSubscription(it) }
        val allowInsecure = existing?.allowInsecureUrl == true
        if (cleanUrl.isNotEmpty()) {
            if (!Utils.isValidUrl(cleanUrl)) return SubsMessage.InvalidUrl
            if (!Utils.isValidSubUrl(cleanUrl) && !allowInsecure) return SubsMessage.InvalidUrl
        }
        val minutes = intervalMinutes.toLong()
        if (autoUpdate && minutes < AppConfig.SUBSCRIPTION_MIN_INTERVAL_MINUTES) return SubsMessage.InvalidInterval
        val id = guid ?: Utils.getUuid()
        val item = (existing ?: SubscriptionItem()).copy(
            remarks = cleanName,
            url = cleanUrl,
            autoUpdate = autoUpdate,
            updateInterval = if (autoUpdate) minutes else (existing?.updateInterval ?: AppConfig.DEFAULT_SUB_INTERVAL_MINUTES),
            enabled = enabled,
        )
        viewModelScope.launch(Dispatchers.IO) {
            MmkvManager.encodeSubscription(id, item)
            if (autoUpdate) SubscriptionUpdater.syncOne(subId = id) else SubscriptionUpdater.cancelOne(subId = id)
            SettingsChangeManager.makeSetupGroupTab()
            reload()
            _state.update { it.copy(message = SubsMessage.Saved) }
            if (guid == null && cleanUrl.isNotEmpty()) updateOne(MmkvManager.decodeSubscriptions().firstOrNull { it.subscription.url == cleanUrl }?.guid ?: id)
        }
        return null
    }

    override fun delete(guid: String) {
        viewModelScope.launch(Dispatchers.IO) {
            SubscriptionUpdater.cancelOne(subId = guid)
            SettingsManager.removeSubscriptionWithDefault(guid)
            SettingsChangeManager.makeSetupGroupTab()
            reload()
            _state.update { it.copy(message = SubsMessage.Deleted) }
        }
    }

    override fun showQr(row: SubRow) {
        viewModelScope.launch {
            val bmp = withContext(Dispatchers.Default) { QRCodeDecoder.createQRCode(row.item.url) }
            _state.update { it.copy(qr = bmp, qrTitle = row.title) }
        }
    }

    override fun dismissQr() = _state.update { it.copy(qr = null) }

    companion object {
        /** True when [text] looks like something that can be saved as a subscription link. */
        fun looksLikeUrl(text: String): Boolean {
            val t = text.trim()
            if (t.isEmpty() || t.contains('\n')) return false
            val scheme = runCatching { Uri.parse(t).scheme }.getOrNull()?.lowercase()
            return scheme == "https" || scheme == "http"
        }

        /** A friendly default name from a link's host. */
        fun suggestName(url: String): String = runCatching { Uri.parse(url.trim()).host.orEmpty().removePrefix("www.") }.getOrDefault("")
    }
}
