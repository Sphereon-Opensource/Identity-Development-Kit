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

package com.sphereon.conf.theme.ui.compose.checkbox

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sphereon.conf.theme.ui.compose.ComponentTheme
import com.sphereon.conf.theme.ui.compose.parseColor
import com.sphereon.conf.theme.ui.compose.tokens.LocalCheckboxTokens
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun Checkbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String? = null,
) {
    val tokens = LocalCheckboxTokens.current

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors =
                CheckboxDefaults.colors(
                    checkedColor = parseColor(tokens.checkedBackground),
                    checkmarkColor = parseColor(tokens.checkedForeground),
                    uncheckedColor = parseColor(tokens.border),
                    disabledCheckedColor = parseColor(tokens.disabledBackground),
                    disabledUncheckedColor = parseColor(tokens.disabledBackground),
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
private fun CheckboxPreview() {
    androidx.compose.material3.MaterialTheme {
        ComponentTheme {
            androidx.compose.foundation.layout.Column {
                Checkbox(checked = false, onCheckedChange = {}, label = "Unchecked")
                Checkbox(checked = true, onCheckedChange = {}, label = "Checked")
                Checkbox(checked = false, onCheckedChange = {}, enabled = false, label = "Disabled")
            }
        }
    }
}
