package com.v2ray.ang.ui.shortcut

import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.satra.GroupHeader
import com.v2ray.ang.ui.compose.satra.LazyGroupRow
import com.v2ray.ang.ui.compose.satra.Satra
import com.v2ray.ang.ui.compose.satra.SatraBarTextAction
import com.v2ray.ang.ui.compose.satra.SatraCheckCircle
import com.v2ray.ang.ui.compose.satra.SatraEmptyState
import com.v2ray.ang.ui.compose.satra.SatraIcons
import com.v2ray.ang.ui.compose.satra.SatraLazyPage
import com.v2ray.ang.ui.compose.satra.SatraRow
import com.v2ray.ang.ui.compose.satra.SatraSwitchRow
import com.v2ray.ang.util.LogUtil

data class TaskerItem(
    val label: String,
    val guid: String,
)

class TaskerActivity : BaseComponentActivity() {

    private val items = mutableListOf<TaskerItem>()

    private val switchState = mutableStateOf(false)
    private val selectedPosition = mutableStateOf(-1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        items.add(TaskerItem(label = getString(R.string.sx_tasker_default), guid = AppConfig.TASKER_DEFAULT_GUID))

        MmkvManager.decodeAllServerList().distinct().filter { it != AppConfig.TASKER_DEFAULT_GUID }.forEach { key ->
            MmkvManager.decodeServerConfig(key)?.let { config ->
                items.add(TaskerItem(label = config.remarks, guid = key))
            }
        }

        init()
    }

    @Composable
    override fun ScreenContent() {
        TaskerScreen(
            items = items,
            switchState = switchState,
            selectedPosition = selectedPosition,
            onBackClick = { finish() },
            onSave = { confirmFinish() }
        )
    }

    private fun init() {
        try {
            val bundle = intent?.getBundleExtra(AppConfig.TASKER_EXTRA_BUNDLE)
            val switch = bundle?.getBoolean(AppConfig.TASKER_EXTRA_BUNDLE_SWITCH, false)
            val guid = bundle?.getString(AppConfig.TASKER_EXTRA_BUNDLE_GUID, "")

            if (switch == null || TextUtils.isEmpty(guid)) {
                return
            } else {
                switchState.value = switch
                selectedPosition.value = items.indexOfFirst { it.guid == guid.toString() }
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to initialize Tasker settings", e)
        }
    }

    private fun confirmFinish() {
        val position = selectedPosition.value
        if (position < 0) {
            return
        }

        val extraBundle = Bundle()
        extraBundle.putBoolean(AppConfig.TASKER_EXTRA_BUNDLE_SWITCH, switchState.value)
        extraBundle.putString(AppConfig.TASKER_EXTRA_BUNDLE_GUID, items[position].guid)
        val intent = Intent()

        val blurb = getString(
            if (switchState.value) R.string.tasker_blurb_start else R.string.tasker_blurb_stop,
            items[position].label
        )

        intent.putExtra(AppConfig.TASKER_EXTRA_BUNDLE, extraBundle)
        intent.putExtra(AppConfig.TASKER_EXTRA_STRING_BLURB, blurb)
        setResult(RESULT_OK, intent)
        finish()
    }
}

@Composable
fun TaskerScreen(
    items: List<TaskerItem>,
    switchState: MutableState<Boolean>,
    selectedPosition: MutableState<Int>,
    onBackClick: () -> Unit,
    onSave: () -> Unit
) {
    SatraLazyPage(
        title = stringResource(R.string.sx_tasker_title),
        onBack = onBackClick,
        actions = { SatraBarTextAction(stringResource(R.string.sx_save), onClick = onSave, enabled = selectedPosition.value >= 0) },
    ) {
        item(key = "switch") {
            LazyGroupRow(0, 1) {
                SatraSwitchRow(
                    title = stringResource(R.string.tasker_start_service),
                    checked = switchState.value,
                    onCheckedChange = { switchState.value = it },
                    icon = SatraIcons.Power,
                    iconColor = Satra.colors.success,
                )
            }
        }
        item(key = "hdr") { GroupHeader(stringResource(R.string.sx_tasker_pick)) }
        if (items.isEmpty()) {
            item(key = "empty") {
                SatraEmptyState(
                    icon = SatraIcons.Servers,
                    title = stringResource(R.string.sx_servers_empty_title),
                    message = stringResource(R.string.sx_servers_empty_msg),
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }
        itemsIndexed(items, key = { _, item -> item.guid }) { index, item ->
            LazyGroupRow(index, items.size, separatorInset = 54.dp) {
                SatraRow(
                    title = item.label.ifBlank { "—" },
                    leading = { SatraCheckCircle(selectedPosition.value == index) },
                    onClick = { selectedPosition.value = index },
                )
            }
        }
    }
}
