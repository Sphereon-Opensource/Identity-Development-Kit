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

package com.sphereon.conf.theme.ui.compose.card

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sphereon.conf.theme.ui.compose.ComponentTheme
import com.sphereon.conf.theme.ui.compose.parseColor
import com.sphereon.conf.theme.ui.compose.parseDp
import com.sphereon.conf.theme.ui.compose.tokens.LocalCardTokens
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun Card(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues? = null,
    content: @Composable () -> Unit,
) {
    val tokens = LocalCardTokens.current
    val shape = RoundedCornerShape(parseDp(tokens.radius))
    val colors =
        CardDefaults.cardColors(
            containerColor = parseColor(tokens.background),
            contentColor = parseColor(tokens.foreground),
        )
    // comp.card carries a border and a raised shadow in every theme tier; without them the card
    // is indistinguishable from the page background (the web Card renders both).
    val borderWidth = parseDp(tokens.borderWidth, 1.dp)
    val border = if (borderWidth > 0.dp) BorderStroke(borderWidth, parseColor(tokens.border)) else null
    val elevation =
        CardDefaults.cardElevation(
            defaultElevation = if (tokens.shadow.trim().equals("none", ignoreCase = true)) 0.dp else 1.dp,
        )

    if (onClick != null) {
        androidx.compose.material3.Card(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            colors = colors,
            elevation = elevation,
            border = border,
        ) {
            Box(Modifier.padding(contentPadding ?: PaddingValues(parseDp(tokens.padding)))) {
                content()
            }
        }
    } else {
        androidx.compose.material3.Card(
            modifier = modifier,
            shape = shape,
            colors = colors,
            elevation = elevation,
            border = border,
        ) {
            Box(Modifier.padding(contentPadding ?: PaddingValues(parseDp(tokens.padding)))) {
                content()
            }
        }
    }
}

@Preview
@Composable
private fun CardPreview() {
    androidx.compose.material3.MaterialTheme {
        ComponentTheme {
            Card {
                Text("Card content")
            }
        }
    }
}
