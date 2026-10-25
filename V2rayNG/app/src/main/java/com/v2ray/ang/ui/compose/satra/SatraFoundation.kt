package com.v2ray.ang.ui.compose.satra

import kotlinx.coroutines.launch
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.text.input.rememberTextFieldState
import kotlin.math.roundToInt

// ───────────────────────────── Surfaces ─────────────────────────────

@Composable
fun SatraBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxSize().background(Satra.colors.background)) { content() }
}

// ───────────────────────────── Large-title scaffold ─────────────────────────────

/**
 * iOS-style navigation: a large title that scrolls away while a compact bar fades in.
 * [actions] stay pinned to the trailing edge of the bar.
 */
@Composable
fun SatraLargeTitleScaffold(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    leading: @Composable (() -> Unit)? = null,
    state: LazyListState = rememberLazyListState(),
    bottomPadding: Dp = 24.dp,
    content: LazyListScope.() -> Unit,
) {
    val density = LocalDensity.current
    val statusTop = WindowInsets.statusBars.getTop(density)
    val barHeight = 56.dp
    val collapse by remember {
        derivedStateOf {
            if (state.firstVisibleItemIndex > 0) 1f
            else (state.firstVisibleItemScrollOffset / with(density) { 36.dp.toPx() }).coerceIn(0f, 1f)
        }
    }
    Box(modifier.fillMaxSize()) {
        LazyColumn(
            state = state,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = with(density) { statusTop.toDp() } + barHeight,
                bottom = bottomPadding,
            ),
        ) {
            item(key = "__large_title", contentType = "title") {
                Column(Modifier.padding(horizontal = SatraSpace.Gutter).padding(top = 2.dp, bottom = 10.dp)) {
                    Text(
                        title,
                        style = Satra.type.largeTitle,
                        color = Satra.colors.textPrimary,
                        modifier = Modifier.semantics { heading() },
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    if (subtitle != null) {
                        Text(
                            subtitle,
                            style = Satra.type.subhead,
                            color = Satra.colors.textSecondary,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
            content()
        }
        // Compact bar
        Box(
            Modifier
                .fillMaxWidth()
                .height(with(density) { statusTop.toDp() } + barHeight)
                .drawBehind {
                    drawRect(Color.Transparent)
                },
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .alpha(collapse)
                    .background(Satra.colors.background),
            )
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .alpha(collapse)
                    .background(Satra.colors.separator),
            )
            Row(
                Modifier
                    .statusBarsPadding()
                    .height(barHeight)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(SatraSpace.MinTouch)) { leading?.invoke() }
                Text(
                    title,
                    style = Satra.type.headline,
                    color = Satra.colors.textPrimary,
                    textAlign = TextAlign.Center,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .graphicsLayer { alpha = collapse; translationY = (1f - collapse) * 6f },
                )
                Row(
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                    content = actions,
                )
            }
        }
    }
}

// ───────────────────────────── Grouped inset lists ─────────────────────────────

class GroupScope {
    internal val items = mutableListOf<@Composable () -> Unit>()
    fun row(content: @Composable () -> Unit) { items += content }
}

/** Rounded grouped container ("inset grouped" list) with hairline separators. */
@Composable
fun InsetGroup(
    modifier: Modifier = Modifier,
    header: String? = null,
    footer: String? = null,
    content: GroupScope.() -> Unit,
) {
    val scope = GroupScope().apply(content)
    Column(modifier.padding(horizontal = SatraSpace.Gutter)) {
        if (header != null) {
            Text(
                header.uppercase(currentLocale()),
                style = Satra.type.footnote.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
                color = Satra.colors.textSecondary,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 7.dp, top = 6.dp)
                    .semantics { heading() },
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(SatraShapes.Large)
                .background(Satra.colors.surface),
        ) {
            scope.items.forEachIndexed { index, item ->
                item()
                if (index < scope.items.lastIndex) GroupSeparator()
            }
        }
        if (footer != null) {
            Text(
                footer,
                style = Satra.type.footnote,
                color = Satra.colors.textSecondary,
                modifier = Modifier.padding(horizontal = 16.dp).padding(top = 7.dp),
            )
        }
    }
}

@Composable
fun GroupSeparator(startInset: Dp = 16.dp) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = startInset)
            .height(0.5.dp)
            .background(Satra.colors.separator),
    )
}

/** Plain card (not a list) used for hero content. */
@Composable
fun SatraCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(16.dp),
    shape: Shape = SatraShapes.Large,
    containerColor: Color = Satra.colors.surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    val base = modifier
        .fillMaxWidth()
        .clip(shape)
        .background(containerColor)
    val clickable = if (onClick != null) base.satraClickable(role = Role.Button, pressedScale = 0.985f, onClick = onClick) else base
    Column(clickable.padding(padding), content = content)
}

@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = Satra.type.title2,
            color = Satra.colors.textPrimary,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (action != null && onAction != null) {
            Text(
                action,
                style = Satra.type.subhead.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                color = Satra.colors.accent,
                modifier = Modifier
                    .heightIn(min = SatraSpace.MinTouch)
                    .satraClickable(onClick = onAction)
                    .padding(horizontal = 4.dp)
                    .wrapCenter(),
            )
        }
    }
}

private fun Modifier.wrapCenter(): Modifier = this.then(Modifier.heightIn(min = 44.dp))

// ───────────────────────────── Rows ─────────────────────────────

/** Rounded square colour tile with a white glyph — the Settings-app leading icon. */
@Composable
fun IconTile(
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 30.dp,
) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.62f))
    }
}

@Composable
fun SatraRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconColor: Color = Satra.colors.accent,
    leading: @Composable (() -> Unit)? = null,
    value: String? = null,
    trailing: @Composable (() -> Unit)? = null,
    chevron: Boolean = false,
    destructive: Boolean = false,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val bg by animateColorAsState(
        if (pressed && onClick != null) Satra.colors.fill else Color.Transparent,
        animationSpec = tween(90), label = "rowBg",
    )
    val haptics = rememberSatraHaptics()
    val clickMod = if (onClick != null) {
        Modifier.combinedClickableCompat(source, enabled, onLongClick?.let { lc -> { haptics.heavy(); lc() } }) {
            haptics.tap(); onClick()
        }
    } else Modifier
    Row(
        modifier
            .fillMaxWidth()
            .background(bg)
            .then(clickMod)
            .heightIn(min = 52.dp)
            .padding(horizontal = 16.dp, vertical = 9.dp)
            .alpha(if (enabled) 1f else 0.45f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(12.dp))
        } else if (icon != null) {
            IconTile(icon, iconColor)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = Satra.type.body,
                color = if (destructive) Satra.colors.danger else Satra.colors.textPrimary,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = Satra.type.footnote,
                    color = Satra.colors.textSecondary,
                    maxLines = 3, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
        }
        if (value != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                value,
                style = Satra.type.callout,
                color = Satra.colors.textSecondary,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn2(180.dp),
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
        if (chevron) {
            Spacer(Modifier.width(6.dp))
            Icon(
                if (androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl)
                    SatraIcons.ChevronLeft else SatraIcons.ChevronRight,
                contentDescription = null,
                tint = Satra.colors.textTertiary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

private fun Modifier.widthIn2(max: Dp): Modifier = this.then(Modifier.widthIn(max = max))

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCompat(
    source: MutableInteractionSource,
    enabled: Boolean,
    onLongClick: (() -> Unit)?,
    onClick: () -> Unit,
): Modifier = this.then(
    Modifier.combinedClickable(
        interactionSource = source, indication = null, enabled = enabled,
        onLongClick = onLongClick, onClick = onClick, role = Role.Button,
    ),
)

@Composable
fun SatraSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconColor: Color = Satra.colors.accent,
    enabled: Boolean = true,
) {
    val haptics = rememberSatraHaptics()
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val bg by animateColorAsState(if (pressed) Satra.colors.fill else Color.Transparent, tween(90), label = "swBg")
    Row(
        modifier
            .fillMaxWidth()
            .background(bg)
            .toggleable(
                value = checked, enabled = enabled, role = Role.Switch,
                interactionSource = source, indication = null,
                onValueChange = { haptics.select(); onCheckedChange(it) },
            )
            .heightIn(min = 52.dp)
            .padding(horizontal = 16.dp, vertical = 9.dp)
            .alpha(if (enabled) 1f else 0.45f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            IconTile(icon, iconColor)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = Satra.type.body, color = Satra.colors.textPrimary)
            if (subtitle != null) {
                Text(
                    subtitle, style = Satra.type.footnote, color = Satra.colors.textSecondary,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        SatraSwitchVisual(checked)
    }
}

/** Visual-only switch: semantics come from the enclosing toggleable row. */
@Composable
fun SatraSwitchVisual(checked: Boolean, modifier: Modifier = Modifier) {
    val c = Satra.colors
    val track by animateColorAsState(if (checked) c.accent else c.fillStrong, tween(180), label = "swTrack")
    val offset by animateDpAsState(if (checked) 20.dp else 0.dp, SatraMotion.press(), label = "swThumb")
    val stretch by animateDpAsState(if (checked) 0.dp else 0.dp, SatraMotion.press(), label = "swStretch")
    Box(
        modifier
            .size(width = 52.dp, height = 32.dp)
            .clip(CircleShape)
            .background(track)
            .padding(2.dp)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .offset { IntOffset(offset.roundToPx(), 0) }
                .size(width = 28.dp + stretch, height = 28.dp)
                .shadow(3.dp, CircleShape, clip = false, ambientColor = Color.Black.copy(.3f), spotColor = Color.Black.copy(.3f))
                .background(Color.White, CircleShape),
        )
    }
}

/** Standalone switch for places that are not full rows. */
@Composable
fun SatraSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    enabled: Boolean = true,
) {
    val haptics = rememberSatraHaptics()
    Box(
        modifier
            .size(width = 60.dp, height = SatraSpace.MinTouch)
            .toggleable(
                value = checked, enabled = enabled, role = Role.Switch,
                onValueChange = { haptics.select(); onCheckedChange(it) },
            )
            .semantics { if (contentDescription != null) this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) { SatraSwitchVisual(checked) }
}

// ───────────────────────────── Buttons ─────────────────────────────

enum class SatraButtonStyle { Filled, Tinted, Plain, Danger }

@Composable
fun SatraButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: SatraButtonStyle = SatraButtonStyle.Filled,
    icon: ImageVector? = null,
    loading: Boolean = false,
    enabled: Boolean = true,
    compact: Boolean = false,
) {
    val c = Satra.colors
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val haptics = rememberSatraHaptics()
    val bg by animateColorAsState(
        when (style) {
            SatraButtonStyle.Filled -> if (pressed) c.accentPressed else c.accent
            SatraButtonStyle.Tinted -> if (pressed) c.accent.copy(alpha = .22f) else c.accentSoft
            SatraButtonStyle.Plain -> if (pressed) c.fill else Color.Transparent
            SatraButtonStyle.Danger -> if (pressed) c.danger.copy(alpha = .25f) else c.dangerSoft
        },
        tween(100), label = "btnBg",
    )
    val fg = when (style) {
        SatraButtonStyle.Filled -> c.onAccent
        SatraButtonStyle.Tinted, SatraButtonStyle.Plain -> c.accent
        SatraButtonStyle.Danger -> c.danger
    }
    Row(
        modifier
            .heightIn(min = if (compact) SatraSpace.MinTouch else 52.dp)
            .pressScale(source, 0.965f)
            .clip(if (compact) SatraShapes.Pill else RoundedCornerShape(16.dp))
            .background(bg)
            .selectable(
                selected = false, enabled = enabled && !loading, role = Role.Button,
                interactionSource = source, indication = null,
                onClick = { haptics.tap(); onClick() },
            )
            .padding(horizontal = if (compact) 18.dp else 22.dp)
            .alpha(if (enabled) 1f else 0.45f),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedVisibility(visible = loading, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
            SatraSpinner(size = 18.dp, color = fg, modifier = Modifier.padding(end = 8.dp))
        }
        if (icon != null && !loading) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text,
            style = Satra.type.headline.copy(fontSize = if (compact) 15.sp else 17.sp),
            color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun SatraIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Satra.colors.textPrimary,
    background: Color? = null,
    enabled: Boolean = true,
    size: Dp = 22.dp,
) {
    val source = remember { MutableInteractionSource() }
    val haptics = rememberSatraHaptics()
    Box(
        modifier
            .size(SatraSpace.MinTouch)
            .pressScale(source, 0.88f)
            .clip(CircleShape)
            .then(if (background != null) Modifier.background(background) else Modifier)
            .selectable(
                selected = false, enabled = enabled, role = Role.Button,
                interactionSource = source, indication = null,
                onClick = { haptics.tap(); onClick() },
            )
            .semantics { this.contentDescription = contentDescription }
            .alpha(if (enabled) 1f else 0.4f),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size))
    }
}

/**
 * Favourite toggle: hollow star → filled amber star with a springy pop and a little turn.
 * Respects reduced motion (instant change). 48dp touch target, small visual.
 */
@Composable
fun FavoriteStar(
    favorite: Boolean,
    onToggle: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
) {
    val c = Satra.colors
    val reduce = rememberReduceMotion()
    val haptics = rememberSatraHaptics()
    val source = remember { MutableInteractionSource() }
    val pop = remember { androidx.compose.animation.core.Animatable(1f) }
    val turn = remember { androidx.compose.animation.core.Animatable(0f) }
    var armed by remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(favorite) {
        if (!armed) { armed = true; return@LaunchedEffect }
        if (reduce) return@LaunchedEffect
        if (favorite) {
            kotlinx.coroutines.coroutineScope {
                launch {
                    pop.snapTo(0.6f)
                    pop.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.35f, stiffness = 500f))
                }
                launch {
                    turn.snapTo(-40f)
                    turn.animateTo(0f, androidx.compose.animation.core.spring(dampingRatio = 0.45f, stiffness = 300f))
                }
            }
        } else {
            pop.snapTo(0.85f)
            pop.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.6f, stiffness = 700f))
        }
    }
    val tint by animateColorAsState(if (favorite) c.warning else c.textTertiary, tween(if (reduce) 0 else 180), label = "starTint")
    Box(
        modifier
            .size(SatraSpace.MinTouch)
            .pressScale(source, 0.85f)
            .clip(CircleShape)
            .selectable(
                selected = favorite, role = Role.Checkbox,
                interactionSource = source, indication = null,
                onClick = { if (favorite) haptics.tap() else haptics.confirm(); onToggle() },
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (favorite) SatraIcons.StarFilled else SatraIcons.Star, contentDescription = null, tint = tint,
            modifier = Modifier
                .size(size)
                .graphicsLayer { scaleX = pop.value; scaleY = pop.value; rotationZ = turn.value },
        )
    }
}

@Composable
fun SatraChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    count: Int? = null,
) {
    val c = Satra.colors
    val bg by animateColorAsState(if (selected) c.accent else c.surface, tween(160), label = "chipBg")
    val fg by animateColorAsState(if (selected) c.onAccent else c.textPrimary, tween(160), label = "chipFg")
    val source = remember { MutableInteractionSource() }
    val haptics = rememberSatraHaptics()
    Row(
        modifier
            .heightIn(min = 36.dp)
            .pressScale(source, 0.94f)
            .clip(SatraShapes.Pill)
            .background(bg)
            .selectable(
                selected = selected, role = Role.Tab, interactionSource = source, indication = null,
                onClick = { haptics.select(); onClick() },
            )
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = Satra.type.subhead.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = fg, maxLines = 1)
        if (count != null) {
            Spacer(Modifier.width(6.dp))
            Text(
                count.toString(),
                style = Satra.type.caption,
                color = fg.copy(alpha = 0.7f),
            )
        }
    }
}

// ───────────────────────────── Segmented control ─────────────────────────────

@Composable
fun SatraSegmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Satra.colors
    val haptics = rememberSatraHaptics()
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(c.fill)
            .padding(3.dp),
    ) {
        val itemWidth = maxWidth / options.size
        val offset by animateDpAsState(itemWidth * selectedIndex, SatraMotion.soft(), label = "segThumb")
        Box(
            Modifier
                .offset { IntOffset(offset.roundToPx(), 0) }
                .width(itemWidth)
                .fillMaxSize()
                .shadow(2.dp, RoundedCornerShape(9.dp), clip = false, ambientColor = c.shadow, spotColor = c.shadow)
                .background(if (c.dark) c.fillStrong else Color.White, RoundedCornerShape(9.dp)),
        )
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { index, label ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .selectable(
                            selected = index == selectedIndex, role = Role.Tab,
                            interactionSource = remember { MutableInteractionSource() }, indication = null,
                            onClick = { if (index != selectedIndex) { haptics.tick(); onSelect(index) } },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = Satra.type.subhead.copy(
                            fontWeight = if (index == selectedIndex) androidx.compose.ui.text.font.FontWeight.SemiBold
                            else androidx.compose.ui.text.font.FontWeight.Medium,
                        ),
                        color = if (index == selectedIndex) c.textPrimary else c.textSecondary,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

// ───────────────────────────── Text field ─────────────────────────────

@Composable
fun SatraSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    clearLabel: String = "Clear",
) {
    val c = Satra.colors
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = Satra.type.body.copy(color = c.textPrimary),
        cursorBrush = Brush.linearGradient(listOf(c.accent, c.accent)),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, autoCorrectEnabled = false),
        keyboardActions = KeyboardActions(onSearch = { }),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp),
        decorationBox = { inner ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(c.fill)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(SatraIcons.Search, null, tint = c.textTertiary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(placeholder, style = Satra.type.body, color = c.textTertiary, maxLines = 1)
                    }
                    inner()
                }
                AnimatedVisibility(visible = value.isNotEmpty(), enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
                    Box(
                        Modifier
                            .size(SatraSpace.MinTouch)
                            .satraClickable(onClick = { onValueChange("") })
                            .semantics { contentDescription = clearLabel },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(SatraIcons.Close, null, tint = c.textTertiary, modifier = Modifier.size(16.dp))
                    }
                }
            }
        },
    )
}

// ───────────────────────────── Spinner / empty state ─────────────────────────────

@Composable
fun SatraSpinner(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    color: Color = Satra.colors.accent,
    strokeWidth: Dp = 2.5.dp,
) {
    val t = rememberInfiniteTransition(label = "spinner")
    val rot by t.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "rot")
    val sweep by t.animateFloat(
        80f, 250f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "sweep",
    )
    Canvas(modifier.size(size)) {
        val w = strokeWidth.toPx()
        drawArc(
            color = color, startAngle = rot, sweepAngle = sweep, useCenter = false,
            topLeft = Offset(w / 2, w / 2), size = Size(this.size.width - w, this.size.height - w),
            style = Stroke(w, cap = StrokeCap.Round),
        )
    }
}

@Composable
fun SatraEmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    secondaryText: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    val c = Satra.colors
    val reduce = rememberReduceMotion()
    val t = rememberInfiniteTransition(label = "float")
    val dy by t.animateFloat(
        -5f, 5f, infiniteRepeatable(tween(2600), RepeatMode.Reverse), label = "dy",
    )
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(96.dp)
                .graphicsLayer { translationY = if (reduce) 0f else dy }
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(c.accentSoft, c.fill))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = c.accent, modifier = Modifier.size(42.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(title, style = Satra.type.title2, color = c.textPrimary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(message, style = Satra.type.callout, color = c.textSecondary, textAlign = TextAlign.Center)
        if (actionText != null && onAction != null) {
            Spacer(Modifier.height(22.dp))
            SatraButton(actionText, onAction, compact = true)
        }
        if (secondaryText != null && onSecondary != null) {
            Spacer(Modifier.height(4.dp))
            SatraButton(secondaryText, onSecondary, style = SatraButtonStyle.Plain, compact = true)
        }
    }
}

// ───────────────────────────── Sheet, dialog, menu ─────────────────────────────

/** Screenshot tests render sheets inline (a real ModalBottomSheet lives in its own window). */
val LocalInlineSheets = androidx.compose.runtime.staticCompositionLocalOf { false }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SatraSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    skipPartiallyExpanded: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Satra.colors
    if (LocalInlineSheets.current) {
        Column(
            modifier
                .fillMaxWidth()
                .clip(SatraShapes.Sheet)
                .background(c.background)
                .padding(top = 10.dp, bottom = 16.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).padding(bottom = 6.dp).size(width = 38.dp, height = 5.dp).clip(SatraShapes.Pill).background(c.fillStrong))
            content()
        }
        return
    }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = skipPartiallyExpanded)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = c.background,
        scrimColor = c.scrim,
        shape = SatraShapes.Sheet,
        tonalElevation = 0.dp,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .size(width = 38.dp, height = 5.dp)
                    .clip(SatraShapes.Pill)
                    .background(c.fillStrong),
            )
        },
        modifier = modifier,
    ) {
        Column(Modifier.padding(bottom = 16.dp).windowInsetsPadding(WindowInsets.navigationBars), content = content)
    }
}

@Composable
fun SheetTitle(text: String, modifier: Modifier = Modifier, subtitle: String? = null) {
    Column(modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 12.dp, top = 2.dp)) {
        Text(text, style = Satra.type.title, color = Satra.colors.textPrimary, modifier = Modifier.semantics { heading() })
        if (subtitle != null) {
            Text(subtitle, style = Satra.type.callout, color = Satra.colors.textSecondary, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

/** iOS-style alert. Buttons are stacked with hairlines; destructive actions are red. */
@Composable
fun SatraAlert(
    title: String,
    message: String?,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dismissText: String? = null,
    destructive: Boolean = false,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val c = Satra.colors
    val reduce = rememberReduceMotion()
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val scale by animateFloatAsState(if (shown || reduce) 1f else 0.86f, SatraMotion.soft(), label = "alertScale")
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier
                .graphicsLayer { scaleX = scale; scaleY = scale; alpha = ((scale - 0.86f) / 0.14f).coerceIn(0f, 1f) }
                .widthIn3(300.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(c.surfaceRaised),
        ) {
            Column(Modifier.padding(horizontal = 22.dp).padding(top = 22.dp, bottom = 18.dp)) {
                Text(title, style = Satra.type.headline, color = c.textPrimary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                if (message != null) {
                    Text(
                        message, style = Satra.type.subhead, color = c.textSecondary,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    )
                }
                content?.let { Spacer(Modifier.height(12.dp)); it() }
            }
            GroupSeparator(0.dp)
            Row(Modifier.fillMaxWidth().height(52.dp)) {
                if (dismissText != null) {
                    AlertButton(dismissText, c.textPrimary, Modifier.weight(1f), bold = false, onClick = onDismiss)
                    Box(Modifier.width(0.5.dp).fillMaxSize().background(c.separator))
                }
                AlertButton(
                    confirmText, if (destructive) c.danger else c.accent, Modifier.weight(1f), bold = true, onClick = onConfirm,
                )
            }
        }
    }
}

private fun Modifier.widthIn3(max: Dp): Modifier = this.then(Modifier.widthIn(max = max))

@Composable
private fun AlertButton(text: String, color: Color, modifier: Modifier, bold: Boolean, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val haptics = rememberSatraHaptics()
    Box(
        modifier
            .fillMaxSize()
            .background(if (pressed) Satra.colors.fill else Color.Transparent)
            .selectable(
                selected = false, role = Role.Button, interactionSource = source, indication = null,
                onClick = { haptics.tap(); onClick() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = Satra.type.body.copy(
                fontWeight = if (bold) androidx.compose.ui.text.font.FontWeight.SemiBold
                else androidx.compose.ui.text.font.FontWeight.Normal,
            ),
            color = color, maxLines = 1,
        )
    }
}

@Composable
fun SatraMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        containerColor = Satra.colors.surfaceRaised,
        tonalElevation = 0.dp,
        shadowElevation = 16.dp,
        content = content,
    )
}

@Composable
fun SatraMenuItem(
    text: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    destructive: Boolean = false,
) {
    val color = if (destructive) Satra.colors.danger else Satra.colors.textPrimary
    val haptics = rememberSatraHaptics()
    DropdownMenuItem(
        text = { Text(text, style = Satra.type.body, color = color) },
        onClick = { haptics.tap(); onClick() },
        leadingIcon = icon?.let { { Icon(it, null, tint = color, modifier = Modifier.size(20.dp)) } },
    )
}

// ───────────────────────────── Tab bar ─────────────────────────────

@Immutable
data class SatraTab(val icon: ImageVector, val label: String)

@Composable
fun SatraTabBar(
    tabs: List<SatraTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Satra.colors
    val haptics = rememberSatraHaptics()
    Column(
        modifier
            .fillMaxWidth()
            .background(c.surface),
    ) {
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(c.separator))
        BoxWithConstraints(
            Modifier
                .windowInsetsPadding(WindowInsets.navigationBars)
                .fillMaxWidth()
                .height(62.dp),
        ) {
            val itemWidth = maxWidth / tabs.size
            val indicatorX by animateDpAsState(
                itemWidth * selectedIndex + (itemWidth - 28.dp) / 2, SatraMotion.soft(), label = "tabIndicator",
            )
            Box(
                Modifier
                    .offset { IntOffset(indicatorX.roundToPx(), 0) }
                    .size(width = 28.dp, height = 3.dp)
                    .clip(RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp))
                    .background(c.accent),
            )
            Row(Modifier.fillMaxSize()) {
                tabs.forEachIndexed { index, tab ->
                    val selected = index == selectedIndex
                    val tint by animateColorAsState(
                        if (selected) c.accent else c.textTertiary, tween(180), label = "tabTint",
                    )
                    val pop = rememberPop(if (selected) index else -1, from = 0.78f)
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .selectable(
                                selected = selected, role = Role.Tab,
                                interactionSource = remember { MutableInteractionSource() }, indication = null,
                                onClick = { if (!selected) { haptics.select(); onSelect(index) } },
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            tab.icon, null, tint = tint,
                            modifier = Modifier.size(25.dp).graphicsLayer { scaleX = pop; scaleY = pop },
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            tab.label,
                            style = Satra.type.caption.copy(fontSize = 11.sp),
                            color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

// ───────────────────────────── Misc ─────────────────────────────

@Composable
fun SatraBadge(text: String, color: Color, background: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        style = Satra.type.caption,
        color = color,
        maxLines = 1,
        modifier = modifier
            .clip(SatraShapes.Pill)
            .background(background)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** Dismisses the IME / clears focus when tapping outside inputs. */
fun Modifier.tapToClearFocus(onTap: () -> Unit): Modifier =
    this.pointerInput(Unit) { detectTapGestures { onTap() } }
