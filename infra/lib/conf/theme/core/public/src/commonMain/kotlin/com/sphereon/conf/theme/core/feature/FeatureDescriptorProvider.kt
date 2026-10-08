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
 * Contributes the built-in brandable features of one product type.
 *
 * Products contribute an implementation into the multibound set with Metro:
 *
 * ```kotlin
 * @Inject
 * @ContributesIntoSet(SessionScope::class, binding = binding<FeatureDescriptorProvider>())
 * class AsLoginFeatureDescriptorProvider : FeatureDescriptorProvider { ... }
 * ```
 *
 * The [FeatureRegistry] merges all contributed descriptors with tenant custom features.
 * Provided [FeatureDefinition]s must have `builtIn = true`; built-in features are read-only.
 */
@JsExportCompat
interface FeatureDescriptorProvider {
    /** The product type whose features this provider declares. */
    val productType: ProductType

    /** The built-in features of [productType]. */
    fun features(): List<FeatureDefinition>
}
