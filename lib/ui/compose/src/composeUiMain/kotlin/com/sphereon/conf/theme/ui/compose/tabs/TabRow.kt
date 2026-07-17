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

package com.sphereon.conf.theme.ui.compose.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sphereon.conf.theme.ui.compose.ComponentTheme
import com.sphereon.conf.theme.ui.compose.parseColor
import com.sphereon.conf.theme.ui.compose.tokens.LocalTabTokens
import androidx.compose.ui.tooling.preview.Preview

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabRow(
    selectedTabIndex: Int,
    modifier: Modifier = Modifier,
    containerColor: Color? = null,
    tabs: @Composable () -> Unit,
) {
    val tokens = LocalTabTokens.current

    PrimaryTabRow(
        selectedTabIndex = selectedTabIndex,
        modifier = modifier,
        containerColor = containerColor ?: parseColor(tokens.background),
        contentColor = parseColor(tokens.foreground),
        indicator = {
            // The indicator spans the tab width minus a fixed inset, so it always covers at least
            // the label. Both the M3 24dp pip and content-size matching render too narrow.
            Box(
                Modifier
                    .tabIndicatorOffset(selectedTabIndex)
                    .padding(horizontal = 20.dp)
                    .height(3.dp)
                    .background(
                        parseColor(tokens.activeIndicator),
                        RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp),
                    ),
            )
        },
        divider = {},
        tabs = tabs,
    )
}

@Composable
fun Tab(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String? = null,
    enabled: Boolean = true,
    content: (@Composable () -> Unit)? = null,
) {
    val tokens = LocalTabTokens.current
    val color =
        if (selected) {
            parseColor(tokens.activeForeground)
        } else {
            parseColor(tokens.foreground)
        }

    Tab(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        selectedContentColor = parseColor(tokens.activeForeground),
        unselectedContentColor = parseColor(tokens.foreground),
    ) {
        // Vertical air around the label; without it the active indicator hugs the text baseline.
        Box(Modifier.padding(vertical = 14.dp)) {
            if (content != null) {
                content()
            } else if (text != null) {
                Text(text, color = color)
            }
        }
    }
}

@Preview
@Composable
private fun TabRowPreview() {
    androidx.compose.material3.MaterialTheme {
        ComponentTheme {
            TabRow(selectedTabIndex = 0) {
                Tab(selected = true, onClick = {}, text = "Tab 1")
                Tab(selected = false, onClick = {}, text = "Tab 2")
                Tab(selected = false, onClick = {}, text = "Tab 3")
            }
        }
    }
}
