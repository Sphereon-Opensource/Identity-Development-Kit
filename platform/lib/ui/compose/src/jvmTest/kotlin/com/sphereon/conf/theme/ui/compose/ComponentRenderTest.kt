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

package com.sphereon.conf.theme.ui.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.sphereon.conf.theme.compose.LocalThemeTokens
import com.sphereon.conf.theme.ui.compose.badge.Badge
import com.sphereon.conf.theme.ui.compose.badge.BadgeVariant
import com.sphereon.conf.theme.ui.compose.button.Button
import com.sphereon.conf.theme.ui.compose.button.ButtonVariant
import com.sphereon.conf.theme.ui.compose.card.Card
import com.sphereon.conf.theme.ui.compose.checkbox.Checkbox
import com.sphereon.conf.theme.ui.compose.radio.Radio
import com.sphereon.conf.theme.ui.compose.radio.RadioGroup
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ComponentRenderTest {
    /**
     * Wraps content with minimal theme setup for testing.
     */
    @Test
    fun buttonRendersAndClicks() =
        runComposeUiTest {
            var clicked = false
            setContent {
                ComponentTheme {
                    MaterialTheme {
                        Button(onClick = { clicked = true }) {
                            Text("Click me")
                        }
                    }
                }
            }
            onNodeWithText("Click me").assertIsDisplayed()
            onNodeWithText("Click me").performClick()
            assertTrue(clicked)
        }

    @Test
    fun buttonDisabledDoesNotClick() =
        runComposeUiTest {
            var clicked = false
            setContent {
                ComponentTheme {
                    MaterialTheme {
                        Button(onClick = { clicked = true }, enabled = false) {
                            Text("Disabled")
                        }
                    }
                }
            }
            onNodeWithText("Disabled").assertIsDisplayed()
            onNodeWithText("Disabled").assertIsNotEnabled()
            onNodeWithText("Disabled").performClick()
            assertTrue(!clicked)
        }

    @Test
    fun buttonVariantsRender() =
        runComposeUiTest {
            setContent {
                ComponentTheme {
                    MaterialTheme {
                        Button(onClick = {}, variant = ButtonVariant.Primary) { Text("Primary") }
                        Button(onClick = {}, variant = ButtonVariant.Secondary) { Text("Secondary") }
                        Button(onClick = {}, variant = ButtonVariant.Ghost) { Text("Ghost") }
                    }
                }
            }
            onNodeWithText("Primary").assertIsDisplayed()
            onNodeWithText("Secondary").assertIsDisplayed()
            onNodeWithText("Ghost").assertIsDisplayed()
        }

    @Test
    fun cardRendersContent() =
        runComposeUiTest {
            setContent {
                ComponentTheme {
                    MaterialTheme {
                        Card {
                            Text("Card content")
                        }
                    }
                }
            }
            onNodeWithText("Card content").assertIsDisplayed()
        }

    @Test
    fun badgeRendersText() =
        runComposeUiTest {
            setContent {
                ComponentTheme {
                    MaterialTheme {
                        Badge(text = "New")
                        Badge(text = "Error", variant = BadgeVariant.Error)
                    }
                }
            }
            onNodeWithText("New").assertIsDisplayed()
            onNodeWithText("Error").assertIsDisplayed()
        }

    @Test
    fun checkboxToggles() =
        runComposeUiTest {
            var checked = false
            setContent {
                ComponentTheme {
                    MaterialTheme {
                        var isChecked by remember { mutableStateOf(false) }
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = {
                                isChecked = it
                                checked = it
                            },
                            label = "Accept",
                        )
                    }
                }
            }
            onNodeWithText("Accept").assertIsDisplayed()
            // Click the toggleable node (the M3 Checkbox)
            onNode(isToggleable()).assertIsOff()
            onNode(isToggleable()).performClick()
            onNode(isToggleable()).assertIsOn()
            assertTrue(checked)
        }

    @Test
    fun radioGroupRendersLabels() =
        runComposeUiTest {
            setContent {
                ComponentTheme {
                    MaterialTheme {
                        RadioGroup {
                            Radio(
                                selected = false,
                                onClick = {},
                                label = "Option A",
                            )
                            Radio(
                                selected = true,
                                onClick = {},
                                label = "Option B",
                            )
                        }
                    }
                }
            }
            onNodeWithText("Option A").assertIsDisplayed()
            onNodeWithText("Option B").assertIsDisplayed()
        }

    @Test
    fun componentsRenderWithCustomTokens() =
        runComposeUiTest {
            // Provide custom token values to verify token cascade
            val customTokens =
                mapOf(
                    "comp.button.primary.background" to "#FF0000",
                    "comp.card.background" to "#00FF00",
                )
            setContent {
                CompositionLocalProvider(
                    LocalThemeTokens provides customTokens,
                ) {
                    ComponentTheme {
                        MaterialTheme {
                            Button(onClick = {}) { Text("Red Button") }
                            Card { Text("Green Card") }
                        }
                    }
                }
            }
            // Components should render without crashing even with custom tokens
            onNodeWithText("Red Button").assertIsDisplayed()
            onNodeWithText("Green Card").assertIsDisplayed()
        }

    @Test
    fun componentsRenderWithEmptyTokens() =
        runComposeUiTest {
            // Verify components fall back to defaults with no tokens
            setContent {
                CompositionLocalProvider(
                    LocalThemeTokens provides emptyMap(),
                ) {
                    ComponentTheme {
                        MaterialTheme {
                            Button(onClick = {}) { Text("Default Button") }
                            Card { Text("Default Card") }
                            Badge(text = "Default Badge")
                        }
                    }
                }
            }
            onNodeWithText("Default Button").assertIsDisplayed()
            onNodeWithText("Default Card").assertIsDisplayed()
            onNodeWithText("Default Badge").assertIsDisplayed()
        }
}
