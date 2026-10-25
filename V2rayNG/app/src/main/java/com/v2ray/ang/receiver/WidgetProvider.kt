package com.v2ray.ang.receiver

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.core.CoreServiceManager
import com.v2ray.ang.core.LauncherManager
import com.v2ray.ang.core.ToggleSurfaces

/**
 * Home-screen (and keyguard) widget: one tap connects/disconnects. Orange when off, green when on,
 * with the config name and its real exit flag.
 */
class WidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        render(context, appWidgetManager, appWidgetIds, CoreServiceManager.isRunning())
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val manager = AppWidgetManager.getInstance(context) ?: return
        val ids = manager.getAppWidgetIds(ComponentName(context, WidgetProvider::class.java))
        when (intent.action) {
            AppConfig.BROADCAST_ACTION_WIDGET_CLICK -> {
                if (CoreServiceManager.isRunning()) {
                    render(context, manager, ids, running = false, pending = true)
                    LauncherManager.stopService(context)
                } else {
                    render(context, manager, ids, running = true, pending = true)
                    if (!LauncherManager.startServiceFromToggle(context)) render(context, manager, ids, false)
                }
            }
            AppConfig.BROADCAST_ACTION_ACTIVITY -> when (intent.getIntExtra("key", 0)) {
                AppConfig.MSG_STATE_RUNNING, AppConfig.MSG_STATE_START_SUCCESS -> render(context, manager, ids, true)
                AppConfig.MSG_STATE_NOT_RUNNING, AppConfig.MSG_STATE_START_FAILURE, AppConfig.MSG_STATE_STOP_SUCCESS ->
                    render(context, manager, ids, false)
            }
        }
    }

    companion object {
        fun render(context: Context, manager: AppWidgetManager, ids: IntArray, running: Boolean, pending: Boolean = false) {
            if (ids.isEmpty()) return
            val views = RemoteViews(context.packageName, R.layout.widget_switch)
            // VPN consent missing: the tap opens the app (only an activity can show the prompt).
            val click = if (!running && ToggleSurfaces.needsVpnConsent(context)) {
                ToggleSurfaces.openAppPendingIntent(context, R.id.layout_switch)
            } else {
                PendingIntent.getBroadcast(
                    context, R.id.layout_switch,
                    Intent(context, WidgetProvider::class.java).setAction(AppConfig.BROADCAST_ACTION_WIDGET_CLICK),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            }
            views.setOnClickPendingIntent(R.id.layout_switch, click)
            views.setInt(R.id.layout_background, "setBackgroundResource",
                if (running) R.drawable.widget_bg_on else R.drawable.widget_bg_off)
            views.setInt(R.id.image_switch, "setImageResource", if (running) R.drawable.ic_satra_logo_full_green else R.drawable.ic_satra_logo_full_orange)
            val state = com.v2ray.ang.handler.AppLocaleManager.currentContext(context).getString(
                when {
                    pending && running -> R.string.sx_toggle_connecting
                    running -> R.string.sx_toggle_on
                    else -> R.string.sx_toggle_tap_on
                },
            )
            views.setTextViewText(R.id.text_state, state)
            views.setTextViewText(R.id.text_name, ToggleSurfaces.selectedName(context))
            val flag = ToggleSurfaces.selectedFlag()
            views.setTextViewText(R.id.text_flag, flag)
            views.setViewVisibility(R.id.text_flag, if (flag.isEmpty()) View.GONE else View.VISIBLE)
            views.setContentDescription(R.id.layout_switch, "${com.v2ray.ang.handler.AppLocaleManager.currentContext(context).getString(R.string.app_name)}: $state")
            for (id in ids) manager.updateAppWidget(id, views)
        }
    }
}
