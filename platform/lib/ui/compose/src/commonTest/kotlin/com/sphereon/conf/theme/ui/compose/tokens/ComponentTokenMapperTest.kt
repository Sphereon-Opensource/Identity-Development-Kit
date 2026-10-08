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

package com.sphereon.conf.theme.ui.compose.tokens

import com.sphereon.conf.theme.core.token.TokenKeyConstants
import kotlin.test.Test
import kotlin.test.assertEquals

class ComponentTokenMapperTest {
    @Test
    fun buttonTokensResolveFromMap() {
        val tokens =
            mapOf(
                TokenKeyConstants.COMP_BUTTON_PRIMARY_BACKGROUND to "#FF0000",
                TokenKeyConstants.COMP_BUTTON_PRIMARY_FOREGROUND to "#FFFFFF",
            )
        val result = ComponentTokenMapper.toButtonTokens(tokens)
        assertEquals("#FF0000", result.primaryBackground)
        assertEquals("#FFFFFF", result.primaryForeground)
        // Unset tokens fall back to defaults
        assertEquals(ButtonTokens().primaryRadius, result.primaryRadius)
    }

    @Test
    fun inputTokensResolveFromMap() {
        val tokens =
            mapOf(
                TokenKeyConstants.COMP_INPUT_BACKGROUND to "#FAFAFA",
                TokenKeyConstants.COMP_INPUT_BORDER_FOCUS to "#0000FF",
            )
        val result = ComponentTokenMapper.toInputTokens(tokens)
        assertEquals("#FAFAFA", result.background)
        assertEquals("#0000FF", result.borderFocus)
        assertEquals(InputTokens().placeholder, result.placeholder)
    }

    @Test
    fun cardTokensResolveFromMap() {
        val tokens =
            mapOf(
                TokenKeyConstants.COMP_CARD_BACKGROUND to "#FFFFFF",
                TokenKeyConstants.COMP_CARD_RADIUS to "24px",
            )
        val result = ComponentTokenMapper.toCardTokens(tokens)
        assertEquals("#FFFFFF", result.background)
        assertEquals("24px", result.radius)
    }

    @Test
    fun emptyMapReturnsDefaults() {
        val empty = emptyMap<String, String>()
        assertEquals(ButtonTokens(), ComponentTokenMapper.toButtonTokens(empty))
        assertEquals(InputTokens(), ComponentTokenMapper.toInputTokens(empty))
        assertEquals(CheckboxTokens(), ComponentTokenMapper.toCheckboxTokens(empty))
        assertEquals(RadioTokens(), ComponentTokenMapper.toRadioTokens(empty))
        assertEquals(CardTokens(), ComponentTokenMapper.toCardTokens(empty))
        assertEquals(BadgeTokens(), ComponentTokenMapper.toBadgeTokens(empty))
        assertEquals(ModalTokens(), ComponentTokenMapper.toModalTokens(empty))
        assertEquals(TabTokens(), ComponentTokenMapper.toTabTokens(empty))
        assertEquals(SelectTokens(), ComponentTokenMapper.toSelectTokens(empty))
        assertEquals(ToastTokens(), ComponentTokenMapper.toToastTokens(empty))
    }

    @Test
    fun toastDismissibleParsesBoolean() {
        val tokens = mapOf(TokenKeyConstants.COMP_TOAST_DISMISSIBLE to "false")
        val result = ComponentTokenMapper.toToastTokens(tokens)
        assertEquals(false, result.dismissible)
    }
}
