package com.v2ray.ang.ui

import android.content.Intent
import com.v2ray.ang.ui.compose.satra.SatraScrollPage
import android.os.Bundle
import android.webkit.WebView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.R
import com.v2ray.ang.core.CoreNativeManager
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.NavigationBarsSpacer
import com.v2ray.ang.ui.compose.SatraColors
import com.v2ray.ang.ui.compose.SettingsMenuItem
import com.v2ray.ang.ui.compose.VersionInfoBlock
import com.v2ray.ang.util.Utils

class AboutActivity : BaseComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    @Composable
    override fun ScreenContent() {
        AboutScreen(
            onBackClick = { finish() },
            onTranslatorsClick = {
                startActivity(Intent(this, TranslatorsActivity::class.java))
            }
        )
    }
}

@Composable
fun AboutScreen(
    onBackClick: () -> Unit,
    onTranslatorsClick: () -> Unit
) {
    val context = LocalContext.current
    var showOssDialog by remember { mutableStateOf(false) }

    val libVersion = CoreNativeManager.getLibVersion()
    val versionText = "v${BuildConfig.VERSION_NAME} ($libVersion)"
    val appIdText = BuildConfig.APPLICATION_ID

    SatraScrollPage(
        title = stringResource(R.string.title_about),
        onBack = onBackClick,
    ) {
        run {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = com.v2ray.ang.ui.compose.satra.SatraSpace.Gutter, vertical = 8.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(com.v2ray.ang.ui.compose.satra.SatraBrand.OrangeLight, com.v2ray.ang.ui.compose.satra.SatraBrand.OrangeDeep)
                        )
                    )
                    .padding(24.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        com.v2ray.ang.ui.compose.satra.SatraLogo(connected = false, size = 56.dp)
                    }
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.satra_brand_tagline),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.88f)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            SettingsMenuItem(
                icon = painterResource(R.drawable.ic_source_code_24dp),
                title = stringResource(R.string.title_source_code),
                onClick = { Utils.openUri(context, AppConfig.APP_URL) }
            )
            SettingsMenuItem(
                icon = painterResource(R.drawable.license_24px),
                title = stringResource(R.string.title_oss_license),
                onClick = { showOssDialog = true }
            )
            SettingsMenuItem(
                icon = painterResource(R.drawable.ic_translate_24dp),
                title = stringResource(R.string.title_translators),
                onClick = onTranslatorsClick
            )
            SettingsMenuItem(
                icon = painterResource(R.drawable.ic_feedback_24dp),
                title = stringResource(R.string.title_pref_feedback),
                onClick = { Utils.openUri(context, AppConfig.APP_ISSUES_URL) }
            )
            SettingsMenuItem(
                icon = painterResource(R.drawable.ic_telegram_24dp),
                title = stringResource(R.string.title_tg_channel),
                onClick = { Utils.openUri(context, AppConfig.TG_CHANNEL_URL) }
            )
            SettingsMenuItem(
                icon = painterResource(R.drawable.ic_privacy_24dp),
                title = stringResource(R.string.title_privacy_policy),
                onClick = { Utils.openUri(context, AppConfig.APP_PRIVACY_POLICY) }
            )
            VersionInfoBlock(
                versionText = versionText,
                appIdText = appIdText
            )
            NavigationBarsSpacer()
        }
    }

    if (showOssDialog) {
        AlertDialog(
            onDismissRequest = { showOssDialog = false },
            title = { Text(stringResource(R.string.title_oss_license)) },
            text = {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            loadUrl("file:///android_asset/open_source_licenses.html")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 300.dp)
                )
            },
            confirmButton = {
                TextButton(onClick = { showOssDialog = false }) {
                    Text(stringResource(R.string.action_ok))
                }
            },
            containerColor = com.v2ray.ang.ui.compose.satra.Satra.colors.surfaceRaised,
            shape = com.v2ray.ang.ui.compose.satra.SatraShapes.XLarge,
            modifier = Modifier.padding(bottom = 60.dp)
        )
    }
}
