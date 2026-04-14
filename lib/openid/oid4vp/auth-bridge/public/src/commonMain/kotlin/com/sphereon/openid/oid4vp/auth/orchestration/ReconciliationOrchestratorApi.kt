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

package com.sphereon.openid.oid4vp.auth.orchestration

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.core.compat.JsExportIgnoreCompat
import com.sphereon.identity.matching.model.AssuranceSummary
import com.sphereon.identity.reconciliation.model.KnownHolderState
import com.sphereon.openid.oid4vp.auth.model.IdvRequirementReason
import com.sphereon.openid.oid4vp.auth.model.ReconciliationPlanType
import kotlinx.serialization.json.JsonElement

/**
 * Interface for the reconciliation orchestrator that coordinates the full
 * identity verification (IDV) flow using IDK building blocks.
 *
 * The implementation lives in the portal service (service-auth-bridge), not in IDK.
 * IDK command implementations depend on this interface to delegate orchestration
 * logic to the service layer.
 *
 * Responsibilities owned by the orchestrator (portal boundary):
 * - Known-holder fast path: binding lookup, decryption, match reuse
 * - Attribute-match and IDV_REQUIRED decision
 * - OIDC reconciliation session lifecycle
 * - Canonical binding materialization from merged wallet + OIDC attributes
 */
@JsExportCompat
interface ReconciliationOrchestratorApi {
    /**
     * Check if a known identity link binding exists for the given holder key fingerprint.
     *
     * If a binding with encrypted canonical attributes is found, decrypts and returns
     * the cached identity without requiring a federation redirect.
     *
     * Supports key rotation dual-read: if no binding is found with the current hash
     * and [rawHolderKey] is provided, computes a hash with the previous key and retries.
     *
     * @param holderKeyHash HMAC hash of the holder key fingerprint (output of [ReconciliationCryptoService.hashHolderKey])
     * @param tenantId Tenant context
     * @param rawHolderKey The raw holder key (pre-hash) for dual-read fallback during key rotation. Null to skip fallback.
     * @param walletAttributes Pre-canonical wallet attributes for multi-material lookup (attribute-tuple matching). Null to skip.
     * @return [ResolvedKnownHolder] if a binding exists, null if not found
     */
    @JsExportIgnoreCompat
    suspend fun resolveKnownHolder(
        holderKeyHash: String,
        tenantId: String,
        rawHolderKey: String? = null,
        walletAttributes: Map<String, JsonElement>? = null,
    ): IdkResult<ResolvedKnownHolder?, IdkError>

    /**
     * Initiate reconciliation for an OID4VP session that requires identity verification.
     *
     * Creates a reconciliation session, generates an OIDC authorization URL for the
     * configured provider (e.g., SURF), and links the reconciliation session to the
     * OID4VP session.
     *
     * @param oid4vpSessionId The OID4VP session ID (must be in IDV_REQUIRED state)
     * @param redirectUri The redirect URI for the OIDC callback
     * @param baseUrl Optional base URL for constructing the callback URL
     * @return [ReconciliationInitiateResult] with the authorization URL
     */
    suspend fun initiateReconciliation(
        oid4vpSessionId: String,
        redirectUri: String,
        baseUrl: String? = null,
    ): IdkResult<ReconciliationInitiateResult, IdkError>

    /**
     * Handle the OIDC callback from the reconciliation provider.
     *
     * Validates the state parameter, exchanges the authorization code for tokens,
     * creates the identity match, merges wallet + OIDC attributes into a canonical
     * attribute bag, and creates an encrypted identity link binding.
     *
     * @param code The authorization code from the OIDC provider
     * @param state The state parameter used to correlate with the reconciliation session
     * @param walletAttributes The mapped wallet credential attributes (from the OID4VP session)
     * @return [ReconciliationCallbackResult] with the resolved identity and binding
     */
    @JsExportIgnoreCompat
    suspend fun handleCallback(
        code: String,
        state: String,
        walletAttributes: Map<String, JsonElement> = emptyMap(),
    ): IdkResult<ReconciliationCallbackResult, IdkError>

    /**
     * Handle reconciliation completion with pre-extracted OIDC claims from the STS.
     *
     * Called when the STS (as the single OIDC RP) has already exchanged the authorization
     * code for tokens and extracted claims. The auth-bridge receives the raw claims,
     * merges them with wallet claims, validates attributes, creates identity matches
     * and bindings, and updates the OID4VP session.
     *
     * @param oid4vpSessionId The OID4VP session ID
     * @param claims Pre-extracted OIDC claims from the STS (ID token + userinfo merged)
     * @param issuer The upstream IdP issuer URL
     * @param providerId The OIDC provider ID used by the STS
     * @return [ReconciliationCallbackResult] with the resolved identity and binding
     */
    @JsExportIgnoreCompat
    suspend fun handleCallbackWithClaims(
        oid4vpSessionId: String,
        claims: Map<String, JsonElement>,
        issuer: String,
        providerId: String,
    ): IdkResult<ReconciliationCallbackResult, IdkError>

    /**
     * Get the current reconciliation status for an OID4VP session.
     *
     * @param oid4vpSessionId The OID4VP session ID
     * @return [ReconciliationStatusResult] with the current status
     */
    suspend fun getStatus(oid4vpSessionId: String): IdkResult<ReconciliationStatusResult, IdkError>

    /**
     * Pre-evaluate reconciliation selector rules to determine if IDV can be skipped.
     *
     * Called from [completeAuthentication] before transitioning to IDV_REQUIRED.
     * The session must already be persisted with [holderIdentifierHash], [knownHolderState],
     * and [verifiedData] set.
     *
     * Evaluates the selector rules against the session context:
     * - [SkipReconciliation]: wallet claims are sufficient — executes skip logic and returns a [ResolvedKnownHolder]
     * - [UseExistingBinding]: an existing binding was found by alternative criteria — executes and returns a [ResolvedKnownHolder]
     * - [RunIdv] / [StepUp]: IDV redirect is needed — returns `null`
     * - [FailClosed]: returns an error
     *
     * @param oid4vpSessionId The OID4VP session ID (must be in IDV_REQUIRED state)
     * @param tenantId Tenant context
     * @return [ResolvedKnownHolder] if the selector resolved without redirect, null if IDV is required
     */
    suspend fun preEvaluateReconciliation(
        oid4vpSessionId: String,
        tenantId: String,
    ): IdkResult<ResolvedKnownHolder?, IdkError>
}

/**
 * Resolved binding payload from the known-holder fast path.
 *
 * Carries the full binding data needed to skip IDV when a holder is already known.
 * The [state] field indicates how the holder was matched (holder key, claim tuple, etc.).
 *
 * @property userId The internal identity ID from the match
 * @property bindingId The identity link binding ID
 * @property matchId The identity match ID
 * @property canonicalAttributes Decrypted canonical attributes from the binding
 * @property assurance Assurance metadata from the binding (ACR, AMR, etc.)
 * @property state How the holder was matched
 */
@JsExportCompat
data class ResolvedKnownHolder(
    val userId: String,
    val bindingId: String,
    val matchId: String,
    @JsExportIgnoreCompat
    val canonicalAttributes: Map<String, JsonElement>,
    val assurance: AssuranceSummary? = null,
    val state: KnownHolderState = KnownHolderState.MATCHED_HOLDER_KEY,
    /** Decrypted institution identifier (federated_subject) from the binding. */
    val decryptedInstitutionId: String? = null,
    /** Reconciliation rule version from the binding. */
    val selectorRuleVersion: String? = null,
    /** When reconciliation last happened. */
    val reconcileTime: kotlin.time.Instant? = null,
)

/**
 * Result from initiating reconciliation.
 *
 * @property sessionId The OID4VP session ID
 * @property reconciliationSessionId The created reconciliation session ID
 * @property authorizationUrl The OIDC authorization URL to redirect the user to
 * @property planType The selected reconciliation plan type
 * @property idvRequirementReason The typed reason for this IDV initiation
 */
@JsExportCompat
data class ReconciliationInitiateResult(
    val sessionId: String,
    val reconciliationSessionId: String? = null,
    val authorizationUrl: String? = null,
    val planType: ReconciliationPlanType? = null,
    val idvRequirementReason: IdvRequirementReason? = null,
)

/**
 * Result from handling the OIDC callback.
 *
 * @property oid4vpSessionId The OID4VP session ID
 * @property matchId The identity match ID
 * @property resolvedUserId The resolved external subject (user ID)
 * @property canonicalAttributes The merged wallet + OIDC canonical attributes
 * @property assurance Assurance metadata from the reconciliation (ACR, AMR, execution ID)
 */
@JsExportCompat
data class ReconciliationCallbackResult(
    val oid4vpSessionId: String,
    val matchId: String,
    val resolvedUserId: String?,
    @JsExportIgnoreCompat
    val canonicalAttributes: Map<String, JsonElement> = emptyMap(),
    val assurance: AssuranceSummary? = null,
)

/**
 * Result from checking reconciliation status.
 *
 * @property sessionId The OID4VP session ID
 * @property status The current reconciliation status name
 * @property message Optional message about the reconciliation state
 * @property planType The selected reconciliation plan type, when known
 * @property idvRequirementReason The typed reason for this IDV flow, when known
 */
@JsExportCompat
data class ReconciliationStatusResult(
    val sessionId: String,
    val status: String,
    val message: String? = null,
    val planType: ReconciliationPlanType? = null,
    val idvRequirementReason: IdvRequirementReason? = null,
)
