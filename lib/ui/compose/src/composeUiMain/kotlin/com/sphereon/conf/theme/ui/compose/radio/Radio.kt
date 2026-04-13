/*
 * Copyright 2023-2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sphereon.conf.theme.ui.compose.radio

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sphereon.conf.theme.ui.compose.ComponentTheme
import com.sphereon.conf.theme.ui.compose.parseColor
import com.sphereon.conf.theme.ui.compose.tokens.LocalRadioTokens
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun RadioGroup(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.selectableGroup()) {
        content()
    }
}

@Composable
fun Radio(
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String? = null,
) {
    val tokens = LocalRadioTokens.current

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.RadioButton(
            selected = selected,
            onClick = onClick,
            enabled = enabled,
            colors =
                RadioButtonDefaults.colors(
                    selectedColor = parseColor(tokens.selectedIndicator),
                    unselectedColor = parseColor(tokens.border),
                    disabledSelectedColor = parseColor(tokens.disabledBorder),
                    disabledUnselectedColor = parseColor(tokens.disabledBorder),
                ),
        )
        if (label != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = label,
                color = parseColor(tokens.labelForeground),
            )
        }
    }
}

@Preview
@Composable
private fun RadioPreview() {
    androidx.compose.material3.MaterialTheme {
        ComponentTheme {
            RadioGroup {
                Radio(selected = true, onClick = {}, label = "Selected")
                Radio(selected = false, onClick = {}, label = "Unselected")
                Radio(selected = false, onClick = {}, enabled = false, label = "Disabled")
            }
        }
    }
}
