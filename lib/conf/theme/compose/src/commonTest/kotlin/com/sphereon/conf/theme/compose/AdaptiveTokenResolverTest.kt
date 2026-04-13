package com.sphereon.conf.theme.compose

import com.sphereon.conf.theme.core.defaults.SystemDefaults
import com.sphereon.conf.theme.core.model.ResolvedTheme
import com.sphereon.conf.theme.core.token.TokenFlattener
import com.sphereon.conf.theme.core.token.TokenKeyConstants
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class AdaptiveTokenResolverTest {

    private fun baselineTheme(): ResolvedTheme {
        val tokens = TokenFlattener.merge(listOf(SystemDefaults.baseline))
        return ResolvedTheme(tokens = tokens, resolvedAt = Clock.System.now())
    }

    @Test
    fun compactReturnsUnchangedTheme() {
        val theme = baselineTheme()
        val result = AdaptiveTokenResolver.resolve(theme, WindowWidthSizeClass.Compact)
        assertEquals(theme, result)
    }

    @Test
    fun mediumScalesDisplayAndHeadline() {
        val theme = baselineTheme()
        val result = AdaptiveTokenResolver.resolve(theme, WindowWidthSizeClass.Medium)
        // Display large fontSize was "57sp", should be scaled by 1.10 = ~62.7sp -> "62.7sp"
        val original = theme.tokens["typography.displayLarge.fontSize"]!!
        val scaled = result.tokens["typography.displayLarge.fontSize"]!!
        assertNotEquals(original, scaled)
        // Title should NOT be scaled for Medium
        assertEquals(
            theme.tokens["typography.titleLarge.fontSize"],
            result.tokens["typography.titleLarge.fontSize"]
        )
    }

    @Test
    fun expandedScalesDisplayHeadlineAndTitle() {
        val theme = baselineTheme()
        val result = AdaptiveTokenResolver.resolve(theme, WindowWidthSizeClass.Expanded)
        // All three categories should be scaled
        assertNotEquals(
            theme.tokens["typography.displayLarge.fontSize"],
            result.tokens["typography.displayLarge.fontSize"]
        )
        assertNotEquals(
            theme.tokens["typography.headlineLarge.fontSize"],
            result.tokens["typography.headlineLarge.fontSize"]
        )
        assertNotEquals(
            theme.tokens["typography.titleLarge.fontSize"],
            result.tokens["typography.titleLarge.fontSize"]
        )
    }

    @Test
    fun customScaleFactorsFromTokens() {
        val tokens = TokenFlattener.merge(listOf(SystemDefaults.baseline)).toMutableMap()
        // Override the scale factors
        tokens[TokenKeyConstants.RESPONSIVE_SCALE_MEDIUM_DISPLAY] = "1.50"
        tokens[TokenKeyConstants.RESPONSIVE_SCALE_MEDIUM_HEADLINE] = "1.25"
        val theme = ResolvedTheme(tokens = tokens, resolvedAt = Clock.System.now())

        val result = AdaptiveTokenResolver.resolve(theme, WindowWidthSizeClass.Medium)
        // Display large fontSize "57sp" * 1.50 = 85.5sp
        assertEquals("85.5sp", result.tokens["typography.displayLarge.fontSize"])
        // Headline large fontSize "32sp" * 1.25 = 40sp
        assertEquals("40sp", result.tokens["typography.headlineLarge.fontSize"])
    }

    @Test
    fun scaleSpValueFormatsCorrectly() {
        assertEquals("62.7sp", AdaptiveTokenResolver.scaleSpValue("57sp", 1.10f))
        assertEquals("36sp", AdaptiveTokenResolver.scaleSpValue("36sp", 1.0f))
        assertEquals("0sp", AdaptiveTokenResolver.scaleSpValue("0sp", 1.5f))
    }

    @Test
    fun scaleSpValueHandlesNonScalable() {
        assertEquals("bold", AdaptiveTokenResolver.scaleSpValue("bold", 1.1f))
        assertEquals("Roboto", AdaptiveTokenResolver.scaleSpValue("Roboto", 1.1f))
    }

    @Test
    fun nonTypographyTokensUnchanged() {
        val theme = baselineTheme()
        val result = AdaptiveTokenResolver.resolve(theme, WindowWidthSizeClass.Expanded)
        // Color tokens should be unchanged
        assertEquals(
            theme.tokens[TokenKeyConstants.COLOR_PRIMARY],
            result.tokens[TokenKeyConstants.COLOR_PRIMARY]
        )
        // Elevation tokens should be unchanged
        assertEquals(
            theme.tokens[TokenKeyConstants.ELEVATION_MD],
            result.tokens[TokenKeyConstants.ELEVATION_MD]
        )
    }
}
