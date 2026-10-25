package com.v2ray.ang.ui.home

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.satra.Satra
import com.v2ray.ang.ui.compose.satra.SatraBrand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Exit location for the Home backdrop: lat/lon from the exit-IP lookup, else the country centroid.
 * Compose state so the map appears as soon as the chip's lookup lands.
 */
internal object ExitLocation {
    val point = mutableStateOf<Pair<Float, Float>?>(null)
    @Volatile var sessionStartMs = 0L

    fun set(sessionStartMs: Long, lat: Double?, lon: Double?, country: String?) {
        val p = if (lat != null && lon != null && (lat != 0.0 || lon != 0.0)) lat.toFloat() to lon.toFloat()
        else centroid(country) ?: return
        this.sessionStartMs = sessionStartMs
        point.value = p
    }

    /** Natural Earth label points (lat, lon) per ISO-3166 alpha-2. */
    private const val CENTROIDS = "AD42.5,1.5;AE23.5,54.5;AF34.2,66.5;AG17.4,-61.8;AI18.2,-63.0;AL40.7,20.1;AM40.5,44.8;AO-12.2,18.0;AQ-79.8,35.9;AR-33.5,-64.2;AS-14.3,-170.7;AT47.5,14.1;AU-24.1,134.0;AW12.5,-70.0;AX60.2,19.9;AZ40.4,47.2;BA44.1,18.1;BB13.2,-59.6;BD24.2,89.7;BE50.8,4.8;BF12.7,-1.4;BG42.5,25.2;BH26.1,50.6;BI-3.3,29.9;BJ10.3,2.4;BL17.9,-62.8;BM32.3,-64.8;BN4.4,114.6;BO-16.7,-64.6;BR-12.1,-49.6;BS26.4,-77.1;BT27.5,90.0;BW-22.1,24.2;BY53.8,28.4;BZ17.2,-88.7;CA60.3,-101.9;CD-1.9,23.5;CF7.0,20.9;CG0.1,15.9;CH46.7,7.5;CI7.5,-5.6;CK-21.2,-159.8;CL-38.2,-72.3;CM4.6,12.5;CN32.5,106.3;TW23.7,120.9;CO3.4,-73.2;CR10.1,-84.1;CU21.3,-78.0;CV15.1,-23.6;CW12.1,-68.9;CY34.9,33.1;CZ49.9,15.4;DE51.0,9.7;DJ12.0,42.5;DK56.0,9.0;DM15.5,-61.3;DO19.1,-70.7;DZ27.4,2.8;EC-1.3,-78.2;EE58.7,25.9;EG26.2,29.4;EH24.0,-12.6;ER15.8,38.3;ES40.1,-3.5;ET8.0,39.1;FI63.3,27.3;FJ-17.8,178.0;FK-51.6,-58.7;FM6.9,158.2;FO62.2,-7.1;FR46.7,2.6;GA-0.4,11.8;GB54.4,-2.1;GD12.1,-61.7;GE41.9,43.7;GG49.5,-2.6;GH7.7,-1.0;GL74.3,-39.3;GM13.6,-15.0;GN10.6,-10.0;GQ2.3,9.0;GR39.5,21.7;GS-55.7,-31.1;GT15.0,-90.5;GU13.4,144.7;GW12.2,-14.5;GY5.1,-58.9;HK22.4,114.1;HM-53.1,73.5;HN14.8,-86.9;HR45.8,16.4;HT19.3,-72.2;HU47.1,19.4;ID-1.0,101.9;IE53.1,-7.8;IL30.9,34.8;IM54.2,-4.5;IN22.7,79.4;IO-6.2,71.3;IQ33.1,43.3;IR32.2,54.9;IS64.8,-18.7;IT44.7,11.1;JE49.2,-2.1;JM18.1,-77.3;JO30.8,36.4;JP36.1,138.4;KE0.5,37.9;KG41.7,74.5;KH12.6,104.5;KI1.8,-157.4;KM-11.7,43.3;KN17.3,-62.8;KP39.9,126.4;KR36.4,128.1;KW29.4,47.3;KY19.3,-81.2;KZ49.1,68.7;LA19.4,102.5;LB34.1,36.0;LC13.9,-61.0;LI47.1,9.6;LK7.6,80.7;LR6.4,-9.5;LS-29.5,28.2;LT55.1,24.1;LU49.7,6.1;LV57.1,25.5;LY26.6,18.0;MA31.7,-7.2;MC43.7,7.4;MD47.4,28.5;ME42.8,19.1;MF18.1,-63.0;MG-18.6,46.7;MH7.1,171.2;MK41.6,21.6;ML18.7,-2.0;MM21.6,95.8;MN46.0,104.2;MO22.1,113.6;MP15.2,145.7;MR19.6,-9.7;MS16.7,-62.2;MT35.9,14.4;MU-20.3,57.6;MV4.2,73.5;MW-13.4,33.6;MX23.9,-102.3;MY2.5,113.8;MZ-13.9,37.8;NA-20.6,17.1;NC-21.1,165.1;NE17.4,9.5;NF-29.0,168.0;NG9.4,7.5;NI12.7,-85.1;NL52.4,5.6;NO61.4,9.7;NP28.3,83.6;NR-0.5,166.9;NU-19.0,-169.9;NZ-39.8,172.8;OM22.1,57.3;PA8.7,-80.4;PE-13.0,-72.9;PF-17.6,-149.5;PG-5.7,143.9;PH11.2,122.5;PK29.3,68.5;PL52.0,19.5;PM47.0,-56.3;PN-24.4,-128.3;PR18.2,-66.5;PS32.0,35.3;PT39.6,-8.3;PW7.5,134.6;PY-21.7,-60.1;QA25.2,51.1;RO45.7,25.0;RS44.2,20.8;RU58.2,44.7;RW-1.9,30.1;SA23.8,44.7;SB-8.0,159.2;SC-4.7,55.5;SD16.3,29.3;SE65.9,19.0;SG1.4,103.8;SH-16.0,-5.7;SI46.1,14.9;SK48.7,19.0;SL8.6,-11.8;SM43.9,12.4;SN15.1,-14.8;SO3.6,45.2;SR4.1,-55.9;SS7.2,30.4;ST1.0,7.0;SV13.7,-88.9;SX18.0,-63.1;SY35.0,38.3;SZ-26.5,31.5;TC21.8,-71.8;TD15.1,18.6;TF-49.3,69.1;TG8.8,1.1;TH15.5,101.1;TJ38.2,72.6;TL-8.8,125.9;TM39.9,58.7;TN33.7,9.0;TO-21.2,-175.2;TR39.3,34.5;TT11.0,-60.9;TV-8.5,179.2;TZ-6.1,35.0;UA49.7,32.1;UG2.0,32.9;US39.5,-97.5;UY-33.0,-56.0;UZ41.7,64.0;VA41.9,12.5;VC13.1,-61.3;VE7.2,-64.6;VG18.4,-64.6;VI17.7,-64.8;VN21.7,105.4;VU-15.4,166.9;WF-14.3,-178.1;WS-13.6,-172.4;XK42.6,20.9;YE15.3,45.9;ZA-29.7,23.7;ZM-14.7,26.4;ZW-18.9,29.9"
    private val table: Map<String, Pair<Float, Float>> by lazy {
        CENTROIDS.split(';').associate { e ->
            val (la, lo) = e.substring(2).split(',')
            e.substring(0, 2) to (la.toFloat() to lo.toFloat())
        }
    }

    fun centroid(cc: String?): Pair<Float, Float>? = cc?.uppercase()?.let { table[it] }
}

/** Simplified Natural Earth 110m land, equirectangular 84N..58S (res/raw/world_land.bin, ~9 KB). */
private object WorldLand {
    const val LAT0 = 84f
    const val LAT1 = -58f
    @Volatile private var cached: Triple<Path, Float, Float>? = null

    fun load(ctx: Context): Triple<Path, Float, Float> = cached ?: synchronized(this) {
        cached ?: run {
            val b = ByteBuffer.wrap(ctx.resources.openRawResource(R.raw.world_land).use { it.readBytes() })
                .order(ByteOrder.LITTLE_ENDIAN)
            val w = b.short.toInt() and 0xFFFF
            val h = b.short.toInt() and 0xFFFF
            val n = b.short.toInt() and 0xFFFF
            val path = Path()
            repeat(n) {
                val c = b.short.toInt() and 0xFFFF
                for (i in 0 until c) {
                    val x = (b.short.toInt() and 0xFFFF).toFloat()
                    val y = (b.short.toInt() and 0xFFFF).toFloat()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
            }
            Triple(path, (w - 1).toFloat(), (h - 1).toFloat()).also { cached = it }
        }
    }
}

/**
 * Monochrome, very low-contrast world map behind Home with one orange dot at the exit location.
 * Static drawing: one ease-in when the location is found, no looping animation (battery).
 */
@Composable
internal fun WorldMapBackdrop(point: Pair<Float, Float>?, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val land by produceState<Triple<Path, Float, Float>?>(null) {
        value = withContext(Dispatchers.IO) { runCatching { WorldLand.load(ctx) }.getOrNull() }
    }
    val appear = remember { Animatable(0f) }
    val dot = remember { Animatable(0f) }
    val shown = point != null && land != null
    LaunchedEffect(shown) {
        if (shown) {
            appear.animateTo(1f, tween(1100, easing = FastOutSlowInEasing))
        } else {
            dot.snapTo(0f); appear.animateTo(0f, tween(400))
        }
    }
    LaunchedEffect(shown, point) {
        if (shown) { dot.snapTo(0f); dot.animateTo(1f, tween(900, delayMillis = 350, easing = FastOutSlowInEasing)) }
    }
    val c = Satra.colors
    val ink = if (c.dark) Color.White else Color.Black
    val landAlpha = if (c.dark) 0.055f else 0.045f
    val l = land ?: return
    val p = point ?: return
    Canvas(modifier) {
        val a = appear.value
        if (a <= 0f) return@Canvas
        val (path, mw, mh) = l
        val px = (p.second + 180f) / 360f * mw
        val py = (WorldLand.LAT0 - p.first.coerceIn(WorldLand.LAT1, WorldLand.LAT0)) / (WorldLand.LAT0 - WorldLand.LAT1) * mh
        // ~60% of the world across the screen; dot centred horizontally, in the upper band above the orb.
        val s = size.width * 1.65f / mw * (0.96f + 0.04f * a)
        val tx = (size.width / 2f - px * s).coerceIn(size.width - mw * s, 0f)
        val ty = size.height * 0.075f - py * s
        withTransform({ translate(tx, ty); scale(s, s, Offset.Zero) }) {
            drawPath(path, ink.copy(alpha = landAlpha * a))
        }
        val d = dot.value
        if (d > 0f) {
            val center = Offset(tx + px * s, ty + py * s)
            val r = 4.dp.toPx()
            drawCircle(SatraBrand.Orange.copy(alpha = 0.16f * d), r * (1f + 2.6f * d), center)
            drawCircle(SatraBrand.Orange.copy(alpha = 0.35f * d), r * (1f + 1.2f * d), center, style = Stroke(1.dp.toPx()))
            drawCircle(SatraBrand.Orange.copy(alpha = d), r * (0.6f + 0.4f * d), center)
        }
    }
}
