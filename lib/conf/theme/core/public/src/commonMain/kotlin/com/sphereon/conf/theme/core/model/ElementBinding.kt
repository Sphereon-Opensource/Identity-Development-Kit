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

package com.sphereon.conf.theme.core.model

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmOverloads
import kotlin.time.Instant

/**
 * A stored design element value at tenant scope ([applicationId] null, the default for every
 * application of the product) or application scope ([applicationId] set), optionally per variant.
 * Exactly one of [asset] or [text] is set, matching the element kind.
 *
 * @property productType Product type the feature belongs to
 * @property featureId Identifier of the feature declaring the element
 * @property elementId Identifier of the design element
 * @property variant Variant the binding applies to; null = all variants
 * @property applicationId Application the binding applies to; null = tenant default for the product
 * @property asset Bound asset value for ASSET elements
 * @property text Bound text value for TEXT elements
 * @property updatedAt When this binding was last updated
 */
@JsExportCompat
@Serializable
data class ElementBinding
    @JvmOverloads
    constructor(
        val productType: ProductType,
        val featureId: String,
        val elementId: String,
        val variant: ThemeVariant? = null,
        val applicationId: String? = null,
        val asset: ThemeAssetReference? = null,
        val text: String? = null,
        val updatedAt: Instant? = null,
    )

/**
 * Value to bind to a design element. Exactly one of [asset] or [text] is set, matching the
 * element kind. [variant] and [applicationId] select the binding slot.
 */
@JsExportCompat
@Serializable
data class ElementBindingInput
    @JvmOverloads
    constructor(
        val variant: ThemeVariant? = null,
        val applicationId: String? = null,
        val asset: ThemeAssetReference? = null,
        val text: String? = null,
    )
