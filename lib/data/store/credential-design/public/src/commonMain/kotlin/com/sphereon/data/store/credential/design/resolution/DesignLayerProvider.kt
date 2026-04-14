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

package com.sphereon.data.store.credential.design.resolution

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.core.compat.JsExportIgnoreCompat
import com.sphereon.data.store.credential.design.model.ClaimPresentation
import com.sphereon.data.store.credential.design.model.DerivedRenderHintsRecord
import com.sphereon.data.store.credential.design.model.DesignSourceType
import com.sphereon.data.store.credential.design.model.EntityLocaleDesign
import com.sphereon.data.store.credential.design.model.LocalizedCredentialDisplay
import com.sphereon.data.store.credential.design.model.RenderVariantRecord
import com.sphereon.data.store.credential.design.model.ResolveCredentialDesignInput
import com.sphereon.data.store.credential.design.model.ResolveEntityDesignInput
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmOverloads

@JsExportCompat
interface DesignLayerProvider {
    val sourceType: DesignSourceType
    val authoritative: Boolean

    suspend fun resolveCredentialLayer(
        tenantId: String,
        input: ResolveCredentialDesignInput,
    ): CredentialDesignLayerResult? = null

    suspend fun resolveIssuerLayer(
        tenantId: String,
        input: ResolveEntityDesignInput,
    ): IssuerDesignLayerResult? = null

    suspend fun resolveVerifierLayer(
        tenantId: String,
        input: ResolveEntityDesignInput,
    ): VerifierDesignLayerResult? = null
}

@JsExportCompat
@Serializable
data class CredentialDesignLayerResult
    @JvmOverloads
    constructor(
        val displays: List<LocalizedCredentialDisplay> = emptyList(),
        val claims: List<ClaimPresentation> = emptyList(),
        val renderVariants: List<RenderVariantRecord> = emptyList(),
        val derivedRenderHints: DerivedRenderHintsRecord? = null,
        @JsExportIgnoreCompat
        val providedFields: Set<String>,
    )

@JsExportCompat
@Serializable
data class IssuerDesignLayerResult
    @JvmOverloads
    constructor(
        val displays: List<EntityLocaleDesign> = emptyList(),
        val renderVariants: List<RenderVariantRecord> = emptyList(),
        @JsExportIgnoreCompat
        val providedFields: Set<String>,
    )

@JsExportCompat
@Serializable
data class VerifierDesignLayerResult
    @JvmOverloads
    constructor(
        val displays: List<EntityLocaleDesign> = emptyList(),
        val renderVariants: List<RenderVariantRecord> = emptyList(),
        @JsExportIgnoreCompat
        val providedFields: Set<String>,
    )
