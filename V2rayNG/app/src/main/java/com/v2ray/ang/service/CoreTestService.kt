package com.v2ray.ang.service

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.core.CoreNativeManager
import com.v2ray.ang.dto.RealPingEvent
import com.v2ray.ang.dto.RealPingResult
import com.v2ray.ang.dto.TestServiceMessage
import com.v2ray.ang.enums.NotificationChannelType
import com.v2ray.ang.extension.serializable
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.AppLocaleManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.helper.MessageHelper
import com.v2ray.ang.helper.NotificationHelper
import com.v2ray.ang.util.LogUtil
import java.util.concurrent.ConcurrentHashMap

class CoreTestService : Service() {

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(newBase?.let(AppLocaleManager::localizedContext))
    }

    // manage active batch workers so each batch is independent and cancellable
    private val activeWorkers = ConcurrentHashMap<RealPingWorkerService, String>()
    @Volatile private var locator: ExitGeoLocator? = null
    @Volatile private var speedJob: java.util.concurrent.Future<*>? = null
    private val speedRunner = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "speed-test").apply { isDaemon = true } }
    private val cancelAction by lazy {
        val intent = Intent(this, CoreTestService::class.java).putExtra(
            "content",
            TestServiceMessage(AppConfig.MSG_MEASURE_CONFIG_CANCEL)
        )
        val pendingIntent = PendingIntent.getService(
            this,
            NotificationChannelType.CORE_TEST.notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        NotificationCompat.Action.Builder(
            R.drawable.ic_stop_24dp,
            getString(R.string.action_cancel),
            pendingIntent
        ).build()
    }

    /**
     * Initializes the V2Ray environment.
     */
    override fun onCreate() {
        super.onCreate()
        CoreNativeManager.initCoreEnv(this)
    }

    /**
     * Binds the service.
     * @param intent The intent.
     * @return The binder.
     */
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    /**
     * Cleans up resources when the service is destroyed.
     */
    override fun onDestroy() {
        LogUtil.i(AppConfig.TAG, "CoreTestService is being destroyed, cancelling ${activeWorkers.size} active workers")
        cancelWorkers()
        locator?.cancel()
        locator = null
        NotificationHelper.stopForeground(this)
        super.onDestroy()
    }

    /**
     * Handles the start command for the service.
     * @param intent The intent.
     * @param flags The flags.
     * @param startId The start ID.
     * @return The start mode.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        NotificationHelper.startForeground(
            this,
            NotificationChannelType.CORE_TEST,
            getString(R.string.app_name),
            getString(R.string.title_real_ping_all_server),
            cancelAction
        )
        val message = intent?.serializable<TestServiceMessage>("content")
        if (message == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        when (message.key) {
            AppConfig.MSG_MEASURE_CONFIG_START -> handleMeasureStart(
                message, startId, intent.getStringExtra(MessageHelper.EXTRA_REQUEST_ID).orEmpty()
            )
            AppConfig.MSG_MEASURE_CONFIG_CANCEL -> handleMeasureCancel()
            AppConfig.MSG_LOCATE_START -> handleLocateStart(message)
            AppConfig.MSG_SPEED_START -> handleSpeedStart(message)
            AppConfig.MSG_SPEED_CANCEL -> {
                speedJob?.cancel(true)
                speedJob = null
                MessageHelper.sendMsg2UI(this, AppConfig.MSG_SPEED_FINISH, "")
                stopIfIdle()
            }
            AppConfig.MSG_LOCATE_CANCEL -> {
                locator?.cancel()
                locator = null
                stopIfIdle()
            }
            else -> {
                NotificationHelper.stopForeground(this); stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    private fun handleLocateStart(message: TestServiceMessage) {
        locator?.cancel()
        if (message.serverGuids.isEmpty()) {
            locator = null
            stopIfIdle()
            return
        }
        NotificationHelper.updateNotification(
            channelType = NotificationChannelType.CORE_TEST,
            context = this,
            title = getString(R.string.app_name),
            content = getString(R.string.sx_locating_servers),
        )
        lateinit var created: ExitGeoLocator
        created = ExitGeoLocator(
            context = this,
            guids = message.serverGuids,
            onResult = { guid, ok ->
                MessageHelper.sendMsg2UI(this, AppConfig.MSG_LOCATE_RESULT, RealPingResult(guid, if (ok) 1L else -1L))
            },
            onFinish = {
                MessageHelper.sendMsg2UI(this, AppConfig.MSG_LOCATE_FINISH, "")
                if (locator === created) locator = null
                stopIfIdle()
            },
        )
        locator = created
        created.start()
    }

    /** Download speed tests, strictly one config at a time (they would skew each other). */
    private fun handleSpeedStart(message: TestServiceMessage) {
        speedJob?.cancel(true)
        val guids = message.serverGuids.distinct().take(SPEED_MAX)
        if (guids.isEmpty()) {
            speedJob = null
            stopIfIdle()
            return
        }
        NotificationHelper.updateNotification(
            channelType = NotificationChannelType.CORE_TEST,
            context = this,
            title = getString(R.string.app_name),
            content = getString(R.string.sx_speed_testing),
        )
        lateinit var self: java.util.concurrent.Future<*>
        self = speedRunner.submit {
            for (guid in guids) {
                if (Thread.currentThread().isInterrupted) break
                val bps = com.v2ray.ang.handler.ExitIpProbe.speed(this, guid) { live, frac ->
                    MessageHelper.sendMsg2UI(this, AppConfig.MSG_SPEED_PROGRESS, "$guid|$live|${(frac * 1000).toInt()}")
                }
                if (Thread.currentThread().isInterrupted) break
                MessageHelper.sendMsg2UI(this, AppConfig.MSG_SPEED_RESULT, RealPingResult(guid, bps))
            }
            MessageHelper.sendMsg2UI(this, AppConfig.MSG_SPEED_FINISH, "")
            if (speedJob === self) speedJob = null
            stopIfIdle()
        }
        speedJob = self
    }

    private fun stopIfIdle() {
        if (activeWorkers.isEmpty() && locator == null && speedJob == null) {
            NotificationHelper.stopForeground(this)
            stopSelf()
        }
    }

    private fun handleMeasureStart(message: TestServiceMessage, startId: Int, requestId: String) {
        LogUtil.i(AppConfig.TAG, "CoreTestService starting worker   subscription ${message.subscriptionId}")

        val guidsList = when {
            message.serverGuids.isNotEmpty() -> message.serverGuids
            message.subscriptionId.isNotEmpty() -> MmkvManager.decodeServerList(message.subscriptionId)
            else -> MmkvManager.decodeAllServerList()
        }

        if (guidsList.isNotEmpty()) {
            lateinit var worker: RealPingWorkerService
            worker = RealPingWorkerService(
                context = this,
                guids = guidsList,
                onlyTcp = message.onlyTcp,
                onEvent = { event ->
                    if (activeWorkers.containsKey(worker)) {
                        handleWorkerEvent(event, message, requestId) { activeWorkers.remove(worker) }
                    }
                }
            )
            activeWorkers[worker] = requestId
            worker.start()
        } else {
            MessageHelper.sendMsg2UI(this, AppConfig.MSG_MEASURE_CONFIG_CANCEL, "", requestId)
            NotificationHelper.stopForeground(this)
            stopSelf(startId)
        }
    }

    private fun handleWorkerEvent(event: RealPingEvent, message: TestServiceMessage, requestId: String, onWorkerDone: () -> Unit) {
        when (event) {
            is RealPingEvent.Progress -> {
                NotificationHelper.updateNotification(
                    channelType = NotificationChannelType.CORE_TEST,
                    context = this,
                    title = getString(R.string.app_name),
                    content = getString(R.string.connection_running_task_left, event.text)
                )
                MessageHelper.sendMsg2UI(this, AppConfig.MSG_MEASURE_CONFIG_NOTIFY, event.text, requestId)
            }

            is RealPingEvent.Result -> {
                MmkvManager.encodeServerTestDelayMillis(event.guid, event.delayMillis)
                MessageHelper.sendMsg2UI(
                    this,
                    AppConfig.MSG_MEASURE_CONFIG_SUCCESS,
                    RealPingResult(event.guid, event.delayMillis),
                    requestId,
                )
            }

            is RealPingEvent.Finish -> {
                if (message.subscriptionId.isNotEmpty()) {
                    if (MmkvManager.decodeSettingsBool(AppConfig.PREF_AUTO_REMOVE_INVALID_AFTER_TEST, false)) {
                        AngConfigManager.removeInvalidServer(message.subscriptionId)
                    }

                    if (MmkvManager.decodeSettingsBool(AppConfig.PREF_AUTO_SORT_AFTER_TEST, false)) {
                        AngConfigManager.sortByTestResultsForSub(message.subscriptionId)
                    }
                }

                MessageHelper.sendMsg2UI(this, AppConfig.MSG_MEASURE_CONFIG_FINISH, event.status, requestId)
                onWorkerDone()
                stopIfIdle()
            }
        }
    }

    private fun handleMeasureCancel() {
        LogUtil.i(AppConfig.TAG, "CoreTestService received cancel message, cancelling ${activeWorkers.size} active workers")
        cancelWorkers()
        // A background exit-country lookup may still be running; it stops the service when done.
        stopIfIdle()
    }

    private fun cancelWorkers() {
        // Reply for each cancelled request, never for a newer UI request that has not started here.
        activeWorkers.entries.toList().forEach { (worker, requestId) ->
            if (activeWorkers.remove(worker, requestId)) {
                worker.cancel()
                MessageHelper.sendMsg2UI(this, AppConfig.MSG_MEASURE_CONFIG_CANCEL, "", requestId)
            }
        }
    }

    companion object {
        /** Bounded: a bulk test never downloads more than this many configs' worth. */
        const val SPEED_MAX = 12
    }
}
