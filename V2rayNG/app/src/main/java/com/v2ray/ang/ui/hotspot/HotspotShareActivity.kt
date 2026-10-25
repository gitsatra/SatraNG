package com.v2ray.ang.ui.hotspot

import androidx.compose.foundation.Image
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.core.LauncherManager
import com.v2ray.ang.handler.HotspotShare
import com.v2ray.ang.handler.ProxyEndpoints
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.satra.InsetGroup
import com.v2ray.ang.ui.compose.satra.Satra
import com.v2ray.ang.ui.compose.satra.SatraIcons
import com.v2ray.ang.ui.compose.satra.SatraRow
import com.v2ray.ang.ui.compose.satra.SatraScrollPage
import com.v2ray.ang.ui.compose.satra.SatraShapes
import com.v2ray.ang.ui.compose.satra.SatraSwitchRow
import com.v2ray.ang.util.QRCodeDecoder
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Share the VPN with trusted devices on this phone's hotspot or Wi-Fi (SOCKS5 / HTTP proxy). */
class HotspotShareActivity : BaseComponentActivity() {
    @Composable
    override fun ScreenContent() {
        HotspotShareScreen(onBack = { finish() }, onApply = {
            // Restart a running connection so the inbounds re-bind.
            if (com.v2ray.ang.handler.SettingsChangeManager.consumeRestartService() && coreRunning()) {
                LauncherManager.restartService(this)
            }
        })
    }

    private fun coreRunning(): Boolean = try {
        java.net.Socket().use { it.connect(java.net.InetSocketAddress("127.0.0.1", SettingsManager.getHttpPort()), 300); true }
    } catch (_: Exception) {
        false
    }
}

@Composable
private fun HotspotShareScreen(onBack: () -> Unit, onApply: () -> Unit) {
    val c = Satra.colors
    val ctx = LocalContext.current
    var on by remember { mutableStateOf(HotspotShare.isOn()) }
    var auth by remember { mutableStateOf(SettingsManager.getSocksUsername() != null) }
    var tick by remember { mutableStateOf(0) }
    val ip by produceState<String?>(null, on, tick) { value = withContext(Dispatchers.IO) { HotspotShare.lanIp() } }
    val user = remember(on, auth, tick) { SettingsManager.getSocksUsername() }
    val pass = remember(on, auth, tick) { SettingsManager.getSocksPassword() }
    val endpoints = remember(on, tick) { ProxyEndpoints.build(ip ?: "—", SettingsManager.getSocksPort(), SettingsManager.getHttpPort()) }
    val copied = stringResource(R.string.sx_exit_ip_copied)
    fun copy(t: String) { Utils.setClipboard(ctx, t); android.widget.Toast.makeText(ctx, copied, android.widget.Toast.LENGTH_SHORT).show() }

    SatraScrollPage(title = stringResource(R.string.sx_hs_title), onBack = onBack, subtitle = stringResource(R.string.sx_hs_sub)) {
        InsetGroup(footer = stringResource(R.string.sx_hs_warn)) {
            row {
                SatraSwitchRow(
                    stringResource(R.string.sx_hs_toggle), checked = on, icon = SatraIcons.Wifi, iconColor = c.accent,
                    subtitle = stringResource(R.string.sx_hs_toggle_sub),
                    onCheckedChange = { v ->
                        if (v) HotspotShare.enable(auth) else HotspotShare.disable()
                        on = v; tick++; onApply()
                    },
                )
            }
            if (on) row {
                SatraSwitchRow(
                    stringResource(R.string.sx_hs_auth), checked = auth, icon = SatraIcons.Lock, iconColor = c.success,
                    subtitle = stringResource(R.string.sx_hs_auth_sub),
                    onCheckedChange = { v -> auth = v; HotspotShare.enable(v); tick++; onApply() },
                )
            }
        }
        if (on) {
            Spacer(Modifier.height(18.dp))
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp).clip(SatraShapes.Large).background(c.surface).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (ip == null) Text(stringResource(R.string.sx_hs_no_ip), style = Satra.type.footnote, color = c.warning)
                endpoints.forEach { e ->
                    val label = when (e.kind) {
                        com.v2ray.ang.handler.ProxyEndpoint.Kind.Socks -> "SOCKS5"
                        com.v2ray.ang.handler.ProxyEndpoint.Kind.Http -> "HTTP"
                        com.v2ray.ang.handler.ProxyEndpoint.Kind.Mixed -> "SOCKS5 + HTTP"
                    }
                    KeyValue(label, "\u2066${e.address}\u2069") { copy(e.address) }
                }
                if (user != null && pass != null) {
                    KeyValue(stringResource(R.string.sx_hs_user), user) { copy(user) }
                    KeyValue(stringResource(R.string.sx_hs_pass), pass) { copy(pass) }
                }
                val first = endpoints.firstOrNull()
                if (ip != null && first != null) {
                    val uri = HotspotShare.shareUri(if (first.kind == com.v2ray.ang.handler.ProxyEndpoint.Kind.Http) "http" else "socks5", ip!!, first.port, user, pass)
                    val qr by produceState<android.graphics.Bitmap?>(null, uri) { value = withContext(Dispatchers.Default) { QRCodeDecoder.createQRCode(uri, 600) } }
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        qr?.let { Image(it.asImageBitmap(), stringResource(R.string.sx_hs_qr), Modifier.size(180.dp).clip(SatraShapes.Medium)) }
                    }
                    KeyValue(stringResource(R.string.sx_hs_link), "\u2066$uri\u2069") { copy(uri) }
                }
            }
            Spacer(Modifier.height(18.dp))
            InsetGroup(header = stringResource(R.string.sx_hs_howto)) {
                row { SatraRow(stringResource(R.string.sx_hs_step_phone), subtitle = stringResource(R.string.sx_hs_step_phone_sub), icon = SatraIcons.Wifi, iconColor = c.info) }
                row { SatraRow(stringResource(R.string.sx_hs_step_pc), subtitle = stringResource(R.string.sx_hs_step_pc_sub), icon = SatraIcons.Globe, iconColor = c.info) }
            }
        }
        LaunchedEffect(Unit) { tick++ }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun KeyValue(label: String, value: String, onCopy: () -> Unit) {
    val c = Satra.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = Satra.type.caption, color = c.textSecondary)
            Text(value, style = Satra.type.callout.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium), color = c.textPrimary)
        }
        Spacer(Modifier.width(8.dp))
        com.v2ray.ang.ui.compose.satra.SatraIconButton(SatraIcons.Copy, stringResource(R.string.sx_hs_copy), onClick = onCopy)
    }
}
