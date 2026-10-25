package com.v2ray.ang.ui.compose

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.ui.compose.satra.SatraDarkPalette
import com.v2ray.ang.ui.compose.satra.SatraLightPalette
import com.v2ray.ang.ui.compose.satra.SatraTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Legacy colour aliases still referenced by the older editor screens. They all resolve to the
 * brand orange / neutral palette so those screens match the new design system.
 */
object SatraColors {
    val Ink = Color(0xFF09090B)
    val InkSoft = Color(0xFF151518)
    val Mist = Color(0xFFF2F2F7)
    val Teal = Color(0xFFFF6A1A)
    val TealDeep = Color(0xFFF25A00)
    val TealSoft = Color(0xFFFFB98A)
    val Green = Color(0xFF32D768)
    val GreenDeep = Color(0xFF1E9E4A)
    val Amber = Color(0xFFFF5E00)
    val AmberSoft = Color(0xFFFF8A3D)
    val AmberDeep = Color(0xFFE04F00)
    val Danger = Color(0xFFFF4F45)
    val Purple = Amber
    val PurpleDeep = AmberDeep
    val PurpleSoft = AmberSoft
}

val colorPing = Color(0xFF32D768)
val colorPingRed = Color(0xFFFF4F45)
val colorConfigType = SatraColors.Amber
val dividerColorLight = Color(0x1F3C3C43)
val dividerColorDark = Color(0x1FFFFFFF)

val toastNormalBgLight = Color(0xF21E1E22)
val toastNormalBgDark = Color(0xF22A2A30)
val toastSuccessBg = Color(0xF21E9E4A)
val toastErrorBg = Color(0xF2E5362C)
val toastInfoBg = Color(0xF21F7AE0)
val toastIconCircleBg = Color(0x33FFFFFF)
val toastTextColor = Color.White

fun satraBackgroundBrush(dark: Boolean): Brush =
    Brush.verticalGradient(
        listOf(
            if (dark) SatraDarkPalette.background else SatraLightPalette.background,
            if (dark) SatraDarkPalette.background else SatraLightPalette.background,
        ),
    )

object ThemeManager {
    private val _themeMode = MutableStateFlow(
        MmkvManager.decodeSettingsString(AppConfig.PREF_UI_MODE_NIGHT, "0") ?: "0"
    )
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _dynamicColorEnabled = MutableStateFlow(false)
    val dynamicColorEnabled: StateFlow<Boolean> = _dynamicColorEnabled.asStateFlow()

    fun setThemeMode(mode: String) {
        MmkvManager.encodeSettings(AppConfig.PREF_UI_MODE_NIGHT, mode)
        _themeMode.value = mode
    }

    /** Kept for settings compatibility; SatraNG always uses its brand palette. */
    fun setDynamicColorEnabled(enabled: Boolean) {
        MmkvManager.encodeSettings(AppConfig.PREF_DYNAMIC_COLOR, enabled)
    }

    fun refresh() {
        _themeMode.value =
            MmkvManager.decodeSettingsString(AppConfig.PREF_UI_MODE_NIGHT, "0") ?: "0"
    }
}

@Composable
fun resolveDarkTheme(): Boolean {
    val mode by ThemeManager.themeMode.collectAsState()
    return when (mode) {
        "1" -> false
        "2" -> true
        else -> isSystemInDarkTheme()
    }
}

val LocalDarkTheme = compositionLocalOf { false }

@Composable
fun AppTheme(
    darkTheme: Boolean = resolveDarkTheme(),
    content: @Composable () -> Unit
) {
    val snackbarController = rememberAppSnackbarController()

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity ?: return@SideEffect
            WindowCompat.getInsetsController(activity.window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalDarkTheme provides darkTheme,
        LocalAppSnackbar provides snackbarController
    ) {
        SatraTheme(dark = darkTheme) {
            Box(modifier = Modifier.fillMaxSize()) {
                AppSnackbarBridge(controller = snackbarController)
                content()
                AppSnackbarHost(hostState = snackbarController.hostState)
            }
        }
    }
}
