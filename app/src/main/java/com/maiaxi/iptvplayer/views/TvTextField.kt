package com.maiaxi.iptvplayer.views

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

/**
 * A text field behind a focusable surface. Focusing a text field opens the keyboard, which then
 * takes the D-pad, so the field only becomes focusable after OK is pressed on the surface.
 */
@Composable
fun TvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "",
    leadingIcon: ImageVector? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    password: Boolean = false,
    down: FocusRequester = FocusRequester.Default,
) {
    var editing by remember { mutableStateOf(false) }
    val surfaceFocus = remember { FocusRequester() }
    val fieldFocus = remember { FocusRequester() }
    val shape = RoundedCornerShape(8.dp)
    val colors = MaterialTheme.colorScheme

    LaunchedEffect(editing) { if (editing) fieldFocus.requestFocus() }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = modifier) {
        label?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant) }
        Surface(
            onClick = { editing = true },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(surfaceFocus)
                .focusProperties { this.down = down },
            shape = ClickableSurfaceDefaults.shape(shape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = colors.surfaceVariant,
                focusedContainerColor = colors.surfaceVariant,
                contentColor = colors.onSurface,
                focusedContentColor = colors.onSurface,
            ),
            border = ClickableSurfaceDefaults.border(
                border = Border(BorderStroke(2.dp, if (editing) colors.primary else colors.surfaceVariant), shape = shape),
                focusedBorder = Border(BorderStroke(2.dp, colors.onSurface), shape = shape),
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                leadingIcon?.let {
                    Icon(it, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.padding(end = 10.dp).size(18.dp))
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                    cursorBrush = SolidColor(colors.primary),
                    visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction, autoCorrectEnabled = false),
                    keyboardActions = KeyboardActions(onAny = {
                        editing = false
                        surfaceFocus.requestFocus()
                    }),
                    decorationBox = { inner ->
                        Box {
                            if (value.isEmpty()) {
                                Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant.copy(alpha = 0.6f))
                            }
                            inner()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(fieldFocus)
                        .focusProperties { canFocus = editing }
                        .onFocusChanged { if (!it.isFocused && editing) editing = false },
                )
            }
        }
    }
}
