package com.sphereon.conf.theme.compose

import androidx.compose.ui.unit.dp
import com.sphereon.conf.theme.core.model.ResolvedTheme
import com.sphereon.conf.theme.core.token.TokenKeyConstants
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals

class ElevationTokenMapperTest {

    private fun resolvedTheme(tokens: Map<String, String> = emptyMap()) = ResolvedTheme(
        tokens = tokens,
        resolvedAt = Clock.System.now()
    )

    @Test
    fun parseDpSuffix() {
        val tokens = mapOf(TokenKeyConstants.ELEVATION_MD to "6dp")
        val result = ElevationTokenMapper.toElevationTokens(resolvedTheme(tokens))
        assertEquals(6.dp, result.md)
    }

    @Test
    fun parsePxSuffix() {
        val tokens = mapOf(TokenKeyConstants.ELEVATION_SM to "8px")
        val result = ElevationTokenMapper.toElevationTokens(resolvedTheme(tokens))
        assertEquals(8.dp, result.sm)
    }

    @Test
    fun parsePlainNumber() {
        val tokens = mapOf(TokenKeyConstants.ELEVATION_LG to "3")
        val result = ElevationTokenMapper.toElevationTokens(resolvedTheme(tokens))
        assertEquals(3.dp, result.lg)
    }

    @Test
    fun invalidValueFallsBackToDefault() {
        val tokens = mapOf(TokenKeyConstants.ELEVATION_XS to "invalid")
        val result = ElevationTokenMapper.toElevationTokens(resolvedTheme(tokens))
        assertEquals(1.dp, result.xs) // default
    }

    @Test
    fun emptyTokensFallBackToDefaults() {
        val result = ElevationTokenMapper.toElevationTokens(resolvedTheme())
        assertEquals(ElevationTokens.Default, result)
    }

    @Test
    fun systemDefaultsMapCorrectly() {
        val tokens = mapOf(
            TokenKeyConstants.ELEVATION_NONE to "0dp",
            TokenKeyConstants.ELEVATION_XS to "1dp",
            TokenKeyConstants.ELEVATION_SM to "3dp",
            TokenKeyConstants.ELEVATION_MD to "6dp",
            TokenKeyConstants.ELEVATION_LG to "8dp",
            TokenKeyConstants.ELEVATION_XL to "12dp",
        )
        val result = ElevationTokenMapper.toElevationTokens(resolvedTheme(tokens))
        assertEquals(0.dp, result.none)
        assertEquals(1.dp, result.xs)
        assertEquals(3.dp, result.sm)
        assertEquals(6.dp, result.md)
        assertEquals(8.dp, result.lg)
        assertEquals(12.dp, result.xl)
    }
}
