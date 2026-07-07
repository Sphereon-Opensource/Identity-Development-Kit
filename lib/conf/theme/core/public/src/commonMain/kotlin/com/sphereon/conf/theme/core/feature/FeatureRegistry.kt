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
import com.sphereon.core.compat.JsExportCompat

/**
 * The effective feature catalog per product type: built-in features contributed in code
 * through the multibound [FeatureDescriptorProvider] set, merged with tenant custom
 * features from the store.
 *
 * Built-in features are read-only; a custom feature never shadows a built-in one with
 * the same feature id.
 */
@JsExportCompat
interface FeatureRegistry {
    /**
     * All features of a product type visible to a tenant: built-ins plus the tenant's
     * custom features.
     */
    suspend fun listFeatures(
        tenant: String,
        productType: ProductType,
    ): List<FeatureDefinition>

    /**
     * A single feature by id, built-in or custom. Null when neither exists.
     */
    suspend fun getFeature(
        tenant: String,
        productType: ProductType,
        featureId: String,
    ): FeatureDefinition?

    /**
     * The built-in (code-contributed) features of a product type, tenant independent.
     */
    fun builtInFeatures(productType: ProductType): List<FeatureDefinition>

    /**
     * Whether a feature id belongs to a built-in feature of the product type.
     * Built-ins are read-only through the management surface.
     */
    fun isBuiltIn(
        productType: ProductType,
        featureId: String,
    ): Boolean
}
