package com.sphereon.conf.theme.web

import com.sphereon.conf.theme.core.model.ThemeVariant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WebSystemDefaultsTest {

    @Test
    fun lightDefaultsContainAllColorTokens() {
        val tokens = WebSystemDefaults.light
        assertNotNull(tokens["color.primary"])
        assertNotNull(tokens["color.onPrimary"])
        assertNotNull(tokens["color.surface"])
        assertNotNull(tokens["color.background"])
        assertEquals("#6750A4", tokens["color.primary"])
    }

    @Test
    fun darkDefaultsContainAllColorTokens() {
        val tokens = WebSystemDefaults.dark
        assertNotNull(tokens["color.primary"])
        assertEquals("#D0BCFF", tokens["color.primary"])
    }

    @Test
    fun forVariantReturnsCorrectMap() {
        assertEquals(WebSystemDefaults.light, WebSystemDefaults.forVariant(ThemeVariant.LIGHT))
        assertEquals(WebSystemDefaults.dark, WebSystemDefaults.forVariant(ThemeVariant.DARK))
        assertEquals(WebSystemDefaults.highContrast, WebSystemDefaults.forVariant(ThemeVariant.HIGH_CONTRAST))
    }

    @Test
    fun forVariantStringReturnsCorrectMap() {
        assertEquals(WebSystemDefaults.light, WebSystemDefaults.forVariantString("light"))
        assertEquals(WebSystemDefaults.dark, WebSystemDefaults.forVariantString("dark"))
        assertEquals(WebSystemDefaults.highContrast, WebSystemDefaults.forVariantString("high_contrast"))
    }

    @Test
    fun defaultsContainTypographyAndShapeTokens() {
        val tokens = WebSystemDefaults.light
        assertNotNull(tokens["typography.fontFamily"])
        assertNotNull(tokens["shape.cornerMedium"])
        assertNotNull(tokens["motion.duration.short1"])
        assertTrue(tokens.size > 100)
    }
}
