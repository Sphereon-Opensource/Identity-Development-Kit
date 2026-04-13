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

package com.sphereon.conf.theme.compose

import com.sphereon.conf.theme.core.model.ResolvedTheme
import com.sphereon.conf.theme.core.token.TokenKeyConstants

/**
 * Algorithmically boosts contrast of existing theme tokens for high-contrast mode.
 *
 * Works with any palette (not just the defaults) by pushing foreground colors toward
 * pure white/black and surface/container colors toward extremes.
 */
object HighContrastTokenResolver {
    // Luminance coefficients (ITU-R BT.601)
    private const val LUMA_RED = 0.299
    private const val LUMA_GREEN = 0.587
    private const val LUMA_BLUE = 0.114
    private const val CHANNEL_MAX = 255.0

    // Lightness threshold for determining light vs dark
    private const val LIGHTNESS_THRESHOLD = 0.5

    // Color adjustment factors
    private const val SURFACE_PUSH_FACTOR = 0.7
    private const val SURFACE_DARKEN_FACTOR = 0.3
    private const val DARKEN_FACTOR = 0.4
    private const val CHANNEL_CLAMP_MAX = 255

    // Hex color parsing
    private const val HEX_LENGTH_RGB = 6
    private const val HEX_LENGTH_ARGB = 8
    private const val ARGB_OFFSET = 2
    private const val COMPONENT_LENGTH = 2
    private const val SUBSTRING_G_OFFSET = 4
    private const val SUBSTRING_B_OFFSET = 6
    private const val HEX_RADIX = 16

    private val foregroundKeys =
        setOf(
            TokenKeyConstants.COLOR_ON_PRIMARY,
            TokenKeyConstants.COLOR_ON_SECONDARY,
            TokenKeyConstants.COLOR_ON_TERTIARY,
            TokenKeyConstants.COLOR_ON_ERROR,
            TokenKeyConstants.COLOR_ON_PRIMARY_CONTAINER,
            TokenKeyConstants.COLOR_ON_SECONDARY_CONTAINER,
            TokenKeyConstants.COLOR_ON_TERTIARY_CONTAINER,
            TokenKeyConstants.COLOR_ON_ERROR_CONTAINER,
            TokenKeyConstants.COLOR_ON_SURFACE,
            TokenKeyConstants.COLOR_ON_SURFACE_VARIANT,
            TokenKeyConstants.COLOR_ON_BACKGROUND,
        )

    private val surfaceKeys =
        setOf(
            TokenKeyConstants.COLOR_SURFACE,
            TokenKeyConstants.COLOR_SURFACE_VARIANT,
            TokenKeyConstants.COLOR_SURFACE_CONTAINER,
            TokenKeyConstants.COLOR_SURFACE_CONTAINER_HIGH,
            TokenKeyConstants.COLOR_SURFACE_CONTAINER_HIGHEST,
            TokenKeyConstants.COLOR_SURFACE_CONTAINER_LOW,
            TokenKeyConstants.COLOR_SURFACE_CONTAINER_LOWEST,
            TokenKeyConstants.COLOR_BACKGROUND,
        )

    private val outlineKeys =
        setOf(
            TokenKeyConstants.COLOR_OUTLINE,
            TokenKeyConstants.COLOR_OUTLINE_VARIANT,
        )

    /**
     * Returns a new [ResolvedTheme] with contrast-boosted color tokens.
     */
    fun resolve(theme: ResolvedTheme): ResolvedTheme {
        val boosted = theme.tokens.toMutableMap()

        for ((key, value) in theme.tokens) {
            if (!value.startsWith("#")) {
                continue
            }

            when (key) {
                in foregroundKeys -> {
                    // Push foreground toward pure white or pure black
                    boosted[key] = pushToExtreme(value)
                }

                in surfaceKeys -> {
                    // Push surfaces toward pure white (light) or pure black (dark)
                    boosted[key] = pushSurfaceToExtreme(value)
                }

                in outlineKeys -> {
                    // Darken outlines for visibility
                    boosted[key] = darkenColor(value)
                }
            }
        }

        // Darken primary colors for better contrast on white
        darkenIfPresent(boosted, TokenKeyConstants.COLOR_PRIMARY)
        darkenIfPresent(boosted, TokenKeyConstants.COLOR_SECONDARY)
        darkenIfPresent(boosted, TokenKeyConstants.COLOR_TERTIARY)
        darkenIfPresent(boosted, TokenKeyConstants.COLOR_ERROR)

        return theme.copy(tokens = boosted)
    }

    private fun darkenIfPresent(
        tokens: MutableMap<String, String>,
        key: String,
    ) {
        val value = tokens[key] ?: return
        if (!value.startsWith("#")) {
            return
        }
        tokens[key] = darkenColor(value)
    }

    /**
     * Pushes a color toward pure white (#FFFFFF) or pure black (#000000)
     * based on its current lightness.
     */
    internal fun pushToExtreme(hex: String): String {
        val (r, g, b) = parseRgb(hex) ?: return hex
        val lightness = (r * LUMA_RED + g * LUMA_GREEN + b * LUMA_BLUE) / CHANNEL_MAX
        return if (lightness > LIGHTNESS_THRESHOLD) {
            "#FFFFFF"
        } else {
            "#000000"
        }
    }

    /**
     * Pushes surface colors toward extremes — light surfaces whiter, dark surfaces blacker.
     */
    internal fun pushSurfaceToExtreme(hex: String): String {
        val (r, g, b) = parseRgb(hex) ?: return hex
        val lightness = (r * LUMA_RED + g * LUMA_GREEN + b * LUMA_BLUE) / CHANNEL_MAX
        return if (lightness > LIGHTNESS_THRESHOLD) {
            // Light surface -- push toward white
            val nr = (r + (CHANNEL_CLAMP_MAX - r) * SURFACE_PUSH_FACTOR).toInt().coerceIn(0, CHANNEL_CLAMP_MAX)
            val ng = (g + (CHANNEL_CLAMP_MAX - g) * SURFACE_PUSH_FACTOR).toInt().coerceIn(0, CHANNEL_CLAMP_MAX)
            val nb = (b + (CHANNEL_CLAMP_MAX - b) * SURFACE_PUSH_FACTOR).toInt().coerceIn(0, CHANNEL_CLAMP_MAX)
            formatHex(nr, ng, nb)
        } else {
            // Dark surface -- push toward black
            val nr = (r * SURFACE_DARKEN_FACTOR).toInt().coerceIn(0, CHANNEL_CLAMP_MAX)
            val ng = (g * SURFACE_DARKEN_FACTOR).toInt().coerceIn(0, CHANNEL_CLAMP_MAX)
            val nb = (b * SURFACE_DARKEN_FACTOR).toInt().coerceIn(0, CHANNEL_CLAMP_MAX)
            formatHex(nr, ng, nb)
        }
    }

    /**
     * Darkens a color by reducing brightness.
     */
    internal fun darkenColor(hex: String): String {
        val (r, g, b) = parseRgb(hex) ?: return hex
        val nr = (r * DARKEN_FACTOR).toInt().coerceIn(0, CHANNEL_CLAMP_MAX)
        val ng = (g * DARKEN_FACTOR).toInt().coerceIn(0, CHANNEL_CLAMP_MAX)
        val nb = (b * DARKEN_FACTOR).toInt().coerceIn(0, CHANNEL_CLAMP_MAX)
        return formatHex(nr, ng, nb)
    }

    private fun parseRgb(hex: String): Triple<Int, Int, Int>? {
        val clean = hex.removePrefix("#")
        if (clean.length != HEX_LENGTH_RGB && clean.length != HEX_LENGTH_ARGB) {
            return null
        }
        return try {
            val offset =
                if (clean.length == HEX_LENGTH_ARGB) {
                    ARGB_OFFSET
                } else {
                    0
                }
            val r = clean.substring(offset, offset + COMPONENT_LENGTH).toInt(HEX_RADIX)
            val g = clean.substring(offset + COMPONENT_LENGTH, offset + SUBSTRING_G_OFFSET).toInt(HEX_RADIX)
            val b = clean.substring(offset + SUBSTRING_G_OFFSET, offset + SUBSTRING_B_OFFSET).toInt(HEX_RADIX)
            Triple(r, g, b)
        } catch (_: NumberFormatException) {
            null
        }
    }

    private fun formatHex(
        r: Int,
        g: Int,
        b: Int,
    ): String = "#${r.toHexByte()}${g.toHexByte()}${b.toHexByte()}"

    private fun Int.toHexByte(): String {
        val hex = this.toString(HEX_RADIX).uppercase()
        return if (hex.length == 1) {
            "0$hex"
        } else {
            hex
        }
    }
}
