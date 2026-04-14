/*
 * © 2026 Sphereon International B.V.
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

package com.sphereon.openid.oid4vp.auth.http.model

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.openid.oid4vp.auth.model.IdvRequirementReason
import com.sphereon.openid.oid4vp.auth.model.ReconciliationPlanType
import kotlinx.serialization.Serializable

/**
 * Response from initiating identity verification (reconciliation).
 *
 * POST /auth/oid4vp/sessions/{sessionId}/idv/initiate
 *
 * @property sessionId The OID4VP session ID.
 * @property redirectUrl The OIDC authorization URL to redirect the user to for identity verification.
 * @property planType The selected reconciliation plan type.
 * @property idvRequirementReason The typed reason for this IDV flow.
 */
@Serializable
@JsExportCompat
data class IdvInitiateResponse(
    val sessionId: String,
    val redirectUrl: String? = null,
    val planType: ReconciliationPlanType? = null,
    val idvRequirementReason: IdvRequirementReason? = null,
)

/**
 * Response from checking identity verification status.
 *
 * GET /auth/oid4vp/sessions/{sessionId}/idv/status
 *
 * @property sessionId The OID4VP session ID.
 * @property status The current reconciliation status.
 * @property message Optional message about the reconciliation state.
 * @property planType The selected reconciliation plan type.
 * @property idvRequirementReason The typed reason for this IDV flow.
 */
@Serializable
@JsExportCompat
data class IdvStatusResponse(
    val sessionId: String,
    val status: String,
    val message: String? = null,
    val planType: ReconciliationPlanType? = null,
    val idvRequirementReason: IdvRequirementReason? = null,
)
