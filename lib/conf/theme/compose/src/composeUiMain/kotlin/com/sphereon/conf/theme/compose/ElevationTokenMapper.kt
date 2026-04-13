package com.sphereon.conf.theme.compose

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sphereon.conf.theme.core.model.ResolvedTheme
import com.sphereon.conf.theme.core.token.TokenKeyConstants

/**
 * Elevation token values mapped from resolved theme tokens.
 */
data class ElevationTokens(
    val none: Dp = 0.dp,
    val xs: Dp = 1.dp,
    val sm: Dp = 3.dp,
    val md: Dp = 6.dp,
    val lg: Dp = 8.dp,
    val xl: Dp = 12.dp
) {
    companion object {
        val Default = ElevationTokens()
    }
}

/**
 * Maps resolved theme tokens to [ElevationTokens].
 */
object ElevationTokenMapper {

    fun toElevationTokens(theme: ResolvedTheme): ElevationTokens {
        val tokens = theme.tokens
        return ElevationTokens(
            none = tokens[TokenKeyConstants.ELEVATION_NONE]?.toDp() ?: 0.dp,
            xs = tokens[TokenKeyConstants.ELEVATION_XS]?.toDp() ?: 1.dp,
            sm = tokens[TokenKeyConstants.ELEVATION_SM]?.toDp() ?: 3.dp,
            md = tokens[TokenKeyConstants.ELEVATION_MD]?.toDp() ?: 6.dp,
            lg = tokens[TokenKeyConstants.ELEVATION_LG]?.toDp() ?: 8.dp,
            xl = tokens[TokenKeyConstants.ELEVATION_XL]?.toDp() ?: 12.dp,
        )
    }

    private fun String.toDp(): Dp? {
        val clean = this.trim()
        return when {
            clean.endsWith("dp") -> clean.removeSuffix("dp").toFloatOrNull()?.dp
            clean.endsWith("px") -> clean.removeSuffix("px").toFloatOrNull()?.dp
            else -> clean.toFloatOrNull()?.dp
        }
    }
}
