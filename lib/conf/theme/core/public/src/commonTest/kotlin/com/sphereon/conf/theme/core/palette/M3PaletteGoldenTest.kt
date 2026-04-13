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
import kotlin.test.assertTrue

/**
 * Golden tests for M3PaletteGenerator: verify deterministic output
 * for known seed colors. These tests ensure cross-platform parity.
 */
class M3PaletteGoldenTest {
    @Test
    fun purpleSeedShouldProduceDeterministicPalette() {
        val palette = M3PaletteGenerator.generate("#6750A4")
        assertEquals("#6750A4", palette.seedColor)
        // Primary palette should span from dark to light
        assertValidHex(palette.primary.tone0)
        assertValidHex(palette.primary.tone100)
        // Tone0 should be very dark (near black)
        assertTrue(isNearBlack(palette.primary.tone0), "tone0 should be near-black")
        // Tone100 should be very light (near white)
        assertTrue(isNearWhite(palette.primary.tone100), "tone100 should be near-white")
    }

    @Test
    fun redSeedShouldProduceDeterministicPalette() {
        val palette = M3PaletteGenerator.generate("#B3261E")
        assertEquals("#B3261E", palette.seedColor)
        assertValidHex(palette.primary.tone40)
        assertValidHex(palette.secondary.tone40)
        assertValidHex(palette.tertiary.tone40)
        assertValidHex(palette.neutral.tone40)
        assertValidHex(palette.error.tone40)
    }

    @Test
    fun blueSeedShouldProduceDeterministicPalette() {
        val palette = M3PaletteGenerator.generate("#1565C0")
        assertEquals("#1565C0", palette.seedColor)
        assertValidHex(palette.primary.tone50)
    }

    @Test
    fun greenSeedShouldProduceDeterministicPalette() {
        val palette = M3PaletteGenerator.generate("#2E7D32")
        assertValidHex(palette.primary.tone40)
        assertValidHex(palette.secondary.tone90)
    }

    @Test
    fun orangeSeedShouldProduceDeterministicPalette() {
        val palette = M3PaletteGenerator.generate("#E65100")
        assertValidHex(palette.primary.tone40)
        assertValidHex(palette.tertiary.tone80)
    }

    @Test
    fun palettesShouldBeDeterministic() {
        val palette1 = M3PaletteGenerator.generate("#6750A4")
        val palette2 = M3PaletteGenerator.generate("#6750A4")
        assertEquals(palette1.primary.tone40, palette2.primary.tone40)
        assertEquals(palette1.secondary.tone80, palette2.secondary.tone80)
        assertEquals(palette1.tertiary.tone90, palette2.tertiary.tone90)
        assertEquals(palette1.neutral.tone50, palette2.neutral.tone50)
        assertEquals(palette1.error.tone40, palette2.error.tone40)
    }

    @Test
    fun allToneStopsShouldBePresent() {
        val palette = M3PaletteGenerator.generate("#6750A4")
        for (role in listOf(palette.primary, palette.secondary, palette.tertiary, palette.neutral, palette.error)) {
            assertValidHex(role.tone0)
            assertValidHex(role.tone10)
            assertValidHex(role.tone20)
            assertValidHex(role.tone30)
            assertValidHex(role.tone40)
            assertValidHex(role.tone50)
            assertValidHex(role.tone60)
            assertValidHex(role.tone70)
            assertValidHex(role.tone80)
            assertValidHex(role.tone90)
            assertValidHex(role.tone95)
            assertValidHex(role.tone99)
            assertValidHex(role.tone100)
        }
    }

    @Test
    fun errorPaletteShouldBeConsistentAcrossSeeds() {
        // Error palette uses fixed hue=25, chroma=84 regardless of seed
        val palette1 = M3PaletteGenerator.generate("#6750A4")
        val palette2 = M3PaletteGenerator.generate("#1565C0")
        assertEquals(palette1.error.tone40, palette2.error.tone40)
        assertEquals(palette1.error.tone80, palette2.error.tone80)
    }

    @Test
    fun differentSeedsShouldProduceDifferentPrimaries() {
        val purple = M3PaletteGenerator.generate("#6750A4")
        val blue = M3PaletteGenerator.generate("#1565C0")
        assertTrue(
            purple.primary.tone40 != blue.primary.tone40,
            "Different seeds should produce different primary tone40 values",
        )
    }

    @Test
    fun multiSeedShouldProduceDeterministicPalette() {
        val palette1 =
            M3PaletteGenerator.generateFromSeeds(
                primarySeed = "#6750A4",
                secondarySeed = "#FF5722",
                neutralSeed = "#9E9E9E",
            )
        val palette2 =
            M3PaletteGenerator.generateFromSeeds(
                primarySeed = "#6750A4",
                secondarySeed = "#FF5722",
                neutralSeed = "#9E9E9E",
            )
        assertEquals(palette1.primary.tone40, palette2.primary.tone40)
        assertEquals(palette1.secondary.tone40, palette2.secondary.tone40)
        assertEquals(palette1.neutral.tone50, palette2.neutral.tone50)
    }

    @Test
    fun multiSeedWithoutOptionalsShouldMatchSingleSeed() {
        val single = M3PaletteGenerator.generate("#6750A4")
        val multi = M3PaletteGenerator.generateFromSeeds(primarySeed = "#6750A4")
        assertEquals(single.primary.tone40, multi.primary.tone40)
        assertEquals(single.secondary.tone40, multi.secondary.tone40)
        assertEquals(single.tertiary.tone40, multi.tertiary.tone40)
        assertEquals(single.neutral.tone50, multi.neutral.tone50)
        assertEquals(single.error.tone40, multi.error.tone40)
    }

    @Test
    fun multiSeedWithDifferentSecondaryShouldDiffer() {
        val p1 =
            M3PaletteGenerator.generateFromSeeds(
                primarySeed = "#6750A4",
                secondarySeed = "#FF5722",
            )
        val p2 =
            M3PaletteGenerator.generateFromSeeds(
                primarySeed = "#6750A4",
            )
        assertTrue(
            p1.secondary.tone40 != p2.secondary.tone40,
            "Different secondary seeds should produce different results",
        )
    }

    @Test
    fun blackAndWhiteSeedsShouldWork() {
        val black = M3PaletteGenerator.generate("#000000")
        assertValidHex(black.primary.tone40)
        val white = M3PaletteGenerator.generate("#FFFFFF")
        assertValidHex(white.primary.tone40)
    }

    private fun assertValidHex(hex: String) {
        assertTrue(hex.startsWith("#"), "Expected hex color starting with #, got: $hex")
        val clean = hex.removePrefix("#")
        assertTrue(
            clean.length == 6 || clean.length == 8,
            "Expected 6 or 8 hex digits, got ${clean.length} in: $hex",
        )
        assertTrue(
            clean.all { it in "0123456789ABCDEFabcdef" },
            "Invalid hex characters in: $hex",
        )
    }

    private fun isNearBlack(hex: String): Boolean {
        val value = hex.removePrefix("#").take(6).toLong(16)
        val r = (value shr 16) and 0xFF
        val g = (value shr 8) and 0xFF
        val b = value and 0xFF
        return r < 30 && g < 30 && b < 30
    }

    private fun isNearWhite(hex: String): Boolean {
        val value = hex.removePrefix("#").take(6).toLong(16)
        val r = (value shr 16) and 0xFF
        val g = (value shr 8) and 0xFF
        val b = value and 0xFF
        return r > 225 && g > 225 && b > 225
    }
}
