package com.sphereon.conf.theme.core.resolve

import com.sphereon.conf.theme.core.model.ResolvedTheme
import com.sphereon.conf.theme.core.model.ThemeVariant

/**
 * Resolves a fully flattened theme by merging definition layers
 * in precedence order: SYSTEM < APP < TENANT < PRINCIPAL.
 */
interface ThemeResolver {

    /**
     * Resolve the effective theme for a tenant.
     *
     * @param tenant The tenant identifier
     * @param variant Optional variant to resolve (LIGHT, DARK, HIGH_CONTRAST)
     * @param principalId Optional principal for per-user overrides
     * @return The resolved theme or an error
     */
    suspend fun resolve(
        tenant: String,
        variant: ThemeVariant? = null,
        principalId: String? = null
    ): ResolvedTheme
}
