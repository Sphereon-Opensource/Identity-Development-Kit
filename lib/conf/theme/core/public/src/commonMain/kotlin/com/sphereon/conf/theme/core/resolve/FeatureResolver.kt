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

import com.sphereon.conf.theme.core.model.ProductType
import com.sphereon.conf.theme.core.model.ResolvedFeature
import com.sphereon.conf.theme.core.model.ThemeVariant
import com.sphereon.core.compat.JsExportCompat

/**
 * Resolves the effective design element values of one feature.
 *
 * Per element, the first match wins:
 * 1. APPLICATION binding with matching variant
 * 2. APPLICATION binding with variant null
 * 3. TENANT binding (applicationId null) with matching variant
 * 4. TENANT binding with variant null
 * 5. Product default from the built-in feature descriptor (PRODUCT_DEFAULT)
 * 6. The element's own default asset/text (ELEMENT_DEFAULT)
 * 7. The element's fallback token key through token resolution (TOKEN_FALLBACK)
 * 8. Omitted; listed in `missingRequired` when the element is required
 */
@JsExportCompat
interface FeatureResolver {
    /**
     * Resolve a feature's elements for a tenant, optionally scoped to an application
     * and variant. Returns null when the feature is unknown for the product type.
     */
    suspend fun resolve(
        tenant: String,
        productType: ProductType,
        featureId: String,
        applicationId: String? = null,
        variant: ThemeVariant? = null,
    ): ResolvedFeature?
}
