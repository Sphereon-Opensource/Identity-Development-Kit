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

package com.sphereon.conf.theme.ui.compose.badge

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sphereon.conf.theme.compose.LocalThemeTokens
import com.sphereon.conf.theme.ui.compose.parseColor
import com.sphereon.conf.theme.ui.compose.parseDp

enum class StatusPillVariant { Success, Warning, Error, Info, Neutral }

/**
 * Soft, bordered status pill driven by the `comp.statuspill.*` token group. This is the Compose
 * counterpart of the web StatusBadge: container-tinted background, matching border, full radius.
 * Use [Badge] for counts and plain labels; use this wherever a state is communicated.
 */
@Composable
fun StatusPill(
    text: String,
    variant: StatusPillVariant = StatusPillVariant.Neutral,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalThemeTokens.current
    val slot =
        when (variant) {
            StatusPillVariant.Success -> "success"
            StatusPillVariant.Warning -> "warning"
            StatusPillVariant.Error -> "error"
            StatusPillVariant.Info -> "info"
            StatusPillVariant.Neutral -> "neutral"
        }

    fun color(
        role: String,
        fallback: Color,
    ): Color = tokens["comp.statuspill.$slot.$role"]?.let { parseColor(it, fallback) } ?: fallback

    val background = color("background", MaterialTheme.colorScheme.surfaceVariant)
    val foreground = color("foreground", MaterialTheme.colorScheme.onSurfaceVariant)
    val border = color("border", foreground.copy(alpha = 0.25f))
    val radius = tokens["comp.statuspill.radius"]?.let { parseDp(it, 9999.dp) } ?: 9999.dp

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(radius),
        color = background,
        contentColor = foreground,
        border = BorderStroke(1.dp, border),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}
