package com.v2ray.ang.ui.compose.satra

import android.content.Context
import android.util.LruCache
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Filled single/multi-line text field with a small caption label — the app's one input style. */
@Composable
fun SatraTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    supporting: String? = null,
    isError: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    readOnly: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: @Composable (() -> Unit)? = null,
) {
    val c = Satra.colors
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val border by animateColorAsState(
        when {
            isError -> c.danger
            focused -> c.accent
            else -> androidx.compose.ui.graphics.Color.Transparent
        },
        tween(150), label = "fieldBorder",
    )
    Column(modifier.fillMaxWidth()) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            minLines = minLines,
            readOnly = readOnly,
            interactionSource = source,
            textStyle = Satra.type.body.copy(color = c.textPrimary),
            cursorBrush = Brush.linearGradient(listOf(c.accent, c.accent)),
            // Keyboards must not rewrite what's typed (e.g. SwiftKey turning "parham" into Persian script).
            keyboardOptions = keyboardOptions.copy(autoCorrectEnabled = false),
            visualTransformation = visualTransformation,
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(c.fill)
                        .border(1.5.dp, border, RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(label, style = Satra.type.caption, color = if (isError) c.danger else c.textSecondary)
                        Box {
                            if (value.isEmpty() && placeholder != null) {
                                Text(placeholder, style = Satra.type.body, color = c.textTertiary, maxLines = 1)
                            }
                            inner()
                        }
                    }
                    if (trailing != null) {
                        Spacer(Modifier.width(8.dp))
                        trailing()
                    }
                }
            },
        )
        if (supporting != null) {
            Text(
                supporting, style = Satra.type.caption,
                color = if (isError) c.danger else c.textSecondary,
                modifier = Modifier.padding(start = 6.dp, top = 4.dp),
            )
        }
    }
}

/** Decoded launcher icons, shared and size-bounded. */
object AppIconCache {
    private val cache = object : LruCache<String, ImageBitmap>(96) {}

    suspend fun load(context: Context, packageName: String): ImageBitmap? {
        cache.get(packageName)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val d = context.packageManager.getApplicationIcon(packageName)
                d.toBitmap(96, 96).asImageBitmap()
            }.getOrNull()
        }?.also { cache.put(packageName, it) }
    }
}

@Composable
fun AppIcon(packageName: String?, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, packageName) {
        value = if (packageName.isNullOrBlank()) null else AppIconCache.load(context, packageName)
    }
    val shape = RoundedCornerShape(size * 0.24f)
    val bmp = bitmap
    if (bmp != null) {
        Image(bmp, contentDescription = null, modifier = modifier.size(size).clip(shape))
    } else {
        Box(modifier.size(size).clip(shape).background(Satra.colors.fill), contentAlignment = Alignment.Center) {
            Icon(SatraIcons.Apps, null, tint = Satra.colors.textTertiary, modifier = Modifier.size(size * 0.5f))
        }
    }
}

/** Two-line section label used above form groups inside sheets. */
@Composable
fun FormLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text, style = Satra.type.footnote.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
        color = Satra.colors.textSecondary,
        modifier = modifier.padding(horizontal = 24.dp).padding(top = 14.dp, bottom = 6.dp),
    )
}

@Composable
fun ChipRow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}
