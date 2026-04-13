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

import com.sphereon.conf.theme.core.model.ThemeVariant
import com.sphereon.core.compat.JsExportCompat

// Central resolver: [ThemeColorConfig] → flat token map.
//
// Output always includes:
// - **Tier 0**: `palette.brand.50` through `palette.brand.900` (and all provided scales)
// - **Tier 2**: `color.primary`, `color.onPrimary`, etc. (mapped from palette or M3)

// M3 design system chroma targets for palette generation
private const val CHROMA_BRAND_MIN = 48.0
private const val CHROMA_SECONDARY = 16.0
private const val CHROMA_NEUTRAL = 4.0

// Semantic color hue/chroma pairs (M3 defaults)
private const val HUE_ERROR = 25.0
private const val CHROMA_ERROR = 84.0
private const val HUE_SUCCESS = 145.0
private const val CHROMA_SUCCESS = 60.0
private const val HUE_WARNING = 85.0
private const val CHROMA_WARNING = 70.0
private const val HUE_INFO = 250.0
private const val CHROMA_INFO = 50.0

// Tertiary hue offset from primary
private const val TERTIARY_HUE_OFFSET = 60.0
private const val HUE_FULL_CIRCLE = 360.0

@JsExportCompat
object DesignSystemPaletteResolver {
    /**
     * Resolve a [ThemeColorConfig] into a flat map of token key → hex color value.
     *
     * @param config The color configuration (seed, multi-seed, explicit, or hybrid)
     * @param variant Light or dark theme variant
     * @param mapping Optional custom palette-to-token mapping. Defaults to [DefaultPaletteMapping.DEFAULT].
     */
    fun resolve(
        config: ThemeColorConfig,
        variant: ThemeVariant,
        mapping: PaletteMapping = DefaultPaletteMapping.DEFAULT,
    ): Map<String, String> =
        when (config) {
            is ThemeColorConfig.SeedColor -> resolveSeed(config.seed, variant, mapping)
            is ThemeColorConfig.MultiSeed -> resolveMultiSeed(config, variant, mapping)
            is ThemeColorConfig.ExplicitPalettes -> resolveExplicit(config.palettes, variant, mapping)
            is ThemeColorConfig.Hybrid -> resolveHybrid(config, variant, mapping)
        }

    private fun resolveSeed(
        seed: String,
        variant: ThemeVariant,
        mapping: PaletteMapping,
    ): Map<String, String> {
        val generator = M3PaletteGenerator.generateExtended(seed)
        val base = generator.base

        // Synthesize PaletteScales from M3 tonal palettes
        val brandScale =
            PaletteScale.fromTonalPalette(
                TonalPalette.fromHex(seed).let {
                    TonalPalette(it.hue, maxOf(it.chroma, CHROMA_BRAND_MIN))
                },
            )
        val secondaryScale =
            PaletteScale.fromTonalPalette(
                TonalPalette.fromHex(seed).let {
                    TonalPalette(it.hue, CHROMA_SECONDARY)
                },
            )
        val neutralScale =
            PaletteScale.fromTonalPalette(
                TonalPalette.fromHex(seed).let {
                    TonalPalette(it.hue, CHROMA_NEUTRAL)
                },
            )
        val errorScale = PaletteScale.fromTonalPalette(TonalPalette(HUE_ERROR, CHROMA_ERROR))
        val successScale = PaletteScale.fromTonalPalette(TonalPalette(HUE_SUCCESS, CHROMA_SUCCESS))
        val warningScale = PaletteScale.fromTonalPalette(TonalPalette(HUE_WARNING, CHROMA_WARNING))
        val infoScale = PaletteScale.fromTonalPalette(TonalPalette(HUE_INFO, CHROMA_INFO))

        val palette =
            DesignSystemPalette(
                brand = brandScale,
                secondary = secondaryScale,
                neutral = neutralScale,
                error = errorScale,
                success = successScale,
                warning = warningScale,
                info = infoScale,
            )

        return buildTokenMap(palette, variant, mapping)
    }

    private fun resolveMultiSeed(
        config: ThemeColorConfig.MultiSeed,
        variant: ThemeVariant,
        mapping: PaletteMapping,
    ): Map<String, String> {
        val primaryHct = HctColor.fromHex(config.primary)
        val brandScale =
            PaletteScale.fromTonalPalette(
                TonalPalette(primaryHct.hue, maxOf(primaryHct.chroma, CHROMA_BRAND_MIN)),
            )

        val secondaryScale =
            if (config.secondary != null) {
                val hct = HctColor.fromHex(config.secondary)
                PaletteScale.fromTonalPalette(TonalPalette(hct.hue, maxOf(hct.chroma, CHROMA_SECONDARY)))
            } else {
                PaletteScale.fromTonalPalette(TonalPalette(primaryHct.hue, CHROMA_SECONDARY))
            }

        val neutralScale =
            if (config.neutral != null) {
                val hct = HctColor.fromHex(config.neutral)
                PaletteScale.fromTonalPalette(TonalPalette(hct.hue, maxOf(hct.chroma, CHROMA_NEUTRAL)))
            } else {
                PaletteScale.fromTonalPalette(TonalPalette(primaryHct.hue, CHROMA_NEUTRAL))
            }

        val errorScale = PaletteScale.fromTonalPalette(TonalPalette(HUE_ERROR, CHROMA_ERROR))
        val successScale = PaletteScale.fromTonalPalette(TonalPalette(HUE_SUCCESS, CHROMA_SUCCESS))
        val warningScale = PaletteScale.fromTonalPalette(TonalPalette(HUE_WARNING, CHROMA_WARNING))
        val infoScale = PaletteScale.fromTonalPalette(TonalPalette(HUE_INFO, CHROMA_INFO))

        val palette =
            DesignSystemPalette(
                brand = brandScale,
                secondary = secondaryScale,
                neutral = neutralScale,
                error = errorScale,
                success = successScale,
                warning = warningScale,
                info = infoScale,
            )

        return buildTokenMap(palette, variant, mapping)
    }

    private fun resolveExplicit(
        palettes: DesignSystemPalette,
        variant: ThemeVariant,
        mapping: PaletteMapping,
    ): Map<String, String> = buildTokenMap(palettes, variant, mapping)

    private fun resolveHybrid(
        config: ThemeColorConfig.Hybrid,
        variant: ThemeVariant,
        mapping: PaletteMapping,
    ): Map<String, String> {
        val seed = config.fallbackSeed ?: config.palettes.brand.s500
        val hct = HctColor.fromHex(seed)

        // Generate M3 fallbacks for missing roles
        val fallbackSecondary = PaletteScale.fromTonalPalette(TonalPalette(hct.hue, CHROMA_SECONDARY))
        val fallbackNeutral = PaletteScale.fromTonalPalette(TonalPalette(hct.hue, CHROMA_NEUTRAL))
        val fallbackError = PaletteScale.fromTonalPalette(TonalPalette(HUE_ERROR, CHROMA_ERROR))
        val fallbackSuccess = PaletteScale.fromTonalPalette(TonalPalette(HUE_SUCCESS, CHROMA_SUCCESS))
        val fallbackWarning = PaletteScale.fromTonalPalette(TonalPalette(HUE_WARNING, CHROMA_WARNING))
        val fallbackInfo = PaletteScale.fromTonalPalette(TonalPalette(HUE_INFO, CHROMA_INFO))

        val merged =
            DesignSystemPalette(
                brand = config.palettes.brand,
                secondary = config.palettes.secondary ?: fallbackSecondary,
                neutral = config.palettes.neutral ?: fallbackNeutral,
                error = config.palettes.error ?: fallbackError,
                success = config.palettes.success ?: fallbackSuccess,
                warning = config.palettes.warning ?: fallbackWarning,
                info = config.palettes.info ?: fallbackInfo,
                pending = config.palettes.pending,
            )

        return buildTokenMap(merged, variant, mapping)
    }

    /**
     * Build the complete token map from a [DesignSystemPalette].
     * Emits both Tier 0 palette primitives and Tier 2 semantic tokens.
     */
    private fun buildTokenMap(
        palette: DesignSystemPalette,
        variant: ThemeVariant,
        mapping: PaletteMapping,
    ): Map<String, String> {
        val tokens = mutableMapOf<String, String>()

        // Tier 0: Emit palette.{role}.{stop} primitives
        emitPaletteTokens(tokens, "brand", palette.brand)
        palette.secondary?.let { emitPaletteTokens(tokens, "secondary", it) }
        palette.neutral?.let { emitPaletteTokens(tokens, "neutral", it) }
        palette.error?.let { emitPaletteTokens(tokens, "error", it) }
        palette.success?.let { emitPaletteTokens(tokens, "success", it) }
        palette.warning?.let { emitPaletteTokens(tokens, "warning", it) }
        palette.info?.let { emitPaletteTokens(tokens, "info", it) }
        palette.pending?.let { emitPaletteTokens(tokens, "pending", it) }

        // Tier 2: Map palette stops to M3 semantic tokens via PaletteMapping
        val mappingForVariant =
            if (variant == ThemeVariant.DARK) {
                mapping.dark
            } else {
                mapping.light
            }
        for ((tokenKey, ref) in mappingForVariant) {
            val scale = palette.getScale(ref.scale) ?: continue
            tokens[tokenKey] = scale[ref.stop]
        }

        // Fixed tokens
        tokens["color.scrim"] = "#000000"
        tokens["color.shadow"] = "#000000"

        return tokens
    }

    private fun emitPaletteTokens(
        tokens: MutableMap<String, String>,
        role: String,
        scale: PaletteScale,
    ) {
        for (stop in PaletteScale.STOPS) {
            tokens["palette.$role.$stop"] = scale[stop]
        }
    }
}
