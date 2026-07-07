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

package com.sphereon.conf.theme.core.store

import com.sphereon.conf.theme.core.model.Application
import com.sphereon.conf.theme.core.model.ElementBinding
import com.sphereon.conf.theme.core.model.FeatureDefinition
import com.sphereon.conf.theme.core.model.ProductType
import com.sphereon.conf.theme.core.model.ThemeDefinition
import com.sphereon.conf.theme.core.model.ThemeScope
import com.sphereon.conf.theme.core.model.ThemeVariant
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory implementation of [ThemeStore].
 * Useful for testing and as a default when no persistence is configured.
 * Data is scoped per session and not persisted across restarts.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<ThemeStore>())
class InMemoryThemeStore : ThemeStore {
    private val mutex = Mutex()

    // Outer key: tenant, inner key: themeId
    private val store = mutableMapOf<String, MutableMap<String, ThemeDefinition>>()

    private val features = mutableMapOf<String, MutableMap<FeatureKey, FeatureDefinition>>()
    private val bindings = mutableMapOf<String, MutableMap<BindingKey, ElementBinding>>()
    private val applications = mutableMapOf<String, MutableMap<String, Application>>()
    private val stylesheets = mutableMapOf<String, MutableMap<String?, StylesheetData>>()

    private data class FeatureKey(
        val productType: ProductType,
        val featureId: String,
    )

    private data class BindingKey(
        val productType: ProductType,
        val featureId: String,
        val elementId: String,
        val applicationId: String?,
        val variant: ThemeVariant?,
    )

    private fun ElementBinding.key(): BindingKey =
        BindingKey(
            productType = productType,
            featureId = featureId,
            elementId = elementId,
            applicationId = applicationId,
            variant = variant,
        )

    private fun tenantStore(tenant: String): MutableMap<String, ThemeDefinition> = store.getOrPut(tenant) { mutableMapOf() }

    override suspend fun getDefinition(
        tenant: String,
        themeId: String,
    ): ThemeDefinition? = mutex.withLock { store[tenant]?.get(themeId) }

    override suspend fun listDefinitions(
        tenant: String,
        scope: ThemeScope?,
    ): List<ThemeDefinition> {
        return mutex.withLock {
            val defs = store[tenant]?.values ?: return@withLock emptyList()
            if (scope != null) defs.filter { it.scope == scope } else defs.toList()
        }
    }

    override suspend fun saveDefinition(
        tenant: String,
        definition: ThemeDefinition,
    ): ThemeDefinition {
        mutex.withLock {
            tenantStore(tenant)[definition.id] = definition
        }
        return definition
    }

    override suspend fun deleteDefinition(
        tenant: String,
        themeId: String,
    ): Boolean = mutex.withLock { store[tenant]?.remove(themeId) != null }

    override suspend fun getDefinitionsByScope(
        tenant: String,
        scope: ThemeScope,
        variant: ThemeVariant?,
        productType: ProductType?,
        applicationId: String?,
    ): List<ThemeDefinition> {
        return mutex.withLock {
            val defs = store[tenant]?.values ?: return@withLock emptyList()
            defs
                .filter { it.scope == scope }
                .filter { it.variant == variant }
                .filter { it.productType == productType }
                .filter { it.applicationId == applicationId }
        }
    }

    override suspend fun countDefinitions(tenant: String): Int = mutex.withLock { store[tenant]?.size ?: 0 }

    // ========== Custom Features ==========

    override suspend fun saveFeature(
        tenant: String,
        feature: FeatureDefinition,
    ): FeatureDefinition {
        mutex.withLock {
            features.getOrPut(tenant) { mutableMapOf() }[FeatureKey(feature.productType, feature.featureId)] = feature
        }
        return feature
    }

    override suspend fun getFeature(
        tenant: String,
        productType: ProductType,
        featureId: String,
    ): FeatureDefinition? = mutex.withLock { features[tenant]?.get(FeatureKey(productType, featureId)) }

    override suspend fun listFeatures(
        tenant: String,
        productType: ProductType,
    ): List<FeatureDefinition> =
        mutex.withLock {
            features[tenant]?.values?.filter { it.productType == productType } ?: emptyList()
        }

    override suspend fun deleteFeature(
        tenant: String,
        productType: ProductType,
        featureId: String,
    ): Boolean = mutex.withLock { features[tenant]?.remove(FeatureKey(productType, featureId)) != null }

    // ========== Design Element Bindings ==========

    override suspend fun setElementBinding(
        tenant: String,
        binding: ElementBinding,
    ): ElementBinding {
        mutex.withLock {
            bindings.getOrPut(tenant) { mutableMapOf() }[binding.key()] = binding
        }
        return binding
    }

    override suspend fun getElementBinding(
        tenant: String,
        productType: ProductType,
        featureId: String,
        elementId: String,
        applicationId: String?,
        variant: ThemeVariant?,
    ): ElementBinding? =
        mutex.withLock {
            bindings[tenant]?.get(BindingKey(productType, featureId, elementId, applicationId, variant))
        }

    override suspend fun listElementBindings(
        tenant: String,
        productType: ProductType,
        featureId: String,
    ): List<ElementBinding> =
        mutex.withLock {
            bindings[tenant]?.values?.filter { it.productType == productType && it.featureId == featureId } ?: emptyList()
        }

    override suspend fun deleteElementBinding(
        tenant: String,
        productType: ProductType,
        featureId: String,
        elementId: String,
        applicationId: String?,
        variant: ThemeVariant?,
    ): Boolean =
        mutex.withLock {
            bindings[tenant]?.remove(BindingKey(productType, featureId, elementId, applicationId, variant)) != null
        }

    // ========== Application Registry ==========

    override suspend fun saveApplication(
        tenant: String,
        application: Application,
    ): Application {
        mutex.withLock {
            applications.getOrPut(tenant) { mutableMapOf() }[application.applicationId] = application
        }
        return application
    }

    override suspend fun getApplication(
        tenant: String,
        applicationId: String,
    ): Application? = mutex.withLock { applications[tenant]?.get(applicationId) }

    override suspend fun listApplications(tenant: String): List<Application> = mutex.withLock { applications[tenant]?.values?.toList() ?: emptyList() }

    override suspend fun deleteApplication(
        tenant: String,
        applicationId: String,
    ): Boolean = mutex.withLock { applications[tenant]?.remove(applicationId) != null }

    // ========== Custom Stylesheets ==========

    override suspend fun saveStylesheet(
        tenant: String,
        applicationId: String?,
        css: String,
        contentHash: String,
    ) {
        mutex.withLock {
            val tenantSheets = stylesheets.getOrPut(tenant) { mutableMapOf() }
            val previousVersion = tenantSheets[applicationId]?.version ?: 0
            tenantSheets[applicationId] = StylesheetData(css = css, contentHash = contentHash, version = previousVersion + 1)
        }
    }

    override suspend fun getStylesheet(
        tenant: String,
        applicationId: String?,
    ): StylesheetData? = mutex.withLock { stylesheets[tenant]?.get(applicationId) }

    override suspend fun deleteStylesheet(
        tenant: String,
        applicationId: String?,
    ): Boolean = mutex.withLock { stylesheets[tenant]?.remove(applicationId) != null }
}
