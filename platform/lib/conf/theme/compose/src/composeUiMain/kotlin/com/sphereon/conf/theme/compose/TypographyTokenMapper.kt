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

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.sphereon.conf.theme.core.model.ResolvedTheme
import com.sphereon.conf.theme.core.token.TokenKeyConstants

/**
 * Maps resolved theme tokens to M3 [Typography].
 */
object TypographyTokenMapper {
    /**
     * Create an M3 Typography from resolved theme tokens.
     * Falls back to M3 defaults when tokens are missing.
     *
     * @param baseFontFamily Optional font family resolved from [ThemeResourceMapper].
     *   When provided, all text styles use this as their base font unless
     *   overridden by a per-style token.
     */
    fun toTypography(
        theme: ResolvedTheme,
        baseFontFamily: FontFamily? = null,
    ): Typography {
        val tokens = theme.tokens
        val base = Typography()

        return Typography(
            displayLarge = buildTextStyle(tokens, "displayLarge", base.displayLarge, baseFontFamily),
            displayMedium = buildTextStyle(tokens, "displayMedium", base.displayMedium, baseFontFamily),
            displaySmall = buildTextStyle(tokens, "displaySmall", base.displaySmall, baseFontFamily),
            headlineLarge = buildTextStyle(tokens, "headlineLarge", base.headlineLarge, baseFontFamily),
            headlineMedium = buildTextStyle(tokens, "headlineMedium", base.headlineMedium, baseFontFamily),
            headlineSmall = buildTextStyle(tokens, "headlineSmall", base.headlineSmall, baseFontFamily),
            titleLarge = buildTextStyle(tokens, "titleLarge", base.titleLarge, baseFontFamily),
            titleMedium = buildTextStyle(tokens, "titleMedium", base.titleMedium, baseFontFamily),
            titleSmall = buildTextStyle(tokens, "titleSmall", base.titleSmall, baseFontFamily),
            bodyLarge = buildTextStyle(tokens, "bodyLarge", base.bodyLarge, baseFontFamily),
            bodyMedium = buildTextStyle(tokens, "bodyMedium", base.bodyMedium, baseFontFamily),
            bodySmall = buildTextStyle(tokens, "bodySmall", base.bodySmall, baseFontFamily),
            labelLarge = buildTextStyle(tokens, "labelLarge", base.labelLarge, baseFontFamily),
            labelMedium = buildTextStyle(tokens, "labelMedium", base.labelMedium, baseFontFamily),
            labelSmall = buildTextStyle(tokens, "labelSmall", base.labelSmall, baseFontFamily),
        )
    }

    private fun buildTextStyle(
        tokens: Map<String, String>,
        styleName: String,
        default: TextStyle,
        baseFontFamily: FontFamily? = null,
    ): TextStyle {
        val prefix = "typography.$styleName"
        return default.copy(
            fontFamily = baseFontFamily ?: default.fontFamily,
            fontSize = tokens["$prefix.fontSize"]?.toTextUnit() ?: default.fontSize,
            fontWeight = tokens["$prefix.fontWeight"]?.toFontWeight() ?: default.fontWeight,
            lineHeight = tokens["$prefix.lineHeight"]?.toTextUnit() ?: default.lineHeight,
            letterSpacing = tokens["$prefix.letterSpacing"]?.toTextUnit() ?: default.letterSpacing,
        )
    }

    private fun String.toTextUnit(): TextUnit? {
        val clean = this.trim()
        return when {
            clean.endsWith("sp") -> clean.removeSuffix("sp").toFloatOrNull()?.sp

            clean.endsWith("px") -> clean.removeSuffix("px").toFloatOrNull()?.sp

            // Approximate
            clean.endsWith("dp") -> clean.removeSuffix("dp").toFloatOrNull()?.sp

            // Approximate
            else -> clean.toFloatOrNull()?.sp
        }
    }

    private const val WEIGHT_THIN_MAX = 149
    private const val WEIGHT_EXTRA_LIGHT_MAX = 249
    private const val WEIGHT_LIGHT_MAX = 349
    private const val WEIGHT_NORMAL_MAX = 449
    private const val WEIGHT_MEDIUM_MAX = 549
    private const val WEIGHT_SEMI_BOLD_MAX = 649
    private const val WEIGHT_BOLD_MAX = 749
    private const val WEIGHT_EXTRA_BOLD_MAX = 849

    private fun String.toFontWeight(): FontWeight? {
        val weightValue = this.trim().toIntOrNull() ?: return null
        return when {
            weightValue <= WEIGHT_THIN_MAX -> FontWeight.Thin
            weightValue <= WEIGHT_EXTRA_LIGHT_MAX -> FontWeight.ExtraLight
            weightValue <= WEIGHT_LIGHT_MAX -> FontWeight.Light
            weightValue <= WEIGHT_NORMAL_MAX -> FontWeight.Normal
            weightValue <= WEIGHT_MEDIUM_MAX -> FontWeight.Medium
            weightValue <= WEIGHT_SEMI_BOLD_MAX -> FontWeight.SemiBold
            weightValue <= WEIGHT_BOLD_MAX -> FontWeight.Bold
            weightValue <= WEIGHT_EXTRA_BOLD_MAX -> FontWeight.ExtraBold
            else -> FontWeight.Black
        }
    }
}
