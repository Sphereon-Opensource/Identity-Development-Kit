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

package com.sphereon.conf.theme.core.feature

import com.sphereon.conf.theme.core.model.FeatureDefinition
import com.sphereon.conf.theme.core.model.ProductType
import com.sphereon.conf.theme.core.store.ThemeStore
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Multibinds
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Declares the [FeatureDescriptorProvider] multibinding as allowed-empty, so assemblies
 * without any code-contributed features still resolve the DI graph.
 */
@ContributesTo(SessionScope::class)
interface ThemeFeatureMultibindDeclarations {
    @Multibinds(allowEmpty = true)
    fun featureDescriptorProviders(): Set<FeatureDescriptorProvider>
}

/**
 * Default implementation of [FeatureRegistry].
 *
 * Merges the built-in features contributed through the multibound
 * [FeatureDescriptorProvider] set with the tenant's custom features from the
 * [ThemeStore]. Built-ins are read-only and never shadowed by a custom feature
 * with the same feature id.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<FeatureRegistry>())
class DefaultFeatureRegistry(
    descriptorProviders: Set<FeatureDescriptorProvider>,
    private val store: ThemeStore,
) : FeatureRegistry {
    private val builtIns: Map<ProductType, List<FeatureDefinition>> =
        descriptorProviders
            .groupBy { it.productType }
            .mapValues { (_, providers) ->
                providers
                    .flatMap { provider -> provider.features() }
                    .map { it.copy(builtIn = true) }
                    .sortedBy { it.featureId }
                    .distinctBy { it.featureId }
            }

    override suspend fun listFeatures(
        tenant: String,
        productType: ProductType,
    ): List<FeatureDefinition> {
        val builtIn = builtInFeatures(productType)
        val builtInIds = builtIn.map { it.featureId }.toSet()
        val custom =
            store
                .listFeatures(tenant, productType)
                .filter { it.featureId !in builtInIds }
                .map { it.copy(builtIn = false) }
        return builtIn + custom
    }

    override suspend fun getFeature(
        tenant: String,
        productType: ProductType,
        featureId: String,
    ): FeatureDefinition? =
        builtInFeatures(productType).firstOrNull { it.featureId == featureId }
            ?: store.getFeature(tenant, productType, featureId)?.copy(builtIn = false)

    override fun builtInFeatures(productType: ProductType): List<FeatureDefinition> = builtIns[productType] ?: emptyList()

    override fun isBuiltIn(
        productType: ProductType,
        featureId: String,
    ): Boolean = builtInFeatures(productType).any { it.featureId == featureId }
}
