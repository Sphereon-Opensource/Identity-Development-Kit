package com.sphereon.conf.theme.core.palette

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Configuration for how theme colors are sourced.
 *
 * Supports four input modes:
 * - [SeedColor]: Single hex color, fully algorithmic M3 generation
 * - [MultiSeed]: Per-role seed colors, still algorithmic
 * - [ExplicitPalettes]: Full design system palette with hand-crafted 50–900 scales
 * - [Hybrid]: Explicit scales where provided, M3 fallback for missing roles
 */
@JsExportCompat
@Serializable
sealed class ThemeColorConfig {

    /**
     * Single seed color — quick prototyping mode.
     * Generates all palettes algorithmically via M3 HCT.
     */
    @Serializable
    @SerialName("seed")
    data class SeedColor(val seed: String) : ThemeColorConfig()

    /**
     * Per-role seed colors for more control while still using algorithmic generation.
     */
    @Serializable
    @SerialName("multi-seed")
    data class MultiSeed(
        val primary: String,
        val secondary: String? = null,
        val tertiary: String? = null,
        val neutral: String? = null,
    ) : ThemeColorConfig()

    /**
     * Full explicit design system palette — pixel-perfect brand colors from Figma.
     */
    @Serializable
    @SerialName("explicit")
    data class ExplicitPalettes(val palettes: DesignSystemPalette) : ThemeColorConfig()

    /**
     * Hybrid mode — explicit scales where provided, M3 fallback for missing roles.
     * Most common: brand palette from Figma, neutral/error generated from M3.
     *
     * @property fallbackSeed Seed for M3 generation of missing roles. Defaults to brand.s500.
     */
    @Serializable
    @SerialName("hybrid")
    data class Hybrid(
        val palettes: DesignSystemPalette,
        val fallbackSeed: String? = null,
    ) : ThemeColorConfig()
}
