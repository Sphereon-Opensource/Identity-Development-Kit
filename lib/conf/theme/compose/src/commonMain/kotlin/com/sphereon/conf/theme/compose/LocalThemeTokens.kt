package com.sphereon.conf.theme.compose

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import com.sphereon.conf.theme.core.model.ResolvedTheme
import com.sphereon.conf.theme.core.model.ThemeVariant

/**
 * CompositionLocal providing raw token access for cases where
 * the M3 ColorScheme mapping isn't sufficient.
 *
 * Usage:
 * ```kotlin
 * val tokens = LocalThemeTokens.current
 * val brandColor = tokens["color.brand"] ?: "#000000"
 * ```
 */
val LocalThemeTokens = compositionLocalOf<Map<String, String>> { emptyMap() }

/**
 * CompositionLocal providing the full [ResolvedTheme] for advanced use cases.
 */
val LocalResolvedTheme = compositionLocalOf<ResolvedTheme?> { null }

/**
 * CompositionLocal providing the active [ThemeVariant].
 */
val LocalThemeVariant = compositionLocalOf<ThemeVariant?> { null }

/**
 * CompositionLocal providing structured branding tokens (app name, logos, fonts).
 */
val LocalBrandingTokens = staticCompositionLocalOf { BrandingTokens.Default }

/**
 * CompositionLocal providing the current [AccessibilityState].
 */
val LocalAccessibilityState = staticCompositionLocalOf { AccessibilityState() }

/**
 * CompositionLocal providing the current [WindowWidthSizeClass].
 */
val LocalWindowWidthSizeClass = staticCompositionLocalOf { WindowWidthSizeClass.Compact }

/**
 * CompositionLocal providing structured motion/animation tokens.
 */
val LocalMotionTokens = staticCompositionLocalOf { MotionTokens.Default }

/**
 * Spacing token values extracted from resolved theme tokens.
 */
data class SpacingTokens(
    val s0: String = "0px",
    val s1: String = "4px",
    val s2: String = "8px",
    val s3: String = "12px",
    val s4: String = "16px",
    val s5: String = "20px",
    val s6: String = "24px",
    val s8: String = "32px",
    val s10: String = "40px",
    val s12: String = "48px",
    val s14: String = "56px",
    val s16: String = "64px",
    val s20: String = "80px",
    val s24: String = "96px",
    val s32: String = "128px",
    val s40: String = "160px",
    val s48: String = "192px"
) {
    companion object {
        val Default = SpacingTokens()
    }
}

/**
 * CompositionLocal providing spacing tokens.
 */
val LocalSpacingTokens = staticCompositionLocalOf { SpacingTokens.Default }

/**
 * Shadow token values extracted from resolved theme tokens.
 * Values are CSS box-shadow strings.
 */
data class ShadowTokens(
    val none: String = "none",
    val xs: String = "0 1px 2px 0 rgba(0,0,0,0.05)",
    val sm: String = "0 1px 3px 0 rgba(0,0,0,0.1), 0 1px 2px -1px rgba(0,0,0,0.1)",
    val md: String = "0 4px 6px -1px rgba(0,0,0,0.1), 0 2px 4px -2px rgba(0,0,0,0.1)",
    val lg: String = "0 10px 15px -3px rgba(0,0,0,0.1), 0 4px 6px -4px rgba(0,0,0,0.1)",
    val xl: String = "0 20px 25px -5px rgba(0,0,0,0.1), 0 8px 10px -6px rgba(0,0,0,0.1)",
    val xxl: String = "0 25px 50px -12px rgba(0,0,0,0.25)"
) {
    companion object {
        val Default = ShadowTokens()
    }
}

/**
 * CompositionLocal providing shadow tokens.
 */
val LocalShadowTokens = staticCompositionLocalOf { ShadowTokens.Default }

/**
 * Border token values extracted from resolved theme tokens.
 */
data class BorderTokens(
    val widthNone: String = "0px",
    val widthThin: String = "1px",
    val widthMedium: String = "2px",
    val widthThick: String = "4px"
) {
    companion object {
        val Default = BorderTokens()
    }
}

/**
 * CompositionLocal providing border tokens.
 */
val LocalBorderTokens = staticCompositionLocalOf { BorderTokens.Default }

/**
 * Structured access to design system palette scales extracted from resolved theme tokens.
 * Provides typed access to Tier 0 palette primitives.
 *
 * Usage:
 * ```kotlin
 * val palette = LocalPaletteTokens.current
 * val brandPrimary = palette.brand?.s500 ?: "#000000"
 * ```
 */
data class PaletteTokens(
    val brand: com.sphereon.conf.theme.core.palette.PaletteScale? = null,
    val secondary: com.sphereon.conf.theme.core.palette.PaletteScale? = null,
    val neutral: com.sphereon.conf.theme.core.palette.PaletteScale? = null,
    val error: com.sphereon.conf.theme.core.palette.PaletteScale? = null,
    val success: com.sphereon.conf.theme.core.palette.PaletteScale? = null,
    val warning: com.sphereon.conf.theme.core.palette.PaletteScale? = null,
    val info: com.sphereon.conf.theme.core.palette.PaletteScale? = null,
    val pending: com.sphereon.conf.theme.core.palette.PaletteScale? = null,
) {
    companion object {
        val Default = PaletteTokens()

        private fun extractScale(tokens: Map<String, String>, role: String): com.sphereon.conf.theme.core.palette.PaletteScale? {
            val prefix = "palette.$role."
            val s50 = tokens["${prefix}50"] ?: return null
            return com.sphereon.conf.theme.core.palette.PaletteScale(
                s50 = s50,
                s100 = tokens["${prefix}100"] ?: return null,
                s200 = tokens["${prefix}200"] ?: return null,
                s300 = tokens["${prefix}300"] ?: return null,
                s400 = tokens["${prefix}400"] ?: return null,
                s500 = tokens["${prefix}500"] ?: return null,
                s600 = tokens["${prefix}600"] ?: return null,
                s700 = tokens["${prefix}700"] ?: return null,
                s800 = tokens["${prefix}800"] ?: return null,
                s900 = tokens["${prefix}900"] ?: return null,
            )
        }

        /**
         * Extract palette tokens from the flat resolved token map.
         */
        fun fromTokens(tokens: Map<String, String>): PaletteTokens = PaletteTokens(
            brand = extractScale(tokens, "brand"),
            secondary = extractScale(tokens, "secondary"),
            neutral = extractScale(tokens, "neutral"),
            error = extractScale(tokens, "error"),
            success = extractScale(tokens, "success"),
            warning = extractScale(tokens, "warning"),
            info = extractScale(tokens, "info"),
            pending = extractScale(tokens, "pending"),
        )
    }
}

/**
 * CompositionLocal providing typed access to design system palette scales.
 */
val LocalPaletteTokens = staticCompositionLocalOf { PaletteTokens.Default }
