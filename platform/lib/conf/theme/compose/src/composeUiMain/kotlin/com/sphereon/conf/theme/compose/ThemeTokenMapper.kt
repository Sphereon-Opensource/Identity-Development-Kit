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

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.sphereon.conf.theme.core.model.ResolvedTheme
import com.sphereon.conf.theme.core.model.ThemeVariant
import com.sphereon.conf.theme.core.token.TokenKeyConstants

/**
 * Maps ResolvedTheme tokens to Material 3 ColorScheme.
 */
object ThemeTokenMapper {
    /**
     * Create an M3 ColorScheme from resolved theme tokens.
     */
    fun toColorScheme(theme: ResolvedTheme): ColorScheme {
        val tokens = theme.tokens
        val isDark = theme.variant == ThemeVariant.DARK

        val base =
            if (isDark) {
                darkColorScheme()
            } else {
                lightColorScheme()
            }

        return base.copy(
            primary = tokens.colorOrDefault(TokenKeyConstants.COLOR_PRIMARY, base.primary),
            onPrimary = tokens.colorOrDefault(TokenKeyConstants.COLOR_ON_PRIMARY, base.onPrimary),
            primaryContainer = tokens.colorOrDefault(TokenKeyConstants.COLOR_PRIMARY_CONTAINER, base.primaryContainer),
            onPrimaryContainer = tokens.colorOrDefault(TokenKeyConstants.COLOR_ON_PRIMARY_CONTAINER, base.onPrimaryContainer),
            secondary = tokens.colorOrDefault(TokenKeyConstants.COLOR_SECONDARY, base.secondary),
            onSecondary = tokens.colorOrDefault(TokenKeyConstants.COLOR_ON_SECONDARY, base.onSecondary),
            secondaryContainer = tokens.colorOrDefault(TokenKeyConstants.COLOR_SECONDARY_CONTAINER, base.secondaryContainer),
            onSecondaryContainer = tokens.colorOrDefault(TokenKeyConstants.COLOR_ON_SECONDARY_CONTAINER, base.onSecondaryContainer),
            tertiary = tokens.colorOrDefault(TokenKeyConstants.COLOR_TERTIARY, base.tertiary),
            onTertiary = tokens.colorOrDefault(TokenKeyConstants.COLOR_ON_TERTIARY, base.onTertiary),
            tertiaryContainer = tokens.colorOrDefault(TokenKeyConstants.COLOR_TERTIARY_CONTAINER, base.tertiaryContainer),
            onTertiaryContainer = tokens.colorOrDefault(TokenKeyConstants.COLOR_ON_TERTIARY_CONTAINER, base.onTertiaryContainer),
            error = tokens.colorOrDefault(TokenKeyConstants.COLOR_ERROR, base.error),
            onError = tokens.colorOrDefault(TokenKeyConstants.COLOR_ON_ERROR, base.onError),
            errorContainer = tokens.colorOrDefault(TokenKeyConstants.COLOR_ERROR_CONTAINER, base.errorContainer),
            onErrorContainer = tokens.colorOrDefault(TokenKeyConstants.COLOR_ON_ERROR_CONTAINER, base.onErrorContainer),
            surface = tokens.colorOrDefault(TokenKeyConstants.COLOR_SURFACE, base.surface),
            onSurface = tokens.colorOrDefault(TokenKeyConstants.COLOR_ON_SURFACE, base.onSurface),
            surfaceVariant = tokens.colorOrDefault(TokenKeyConstants.COLOR_SURFACE_VARIANT, base.surfaceVariant),
            onSurfaceVariant = tokens.colorOrDefault(TokenKeyConstants.COLOR_ON_SURFACE_VARIANT, base.onSurfaceVariant),
            background = tokens.colorOrDefault(TokenKeyConstants.COLOR_BACKGROUND, base.background),
            onBackground = tokens.colorOrDefault(TokenKeyConstants.COLOR_ON_BACKGROUND, base.onBackground),
            outline = tokens.colorOrDefault(TokenKeyConstants.COLOR_OUTLINE, base.outline),
            outlineVariant = tokens.colorOrDefault(TokenKeyConstants.COLOR_OUTLINE_VARIANT, base.outlineVariant),
            inverseSurface = tokens.colorOrDefault(TokenKeyConstants.COLOR_INVERSE_SURFACE, base.inverseSurface),
            inverseOnSurface = tokens.colorOrDefault(TokenKeyConstants.COLOR_INVERSE_ON_SURFACE, base.inverseOnSurface),
            inversePrimary = tokens.colorOrDefault(TokenKeyConstants.COLOR_INVERSE_PRIMARY, base.inversePrimary),
            scrim = tokens.colorOrDefault(TokenKeyConstants.COLOR_SCRIM, base.scrim),
            surfaceContainerHighest = tokens.colorOrDefault(TokenKeyConstants.COLOR_SURFACE_CONTAINER_HIGHEST, base.surfaceContainerHighest),
            surfaceContainerHigh = tokens.colorOrDefault(TokenKeyConstants.COLOR_SURFACE_CONTAINER_HIGH, base.surfaceContainerHigh),
            surfaceContainer = tokens.colorOrDefault(TokenKeyConstants.COLOR_SURFACE_CONTAINER, base.surfaceContainer),
            surfaceContainerLow = tokens.colorOrDefault(TokenKeyConstants.COLOR_SURFACE_CONTAINER_LOW, base.surfaceContainerLow),
            surfaceContainerLowest = tokens.colorOrDefault(TokenKeyConstants.COLOR_SURFACE_CONTAINER_LOWEST, base.surfaceContainerLowest),
        )
    }

    private fun Map<String, String>.colorOrDefault(
        key: String,
        default: Color,
    ): Color {
        val hex = this[key] ?: return default
        return parseHexColor(hex) ?: default
    }

    private const val HEX_LENGTH_RGB = 6
    private const val HEX_LENGTH_ARGB = 8
    private const val HEX_RADIX = 16
    private const val BYTE_MASK = 0xFFL
    private const val SHIFT_ALPHA = 24
    private const val SHIFT_RED = 16
    private const val SHIFT_GREEN = 8

    private fun parseHexColor(hex: String): Color? {
        val clean = hex.removePrefix("#")
        return try {
            when (clean.length) {
                HEX_LENGTH_RGB -> {
                    val value = clean.toLong(HEX_RADIX)
                    Color(
                        red = ((value shr SHIFT_RED) and BYTE_MASK).toInt(),
                        green = ((value shr SHIFT_GREEN) and BYTE_MASK).toInt(),
                        blue = (value and BYTE_MASK).toInt(),
                    )
                }

                HEX_LENGTH_ARGB -> {
                    val value = clean.toLong(HEX_RADIX)
                    Color(
                        alpha = ((value shr SHIFT_ALPHA) and BYTE_MASK).toInt(),
                        red = ((value shr SHIFT_RED) and BYTE_MASK).toInt(),
                        green = ((value shr SHIFT_GREEN) and BYTE_MASK).toInt(),
                        blue = (value and BYTE_MASK).toInt(),
                    )
                }

                else -> {
                    null
                }
            }
        } catch (_: NumberFormatException) {
            null
        }
    }
}
