/*
 * Copyright 2023-2026 Sphereon International B.V.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sphereon.conf.theme.ui.compose.menu

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sphereon.conf.theme.ui.compose.parseColor
import com.sphereon.conf.theme.ui.compose.parseDp
import com.sphereon.conf.theme.ui.compose.tokens.LocalSelectTokens

/** Externally-stateful filter menu so expansion can be serialized by a presenter. */
@Composable
fun <T> FilterMenu(
    options: List<T>,
    selectedOption: T,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    trigger: @Composable BoxScope.() -> Unit,
) {
    require(options.isNotEmpty()) { "filter_menu_options_empty" }
    require(selectedOption in options) { "filter_menu_selection_missing" }
    val tokens = LocalSelectTokens.current
    Box(modifier) {
        Surface(
            modifier =
                Modifier
                    .size(48.dp)
                    .semantics {
                        this.contentDescription = contentDescription
                        role = Role.Button
                    }
                    .clickable(role = Role.Button) { onExpandedChange(!expanded) },
            shape = CircleShape,
            color = parseColor(tokens.background),
            contentColor = parseColor(tokens.foreground),
            border = BorderStroke(1.dp, parseColor(if (expanded) tokens.borderFocus else tokens.border)),
        ) {
            Box(contentAlignment = Alignment.Center, content = trigger)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(parseDp(tokens.menuRadius)),
            containerColor = parseColor(tokens.menuBackground),
        ) {
            options.forEach { option ->
                val selected = option == selectedOption
                DropdownMenuItem(
                    text = { Text(label(option)) },
                    onClick = {
                        onSelect(option)
                        onExpandedChange(false)
                    },
                    trailingIcon = {
                        if (selected) Text("\u2713", color = MaterialTheme.colorScheme.primary)
                    },
                    modifier =
                        Modifier.semantics {
                            this.selected = selected
                            role = Role.RadioButton
                        },
                )
            }
        }
    }
}
