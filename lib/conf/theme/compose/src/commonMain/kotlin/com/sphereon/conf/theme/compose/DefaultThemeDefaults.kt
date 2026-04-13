package com.sphereon.conf.theme.compose

import com.sphereon.conf.theme.core.defaults.SystemDefaults
import com.sphereon.conf.theme.core.model.ResolvedTheme
import com.sphereon.conf.theme.core.model.ThemeVariant
import com.sphereon.conf.theme.core.token.TokenFlattener
import com.sphereon.conf.theme.core.token.TokenReferenceResolver
import kotlinx.datetime.Clock

/**
 * Pre-built [ResolvedTheme] instances from [SystemDefaults].
 *
 * Provides offline/bundled default branding without needing a network call.
 * Used as fallback when [ThemeClient] fails or while loading.
 */
object DefaultThemeDefaults {

    val light: ResolvedTheme by lazy { buildResolvedTheme(SystemDefaults.baseline, ThemeVariant.LIGHT) }
    val dark: ResolvedTheme by lazy { buildResolvedTheme(SystemDefaults.baselineDark, ThemeVariant.DARK) }
    val highContrast: ResolvedTheme by lazy { buildResolvedTheme(SystemDefaults.baselineHighContrast, ThemeVariant.HIGH_CONTRAST) }

    private fun buildResolvedTheme(
        definition: com.sphereon.conf.theme.core.model.ThemeDefinition,
        variant: ThemeVariant
    ): ResolvedTheme {
        val merged = TokenFlattener.merge(listOf(definition))
        val resolved = TokenReferenceResolver.resolve(merged)
        val sortedHash = resolved.entries.sortedBy { it.key }.fold(0) { acc, (k, v) -> acc * 31 + k.hashCode() + v.hashCode() }.toUInt()
        return ResolvedTheme(
            tokens = resolved,
            resolvedAt = Clock.System.now(),
            variant = variant,
            layerCount = 1,
            etag = "W/\"$sortedHash\"",
            fallback = true
        )
    }
}
