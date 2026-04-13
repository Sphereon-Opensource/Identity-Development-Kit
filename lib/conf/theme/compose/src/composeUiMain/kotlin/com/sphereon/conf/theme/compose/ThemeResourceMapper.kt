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

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontFamily

/**
 * Registry-based resource mapper for theme branding assets.
 *
 * Apps register bundled fonts and images at startup; the mapper resolves
 * [BrandingTokens] resource IDs to registered resources with fallback to defaults.
 *
 * Usage:
 * ```kotlin
 * // At app startup
 * ThemeResourceMapper.registerFont("inter", InterFontFamily)
 * ThemeResourceMapper.registerDrawable("sphereon-logo") { painterResource(Res.drawable.logo) }
 *
 * // At render time (handled automatically by DefaultTheme)
 * val font = ThemeResourceMapper.resolveFontFamily(brandingTokens)
 * val logo = ThemeResourceMapper.resolveLogoPainter(brandingTokens, isDark = false)
 * ```
 */
object ThemeResourceMapper {
    private val fontRegistry = mutableMapOf<String, FontFamily>()
    private val drawableRegistry = mutableMapOf<String, @Composable () -> Painter>()

    /**
     * Register a font family by resource ID.
     * The ID should match the value stored in [BrandingTokens.fontResourceId].
     */
    fun registerFont(
        resourceId: String,
        fontFamily: FontFamily,
    ) {
        fontRegistry[resourceId] = fontFamily
    }

    /**
     * Register a drawable painter provider by resource ID.
     * The ID should match [BrandingTokens.logoResourceId] or [BrandingTokens.logoDarkResourceId].
     */
    fun registerDrawable(
        resourceId: String,
        provider: @Composable () -> Painter,
    ) {
        drawableRegistry[resourceId] = provider
    }

    /**
     * Resolve the font family from [BrandingTokens.fontResourceId].
     * Returns null if no font is registered for the given resource ID.
     */
    fun resolveFontFamily(branding: BrandingTokens): FontFamily? {
        val id = branding.fontResourceId ?: return null
        return fontRegistry[id]
    }

    /**
     * Resolve the logo painter from branding tokens.
     * In dark mode, prefers [BrandingTokens.logoDarkResourceId], falling back to [BrandingTokens.logoResourceId].
     * Returns null if no drawable is registered for the resolved resource ID.
     */
    fun resolveLogoPainter(
        branding: BrandingTokens,
        isDark: Boolean,
    ): (@Composable () -> Painter)? {
        val id =
            if (isDark) {
                branding.logoDarkResourceId ?: branding.logoResourceId
            } else {
                branding.logoResourceId
            }
        return id?.let { drawableRegistry[it] }
    }

    /**
     * Clear all registered resources. Useful for testing.
     */
    fun clearRegistrations() {
        fontRegistry.clear()
        drawableRegistry.clear()
    }
}
