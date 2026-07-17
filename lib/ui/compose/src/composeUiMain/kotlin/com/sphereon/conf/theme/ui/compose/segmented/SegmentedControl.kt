/*
 * Copyright 2023-2026 Sphereon International B.V.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sphereon.conf.theme.ui.compose.segmented

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.sphereon.conf.theme.ui.compose.parseColor
import com.sphereon.conf.theme.ui.compose.parseDp
import com.sphereon.conf.theme.ui.compose.tokens.LocalButtonTokens

/** Token-resolved single-choice control shared by wallet and administration surfaces. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selectedOption: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    cornerRadius: Dp? = null,
) {
    require(options.isNotEmpty()) { "segmented_control_options_empty" }
    require(selectedOption in options) { "segmented_control_selection_missing" }
    val tokens = LocalButtonTokens.current
    val resolvedCornerRadius = cornerRadius ?: parseDp(tokens.primaryRadius, 12.dp)
    SingleChoiceSegmentedButtonRow(
        modifier =
            modifier.then(
                contentDescription?.let { description ->
                    Modifier.semantics { this.contentDescription = description }
                } ?: Modifier,
            ),
    ) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selectedOption,
                onClick = { onSelect(option) },
                // Material's default segmented shape is pill-like. Sphereon controls use the
                // configured component radius; only wallet status indicators are full pills.
                shape = segmentedItemShape(index, options.size, resolvedCornerRadius),
                modifier = Modifier.heightIn(min = 48.dp),
                colors =
                    SegmentedButtonDefaults.colors(
                        activeContainerColor = parseColor(tokens.primaryBackground),
                        activeContentColor = parseColor(tokens.primaryForeground),
                        inactiveContainerColor = parseColor(tokens.secondaryBackground),
                        inactiveContentColor = parseColor(tokens.secondaryForeground),
                        activeBorderColor = parseColor(tokens.primaryBorder),
                        inactiveBorderColor = parseColor(tokens.secondaryBorder),
                    ),
            ) {
                Text(label(option))
            }
        }
    }
}

private fun segmentedItemShape(index: Int, count: Int, radius: Dp) =
    when {
        count == 1 -> RoundedCornerShape(radius)
        index == 0 -> RoundedCornerShape(topStart = radius, bottomStart = radius)
        index == count - 1 -> RoundedCornerShape(topEnd = radius, bottomEnd = radius)
        else -> RoundedCornerShape(0.dp)
    }
