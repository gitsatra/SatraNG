package com.v2ray.ang

import android.app.Application
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.work.Configuration
import androidx.work.WorkManager
import com.v2ray.ang.AppConfig.ANG_PACKAGE
import com.v2ray.ang.handler.AppLocaleManager
import com.v2ray.ang.handler.HybridUsageTracker
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.ui.compose.ThemeManager

class AngApplication : Application() {
    companion object {
        lateinit var application: AngApplication
    }

    /**
     * Attaches the base context to the application.
     * @param base The base context.
     */
    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base?.let(ContextCompat::getContextForLanguage))
        application = this
    }

    private val workManagerConfiguration: Configuration = Configuration.Builder()
        .setDefaultProcessName("${ANG_PACKAGE}:bg")
        .build()

    /**
     * Initializes the application.
     */
    override fun onCreate() {
        super.onCreate()
        // Go runtime (Xray) reads these when libgojni loads: a soft heap cap keeps RSS flat on
        // long sessions without raising GC frequency at normal load (GOGC stays default).
        runCatching { android.system.Os.setenv("GOMEMLIMIT", "96MiB", false) }

        MmkvManager.initialize(this)

        AppLocaleManager.initialize(this)

        // Initialize WorkManager with the custom configuration
        WorkManager.initialize(this, workManagerConfiguration)

        // Ensure critical preference defaults are present in MMKV early
        SettingsManager.initApp(this)

        // Every process (:daemon, :tasks, :bg) runs onCreate; schedule periodic work once, from the UI process.
        if (isMainProcess()) HybridUsageTracker.schedulePeriodicSync(this)

        // Initialize theme state from MMKV
        ThemeManager.refresh()

        if (isMainProcess()) Thread { runCatching { com.v2ray.ang.core.ToggleSurfaces.publishShortcuts(com.v2ray.ang.handler.AppLocaleManager.currentContext(this)) } }.apply { priority = Thread.MIN_PRIORITY }.start()
    }

    private fun isMainProcess(): Boolean =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) getProcessName() == packageName else true
}
