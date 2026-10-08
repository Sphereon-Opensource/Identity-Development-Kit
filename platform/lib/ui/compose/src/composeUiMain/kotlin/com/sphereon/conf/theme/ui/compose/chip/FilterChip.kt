/*
 * Copyright 2023-2026 Sphereon International B.V.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sphereon.conf.theme.ui.compose.chip

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sphereon.conf.theme.compose.LocalThemeTokens
import com.sphereon.conf.theme.ui.compose.parseColor
import com.sphereon.conf.theme.ui.compose.parseDp

/** Wallet-safe chip primitive backed directly by the shared comp.chip token contract. */
@Composable
fun FilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalThemeTokens.current
    val background = parseColor(tokens["comp.chip.background"] ?: tokens["color.surface"] ?: "#FFFBFE")
    val activeBackground = parseColor(tokens["comp.chip.backgroundActive"] ?: tokens["color.text.primary"] ?: "#1C1B1F")
    val foreground = parseColor(tokens["comp.chip.foreground"] ?: tokens["color.text.primary"] ?: "#1C1B1F")
    val activeForeground = parseColor(tokens["comp.chip.foregroundActive"] ?: tokens["color.onPrimary"] ?: "#FFFFFF")
    val border = parseColor(tokens["comp.chip.border"] ?: tokens["color.border.default"] ?: "#79747E")
    val radius = parseDp(tokens["comp.chip.radius"] ?: "9999px")
    androidx.compose.material3.FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(radius),
        colors =
            FilterChipDefaults.filterChipColors(
                containerColor = background,
                labelColor = foreground,
                selectedContainerColor = activeBackground,
                selectedLabelColor = activeForeground,
            ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = border,
            selectedBorderColor = activeBackground,
            borderWidth = 1.dp,
            selectedBorderWidth = 1.dp,
        ),
    )
}
