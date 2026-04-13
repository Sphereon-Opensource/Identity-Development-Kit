package com.sphereon.conf.theme.ui.compose.select

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.sphereon.conf.theme.ui.compose.ComponentTheme
import com.sphereon.conf.theme.ui.compose.parseColor
import com.sphereon.conf.theme.ui.compose.parseDp
import com.sphereon.conf.theme.ui.compose.tokens.LocalSelectTokens
import org.jetbrains.compose.ui.tooling.preview.Preview

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> Select(
    items: List<T>,
    selectedItem: T?,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    getItemLabel: (T) -> String = { it.toString() },
    placeholder: String = "Select...",
    enabled: Boolean = true,
) {
    val tokens = LocalSelectTokens.current
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selectedItem?.let(getItemLabel) ?: "",
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            placeholder = { Text(placeholder) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
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
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(getItemLabel(item)) },
                    onClick = {
                        onSelect(item)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Preview
@Composable
private fun SelectPreview() {
    androidx.compose.material3.MaterialTheme {
        ComponentTheme {
            Select(
                items = listOf("Apple", "Banana", "Cherry"),
                selectedItem = "Banana",
                onSelect = {},
                placeholder = "Pick fruit",
            )
        }
    }
}
