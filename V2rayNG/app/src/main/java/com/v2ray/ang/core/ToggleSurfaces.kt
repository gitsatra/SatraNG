package com.v2ray.ang.core

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.service.quicksettings.TileService
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.v2ray.ang.R
import com.v2ray.ang.handler.GeoIpResolver
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.receiver.WidgetProvider
import com.v2ray.ang.service.QSTileService
import com.v2ray.ang.ui.main.MainActivity
import com.v2ray.ang.ui.shortcut.ScSwitchActivity
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.AppConfig

/**
 * Shared bits for the on/off surfaces outside the app: Quick Settings tile, home-screen widget and
 * launcher shortcuts. All of them toggle through [LauncherManager]; when Android still needs the
 * user's VPN consent they open the app instead (only an activity can show that prompt).
 */
object ToggleSurfaces {
    const val ACTION_CONNECT = "com.satra.ng.action.CONNECT"
    const val ACTION_SMART_CONNECT = "com.satra.ng.action.SMART_CONNECT"

    /** Name of the selected config, for tile/widget labels. */
    fun selectedName(context: Context): String {
        val guid = MmkvManager.getSelectServer() ?: return context.getString(R.string.app_name)
        return MmkvManager.decodeServerConfig(guid)?.remarks?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.app_name)
    }

    /** Flag of the selected config's real exit country, or blank when unknown (never a guess). */
    fun selectedFlag(): String {
        val guid = MmkvManager.getSelectServer() ?: return ""
        val aff = MmkvManager.decodeServerAffiliationInfo(guid) ?: return ""
        return flagFor(aff.countryCode, aff.exitIpVerified)
    }

    internal fun flagFor(countryCode: String?, verified: Boolean): String =
        if (!verified || countryCode.isNullOrBlank()) "" else GeoIpResolver.flagEmoji(countryCode).takeIf { it != "🌐" }.orEmpty()

    fun needsVpnConsent(context: Context): Boolean =
        SettingsManager.isVpnMode() && !SettingsManager.isRootMode() && VpnService.prepare(context) != null

    fun openAppIntent(context: Context, action: String = ACTION_CONNECT): Intent =
        Intent(context, MainActivity::class.java)
            .setAction(action)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    fun openAppPendingIntent(context: Context, requestCode: Int, action: String = ACTION_CONNECT): PendingIntent =
        PendingIntent.getActivity(
            context, requestCode, openAppIntent(context, action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /** Redraw the widget(s) and ask the system to refresh the tile. Safe from any process. */
    fun refresh(context: Context, running: Boolean = CoreServiceManager.isRunning()) {
        try {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, WidgetProvider::class.java))
            if (ids.isNotEmpty()) WidgetProvider.render(context, manager, ids, running)
        } catch (e: Exception) {
            LogUtil.w(AppConfig.TAG, "ToggleSurfaces: widget refresh failed: ${e.javaClass.simpleName}")
        }
        try {
            TileService.requestListeningState(context, ComponentName(context, QSTileService::class.java))
        } catch (_: Exception) {
        }
    }

    /**
     * Launcher long-press shortcuts. Built at runtime so they always target this build's own
     * package (a static shortcuts.xml can only name one fixed package).
     */
    fun publishShortcuts(context: Context) {
        try {
            val toggle = ShortcutInfoCompat.Builder(context, "satra_toggle")
                .setShortLabel(context.getString(R.string.sx_shortcut_toggle))
                .setLongLabel(context.getString(R.string.sx_shortcut_toggle_long))
                .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_power))
                .setIntent(Intent(context, ScSwitchActivity::class.java).setAction(Intent.ACTION_VIEW))
                .setRank(0)
                .build()
            val smart = ShortcutInfoCompat.Builder(context, "satra_smart")
                .setShortLabel(context.getString(R.string.sx_shortcut_smart))
                .setLongLabel(context.getString(R.string.sx_shortcut_smart_long))
                .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_bolt))
                .setIntent(openAppIntent(context, ACTION_SMART_CONNECT))
                .setRank(1)
                .build()
            ShortcutManagerCompat.setDynamicShortcuts(context, listOf(toggle, smart))
        } catch (e: Exception) {
            LogUtil.w(AppConfig.TAG, "ToggleSurfaces: shortcuts failed: ${e.javaClass.simpleName}")
        }
    }
}
