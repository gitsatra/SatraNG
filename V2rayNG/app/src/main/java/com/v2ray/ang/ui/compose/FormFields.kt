package com.v2ray.ang.ui.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.v2ray.ang.ui.compose.satra.Satra
import com.v2ray.ang.ui.compose.satra.SatraShapes
import com.v2ray.ang.ui.compose.satra.SatraSpace
import com.v2ray.ang.ui.compose.satra.SatraTextField
import androidx.compose.ui.draw.alpha
import com.v2ray.ang.ui.compose.satra.satraClickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background

@Composable
fun FormTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    placeholder: String? = null,
    maxLines: Int = 5,
    isError: Boolean = false,
    supportingText: String? = null,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = SatraSpace.Gutter, vertical = 4.dp)
            .alpha(if (enabled) 1f else 0.5f)
    ) {
        SatraTextField(
            value = value,
            onValueChange = onValueChange,
            label = label,
            placeholder = placeholder,
            supporting = supportingText,
            isError = isError,
            singleLine = false,
            readOnly = !enabled,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        )
    }
}

/**
 * A choice field. Read-only: tapping opens a sheet with the options. Editable: free text with a
 * chevron that opens the same sheet. [inset] adds the page gutter (off when placed inside a row).
 */
@Composable
fun FormDropdownField(
    label: String,
    value: String,
    options: List<String>,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    editable: Boolean = false,
    enabled: Boolean = true,
    placeholder: String? = null,
    supportingText: String? = null,
    inset: Boolean = true,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val chevron: @Composable () -> Unit = {
        if (editable) {
            com.v2ray.ang.ui.compose.satra.SatraIconButton(
                com.v2ray.ang.ui.compose.satra.SatraIcons.ChevronDown, label,
                { if (enabled && options.isNotEmpty()) expanded = true },
                tint = Satra.colors.textTertiary, size = 20.dp,
            )
        } else {
            androidx.compose.material3.Icon(
                com.v2ray.ang.ui.compose.satra.SatraIcons.ChevronDown, null,
                tint = Satra.colors.textTertiary,
                modifier = Modifier.padding(end = 4.dp),
            )
        }
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(if (inset) Modifier.padding(horizontal = SatraSpace.Gutter, vertical = 4.dp) else Modifier)
            .alpha(if (enabled) 1f else 0.5f),
    ) {
        SatraTextField(
            value = value,
            onValueChange = { if (editable && enabled) onValueChange(it) },
            label = label,
            placeholder = placeholder,
            supporting = supportingText,
            readOnly = !editable || !enabled,
            trailing = chevron,
        )
        if (!editable) {
            // Transparent tap target over the read-only field.
            Box(
                Modifier
                    .matchParentSize()
                    .then(
                        if (enabled) Modifier.satraClickable(role = androidx.compose.ui.semantics.Role.DropdownList, pressedScale = 0.99f) { expanded = true }
                        else Modifier
                    ),
            )
        }
    }
    if (expanded) {
        com.v2ray.ang.ui.compose.satra.SatraSheet(onDismiss = { expanded = false }) {
            com.v2ray.ang.ui.compose.satra.SheetTitle(label)
            androidx.compose.foundation.layout.Column(
                Modifier
                    .padding(horizontal = SatraSpace.Gutter)
                    .clip(SatraShapes.Large)
                    .background(Satra.colors.surface)
                    .androidx_verticalScroll(),
            ) {
                options.forEachIndexed { i, option ->
                    com.v2ray.ang.ui.compose.satra.SatraRow(
                        title = option,
                        trailing = if (option == value) ({
                            androidx.compose.material3.Icon(
                                com.v2ray.ang.ui.compose.satra.SatraIcons.Check, null,
                                tint = Satra.colors.accent,
                            )
                        }) else null,
                        onClick = { onValueChange(option); expanded = false },
                    )
                    if (i < options.lastIndex) com.v2ray.ang.ui.compose.satra.GroupSeparator()
                }
            }
            androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 16.dp))
        }
    }
}

@Composable
internal fun satraFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = Satra.colors.fill,
    unfocusedContainerColor = Satra.colors.fill,
    disabledContainerColor = Satra.colors.fill.copy(alpha = 0.5f),
    errorContainerColor = Satra.colors.fill,
    focusedBorderColor = Satra.colors.accent,
    unfocusedBorderColor = Color.Transparent,
    errorBorderColor = Satra.colors.danger,
    focusedLabelColor = Satra.colors.accent,
    unfocusedLabelColor = Satra.colors.textSecondary,
    cursorColor = Satra.colors.accent,
    selectionColors = TextSelectionColors(
        handleColor = Satra.colors.accent,
        backgroundColor = Satra.colors.accent.copy(alpha = 0.35f),
    ),
)

@Composable
private fun Modifier.androidx_verticalScroll(): Modifier =
    this.then(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()))
