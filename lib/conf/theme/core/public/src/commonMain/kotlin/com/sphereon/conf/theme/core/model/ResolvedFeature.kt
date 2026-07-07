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
import com.sphereon.core.compat.JsExportIgnoreCompat
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmOverloads

/**
 * The effective design element values of one feature for a tenant, optionally scoped
 * to an application and variant.
 *
 * @property productType Product type the feature belongs to
 * @property featureId Identifier of the resolved feature
 * @property tenantId The tenant the feature was resolved for
 * @property applicationId The application the feature was resolved for (null = tenant defaults)
 * @property variant The variant the feature was resolved for
 * @property etag Content hash for conditional HTTP caching
 * @property elements Map of element id to resolved value
 * @property missingRequired Element ids of required elements that resolved to nothing
 */
@JsExportCompat
@Serializable
data class ResolvedFeature
    @JvmOverloads
    constructor(
        val productType: ProductType,
        val featureId: String,
        val tenantId: String,
        val applicationId: String? = null,
        val variant: ThemeVariant? = null,
        val etag: String? = null,
        @JsExportIgnoreCompat
        val elements: Map<String, ResolvedElement> = emptyMap(),
        val missingRequired: List<String>? = null,
    )
