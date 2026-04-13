package com.sphereon.conf.theme.ui.compose.input

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sphereon.conf.theme.ui.compose.ComponentTheme
import com.sphereon.conf.theme.ui.compose.parseColor
import com.sphereon.conf.theme.ui.compose.parseDp
import com.sphereon.conf.theme.ui.compose.tokens.LocalInputTokens
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun TextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    enabled: Boolean = true,
    isError: Boolean = false,
    singleLine: Boolean = true,
) {
    val tokens = LocalInputTokens.current

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = if (label != null) {
            { Text(label) }
        } else null,
        placeholder = if (placeholder != null) {
            { Text(placeholder) }
        } else null,
        enabled = enabled,
        isError = isError,
        singleLine = singleLine,
        shape = RoundedCornerShape(parseDp(tokens.radius)),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = parseColor(tokens.background),
            unfocusedContainerColor = parseColor(tokens.background),
            focusedBorderColor = parseColor(tokens.borderFocus),
            unfocusedBorderColor = parseColor(tokens.border),
            focusedTextColor = parseColor(tokens.foreground),
            unfocusedTextColor = parseColor(tokens.foreground),
            focusedPlaceholderColor = parseColor(tokens.placeholder),
            unfocusedPlaceholderColor = parseColor(tokens.placeholder),
        ),
    )
}

@Preview
@Composable
private fun TextFieldPreview() {
    androidx.compose.material3.MaterialTheme {
        ComponentTheme {
            androidx.compose.foundation.layout.Column(
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
            ) {
                TextField(value = "Hello", onValueChange = {}, label = "Name")
                TextField(value = "", onValueChange = {}, placeholder = "Enter email")
                TextField(value = "Error", onValueChange = {}, isError = true)
                TextField(value = "", onValueChange = {}, enabled = false, placeholder = "Disabled")
            }
        }
    }
}
