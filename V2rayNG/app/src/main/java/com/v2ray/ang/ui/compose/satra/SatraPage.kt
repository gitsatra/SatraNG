package com.v2ray.ang.ui.compose.satra

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.ParentDataModifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R

/**
 * True inside a [GroupedColumn]: legacy rows then draw flat (no own card) and the column draws one
 * inset-grouped card behind each run of consecutive rows, with hairline separators between them.
 */
val LocalGroupedRows = compositionLocalOf { false }

private object GroupRowData : ParentDataModifier {
    override fun Density.modifyParentData(parentData: Any?): Any = this@GroupRowData
}

/** Marks a direct child of [GroupedColumn] as a grouped row. */
fun Modifier.groupRow(): Modifier = this.then(GroupRowData)

private class GroupGeometry {
    var runs: List<Pair<Int, Int>> = emptyList() // top,bottom px of each card
    var separators: List<Int> = emptyList()      // y px of hairlines
}

/**
 * Column that turns consecutive [groupRow] children into iOS-style inset grouped cards. Anything
 * else (headers, notes, buttons) is laid out full-width and breaks the group.
 */
@Composable
fun GroupedColumn(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val c = Satra.colors
    val geo = remember { GroupGeometry() }
    val density = LocalDensity.current
    val gutter = with(density) { SatraSpace.Gutter.roundToPx() }
    val radius = with(density) { 14.dp.toPx() }
    val sepInset = with(density) { 16.dp.toPx() }
    val hair = with(density) { 0.5.dp.toPx() }.coerceAtLeast(1f)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    CompositionLocalProvider(LocalGroupedRows provides true) {
        Layout(
            content = content,
            modifier = modifier
                .fillMaxWidth()
                .drawWithContent {
                    geo.runs.forEach { (t, b) ->
                        drawRoundRect(
                            c.surface, topLeft = Offset(gutter.toFloat(), t.toFloat()),
                            size = Size(size.width - 2 * gutter, (b - t).toFloat()),
                            cornerRadius = CornerRadius(radius, radius),
                        )
                    }
                    drawContent()
                    val w = size.width - 2 * gutter - sepInset
                    geo.separators.forEach { y ->
                        val x = if (rtl) gutter.toFloat() else gutter + sepInset
                        drawRect(c.separator, topLeft = Offset(x, y.toFloat()), size = Size(w, hair))
                    }
                },
        ) { measurables, constraints ->
            val full = constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
            val inner = full.copy(
                minWidth = (full.maxWidth - 2 * gutter).coerceAtLeast(0),
                maxWidth = (full.maxWidth - 2 * gutter).coerceAtLeast(0),
            )
            val isRow = measurables.map { it.parentData === GroupRowData }
            val placeables = measurables.mapIndexed { i, m -> m.measure(if (isRow[i]) inner else full) }
            val runs = mutableListOf<Pair<Int, Int>>()
            val seps = mutableListOf<Int>()
            var y = 0
            var runStart = -1
            placeables.forEachIndexed { i, p ->
                if (isRow[i]) {
                    if (runStart < 0) runStart = y else seps += y
                } else if (runStart >= 0) {
                    runs += runStart to y; runStart = -1
                }
                y += p.height
            }
            if (runStart >= 0) runs += runStart to y
            geo.runs = runs
            geo.separators = seps
            layout(full.maxWidth, y) {
                var yy = 0
                placeables.forEachIndexed { i, p ->
                    p.place(if (isRow[i]) gutter else 0, yy)
                    yy += p.height
                }
            }
        }
    }
}

/**
 * Scrollable secondary page: large title that collapses into a compact bar with a back chevron,
 * grouped content, optional loading bar. Used by every pushed screen so none keep the old look.
 */
@Composable
fun SatraScrollPage(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    isLoading: Boolean = false,
    scrollState: ScrollState = rememberScrollState(),
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val statusTop = with(density) { WindowInsets.statusBars.getTop(density).toDp() }
    val barHeight = 56.dp
    val collapse by remember {
        derivedStateOf { (scrollState.value / with(density) { 36.dp.toPx() }).coerceIn(0f, 1f) }
    }
    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .imePadding()
                    .verticalScroll(scrollState),
            ) {
                Spacer(Modifier.height(statusTop + barHeight))
                LargeTitleBlock(title, subtitle)
                GroupedColumn(content = content)
                Spacer(Modifier.height(28.dp))
                if (bottomBar == null) Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
            if (bottomBar != null) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Satra.colors.background)
                        .padding(horizontal = SatraSpace.Gutter, vertical = 10.dp),
                ) {
                    bottomBar()
                    Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
            }
        }
        CompactBar(title, collapse, statusTop + barHeight, isLoading, onBack, actions)
    }
}

@Composable
internal fun LargeTitleBlock(title: String, subtitle: String?) {
    Column(Modifier.padding(horizontal = SatraSpace.Gutter).padding(top = 2.dp, bottom = 10.dp)) {
        Text(
            title,
            style = Satra.type.largeTitle,
            color = Satra.colors.textPrimary,
            modifier = Modifier.semantics { heading() },
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        if (subtitle != null) {
            Text(subtitle, style = Satra.type.subhead, color = Satra.colors.textSecondary, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
internal fun CompactBar(
    title: String,
    collapse: Float,
    height: androidx.compose.ui.unit.Dp,
    isLoading: Boolean,
    onBack: (() -> Unit)?,
    actions: @Composable RowScope.() -> Unit,
) {
    Box(Modifier.fillMaxWidth().height(height)) {
        Box(Modifier.fillMaxSize().alpha(collapse).background(Satra.colors.background))
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(0.5.dp)
                .alpha(collapse).background(Satra.colors.separator),
        )
        Row(
            Modifier.statusBarsPadding().height(56.dp).fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(SatraSpace.MinTouch)) {
                if (onBack != null) SatraBackButton(onBack)
            }
            Text(
                title,
                style = Satra.type.headline,
                color = Satra.colors.textPrimary,
                textAlign = TextAlign.Center,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).graphicsLayer { alpha = collapse; translationY = (1f - collapse) * 6f },
            )
            Row(verticalAlignment = Alignment.CenterVertically, content = actions)
            if (onBack != null) Spacer(Modifier.width(0.dp))
        }
        AnimatedVisibility(
            visible = isLoading,
            enter = expandVertically(), exit = shrinkVertically(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = Satra.colors.accent,
                trackColor = Satra.colors.accent.copy(alpha = 0.15f),
            )
        }
    }
}

/** Mirrored back chevron for pushed pages. */
@Composable
fun SatraBackButton(onBack: () -> Unit) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    SatraIconButton(
        icon = if (rtl) SatraIcons.ChevronRight else SatraIcons.ChevronLeft,
        contentDescription = stringResource(R.string.acc_back),
        onClick = onBack,
    )
}

/** Section header for grouped pages (sits between cards). */
@Composable
fun GroupHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(currentLocale()),
        style = Satra.type.footnote.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
        color = Satra.colors.textSecondary,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = SatraSpace.Gutter + 16.dp, end = SatraSpace.Gutter + 16.dp, top = 22.dp, bottom = 7.dp)
            .semantics { heading() },
    )
}

/** Footnote under a card. */
@Composable
fun GroupFooter(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = Satra.type.footnote,
        color = Satra.colors.textSecondary,
        modifier = modifier.fillMaxWidth().padding(horizontal = SatraSpace.Gutter + 16.dp).padding(top = 7.dp, bottom = 4.dp),
    )
}

/** Vertical gap that also ends the current card. */
@Composable
fun GroupGap(height: androidx.compose.ui.unit.Dp = 22.dp) {
    Spacer(Modifier.fillMaxWidth().height(height))
}

/** Text action in a page bar (iOS "Save" / "Done"). */
@Composable
fun SatraBarTextAction(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    Text(
        text,
        style = Satra.type.body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
        color = if (enabled) Satra.colors.accent else Satra.colors.textTertiary,
        modifier = Modifier
            .heightIn(min = 44.dp)
            .satraClickable(enabled = enabled, role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
    )
}

/**
 * Lazy-list secondary page: large collapsing title with back chevron, optional always-visible
 * search field (iOS style) and a thin loading bar.
 */
@Composable
fun SatraLazyPage(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    isLoading: Boolean = false,
    state: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
    searchQuery: String? = null,
    onSearchChange: ((String) -> Unit)? = null,
    searchPlaceholder: String = "",
    actions: @Composable RowScope.() -> Unit = {},
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    val navBottom = with(LocalDensity.current) { WindowInsets.navigationBars.getBottom(this).toDp() }
    Box(modifier.fillMaxSize()) {
        SatraLargeTitleScaffold(
            title = title,
            subtitle = subtitle,
            actions = actions,
            leading = { SatraBackButton(onBack) },
            state = state,
            bottomPadding = navBottom + 28.dp,
        ) {
            if (searchQuery != null && onSearchChange != null) {
                item(key = "__search", contentType = "search") {
                    SatraSearchField(
                        value = searchQuery,
                        onValueChange = onSearchChange,
                        placeholder = searchPlaceholder,
                        modifier = Modifier.padding(horizontal = SatraSpace.Gutter).padding(bottom = 12.dp),
                    )
                }
            }
            content()
        }
        AnimatedVisibility(
            visible = isLoading,
            enter = expandVertically(), exit = shrinkVertically(),
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 56.dp),
        ) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = Satra.colors.accent,
                trackColor = Satra.colors.accent.copy(alpha = 0.15f),
            )
        }
    }
}

/**
 * One row of an inset-grouped card rendered as separate lazy items: rounds only the outer corners
 * of the first/last row and draws the hairline between rows.
 */
@Composable
fun LazyGroupRow(
    index: Int,
    count: Int,
    modifier: Modifier = Modifier,
    separatorInset: androidx.compose.ui.unit.Dp = 16.dp,
    content: @Composable () -> Unit,
) {
    val r = 14.dp
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(
        topStart = if (index == 0) r else 0.dp, topEnd = if (index == 0) r else 0.dp,
        bottomStart = if (index == count - 1) r else 0.dp, bottomEnd = if (index == count - 1) r else 0.dp,
    )
    Column(
        modifier
            .padding(horizontal = SatraSpace.Gutter)
            .fillMaxWidth()
            .androidx_clip(shape)
            .background(Satra.colors.surface),
    ) {
        if (index > 0) GroupSeparator(separatorInset)
        content()
    }
}

private fun Modifier.androidx_clip(shape: androidx.compose.ui.graphics.Shape): Modifier =
    this.then(Modifier.clip(shape))

/** Round selection mark that pops in with a spring when checked (respects reduced motion). */
@Composable
fun SatraCheckCircle(checked: Boolean, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 24.dp) {
    val c = Satra.colors
    val reduce = rememberReduceMotion()
    val fill by androidx.compose.animation.animateColorAsState(
        if (checked) c.accent else androidx.compose.ui.graphics.Color.Transparent,
        androidx.compose.animation.core.tween(if (reduce) 0 else 160), label = "chkFill",
    )
    val scale by androidx.compose.animation.core.animateFloatAsState(
        if (checked) 1f else 0.6f,
        if (reduce) androidx.compose.animation.core.snap() else androidx.compose.animation.core.spring(dampingRatio = 0.5f, stiffness = 700f),
        label = "chkScale",
    )
    Box(
        modifier
            .androidx_size(size)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(fill)
            .then(
                if (!checked) Modifier.androidx_border(c.textTertiary, androidx.compose.foundation.shape.CircleShape) else Modifier
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            androidx.compose.material3.Icon(
                SatraIcons.Check, null, tint = c.onAccent,
                modifier = Modifier.androidx_size(size * 0.62f).graphicsLayer { scaleX = scale; scaleY = scale },
            )
        }
    }
}

private fun Modifier.androidx_size(s: androidx.compose.ui.unit.Dp): Modifier = this.then(Modifier.size(s))
private fun Modifier.androidx_border(color: androidx.compose.ui.graphics.Color, shape: androidx.compose.ui.graphics.Shape): Modifier =
    this.then(Modifier.border(1.5.dp, color, shape))
