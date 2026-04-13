package com.sphereon.conf.theme.web

import kotlin.test.Test
import kotlin.test.assertEquals

class CssTokenMapperTest {

    @Test
    fun tokenKeyToCssVarConvertsDots() {
        assertEquals("--color-primary", CssTokenMapper.tokenKeyToCssVar("color.primary"))
        assertEquals("--typography-displayLarge-fontSize", CssTokenMapper.tokenKeyToCssVar("typography.displayLarge.fontSize"))
    }

    @Test
    fun convertUnitConvertsSpAndDp() {
        assertEquals("16px", CssTokenMapper.convertUnit("16sp"))
        assertEquals("12px", CssTokenMapper.convertUnit("12dp"))
        assertEquals("-0.25px", CssTokenMapper.convertUnit("-0.25sp"))
        assertEquals("cubic-bezier(0.2, 0.0, 0, 1.0)", CssTokenMapper.convertUnit("cubic-bezier(0.2, 0.0, 0, 1.0)"))
        assertEquals("#FF0000", CssTokenMapper.convertUnit("#FF0000"))
    }

    @Test
    fun tokensToCssVarsConvertsAll() {
        val tokens = mapOf(
            "color.primary" to "#6750A4",
            "typography.bodyLarge.fontSize" to "16sp",
        )
        val result = CssTokenMapper.tokensToCssVars(tokens)
        assertEquals("#6750A4", result["--color-primary"])
        assertEquals("16px", result["--typography-bodyLarge-fontSize"])
    }

    @Test
    fun hexToRgbaConvertsCorrectly() {
        assertEquals("rgba(103, 80, 164, 0.1)", CssTokenMapper.hexToRgba("#6750A4", 0.1))
    }

    @Test
    fun shadowTokensPassThroughWithoutConversion() {
        val tokens = mapOf(
            "shadow.elevation.sm" to "0 1px 3px 0 rgba(0,0,0,0.1), 0 1px 2px -1px rgba(0,0,0,0.1)",
            "shadow.state.focus" to "0 0 0 3px rgba(103,80,164,0.4)",
            "shadow.elevation.none" to "none",
        )
        val result = CssTokenMapper.tokensToCssVars(tokens)
        // Shadow values should NOT have unit conversion applied
        assertEquals("0 1px 3px 0 rgba(0,0,0,0.1), 0 1px 2px -1px rgba(0,0,0,0.1)", result["--shadow-elevation-sm"])
        assertEquals("0 0 0 3px rgba(103,80,164,0.4)", result["--shadow-state-focus"])
        assertEquals("none", result["--shadow-elevation-none"])
    }

    @Test
    fun spacingTokensGetUnitConversion() {
        val tokens = mapOf(
            "spacing.4" to "16px",
            "shape.radius.md" to "8dp",
        )
        val result = CssTokenMapper.tokensToCssVars(tokens)
        assertEquals("16px", result["--spacing-4"])
        assertEquals("8px", result["--shape-radius-md"])
    }
}
