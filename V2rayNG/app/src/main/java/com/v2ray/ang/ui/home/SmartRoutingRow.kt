package com.v2ray.ang.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.satra.Satra
import com.v2ray.ang.ui.compose.satra.SatraIconButton
import com.v2ray.ang.ui.compose.satra.SatraIcons
import com.v2ray.ang.ui.compose.satra.SatraShapes
import com.v2ray.ang.ui.compose.satra.SatraSwitchVisual
import com.v2ray.ang.ui.compose.satra.satraClickable

/** Region of the active smart-routing preset. */
enum class SmartRegion { Iran, Russia, China }

/**
 * One calm row on Home: "Smart routing (Iran)" + a one-line explanation, an info icon to the
 * routing settings, and a switch. Off = full tunnel. [showTip] adds a one-time first-run hint.
 */
@Composable
internal fun SmartRoutingRow(
    on: Boolean,
    region: SmartRegion,
    showTip: Boolean,
    onToggle: (Boolean) -> Unit,
    onInfo: () -> Unit,
    onDismissTip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Satra.colors
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clip(SatraShapes.Large).background(c.surface)
                .satraClickable { onToggle(!on) }
                .padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(when (region) { SmartRegion.Iran -> R.string.sx_smart_routing_ir; SmartRegion.Russia -> R.string.sx_smart_routing_ru; SmartRegion.China -> R.string.sx_smart_routing_cn }),
                    style = Satra.type.subhead.copy(fontWeight = FontWeight.SemiBold), color = c.textPrimary, maxLines = 1,
                )
                Text(
                    stringResource(
                        if (!on) R.string.sx_smart_routing_off
                        else when (region) { SmartRegion.Iran -> R.string.sx_smart_routing_ir_note; SmartRegion.Russia -> R.string.sx_smart_routing_ru_note; SmartRegion.China -> R.string.sx_smart_routing_cn_note },
                    ),
                    style = Satra.type.caption, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            SatraIconButton(SatraIcons.Info, stringResource(R.string.sx_smart_routing_info), onClick = onInfo)
            Spacer(Modifier.width(2.dp))
            SatraSwitchVisual(checked = on)
        }
        AnimatedVisibility(showTip) {
            Row(
                Modifier.padding(top = 6.dp).fillMaxWidth().clip(SatraShapes.Medium).background(c.accent.copy(alpha = 0.12f))
                    .satraClickable(onClick = onDismissTip).padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(SatraIcons.Info, null, tint = c.accent, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.sx_smart_routing_tip), style = Satra.type.caption, color = c.textPrimary, modifier = Modifier.weight(1f), maxLines = 2)
                Icon(SatraIcons.Close, null, tint = c.textSecondary, modifier = Modifier.size(14.dp))
            }
        }
    }
}
