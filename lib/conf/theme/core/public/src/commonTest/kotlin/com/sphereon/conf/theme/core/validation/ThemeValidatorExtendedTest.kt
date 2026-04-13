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

package com.sphereon.conf.theme.core.validation

import com.sphereon.conf.theme.core.model.ThemeToken
import com.sphereon.conf.theme.core.model.ThemeTokenType
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThemeValidatorExtendedTest {
    @Test
    fun validShadowNone() {
        val token = ThemeToken("shadow.elevation.none", "none", ThemeTokenType.SHADOW)
        val result = ThemeValidator.validateToken(token)
        assertTrue(result.valid, "Shadow 'none' should be valid: ${result.errors}")
    }

    @Test
    fun validShadowSingleLayer() {
        val token = ThemeToken("shadow.elevation.xs", "0 1px 2px 0 rgba(0,0,0,0.05)", ThemeTokenType.SHADOW)
        val result = ThemeValidator.validateToken(token)
        assertTrue(result.valid, "Single-layer shadow should be valid: ${result.errors}")
    }

    @Test
    fun validShadowMultiLayer() {
        val token = ThemeToken("shadow.elevation.sm", "0 1px 3px 0 rgba(0,0,0,0.1), 0 1px 2px -1px rgba(0,0,0,0.1)", ThemeTokenType.SHADOW)
        val result = ThemeValidator.validateToken(token)
        assertTrue(result.valid, "Multi-layer shadow should be valid: ${result.errors}")
    }

    @Test
    fun validShadowReference() {
        val token = ThemeToken("shadow.subtle", "{shadow.elevation.xs}", ThemeTokenType.SHADOW)
        val result = ThemeValidator.validateToken(token)
        assertTrue(result.valid, "Shadow reference should be valid: ${result.errors}")
    }

    @Test
    fun invalidShadowValue() {
        val token = ThemeToken("shadow.test", "not-a-shadow", ThemeTokenType.SHADOW)
        val result = ThemeValidator.validateToken(token)
        assertFalse(result.valid, "Invalid shadow value should fail validation")
    }

    @Test
    fun validSpacingValue() {
        val token = ThemeToken("spacing.4", "16px", ThemeTokenType.SPACING)
        val result = ThemeValidator.validateToken(token)
        assertTrue(result.valid, "Spacing '16px' should be valid: ${result.errors}")
    }

    @Test
    fun validSpacingReference() {
        val token = ThemeToken("spacing.inline.md", "{spacing.4}", ThemeTokenType.SPACING)
        val result = ThemeValidator.validateToken(token)
        assertTrue(result.valid, "Spacing reference should be valid: ${result.errors}")
    }

    @Test
    fun validBorderWidthValue() {
        val token = ThemeToken("borderWidth.thin", "1px", ThemeTokenType.BORDER_WIDTH)
        val result = ThemeValidator.validateToken(token)
        assertTrue(result.valid, "Border width '1px' should be valid: ${result.errors}")
    }

    @Test
    fun validBorderWidthZero() {
        val token = ThemeToken("borderWidth.none", "0px", ThemeTokenType.BORDER_WIDTH)
        val result = ThemeValidator.validateToken(token)
        assertTrue(result.valid, "Border width '0px' should be valid: ${result.errors}")
    }

    @Test
    fun invalidSpacingValue() {
        val token = ThemeToken("spacing.test", "invalid", ThemeTokenType.SPACING)
        val result = ThemeValidator.validateToken(token)
        assertFalse(result.valid, "Invalid spacing value should fail validation")
    }
}
