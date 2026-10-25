package com.v2ray.ang.ui.apppicker

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.dto.AppInfo
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.satra.LazyGroupRow
import com.v2ray.ang.ui.compose.satra.SatraEmptyState
import com.v2ray.ang.ui.compose.satra.SatraIconButton
import com.v2ray.ang.ui.compose.satra.SatraIcons
import com.v2ray.ang.ui.compose.satra.SatraLazyPage
import com.v2ray.ang.ui.compose.satra.SatraMenu
import com.v2ray.ang.ui.compose.satra.SatraMenuItem
import com.v2ray.ang.ui.perappproxy.SplitAppRow

class AppPickerActivity : BaseComponentActivity() {

    companion object {
        private const val EXTRA_SELECTED_PACKAGES = "selected_packages"
        private const val EXTRA_PICKER_TITLE = "picker_title"

        fun createIntent(
            context: Context,
            selectedPackages: Collection<String> = emptyList(),
            title: String? = null
        ): Intent = Intent(context, AppPickerActivity::class.java).apply {
            putStringArrayListExtra(EXTRA_SELECTED_PACKAGES, ArrayList(selectedPackages))
            title?.let { putExtra(EXTRA_PICKER_TITLE, it) }
        }

        fun getSelectedPackages(intent: Intent?): List<String> {
            return intent?.getStringArrayListExtra(EXTRA_SELECTED_PACKAGES).orEmpty()
        }
    }

    private val viewModel: AppPickerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initial = intent.getStringArrayListExtra(EXTRA_SELECTED_PACKAGES).orEmpty()
        viewModel.initialize(initial)
        viewModel.loadApps(this)
    }

    override fun finish() {
        setResult(
            RESULT_OK,
            Intent().apply {
                putStringArrayListExtra(EXTRA_SELECTED_PACKAGES, ArrayList(viewModel.getSelectedPackages()))
            }
        )
        super.finish()
    }

    @Composable
    override fun ScreenContent() {
        val apps by viewModel.displayedApps.collectAsStateWithLifecycle()
        val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
        val selectedPackages by viewModel.selectedPackages.collectAsStateWithLifecycle()

        AppPickerScreen(
            title = resolveScreenTitle(),
            apps = apps,
            isLoading = isLoading,
            selectedPackages = selectedPackages,
            onBackClick = { finish() },
            onToggleApp = { viewModel.toggleApp(it) },
            onSearch = { viewModel.filterApps(it) },
            onSelectAll = { viewModel.selectAll() },
            onInvertSelection = { viewModel.invertSelection() }
        )
    }

    private fun resolveScreenTitle(): String {
        return intent.getStringExtra(EXTRA_PICKER_TITLE) ?: getString(R.string.per_app_proxy_settings)
    }
}

@Composable
fun AppPickerScreen(
    title: String,
    apps: List<AppInfo>,
    isLoading: Boolean,
    selectedPackages: Set<String>,
    onBackClick: () -> Unit,
    onToggleApp: (String) -> Unit,
    onSearch: (String) -> Unit,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { onSearch(searchQuery) }

    SatraLazyPage(
        title = title,
        onBack = onBackClick,
        subtitle = if (selectedPackages.isNotEmpty()) pluralStringResource(R.plurals.sx_split_selected, selectedPackages.size, selectedPackages.size) else null,
        isLoading = isLoading,
        searchQuery = searchQuery,
        onSearchChange = { searchQuery = it; onSearch(it) },
        searchPlaceholder = stringResource(R.string.sx_search),
        actions = {
            Box {
                SatraIconButton(SatraIcons.More, stringResource(R.string.acc_more), { showMenu = true })
                SatraMenu(expanded = showMenu, onDismiss = { showMenu = false }) {
                    SatraMenuItem(stringResource(R.string.menu_item_select_all), { showMenu = false; onSelectAll() }, SatraIcons.Check)
                    SatraMenuItem(stringResource(R.string.menu_item_invert_selection), { showMenu = false; onInvertSelection() }, SatraIcons.Refresh)
                }
            }
        },
    ) {
        if (apps.isEmpty() && !isLoading) {
            item(key = "empty") {
                SatraEmptyState(
                    icon = SatraIcons.Search,
                    title = stringResource(R.string.sx_servers_none_found),
                    message = stringResource(R.string.sx_apps_none_found_msg),
                    modifier = Modifier.padding(top = 32.dp),
                )
            }
        }
        itemsIndexed(items = apps, key = { _, it -> it.packageName }) { index, app ->
            LazyGroupRow(index, apps.size, separatorInset = 64.dp) {
                SplitAppRow(app, selectedPackages.contains(app.packageName)) { onToggleApp(app.packageName) }
            }
        }
    }
}
