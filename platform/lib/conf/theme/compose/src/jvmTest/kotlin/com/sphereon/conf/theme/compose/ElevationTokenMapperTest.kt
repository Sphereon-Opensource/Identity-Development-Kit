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

import androidx.compose.ui.unit.dp
import com.sphereon.conf.theme.core.model.ResolvedTheme
import com.sphereon.conf.theme.core.token.TokenKeyConstants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock

class ElevationTokenMapperTest {
    private fun resolvedTheme(tokens: Map<String, String> = emptyMap()) =
        ResolvedTheme(
            tokens = tokens,
            resolvedAt = Clock.System.now(),
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
        val tokens =
            mapOf(
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
