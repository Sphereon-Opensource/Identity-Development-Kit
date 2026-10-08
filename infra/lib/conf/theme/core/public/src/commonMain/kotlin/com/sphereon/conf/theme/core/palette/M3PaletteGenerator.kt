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

import com.sphereon.conf.theme.core.model.ExtendedThemePalette
import com.sphereon.conf.theme.core.model.ThemePalette
import com.sphereon.core.compat.JsExportCompat

// Generates a full Material Design 3 palette from a single seed color.
// Produces 5 tonal palettes: primary, secondary, tertiary, neutral, and error.

// M3 chroma targets
private const val CHROMA_BRAND_MIN = 48.0
private const val CHROMA_SECONDARY = 16.0
private const val CHROMA_TERTIARY = 24.0
private const val CHROMA_NEUTRAL = 4.0
private const val HUE_ERROR = 25.0
private const val CHROMA_ERROR = 84.0
private const val HUE_SUCCESS = 145.0
private const val CHROMA_SUCCESS = 60.0
private const val HUE_WARNING = 85.0
private const val CHROMA_WARNING = 70.0
private const val HUE_INFO = 250.0
private const val CHROMA_INFO = 50.0
private const val TERTIARY_HUE_OFFSET = 60.0
private const val HUE_FULL_CIRCLE = 360.0

@JsExportCompat
object M3PaletteGenerator {
    /**
     * Generate a full M3 palette from a seed hex color.
     *
     * @param seedHex Hex color string (e.g. "#6750A4")
     * @return ThemePalette with all 5 role palettes
     */
    fun generate(seedHex: String): ThemePalette {
        val seed = HctColor.fromHex(seedHex)

        // Primary: same hue, full chroma
        val primary = TonalPalette(seed.hue, maxOf(seed.chroma, CHROMA_BRAND_MIN))

        // Secondary: same hue, reduced chroma
        val secondary = TonalPalette(seed.hue, CHROMA_SECONDARY)

        // Tertiary: rotated hue by 60 degrees, moderate chroma
        val tertiaryHue = (seed.hue + TERTIARY_HUE_OFFSET) % HUE_FULL_CIRCLE
        val tertiary = TonalPalette(tertiaryHue, CHROMA_TERTIARY)

        // Neutral: same hue, very low chroma
        val neutral = TonalPalette(seed.hue, CHROMA_NEUTRAL)

        // Error: fixed red hue
        val error = TonalPalette(HUE_ERROR, CHROMA_ERROR)

        return ThemePalette(
            seedColor = seedHex,
            primary = primary.toResult(),
            secondary = secondary.toResult(),
            tertiary = tertiary.toResult(),
            neutral = neutral.toResult(),
            error = error.toResult(),
        )
    }

    /**
     * Generate an M3 palette from multiple seed colors.
     *
     * @param primarySeed Primary seed hex color
     * @param secondarySeed Optional secondary seed (defaults to primary hue, 16.0 chroma)
     * @param tertiarySeed Optional tertiary seed (defaults to primary hue + 60°, 24.0 chroma)
     * @param neutralSeed Optional neutral seed (defaults to primary hue, 4.0 chroma)
     */
    fun generateFromSeeds(
        primarySeed: String,
        secondarySeed: String? = null,
        tertiarySeed: String? = null,
        neutralSeed: String? = null,
    ): ThemePalette {
        val primaryHct = HctColor.fromHex(primarySeed)
        val primary = TonalPalette(primaryHct.hue, maxOf(primaryHct.chroma, CHROMA_BRAND_MIN))

        val secondary =
            if (secondarySeed != null) {
                val hct = HctColor.fromHex(secondarySeed)
                TonalPalette(hct.hue, maxOf(hct.chroma, CHROMA_SECONDARY))
            } else {
                TonalPalette(primaryHct.hue, CHROMA_SECONDARY)
            }

        val tertiary =
            if (tertiarySeed != null) {
                val hct = HctColor.fromHex(tertiarySeed)
                TonalPalette(hct.hue, maxOf(hct.chroma, CHROMA_TERTIARY))
            } else {
                val tertiaryHue = (primaryHct.hue + TERTIARY_HUE_OFFSET) % HUE_FULL_CIRCLE
                TonalPalette(tertiaryHue, CHROMA_TERTIARY)
            }

        val neutral =
            if (neutralSeed != null) {
                val hct = HctColor.fromHex(neutralSeed)
                TonalPalette(hct.hue, maxOf(hct.chroma, CHROMA_NEUTRAL))
            } else {
                TonalPalette(primaryHct.hue, CHROMA_NEUTRAL)
            }

        val error = TonalPalette(HUE_ERROR, CHROMA_ERROR)

        return ThemePalette(
            seedColor = primarySeed,
            primary = primary.toResult(),
            secondary = secondary.toResult(),
            tertiary = tertiary.toResult(),
            neutral = neutral.toResult(),
            error = error.toResult(),
        )
    }

    /**
     * Generate an extended palette that includes success, warning, and info utility palettes
     * in addition to the standard M3 5-palette set.
     *
     * @param seedHex Hex color string (e.g. "#6750A4")
     * @return ExtendedThemePalette with 8 role palettes
     */
    fun generateExtended(seedHex: String): ExtendedThemePalette {
        val base = generate(seedHex)

        // Success: fixed green hue (145°), moderate chroma
        val success = TonalPalette(HUE_SUCCESS, CHROMA_SUCCESS)

        // Warning: fixed amber hue (85 deg), high chroma
        val warning = TonalPalette(HUE_WARNING, CHROMA_WARNING)

        // Info: fixed blue hue (250 deg), moderate chroma
        val info = TonalPalette(HUE_INFO, CHROMA_INFO)

        return ExtendedThemePalette(
            base = base,
            success = success.toResult(),
            warning = warning.toResult(),
            info = info.toResult(),
        )
    }
}
