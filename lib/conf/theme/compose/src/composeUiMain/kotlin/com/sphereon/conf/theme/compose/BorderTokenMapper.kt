package com.sphereon.conf.theme.compose

import com.sphereon.conf.theme.core.model.ResolvedTheme
import com.sphereon.conf.theme.core.token.TokenKeyConstants

/**
 * Maps resolved theme tokens to [BorderTokens].
 */
object BorderTokenMapper {

    fun toBorderTokens(theme: ResolvedTheme): BorderTokens {
        val tokens = theme.tokens
        return BorderTokens(
            widthNone = tokens[TokenKeyConstants.BORDER_WIDTH_NONE] ?: "0px",
            widthThin = tokens[TokenKeyConstants.BORDER_WIDTH_THIN] ?: "1px",
            widthMedium = tokens[TokenKeyConstants.BORDER_WIDTH_MEDIUM] ?: "2px",
            widthThick = tokens[TokenKeyConstants.BORDER_WIDTH_THICK] ?: "4px",
        )
    }
}
