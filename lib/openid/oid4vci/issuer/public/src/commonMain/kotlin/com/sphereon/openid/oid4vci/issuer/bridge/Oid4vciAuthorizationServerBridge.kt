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

@file:OptIn(ExperimentalTime::class)

package com.sphereon.openid.oid4vci.issuer.bridge

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Bridge interface abstracting the AS topology from the OID4VCI issuer.
 *
 * Scoped to what differs by AS topology. Nonce lifecycle is NOT in the bridge —
 * it is issuer-owned protocol state regardless of AS configuration.
 */
@JsExportCompat
interface Oid4vciAuthorizationServerBridge {
    /** Register a pre-authorized code in the AS, returning the code string. */
    suspend fun registerPreAuthorizedCode(args: RegisterPreAuthCodeArgs): IdkResult<RegisteredPreAuthCode, IdkError>

    /** Atomically consume a pre-authorized code, returning the linked session. */
    suspend fun consumePreAuthorizedCode(args: ConsumePreAuthCodeArgs): IdkResult<ConsumedPreAuthCode, IdkError>

    /** Create an authorization context linked to issuer_state. */
    suspend fun createAuthorizationContext(args: CreateAuthContextArgs): IdkResult<AuthorizationContextRef, IdkError>

    /** Validate an access token and return the associated grant context. */
    suspend fun validateAccessToken(args: ValidateAccessTokenArgs): IdkResult<ValidatedTokenContext, IdkError>

    /** Contribute OID4VCI-specific fields to AS metadata. */
    suspend fun augmentAsMetadata(args: AugmentAsMetadataArgs): IdkResult<JsonObject, IdkError>
}

@JsExportCompat
data class RegisterPreAuthCodeArgs(
    val sessionId: String,
    val credentialConfigurationIds: List<String>,
    val txCodeRequired: Boolean,
    /** Number of digits/characters to generate for the tx_code (PIN). Null = issuer default. */
    val txCodeLength: Int? = null,
    /** tx_code input mode ("numeric" | "text"); drives the generated PIN alphabet. Null = numeric. */
    val txCodeInputMode: String? = null,
    val issuerIdentifier: String? = null,
    val useCredentialIdentifiers: Boolean = true,
)

@JsExportCompat
data class RegisteredPreAuthCode(
    val code: String,
    val txCode: String?,
)

@JsExportCompat
data class ConsumePreAuthCodeArgs(
    val code: String,
    val txCode: String?,
    val clientId: String,
)

@JsExportCompat
data class ConsumedPreAuthCode(
    val sessionId: String,
    val subject: String?,
    val credentialConfigurationIds: List<String>,
    val credentialIdentifiers: List<String>? = null,
)

@JsExportCompat
data class CreateAuthContextArgs(
    val issuerState: String,
    val credentialConfigurationIds: List<String>,
    val authorizationDetails: List<com.sphereon.openid.oid4vci.common.model.Oid4vciAuthorizationDetail>? = null,
)

@JsExportCompat
data class AuthorizationContextRef(
    val issuerState: String,
    val sessionId: String,
)

@JsExportCompat
data class ValidateAccessTokenArgs(
    val accessToken: String,
    val dpopProof: String? = null,
    /**
     * RFC 9449 §7.1: the resource server MUST verify the DPoP proof binds to *this* request,
     * which requires `htu` / `htm` matching. Pass the full request URL (with scheme + host but
     * stripped of query/fragment) and the HTTP method here so the bridge can run that check.
     * `null` only when the caller has already verified the proof itself; the bridge will skip
     * DPoP verification entirely in that case.
     */
    val httpUrl: String? = null,
    val httpMethod: String? = null,
)

@JsExportCompat
data class ValidatedWalletUnitStatusReference(
    val statusListUri: String,
    val index: String,
    val status: String? = null,
    val revoked: Boolean = false,
    val maintenanceExpiresAtEpochSeconds: Long? = null,
)

@JsExportCompat
data class ValidatedWalletInstanceAttestationEvidence(
    val evidenceId: String,
    val profile: String,
    val format: String,
    val expiresAtEpochSeconds: Long,
    val clientStatus: ValidatedWalletUnitStatusReference,
    val walletInstanceId: String? = null,
    val walletProvider: String? = null,
    val walletSolution: String? = null,
    val walletUnitId: String? = null,
    val walletAccountId: String? = null,
)

@JsExportCompat
data class ValidatedTokenContext(
    val subject: String,
    val clientId: String,
    val scope: String?,
    val credentialConfigurationIds: List<String>,
    val credentialIdentifiers: List<String>? = null,
    /**
     * RFC 9449 §6: when the access token carries `cnf.jkt`, it is bound to a DPoP key.
     * Surfaced so callers know whether the token MUST be presented with a matching DPoP proof,
     * and so layered checks (e.g. resource server pinning) can compare against the binding.
     * `null` for plain bearer tokens.
     */
    val cnfJkt: String? = null,
    /** Userinfo claims surfaced by the AS (when it embeds userinfo in the token or via a userinfo lookup).
     *  Null when the AS does not provide them or tenant config has not opted in. */
    val userinfoClaims: Map<String, JsonElement>? = null,
    /** Authentication-context-class reference, for assurance-level decisions. */
    val acr: String? = null,
    /**
     * Authentication time. Source is the `auth_time` claim (OIDC Core §2); value is
     * epoch-seconds in the token converted to an [Instant]. Null when the claim is absent.
     */
    val authTime: Instant? = null,
    /** Upstream IdP subject when the AS federated authentication to an enterprise IdP. Null for local auth. */
    val upstreamSubject: String? = null,
    /** Upstream IdP issuer when the AS federated authentication. Null for local auth. */
    val upstreamIssuer: String? = null,
    /**
     * Persisted Wallet Unit WIA/status/trust evidence surfaced by the authorization server
     * when production Wallet Instance Attestation enforcement was applied at PAR/token time.
     */
    val walletInstanceAttestation: ValidatedWalletInstanceAttestationEvidence? = null,
    /** AS-authenticated token identifier (`jti`) when supplied, bounded and non-blank. */
    val tokenId: String? = null,
    /** Access-token expiration from introspection, in epoch seconds when supplied by the AS. */
    val expiresAtEpochSeconds: Long? = null,
)

@JsExportCompat
data class AugmentAsMetadataArgs(
    val baseMetadata: JsonObject,
)
