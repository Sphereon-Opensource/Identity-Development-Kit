package com.sphereon.conf.theme.core.palette

import com.sphereon.conf.theme.core.model.ThemeVariant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DesignSystemPaletteResolverTest {

    @Test
    fun seedModeShouldProduceAllPalettePrimitives() {
        val config = ThemeColorConfig.SeedColor("#6750A4")
        val tokens = DesignSystemPaletteResolver.resolve(config, ThemeVariant.LIGHT)

        // Tier 0: palette primitives should be present
        for (stop in PaletteScale.STOPS) {
            assertNotNull(tokens["palette.brand.$stop"], "Missing palette.brand.$stop")
            assertNotNull(tokens["palette.secondary.$stop"], "Missing palette.secondary.$stop")
            assertNotNull(tokens["palette.neutral.$stop"], "Missing palette.neutral.$stop")
            assertNotNull(tokens["palette.error.$stop"], "Missing palette.error.$stop")
        }

        // Tier 2: M3 semantic tokens should be present
        assertNotNull(tokens["color.primary"], "Missing color.primary")
        assertNotNull(tokens["color.onPrimary"], "Missing color.onPrimary")
        assertNotNull(tokens["color.surface"], "Missing color.surface")
    }

    @Test
    fun seedModeShouldBeDeterministic() {
        val config = ThemeColorConfig.SeedColor("#7C40E8")
        val tokens1 = DesignSystemPaletteResolver.resolve(config, ThemeVariant.LIGHT)
        val tokens2 = DesignSystemPaletteResolver.resolve(config, ThemeVariant.LIGHT)
        assertEquals(tokens1, tokens2)
    }

    @Test
    fun multiSeedShouldUseProvidedSeeds() {
        val config = ThemeColorConfig.MultiSeed(
            primary = "#7C40E8",
            secondary = "#FF5722",
            neutral = "#9E9E9E",
        )
        val tokens = DesignSystemPaletteResolver.resolve(config, ThemeVariant.LIGHT)

        assertNotNull(tokens["palette.brand.500"])
        assertNotNull(tokens["palette.secondary.500"])
        assertNotNull(tokens["palette.neutral.500"])
        assertNotNull(tokens["color.primary"])
        assertNotNull(tokens["color.secondary"])
    }

    @Test
    fun explicitModeShouldUseExactColors() {
        val brandScale = PaletteScale(
            s50 = "#ECE4FC", s100 = "#E0D2FA", s200 = "#C7ADF5",
            s300 = "#AE89F1", s400 = "#9564EC", s500 = "#7C40E8",
            s600 = "#5D1AD6", s700 = "#4714A4", s800 = "#320E72",
            s900 = "#1C0840",
        )
        val config = ThemeColorConfig.ExplicitPalettes(
            palettes = DesignSystemPalette(brand = brandScale)
        )
        val tokens = DesignSystemPaletteResolver.resolve(config, ThemeVariant.LIGHT)

        // Tier 0 should have exact values
        assertEquals("#ECE4FC", tokens["palette.brand.50"])
        assertEquals("#7C40E8", tokens["palette.brand.500"])
        assertEquals("#1C0840", tokens["palette.brand.900"])

        // Tier 2: color.primary should come from brand.500 (per default light mapping)
        assertEquals("#7C40E8", tokens["color.primary"])
    }

    @Test
    fun hybridModeShouldFallbackForMissingScales() {
        val brandScale = PaletteScale(
            s50 = "#ECE4FC", s100 = "#E0D2FA", s200 = "#C7ADF5",
            s300 = "#AE89F1", s400 = "#9564EC", s500 = "#7C40E8",
            s600 = "#5D1AD6", s700 = "#4714A4", s800 = "#320E72",
            s900 = "#1C0840",
        )
        val config = ThemeColorConfig.Hybrid(
            palettes = DesignSystemPalette(brand = brandScale)
        )
        val tokens = DesignSystemPaletteResolver.resolve(config, ThemeVariant.LIGHT)

        // Brand should use exact values
        assertEquals("#7C40E8", tokens["palette.brand.500"])

        // Secondary/neutral should be M3-generated (not null)
        assertNotNull(tokens["palette.secondary.500"], "Hybrid should generate secondary")
        assertNotNull(tokens["palette.neutral.500"], "Hybrid should generate neutral")
        assertNotNull(tokens["color.surface"], "Hybrid should map surface from neutral")
    }

    @Test
    fun hybridModeShouldUseFallbackSeed() {
        val brandScale = PaletteScale(
            s50 = "#ECE4FC", s100 = "#E0D2FA", s200 = "#C7ADF5",
            s300 = "#AE89F1", s400 = "#9564EC", s500 = "#7C40E8",
            s600 = "#5D1AD6", s700 = "#4714A4", s800 = "#320E72",
            s900 = "#1C0840",
        )
        val withoutFallback = ThemeColorConfig.Hybrid(
            palettes = DesignSystemPalette(brand = brandScale)
        )
        val withFallback = ThemeColorConfig.Hybrid(
            palettes = DesignSystemPalette(brand = brandScale),
            fallbackSeed = "#FF0000",
        )
        val tokens1 = DesignSystemPaletteResolver.resolve(withoutFallback, ThemeVariant.LIGHT)
        val tokens2 = DesignSystemPaletteResolver.resolve(withFallback, ThemeVariant.LIGHT)

        // Different fallback seeds should produce different neutrals
        assertTrue(tokens1["palette.neutral.500"] != tokens2["palette.neutral.500"],
            "Different fallback seeds should produce different neutral palettes")
    }

    @Test
    fun darkVariantShouldUseDarkMapping() {
        val brandScale = PaletteScale(
            s50 = "#ECE4FC", s100 = "#E0D2FA", s200 = "#C7ADF5",
            s300 = "#AE89F1", s400 = "#9564EC", s500 = "#7C40E8",
            s600 = "#5D1AD6", s700 = "#4714A4", s800 = "#320E72",
            s900 = "#1C0840",
        )
        val config = ThemeColorConfig.ExplicitPalettes(
            palettes = DesignSystemPalette(brand = brandScale)
        )
        val lightTokens = DesignSystemPaletteResolver.resolve(config, ThemeVariant.LIGHT)
        val darkTokens = DesignSystemPaletteResolver.resolve(config, ThemeVariant.DARK)

        // Light: color.primary should be brand.500, Dark: brand.400
        assertEquals("#7C40E8", lightTokens["color.primary"])
        assertEquals("#9564EC", darkTokens["color.primary"])
    }

    @Test
    fun allTokenValuesShouldBeValidHex() {
        val config = ThemeColorConfig.SeedColor("#1565C0")
        val tokens = DesignSystemPaletteResolver.resolve(config, ThemeVariant.LIGHT)

        for ((key, value) in tokens) {
            assertTrue(value.startsWith("#"), "Token $key has non-hex value: $value")
            val hex = value.removePrefix("#")
            assertTrue(hex.length == 6 || hex.length == 8,
                "Token $key has invalid hex length: $value")
        }
    }

    @Test
    fun fixedTokensShouldBePresent() {
        val config = ThemeColorConfig.SeedColor("#6750A4")
        val tokens = DesignSystemPaletteResolver.resolve(config, ThemeVariant.LIGHT)
        assertEquals("#000000", tokens["color.scrim"])
        assertEquals("#000000", tokens["color.shadow"])
    }
}
