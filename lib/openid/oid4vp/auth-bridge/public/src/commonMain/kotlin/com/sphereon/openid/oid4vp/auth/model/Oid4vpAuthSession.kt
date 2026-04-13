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
 *
 */

package com.sphereon.openid.oid4vp.auth.model

import com.sphereon.identity.reconciliation.model.KnownHolderState
import com.sphereon.openid.oid4vp.universal.VerifiedData
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * Represents an OID4VP authentication session.
 *
 * This session tracks the state of an OID4VP authentication flow, linking
 * the Universal OID4VP session with OAuth2 authentication.
 *
 * @property sessionId Internal session ID for this auth bridge session.
 * @property correlationId Universal OID4VP correlation ID for the underlying verification session.
 * @property oauthSessionId Optional linked OAuth2 session ID.
 * @property queryId Query ID used for claims mapping configuration lookup.
 * @property status Current session status.
 * @property verifiedData Cached verified data from Universal OID4VP (populated when status=VERIFIED).
 * @property resolvedUserId User ID after user resolution (populated when status=COMPLETED).
 * @property errorMessage Error message if status=ERROR.
 * @property reconciliationSessionId Linked reconciliation session ID when IDV is in progress.
 * @property holderIdentifierHash HMAC hash of the holder key fingerprint (used for reconciliation binding lookup/creation).
 * @property holderHashKeyVersion Key version used to produce [holderIdentifierHash] (needed to create bindings without re-hashing).
 * @property knownHolderState Selector-facing known-holder outcome captured during fast-path resolution.
 * @property idvRequirementReason Typed reason for entering `IDV_REQUIRED`.
 * @property idvMessage Optional message for frontend explaining why IDV is needed.
 * @property createdAt Session creation timestamp.
 * @property updatedAt Last update timestamp.
 * @property expiresAt Session expiration timestamp.
 */
@Serializable
data class Oid4vpAuthSession(
    @SerialName("session_id")
    val sessionId: String,
    @SerialName("correlation_id")
    val correlationId: String,
    @SerialName("oauth_session_id")
    val oauthSessionId: String? = null,
    @SerialName("query_id")
    val queryId: String,
    val status: Oid4vpAuthSessionStatus,
    @SerialName("verified_data")
    val verifiedData: VerifiedData? = null,
    @SerialName("resolved_user_id")
    val resolvedUserId: String? = null,
    @SerialName("error_message")
    val errorMessage: String? = null,
    @SerialName("reconciliation_session_id")
    val reconciliationSessionId: String? = null,
    @SerialName("material_profile_id")
    val materialProfileId: String? = null,
    @SerialName("reconciliation_plan_type")
    val reconciliationPlanType: ReconciliationPlanType? = null,
    @SerialName("holder_identifier_hash")
    val holderIdentifierHash: String? = null,
    @SerialName("holder_hash_key_version")
    val holderHashKeyVersion: String? = null,
    @SerialName("known_holder_state")
    val knownHolderState: KnownHolderState? = null,
    @SerialName("idv_requirement_reason")
    val idvRequirementReason: IdvRequirementReason? = null,
    @SerialName("raw_holder_key_fingerprint")
    val rawHolderKeyFingerprint: String? = null,
    @SerialName("idv_message")
    val idvMessage: String? = null,
    @SerialName("requested_projection")
    val requestedProjection: String? = null,
    @SerialName("force_reconciliation")
    val forceReconciliation: Boolean = false,
    @SerialName("created_at")
    val createdAt: Instant,
    @SerialName("updated_at")
    val updatedAt: Instant,
    @SerialName("expires_at")
    val expiresAt: Instant,
) {
    /**
     * Check if the session is still valid (not expired).
     */
    fun isValid(now: Instant): Boolean = now < expiresAt && status != Oid4vpAuthSessionStatus.EXPIRED

    /**
     * Check if authentication can be completed (status is VERIFIED).
     */
    fun canComplete(): Boolean = status == Oid4vpAuthSessionStatus.VERIFIED

    /**
     * Check if the session has completed successfully.
     */
    fun isCompleted(): Boolean = status == Oid4vpAuthSessionStatus.COMPLETED

    /**
     * Check if the session has an error.
     */
    fun hasError(): Boolean = status == Oid4vpAuthSessionStatus.ERROR

    /**
     * Check if identity verification (reconciliation) is required.
     */
    fun isIdvRequired(): Boolean = status == Oid4vpAuthSessionStatus.IDV_REQUIRED
}
