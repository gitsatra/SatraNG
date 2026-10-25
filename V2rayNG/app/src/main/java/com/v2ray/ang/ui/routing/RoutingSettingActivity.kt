package com.v2ray.ang.ui.routing

import android.content.Intent
import android.os.Bundle
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.RulesetItem
import com.v2ray.ang.enums.RoutingType
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.ui.base.HelperBaseComponentActivity
import com.v2ray.ang.ui.compose.AppDropdownMenuItems
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.ItemDivider
import com.v2ray.ang.ui.compose.NavigationBarsBottomPadding
import com.v2ray.ang.ui.compose.ReorderableListItem
import com.v2ray.ang.ui.compose.SelectListDialog
import com.v2ray.ang.ui.compose.SettingsListItem
import com.v2ray.ang.ui.compose.colorConfigType
import com.v2ray.ang.ui.compose.verticalScrollbar
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.produceState
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.zIndex
import com.v2ray.ang.handler.RoutingPresets
import com.v2ray.ang.ui.compose.satra.InsetGroup
import com.v2ray.ang.ui.compose.satra.LazyGroupRow
import com.v2ray.ang.ui.compose.satra.Satra
import com.v2ray.ang.ui.compose.satra.SatraAlert
import com.v2ray.ang.ui.compose.satra.SatraBadge
import com.v2ray.ang.ui.compose.satra.SatraCheckCircle
import com.v2ray.ang.ui.compose.satra.SatraIconButton
import com.v2ray.ang.ui.compose.satra.SatraIcons
import com.v2ray.ang.ui.compose.satra.SatraLazyPage
import com.v2ray.ang.ui.compose.satra.SatraMenu
import com.v2ray.ang.ui.compose.satra.SatraMenuItem
import com.v2ray.ang.ui.compose.satra.SatraRow
import com.v2ray.ang.ui.compose.satra.SatraShapes
import com.v2ray.ang.ui.compose.satra.SatraSheet
import com.v2ray.ang.ui.compose.satra.SatraSpace
import com.v2ray.ang.ui.compose.satra.SatraSwitch
import com.v2ray.ang.ui.compose.satra.SheetTitle
import com.v2ray.ang.ui.compose.satra.currentLocale
import com.v2ray.ang.ui.compose.satra.satraClickable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private enum class RoutingMenuAction(@StringRes val labelRes: Int) {
    ImportClipboard(R.string.routing_settings_import_rulesets_from_clipboard),
    ImportQRCode(R.string.routing_settings_import_rulesets_from_qrcode),
    ExportClipboard(R.string.routing_settings_export_rulesets_to_clipboard)
}

private enum class RoutingPreset(val type: RoutingType, @StringRes val labelRes: Int, @StringRes val noteRes: Int, val flag: String) {
    IranWhitelist(RoutingType.WHITE_IRAN, R.string.sx_routing_iran, R.string.sx_routing_iran_note, "🇮🇷"),
    RussiaWhitelist(RoutingType.WHITE_RUSSIA, R.string.sx_routing_russia, R.string.sx_routing_russia_note, "🇷🇺"),
    ChinaWhitelist(RoutingType.WHITE_CHINA, R.string.sx_routing_china, R.string.sx_routing_china_note, "🇨🇳"),
}

class RoutingSettingActivity : HelperBaseComponentActivity() {
    private val viewModel: RoutingSettingsViewModel by viewModels()
    private val domainStrategyState = MutableStateFlow("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        domainStrategyState.value = getDomainStrategy()
    }

    @Composable
    override fun ScreenContent() {
        RoutingSettingScreen(
            viewModel = viewModel,
            domainStrategyState = domainStrategyState,
            onBackClick = { finish() },
            onAddRule = { startActivity(Intent(this, RoutingEditActivity::class.java)) },
            onEditRule = { position ->
                startActivity(Intent(this, RoutingEditActivity::class.java).putExtra("position", position))
            },
            onDomainStrategySelected = { value ->
                MmkvManager.encodeSettings(AppConfig.PREF_ROUTING_DOMAIN_STRATEGY, value)
                domainStrategyState.value = value
            },
            onImportPredefined = { type -> importPredefined(type) },
            onImportClipboard = { importFromClipboard() },
            onImportQRcode = { importQRcode() },
            onExportClipboard = { export2Clipboard() }
        )
    }

    override fun onResume() {
        super.onResume()
        viewModel.reload()
    }

    private fun getDomainStrategy(): String {
        val strategies = resources.getStringArray(R.array.routing_domain_strategy)
        return MmkvManager.decodeSettingsString(AppConfig.PREF_ROUTING_DOMAIN_STRATEGY) ?: strategies.first()
    }

    private fun importPredefined(type: RoutingType) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                SettingsManager.resetRoutingRulesetsFromPresets(this@RoutingSettingActivity, type)
                launch(Dispatchers.Main) {
                    viewModel.reload()
                    toastSuccess(R.string.toast_success)
                }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to import predefined ruleset", e)
            }
        }
    }

    private fun importFromClipboard() {
        val clipboard = try {
            Utils.getClipboard(this)
        } catch (e: Exception) {
            toastError(R.string.toast_failure)
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            val result = SettingsManager.resetRoutingRulesets(clipboard)
            withContext(Dispatchers.Main) {
                if (result) {
                    viewModel.reload()
                    toastSuccess(R.string.toast_success)
                } else {
                    toastError(R.string.toast_failure)
                }
            }
        }
    }

    private fun importQRcode() {
        launchQRCodeScanner { scanResult ->
            if (scanResult != null) {
                lifecycleScope.launch(Dispatchers.IO) {
                    val result = SettingsManager.resetRoutingRulesets(scanResult)
                    withContext(Dispatchers.Main) {
                        if (result) {
                            viewModel.reload()
                            toastSuccess(R.string.toast_success)
                        } else {
                            toastError(R.string.toast_failure)
                        }
                    }
                }
            }
        }
    }

    private fun export2Clipboard() {
        val rulesetList = MmkvManager.decodeRoutingRulesets()
        if (rulesetList.isNullOrEmpty()) {
            toastError(R.string.toast_failure)
        } else {
            Utils.setClipboard(this, JsonUtil.toJson(rulesetList))
            toastSuccess(R.string.toast_success)
        }
    }
}

@Composable
fun RoutingSettingScreen(
    viewModel: RoutingSettingsViewModel,
    domainStrategyState: MutableStateFlow<String>,
    onBackClick: () -> Unit,
    onAddRule: () -> Unit,
    onEditRule: (Int) -> Unit,
    onDomainStrategySelected: (String) -> Unit,
    onImportPredefined: (RoutingType) -> Unit,
    onImportClipboard: () -> Unit,
    onImportQRcode: () -> Unit,
    onExportClipboard: () -> Unit
) {
    val c = Satra.colors
    val context = LocalContext.current
    val rulesets by viewModel.rulesetsFlow.collectAsStateWithLifecycle()
    val domainStrategy by domainStrategyState.collectAsState()
    var showMenu by remember { mutableStateOf(false) }
    var confirmPreset by remember { mutableStateOf<RoutingPreset?>(null) }
    var pickStrategy by remember { mutableStateOf(false) }
    val presets by produceState<Map<RoutingType, List<RulesetItem>>>(emptyMap()) {
        value = withContext(Dispatchers.IO) { SettingsManager.offeredRoutingPresets(context) }
    }
    val active = remember(rulesets, presets) { RoutingPresets.matching(rulesets, presets) }

    val domainStrategies = stringArrayResource(R.array.routing_domain_strategy).toList()
    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        // Lazy list indices include the preceding non-rule content, so resolve the stable rule keys.
        val fromIndex = rulesets.indexOfFirst { it.id == from.key }
        val toIndex = rulesets.indexOfFirst { it.id == to.key }
        if (fromIndex >= 0 && toIndex >= 0) viewModel.move(fromIndex, toIndex)
    }

    SatraLazyPage(
        title = stringResource(R.string.sx_routing_title),
        onBack = onBackClick,
        state = lazyListState,
        actions = {
            SatraIconButton(SatraIcons.Plus, stringResource(R.string.acc_add_rule), onClick = onAddRule)
            Box {
                SatraIconButton(SatraIcons.More, stringResource(R.string.acc_more), onClick = { showMenu = true })
                SatraMenu(expanded = showMenu, onDismiss = { showMenu = false }) {
                    RoutingMenuAction.entries.forEach { action ->
                        SatraMenuItem(stringResource(action.labelRes), onClick = {
                            showMenu = false
                            when (action) {
                                RoutingMenuAction.ImportClipboard -> onImportClipboard()
                                RoutingMenuAction.ImportQRCode -> onImportQRcode()
                                RoutingMenuAction.ExportClipboard -> onExportClipboard()
                            }
                        })
                    }
                }
            }
        },
    ) {
        item(key = "preset") {
            InsetGroup(
                header = stringResource(R.string.sx_routing_preset),
                footer = if (active == null && presets.isNotEmpty()) stringResource(R.string.sx_routing_custom_note) else null,
            ) {
                RoutingPreset.entries.forEach { preset ->
                    row {
                        SatraRow(
                            title = stringResource(preset.labelRes),
                            subtitle = stringResource(preset.noteRes),
                            leading = { Text(preset.flag, style = Satra.type.title2) },
                            trailing = if (active == preset.type) ({ SatraCheckCircle(true) }) else null,
                            onClick = { if (active != preset.type) confirmPreset = preset },
                        )
                    }
                }
            }
        }
        item(key = "exceptions") { RoutingExceptionsSection() }
        item(key = "rules_h") {
            Text(
                stringResource(R.string.sx_routing_rules).uppercase(currentLocale()),
                style = Satra.type.footnote.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
                color = c.textSecondary,
                modifier = Modifier.padding(horizontal = SatraSpace.Gutter + 16.dp).padding(top = 22.dp, bottom = 7.dp),
            )
        }
        itemsIndexed(items = rulesets, key = { _, ruleset -> ruleset.id }) { index, ruleset ->
            ReorderableItem(reorderableState, key = ruleset.id) { isDragging ->
                val lift by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "lift")
                LazyGroupRow(index, rulesets.size, modifier = Modifier.zIndex(if (isDragging) 1f else 0f).shadow(lift, SatraShapes.Medium)) {
                    Row(
                        Modifier.fillMaxWidth().longPressDraggableHandle(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RoutingRulesetItem(
                            ruleset = ruleset,
                            onEdit = { onEditRule(index) },
                            onEnabledChange = { checked -> viewModel.update(index, ruleset.copy(enabled = checked)) },
                        )
                    }
                }
            }
        }
        item(key = "rules_f") {
            Text(
                stringResource(R.string.sx_routing_rules_note),
                style = Satra.type.footnote, color = c.textSecondary,
                modifier = Modifier.padding(horizontal = SatraSpace.Gutter + 16.dp).padding(top = 7.dp),
            )
        }
        item(key = "advanced") {
            Spacer(Modifier.height(22.dp))
            InsetGroup(header = stringResource(R.string.sx_routing_advanced)) {
                row {
                    SatraRow(
                        title = stringResource(R.string.routing_settings_domain_strategy),
                        value = domainStrategy,
                        chevron = true,
                        onClick = { pickStrategy = true },
                    )
                }
            }
        }
    }

    confirmPreset?.let { preset ->
        SatraAlert(
            title = stringResource(R.string.sx_routing_apply_title, stringResource(preset.labelRes)),
            message = stringResource(R.string.sx_routing_apply_msg),
            confirmText = stringResource(R.string.sx_routing_apply),
            dismissText = stringResource(R.string.sx_cancel),
            onConfirm = { confirmPreset = null; onImportPredefined(preset.type) },
            onDismiss = { confirmPreset = null },
        )
    }
    if (pickStrategy) {
        SatraSheet(onDismiss = { pickStrategy = false }) {
            SheetTitle(stringResource(R.string.routing_settings_domain_strategy))
            InsetGroup {
                domainStrategies.forEach { v ->
                    row {
                        SatraRow(
                            title = v,
                            trailing = if (v == domainStrategy) ({ Icon(SatraIcons.Check, null, tint = c.accent) }) else null,
                            onClick = { pickStrategy = false; onDomainStrategySelected(v) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun RowScope.RoutingRulesetItem(
    ruleset: RulesetItem,
    onEdit: () -> Unit,
    onEnabledChange: (Boolean) -> Unit
) {
    val c = Satra.colors
    val (tagColor, tagBg) = when (ruleset.outboundTag) {
        "direct" -> c.success to c.successSoft
        "block" -> c.danger to c.dangerSoft
        else -> c.accent to c.accentSoft
    }
    val tagLabel = when (ruleset.outboundTag) {
        "direct" -> stringResource(R.string.sx_routing_tag_direct)
        "block" -> stringResource(R.string.sx_routing_tag_block)
        "proxy" -> stringResource(R.string.sx_routing_tag_proxy)
        else -> ruleset.outboundTag
    }
    Row(
        Modifier
            .weight(1f)
            .satraClickable(role = Role.Button, pressedScale = 0.985f, onClick = onEdit)
            .padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    ruleset.remarks.orEmpty(),
                    style = Satra.type.body, color = if (ruleset.enabled) c.textPrimary else c.textTertiary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                )
                if (ruleset.locked == true) {
                    Spacer(Modifier.width(6.dp))
                    Icon(SatraIcons.Lock, stringResource(R.string.acc_locked), tint = c.textTertiary, modifier = Modifier.size(14.dp))
                }
            }
            val info = (ruleset.domain ?: ruleset.ip ?: ruleset.process)?.joinToString(", ") ?: ruleset.port.orEmpty()
            if (info.isNotEmpty()) {
                Text(info, style = Satra.type.footnote, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (ruleset.outboundTag.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            SatraBadge(tagLabel, tagColor, tagBg)
        }
    }
    Box(Modifier.padding(end = 12.dp)) {
        SatraSwitch(checked = ruleset.enabled, onCheckedChange = onEnabledChange)
    }
}
