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

    private val foregroundKeys = setOf(
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
        TokenKeyConstants.COLOR_ON_BACKGROUND
    )

    private val surfaceKeys = setOf(
        TokenKeyConstants.COLOR_SURFACE,
        TokenKeyConstants.COLOR_SURFACE_VARIANT,
        TokenKeyConstants.COLOR_SURFACE_CONTAINER,
        TokenKeyConstants.COLOR_SURFACE_CONTAINER_HIGH,
        TokenKeyConstants.COLOR_SURFACE_CONTAINER_HIGHEST,
        TokenKeyConstants.COLOR_SURFACE_CONTAINER_LOW,
        TokenKeyConstants.COLOR_SURFACE_CONTAINER_LOWEST,
        TokenKeyConstants.COLOR_BACKGROUND
    )

    private val outlineKeys = setOf(
        TokenKeyConstants.COLOR_OUTLINE,
        TokenKeyConstants.COLOR_OUTLINE_VARIANT
    )

    /**
     * Returns a new [ResolvedTheme] with contrast-boosted color tokens.
     */
    fun resolve(theme: ResolvedTheme): ResolvedTheme {
        val boosted = theme.tokens.toMutableMap()

        for ((key, value) in theme.tokens) {
            if (!value.startsWith("#")) continue

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

    private fun darkenIfPresent(tokens: MutableMap<String, String>, key: String) {
        val value = tokens[key] ?: return
        if (!value.startsWith("#")) return
        tokens[key] = darkenColor(value)
    }

    /**
     * Pushes a color toward pure white (#FFFFFF) or pure black (#000000)
     * based on its current lightness.
     */
    internal fun pushToExtreme(hex: String): String {
        val (r, g, b) = parseRgb(hex) ?: return hex
        val lightness = (r * 0.299 + g * 0.587 + b * 0.114) / 255.0
        return if (lightness > 0.5) "#FFFFFF" else "#000000"
    }

    /**
     * Pushes surface colors toward extremes — light surfaces whiter, dark surfaces blacker.
     */
    internal fun pushSurfaceToExtreme(hex: String): String {
        val (r, g, b) = parseRgb(hex) ?: return hex
        val lightness = (r * 0.299 + g * 0.587 + b * 0.114) / 255.0
        return if (lightness > 0.5) {
            // Light surface — push toward white
            val nr = (r + (255 - r) * 0.7).toInt().coerceIn(0, 255)
            val ng = (g + (255 - g) * 0.7).toInt().coerceIn(0, 255)
            val nb = (b + (255 - b) * 0.7).toInt().coerceIn(0, 255)
            formatHex(nr, ng, nb)
        } else {
            // Dark surface — push toward black
            val nr = (r * 0.3).toInt().coerceIn(0, 255)
            val ng = (g * 0.3).toInt().coerceIn(0, 255)
            val nb = (b * 0.3).toInt().coerceIn(0, 255)
            formatHex(nr, ng, nb)
        }
    }

    /**
     * Darkens a color by reducing brightness.
     */
    internal fun darkenColor(hex: String): String {
        val (r, g, b) = parseRgb(hex) ?: return hex
        val nr = (r * 0.4).toInt().coerceIn(0, 255)
        val ng = (g * 0.4).toInt().coerceIn(0, 255)
        val nb = (b * 0.4).toInt().coerceIn(0, 255)
        return formatHex(nr, ng, nb)
    }

    private fun parseRgb(hex: String): Triple<Int, Int, Int>? {
        val clean = hex.removePrefix("#")
        if (clean.length != 6 && clean.length != 8) return null
        return try {
            val offset = if (clean.length == 8) 2 else 0
            val r = clean.substring(offset, offset + 2).toInt(16)
            val g = clean.substring(offset + 2, offset + 4).toInt(16)
            val b = clean.substring(offset + 4, offset + 6).toInt(16)
            Triple(r, g, b)
        } catch (_: NumberFormatException) {
            null
        }
    }

    private fun formatHex(r: Int, g: Int, b: Int): String {
        return "#${r.toHexByte()}${g.toHexByte()}${b.toHexByte()}"
    }

    private fun Int.toHexByte(): String {
        val hex = this.toString(16).uppercase()
        return if (hex.length == 1) "0$hex" else hex
    }
}
