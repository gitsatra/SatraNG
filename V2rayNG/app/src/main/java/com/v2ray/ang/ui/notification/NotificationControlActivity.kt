package com.v2ray.ang.ui.notification

import android.content.Intent
import com.v2ray.ang.ui.compose.satra.SatraScrollPage
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.handler.NotificationPrefs
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.satra.GroupFooter
import com.v2ray.ang.ui.compose.satra.GroupGap
import com.v2ray.ang.ui.compose.satra.GroupHeader
import com.v2ray.ang.ui.compose.satra.Satra
import com.v2ray.ang.ui.compose.satra.SatraIcons
import com.v2ray.ang.ui.compose.satra.SatraRow
import com.v2ray.ang.ui.compose.satra.SatraSpace
import com.v2ray.ang.ui.compose.satra.SatraSwitchRow
import com.v2ray.ang.ui.compose.satra.groupRow

class NotificationControlActivity : BaseComponentActivity() {
    @Composable
    override fun ScreenContent() {
        val prefs by NotificationPrefs.state.collectAsStateWithLifecycle()

        SatraScrollPage(
            title = stringResource(R.string.satra_notif_control),
            onBack = { finish() },
        ) {
            GroupFooter(stringResource(R.string.satra_notif_control_desc))
            GroupHeader(stringResource(R.string.satra_notif_preview))
            NotifPreviewCard(
                title = stringResource(R.string.satra_notif_title_connected),
                subtitle = buildString {
                    if (prefs.showLocation) append("🇩🇪 Berlin · ")
                    append("SatraNG")
                    if (prefs.showSpeed) append("\n↑ 1.2 MB/s  ↓ 3.4 MB/s")
                },
                actions = listOfNotNull(
                    if (prefs.actionStop) stringResource(R.string.satra_notif_action_stop) else null,
                    if (prefs.actionRestart) stringResource(R.string.satra_notif_action_restart) else null,
                ),
            )
            GroupGap()
            SatraSwitchRow(stringResource(R.string.satra_notif_show_speed), prefs.showSpeed, { v -> NotificationPrefs.update { it.copy(showSpeed = v) } }, Modifier.groupRow(), icon = SatraIcons.Speed, iconColor = Satra.colors.info)
            SatraSwitchRow(stringResource(R.string.satra_notif_show_location), prefs.showLocation, { v -> NotificationPrefs.update { it.copy(showLocation = v) } }, Modifier.groupRow(), icon = SatraIcons.Globe, iconColor = Satra.colors.success)
            SatraSwitchRow(stringResource(R.string.satra_notif_action_stop), prefs.actionStop, { v -> NotificationPrefs.update { it.copy(actionStop = v) } }, Modifier.groupRow(), icon = SatraIcons.Stop, iconColor = Satra.colors.danger)
            SatraSwitchRow(stringResource(R.string.satra_notif_action_restart), prefs.actionRestart, { v -> NotificationPrefs.update { it.copy(actionRestart = v) } }, Modifier.groupRow(), icon = SatraIcons.Refresh, iconColor = Satra.colors.accent)
            GroupGap()
            SatraRow(
                title = stringResource(R.string.satra_notif_open_system),
                icon = SatraIcons.Bell,
                iconColor = Satra.colors.warning,
                chevron = true,
                modifier = Modifier.groupRow(),
                onClick = {
                    runCatching {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            startActivity(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                                putExtra(Settings.EXTRA_CHANNEL_ID, AppConfig.RAY_NG_CHANNEL_ID)
                            })
                        } else {
                            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = android.net.Uri.parse("package:$packageName")
                            })
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun NotifPreviewCard(title: String, subtitle: String, actions: List<String>) {
    val c = Satra.colors
    Column(
        Modifier
            .padding(horizontal = SatraSpace.Gutter)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(c.surface)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)).background(c.accent), contentAlignment = Alignment.Center) {
                Icon(SatraIcons.Shield, null, tint = c.onAccent, modifier = Modifier.size(14.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text("SatraNG", style = Satra.type.caption, color = c.textSecondary)
        }
        Spacer(Modifier.height(10.dp))
        Text(title, style = Satra.type.headline, color = c.success)
        Spacer(Modifier.height(2.dp))
        Text(subtitle, style = Satra.type.footnote, color = c.textSecondary)
        if (actions.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                actions.forEach { Text(it, style = Satra.type.subhead.copy(fontWeight = FontWeight.SemiBold), color = c.accent) }
            }
        }
    }
}
