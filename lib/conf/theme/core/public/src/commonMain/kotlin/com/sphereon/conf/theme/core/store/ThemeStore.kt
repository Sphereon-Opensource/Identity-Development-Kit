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
import com.sphereon.core.compat.JsExportCompat
import kotlin.jvm.JvmOverloads

/**
 * Storage abstraction for theme definitions, custom features, design element bindings,
 * registered applications, and custom stylesheets.
 * Implementations may be in-memory or database-backed.
 */
@JsExportCompat
interface ThemeStore {
    // ========== Theme Definitions ==========

    suspend fun getDefinition(
        tenant: String,
        themeId: String,
    ): ThemeDefinition?

    suspend fun listDefinitions(
        tenant: String,
        scope: ThemeScope? = null,
    ): List<ThemeDefinition>

    suspend fun saveDefinition(
        tenant: String,
        definition: ThemeDefinition,
    ): ThemeDefinition

    suspend fun deleteDefinition(
        tenant: String,
        themeId: String,
    ): Boolean

    /**
     * Returns the definitions of one resolution layer: exact [scope], exact [variant]
     * (null selects the common, variant-null layer), exact [productType] (PRODUCT scope),
     * and exact [applicationId] (APPLICATION scope).
     */
    suspend fun getDefinitionsByScope(
        tenant: String,
        scope: ThemeScope,
        variant: ThemeVariant? = null,
        productType: ProductType? = null,
        applicationId: String? = null,
    ): List<ThemeDefinition>

    // ========== Count Methods (for quota enforcement) ==========

    /**
     * Count theme definitions for a tenant.
     */
    suspend fun countDefinitions(tenant: String): Int = listDefinitions(tenant).size

    // ========== History Snapshot Methods ==========

    /**
     * Save a snapshot of a theme definition at a specific version.
     * Used for undo/history before overwriting or deleting.
     */
    suspend fun saveDefinitionSnapshot(
        tenant: String,
        themeId: String,
        version: Long,
        definition: ThemeDefinition,
    ) {}

    /**
     * Get a historical snapshot at a specific version.
     */
    suspend fun getDefinitionHistory(
        tenant: String,
        themeId: String,
        version: Long,
    ): ThemeDefinition? = null

    /**
     * List all historical snapshots for a theme definition.
     */
    suspend fun listDefinitionHistory(
        tenant: String,
        themeId: String,
    ): List<ThemeDefinition> = emptyList()

    /**
     * Remove history snapshots beyond the retention limit.
     */
    suspend fun pruneHistory(
        tenant: String,
        themeId: String,
        keepVersions: Int = 5,
    ) {}

    /**
     * Restore a soft-deleted theme definition (un-delete).
     */
    suspend fun restoreDefinition(
        tenant: String,
        themeId: String,
    ): ThemeDefinition? = null

    // ========== Custom Features ==========

    /**
     * Save a tenant custom feature. Built-in features are contributed in code and never stored.
     */
    suspend fun saveFeature(
        tenant: String,
        feature: FeatureDefinition,
    ): FeatureDefinition

    /**
     * Get a tenant custom feature by product type and feature id.
     */
    suspend fun getFeature(
        tenant: String,
        productType: ProductType,
        featureId: String,
    ): FeatureDefinition?

    /**
     * List the tenant's custom features for a product type.
     */
    suspend fun listFeatures(
        tenant: String,
        productType: ProductType,
    ): List<FeatureDefinition>

    /**
     * Delete a tenant custom feature. Returns true if deleted.
     */
    suspend fun deleteFeature(
        tenant: String,
        productType: ProductType,
        featureId: String,
    ): Boolean

    // ========== Design Element Bindings ==========

    /**
     * Upsert a design element binding. The binding slot is
     * `(tenant, productType, featureId, elementId, applicationId?, variant?)`.
     */
    suspend fun setElementBinding(
        tenant: String,
        binding: ElementBinding,
    ): ElementBinding

    /**
     * Get the binding at an exact slot: [applicationId] null selects the tenant-scope
     * binding, [variant] null selects the variant-independent binding.
     */
    suspend fun getElementBinding(
        tenant: String,
        productType: ProductType,
        featureId: String,
        elementId: String,
        applicationId: String? = null,
        variant: ThemeVariant? = null,
    ): ElementBinding?

    /**
     * List all bindings stored for a feature, across all elements, applications, and variants.
     */
    suspend fun listElementBindings(
        tenant: String,
        productType: ProductType,
        featureId: String,
    ): List<ElementBinding>

    /**
     * Delete the binding at an exact slot. Returns true if deleted.
     */
    suspend fun deleteElementBinding(
        tenant: String,
        productType: ProductType,
        featureId: String,
        elementId: String,
        applicationId: String? = null,
        variant: ThemeVariant? = null,
    ): Boolean

    // ========== Application Registry ==========

    /**
     * Save a REST-registered application record. Platform-managed instances are merged
     * in at list time by higher layers, not stored here.
     */
    suspend fun saveApplication(
        tenant: String,
        application: Application,
    ): Application

    /**
     * Get a registered application by id.
     */
    suspend fun getApplication(
        tenant: String,
        applicationId: String,
    ): Application?

    /**
     * List the tenant's registered applications.
     */
    suspend fun listApplications(tenant: String): List<Application>

    /**
     * Delete a registered application. Returns true if deleted.
     */
    suspend fun deleteApplication(
        tenant: String,
        applicationId: String,
    ): Boolean

    // ========== Custom Stylesheets ==========

    /**
     * Save the custom stylesheet for a tenant ([applicationId] null) or one application.
     */
    suspend fun saveStylesheet(
        tenant: String,
        applicationId: String?,
        css: String,
        contentHash: String,
    )

    /**
     * Get the custom stylesheet for a tenant ([applicationId] null) or one application.
     */
    suspend fun getStylesheet(
        tenant: String,
        applicationId: String? = null,
    ): StylesheetData?

    /**
     * Delete the custom stylesheet for a tenant ([applicationId] null) or one application.
     * Returns true if deleted.
     */
    suspend fun deleteStylesheet(
        tenant: String,
        applicationId: String? = null,
    ): Boolean
}

/**
 * Simple data holder for custom stylesheet content retrieved from the store.
 */
@JsExportCompat
data class StylesheetData
    @JvmOverloads
    constructor(
        val css: String,
        val contentHash: String,
        val version: Long = 1,
    )
