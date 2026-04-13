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

package com.sphereon.conf.theme.core.token

import com.sphereon.conf.theme.core.defaults.SystemDefaults
import com.sphereon.conf.theme.core.model.CssPolicyConfig
import com.sphereon.conf.theme.core.model.ThemeDefinition
import com.sphereon.conf.theme.core.model.ThemeScope
import com.sphereon.conf.theme.core.validation.ThemeValidator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Cross-platform parity tests for token processing pipeline.
 * Ensures JVM and other targets produce identical results.
 */
class TokenProcessingParityTest {
    // ===== Token Reference Resolution =====

    @Test
    fun simpleReferenceResolution() {
        val tokens = mapOf("a" to "#FF0000", "b" to "{a}")
        val resolved = TokenReferenceResolver.resolve(tokens)
        assertEquals("#FF0000", resolved["b"])
    }

    @Test
    fun chainedReferenceResolution() {
        val tokens = mapOf("a" to "#FF0000", "b" to "{a}", "c" to "{b}")
        val resolved = TokenReferenceResolver.resolve(tokens)
        assertEquals("#FF0000", resolved["c"])
    }

    @Test
    fun deepChainResolution() {
        val tokens = mapOf("a" to "val", "b" to "{a}", "c" to "{b}", "d" to "{c}", "e" to "{d}")
        val resolved = TokenReferenceResolver.resolve(tokens)
        assertEquals("val", resolved["e"])
    }

    @Test
    fun cycleDetectionShouldNotThrow() {
        val tokens = mapOf("a" to "{b}", "b" to "{a}")
        val resolved = TokenReferenceResolver.resolve(tokens)
        // Circular references should be left unresolved
        assertTrue(resolved["a"]!!.contains("{"))
        assertTrue(resolved["b"]!!.contains("{"))
    }

    @Test
    fun selfReferenceDetection() {
        val tokens = mapOf("x" to "{x}")
        val resolved = TokenReferenceResolver.resolve(tokens)
        assertTrue(resolved["x"]!!.contains("{"))
    }

    @Test
    fun unknownReferencePreserved() {
        val tokens = mapOf("a" to "{nonexistent}")
        val resolved = TokenReferenceResolver.resolve(tokens)
        assertEquals("{nonexistent}", resolved["a"])
    }

    @Test
    fun mixedResolvedAndUnresolvedReferences() {
        val tokens = mapOf("color" to "#00FF00", "ref" to "{color}", "bad" to "{missing}")
        val resolved = TokenReferenceResolver.resolve(tokens)
        assertEquals("#00FF00", resolved["ref"])
        assertEquals("{missing}", resolved["bad"])
    }

    @Test
    fun nonReferenceValuesPassThrough() {
        val tokens = mapOf("a" to "plain text", "b" to "16sp", "c" to "#ABCDEF")
        val resolved = TokenReferenceResolver.resolve(tokens)
        assertEquals("plain text", resolved["a"])
        assertEquals("16sp", resolved["b"])
        assertEquals("#ABCDEF", resolved["c"])
    }

    // ===== Token Flattening =====

    @Test
    fun flatteningLayerPrecedence() {
        val system =
            ThemeDefinition(
                id = "sys",
                name = "System",
                scope = ThemeScope.SYSTEM,
                tokens =
                    buildTokens {
                        color("color.primary", "#111111")
                        color("color.secondary", "#222222")
                    },
            )
        val tenant =
            ThemeDefinition(
                id = "tenant",
                name = "Tenant",
                scope = ThemeScope.TENANT,
                tokens = buildTokens { color("color.primary", "#AAAAAA") },
            )
        val merged = TokenFlattener.merge(listOf(system, tenant))
        assertEquals("#AAAAAA", merged["color.primary"])
        assertEquals("#222222", merged["color.secondary"])
    }

    @Test
    fun flatteningPreservesAllKeysFromAllLayers() {
        val layer1 =
            ThemeDefinition(
                id = "l1",
                name = "L1",
                scope = ThemeScope.SYSTEM,
                tokens = buildTokens { color("a", "#111") },
            )
        val layer2 =
            ThemeDefinition(
                id = "l2",
                name = "L2",
                scope = ThemeScope.APP,
                tokens = buildTokens { color("b", "#222") },
            )
        val layer3 =
            ThemeDefinition(
                id = "l3",
                name = "L3",
                scope = ThemeScope.TENANT,
                tokens = buildTokens { color("c", "#333") },
            )
        val merged = TokenFlattener.merge(listOf(layer1, layer2, layer3))
        assertEquals(3, merged.size)
        assertEquals("#111", merged["a"])
        assertEquals("#222", merged["b"])
        assertEquals("#333", merged["c"])
    }

    @Test
    fun systemDefaultsBaselineTokenCount() {
        val tokens = TokenFlattener.merge(listOf(SystemDefaults.baseline))
        // Should have all color tokens (29) + elevation (6) + typography generic (2) + typography styles (15*5=75) + shape (5)
        // + motion durations (12) + motion easings (7) + motion theme-transition (2) + responsive scales (5) = 143
        assertTrue(tokens.size >= 140, "Baseline should have at least 140 tokens, got ${tokens.size}")
        assertTrue(tokens.containsKey(TokenKeyConstants.COLOR_PRIMARY))
        assertTrue(tokens.containsKey(TokenKeyConstants.ELEVATION_MD))
        assertTrue(tokens.containsKey(TokenKeyConstants.TYPOGRAPHY_DISPLAY_LARGE_FONT_SIZE))
        assertTrue(tokens.containsKey(TokenKeyConstants.TYPOGRAPHY_BODY_SMALL_LETTER_SPACING))
        assertTrue(tokens.containsKey(TokenKeyConstants.TYPOGRAPHY_FONT_FAMILY))
    }

    @Test
    fun systemDefaultsDarkTokenCount() {
        val lightTokens = TokenFlattener.merge(listOf(SystemDefaults.baseline))
        val darkTokens = TokenFlattener.merge(listOf(SystemDefaults.baselineDark))
        // Light and dark should have the same number of tokens
        assertEquals(
            lightTokens.size,
            darkTokens.size,
            "Light and dark baselines should have the same token count",
        )
    }

    @Test
    fun systemDefaultsHighContrastTokenCount() {
        val lightTokens = TokenFlattener.merge(listOf(SystemDefaults.baseline))
        val highContrastTokens = TokenFlattener.merge(listOf(SystemDefaults.baselineHighContrast))
        assertEquals(
            lightTokens.size,
            highContrastTokens.size,
            "High contrast baseline should have the same token count as light (got ${highContrastTokens.size} vs ${lightTokens.size})",
        )
    }

    @Test
    fun systemDefaultsLightDarkColorDifference() {
        val light = TokenFlattener.merge(listOf(SystemDefaults.baseline))
        val dark = TokenFlattener.merge(listOf(SystemDefaults.baselineDark))
        // Colors should differ between light and dark
        assertTrue(light[TokenKeyConstants.COLOR_PRIMARY] != dark[TokenKeyConstants.COLOR_PRIMARY])
        assertTrue(light[TokenKeyConstants.COLOR_SURFACE] != dark[TokenKeyConstants.COLOR_SURFACE])
        // Typography should be the same
        assertEquals(
            light[TokenKeyConstants.TYPOGRAPHY_DISPLAY_LARGE_FONT_SIZE],
            dark[TokenKeyConstants.TYPOGRAPHY_DISPLAY_LARGE_FONT_SIZE],
        )
    }

    @Test
    fun emptyLayerList() {
        val merged = TokenFlattener.merge(emptyList())
        assertTrue(merged.isEmpty())
    }

    @Test
    fun singleLayer() {
        val def =
            ThemeDefinition(
                id = "one",
                name = "One",
                scope = ThemeScope.SYSTEM,
                tokens = buildTokens { color("x", "#FFF") },
            )
        val merged = TokenFlattener.merge(listOf(def))
        assertEquals(1, merged.size)
        assertEquals("#FFF", merged["x"])
    }

    @Test
    fun systemDefaultsMotionTokensPresent() {
        val tokens = TokenFlattener.merge(listOf(SystemDefaults.baseline))
        assertTrue(tokens.containsKey(TokenKeyConstants.MOTION_DURATION_SHORT1))
        assertTrue(tokens.containsKey(TokenKeyConstants.MOTION_DURATION_LONG4))
        assertTrue(tokens.containsKey(TokenKeyConstants.MOTION_EASING_STANDARD))
        assertTrue(tokens.containsKey(TokenKeyConstants.MOTION_EASING_LINEAR))
        assertTrue(tokens.containsKey(TokenKeyConstants.MOTION_THEME_TRANSITION_DURATION))
        assertTrue(tokens.containsKey(TokenKeyConstants.MOTION_THEME_TRANSITION_EASING))
    }

    @Test
    fun systemDefaultsResponsiveTokensPresent() {
        val tokens = TokenFlattener.merge(listOf(SystemDefaults.baseline))
        assertTrue(tokens.containsKey(TokenKeyConstants.RESPONSIVE_SCALE_MEDIUM_DISPLAY))
        assertTrue(tokens.containsKey(TokenKeyConstants.RESPONSIVE_SCALE_MEDIUM_HEADLINE))
        assertTrue(tokens.containsKey(TokenKeyConstants.RESPONSIVE_SCALE_EXPANDED_DISPLAY))
        assertTrue(tokens.containsKey(TokenKeyConstants.RESPONSIVE_SCALE_EXPANDED_HEADLINE))
        assertTrue(tokens.containsKey(TokenKeyConstants.RESPONSIVE_SCALE_EXPANDED_TITLE))
    }

    @Test
    fun systemDefaultsMotionTokensPresentInAllVariants() {
        val light = TokenFlattener.merge(listOf(SystemDefaults.baseline))
        val dark = TokenFlattener.merge(listOf(SystemDefaults.baselineDark))
        val highContrast = TokenFlattener.merge(listOf(SystemDefaults.baselineHighContrast))
        // All variants should have identical motion duration values
        assertEquals(light[TokenKeyConstants.MOTION_DURATION_MEDIUM2], dark[TokenKeyConstants.MOTION_DURATION_MEDIUM2])
        assertEquals(light[TokenKeyConstants.MOTION_DURATION_MEDIUM2], highContrast[TokenKeyConstants.MOTION_DURATION_MEDIUM2])
        // All variants should have identical easing values
        assertEquals(light[TokenKeyConstants.MOTION_EASING_STANDARD], dark[TokenKeyConstants.MOTION_EASING_STANDARD])
        assertEquals(light[TokenKeyConstants.MOTION_EASING_STANDARD], highContrast[TokenKeyConstants.MOTION_EASING_STANDARD])
    }

    // ===== CSS Variable Generation Input/Output =====

    @Test
    fun resolvedTokensAreConsistentAcrossRuns() {
        val tokens = TokenFlattener.merge(listOf(SystemDefaults.baseline))
        val resolved1 = TokenReferenceResolver.resolve(tokens)
        val resolved2 = TokenReferenceResolver.resolve(tokens)
        assertEquals(resolved1, resolved2, "Token resolution should be deterministic")
    }

    @Test
    fun resolvedTokensSortedHashIsDeterministic() {
        val tokens = TokenFlattener.merge(listOf(SystemDefaults.baseline))
        val resolved = TokenReferenceResolver.resolve(tokens)
        val hash1 =
            resolved.entries
                .sortedBy { it.key }
                .map { it.key to it.value }
                .hashCode()
        val hash2 =
            resolved.entries
                .sortedBy { it.key }
                .map { it.key to it.value }
                .hashCode()
        assertEquals(hash1, hash2, "Hash of sorted token map should be deterministic")
    }

    // ===== CSS Policy Validation =====

    @Test
    fun cssPolicyAcceptsCleanCss() {
        val css = ":root { --color-primary: #6750A4; }"
        val errors = ThemeValidator.validateCustomCss(css)
        assertTrue(errors.isEmpty(), "Clean CSS should pass validation: $errors")
    }

    @Test
    fun cssPolicyRejectsExpression() {
        val css = "div { width: expression(document.body.clientWidth / 2); }"
        val errors = ThemeValidator.validateCustomCss(css)
        assertTrue(errors.any { it.contains("expression") })
    }

    @Test
    fun cssPolicyRejectsJavascriptUrl() {
        val css = "a { background: url(javascript:alert(1)); }"
        val errors = ThemeValidator.validateCustomCss(css)
        assertTrue(errors.any { it.contains("javascript") })
    }

    @Test
    fun cssPolicyRejectsImport() {
        val css = "@import url('https://evil.com/style.css');"
        val errors = ThemeValidator.validateCustomCss(css)
        assertTrue(errors.any { it.contains("@import") })
    }

    @Test
    fun cssPolicyRejectsBlockedSelectors() {
        val css = "<script>alert(1)</script>"
        val errors = ThemeValidator.validateCustomCss(css)
        assertTrue(errors.any { it.contains("script") })
    }

    @Test
    fun cssPolicyRejectsOversizedCss() {
        val largeCss = "a".repeat(60_000)
        val errors = ThemeValidator.validateCustomCss(largeCss)
        assertTrue(errors.any { it.contains("maximum size") })
    }

    @Test
    fun cssPolicyAllowlistFiltering() {
        val css = "div { color: red; margin: 10px; }"
        val policy = CssPolicyConfig(allowedProperties = setOf("color"))
        val errors = ThemeValidator.validateCustomCss(css, policy)
        assertTrue(errors.any { it.contains("margin") })
    }

    // ===== Design System Token Extensions =====

    @Test
    fun systemDefaultsSpacingTokensPresent() {
        val tokens = TokenFlattener.merge(listOf(SystemDefaults.baseline))
        assertTrue(tokens.containsKey(TokenKeyConstants.SPACING_0))
        assertTrue(tokens.containsKey(TokenKeyConstants.SPACING_4))
        assertTrue(tokens.containsKey(TokenKeyConstants.SPACING_48))
        // Semantic spacing
        assertTrue(tokens.containsKey(TokenKeyConstants.SPACING_INLINE_MD))
        assertTrue(tokens.containsKey(TokenKeyConstants.SPACING_STACK_LG))
        assertTrue(tokens.containsKey(TokenKeyConstants.SPACING_INSET_XS))
    }

    @Test
    fun systemDefaultsBorderWidthTokensPresent() {
        val tokens = TokenFlattener.merge(listOf(SystemDefaults.baseline))
        assertTrue(tokens.containsKey(TokenKeyConstants.BORDER_WIDTH_NONE))
        assertTrue(tokens.containsKey(TokenKeyConstants.BORDER_WIDTH_THIN))
        assertTrue(tokens.containsKey(TokenKeyConstants.BORDER_WIDTH_MEDIUM))
        assertTrue(tokens.containsKey(TokenKeyConstants.BORDER_WIDTH_THICK))
    }

    @Test
    fun systemDefaultsShadowTokensPresent() {
        val tokens = TokenFlattener.merge(listOf(SystemDefaults.baseline))
        assertTrue(tokens.containsKey(TokenKeyConstants.SHADOW_ELEVATION_NONE))
        assertTrue(tokens.containsKey(TokenKeyConstants.SHADOW_ELEVATION_SM))
        assertTrue(tokens.containsKey(TokenKeyConstants.SHADOW_ELEVATION_XXL))
        // Semantic
        assertTrue(tokens.containsKey(TokenKeyConstants.SHADOW_SUBTLE))
        assertTrue(tokens.containsKey(TokenKeyConstants.SHADOW_FLOATING))
        // State
        assertTrue(tokens.containsKey(TokenKeyConstants.SHADOW_STATE_FOCUS))
        assertTrue(tokens.containsKey(TokenKeyConstants.SHADOW_STATE_ERROR))
    }

    @Test
    fun systemDefaultsShapeRadiusTokensPresent() {
        val tokens = TokenFlattener.merge(listOf(SystemDefaults.baseline))
        assertTrue(tokens.containsKey(TokenKeyConstants.SHAPE_RADIUS_NONE))
        assertTrue(tokens.containsKey(TokenKeyConstants.SHAPE_RADIUS_SM))
        assertTrue(tokens.containsKey(TokenKeyConstants.SHAPE_RADIUS_FULL))
    }

    @Test
    fun systemDefaultsExtendedTypographyPresent() {
        val tokens = TokenFlattener.merge(listOf(SystemDefaults.baseline))
        assertTrue(tokens.containsKey(TokenKeyConstants.TYPOGRAPHY_DISPLAY_EXTRA_LARGE_FONT_SIZE))
        assertTrue(tokens.containsKey(TokenKeyConstants.TYPOGRAPHY_DISPLAY_EXTRA_LARGE_FONT_WEIGHT))
        assertTrue(tokens.containsKey(TokenKeyConstants.TYPOGRAPHY_BODY_EXTRA_SMALL_FONT_SIZE))
        assertTrue(tokens.containsKey(TokenKeyConstants.TYPOGRAPHY_BODY_EXTRA_SMALL_LINE_HEIGHT))
    }

    @Test
    fun systemDefaultsSemanticColorTokensPresent() {
        val tokens = TokenFlattener.merge(listOf(SystemDefaults.baseline))
        assertTrue(tokens.containsKey(TokenKeyConstants.COLOR_INTERACTIVE_HOVER))
        assertTrue(tokens.containsKey(TokenKeyConstants.COLOR_INTERACTIVE_DISABLED))
        assertTrue(tokens.containsKey(TokenKeyConstants.COLOR_FEEDBACK_SUCCESS))
        assertTrue(tokens.containsKey(TokenKeyConstants.COLOR_FEEDBACK_WARNING_CONTAINER))
        assertTrue(tokens.containsKey(TokenKeyConstants.COLOR_FEEDBACK_INFO))
        assertTrue(tokens.containsKey(TokenKeyConstants.COLOR_TEXT_PRIMARY))
        assertTrue(tokens.containsKey(TokenKeyConstants.COLOR_TEXT_DISABLED))
        assertTrue(tokens.containsKey(TokenKeyConstants.COLOR_BORDER_DEFAULT))
        assertTrue(tokens.containsKey(TokenKeyConstants.COLOR_BORDER_SUBTLE))
    }

    @Test
    fun systemDefaultsExtendedMotionTokensPresent() {
        val tokens = TokenFlattener.merge(listOf(SystemDefaults.baseline))
        assertTrue(tokens.containsKey(TokenKeyConstants.MOTION_DURATION_INSTANT))
        assertTrue(tokens.containsKey(TokenKeyConstants.MOTION_DURATION_FAST))
        assertTrue(tokens.containsKey(TokenKeyConstants.MOTION_DURATION_NORMAL))
        assertTrue(tokens.containsKey(TokenKeyConstants.MOTION_DURATION_SLOW))
        assertTrue(tokens.containsKey(TokenKeyConstants.MOTION_DURATION_SLOWER))
        assertTrue(tokens.containsKey(TokenKeyConstants.MOTION_DELAY_NONE))
        assertTrue(tokens.containsKey(TokenKeyConstants.MOTION_DELAY_SHORT))
        assertTrue(tokens.containsKey(TokenKeyConstants.MOTION_EASING_BOUNCE))
    }

    @Test
    fun systemDefaultsNewTokensPresentInAllVariants() {
        val light = TokenFlattener.merge(listOf(SystemDefaults.baseline))
        val dark = TokenFlattener.merge(listOf(SystemDefaults.baselineDark))
        val hc = TokenFlattener.merge(listOf(SystemDefaults.baselineHighContrast))
        // All should have spacing
        assertTrue(dark.containsKey(TokenKeyConstants.SPACING_4))
        assertTrue(hc.containsKey(TokenKeyConstants.SPACING_4))
        // All should have shadows
        assertTrue(dark.containsKey(TokenKeyConstants.SHADOW_ELEVATION_MD))
        assertTrue(hc.containsKey(TokenKeyConstants.SHADOW_ELEVATION_MD))
        // All should have semantic colors
        assertTrue(dark.containsKey(TokenKeyConstants.COLOR_FEEDBACK_SUCCESS))
        assertTrue(hc.containsKey(TokenKeyConstants.COLOR_FEEDBACK_SUCCESS))
    }

    @Test
    fun semanticSpacingReferencesResolveCorrectly() {
        val tokens = TokenFlattener.merge(listOf(SystemDefaults.baseline))
        val resolved = TokenReferenceResolver.resolve(tokens)
        // spacing.inline.xs references {spacing.1} which is 4px
        assertEquals("4px", resolved[TokenKeyConstants.SPACING_INLINE_XS])
        // spacing.inline.md references {spacing.4} which is 16px
        assertEquals("16px", resolved[TokenKeyConstants.SPACING_INLINE_MD])
        // spacing.stack.xl references {spacing.8} which is 32px
        assertEquals("32px", resolved[TokenKeyConstants.SPACING_STACK_XL])
    }

    @Test
    fun semanticShadowReferencesResolveCorrectly() {
        val tokens = TokenFlattener.merge(listOf(SystemDefaults.baseline))
        val resolved = TokenReferenceResolver.resolve(tokens)
        // shadow.subtle references {shadow.elevation.xs}
        assertEquals(resolved[TokenKeyConstants.SHADOW_ELEVATION_XS], resolved[TokenKeyConstants.SHADOW_SUBTLE])
        // shadow.floating references {shadow.elevation.lg}
        assertEquals(resolved[TokenKeyConstants.SHADOW_ELEVATION_LG], resolved[TokenKeyConstants.SHADOW_FLOATING])
    }

    @Test
    fun semanticColorReferencesResolveCorrectly() {
        val tokens = TokenFlattener.merge(listOf(SystemDefaults.baseline))
        val resolved = TokenReferenceResolver.resolve(tokens)
        // color.text.primary references {color.onSurface}
        assertEquals(resolved[TokenKeyConstants.COLOR_ON_SURFACE], resolved[TokenKeyConstants.COLOR_TEXT_PRIMARY])
        // color.border.default references {color.outline}
        assertEquals(resolved[TokenKeyConstants.COLOR_OUTLINE], resolved[TokenKeyConstants.COLOR_BORDER_DEFAULT])
    }

    @Test
    fun lightShadowsAreVisible() {
        val tokens = TokenFlattener.merge(listOf(SystemDefaults.baseline))
        val resolved = TokenReferenceResolver.resolve(tokens)
        // Light theme shadows should NOT be "none" (except elevation.none)
        assertEquals("none", resolved[TokenKeyConstants.SHADOW_ELEVATION_NONE])
        assertTrue(
            resolved[TokenKeyConstants.SHADOW_ELEVATION_XS] != "none",
            "Light shadow.elevation.xs should be visible, not none",
        )
        assertTrue(
            resolved[TokenKeyConstants.SHADOW_ELEVATION_MD]!!.contains("rgba"),
            "Light shadow.elevation.md should contain rgba values",
        )
    }

    @Test
    fun darkShadowsHaveHigherOpacity() {
        val light = TokenFlattener.merge(listOf(SystemDefaults.baseline))
        val dark = TokenFlattener.merge(listOf(SystemDefaults.baselineDark))
        // Dark shadows should differ from light (higher opacity)
        assertTrue(
            light[TokenKeyConstants.SHADOW_ELEVATION_SM] != dark[TokenKeyConstants.SHADOW_ELEVATION_SM],
            "Dark theme shadows should have different opacity than light",
        )
    }
}
