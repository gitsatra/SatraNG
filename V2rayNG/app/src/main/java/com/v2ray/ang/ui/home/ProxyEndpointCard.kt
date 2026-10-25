package com.v2ray.ang.ui.home

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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.handler.ProxyEndpoint
import com.v2ray.ang.ui.compose.satra.Satra
import com.v2ray.ang.ui.compose.satra.SatraIcons
import com.v2ray.ang.ui.compose.satra.SatraShapes
import com.v2ray.ang.ui.compose.satra.ltr
import com.v2ray.ang.ui.compose.satra.satraClickable

@Composable
fun proxyKindLabel(kind: ProxyEndpoint.Kind): String = stringResource(
    when (kind) {
        ProxyEndpoint.Kind.Socks -> R.string.sx_proxy_socks
        ProxyEndpoint.Kind.Http -> R.string.sx_proxy_http
        ProxyEndpoint.Kind.Mixed -> R.string.sx_proxy_mixed
    },
)

/** Proxy-only mode: the local address(es) other apps should use; each line copies on tap. */
@Composable
fun ProxyEndpointCard(
    endpoints: List<ProxyEndpoint>,
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier,
    sharedOnLan: Boolean = false,
    username: String? = null,
    showHint: Boolean = false,
) {
    if (endpoints.isEmpty()) return
    val c = Satra.colors
    Column(modifier.fillMaxWidth().clip(SatraShapes.Large).background(c.surface)) {
        endpoints.forEachIndexed { i, ep ->
            if (i > 0) com.v2ray.ang.ui.compose.satra.GroupSeparator(56.dp)
            Row(
                Modifier
                    .fillMaxWidth()
                    .satraClickable(pressedScale = 0.985f) { onCopy(ep.address) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                com.v2ray.ang.ui.compose.satra.IconTile(SatraIcons.Link, c.info)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.sx_proxy_local) + " · " + proxyKindLabel(ep.kind),
                        style = Satra.type.footnote, color = c.textSecondary, maxLines = 1,
                    )
                    Text(ltr(ep.address), style = Satra.type.headline, color = c.textPrimary, fontFamily = FontFamily.Monospace, maxLines = 1)
                }
                Icon(SatraIcons.Copy, stringResource(R.string.sx_proxy_tap_copy), tint = c.textTertiary, modifier = Modifier.size(18.dp))
            }
        }
        val notes = buildList {
            if (showHint) add(stringResource(R.string.sx_proxy_tap_copy))
            username?.let { add(stringResource(R.string.sx_proxy_user, it)) }
            if (sharedOnLan) add(stringResource(R.string.sx_proxy_lan))
        }
        if (notes.isNotEmpty()) {
            Text(
                notes.joinToString(" · "),
                style = Satra.type.caption, color = c.textSecondary,
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 10.dp),
            )
        }
    }
}
