package com.sphereon.conf.theme.compose

import com.sphereon.conf.theme.core.model.ResolvedTheme
import com.sphereon.conf.theme.core.token.TokenKeyConstants

/**
 * Maps resolved theme tokens to [ShadowTokens].
 * Shadow values are CSS box-shadow strings; Compose consumers parse them as needed.
 */
object ShadowTokenMapper {

    fun toShadowTokens(theme: ResolvedTheme): ShadowTokens {
        val tokens = theme.tokens
        return ShadowTokens(
            none = tokens[TokenKeyConstants.SHADOW_ELEVATION_NONE] ?: "none",
            xs = tokens[TokenKeyConstants.SHADOW_ELEVATION_XS] ?: ShadowTokens.Default.xs,
            sm = tokens[TokenKeyConstants.SHADOW_ELEVATION_SM] ?: ShadowTokens.Default.sm,
            md = tokens[TokenKeyConstants.SHADOW_ELEVATION_MD] ?: ShadowTokens.Default.md,
            lg = tokens[TokenKeyConstants.SHADOW_ELEVATION_LG] ?: ShadowTokens.Default.lg,
            xl = tokens[TokenKeyConstants.SHADOW_ELEVATION_XL] ?: ShadowTokens.Default.xl,
            xxl = tokens[TokenKeyConstants.SHADOW_ELEVATION_XXL] ?: ShadowTokens.Default.xxl,
        )
    }
}
