package com.v2ray.ang.ui.compose.satra

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role

/** Motion spec: every interaction is a spring; durations are only used for fades. */
object SatraMotion {
    const val FadeMs = 200
    const val EnterMs = 320

    /** Fast, slightly bouncy feedback for presses. */
    fun <T> press(): SpringSpec<T> = spring(dampingRatio = 0.62f, stiffness = 700f)

    /** Default layout / state spring. */
    fun <T> soft(): SpringSpec<T> = spring(dampingRatio = 0.82f, stiffness = 380f)

    /** Playful overshoot for success / selection. */
    fun <T> bouncy(): SpringSpec<T> = spring(dampingRatio = 0.5f, stiffness = 260f)

    /** Critically damped, no overshoot, used for charts and progress. */
    fun <T> smooth(): SpringSpec<T> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 120f)

    fun <T> fade(ms: Int = FadeMs): FiniteAnimationSpec<T> = tween(ms)
}

/** True when the user disabled system animations (Settings → Remove animations). */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) { context.isReduceMotionEnabled() }
}

fun Context.isReduceMotionEnabled(): Boolean = Settings.Global.getFloat(
    contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f,
) == 0f

/** Haptic vocabulary. Falls back to older constants on older Android versions. */
class SatraHaptics(private val view: View) {
    fun tap() = perform(HapticFeedbackConstants.VIRTUAL_KEY)
    fun tick() = perform(
        if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.SEGMENT_TICK
        else HapticFeedbackConstants.CLOCK_TICK,
    )
    fun select() = perform(HapticFeedbackConstants.CLOCK_TICK)
    fun heavy() = perform(HapticFeedbackConstants.LONG_PRESS)
    fun confirm() = perform(
        if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM
        else HapticFeedbackConstants.LONG_PRESS,
    )
    fun reject() = perform(
        if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT
        else HapticFeedbackConstants.LONG_PRESS,
    )

    private fun perform(constant: Int) {
        view.performHapticFeedback(constant)
    }
}

val LocalSatraHaptics = staticCompositionLocalOf<SatraHaptics?> { null }

@Composable
fun rememberSatraHaptics(): SatraHaptics {
    val view = LocalView.current
    return remember(view) { SatraHaptics(view) }
}

/** Scales its content down while pressed, springing back with a slight overshoot. */
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.96f,
): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val reduce = rememberReduceMotion()
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduce) pressedScale else 1f,
        animationSpec = SatraMotion.press(),
        label = "pressScale",
    )
    graphicsLayer { scaleX = scale; scaleY = scale }
}

/**
 * Standard Satra tap target: press-scale animation, tick haptic, no ripple (Apple-style),
 * optional long-click.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.satraClickable(
    enabled: Boolean = true,
    role: Role? = Role.Button,
    pressedScale: Float = 0.97f,
    haptic: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val haptics = rememberSatraHaptics()
    this
        .pressScale(source, pressedScale)
        .combinedClickable(
            interactionSource = source,
            indication = null,
            enabled = enabled,
            role = role,
            onLongClick = onLongClick?.let { lc ->
                {
                    if (haptic) haptics.heavy()
                    lc()
                }
            },
            onClick = {
                if (haptic) haptics.tap()
                onClick()
            },
        )
}

/** One-shot attention "pop" (scale overshoot) when [trigger] changes. */
@Composable
fun rememberPop(trigger: Any?, from: Float = 0.82f): Float {
    val reduce = rememberReduceMotion()
    val anim = remember { Animatable(1f) }
    androidx.compose.runtime.LaunchedEffect(trigger) {
        if (reduce) return@LaunchedEffect
        anim.snapTo(from)
        anim.animateTo(1f, SatraMotion.bouncy())
    }
    return anim.value
}
