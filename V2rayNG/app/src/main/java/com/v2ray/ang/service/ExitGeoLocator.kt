package com.v2ray.ang.service

import android.content.Context
import com.v2ray.ang.AppConfig
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.isComplexType
import com.v2ray.ang.extension.isNotNullEmpty
import com.v2ray.ang.handler.ExitIpProbe
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SpeedtestManager
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible

/**
 * Finds the real exit country of configs in the background, right after they were imported, so
 * flags appear without the user running a ping test. Four probes at a time, a hard deadline per
 * probe, one retry for configs that answer but whose exit lookup failed, and cancellable as a
 * whole. Each finished config is reported through [onResult] (true = exit country measured).
 */
class ExitGeoLocator(
    private val context: Context,
    private val guids: List<String>,
    private val onResult: (guid: String, ok: Boolean) -> Unit,
    private val onFinish: () -> Unit,
) {
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(PARALLEL))

    fun start() {
        val jobs = guids.distinct().map { guid ->
            scope.launch {
                val ok = try { locate(guid) } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    LogUtil.w(AppConfig.TAG, "ExitGeoLocator: $guid failed: ${e.message}")
                    false
                }
                if (isActive) onResult(guid, ok)
            }
        }
        scope.launch {
            joinAll(*jobs.toTypedArray())
            onFinish()
        }
    }

    fun cancel() = scope.cancel()

    private suspend fun locate(guid: String): Boolean {
        // Which configs need measuring (new, failed, or rechecks) was decided by the caller.
        val profile = MmkvManager.decodeServerConfig(guid) ?: return false
        if (profile.configType == EConfigType.CUSTOM) return false
        // Unreachable servers fail fast instead of burning a probe slot for half a minute.
        if (!profile.configType.isComplexType() && profile.configType != EConfigType.HYSTERIA2 &&
            profile.configType != EConfigType.WIREGUARD && profile.alpn?.startsWith("h3") != true &&
            profile.server.isNotNullEmpty() && profile.serverPort?.toIntOrNull() != null
        ) {
            val tcp = runInterruptible { SpeedtestManager.socketConnectTime(profile.server.orEmpty(), profile.serverPort!!.toInt(), 2500) }
            if (tcp <= -1L) {
                MmkvManager.markServerGeoAttempt(guid)
                return false
            }
        }
        repeat(ATTEMPTS) { attempt ->
            if (attempt > 0) delay(RETRY_DELAY_MS)
            val r = runInterruptible { ExitIpProbe.probeBounded(context, guid, PROBE_TIMEOUT_MS) }
            if (r.exitVerified) return true
        }
        return false
    }

    companion object {
        const val PARALLEL = 4
        const val ATTEMPTS = 2
        const val RETRY_DELAY_MS = 3_000L
        const val PROBE_TIMEOUT_MS = 50_000L
    }
}
