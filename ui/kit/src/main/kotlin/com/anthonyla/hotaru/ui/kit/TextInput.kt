package com.anthonyla.hotaru.ui.kit

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.meta.spatial.uiset.theme.interFontFamily
import com.meta.spatial.uiset.theme.SpatialTheme

/**
 * visionOS's text field, recessed into the glass. It is Material's because the UI set's cannot mask a password, which
 * [KeyboardType.Password] does here. The keyboard's Done calls [onSubmit].
 */
@Composable
fun TextInput(
    value: String,
    label: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.heightIn(min = HotaruDimens.hitTargetPrimary),
        singleLine = true,
        // Material sizes these itself as the label floats; only the typeface is the app's.
        label = { Text(label, fontFamily = interFontFamily) },
        placeholder = placeholder?.let { { Text(it, color = HotaruColors.TextTertiary, fontFamily = interFontFamily) } },
        visualTransformation = if (keyboardType == KeyboardType.Password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        textStyle = SpatialTheme.typography.body1,
        shape = HotaruShapes.row,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = HotaruColors.Text, unfocusedBorderColor = Color.Transparent,
            focusedLabelColor = HotaruColors.Text, unfocusedLabelColor = HotaruColors.TextSecondary, cursorColor = HotaruColors.Text,
            focusedContainerColor = HotaruColors.Recess, unfocusedContainerColor = HotaruColors.Recess,
        ),
    )
}
