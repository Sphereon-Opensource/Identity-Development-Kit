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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.sphereon.conf.theme.core.defaults.SystemDefaults
import com.sphereon.conf.theme.core.model.ResolvedTheme
import com.sphereon.conf.theme.core.token.TokenFlattener
import com.sphereon.conf.theme.core.token.TokenKeyConstants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Clock

class TypographyTokenMapperTest {
    private fun resolvedTheme(tokens: Map<String, String> = emptyMap()) =
        ResolvedTheme(
            tokens = tokens,
            resolvedAt = Clock.System.now(),
        )

    @Test
    fun toTypographyWithSystemDefaults() {
        val tokens = TokenFlattener.merge(listOf(SystemDefaults.baseline))
        val typography = TypographyTokenMapper.toTypography(resolvedTheme(tokens))

        assertEquals(57.sp, typography.displayLarge.fontSize)
        assertEquals(FontWeight.Normal, typography.displayLarge.fontWeight)
        assertEquals(14.sp, typography.labelLarge.fontSize)
        assertEquals(FontWeight.Medium, typography.labelLarge.fontWeight)
    }

    @Test
    fun toTypographyEmptyTokensFallBackToDefaults() {
        val typography = TypographyTokenMapper.toTypography(resolvedTheme())
        val defaults = Typography()

        assertEquals(defaults.displayLarge.fontSize, typography.displayLarge.fontSize)
        assertEquals(defaults.bodyMedium.fontSize, typography.bodyMedium.fontSize)
        assertEquals(defaults.labelSmall.fontSize, typography.labelSmall.fontSize)
    }

    @Test
    fun parseTextUnitSp() {
        val tokens =
            mapOf(
                "${TokenKeyConstants.TYPOGRAPHY_DISPLAY_LARGE_FONT_SIZE}" to "16sp",
            )
        val typography = TypographyTokenMapper.toTypography(resolvedTheme(tokens))
        assertEquals(16.sp, typography.displayLarge.fontSize)
    }

    @Test
    fun parseTextUnitPxConvertsToSp() {
        val tokens =
            mapOf(
                "${TokenKeyConstants.TYPOGRAPHY_BODY_LARGE_FONT_SIZE}" to "14px",
            )
        val typography = TypographyTokenMapper.toTypography(resolvedTheme(tokens))
        assertEquals(14.sp, typography.bodyLarge.fontSize)
    }

    @Test
    fun parseFontWeightBoundaries() {
        // Thin (0-149)
        val tokensLight =
            mapOf(
                "${TokenKeyConstants.TYPOGRAPHY_DISPLAY_LARGE_FONT_WEIGHT}" to "100",
            )
        assertEquals(FontWeight.Thin, TypographyTokenMapper.toTypography(resolvedTheme(tokensLight)).displayLarge.fontWeight)

        // Normal (350-449)
        val tokensNormal =
            mapOf(
                "${TokenKeyConstants.TYPOGRAPHY_DISPLAY_LARGE_FONT_WEIGHT}" to "400",
            )
        assertEquals(FontWeight.Normal, TypographyTokenMapper.toTypography(resolvedTheme(tokensNormal)).displayLarge.fontWeight)

        // Bold (650-749)
        val tokensBold =
            mapOf(
                "${TokenKeyConstants.TYPOGRAPHY_DISPLAY_LARGE_FONT_WEIGHT}" to "700",
            )
        assertEquals(FontWeight.Bold, TypographyTokenMapper.toTypography(resolvedTheme(tokensBold)).displayLarge.fontWeight)

        // Black (850+)
        val tokensBlack =
            mapOf(
                "${TokenKeyConstants.TYPOGRAPHY_DISPLAY_LARGE_FONT_WEIGHT}" to "900",
            )
        assertEquals(FontWeight.Black, TypographyTokenMapper.toTypography(resolvedTheme(tokensBlack)).displayLarge.fontWeight)
    }

    @Test
    fun parseFontWeightInvalidReturnsDefault() {
        val tokens =
            mapOf(
                "${TokenKeyConstants.TYPOGRAPHY_DISPLAY_LARGE_FONT_WEIGHT}" to "not-a-weight",
            )
        val typography = TypographyTokenMapper.toTypography(resolvedTheme(tokens))
        val defaults = Typography()
        assertEquals(defaults.displayLarge.fontWeight, typography.displayLarge.fontWeight)
    }

    @Test
    fun customBaseFontFamily() {
        val customFont = FontFamily.Monospace
        val typography = TypographyTokenMapper.toTypography(resolvedTheme(), baseFontFamily = customFont)

        assertEquals(customFont, typography.displayLarge.fontFamily)
        assertEquals(customFont, typography.bodyMedium.fontFamily)
        assertEquals(customFont, typography.labelSmall.fontFamily)
    }

    @Test
    fun allFifteenStylesPresent() {
        val tokens = TokenFlattener.merge(listOf(SystemDefaults.baseline))
        val typography = TypographyTokenMapper.toTypography(resolvedTheme(tokens))

        assertNotNull(typography.displayLarge)
        assertNotNull(typography.displayMedium)
        assertNotNull(typography.displaySmall)
        assertNotNull(typography.headlineLarge)
        assertNotNull(typography.headlineMedium)
        assertNotNull(typography.headlineSmall)
        assertNotNull(typography.titleLarge)
        assertNotNull(typography.titleMedium)
        assertNotNull(typography.titleSmall)
        assertNotNull(typography.bodyLarge)
        assertNotNull(typography.bodyMedium)
        assertNotNull(typography.bodySmall)
        assertNotNull(typography.labelLarge)
        assertNotNull(typography.labelMedium)
        assertNotNull(typography.labelSmall)
    }
}
