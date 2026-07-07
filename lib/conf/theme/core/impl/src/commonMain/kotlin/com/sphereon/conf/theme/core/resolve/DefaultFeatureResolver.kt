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

import com.sphereon.conf.theme.core.feature.FeatureRegistry
import com.sphereon.conf.theme.core.model.DesignElement
import com.sphereon.conf.theme.core.model.ElementBinding
import com.sphereon.conf.theme.core.model.ElementKind
import com.sphereon.conf.theme.core.model.ElementOrigin
import com.sphereon.conf.theme.core.model.ProductType
import com.sphereon.conf.theme.core.model.ResolvedElement
import com.sphereon.conf.theme.core.model.ResolvedFeature
import com.sphereon.conf.theme.core.model.ThemeVariant
import com.sphereon.conf.theme.core.store.ThemeStore
import com.sphereon.data.store.asset.model.AssetReference
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Default implementation of [FeatureResolver].
 *
 * Resolves each element of a feature by walking the binding chain, first match wins:
 * application binding (variant, then variant null), tenant binding (variant, then
 * variant null), the feature descriptor's default (PRODUCT_DEFAULT for built-in
 * features, ELEMENT_DEFAULT for custom ones), and finally the element's fallback
 * token key through token resolution (TOKEN_FALLBACK), where asset elements wrap
 * the token value as an [AssetReference].
 *
 * The theme resolver is Provider-deferred: token resolution is only needed when a
 * fallback token key is actually consulted, and assemblies may bind a [ThemeResolver]
 * whose implementation itself depends on a [FeatureResolver] (a construction-time
 * cycle if this were an eager dependency).
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<FeatureResolver>())
class DefaultFeatureResolver(
    private val registry: FeatureRegistry,
    private val store: ThemeStore,
    private val themeResolver: Provider<ThemeResolver>,
) : FeatureResolver {
    override suspend fun resolve(
        tenant: String,
        productType: ProductType,
        featureId: String,
        applicationId: String?,
        variant: ThemeVariant?,
    ): ResolvedFeature? {
        val feature = registry.getFeature(tenant, productType, featureId) ?: return null
        val bindings = store.listElementBindings(tenant, productType, featureId)

        var resolvedTokens: Map<String, String>? = null

        suspend fun tokens(): Map<String, String> = resolvedTokens ?: themeResolver().resolve(tenant, variant, applicationId).tokens.also { resolvedTokens = it }

        val elements = mutableMapOf<String, ResolvedElement>()
        val missingRequired = mutableListOf<String>()

        for (element in feature.elements) {
            val resolved =
                resolveFromBindings(element, bindings, applicationId, variant)
                    ?: resolveFromDefaults(element, builtIn = feature.builtIn)
                    ?: resolveFromTokenFallback(element) { tokens() }

            if (resolved != null) {
                elements[element.elementId] = resolved
            } else if (element.required) {
                missingRequired += element.elementId
            }
        }

        return ResolvedFeature(
            productType = productType,
            featureId = featureId,
            tenantId = tenant,
            applicationId = applicationId,
            variant = variant,
            elements = elements,
            missingRequired = missingRequired.takeIf { it.isNotEmpty() },
        )
    }

    private fun resolveFromBindings(
        element: DesignElement,
        bindings: List<ElementBinding>,
        applicationId: String?,
        variant: ThemeVariant?,
    ): ResolvedElement? {
        val candidates = bindings.filter { it.elementId == element.elementId && it.carriesValueFor(element) }

        fun at(
            bindingApplicationId: String?,
            bindingVariant: ThemeVariant?,
        ): ElementBinding? = candidates.firstOrNull { it.applicationId == bindingApplicationId && it.variant == bindingVariant }

        if (applicationId != null) {
            if (variant != null) {
                at(applicationId, variant)?.let { return it.toResolvedElement(ElementOrigin.APPLICATION) }
            }
            at(applicationId, null)?.let { return it.toResolvedElement(ElementOrigin.APPLICATION) }
        }
        if (variant != null) {
            at(null, variant)?.let { return it.toResolvedElement(ElementOrigin.TENANT) }
        }
        at(null, null)?.let { return it.toResolvedElement(ElementOrigin.TENANT) }

        return null
    }

    private fun resolveFromDefaults(
        element: DesignElement,
        builtIn: Boolean,
    ): ResolvedElement? {
        val origin = if (builtIn) ElementOrigin.PRODUCT_DEFAULT else ElementOrigin.ELEMENT_DEFAULT
        return when (element.kind) {
            ElementKind.ASSET -> element.defaultAsset?.let { ResolvedElement(asset = it, origin = origin) }
            ElementKind.TEXT -> element.defaultText?.let { ResolvedElement(text = it, origin = origin) }
        }
    }

    private suspend fun resolveFromTokenFallback(
        element: DesignElement,
        tokens: suspend () -> Map<String, String>,
    ): ResolvedElement? {
        val tokenKey = element.fallbackTokenKey ?: return null
        val value = tokens()[tokenKey]?.takeIf { it.isNotBlank() } ?: return null
        return when (element.kind) {
            ElementKind.ASSET -> ResolvedElement(asset = AssetReference(uri = value), origin = ElementOrigin.TOKEN_FALLBACK)
            ElementKind.TEXT -> ResolvedElement(text = value, origin = ElementOrigin.TOKEN_FALLBACK)
        }
    }

    private fun ElementBinding.carriesValueFor(element: DesignElement): Boolean =
        when (element.kind) {
            ElementKind.ASSET -> asset != null
            ElementKind.TEXT -> text != null
        }

    private fun ElementBinding.toResolvedElement(origin: ElementOrigin): ResolvedElement = ResolvedElement(asset = asset, text = text, origin = origin)
}
