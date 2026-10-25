package com.v2ray.ang.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.core.CoreServiceManager
import com.v2ray.ang.core.LauncherManager
import com.v2ray.ang.core.ToggleSurfaces
import com.v2ray.ang.handler.AppLocaleManager
import com.v2ray.ang.helper.MessageHelper
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import java.lang.ref.SoftReference

class QSTileService : TileService() {

    private fun lc(): Context = AppLocaleManager.currentContext(this)

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(newBase?.let(AppLocaleManager::localizedContext))
    }

    /**
     * Sets the state of the tile.
     * @param state The state to set.
     */
    fun setState(state: Int) {
        val tile = qsTile ?: return
        val on = state == Tile.STATE_ACTIVE
        tile.icon = Icon.createWithResource(applicationContext, R.drawable.ic_notif_satra)
        tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        // Label = the config it connects to (or is connected through).
        val name = if (on) CoreServiceManager.getRunningServerName().ifBlank { ToggleSurfaces.selectedName(this) }
        else ToggleSurfaces.selectedName(this)
        tile.label = name
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            tile.subtitle = lc().getString(if (on) R.string.sx_toggle_on else R.string.sx_toggle_off)
        }
        tile.contentDescription = "${lc().getString(R.string.app_name)}: $name, ${lc().getString(if (on) R.string.sx_toggle_on else R.string.sx_toggle_off)}"
        tile.updateTile()
    }

    /**
     * Refer to the official documentation for [registerReceiver](https://developer.android.com/reference/androidx/core/content/ContextCompat#registerReceiver(android.content.Context,android.content.BroadcastReceiver,android.content.IntentFilter,int):
     * `registerReceiver(Context, BroadcastReceiver, IntentFilter, int)`.
     */
    override fun onStartListening() {
        super.onStartListening()

        if (CoreServiceManager.isRunning()) {
            setState(Tile.STATE_ACTIVE)
        } else {
            setState(Tile.STATE_INACTIVE)
        }
        mMsgReceive = ReceiveMessageHandler(this)
        val mFilter = IntentFilter(AppConfig.BROADCAST_ACTION_ACTIVITY)
        ContextCompat.registerReceiver(applicationContext, mMsgReceive, mFilter, Utils.receiverFlags())
        MessageHelper.sendMsg2Service(this, AppConfig.MSG_REGISTER_CLIENT, "")
    }

    /**
     * Called when the tile stops listening.
     */
    override fun onStopListening() {
        super.onStopListening()

        try {
            applicationContext.unregisterReceiver(mMsgReceive)
            mMsgReceive = null
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to unregister receiver", e)
        }

    }

    /**
     * Called when the tile is clicked.
     */
    override fun onClick() {
        super.onClick()
        // Toggling works from the lock screen without unlocking. Only the VPN consent prompt
        // needs the app, and that is the one case we ask to unlock.
        if (qsTile?.state == Tile.STATE_ACTIVE) {
            setStatePending(false)
            LauncherManager.stopService(this)
            return
        }
        if (ToggleSurfaces.needsVpnConsent(this)) {
            unlockAndRun { openApp() }
            return
        }
        setStatePending(true)
        if (!LauncherManager.startServiceFromToggle(this)) setState(Tile.STATE_INACTIVE)
    }

    /** Immediate feedback while the core starts/stops; the broadcast then sets the final state. */
    private fun setStatePending(on: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            tile.subtitle = lc().getString(if (on) R.string.sx_toggle_connecting else R.string.sx_toggle_off)
        }
        tile.updateTile()
    }

    @android.annotation.SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openApp() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 34) {
                startActivityAndCollapse(ToggleSurfaces.openAppPendingIntent(this, 41))
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(ToggleSurfaces.openAppIntent(this))
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "QSTileService: cannot open app", e)
        }
    }

    private var mMsgReceive: BroadcastReceiver? = null

    private class ReceiveMessageHandler(context: QSTileService) : BroadcastReceiver() {
        var mReference: SoftReference<QSTileService> = SoftReference(context)
        override fun onReceive(ctx: Context?, intent: Intent?) {
            val context = mReference.get()
            when (intent?.getIntExtra("key", 0)) {
                AppConfig.MSG_STATE_RUNNING -> {
                    context?.setState(Tile.STATE_ACTIVE)
                }

                AppConfig.MSG_STATE_NOT_RUNNING -> {
                    context?.setState(Tile.STATE_INACTIVE)
                }

                AppConfig.MSG_STATE_START_SUCCESS -> {
                    context?.setState(Tile.STATE_ACTIVE)
                }

                AppConfig.MSG_STATE_START_FAILURE -> {
                    context?.setState(Tile.STATE_INACTIVE)
                }

                AppConfig.MSG_STATE_STOP_SUCCESS -> {
                    context?.setState(Tile.STATE_INACTIVE)
                }
            }
        }
    }
}
