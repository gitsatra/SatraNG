package com.v2ray.ang.ui.compose.satra

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Hand-authored outline icon set (24×24 grid, 2px round stroke) so every screen shares
 * one consistent, SF-Symbols-like visual language. Tint is applied by [androidx.compose.material3.Icon].
 */
object SatraIcons {
    private fun icon(name: String, vararg paths: String, filled: Boolean = false): ImageVector {
        val b = ImageVector.Builder(
            name = name, defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        )
        paths.forEach { d ->
            val nodes = PathParser().parsePathString(d).toNodes()
            if (filled) {
                b.addPath(pathData = nodes, fill = SolidColor(Color.Black))
            } else {
                b.addPath(
                    pathData = nodes,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.9f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }
        return b.build()
    }

    private const val CIRCLE = "M12 2a10 10 0 1 0 0 20a10 10 0 1 0 0-20z"

    val Home by lazy { icon("home", "M3 10.5L12 3l9 7.5V20a1.5 1.5 0 0 1-1.5 1.5H4.5A1.5 1.5 0 0 1 3 20z", "M9.5 21.5V13h5v8.5") }
    val Bolt by lazy { icon("bolt", "M13 2L4 14h7l-1 8 9-12h-7l1-8z") }
    val Servers by lazy { icon("servers", "M4 4h16a1 1 0 0 1 1 1v5a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1z", "M4 13h16a1 1 0 0 1 1 1v5a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1v-5a1 1 0 0 1 1-1z", "M7 7.5h.01M7 16.5h.01") }
    val Layers by lazy { icon("layers", "M12 3L3 8l9 5 9-5-9-5z", "M3 12.5l9 5 9-5", "M3 16.5l9 5 9-5") }
    val Chart by lazy { icon("chart", "M5 20V11", "M12 20V4", "M19 20v-6") }
    val Sliders by lazy { icon("sliders", "M4 21v-7M4 10V3M12 21v-9M12 8V3M20 21v-5M20 12V3", "M1 14h6M9 8h6M17 16h6") }
    val Gear by lazy { icon("gear", CIRCLE.replace("M12 2a10 10 0 1 0 0 20a10 10 0 1 0 0-20z", "M12 9a3 3 0 1 0 0 6a3 3 0 1 0 0-6z"), "M19.4 15a1.7 1.7 0 0 0 .3 1.9l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.9-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.9.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.9 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.9l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.9.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.9-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.9V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z") }
    val Plus by lazy { icon("plus", "M12 5v14M5 12h14") }
    val Minus by lazy { icon("minus", "M5 12h14") }
    val Close by lazy { icon("close", "M18 6L6 18M6 6l12 12") }
    val Check by lazy { icon("check", "M20 6L9 17l-5-5") }
    val ChevronRight by lazy { icon("chevronRight", "M9 18l6-6-6-6") }
    val ChevronLeft by lazy { icon("chevronLeft", "M15 18l-6-6 6-6") }
    val ChevronDown by lazy { icon("chevronDown", "M6 9l6 6 6-6") }
    val ChevronUp by lazy { icon("chevronUp", "M18 15l-6-6-6 6") }
    val Search by lazy { icon("search", "M11 3a8 8 0 1 0 0 16a8 8 0 1 0 0-16z", "M21 21l-4.3-4.3") }
    val Refresh by lazy { icon("refresh", "M21 12a9 9 0 0 1-15.5 6.3L3 16", "M3 12a9 9 0 0 1 15.5-6.3L21 8", "M21 3v5h-5", "M3 21v-5h5") }
    val Trash by lazy { icon("trash", "M3 6h18", "M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6", "M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2", "M10 11v6M14 11v6") }
    val Star by lazy { icon("star", "M12 2.5l2.9 6 6.6.9-4.8 4.6 1.2 6.5L12 17.4 6.1 20.5l1.2-6.5L2.5 9.4l6.6-.9z") }
    val StarFilled by lazy { icon("starFilled", "M12 2.5l2.9 6 6.6.9-4.8 4.6 1.2 6.5L12 17.4 6.1 20.5l1.2-6.5L2.5 9.4l6.6-.9z", filled = true) }
    val Share by lazy { icon("share", "M4 12v7a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-7", "M16 6l-4-4-4 4", "M12 2v13") }
    val Copy by lazy { icon("copy", "M10 9h10a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1H10a1 1 0 0 1-1-1V10a1 1 0 0 1 1-1z", "M5 15H4a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1h10a1 1 0 0 1 1 1v1") }
    val Edit by lazy { icon("edit", "M17 3a2.8 2.8 0 0 1 4 4L8 20l-5 1 1-5L17 3z") }
    val Qr by lazy { icon("qr", "M4 4h6v6H4zM14 4h6v6h-6zM4 14h6v6H4z", "M14 14h2v2h-2zM18 14h2M14 18h2M18 18h2v2h-2z") }
    val Scan by lazy { icon("scan", "M4 8V6a2 2 0 0 1 2-2h2M16 4h2a2 2 0 0 1 2 2v2M20 16v2a2 2 0 0 1-2 2h-2M8 20H6a2 2 0 0 1-2-2v-2", "M4 12h16") }
    val Clipboard by lazy { icon("clipboard", "M9 4h6a1 1 0 0 1 1 1v1a1 1 0 0 1-1 1H9a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1z", "M16 5h2a2 2 0 0 1 2 2v13a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2h2") }
    val Link by lazy { icon("link", "M10 13a5 5 0 0 0 7.5.5l3-3a5 5 0 0 0-7-7l-1.7 1.7", "M14 11a5 5 0 0 0-7.5-.5l-3 3a5 5 0 0 0 7 7l1.7-1.7") }
    val Globe by lazy { icon("globe", CIRCLE, "M2 12h20", "M12 2a15 15 0 0 1 4 10 15 15 0 0 1-4 10 15 15 0 0 1-4-10 15 15 0 0 1 4-10z") }
    val Power by lazy { icon("power", "M17.7 6.3a8 8 0 1 1-11.4 0", "M12 2.5v9") }
    val Shield by lazy { icon("shield", "M12 21.5s8-3.8 8-10V5l-8-3-8 3v6.5c0 6.2 8 10 8 10z") }
    val ShieldCheck by lazy { icon("shieldCheck", "M12 21.5s8-3.8 8-10V5l-8-3-8 3v6.5c0 6.2 8 10 8 10z", "M8.5 12l2.5 2.5 4.5-5") }
    val Clock by lazy { icon("clock", CIRCLE, "M12 6.5V12l3.5 2") }
    val ArrowUp by lazy { icon("arrowUp", "M12 19V5M5 12l7-7 7 7") }
    val ArrowDown by lazy { icon("arrowDown", "M12 5v14M19 12l-7 7-7-7") }
    val More by lazy { icon("more", "M5 12h.01M12 12h.01M19 12h.01") }
    val Info by lazy { icon("info", CIRCLE, "M12 16v-4.5M12 8h.01") }
    val Warning by lazy { icon("warning", "M12 3.5L2.5 20h19L12 3.5z", "M12 10v4.5M12 17.5h.01") }
    val Bell by lazy { icon("bell", "M18 8a6 6 0 0 0-12 0c0 7-3 9-3 9h18s-3-2-3-9z", "M13.7 21a2 2 0 0 1-3.4 0") }
    val Apps by lazy { icon("apps", "M4 4h6v6H4zM14 4h6v6h-6zM4 14h6v6H4zM14 14h6v6h-6z") }
    val Phone by lazy { icon("phone", "M7 2h10a2 2 0 0 1 2 2v16a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2z", "M12 18h.01") }
    val Ban by lazy { icon("ban", CIRCLE, "M5 5l14 14") }
    val Lock by lazy { icon("lock", "M6 11h12a1 1 0 0 1 1 1v8a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1v-8a1 1 0 0 1 1-1z", "M8 11V7a4 4 0 0 1 8 0v4") }
    val Sort by lazy { icon("sort", "M4 6h16M7 12h10M10 18h4") }
    val Download by lazy { icon("download", "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4", "M7 10l5 5 5-5", "M12 15V3") }
    val Upload by lazy { icon("upload", "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4", "M17 8l-5-5-5 5", "M12 3v12") }
    val Terminal by lazy { icon("terminal", "M4 4h16a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1z", "M7 9l3 3-3 3M13 15h4") }
    val Moon by lazy { icon("moon", "M21 12.8A9 9 0 1 1 11.2 3 7 7 0 0 0 21 12.8z") }
    val Route by lazy { icon("route", "M6 4a2 2 0 1 0 0 4a2 2 0 1 0 0-4z", "M18 16a2 2 0 1 0 0 4a2 2 0 1 0 0-4z", "M8 6h5.5a3.5 3.5 0 0 1 0 7h-3a3.5 3.5 0 0 0 0 7H16") }
    val Pulse by lazy { icon("pulse", "M2 12h4l3-8 4 16 3-8h6") }
    val Speed by lazy { icon("speed", "M12 14l4-4", "M3.3 18a9.5 9.5 0 1 1 17.4 0") }
    val Heart by lazy { icon("heart", "M20.8 5.6a5.5 5.5 0 0 0-7.8 0L12 6.7l-1-1.1a5.5 5.5 0 0 0-7.8 7.8l1 1L12 22l7.8-7.6 1-1a5.5 5.5 0 0 0 0-7.8z") }
    val Language by lazy { icon("language", "M5 8h8M9 5v3M7 14c3-1 5-4 6-6M6 8c1 3 4 6 7 7", "M13 20l4-9 4 9M14.5 17h5") }
    val Palette by lazy { icon("palette", "M12 3a9 9 0 1 0 0 18c1.1 0 1.5-.8 1.2-1.7-.4-1.1.3-2.3 1.5-2.3H17a4 4 0 0 0 4-4c0-5-4-10-9-10z", "M7.5 11h.01M10 7.5h.01M14.5 7.5h.01") }
    val Wifi by lazy { icon("wifi", "M2 9a15 15 0 0 1 20 0", "M5 12.5a10 10 0 0 1 14 0", "M8.5 16a5 5 0 0 1 7 0", "M12 19.5h.01") }
    val Flash by lazy { icon("flash", "M13 2L4 14h7l-1 8 9-12h-7l1-8z") }
    val Play by lazy { icon("play", "M7 4.5v15l12.5-7.5z") }
    val Stop by lazy { icon("stop", "M6 6h12v12H6z") }
    val Eye by lazy { icon("eye", "M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7S2 12 2 12z", "M12 9a3 3 0 1 0 0 6a3 3 0 1 0 0-6z") }
    val File by lazy { icon("file", "M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z", "M14 3v5h5") }
    val Tag by lazy { icon("tag", "M20.6 13.4l-7.2 7.2a2 2 0 0 1-2.8 0L2 12V2h10l8.6 8.6a2 2 0 0 1 0 2.8z", "M7 7h.01") }
}
