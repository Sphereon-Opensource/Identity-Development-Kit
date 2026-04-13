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

import com.sphereon.conf.theme.core.model.ResolvedTheme
import com.sphereon.conf.theme.core.model.ThemeVariant
import com.sphereon.conf.theme.core.palette.DefaultPaletteMapping
import com.sphereon.conf.theme.core.palette.DesignSystemPaletteResolver
import com.sphereon.conf.theme.core.palette.PaletteMapping
import com.sphereon.conf.theme.core.palette.ThemeColorConfig
import com.sphereon.conf.theme.core.token.TokenKeyConstants
import kotlin.time.Clock

/**
 * Client-side palette resolver that generates a full M3 palette from a [ThemeColorConfig].
 *
 * Supports all four input modes: seed, multi-seed, explicit, and hybrid.
 * Generates a complete [ResolvedTheme] with all M3 color tokens plus Tier 0 palette primitives.
 */
object ClientPaletteResolver {
    /**
     * Generate a [ResolvedTheme] from a [ThemeColorConfig].
     *
     * @param colorConfig Color configuration (seed, multi-seed, explicit, or hybrid)
     * @param appName Optional app name for branding tokens
     * @param logoUrl Optional logo URL for branding tokens
     * @param variant Theme variant (light/dark)
     * @param paletteMapping Optional custom palette-to-token mapping
     */
    fun resolve(
        colorConfig: ThemeColorConfig,
        appName: String? = null,
        logoUrl: String? = null,
        variant: ThemeVariant = ThemeVariant.LIGHT,
        paletteMapping: PaletteMapping? = null,
    ): ResolvedTheme {
        val mapping = paletteMapping ?: DefaultPaletteMapping.DEFAULT
        val tokens = DesignSystemPaletteResolver.resolve(colorConfig, variant, mapping).toMutableMap()

        // Add branding tokens
        if (appName != null) {
            tokens[TokenKeyConstants.BRANDING_APP_NAME] = appName
        }
        if (logoUrl != null) {
            tokens[TokenKeyConstants.BRANDING_LOGO_URL] = logoUrl
        }

        return ResolvedTheme(
            tokens = tokens,
            resolvedAt = Clock.System.now(),
            variant = variant,
            fallback = false,
            etag = null,
        )
    }
}
