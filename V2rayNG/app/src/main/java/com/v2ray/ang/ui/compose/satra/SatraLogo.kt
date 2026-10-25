package com.v2ray.ang.ui.compose.satra

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R

/** The SatraNG shield mark on its dark squircle: orange when idle, green once connected. */
@Composable
fun SatraLogo(connected: Boolean, modifier: Modifier = Modifier, size: Dp = 28.dp) {
    Crossfade(connected, animationSpec = tween(450), label = "logoState", modifier = modifier) { on ->
        Image(
            painterResource(if (on) R.drawable.ic_satra_logo_full_green else R.drawable.ic_satra_logo_full_orange),
            contentDescription = null,
            modifier = Modifier.size(size),
        )
    }
}
