package com.sphereon.conf.theme.web

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class CssLegacyAliasesTest {

    @Test
    fun generatesLegacyColorAliases() {
        val tokens = mapOf(
            "color.primary" to "#6750A4",
            "color.onSurface" to "#1D1B20",
            "color.background" to "#FEF7FF",
            "color.outline" to "#79747E",
        )
        val aliases = CssLegacyAliases.generateAliases(tokens)
        assertEquals("#6750A4", aliases["--color-primary"])
        assertEquals("#1D1B20", aliases["--color-foreground"])
        assertEquals("#FEF7FF", aliases["--color-background"])
    }

    @Test
    fun generatesShapeAliases() {
        val tokens = mapOf(
            "shape.cornerExtraSmall" to "4dp",
            "shape.cornerSmall" to "8dp",
        )
        val aliases = CssLegacyAliases.generateAliases(tokens)
        assertEquals("4px", aliases["--radius-sm"])
        assertEquals("8px", aliases["--radius-md"])
    }

    @Test
    fun generatesSemanticAliases() {
        val tokens = mapOf(
            "color.primary" to "#6750A4",
            "color.onSurface" to "#1D1B20",
            "color.surfaceContainer" to "#F3EDF7",
        )
        val aliases = CssLegacyAliases.generateAliases(tokens)
        assertNotNull(aliases["--color-primary-bg"])
        assertEquals("#6750A4", aliases["--color-text-link"])
        assertEquals("#1D1B20", aliases["--color-text-primary"])
        assertEquals("#F3EDF7", aliases["--color-bg-secondary"])
    }

    @Test
    fun generatesElevationToShadowAliases() {
        val tokens = mapOf(
            "shadow.elevation.none" to "none",
            "shadow.elevation.xs" to "0 1px 2px 0 rgba(0,0,0,0.05)",
            "shadow.elevation.sm" to "0 1px 3px 0 rgba(0,0,0,0.1)",
        )
        val aliases = CssLegacyAliases.generateAliases(tokens)
        assertEquals("none", aliases["--elevation-none"])
        assertEquals("0 1px 2px 0 rgba(0,0,0,0.05)", aliases["--elevation-xs"])
        assertEquals("0 1px 3px 0 rgba(0,0,0,0.1)", aliases["--elevation-sm"])
    }

    @Test
    fun generatesShapeRadiusAliases() {
        val tokens = mapOf(
            "shape.radius.sm" to "4dp",
            "shape.radius.md" to "8dp",
            "shape.radius.xxxl" to "28dp",
        )
        val aliases = CssLegacyAliases.generateAliases(tokens)
        assertEquals("4px", aliases["--shape-cornerExtraSmall"])
        assertEquals("8px", aliases["--shape-cornerSmall"])
        assertEquals("28px", aliases["--shape-cornerExtraLarge"])
    }
}
