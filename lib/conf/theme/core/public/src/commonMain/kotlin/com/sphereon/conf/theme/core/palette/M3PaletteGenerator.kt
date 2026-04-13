package com.sphereon.conf.theme.core.palette

import com.sphereon.conf.theme.core.model.ExtendedThemePalette
import com.sphereon.conf.theme.core.model.ThemePalette
import com.sphereon.core.compat.JsExportCompat

/**
 * Generates a full Material Design 3 palette from a single seed color.
 * Produces 5 tonal palettes: primary, secondary, tertiary, neutral, and error.
 */
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
        val primary = TonalPalette(seed.hue, maxOf(seed.chroma, 48.0))

        // Secondary: same hue, reduced chroma
        val secondary = TonalPalette(seed.hue, 16.0)

        // Tertiary: rotated hue by 60 degrees, moderate chroma
        val tertiaryHue = (seed.hue + 60.0) % 360.0
        val tertiary = TonalPalette(tertiaryHue, 24.0)

        // Neutral: same hue, very low chroma
        val neutral = TonalPalette(seed.hue, 4.0)

        // Error: fixed red hue
        val error = TonalPalette(25.0, 84.0)

        return ThemePalette(
            seedColor = seedHex,
            primary = primary.toResult(),
            secondary = secondary.toResult(),
            tertiary = tertiary.toResult(),
            neutral = neutral.toResult(),
            error = error.toResult()
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
        val primary = TonalPalette(primaryHct.hue, maxOf(primaryHct.chroma, 48.0))

        val secondary = if (secondarySeed != null) {
            val hct = HctColor.fromHex(secondarySeed)
            TonalPalette(hct.hue, maxOf(hct.chroma, 16.0))
        } else {
            TonalPalette(primaryHct.hue, 16.0)
        }

        val tertiary = if (tertiarySeed != null) {
            val hct = HctColor.fromHex(tertiarySeed)
            TonalPalette(hct.hue, maxOf(hct.chroma, 24.0))
        } else {
            val tertiaryHue = (primaryHct.hue + 60.0) % 360.0
            TonalPalette(tertiaryHue, 24.0)
        }

        val neutral = if (neutralSeed != null) {
            val hct = HctColor.fromHex(neutralSeed)
            TonalPalette(hct.hue, maxOf(hct.chroma, 4.0))
        } else {
            TonalPalette(primaryHct.hue, 4.0)
        }

        val error = TonalPalette(25.0, 84.0)

        return ThemePalette(
            seedColor = primarySeed,
            primary = primary.toResult(),
            secondary = secondary.toResult(),
            tertiary = tertiary.toResult(),
            neutral = neutral.toResult(),
            error = error.toResult()
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
        val success = TonalPalette(145.0, 60.0)

        // Warning: fixed amber hue (85°), high chroma
        val warning = TonalPalette(85.0, 70.0)

        // Info: fixed blue hue (250°), moderate chroma
        val info = TonalPalette(250.0, 50.0)

        return ExtendedThemePalette(
            base = base,
            success = success.toResult(),
            warning = warning.toResult(),
            info = info.toResult()
        )
    }
}
