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

package com.sphereon.data.store.credential.design.model

import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class AppliedDesignLayer(
    val sourceType: DesignSourceType,
    val sourceUrl: String? = null,
    val priority: Int,
    val authoritative: Boolean = false,
)

@Serializable
data class ResolvedCredentialDesign(
    val design: CredentialDesignRecord,
    val issuerDesign: IssuerDesignRecord? = null,
    val verifierDesign: VerifierDesignRecord? = null,
    val renderVariants: List<RenderVariantRecord>,
    val derivedRenderHints: DerivedRenderHintsRecord? = null,
    val appliedLayers: List<AppliedDesignLayer>,
    val lockedFields: Map<String, DesignSourceType>,
    val resolvedAt: Instant,
    val etag: String? = null,
)

@Serializable
data class ResolvedIssuerDesign(
    val design: IssuerDesignRecord,
    val renderVariants: List<RenderVariantRecord>,
    val appliedLayers: List<AppliedDesignLayer>,
    val lockedFields: Map<String, DesignSourceType>,
    val resolvedAt: Instant,
    val etag: String? = null,
)

@Serializable
data class ResolvedVerifierDesign(
    val design: VerifierDesignRecord,
    val renderVariants: List<RenderVariantRecord>,
    val appliedLayers: List<AppliedDesignLayer>,
    val lockedFields: Map<String, DesignSourceType>,
    val resolvedAt: Instant,
    val etag: String? = null,
)
