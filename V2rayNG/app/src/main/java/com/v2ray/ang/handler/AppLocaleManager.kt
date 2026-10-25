package com.v2ray.ang.handler

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.LocaleManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.os.ConfigurationCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.enums.Language

/**
 * Keeps the legacy in-app language preference synchronized with Android's per-app locale APIs.
 */
object AppLocaleManager {

    /**
     * Migrates the existing MMKV language preference and restores it before the first activity.
     */
    fun initialize(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Api33.prepareMigration(context)
        } else {
            initializeCompat(context)
        }
    }

    /**
     * Completes the one-time handoff after AppCompat has attached the activity context.
     */
    fun onActivityCreated(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Api33.completeMigration(context)
        } else {
            syncLegacyPreference(AppCompatDelegate.getApplicationLocales().get(0)?.toLanguageTag())
        }
    }

    /**
     * Applies a language selected in the app and lets AppCompat recreate the current activity.
     */
    fun setApplicationLanguage(languageCode: String) {
        val language = Language.fromCode(languageCode)
        persistLegacyPreference(language)
        AppCompatDelegate.setApplicationLocales(language.toLocaleList())
        refreshOutsideSurfaces()
    }

    /**
     * The language may also be changed from Android Settings (per-app language). On resume, pick
     * that up so the notification, tile, widget and shortcuts follow it too.
     */
    @Volatile private var lastSurfaceLanguage: String? = null

    fun syncFromSystem(activity: Context) {
        val shown = ConfigurationCompat.getLocales(activity.resources.configuration).get(0)?.language
        if (shown == lastSurfaceLanguage) return
        lastSurfaceLanguage = shown
        val tag = AppCompatDelegate.getApplicationLocales().get(0)?.toLanguageTag()
        val system = Language.fromLanguageTag(tag)
        if (system != storedLanguage()) persistLegacyPreference(system)
        Thread { runCatching { refreshOutsideSurfaces() } }.apply { priority = Thread.MIN_PRIORITY }.start()
    }

    /** Notification, Quick Settings tile, widget and launcher shortcuts follow the app language. */
    private fun refreshOutsideSurfaces() {
        val app = com.v2ray.ang.AngApplication.application
        val ctx = currentContext(app)
        com.v2ray.ang.core.ToggleSurfaces.publishShortcuts(ctx)
        com.v2ray.ang.core.ToggleSurfaces.refresh(app)
        com.v2ray.ang.helper.MessageHelper.sendMsg2Service(app, AppConfig.MSG_REFRESH_LANGUAGE, "")
    }

    /**
     * Returns a context suitable for resource access outside AppCompatActivity.
     */
    fun localizedContext(context: Context): Context {
        val localizedContext = ContextCompat.getContextForLanguage(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU || localizedContext !== context) {
            return localizedContext
        }

        // During the one-time upgrade from the old picker, AppCompat has not written its locale
        // record yet. Use the still-current MMKV value for non-activity contexts on that launch.
        val language = storedLanguage()
        language.locale ?: return context
        val configuration = Configuration(context.resources.configuration)
        ConfigurationCompat.setLocales(configuration, language.toLocaleList())
        return context.createConfigurationContext(configuration)
    }

    /**
     * A context localized from the stored in-app language, read fresh on every call. Long-lived
     * daemon-process services keep the locale they were attached with, so anything they render
     * later (notification text/actions, tile labels, shortcuts) must use this instead.
     */
    fun currentContext(context: Context): Context {
        val language = storedLanguage()
        val locale = language.locale ?: return context
        val configuration = Configuration(context.resources.configuration)
        if (ConfigurationCompat.getLocales(configuration).get(0) == locale) return context
        ConfigurationCompat.setLocales(configuration, language.toLocaleList())
        return context.createConfigurationContext(configuration)
    }

    private fun initializeCompat(context: Context) {
        val storedLocales = LocaleManagerCompat.getApplicationLocales(context)
        if (storedLocales.isEmpty) {
            val legacyLanguage = storedLanguage()
            if (legacyLanguage != Language.AUTO) {
                AppCompatDelegate.setApplicationLocales(legacyLanguage.toLocaleList())
            }
        } else {
            syncLegacyPreference(storedLocales.get(0)?.toLanguageTag())
        }
    }

    private fun storedLanguage(): Language = Language.fromCode(
        MmkvManager.decodeSettingsString(AppConfig.PREF_LANGUAGE) ?: Language.AUTO.code
    )

    private fun syncLegacyPreference(languageTag: String?) {
        persistLegacyPreference(Language.fromLanguageTag(languageTag))
    }

    private fun persistLegacyPreference(language: Language) {
        val current = MmkvManager.decodeSettingsString(AppConfig.PREF_LANGUAGE)
        if (current != language.code) {
            MmkvManager.encodeSettings(AppConfig.PREF_LANGUAGE, language.code)
            SettingsChangeManager.notifySettingChanged(AppConfig.PREF_LANGUAGE)
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private object Api33 {
        fun prepareMigration(context: Context) {
            if (MmkvManager.decodeSettingsBool(AppConfig.PREF_APP_LOCALE_MIGRATED, false)) {
                return
            }

            val localeManager = context.getSystemService(LocaleManager::class.java)
            val applicationLocales = localeManager.applicationLocales
            if (!applicationLocales.isEmpty) {
                syncLegacyPreference(applicationLocales.get(0)?.toLanguageTag())
                return
            }

            val legacyLanguage = storedLanguage()
            if (legacyLanguage != Language.AUTO) {
                // Make application and service contexts correct even before the first activity.
                localeManager.applicationLocales = android.os.LocaleList(
                    requireNotNull(legacyLanguage.locale)
                )
            }
        }

        fun completeMigration(context: Context) {
            val localeManager = context.getSystemService(LocaleManager::class.java)
            if (!MmkvManager.decodeSettingsBool(AppConfig.PREF_APP_LOCALE_MIGRATED, false)) {
                val frameworkLanguage = Language.fromLanguageTag(
                    localeManager.applicationLocales.get(0)?.toLanguageTag()
                )
                val language = frameworkLanguage.takeUnless { it == Language.AUTO }
                    ?: storedLanguage()

                // Android's migration guidance requires this one-time AppCompat handoff after
                // Activity.onCreate(), including on Android 13 and newer.
                AppCompatDelegate.setApplicationLocales(language.toLocaleList())
                persistLegacyPreference(language)
                MmkvManager.encodeSettings(AppConfig.PREF_APP_LOCALE_MIGRATED, true)
                return
            }

            syncLegacyPreference(localeManager.applicationLocales.get(0)?.toLanguageTag())
        }
    }
}
