package com.sphereon.conf.theme.core.resolve

import com.sphereon.conf.theme.core.defaults.SystemDefaults
import com.sphereon.conf.theme.core.model.ResolvedTheme
import com.sphereon.conf.theme.core.model.ThemeScope
import com.sphereon.conf.theme.core.model.ThemeVariant
import com.sphereon.conf.theme.core.store.ThemeStore
import com.sphereon.conf.theme.core.token.TokenFlattener
import com.sphereon.conf.theme.core.token.TokenReferenceResolver
import com.sphereon.di.session.SessionScope
import kotlinx.datetime.Clock
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Default implementation of [ThemeResolver].
 * Resolves themes by merging definition layers in precedence order:
 * SYSTEM baseline -> APP common -> APP variant -> TENANT common -> TENANT variant -> PRINCIPAL common -> PRINCIPAL variant
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<ThemeResolver>())
class DefaultThemeResolver(
    private val store: ThemeStore
) : ThemeResolver {

    override suspend fun resolve(
        tenant: String,
        variant: ThemeVariant?,
        principalId: String?
    ): ResolvedTheme {
        val layers = mutableListOf(
            // Start with system baseline (use variant-appropriate baseline if available)
            if (variant == ThemeVariant.DARK) SystemDefaults.baselineDark
            else SystemDefaults.baseline
        )

        // APP scope: common (null variant) + variant-specific
        layers += store.getDefinitionsByScope(tenant, ThemeScope.APP, null)
        if (variant != null) {
            layers += store.getDefinitionsByScope(tenant, ThemeScope.APP, variant)
        }

        // TENANT scope: common + variant-specific
        layers += store.getDefinitionsByScope(tenant, ThemeScope.TENANT, null)
        if (variant != null) {
            layers += store.getDefinitionsByScope(tenant, ThemeScope.TENANT, variant)
        }

        // PRINCIPAL scope: only if principalId provided
        if (principalId != null) {
            layers += store.getDefinitionsByScope(tenant, ThemeScope.PRINCIPAL, null)
            if (variant != null) {
                layers += store.getDefinitionsByScope(tenant, ThemeScope.PRINCIPAL, variant)
            }
        }

        val merged = TokenFlattener.merge(layers)
        val resolved = TokenReferenceResolver.resolve(merged)

        return ResolvedTheme(
            tokens = resolved,
            resolvedAt = Clock.System.now(),
            variant = variant,
            layerCount = layers.size
        )
    }
}
