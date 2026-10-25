package com.v2ray.ang.ui.server

import android.os.Bundle
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.isComplexType
import com.v2ray.ang.extension.moveItem
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.DeleteConfirmDialog
import com.v2ray.ang.ui.compose.FormDropdownField
import com.v2ray.ang.ui.compose.FormTextField
import com.v2ray.ang.ui.compose.reorderableDragHandle
import com.v2ray.ang.ui.compose.verticalScrollbar
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.util.UUID

class ServerProxyChainActivity : BaseComponentActivity() {

    private val editGuid by lazy { intent.getStringExtra("guid").orEmpty() }
    private val isRunning by lazy {
        intent.getBooleanExtra("isRunning", false)
                && editGuid.isNotEmpty()
                && editGuid == MmkvManager.getSelectServer()
    }
    private val subscriptionId by lazy { intent.getStringExtra("subscriptionId") }

    private lateinit var allRemarks: List<String>
    private lateinit var initialRemarks: String
    private lateinit var initialMembers: List<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        allRemarks = SettingsManager.getProfileRemarks(
            excludeConfigTypes = setOf(EConfigType.CUSTOM, EConfigType.POLICYGROUP, EConfigType.PROXYCHAIN)
        )
        val config = MmkvManager.decodeServerConfig(editGuid)
        initialRemarks = config?.remarks ?: ""
        initialMembers = config?.proxyChainProfiles?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: listOf("", "")
    }

    @Composable
    override fun ScreenContent() {
        ProxyChainScreen(
            editGuid = editGuid,
            isRunning = isRunning,
            initialRemarks = initialRemarks,
            initialMembers = initialMembers,
            allRemarks = allRemarks,
            onBackClick = { finish() },
            onSave = { remarks, members -> saveServer(remarks, members) },
            onDelete = { deleteServer() }
        )
    }

    private fun saveServer(
        remarks: String,
        members: List<String>
    ): Boolean {
        if (remarks.isBlank()) {
            return false
        }

        val chainMembers = members
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (chainMembers.size != members.size) {
            toast(R.string.server_proxy_chain_members_unselected)
            return false
        }

        if (chainMembers.size < 2) {
            toast(R.string.server_proxy_chain_members_insufficient)
            return false
        }

        val invalidMembers = chainMembers.filter { member ->
            val profile = SettingsManager.getServerViaRemarks(member)
            profile == null || profile.configType.isComplexType()
        }

        if (invalidMembers.isNotEmpty()) {
            toast(
                getString(
                    R.string.server_proxy_chain_members_invalid,
                    invalidMembers.joinToString(", ")
                )
            )
            return false
        }

        val config =
            MmkvManager.decodeServerConfig(editGuid)
                ?: ProfileItem.create(EConfigType.PROXYCHAIN)

        config.remarks = remarks.trim()
        config.proxyChainProfiles =
            chainMembers.joinToString(",")

        config.description =
            chainMembers.joinToString(" -> ")

        if (
            config.subscriptionId.isEmpty() &&
            !subscriptionId.isNullOrEmpty()
        ) {
            config.subscriptionId = subscriptionId.orEmpty()
        }

        val savedGuid = MmkvManager.encodeServerConfig(
            editGuid,
            config
        )

        toastSuccess(R.string.toast_success)

        ProfileEditorResult.run {
            finishSaved(
                guid = savedGuid,
                restartService = isRunning
            )
        }

        return true
    }

    private fun deleteServer(): Boolean {
        if (editGuid.isEmpty()) {
            return false
        }

        if (editGuid == MmkvManager.getSelectServer()) {
            toast(R.string.toast_action_not_allowed)
            return false
        }

        MmkvManager.removeServer(editGuid)

        ProfileEditorResult.run {
            finishDeleted(editGuid)
        }

        return true
    }
}

@Composable
fun ProxyChainScreen(
    editGuid: String,
    isRunning: Boolean,
    initialRemarks: String,
    initialMembers: List<String>,
    allRemarks: List<String>,
    onBackClick: () -> Unit,
    onSave: (String, List<String>) -> Boolean,
    onDelete: () -> Unit
) {
    var remarks by rememberSaveable { mutableStateOf(initialRemarks) }
    var isRemarksError by rememberSaveable { mutableStateOf(false) }
    var members by rememberSaveable { mutableStateOf(initialMembers) }
    var memberKeys by rememberSaveable { mutableStateOf(List(initialMembers.size) { UUID.randomUUID().toString() }) }
    var showProfileDeleteConfirm by remember { mutableStateOf(false) }
    var memberToDeleteKey by rememberSaveable { mutableStateOf<String?>(null) }
    val showDelete = editGuid.isNotEmpty() && !isRunning

    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val fromIndex = memberKeys.indexOf(from.key)
        val toIndex = memberKeys.indexOf(to.key)
        if (fromIndex != -1 && toIndex != -1) {
            val reordered = members.toMutableList()
            val reorderedKeys = memberKeys.toMutableList()
            if (reordered.moveItem(fromIndex, toIndex)) {
                reorderedKeys.moveItem(fromIndex, toIndex)
                members = reordered
                memberKeys = reorderedKeys
            }
        }
    }

    val sc = com.v2ray.ang.ui.compose.satra.Satra.colors
    com.v2ray.ang.ui.compose.satra.SatraLazyPage(
        title = stringResource(R.string.sx_chain_title),
        onBack = onBackClick,
        state = lazyListState,
        modifier = Modifier.imePadding(),
        actions = {
            if (showDelete) {
                com.v2ray.ang.ui.compose.satra.SatraIconButton(icon = com.v2ray.ang.ui.compose.satra.SatraIcons.Trash, contentDescription = stringResource(R.string.acc_delete), onClick = { showProfileDeleteConfirm = true }, tint = sc.danger)
            }
            com.v2ray.ang.ui.compose.satra.SatraBarTextAction(stringResource(R.string.sx_save), onClick = {
                isRemarksError = remarks.isBlank()
                if (!isRemarksError) onSave(remarks, members)
            })
        },
    ) {
        item(key = "remarks_field") {
            FormTextField(
                label = stringResource(R.string.sx_chain_name),
                value = remarks,
                onValueChange = { remarks = it; isRemarksError = false },
                isError = isRemarksError
            )
        }
        item(key = "members_header") { com.v2ray.ang.ui.compose.satra.GroupHeader(stringResource(R.string.server_proxy_chain_members)) }
        val count = members.size + 1
        itemsIndexed(items = members, key = { index, _ -> memberKeys.getOrElse(index) { "m$index" } }) { index, member ->
            val memberKey = memberKeys.getOrElse(index) { "m$index" }
            ReorderableItem(reorderableState, key = memberKey) { isDragging ->
                val elevation by animateDpAsState(if (isDragging) 6.dp else 0.dp)
                com.v2ray.ang.ui.compose.satra.LazyGroupRow(index, count, Modifier.shadow(elevation), separatorInset = 48.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 6.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(36.dp).then(reorderableDragHandle()),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "${index + 1}",
                                style = com.v2ray.ang.ui.compose.satra.Satra.type.headline,
                                color = sc.accent,
                            )
                        }
                        FormDropdownField(
                            label = stringResource(R.string.sx_chain_hop, index + 1),
                            placeholder = stringResource(R.string.server_proxy_chain_member_unselected),
                            value = member,
                            options = allRemarks,
                            onValueChange = { newVal ->
                                members = members.toMutableList().also { if (index < it.size) it[index] = newVal }
                            },
                            editable = true,
                            inset = false,
                            modifier = Modifier.weight(1f).padding(vertical = 2.dp)
                        )
                        com.v2ray.ang.ui.compose.satra.SatraIconButton(icon = com.v2ray.ang.ui.compose.satra.SatraIcons.Minus, contentDescription = stringResource(R.string.acc_remove), onClick = {
                            if (member.isBlank()) {
                                val (remainingMembers, remainingKeys) = withoutProxyChainMember(members, memberKeys, memberKey)
                                members = remainingMembers
                                memberKeys = remainingKeys
                            } else {
                                memberToDeleteKey = memberKey
                            }
                        }, tint = sc.danger)
                    }
                }
            }
        }
        item(key = "add_member") {
            com.v2ray.ang.ui.compose.satra.LazyGroupRow(count - 1, count) {
                com.v2ray.ang.ui.compose.satra.SatraRow(
                    title = stringResource(R.string.acc_add_member),
                    icon = com.v2ray.ang.ui.compose.satra.SatraIcons.Plus,
                    onClick = {
                        members = members + ""
                        memberKeys = memberKeys + UUID.randomUUID().toString()
                    },
                )
            }
        }
        item(key = "footer") { com.v2ray.ang.ui.compose.satra.GroupFooter(stringResource(R.string.sx_chain_footer)) }
    }

    if (showProfileDeleteConfirm) {
        DeleteConfirmDialog(
            message = stringResource(R.string.confirm_delete_profile),
            itemName = initialRemarks,
            onConfirm = { showProfileDeleteConfirm = false; onDelete() },
            onDismiss = { showProfileDeleteConfirm = false }
        )
    }
    memberToDeleteKey?.let { memberKey ->
        DeleteConfirmDialog(
            message = stringResource(R.string.confirm_delete_proxy_chain_member),
            itemName = members.getOrNull(memberKeys.indexOf(memberKey)).orEmpty(),
            onConfirm = {
                val (remainingMembers, remainingKeys) = withoutProxyChainMember(members, memberKeys, memberKey)
                members = remainingMembers
                memberKeys = remainingKeys
                memberToDeleteKey = null
            },
            onDismiss = { memberToDeleteKey = null }
        )
    }
}
