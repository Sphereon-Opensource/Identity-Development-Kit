package com.sphereon.conf.theme.compose

import com.sphereon.conf.theme.core.model.ResolvedTheme
import com.sphereon.conf.theme.core.model.ThemeVariant
import com.sphereon.conf.theme.core.palette.DefaultPaletteMapping
import com.sphereon.conf.theme.core.palette.DesignSystemPaletteResolver
import com.sphereon.conf.theme.core.palette.PaletteMapping
import com.sphereon.conf.theme.core.palette.ThemeColorConfig
import com.sphereon.conf.theme.core.token.TokenKeyConstants
import kotlinx.datetime.Clock

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
        if (appName != null) tokens[TokenKeyConstants.BRANDING_APP_NAME] = appName
        if (logoUrl != null) tokens[TokenKeyConstants.BRANDING_LOGO_URL] = logoUrl

        return ResolvedTheme(
            tokens = tokens,
            resolvedAt = Clock.System.now(),
            variant = variant,
            fallback = false,
            etag = null
        )
    }
}
