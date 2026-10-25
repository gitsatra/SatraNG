package com.v2ray.ang.handler

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.ui.main.MainActivity
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.extension.toTrafficString

/** Limit notifications (warning, exceeded, global) with their quick actions. */
object UsageNotifier {
    private const val CHANNEL_ID = "satra_usage_limits"
    private const val BASE_ID = 7400
    const val ACTION = "com.v2ray.ang.action.USAGE_LIMIT"
    const val EXTRA_RULE = "rule"
    const val EXTRA_OP = "op"
    const val EXTRA_BYTES = "bytes"
    const val OP_BONUS = "bonus"
    const val OP_GLOBAL_BONUS = "global_bonus"
    const val EXTRA_OPEN_TAB = "satra_open_tab"

    fun limitExceeded(context: Context, rule: UsageRule) {
        val ctx = AppLocaleManager.localizedContext(context)
        val title = ctx.getString(R.string.sx_notif_limit_title, rule.label)
        val body = ctx.getString(
            R.string.sx_notif_limit_body,
            rule.limitBytes.toTrafficString(),
            periodPhrase(ctx, rule.period),
        )
        val hint = ctx.getString(R.string.sx_notif_topup_hint)
        post(ctx, BASE_ID + (rule.id.hashCode() and 0xfff), title, "$body\n$hint", rule, OP_BONUS, TopUp.options(rule.limitBytes))
    }

    fun limitWarning(context: Context, rule: UsageRule) {
        val ctx = AppLocaleManager.localizedContext(context)
        val title = ctx.getString(R.string.sx_notif_warn_title, rule.label)
        val used = UsageLimitEngine.usedNow(rule)
        val body = ctx.getString(
            R.string.sx_notif_warn_body,
            used.toTrafficString(),
            rule.limitBytes.toTrafficString(),
            periodPhrase(ctx, rule.period),
        )
        post(ctx, BASE_ID + 0x1000 + (rule.id.hashCode() and 0xfff), title, body, null, null, emptyList())
    }

    fun globalExceeded(context: Context, limit: GlobalLimit) {
        val ctx = AppLocaleManager.localizedContext(context)
        val title = ctx.getString(R.string.sx_notif_global_title)
        val body = ctx.getString(
            R.string.sx_notif_global_body,
            limit.limitBytes.toTrafficString(),
            periodPhrase(ctx, limit.period),
        )
        val hint = ctx.getString(R.string.sx_notif_topup_hint)
        post(ctx, BASE_ID + 0x2000, title, "$body\n$hint", null, OP_GLOBAL_BONUS, TopUp.options(limit.limitBytes))
    }

    fun periodPhrase(ctx: Context, period: LimitPeriod): String = ctx.getString(
        when (period) {
            LimitPeriod.DAILY -> R.string.sx_period_today
            LimitPeriod.WEEKLY -> R.string.sx_period_this_week
            LimitPeriod.MONTHLY -> R.string.sx_period_this_month
        },
    )

    /** "+50 MB" / "+0.5 GB" in the user's language and digits. */
    fun topUpLabel(ctx: Context, bytes: Long): String {
        val nf = java.text.NumberFormat.getNumberInstance(ctx.resources.configuration.locales[0]).apply {
            maximumFractionDigits = 1
        }
        return if (TopUp.isGb(bytes)) ctx.getString(R.string.sx_topup_gb, nf.format(bytes.toDouble() / TopUp.GB))
        else ctx.getString(R.string.sx_topup_mb, nf.format(bytes / TopUp.MB))
    }

    private fun post(ctx: Context, id: Int, title: String, body: String, rule: UsageRule?, op: String?, amounts: List<Long>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel(ctx)
        val open = PendingIntent.getActivity(
            ctx, id,
            Intent(ctx, MainActivity::class.java).putExtra(EXTRA_OPEN_TAB, "usage")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif_satra)
            .setColor(com.v2ray.ang.handler.NOTIF_ORANGE)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        if (op != null) {
            amounts.take(3).forEachIndexed { i, bytes ->
                val intent = Intent(ACTION).setPackage(ctx.packageName)
                    .putExtra(EXTRA_OP, op).putExtra(EXTRA_RULE, rule?.id)
                    .putExtra(EXTRA_BYTES, bytes)
                    .putExtra("nid", id)
                val pi = PendingIntent.getBroadcast(
                    ctx, id * 4 + i, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                builder.addAction(0, topUpLabel(ctx, bytes), pi)
            }
        }
        try {
            NotificationManagerCompat.from(ctx).notify(id, builder.build())
        } catch (e: SecurityException) {
            LogUtil.e(AppConfig.TAG, "UsageNotifier: notify failed", e)
        }
    }

    private fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, ctx.getString(R.string.sx_notif_channel), NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    /**
     * Handles the top-up actions. A rule's block lifts on the daemon's next tick (about a second:
     * it sees the cap is above usage and reloads the core in place). The global limit stopped the
     * VPN, so a global top-up starts it again.
     */
    class ActionReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ACTION) return
            val bytes = intent.getLongExtra(EXTRA_BYTES, 0L).takeIf { it > 0L } ?: return
            when (intent.getStringExtra(EXTRA_OP)) {
                OP_BONUS -> intent.getStringExtra(EXTRA_RULE)?.let { UsageStore.addBonus(it, bytes) }
                OP_GLOBAL_BONUS -> {
                    UsageStore.putLong("g:bonus", UsageStore.getLong("g:bonus", 0L) + bytes)
                    if (!UsageStore.live().running) {
                        runCatching { com.v2ray.ang.core.LauncherManager.startService(context) }
                            .onFailure { LogUtil.e(AppConfig.TAG, "UsageNotifier: restart after top-up failed", it) }
                    }
                }
            }
            NotificationManagerCompat.from(context).cancel(intent.getIntExtra("nid", 0))
        }
    }
}
