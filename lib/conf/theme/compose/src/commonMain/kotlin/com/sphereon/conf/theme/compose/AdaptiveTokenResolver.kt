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
 * Adapts typography tokens based on [WindowWidthSizeClass].
 *
 * Scale factors are read from responsive tokens in the theme, falling back to M3 defaults:
 * - **Compact**: 1.0x (no change — mobile baseline)
 * - **Medium**: Display scale from `responsive.scale.medium.display`, Headline from `responsive.scale.medium.headline`
 * - **Expanded**: Display/Headline/Title from their respective `responsive.scale.expanded.*` tokens
 */
object AdaptiveTokenResolver {
    // Default responsive scale factors (M3 spec)
    private const val SCALE_MEDIUM_DISPLAY = 1.10f
    private const val SCALE_MEDIUM_HEADLINE = 1.05f
    private const val SCALE_EXPANDED_DISPLAY = 1.20f
    private const val SCALE_EXPANDED_HEADLINE = 1.10f
    private const val SCALE_EXPANDED_TITLE = 1.05f

    // Rounding precision
    private const val DECIMAL_PRECISION = 10
    private const val DECIMAL_PRECISION_DOUBLE = 10.0
    private const val ROUNDING_OFFSET = 0.5

    private val displayKeys =
        listOf(
            "typography.displayLarge.fontSize",
            "typography.displayLarge.lineHeight",
            "typography.displayMedium.fontSize",
            "typography.displayMedium.lineHeight",
            "typography.displaySmall.fontSize",
            "typography.displaySmall.lineHeight",
        )

    private val headlineKeys =
        listOf(
            "typography.headlineLarge.fontSize",
            "typography.headlineLarge.lineHeight",
            "typography.headlineMedium.fontSize",
            "typography.headlineMedium.lineHeight",
            "typography.headlineSmall.fontSize",
            "typography.headlineSmall.lineHeight",
        )

    private val titleKeys =
        listOf(
            "typography.titleLarge.fontSize",
            "typography.titleLarge.lineHeight",
            "typography.titleMedium.fontSize",
            "typography.titleMedium.lineHeight",
            "typography.titleSmall.fontSize",
            "typography.titleSmall.lineHeight",
        )

    /**
     * Returns a new [ResolvedTheme] with typography tokens scaled for the given [widthSizeClass].
     * Scale factors are read from the theme's responsive tokens, with hardcoded defaults as fallback.
     */
    fun resolve(
        theme: ResolvedTheme,
        widthSizeClass: WindowWidthSizeClass,
    ): ResolvedTheme {
        if (widthSizeClass == WindowWidthSizeClass.Compact) {
            return theme
        }

        val tokens = theme.tokens
        val adjusted = tokens.toMutableMap()

        when (widthSizeClass) {
            WindowWidthSizeClass.Compact -> { /* no scaling */ }

            WindowWidthSizeClass.Medium -> {
                val displayScale = tokens[TokenKeyConstants.RESPONSIVE_SCALE_MEDIUM_DISPLAY]?.toFloatOrNull() ?: SCALE_MEDIUM_DISPLAY
                val headlineScale = tokens[TokenKeyConstants.RESPONSIVE_SCALE_MEDIUM_HEADLINE]?.toFloatOrNull() ?: SCALE_MEDIUM_HEADLINE
                scaleTokens(adjusted, displayKeys, displayScale)
                scaleTokens(adjusted, headlineKeys, headlineScale)
            }

            WindowWidthSizeClass.Expanded -> {
                val displayScale = tokens[TokenKeyConstants.RESPONSIVE_SCALE_EXPANDED_DISPLAY]?.toFloatOrNull() ?: SCALE_EXPANDED_DISPLAY
                val headlineScale = tokens[TokenKeyConstants.RESPONSIVE_SCALE_EXPANDED_HEADLINE]?.toFloatOrNull() ?: SCALE_EXPANDED_HEADLINE
                val titleScale = tokens[TokenKeyConstants.RESPONSIVE_SCALE_EXPANDED_TITLE]?.toFloatOrNull() ?: SCALE_EXPANDED_TITLE
                scaleTokens(adjusted, displayKeys, displayScale)
                scaleTokens(adjusted, headlineKeys, headlineScale)
                scaleTokens(adjusted, titleKeys, titleScale)
            }
        }

        return theme.copy(tokens = adjusted)
    }

    private fun scaleTokens(
        tokens: MutableMap<String, String>,
        keys: List<String>,
        factor: Float,
    ) {
        for (key in keys) {
            val value = tokens[key] ?: continue
            tokens[key] = scaleSpValue(value, factor)
        }
    }

    /**
     * Parses an "Xsp" value, multiplies by [factor], and formats back.
     */
    internal fun scaleSpValue(
        value: String,
        factor: Float,
    ): String {
        val trimmed = value.trim()
        val numericPart: String
        val unit: String

        when {
            trimmed.endsWith("sp") -> {
                numericPart = trimmed.removeSuffix("sp")
                unit = "sp"
            }

            trimmed.endsWith("dp") -> {
                numericPart = trimmed.removeSuffix("dp")
                unit = "dp"
            }

            else -> {
                return value
            } // Not a scalable value
        }

        val number = numericPart.toFloatOrNull() ?: return value
        val scaled = number * factor

        // Format: remove trailing zeros but keep one decimal if needed
        val formatted =
            if (scaled == scaled.toLong().toFloat()) {
                scaled.toLong().toString()
            } else {
                val rounded = (scaled * DECIMAL_PRECISION).toLong() / DECIMAL_PRECISION_DOUBLE
                val whole = rounded.toLong()
                val frac = ((rounded - whole) * DECIMAL_PRECISION + ROUNDING_OFFSET).toLong()
                "$whole.$frac"
            }

        return "$formatted$unit"
    }
}
