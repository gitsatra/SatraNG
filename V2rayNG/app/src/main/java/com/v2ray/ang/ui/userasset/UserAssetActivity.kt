package com.v2ray.ang.ui.userasset

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.format.DateFormat
import android.text.format.Formatter
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.AssetUrlCache
import com.v2ray.ang.dto.entities.AssetUrlItem
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.ui.base.HelperBaseComponentActivity
import com.v2ray.ang.ui.compose.AppDropdownMenuItems
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.DeleteConfirmDialog
import com.v2ray.ang.ui.compose.ItemDivider
import com.v2ray.ang.ui.compose.NavigationBarsBottomPadding
import com.v2ray.ang.ui.compose.SettingsListItem
import com.v2ray.ang.ui.compose.verticalScrollbar
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

private enum class AddAssetMenuAction(@StringRes val labelRes: Int) {
    File(R.string.menu_item_add_file),
    Url(R.string.menu_item_add_url),
    QRCode(R.string.menu_item_scan_qrcode)
}

private data class AssetDeleteTarget(val guid: String, val name: String)

class UserAssetActivity : HelperBaseComponentActivity() {

    private val viewModel: UserAssetViewModel by viewModels()
    private val extDir by lazy { File(Utils.userAssetPath(this)) }
    private val isLoadingState = MutableStateFlow(false)
    private val geoFilesSourceState = MutableStateFlow("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        geoFilesSourceState.value = getGeoFilesSources()
    }

    @Composable
    override fun ScreenContent() {
        val isLoading by isLoadingState.collectAsStateWithLifecycle()
        val geoFilesSource by geoFilesSourceState.collectAsStateWithLifecycle()
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        UserAssetScreen(
            uiState = uiState,
            isLoading = isLoading,
            geoFilesSource = geoFilesSource,
            geoFilesSourcesList = AppConfig.GEO_FILES_SOURCES.toList(),
            onBackClick = { finish() },
            onGeoSourceSelected = { value ->
                MmkvManager.encodeSettings(AppConfig.PREF_GEO_FILES_SOURCES, value)
                geoFilesSourceState.value = value
                refreshData()
            },
            onAddFileClick = { showFileChooser() },
            onAddUrlClick = { startActivity(Intent(this, UserAssetUrlActivity::class.java)) },
            onAddQrcodeClick = { importAssetFromQRcode() },
            onDownloadClick = { downloadGeoFiles() },
            onEditAsset = { guid ->
                startActivity(Intent(this, UserAssetUrlActivity::class.java).putExtra("assetId", guid))
            },
            onRemoveAsset = { guid, name ->
                extDir.listFiles()?.find { it.name == name }?.delete()
                MmkvManager.removeAssetUrl(guid)
                initAssets()
            }
        )
    }

    override fun onResume() {
        super.onResume()
        refreshData()
    }

    private fun getGeoFilesSources(): String {
        return MmkvManager.decodeSettingsString(AppConfig.PREF_GEO_FILES_SOURCES) ?: AppConfig.GEO_FILES_SOURCES.first()
    }

    private fun showFileChooser() {
        launchFileChooser { uri ->
            if (uri == null) {
                return@launchFileChooser
            }

            val assetId = Utils.getUuid()
            runCatching {
                val assetItem = AssetUrlItem(
                    getCursorName(uri) ?: uri.toString(),
                    "file"
                )

                val assetList = MmkvManager.decodeAssetUrls()
                if (assetList.any { it.assetUrl.remarks == assetItem.remarks && it.guid != assetId }) {
                    toast(R.string.msg_remark_is_duplicate)
                } else {
                    MmkvManager.encodeAsset(assetId, assetItem)
                    copyFile(uri)
                }
            }.onFailure {
                toastError(R.string.toast_asset_copy_failed)
                MmkvManager.removeAssetUrl(assetId)
            }
        }
    }

    private fun copyFile(uri: Uri): String {
        val targetFile = File(extDir, getCursorName(uri) ?: uri.toString())
        contentResolver.openInputStream(uri).use { inputStream ->
            targetFile.outputStream().use { fileOut ->
                inputStream?.copyTo(fileOut)
                toastSuccess(R.string.toast_success)
                refreshData()
            }
        }
        return targetFile.path
    }

    private fun getCursorName(uri: Uri): String? = try {
        contentResolver.query(uri, null, null, null, null)?.let { cursor ->
            cursor.run {
                if (moveToFirst()) getString(getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                else null
            }.also { cursor.close() }
        }
    } catch (e: Exception) {
        LogUtil.e(AppConfig.TAG, "Failed to get cursor name", e)
        null
    }

    private fun importAssetFromQRcode(): Boolean {
        launchQRCodeScanner { scanResult ->
            if (scanResult != null) {
                importAsset(scanResult)
            }
        }
        return true
    }

    private fun importAsset(url: String?): Boolean {
        try {
            if (!Utils.isValidUrl(url)) {
                toast(R.string.toast_invalid_url)
                return false
            }
            startActivity(
                Intent(this, UserAssetUrlActivity::class.java)
                    .putExtra(UserAssetUrlActivity.ASSET_URL_QRCODE, url)
            )
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to import asset from URL", e)
            return false
        }
        return true
    }

    private fun downloadGeoFiles() {
        isLoadingState.value = true
        toast(R.string.msg_downloading_content)

        val proxyUsername = SettingsManager.getSocksUsername()
        val proxyPassword = SettingsManager.getSocksPassword()
        val httpPort = SettingsManager.getHttpPort()
        lifecycleScope.launch {
            refreshData().join()
            val result = withContext(Dispatchers.IO) {
                viewModel.downloadGeoFiles(extDir, httpPort, proxyUsername, proxyPassword)
            }
            if (result.successCount > 0) {
                toast(
                    resources.getQuantityString(
                        R.plurals.title_update_asset_count,
                        result.successCount,
                        result.successCount,
                    )
                )
            } else {
                toast(getString(R.string.toast_failure))
            }
            refreshData().join()
            isLoadingState.value = false
        }
    }

    private fun initAssets() {
        lifecycleScope.launch(Dispatchers.Default) {
            SettingsManager.initAssets(this@UserAssetActivity, assets)
            withContext(Dispatchers.Main) {
                refreshData()
            }
        }
    }

    private fun refreshData() = viewModel.reload(getGeoFilesSources(), extDir)
}

@Composable
internal fun UserAssetScreen(
    uiState: UserAssetUiState,
    isLoading: Boolean,
    geoFilesSource: String,
    geoFilesSourcesList: List<String>,
    onBackClick: () -> Unit,
    onGeoSourceSelected: (String) -> Unit,
    onAddFileClick: () -> Unit,
    onAddUrlClick: () -> Unit,
    onAddQrcodeClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onEditAsset: (String) -> Unit,
    onRemoveAsset: (String, String) -> Unit
) {
    var showAddMenu by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<AssetDeleteTarget?>(null) }
    val listState = rememberLazyListState()

    com.v2ray.ang.ui.compose.satra.SatraLazyPage(
        title = stringResource(R.string.title_user_asset_setting),
        onBack = onBackClick,
        isLoading = isLoading,
        state = listState,
        actions = {
            com.v2ray.ang.ui.compose.satra.SatraIconButton(icon = com.v2ray.ang.ui.compose.satra.SatraIcons.Download, contentDescription = stringResource(R.string.acc_download_file), onClick = onDownloadClick, enabled = !isLoading)
            Box {
                com.v2ray.ang.ui.compose.satra.SatraIconButton(icon = com.v2ray.ang.ui.compose.satra.SatraIcons.Plus, contentDescription = stringResource(R.string.acc_add_asset), onClick = { showAddMenu = true })
                com.v2ray.ang.ui.compose.satra.SatraMenu(expanded = showAddMenu, onDismiss = { showAddMenu = false }) {
                    com.v2ray.ang.ui.compose.satra.SatraMenuItem(stringResource(R.string.menu_item_add_file), { showAddMenu = false; onAddFileClick() }, com.v2ray.ang.ui.compose.satra.SatraIcons.File)
                    com.v2ray.ang.ui.compose.satra.SatraMenuItem(stringResource(R.string.menu_item_add_url), { showAddMenu = false; onAddUrlClick() }, com.v2ray.ang.ui.compose.satra.SatraIcons.Link)
                    com.v2ray.ang.ui.compose.satra.SatraMenuItem(stringResource(R.string.menu_item_scan_qrcode), { showAddMenu = false; onAddQrcodeClick() }, com.v2ray.ang.ui.compose.satra.SatraIcons.Scan)
                }
            }
        },
    ) {
        item(key = "geo_source") {
            com.v2ray.ang.ui.compose.FormDropdownField(
                label = stringResource(R.string.asset_geo_files_sources),
                value = geoFilesSource,
                options = geoFilesSourcesList,
                onValueChange = onGeoSourceSelected,
            )
        }
        item(key = "files_header") { com.v2ray.ang.ui.compose.satra.GroupHeader(stringResource(R.string.sx_assets_files)) }
        val assets = uiState.assets
        itemsIndexed(items = assets, key = { _, item -> item.guid }) { index, item ->
            com.v2ray.ang.ui.compose.satra.LazyGroupRow(index, assets.size, separatorInset = 56.dp) {
                UserAssetItem(
                    item = item,
                    fileMetadata = uiState.fileMetadata[item.guid],
                    onEdit = { onEditAsset(item.guid) },
                    onDeleteClick = { deleteTarget = AssetDeleteTarget(item.guid, item.assetUrl.remarks) }
                )
            }
        }
        item(key = "files_footer") { com.v2ray.ang.ui.compose.satra.GroupFooter(stringResource(R.string.sx_assets_footer)) }
    }

    deleteTarget?.let { asset ->
        DeleteConfirmDialog(
            message = stringResource(R.string.confirm_delete_asset_file),
            itemName = asset.name,
            onConfirm = {
                deleteTarget = null
                onRemoveAsset(asset.guid, asset.name)
            },
            onDismiss = { deleteTarget = null }
        )
    }
}

@Composable
private fun UserAssetItem(
    item: AssetUrlCache,
    fileMetadata: AssetFileMetadata?,
    onEdit: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val propertiesText = if (fileMetadata != null) {
        val fileSize = Formatter.formatFileSize(context, fileMetadata.length)
        val skeleton = if (DateFormat.is24HourFormat(context)) "yMMMdHm" else "yMMMdhm"
        val formattedDate = SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
            .format(Date(fileMetadata.lastModified))
        "$fileSize  •  $formattedDate"
    } else {
        stringResource(R.string.msg_file_not_found)
    }
    val showEditButton = item.assetUrl.locked != true && item.assetUrl.url != "file"

    val c = com.v2ray.ang.ui.compose.satra.Satra.colors
    com.v2ray.ang.ui.compose.satra.SatraRow(
        title = item.assetUrl.remarks,
        subtitle = propertiesText,
        icon = com.v2ray.ang.ui.compose.satra.SatraIcons.File,
        iconColor = if (fileMetadata != null) c.info else c.warning,
        onClick = if (showEditButton) onEdit else null,
        trailing = {
            com.v2ray.ang.ui.compose.satra.SatraIconButton(icon = com.v2ray.ang.ui.compose.satra.SatraIcons.Trash, contentDescription = stringResource(R.string.acc_delete), onClick = onDeleteClick, tint = c.danger)
        },
        chevron = showEditButton,
    )
}
