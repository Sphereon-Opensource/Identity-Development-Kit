package com.sphereon.conf.theme.core.defaults

import com.sphereon.conf.theme.core.token.TokenFlattener
import com.sphereon.conf.theme.core.model.ThemeDefinition
import com.sphereon.conf.theme.core.token.TokenReferenceResolver
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * WCAG AAA conformance for the two high-contrast themes.
 *
 * Nothing measured these before. The light high-contrast theme shipped unguarded, the dark one did
 * not exist upstream at all, and the only check on either lived in a consuming repository. A
 * contrast regression therefore reached a consumer rather than being stopped at source, which is
 * the gap this file closes.
 *
 * The bar here is AAA 7:1 for text, not the AA 4.5 the base themes are held to. That difference is
 * the whole point of the variants: a base theme clearing 4.5 is correct, and a high-contrast theme
 * clearing only 4.5 is not.
 */
class HighContrastConformanceTest {
    private companion object {
        const val AAA_TEXT = 7.0
        const val NON_TEXT = 3.0

        /**
         * Foreground and background pairs that must clear the text bar. Pinned by name and count:
         * an assertion that iterates whatever it finds reports success on an empty list, which is
         * the failure shape this suite exists to prevent rather than reproduce.
         */
        val TEXT_PAIRS =
            listOf(
                "color.onSurface" to "color.surface",
                "color.onBackground" to "color.background",
                "color.onSurfaceVariant" to "color.surfaceVariant",
                "color.onPrimary" to "color.primary",
                "color.onPrimaryContainer" to "color.primaryContainer",
                "color.onSecondary" to "color.secondary",
                "color.onSecondaryContainer" to "color.secondaryContainer",
                "color.onTertiary" to "color.tertiary",
                "color.onTertiaryContainer" to "color.tertiaryContainer",
                "color.onError" to "color.error",
                "color.onErrorContainer" to "color.errorContainer",
                "color.feedback.onSuccess" to "color.feedback.success",
                "color.feedback.onWarning" to "color.feedback.warning",
                "color.feedback.onInfo" to "color.feedback.info",
                "color.text.primary" to "color.surface",
                "color.text.secondary" to "color.surface",
                "color.primary" to "color.surface",
                "color.secondary" to "color.surface",
                "color.inverseOnSurface" to "color.inverseSurface",
            )

        const val EXPECTED_TEXT_PAIRS = 19

        val NON_TEXT_PAIRS =
            listOf(
                "color.border.default" to "color.surface",
                "color.border.subtle" to "color.surface",
                "color.outline" to "color.surface",
            )

        const val EXPECTED_NON_TEXT_PAIRS = 3
    }

    private fun resolved(definition: ThemeDefinition): Map<String, String> =
        TokenReferenceResolver.resolve(TokenFlattener.merge(listOf(definition)))

    private fun channel(component: Int): Double {
        val c = component / 255.0
        return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(hex: String): Double {
        val r = channel(hex.substring(1, 3).toInt(16))
        val g = channel(hex.substring(3, 5).toInt(16))
        val b = channel(hex.substring(5, 7).toInt(16))
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun contrast(
        a: String,
        b: String,
    ): Double {
        val la = luminance(a)
        val lb = luminance(b)
        val hi = maxOf(la, lb)
        val lo = minOf(la, lb)
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun isHex(value: String?): Boolean = value != null && Regex("^#[0-9a-fA-F]{6}$").matches(value)

    private fun assertPairs(
        themeName: String,
        definition: ThemeDefinition,
        pairs: List<Pair<String, String>>,
        floor: Double,
    ) {
        val tokens = resolved(definition)
        if (tokens.isEmpty()) fail("$themeName resolved to an empty token map, so nothing below could have failed")
        for ((fgKey, bgKey) in pairs) {
            val fg = tokens[fgKey]
            val bg = tokens[bgKey]
            // Asserted rather than skipped. A token that stops resolving to a hex value stops being
            // measurable, and a pair that quietly stops being measured reads as a passing pair.
            if (!isHex(fg)) fail("$themeName: $fgKey did not resolve to a hex value, got: $fg")
            if (!isHex(bg)) fail("$themeName: $bgKey did not resolve to a hex value, got: $bg")
            val ratio = contrast(fg!!, bg!!)
            assertTrue(
                ratio >= floor,
                "$themeName: $fgKey($fg) on $bgKey($bg) = ${(ratio * 100).toInt() / 100.0}, needs >= $floor",
            )
        }
    }

    @Test
    fun pairListsAreFullyPinned() {
        assertTrue(TEXT_PAIRS.size == EXPECTED_TEXT_PAIRS, "text pair list changed size, update EXPECTED_TEXT_PAIRS deliberately")
        assertTrue(NON_TEXT_PAIRS.size == EXPECTED_NON_TEXT_PAIRS, "non-text pair list changed size, update EXPECTED_NON_TEXT_PAIRS deliberately")
    }

    @Test
    fun highContrastLightMeetsAaaText() {
        assertPairs("high-contrast light", SystemDefaults.baselineHighContrastLight, TEXT_PAIRS, AAA_TEXT)
    }

    @Test
    fun highContrastDarkMeetsAaaText() {
        assertPairs("high-contrast dark", SystemDefaults.baselineHighContrastDark, TEXT_PAIRS, AAA_TEXT)
    }

    @Test
    fun highContrastLightMeetsNonTextFloor() {
        assertPairs("high-contrast light", SystemDefaults.baselineHighContrastLight, NON_TEXT_PAIRS, NON_TEXT)
    }

    @Test
    fun highContrastDarkMeetsNonTextFloor() {
        assertPairs("high-contrast dark", SystemDefaults.baselineHighContrastDark, NON_TEXT_PAIRS, NON_TEXT)
    }

    @Test
    fun theTwoHighContrastGroundsAreOpposite() {
        // The variants invert rather than mirror. Pointing both at one definition, or copying the
        // light values into dark, would satisfy every threshold above while shipping black text on
        // a black ground for anyone using the dark variant.
        val light = resolved(SystemDefaults.baselineHighContrastLight)
        val dark = resolved(SystemDefaults.baselineHighContrastDark)
        assertTrue(light["color.surface"] == "#FFFFFF", "high-contrast light surface should be pure white, was ${light["color.surface"]}")
        assertTrue(dark["color.surface"] == "#000000", "high-contrast dark surface should be pure black, was ${dark["color.surface"]}")
        assertTrue(
            luminance(light["color.onSurface"]!!) < luminance(dark["color.onSurface"]!!),
            "the light variant's ink should be darker than the dark variant's",
        )
    }
}
