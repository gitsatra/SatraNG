package com.v2ray.ang.ui.logcat

import android.content.ClipData
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.satra.LazyGroupRow
import com.v2ray.ang.ui.compose.satra.Satra
import com.v2ray.ang.ui.compose.satra.SatraEmptyState
import com.v2ray.ang.ui.compose.satra.SatraIconButton
import com.v2ray.ang.ui.compose.satra.SatraIcons
import com.v2ray.ang.ui.compose.satra.SatraLazyPage
import com.v2ray.ang.ui.compose.satra.SatraMenu
import com.v2ray.ang.ui.compose.satra.SatraMenuItem
import com.v2ray.ang.ui.compose.satra.ltr
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LogcatActivity : BaseComponentActivity() {
    private val viewModel: LogcatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) viewModel.loadLogcat()
    }

    @Composable
    override fun ScreenContent() {
        LogcatScreen(
            viewModel = viewModel,
            onBackClick = { finish() },
            onShareLogcat = { shareLogcat() }
        )
    }

    private fun shareLogcat() {
        lifecycleScope.launch(Dispatchers.IO) {
            val logText = viewModel.filteredLogs.value.joinToString("\n")

            val result = try {
                val shareDir = File(cacheDir, "shared_logs").apply {
                    mkdirs()
                }

                shareDir.listFiles()?.forEach { it.delete() }

                val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
                val logFile = File(shareDir, "v2rayNG_logcat_$timestamp.txt")
                logFile.writeText(logText, Charsets.UTF_8)

                val uri = FileProvider.getUriForFile(
                    this@LogcatActivity,
                    "${packageName}.cache",
                    logFile
                )

                uri to logFile.name
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to share Logcat", e)
                withContext(Dispatchers.Main) {
                    toastError(R.string.toast_failure)
                }
                return@launch
            }

            withContext(Dispatchers.Main) {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, result.first)
                    putExtra(Intent.EXTRA_SUBJECT, result.second)
                    putExtra(Intent.EXTRA_TITLE, result.second)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    clipData = ClipData.newUri(contentResolver, result.second, result.first)
                }

                startActivity(
                    Intent.createChooser(
                        shareIntent,
                        getString(R.string.logcat_share)
                    )
                )
            }
        }
    }
}

@Composable
fun LogcatScreen(
    viewModel: LogcatViewModel,
    onBackClick: () -> Unit,
    onShareLogcat: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val logs by viewModel.logEntries.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    var searchQuery by rememberSaveable { mutableStateOf("") }
    LogcatContent(
        logs = logs,
        isLoading = isLoading,
        searchQuery = searchQuery,
        onSearchChange = { searchQuery = it; viewModel.filter(it) },
        onBack = onBackClick,
        onRefresh = { viewModel.loadLogcat() },
        onCopyAll = { viewModel.copyLogcat() },
        onShare = onShareLogcat,
        onClear = { scope.launch(Dispatchers.IO) { viewModel.clearLogcat() } },
        onCopyLine = { Utils.setClipboard(context, it); context.toast(R.string.sx_logs_copied) },
    )
}

/** Stateless log page (also rendered by the screenshot tests). */
@Composable
fun LogcatContent(
    logs: List<LogcatEntry>,
    isLoading: Boolean,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onCopyAll: () -> Unit,
    onShare: () -> Unit,
    onClear: () -> Unit,
    onCopyLine: (String) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    SatraLazyPage(
        title = stringResource(R.string.sx_logs_title),
        onBack = onBack,
        isLoading = isLoading,
        searchQuery = searchQuery,
        onSearchChange = onSearchChange,
        searchPlaceholder = stringResource(R.string.sx_search),
        actions = {
            SatraIconButton(SatraIcons.Refresh, stringResource(R.string.acc_refresh), onRefresh, enabled = !isLoading)
            Box {
                SatraIconButton(SatraIcons.More, stringResource(R.string.acc_more), { menu = true })
                SatraMenu(expanded = menu, onDismiss = { menu = false }) {
                    SatraMenuItem(stringResource(R.string.acc_copy_log), { menu = false; onCopyAll() }, SatraIcons.Copy)
                    SatraMenuItem(stringResource(R.string.acc_share_log), { menu = false; onShare() }, SatraIcons.Share)
                    SatraMenuItem(stringResource(R.string.sx_logs_clear), { menu = false; onClear() }, SatraIcons.Trash, destructive = true)
                }
            }
        },
    ) {
        if (logs.isEmpty()) {
            item(key = "empty") {
                SatraEmptyState(
                    icon = SatraIcons.Terminal,
                    title = stringResource(R.string.sx_logs_empty_title),
                    message = stringResource(R.string.sx_logs_empty_msg),
                    actionText = stringResource(R.string.acc_refresh),
                    onAction = onRefresh,
                    modifier = Modifier.padding(top = 32.dp),
                )
            }
        } else {
            items(logs.size, key = { logs[it].key }, contentType = { "log" }) { i ->
                LazyGroupRow(i, logs.size, separatorInset = 14.dp) {
                    LogcatItem(log = logs[i].text, onLongClick = { onCopyLine(logs[i].text) })
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LogcatItem(log: String, onLongClick: () -> Unit) {
    val (tag, content) = remember(log) {
        if (log.isEmpty()) "" to "" else {
            val parts = log.split("):", limit = 2)
            val tagPart = parts.first().split("(", limit = 2).first().trim()
            val contentPart = if (parts.size > 1) parts.last().trim() else ""
            tagPart to contentPart
        }
    }
    val c = Satra.colors
    val level = tag.substringAfter(' ').substringAfter(' ').take(1)
    val tint = when (level) { "E", "F" -> c.danger; "W" -> c.warning; else -> c.textSecondary }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = {}, onLongClick = onLongClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(text = ltr(tag), style = Satra.type.caption, color = tint, fontFamily = FontFamily.Monospace)
        if (content.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = content, style = Satra.type.footnote, color = c.textPrimary, fontFamily = FontFamily.Monospace)
        }
    }
}
