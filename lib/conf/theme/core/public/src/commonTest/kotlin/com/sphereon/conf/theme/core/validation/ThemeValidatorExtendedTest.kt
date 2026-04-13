package com.sphereon.conf.theme.core.validation

import com.sphereon.conf.theme.core.model.ThemeToken
import com.sphereon.conf.theme.core.model.ThemeTokenType
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThemeValidatorExtendedTest {

    @Test
    fun validShadowNone() {
        val token = ThemeToken("shadow.elevation.none", "none", ThemeTokenType.SHADOW)
        val result = ThemeValidator.validateToken(token)
        assertTrue(result.valid, "Shadow 'none' should be valid: ${result.errors}")
    }

    @Test
    fun validShadowSingleLayer() {
        val token = ThemeToken("shadow.elevation.xs", "0 1px 2px 0 rgba(0,0,0,0.05)", ThemeTokenType.SHADOW)
        val result = ThemeValidator.validateToken(token)
        assertTrue(result.valid, "Single-layer shadow should be valid: ${result.errors}")
    }

    @Test
    fun validShadowMultiLayer() {
        val token = ThemeToken("shadow.elevation.sm", "0 1px 3px 0 rgba(0,0,0,0.1), 0 1px 2px -1px rgba(0,0,0,0.1)", ThemeTokenType.SHADOW)
        val result = ThemeValidator.validateToken(token)
        assertTrue(result.valid, "Multi-layer shadow should be valid: ${result.errors}")
    }

    @Test
    fun validShadowReference() {
        val token = ThemeToken("shadow.subtle", "{shadow.elevation.xs}", ThemeTokenType.SHADOW)
        val result = ThemeValidator.validateToken(token)
        assertTrue(result.valid, "Shadow reference should be valid: ${result.errors}")
    }

    @Test
    fun invalidShadowValue() {
        val token = ThemeToken("shadow.test", "not-a-shadow", ThemeTokenType.SHADOW)
        val result = ThemeValidator.validateToken(token)
        assertFalse(result.valid, "Invalid shadow value should fail validation")
    }

    @Test
    fun validSpacingValue() {
        val token = ThemeToken("spacing.4", "16px", ThemeTokenType.SPACING)
        val result = ThemeValidator.validateToken(token)
        assertTrue(result.valid, "Spacing '16px' should be valid: ${result.errors}")
    }

    @Test
    fun validSpacingReference() {
        val token = ThemeToken("spacing.inline.md", "{spacing.4}", ThemeTokenType.SPACING)
        val result = ThemeValidator.validateToken(token)
        assertTrue(result.valid, "Spacing reference should be valid: ${result.errors}")
    }

    @Test
    fun validBorderWidthValue() {
        val token = ThemeToken("borderWidth.thin", "1px", ThemeTokenType.BORDER_WIDTH)
        val result = ThemeValidator.validateToken(token)
        assertTrue(result.valid, "Border width '1px' should be valid: ${result.errors}")
    }

    @Test
    fun validBorderWidthZero() {
        val token = ThemeToken("borderWidth.none", "0px", ThemeTokenType.BORDER_WIDTH)
        val result = ThemeValidator.validateToken(token)
        assertTrue(result.valid, "Border width '0px' should be valid: ${result.errors}")
    }

    @Test
    fun invalidSpacingValue() {
        val token = ThemeToken("spacing.test", "invalid", ThemeTokenType.SPACING)
        val result = ThemeValidator.validateToken(token)
        assertFalse(result.valid, "Invalid spacing value should fail validation")
    }
}
