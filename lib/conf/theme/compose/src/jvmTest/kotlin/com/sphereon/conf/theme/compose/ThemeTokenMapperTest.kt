package com.sphereon.conf.theme.compose

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.sphereon.conf.theme.core.defaults.SystemDefaults
import com.sphereon.conf.theme.core.model.ResolvedTheme
import com.sphereon.conf.theme.core.model.ThemeVariant
import com.sphereon.conf.theme.core.token.TokenFlattener
import com.sphereon.conf.theme.core.token.TokenKeyConstants
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ThemeTokenMapperTest {

    private fun resolvedTheme(
        tokens: Map<String, String> = emptyMap(),
        variant: ThemeVariant? = null
    ) = ResolvedTheme(
        tokens = tokens,
        resolvedAt = Clock.System.now(),
        variant = variant
    )

    @Test
    fun toColorSchemeWithSystemDefaults() {
        val tokens = TokenFlattener.merge(listOf(SystemDefaults.baseline))
        val theme = resolvedTheme(tokens, ThemeVariant.LIGHT)
        val colorScheme = ThemeTokenMapper.toColorScheme(theme)

        // System defaults map to valid colors (not unspecified)
        assertNotEquals(Color.Unspecified, colorScheme.primary)
        assertNotEquals(Color.Unspecified, colorScheme.secondary)
        assertNotEquals(Color.Unspecified, colorScheme.surface)
        // Primary parsed from hex #6750A4
        assertEquals(Color(red = 103, green = 80, blue = 164), colorScheme.primary)
    }

    @Test
    fun toColorSchemeEmptyTokensFallsBackToDefaults() {
        val theme = resolvedTheme(variant = ThemeVariant.LIGHT)
        val colorScheme = ThemeTokenMapper.toColorScheme(theme)

        // With no tokens, should return default lightColorScheme
        assertEquals(lightColorScheme().primary, colorScheme.primary)
        assertEquals(lightColorScheme().secondary, colorScheme.secondary)
        assertEquals(lightColorScheme().surface, colorScheme.surface)
    }

    @Test
    fun toColorSchemeDarkVariantUsesDarkBase() {
        val theme = resolvedTheme(variant = ThemeVariant.DARK)
        val colorScheme = ThemeTokenMapper.toColorScheme(theme)

        assertEquals(darkColorScheme().primary, colorScheme.primary)
        assertEquals(darkColorScheme().surface, colorScheme.surface)
    }

    @Test
    fun toColorSchemeLightVariantUsesLightBase() {
        val theme = resolvedTheme(variant = ThemeVariant.LIGHT)
        val colorScheme = ThemeTokenMapper.toColorScheme(theme)

        assertEquals(lightColorScheme().primary, colorScheme.primary)
    }

    @Test
    fun toColorSchemeParses6DigitHex() {
        val tokens = mapOf(TokenKeyConstants.COLOR_PRIMARY to "#FF0000")
        val theme = resolvedTheme(tokens, ThemeVariant.LIGHT)
        val colorScheme = ThemeTokenMapper.toColorScheme(theme)

        assertEquals(Color(red = 255, green = 0, blue = 0), colorScheme.primary)
    }

    @Test
    fun toColorSchemeParses8DigitHexWithAlpha() {
        val tokens = mapOf(TokenKeyConstants.COLOR_PRIMARY to "#80FF0000")
        val theme = resolvedTheme(tokens, ThemeVariant.LIGHT)
        val colorScheme = ThemeTokenMapper.toColorScheme(theme)

        assertEquals(Color(alpha = 128, red = 255, green = 0, blue = 0), colorScheme.primary)
    }

    @Test
    fun toColorSchemeInvalidHexFallsBackToDefault() {
        val tokens = mapOf(TokenKeyConstants.COLOR_PRIMARY to "not-a-color")
        val theme = resolvedTheme(tokens, ThemeVariant.LIGHT)
        val colorScheme = ThemeTokenMapper.toColorScheme(theme)

        assertEquals(lightColorScheme().primary, colorScheme.primary)
    }

    @Test
    fun toColorSchemeMapsAllContainerRoles() {
        val tokens = mapOf(
            TokenKeyConstants.COLOR_SURFACE_CONTAINER to "#111111",
            TokenKeyConstants.COLOR_SURFACE_CONTAINER_HIGH to "#222222",
            TokenKeyConstants.COLOR_SURFACE_CONTAINER_HIGHEST to "#333333",
            TokenKeyConstants.COLOR_SURFACE_CONTAINER_LOW to "#444444",
            TokenKeyConstants.COLOR_SURFACE_CONTAINER_LOWEST to "#555555"
        )
        val theme = resolvedTheme(tokens, ThemeVariant.LIGHT)
        val colorScheme = ThemeTokenMapper.toColorScheme(theme)

        assertNotEquals(lightColorScheme().surfaceContainer, colorScheme.surfaceContainer)
        assertNotEquals(lightColorScheme().surfaceContainerHigh, colorScheme.surfaceContainerHigh)
        assertNotEquals(lightColorScheme().surfaceContainerHighest, colorScheme.surfaceContainerHighest)
        assertNotEquals(lightColorScheme().surfaceContainerLow, colorScheme.surfaceContainerLow)
        assertNotEquals(lightColorScheme().surfaceContainerLowest, colorScheme.surfaceContainerLowest)
    }
}
