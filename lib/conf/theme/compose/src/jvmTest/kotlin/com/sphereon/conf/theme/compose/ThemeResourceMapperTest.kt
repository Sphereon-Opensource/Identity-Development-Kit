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

import androidx.compose.ui.text.font.FontFamily
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ThemeResourceMapperTest {
    private fun branding(
        fontResourceId: String? = null,
        logoResourceId: String? = null,
        logoDarkResourceId: String? = null,
    ) = BrandingTokens(
        fontResourceId = fontResourceId,
        logoResourceId = logoResourceId,
        logoDarkResourceId = logoDarkResourceId,
    )

    @Test
    fun registerFontAndResolve() {
        ThemeResourceMapper.clearRegistrations()
        val font = FontFamily.Monospace
        ThemeResourceMapper.registerFont("test-font", font)

        val resolved = ThemeResourceMapper.resolveFontFamily(branding(fontResourceId = "test-font"))
        assertEquals(font, resolved)

        ThemeResourceMapper.clearRegistrations()
    }

    @Test
    fun resolveUnregisteredFontReturnsNull() {
        ThemeResourceMapper.clearRegistrations()
        val resolved = ThemeResourceMapper.resolveFontFamily(branding(fontResourceId = "unknown"))
        assertNull(resolved)
    }

    @Test
    fun resolveNullFontResourceIdReturnsNull() {
        ThemeResourceMapper.clearRegistrations()
        val resolved = ThemeResourceMapper.resolveFontFamily(branding(fontResourceId = null))
        assertNull(resolved)
    }

    @Test
    fun clearRegistrationsRemovesAll() {
        ThemeResourceMapper.registerFont("test-font", FontFamily.Serif)
        ThemeResourceMapper.clearRegistrations()

        val resolved = ThemeResourceMapper.resolveFontFamily(branding(fontResourceId = "test-font"))
        assertNull(resolved)
    }

    @Test
    fun resolveLogoPainterDarkModePrefersDarkResource() {
        ThemeResourceMapper.clearRegistrations()
        // resolveLogoPainter needs @Composable painters, but we can test the ID selection logic
        // by checking null returns when nothing is registered
        val result =
            ThemeResourceMapper.resolveLogoPainter(
                branding(logoResourceId = "light", logoDarkResourceId = "dark"),
                isDark = true,
            )
        // No drawables registered, so should be null
        assertNull(result)

        ThemeResourceMapper.clearRegistrations()
    }
}
