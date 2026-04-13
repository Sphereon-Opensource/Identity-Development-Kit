package com.sphereon.conf.theme.compose

import com.sphereon.conf.theme.core.model.ResolvedTheme
import com.sphereon.conf.theme.core.token.TokenKeyConstants

/**
 * Maps resolved theme tokens to [SpacingTokens].
 */
object SpacingTokenMapper {

    fun toSpacingTokens(theme: ResolvedTheme): SpacingTokens {
        val tokens = theme.tokens
        return SpacingTokens(
            s0 = tokens[TokenKeyConstants.SPACING_0] ?: "0px",
            s1 = tokens[TokenKeyConstants.SPACING_1] ?: "4px",
            s2 = tokens[TokenKeyConstants.SPACING_2] ?: "8px",
            s3 = tokens[TokenKeyConstants.SPACING_3] ?: "12px",
            s4 = tokens[TokenKeyConstants.SPACING_4] ?: "16px",
            s5 = tokens[TokenKeyConstants.SPACING_5] ?: "20px",
            s6 = tokens[TokenKeyConstants.SPACING_6] ?: "24px",
            s8 = tokens[TokenKeyConstants.SPACING_8] ?: "32px",
            s10 = tokens[TokenKeyConstants.SPACING_10] ?: "40px",
            s12 = tokens[TokenKeyConstants.SPACING_12] ?: "48px",
            s14 = tokens[TokenKeyConstants.SPACING_14] ?: "56px",
            s16 = tokens[TokenKeyConstants.SPACING_16] ?: "64px",
            s20 = tokens[TokenKeyConstants.SPACING_20] ?: "80px",
            s24 = tokens[TokenKeyConstants.SPACING_24] ?: "96px",
            s32 = tokens[TokenKeyConstants.SPACING_32] ?: "128px",
            s40 = tokens[TokenKeyConstants.SPACING_40] ?: "160px",
            s48 = tokens[TokenKeyConstants.SPACING_48] ?: "192px",
        )
    }
}
