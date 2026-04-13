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
    fun toTypography(theme: ResolvedTheme, baseFontFamily: FontFamily? = null): Typography {
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
        baseFontFamily: FontFamily? = null
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
            clean.endsWith("px") -> clean.removeSuffix("px").toFloatOrNull()?.sp // Approximate
            clean.endsWith("dp") -> clean.removeSuffix("dp").toFloatOrNull()?.sp // Approximate
            else -> clean.toFloatOrNull()?.sp
        }
    }

    private fun String.toFontWeight(): FontWeight? {
        return when (val weight = this.trim().toIntOrNull()) {
            null -> null
            in 0..149 -> FontWeight.Thin
            in 150..249 -> FontWeight.ExtraLight
            in 250..349 -> FontWeight.Light
            in 350..449 -> FontWeight.Normal
            in 450..549 -> FontWeight.Medium
            in 550..649 -> FontWeight.SemiBold
            in 650..749 -> FontWeight.Bold
            in 750..849 -> FontWeight.ExtraBold
            else -> FontWeight.Black
        }
    }
}
