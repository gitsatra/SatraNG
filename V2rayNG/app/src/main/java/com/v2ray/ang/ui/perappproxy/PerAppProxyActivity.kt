package com.v2ray.ang.ui.perappproxy

import android.os.Bundle
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.dto.AppInfo
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.AppDivider
import com.v2ray.ang.ui.compose.AppDropdownMenuItems
import com.v2ray.ang.ui.compose.AppListItem
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.ConfirmDialog
import com.v2ray.ang.ui.compose.ItemDivider
import com.v2ray.ang.ui.compose.NavigationBarsBottomPadding
import com.v2ray.ang.ui.compose.verticalScrollbar
import com.v2ray.ang.util.Utils
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import com.v2ray.ang.ui.compose.satra.AppIcon
import com.v2ray.ang.ui.compose.satra.LazyGroupRow
import com.v2ray.ang.ui.compose.satra.Satra
import com.v2ray.ang.ui.compose.satra.SatraCheckCircle
import com.v2ray.ang.ui.compose.satra.SatraEmptyState
import com.v2ray.ang.ui.compose.satra.SatraIconButton
import com.v2ray.ang.ui.compose.satra.SatraIcons
import com.v2ray.ang.ui.compose.satra.SatraLazyPage
import com.v2ray.ang.ui.compose.satra.SatraMenu
import com.v2ray.ang.ui.compose.satra.SatraMenuItem
import com.v2ray.ang.ui.compose.satra.SatraSegmented
import com.v2ray.ang.ui.compose.satra.SatraSpace
import com.v2ray.ang.ui.compose.satra.currentLocale

private enum class PerAppMenuAction(@StringRes val labelRes: Int) {
    SelectAll(R.string.menu_item_select_all),
    InvertSelection(R.string.menu_item_invert_selection),
    SelectProxyApps(R.string.menu_item_select_proxy_app),
    ImportSelection(R.string.menu_item_import_proxy_app),
    ExportSelection(R.string.menu_item_export_proxy_app)
}

class PerAppProxyActivity : BaseComponentActivity() {

    private val viewModel: PerAppProxyViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.loadApps(this)
    }

    @Composable
    override fun ScreenContent() {
        val apps by viewModel.displayedApps.collectAsStateWithLifecycle()
        val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
        val blacklist by viewModel.blacklist.collectAsStateWithLifecycle()
        val perAppProxyEnabled by viewModel.perAppProxyEnabled.collectAsStateWithLifecycle()
        val bypassApps by viewModel.bypassApps.collectAsStateWithLifecycle()

        PerAppProxyScreen(
            apps = apps,
            isLoading = isLoading,
            blacklist = blacklist,
            perAppProxyEnabled = perAppProxyEnabled,
            bypassApps = bypassApps,
            onBackClick = { finish() },
            onPerAppProxyChanged = { viewModel.setPerAppProxyEnabled(it) },
            onBypassAppsChanged = { viewModel.setBypassAppsEnabled(it) },
            onToggleApp = { viewModel.toggle(it) },
            onSearch = { viewModel.filterApps(it) },
            onSelectAll = { viewModel.selectAll() },
            onInvertSelection = { viewModel.invertSelection() },
            onSelectProxyAuto = { viewModel.selectProxyAppAuto(this) },
            onImportProxyApp = {
                val content = Utils.getClipboard(applicationContext)
                viewModel.importProxyApp(content, this)
            },
            onExportProxyApp = {
                val export = viewModel.exportProxyApp()
                Utils.setClipboard(applicationContext, export)
                toastSuccess(R.string.toast_success)
            }
        )
    }
}

@Composable
fun PerAppProxyScreen(
    apps: List<AppInfo>,
    isLoading: Boolean,
    blacklist: Set<String>,
    perAppProxyEnabled: Boolean,
    bypassApps: Boolean,
    onBackClick: () -> Unit,
    onPerAppProxyChanged: (Boolean) -> Unit,
    onBypassAppsChanged: (Boolean) -> Unit,
    onToggleApp: (String) -> Unit,
    onSearch: (String) -> Unit,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
    onSelectProxyAuto: () -> Unit,
    onImportProxyApp: () -> Unit,
    onExportProxyApp: () -> Unit
) {
    val c = Satra.colors
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { onSearch(searchQuery) }

    // 0 = every app uses the VPN, 1 = only the selected apps, 2 = all except the selected apps.
    val mode = when {
        !perAppProxyEnabled -> 0
        bypassApps -> 2
        else -> 1
    }
    val selectedCount = apps.count { blacklist.contains(it.packageName) }

    SatraLazyPage(
        title = stringResource(R.string.sx_set_split),
        onBack = onBackClick,
        isLoading = isLoading,
        searchQuery = if (mode == 0) null else searchQuery,
        onSearchChange = { searchQuery = it; onSearch(it) },
        searchPlaceholder = stringResource(R.string.menu_item_search),
        actions = {
            if (mode != 0) {
                Box {
                    SatraIconButton(SatraIcons.More, stringResource(R.string.acc_more), onClick = { showMenu = true })
                    SatraMenu(expanded = showMenu, onDismiss = { showMenu = false }) {
                        PerAppMenuAction.entries.forEach { action ->
                            SatraMenuItem(stringResource(action.labelRes), onClick = {
                                showMenu = false
                                when (action) {
                                    PerAppMenuAction.SelectAll -> onSelectAll()
                                    PerAppMenuAction.InvertSelection -> onInvertSelection()
                                    PerAppMenuAction.SelectProxyApps -> onSelectProxyAuto()
                                    PerAppMenuAction.ImportSelection -> onImportProxyApp()
                                    PerAppMenuAction.ExportSelection -> onExportProxyApp()
                                }
                            })
                        }
                    }
                }
            }
        },
    ) {
        item(key = "mode") {
            Column(Modifier.padding(horizontal = SatraSpace.Gutter)) {
                SatraSegmented(
                    options = listOf(
                        stringResource(R.string.sx_split_all),
                        stringResource(R.string.sx_split_only),
                        stringResource(R.string.sx_split_except),
                    ),
                    selectedIndex = mode,
                    onSelect = { i ->
                        when (i) {
                            0 -> onPerAppProxyChanged(false)
                            1 -> { onBypassAppsChanged(false); onPerAppProxyChanged(true) }
                            else -> { onBypassAppsChanged(true); onPerAppProxyChanged(true) }
                        }
                    },
                )
                Text(
                    stringResource(
                        when (mode) {
                            0 -> R.string.sx_split_all_note
                            1 -> R.string.sx_split_only_note
                            else -> R.string.sx_split_except_note
                        }
                    ),
                    style = Satra.type.footnote,
                    color = c.textSecondary,
                    modifier = Modifier.padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 18.dp),
                )
            }
        }
        if (mode != 0) {
            item(key = "count") {
                Text(
                    pluralStringResource(R.plurals.sx_split_selected, selectedCount, selectedCount).uppercase(currentLocale()),
                    style = Satra.type.footnote.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
                    color = c.textSecondary,
                    modifier = Modifier.padding(horizontal = SatraSpace.Gutter + 16.dp).padding(bottom = 7.dp),
                )
            }
            if (apps.isEmpty() && !isLoading) {
                item(key = "empty") {
                    SatraEmptyState(
                        icon = SatraIcons.Search,
                        title = stringResource(R.string.sx_split_none_title),
                        message = stringResource(R.string.sx_split_none_msg),
                    )
                }
            }
            itemsIndexed(items = apps, key = { _, it -> it.packageName }) { index, app ->
                val checked = blacklist.contains(app.packageName)
                LazyGroupRow(index, apps.size, separatorInset = 64.dp) {
                    SplitAppRow(app, checked) { onToggleApp(app.packageName) }
                }
            }
        }
    }
}

@Composable
internal fun SplitAppRow(app: AppInfo, checked: Boolean, onToggle: () -> Unit) {
    val c = Satra.colors
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(app.packageName, size = 36.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(app.appName, style = Satra.type.body, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(app.packageName, style = Satra.type.caption, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(10.dp))
        SatraCheckCircle(checked)
    }
}
