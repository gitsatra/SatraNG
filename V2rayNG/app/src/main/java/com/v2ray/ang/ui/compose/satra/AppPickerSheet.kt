package com.v2ray.ang.ui.compose.satra

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import com.v2ray.ang.dto.AppInfo
import com.v2ray.ang.handler.AppCategory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.v2ray.ang.R
import com.v2ray.ang.ui.apppicker.AppListViewModel

/** Display name of an app group. */
@Composable
fun categoryLabel(cat: AppCategory): String = stringResource(
    when (cat) {
        AppCategory.SOCIAL -> R.string.sx_cat_social
        AppCategory.MESSAGING -> R.string.sx_cat_messaging
        AppCategory.VIDEO -> R.string.sx_cat_video
        AppCategory.GAMES -> R.string.sx_cat_games
        AppCategory.BROWSERS -> R.string.sx_cat_browsers
        AppCategory.VPN -> R.string.sx_cat_vpn
        AppCategory.SHOPPING -> R.string.sx_cat_shopping
        AppCategory.PRODUCTIVITY -> R.string.sx_cat_productivity
        AppCategory.OTHER -> R.string.sx_cat_other
        AppCategory.SYSTEM -> R.string.sx_cat_system
    },
)

/**
 * Searchable app list with icons, in sections by [AppCategory]. System apps stay hidden behind a
 * toggle (selected ones always show). [multi] = false closes on first tap; otherwise a Done button
 * returns the whole selection. With [onPickCategory], each section offers "Limit all".
 */
@Composable
fun AppPickerSheet(
    title: String,
    selected: Set<String>,
    multi: Boolean,
    onDismiss: () -> Unit,
    onResult: (Set<String>) -> Unit,
    disabled: Set<String> = emptySet(),
    onPickCategory: ((AppCategory) -> Unit)? = null,
    disabledCategories: Set<AppCategory> = emptySet(),
) {
    val vm: AppListViewModel = viewModel()
    LaunchedEffect(Unit) { vm.ensureLoaded() }
    val apps by vm.apps.collectAsState()
    val loading by vm.loading.collectAsState()
    SatraSheet(onDismiss = onDismiss) {
        AppPickerContent(title, apps, loading, selected, multi, onResult, disabled, onPickCategory, disabledCategories)
    }
}

@Composable
fun AppPickerContent(
    title: String,
    apps: List<AppInfo>,
    loading: Boolean,
    selected: Set<String>,
    multi: Boolean,
    onResult: (Set<String>) -> Unit,
    disabled: Set<String> = emptySet(),
    onPickCategory: ((AppCategory) -> Unit)? = null,
    disabledCategories: Set<AppCategory> = emptySet(),
) {
    val c = Satra.colors
    var query by rememberSaveable { mutableStateOf("") }
    var showSystem by rememberSaveable { mutableStateOf(false) }
    var picked by remember { mutableStateOf(selected) }
    val sections = remember(apps, query, showSystem, selected) {
        val q = query.trim()
        apps.asSequence()
            .filter { showSystem || !it.isSystemApp || it.category != AppCategory.SYSTEM || it.packageName in selected }
            .filter { q.isEmpty() || it.appName.contains(q, true) || it.packageName.contains(q, true) }
            .groupBy { it.category }
            .toSortedMap(compareBy { it.ordinal })
    }
    SheetTitle(title)
    SatraSearchField(
        value = query, onValueChange = { query = it },
        placeholder = stringResource(R.string.sx_search),
        clearLabel = stringResource(R.string.sx_clear),
        modifier = Modifier.padding(horizontal = 20.dp),
    )
    Box(Modifier.padding(top = 4.dp)) {
        InsetGroup { row { SatraSwitchRow(stringResource(R.string.sx_show_system_apps), showSystem, { showSystem = it }) } }
    }
    Spacer(Modifier.height(4.dp))
    Box(Modifier.fillMaxWidth().heightIn(min = 240.dp, max = 460.dp)) {
        if (loading && apps.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { SatraSpinner() }
        } else {
            LazyColumn {
                sections.forEach { (cat, list) ->
                    item(key = "h-" + cat.name) {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                categoryLabel(cat).uppercase(currentLocale()),
                                style = Satra.type.caption, color = c.textSecondary,
                            )
                            Text(
                                "  ·  " + pluralStringResource(R.plurals.sx_cat_apps, list.size, list.size),
                                style = Satra.type.caption, color = c.textTertiary,
                                modifier = Modifier.weight(1f),
                            )
                            if (onPickCategory != null && cat !in disabledCategories) {
                                Text(
                                    stringResource(R.string.sx_cat_limit_all),
                                    style = Satra.type.footnote, color = c.accent,
                                    modifier = Modifier.clip(SatraShapes.Small).satraClickable { onPickCategory(cat) }
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                    items(list, key = { it.packageName }) { app ->
                        val isOn = app.packageName in picked
                        val isDisabled = app.packageName in disabled
                        SatraRow(
                            title = app.appName,
                            subtitle = if (isDisabled) stringResource(R.string.sx_already_limited) else app.packageName,
                            leading = { AppIcon(app.packageName, size = 40.dp) },
                            enabled = !isDisabled,
                            trailing = if (multi) ({
                                if (isOn) Icon(SatraIcons.Check, null, tint = c.accent, modifier = Modifier.size(22.dp))
                            }) else null,
                            onClick = {
                                if (multi) {
                                    picked = if (isOn) picked - app.packageName else picked + app.packageName
                                } else {
                                    onResult(setOf(app.packageName))
                                }
                            },
                        )
                    }
                }
            }
        }
    }
    if (multi) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            SatraButton(stringResource(R.string.sx_done), { onResult(picked) }, modifier = Modifier.fillMaxWidth())
        }
    }
}
