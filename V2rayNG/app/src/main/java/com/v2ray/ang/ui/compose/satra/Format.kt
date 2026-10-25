package com.v2ray.ang.ui.compose.satra

import com.v2ray.ang.extension.toSpeedString
import com.v2ray.ang.extension.toTrafficString
import java.util.Locale

/** Isolates a number+unit run so it reads left-to-right (e.g. "2.3 MB/s") inside RTL text. */
fun ltr(text: String): String =
    // Arabic decimal/thousands marks are bidi class AN and make mixed number+unit runs reorder
    // unpredictably; the ASCII marks keep the run intact (the digits themselves stay localized).
    "\u202A${text.replace('\u066B', '.').replace('\u066C', ',')}\u202C"

fun formatBytes(bytes: Long): String = ltr(bytes.coerceAtLeast(0L).toTrafficString())

fun formatSpeed(bytesPerSecond: Long): String = ltr(bytesPerSecond.coerceAtLeast(0L).toSpeedString())

/** HH:MM:SS (or MM:SS under an hour). */
fun formatClock(totalSeconds: Long): String {
    val s = totalSeconds.coerceAtLeast(0L)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return ltr(
        if (h > 0) String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, sec)
        else String.format(Locale.getDefault(), "%02d:%02d", m, sec),
    )
}

/** "2 h 05 m" style duration for charts. */
fun formatHoursMinutes(totalSeconds: Long, hourUnit: String, minuteUnit: String): String {
    val s = totalSeconds.coerceAtLeast(0L)
    val h = s / 3600
    val m = (s % 3600) / 60
    return when {
        h > 0 -> String.format(Locale.getDefault(), "%d %s %02d %s", h, hourUnit, m, minuteUnit)
        else -> String.format(Locale.getDefault(), "%d %s", m, minuteUnit)
    }
}

/** Current UI locale, observable so composables update when the language changes. */
@androidx.compose.runtime.Composable
fun currentLocale(): java.util.Locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
