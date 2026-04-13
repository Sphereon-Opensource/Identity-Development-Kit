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

        val base = if (isDark) darkColorScheme() else lightColorScheme()

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

    private fun Map<String, String>.colorOrDefault(key: String, default: Color): Color {
        val hex = this[key] ?: return default
        return parseHexColor(hex) ?: default
    }

    private fun parseHexColor(hex: String): Color? {
        val clean = hex.removePrefix("#")
        return try {
            when (clean.length) {
                6 -> {
                    val value = clean.toLong(16)
                    Color(
                        red = ((value shr 16) and 0xFF).toInt(),
                        green = ((value shr 8) and 0xFF).toInt(),
                        blue = (value and 0xFF).toInt()
                    )
                }
                8 -> {
                    val value = clean.toLong(16)
                    Color(
                        alpha = ((value shr 24) and 0xFF).toInt(),
                        red = ((value shr 16) and 0xFF).toInt(),
                        green = ((value shr 8) and 0xFF).toInt(),
                        blue = (value and 0xFF).toInt()
                    )
                }
                else -> null
            }
        } catch (e: NumberFormatException) {
            null
        }
    }
}
