/*
 * Copyright 2025 Sphereon International B.V.
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

package com.sphereon.identity.matching.model

import kotlinx.serialization.Serializable

/**
 * IDV-compatible assurance metadata captured during reconciliation.
 *
 * @property walletAssuranceLevel The assurance level reported by the wallet
 * @property oidcAcr The Authentication Context Class Reference from the OIDC provider
 * @property oidcAmr The Authentication Methods References from the OIDC provider
 * @property executionId A unique identifier for the reconciliation execution
 * @property evidenceReferenceHash A hash referencing the evidence used for verification
 */
@Serializable
data class AssuranceSummary(
    val walletAssuranceLevel: String? = null,
    val oidcAcr: String? = null,
    val oidcAmr: List<String>? = null,
    val executionId: String? = null,
    val evidenceReferenceHash: String? = null,
)
