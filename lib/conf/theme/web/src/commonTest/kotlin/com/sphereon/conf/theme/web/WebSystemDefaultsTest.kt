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

package com.sphereon.conf.theme.web

import com.sphereon.conf.theme.core.model.ThemeVariant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WebSystemDefaultsTest {
    @Test
    fun lightDefaultsContainAllColorTokens() {
        val tokens = WebSystemDefaults.light
        assertNotNull(tokens["color.primary"])
        assertNotNull(tokens["color.onPrimary"])
        assertNotNull(tokens["color.surface"])
        assertNotNull(tokens["color.background"])
        // Wallet brand purple, palette.brand.500
        assertEquals("#7C40E8", tokens["color.primary"])
    }

    @Test
    fun darkDefaultsContainAllColorTokens() {
        val tokens = WebSystemDefaults.dark
        assertNotNull(tokens["color.primary"])
        // Wallet dark primary, palette.brand.500
        assertEquals("#7C40E8", tokens["color.primary"])
    }

    @Test
    fun forVariantReturnsCorrectMap() {
        assertEquals(WebSystemDefaults.light, WebSystemDefaults.forVariant(ThemeVariant.LIGHT))
        assertEquals(WebSystemDefaults.dark, WebSystemDefaults.forVariant(ThemeVariant.DARK))
        assertEquals(WebSystemDefaults.highContrastLight, WebSystemDefaults.forVariant(ThemeVariant.HIGH_CONTRAST_LIGHT))
        assertEquals(WebSystemDefaults.highContrastDark, WebSystemDefaults.forVariant(ThemeVariant.HIGH_CONTRAST_DARK))
    }

    @Test
    fun forVariantStringReturnsCorrectMap() {
        assertEquals(WebSystemDefaults.light, WebSystemDefaults.forVariantString("light"))
        assertEquals(WebSystemDefaults.dark, WebSystemDefaults.forVariantString("dark"))
        assertEquals(WebSystemDefaults.highContrastLight, WebSystemDefaults.forVariantString("high_contrast_light"))
        assertEquals(WebSystemDefaults.highContrastDark, WebSystemDefaults.forVariantString("high_contrast_dark"))
    }

    @Test
    fun highContrastGroundsAreDistinct() {
        // The two high-contrast variants invert rather than mirror, so a copy-paste that pointed
        // both at the same definition would otherwise pass every other assertion in this file.
        assertEquals("#000000", WebSystemDefaults.highContrastDark["color.surface"])
        assertEquals("#FFFFFF", WebSystemDefaults.highContrastLight["color.surface"])
    }

    @Test
    fun defaultsContainTypographyAndShapeTokens() {
        val tokens = WebSystemDefaults.light
        assertNotNull(tokens["typography.fontFamily"])
        assertNotNull(tokens["shape.cornerMedium"])
        assertNotNull(tokens["motion.duration.short1"])
        assertTrue(tokens.size > 100)
    }
}
