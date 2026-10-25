package com.v2ray.ang.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.text.font.FontWeight
import com.v2ray.ang.ui.compose.satra.Satra
import com.v2ray.ang.ui.compose.satra.SatraIcons
import com.v2ray.ang.ui.compose.satra.SatraShapes
import com.v2ray.ang.ui.compose.satra.SatraSpace
import com.v2ray.ang.ui.compose.satra.SatraSwitchVisual
import com.v2ray.ang.ui.compose.satra.currentLocale
import com.v2ray.ang.ui.compose.satra.satraClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.satra.groupRow

@Composable
fun PreferenceGroupHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title.uppercase(currentLocale()),
        style = Satra.type.footnote.copy(fontWeight = FontWeight.Medium),
        color = Satra.colors.textSecondary,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = SatraSpace.Gutter + 16.dp, end = SatraSpace.Gutter, top = 22.dp, bottom = 8.dp)
            .semantics { heading() },
    )
}

@Composable
fun CollapsiblePreferenceGroupHeader(
    title: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .satraClickable(role = Role.Button, pressedScale = 0.99f) { onExpandedChange(!expanded) }
            .padding(start = SatraSpace.Gutter + 16.dp, end = SatraSpace.Gutter + 8.dp, top = 22.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title.uppercase(currentLocale()),
            style = Satra.type.footnote.copy(fontWeight = FontWeight.Medium),
            color = Satra.colors.textSecondary,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = SatraIcons.ChevronDown,
            contentDescription = null,
            tint = Satra.colors.textSecondary,
            modifier = Modifier
                .size(20.dp)
                .rotate(if (expanded) 180f else 0f)
        )
    }
}

/**
 * One preference row, drawn as a rounded card segment so long legacy pages read as a stack of
 * grouped, tappable rows that match the main tabs.
 */
@Composable
private fun SettingsItemRow(
    icon: Painter?,
    title: String,
    description: String?,
    enabled: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null
) {
    val c = Satra.colors
    val titleColor = if (enabled) c.textPrimary else c.textTertiary
    val descriptionColor = if (enabled) c.textSecondary else c.textTertiary

    val grouped = com.v2ray.ang.ui.compose.satra.LocalGroupedRows.current
    val outer = if (grouped) {
        Modifier.groupRow().fillMaxWidth()
    } else {
        Modifier
            .fillMaxWidth()
            .padding(horizontal = SatraSpace.Gutter, vertical = 1.5.dp)
            .clip(SatraShapes.Medium)
            .background(c.surface)
    }
    Row(
        modifier = outer
            .then(modifier)
            .then(if (onClick != null) Modifier.satraClickable(enabled = enabled, role = Role.Button, pressedScale = if (grouped) 0.985f else 0.99f, onClick = onClick) else Modifier)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Box(
                Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(if (enabled) c.accentSoft else c.fill),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = icon,
                    contentDescription = null,
                    modifier = Modifier.size(19.dp),
                    tint = if (enabled) c.accent else c.textTertiary
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = Satra.type.body, color = titleColor)
            if (!description.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = description, style = Satra.type.footnote, color = descriptionColor)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing.invoke()
        } else if (onClick != null) {
            Spacer(Modifier.width(8.dp))
            Icon(
                if (androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl)
                    SatraIcons.ChevronLeft else SatraIcons.ChevronRight,
                null, tint = c.textTertiary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
fun SettingsEditItem(
    icon: Painter? = null,
    title: String,
    value: String,
    onValueChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isPassword: Boolean = false,
    keyboardNumber: Boolean = false
) {
    var showDialog by remember { mutableStateOf(false) }
    val description = if (isPassword) {
        if (value.isEmpty()) null else "******"
    } else {
        value.ifEmpty { null }
    }

    SettingsItemRow(
        icon = icon,
        title = title,
        description = description,
        enabled = enabled,
        onClick = if (enabled) {
            { showDialog = true }
        } else null,
        modifier = modifier
    )

    if (showDialog) {
        var text by remember { mutableStateOf(value) }
        InputDialog(
            title = title,
            fields = listOf(
                InputField(
                    label = title,
                    value = text,
                    visualTransformation = VisualTransformation.None
                )
            ),
            onFieldChange = { _, v -> text = v },
            confirmText = stringResource(R.string.action_ok),
            dismissText = stringResource(R.string.action_cancel),
            onConfirm = { showDialog = false; onValueChanged(text) },
            onDismiss = { showDialog = false }
        )
    }
}

@Composable
fun SettingsListItem(
    icon: Painter? = null,
    title: String,
    entries: List<String>,
    values: List<String>,
    selectedValue: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var showDialog by remember { mutableStateOf(false) }
    val options = entries.zip(values)
    val selectedOption = options.find { it.second == selectedValue } ?: options.firstOrNull()
    val summary = selectedOption?.first.orEmpty()

    SettingsItemRow(
        icon = icon,
        title = title,
        description = summary.ifEmpty { null },
        enabled = enabled,
        onClick = if (enabled) {
            { showDialog = true }
        } else null,
        modifier = modifier
    )

    if (showDialog) {
        SelectListDialog(
            title = title,
            options = options,
            optionText = { it.first },
            selectedOption = selectedOption,
            onSelected = { option ->
                showDialog = false
                onSelected(option.second)
            },
            onDismiss = { showDialog = false },
            showRadio = true
        )
    }
}

@Composable
fun SettingsMenuItem(
    icon: Painter? = null,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    SettingsItemRow(
        icon = icon,
        title = title,
        description = subtitle,
        enabled = true,
        onClick = onClick,
        modifier = modifier
    )
}

@Composable
fun SettingsSwitchItem(
    icon: Painter? = null,
    title: String,
    summary: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    SettingsItemRow(
        icon = icon,
        title = title,
        description = summary,
        enabled = enabled,
        onClick = if (enabled) {
            { onCheckedChange(!checked) }
        } else null,
        modifier = modifier.semantics(mergeDescendants = true) {
            toggleableState = androidx.compose.ui.state.ToggleableState(checked)
            role = Role.Switch
        },
        trailing = { SatraSwitchVisual(checked) }
    )
}
