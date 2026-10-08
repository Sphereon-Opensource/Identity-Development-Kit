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
 * A brandable capability a product type declares, with the named design elements it renders.
 *
 * Built-in features are contributed in code by the product through a multibound
 * `FeatureDescriptorProvider` and are read-only; custom features are registered
 * per tenant through REST.
 *
 * @property featureId Identifier of the feature within its product type, for example `login`
 * @property productType Product type the feature belongs to
 * @property name Human-readable name
 * @property description Human-readable description
 * @property builtIn True for features contributed in code by the product
 * @property elements The design elements the feature declares
 * @property version Version of the feature definition
 * @property createdAt When this feature was created
 * @property updatedAt When this feature was last updated
 */
@JsExportCompat
@Serializable
data class FeatureDefinition
    @JvmOverloads
    constructor(
        val featureId: String,
        val productType: ProductType,
        val name: String,
        val description: String? = null,
        val builtIn: Boolean = false,
        val elements: List<DesignElement> = emptyList(),
        val version: Long = 1,
        val createdAt: Instant? = null,
        val updatedAt: Instant? = null,
    )
