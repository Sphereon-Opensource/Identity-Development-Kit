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

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.sphereon.conf.theme.ui.compose.ComponentTheme
import com.sphereon.conf.theme.ui.compose.parseColor
import com.sphereon.conf.theme.ui.compose.tokens.LocalTabTokens
import org.jetbrains.compose.ui.tooling.preview.Preview

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabRow(
    selectedTabIndex: Int,
    modifier: Modifier = Modifier,
    tabs: @Composable () -> Unit,
) {
    val tokens = LocalTabTokens.current

    PrimaryTabRow(
        selectedTabIndex = selectedTabIndex,
        modifier = modifier,
        containerColor = parseColor(tokens.background),
        contentColor = parseColor(tokens.foreground),
        indicator = {
            TabRowDefaults.PrimaryIndicator(
                Modifier.tabIndicatorOffset(selectedTabIndex),
                color = parseColor(tokens.activeIndicator),
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
        if (content != null) {
            content()
        } else if (text != null) {
            Text(text, color = color)
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
