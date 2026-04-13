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

package com.sphereon.conf.theme.core.palette

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class M3PaletteExtendedTest {
    @Test
    fun generateExtendedIncludesBasePalette() {
        val extended = M3PaletteGenerator.generateExtended("#6750A4")
        // Base palette should be identical to standard generate()
        val standard = M3PaletteGenerator.generate("#6750A4")
        assertEquals(standard, extended.base)
    }

    @Test
    fun generateExtendedHasSuccessPalette() {
        val extended = M3PaletteGenerator.generateExtended("#6750A4")
        // Success palette should have all 13 tones
        assertTrue(extended.success.tone0.startsWith("#"))
        assertTrue(extended.success.tone50.startsWith("#"))
        assertTrue(extended.success.tone100.startsWith("#"))
        // tone0 near black, tone100 near white
        assertEquals("#000000", extended.success.tone0.uppercase())
        assertEquals("#FFFFFF", extended.success.tone100.uppercase())
    }

    @Test
    fun generateExtendedHasWarningPalette() {
        val extended = M3PaletteGenerator.generateExtended("#6750A4")
        assertTrue(extended.warning.tone0.startsWith("#"))
        assertTrue(extended.warning.tone50.startsWith("#"))
        assertTrue(extended.warning.tone100.startsWith("#"))
    }

    @Test
    fun generateExtendedHasInfoPalette() {
        val extended = M3PaletteGenerator.generateExtended("#6750A4")
        assertTrue(extended.info.tone0.startsWith("#"))
        assertTrue(extended.info.tone50.startsWith("#"))
        assertTrue(extended.info.tone100.startsWith("#"))
    }

    @Test
    fun utilityPalettesAreDistinct() {
        val extended = M3PaletteGenerator.generateExtended("#6750A4")
        // Success, warning, and info should produce different mid-tone colors
        assertNotEquals(extended.success.tone50, extended.warning.tone50)
        assertNotEquals(extended.success.tone50, extended.info.tone50)
        assertNotEquals(extended.warning.tone50, extended.info.tone50)
    }

    @Test
    fun generateExtendedIsDeterministic() {
        val ext1 = M3PaletteGenerator.generateExtended("#6750A4")
        val ext2 = M3PaletteGenerator.generateExtended("#6750A4")
        assertEquals(ext1, ext2)
    }

    @Test
    fun utilityPalettesAreIndependentOfSeed() {
        // Utility palettes use fixed hues, so should be the same regardless of seed
        val ext1 = M3PaletteGenerator.generateExtended("#6750A4")
        val ext2 = M3PaletteGenerator.generateExtended("#FF0000")
        assertEquals(ext1.success, ext2.success)
        assertEquals(ext1.warning, ext2.warning)
        assertEquals(ext1.info, ext2.info)
    }
}
