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

package com.sphereon.conf.theme.core.resolve

import com.sphereon.conf.theme.core.defaults.SystemDefaults
import com.sphereon.conf.theme.core.model.ResolvedTheme
import com.sphereon.conf.theme.core.model.ThemeScope
import com.sphereon.conf.theme.core.model.ThemeVariant
import com.sphereon.conf.theme.core.store.ThemeStore
import com.sphereon.conf.theme.core.token.TokenFlattener
import com.sphereon.conf.theme.core.token.TokenReferenceResolver
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.time.Clock

/**
 * Default implementation of [ThemeResolver].
 * Resolves themes by merging definition layers in precedence order, later layers winning:
 *
 * 1. SYSTEM baseline (dark baseline when the variant is DARK)
 * 2. PRODUCT scope of the application's product type: common (variant null), then variant specific
 * 3. TENANT scope: common, then variant specific
 * 4. APPLICATION scope (applicationId match): common, then variant specific
 * 5. PRINCIPAL scope (authenticated principal only): common, then variant specific
 *
 * Steps 2 and 4 are skipped when no applicationId is supplied; step 2 needs the
 * application's product type, looked up through the store's application registry.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<ThemeResolver>())
class DefaultThemeResolver(
    private val store: ThemeStore,
) : ThemeResolver {
    override suspend fun resolve(
        tenant: String,
        variant: ThemeVariant?,
        applicationId: String?,
        principalId: String?,
    ): ResolvedTheme {
        val layers =
            mutableListOf(
                if (variant == ThemeVariant.DARK) {
                    SystemDefaults.baselineDark
                } else {
                    SystemDefaults.baseline
                },
            )

        val application = applicationId?.let { store.getApplication(tenant, it) }

        // PRODUCT scope: only when the application (and thus its product type) is known
        if (application != null) {
            layers += store.getDefinitionsByScope(tenant, ThemeScope.PRODUCT, null, productType = application.productType)
            if (variant != null) {
                layers += store.getDefinitionsByScope(tenant, ThemeScope.PRODUCT, variant, productType = application.productType)
            }
        }

        // TENANT scope: common + variant-specific
        layers += store.getDefinitionsByScope(tenant, ThemeScope.TENANT, null)
        if (variant != null) {
            layers += store.getDefinitionsByScope(tenant, ThemeScope.TENANT, variant)
        }

        // APPLICATION scope: only when an applicationId is supplied
        if (applicationId != null) {
            layers += store.getDefinitionsByScope(tenant, ThemeScope.APPLICATION, null, applicationId = applicationId)
            if (variant != null) {
                layers += store.getDefinitionsByScope(tenant, ThemeScope.APPLICATION, variant, applicationId = applicationId)
            }
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
            layerCount = layers.size,
            tenantId = tenant,
            applicationId = applicationId,
        )
    }
}
