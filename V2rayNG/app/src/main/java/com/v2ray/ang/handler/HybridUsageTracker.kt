package com.v2ray.ang.handler

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.multiprocess.RemoteWorkManager
import com.v2ray.ang.AngApplication
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.SubscriptionUpdateMessage
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.helper.MessageHelper
import com.v2ray.ang.util.LogUtil
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Hybrid subscription quota: remote baseline + local core traffic delta between syncs.
 *
 * displayUsed = baselineUsed + localDeltaBytes
 * Pull-to-refresh / successful sub update resets baseline from Subscription-Userinfo.
 * A 15-minute WorkManager task refreshes subscriptions and logs baseline vs remote drift.
 */
object HybridUsageTracker {

    private const val KEY_BASELINE_USED = "hybrid_sub_baseline_used_"
    private const val KEY_BASELINE_AT = "hybrid_sub_baseline_at_"
    private const val KEY_LOCAL_DELTA = "hybrid_local_delta_bytes"
    private const val KEY_ACTIVE_SUB = "hybrid_local_delta_sub_id"
    private const val WORK_NAME = "hybrid_quota_sync_15m"
    private const val SYNC_INTERVAL_MINUTES = 15L

    data class DisplayUsage(
        val displayUsed: Long,
        val baselineUsed: Long,
        val localDelta: Long,
        val total: Long,
        val remaining: Long?,
        val fractionUsed: Float?,
    )

    /** Pure display math — unit-tested. */
    fun computeDisplayUsed(baselineUsed: Long, localDelta: Long): Long =
        (baselineUsed.coerceAtLeast(0L) + localDelta.coerceAtLeast(0L)).coerceAtLeast(0L)

    fun computeDisplay(
        baselineUsed: Long,
        localDelta: Long,
        total: Long,
    ): DisplayUsage {
        val used = computeDisplayUsed(baselineUsed, localDelta)
        val remaining = if (total > 0L) (total - used).coerceAtLeast(0L) else null
        val fraction = if (total > 0L) {
            (used.toDouble() / total.toDouble()).toFloat().coerceIn(0f, 1f)
        } else {
            null
        }
        return DisplayUsage(
            displayUsed = used,
            baselineUsed = baselineUsed.coerceAtLeast(0L),
            localDelta = localDelta.coerceAtLeast(0L),
            total = total,
            remaining = remaining,
            fractionUsed = fraction,
        )
    }

    fun displayFor(subId: String, subscription: SubscriptionItem): DisplayUsage {
        val baseline = baselineUsed(subId).takeIf { it >= 0L }
            ?: subscription.usedBytes()
        val delta = if (activeSubId() == subId) localDelta() else 0L
        return computeDisplay(baseline, delta, subscription.total)
    }

    fun baselineUsed(subId: String): Long =
        MmkvManager.decodeSettingsLong(KEY_BASELINE_USED + subId, -1L)

    fun baselineAt(subId: String): Long =
        MmkvManager.decodeSettingsLong(KEY_BASELINE_AT + subId, 0L)

    fun localDelta(): Long =
        MmkvManager.decodeSettingsLong(KEY_LOCAL_DELTA, 0L).coerceAtLeast(0L)

    fun activeSubId(): String =
        MmkvManager.decodeSettingsString(KEY_ACTIVE_SUB).orEmpty()

    /**
     * Reset baseline from the latest remote userinfo and clear the local delta for [subId].
     */
    fun resetBaseline(subId: String, remoteUsedBytes: Long) {
        val used = remoteUsedBytes.coerceAtLeast(0L)
        MmkvManager.encodeSettings(KEY_BASELINE_USED + subId, used)
        MmkvManager.encodeSettings(KEY_BASELINE_AT + subId, System.currentTimeMillis())
        if (activeSubId() == subId || activeSubId().isEmpty()) {
            MmkvManager.encodeSettings(KEY_LOCAL_DELTA, 0L)
            MmkvManager.encodeSettings(KEY_ACTIVE_SUB, subId)
        }
        LogUtil.d(
            AppConfig.TAG,
            "HybridUsage: baseline reset sub=$subId used=$used",
        )
    }

    fun ensureBaseline(subId: String, subscription: SubscriptionItem) {
        val remote = subscription.usedBytes()
        val baseline = baselineUsed(subId)
        if (baseline < 0L) {
            resetBaseline(subId, remote)
            return
        }
        // Remote userinfo advanced (or jumped) while we had no local traffic — adopt it
        // so Home quota cards stay aligned with the subscription after pull-refresh.
        if (localDelta() == 0L && remote > baseline) {
            resetBaseline(subId, remote)
        }
    }

    /**
     * After a remote sync, compare hybrid display vs fresh remote used and log the gap,
     * then adopt the remote value as the new baseline.
     */
    fun onRemoteSync(subId: String, subscription: SubscriptionItem) {
        val remoteUsed = subscription.usedBytes()
        val priorBaseline = baselineUsed(subId)
        val delta = if (activeSubId() == subId) localDelta() else 0L
        if (priorBaseline >= 0L) {
            val hybrid = computeDisplayUsed(priorBaseline, delta)
            val discrepancy = hybrid - remoteUsed
            LogUtil.d(
                AppConfig.TAG,
                "HybridUsage: 15m sync discrepancy sub=$subId hybrid=$hybrid remote=$remoteUsed " +
                    "gap=$discrepancy abs=${abs(discrepancy)}",
            )
        }
        resetBaseline(subId, remoteUsed)
    }

    /** Called once per second by [UsageSampler] with the proxied bytes of that second. */
    fun addLocalDelta(proxyBytes: Long) {
        if (proxyBytes <= 0L) return
        MmkvManager.encodeSettings(KEY_LOCAL_DELTA, localDelta() + proxyBytes)
        val subId = MmkvManager.getSelectServer()
            ?.let { MmkvManager.decodeServerConfig(it)?.subscriptionId }
            ?.takeIf { it.isNotBlank() }
        if (!subId.isNullOrBlank() && activeSubId() != subId) {
            MmkvManager.encodeSettings(KEY_ACTIVE_SUB, subId)
        }
    }

    fun schedulePeriodicSync(context: Context = AngApplication.application) {
        val request = PeriodicWorkRequestBuilder<QuotaSyncTask>(
            SYNC_INTERVAL_MINUTES,
            TimeUnit.MINUTES,
            // Flex window lets WorkManager batch this with other jobs instead of waking on its own.
            5L,
            TimeUnit.MINUTES,
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build(),
            )
            .addTag(WORK_NAME)
            .build()
        RemoteWorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
        LogUtil.i(AppConfig.TAG, "HybridUsage: scheduled 15m quota sync")
    }

    class QuotaSyncTask(context: Context, params: WorkerParameters) :
        CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            val ids = MmkvManager.decodeSubscriptions()
                .filter { it.subscription.enabled && it.subscription.url.isNotEmpty() }
                .map { it.guid }
            if (ids.isEmpty()) return Result.success()
            MessageHelper.sendMsg2SubscriptionService(
                applicationContext,
                SubscriptionUpdateMessage(AppConfig.MSG_SUB_UPDATE_START, true, ids),
            )
            // Discrepancy logging happens when AngConfigManager finishes each update
            // via [onRemoteSync] after headers are applied.
            LogUtil.d(AppConfig.TAG, "HybridUsage: 15m sync requested for ${ids.size} subs")
            return Result.success()
        }
    }
}
