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

package com.sphereon.openid.oid4vp.verifier.model

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.oauth2.common.model.AuthorizationRequest
import com.sphereon.openid.oid4vp.common.ResponseMode
import com.sphereon.openid.oid4vp.dcql.DcqlQuery
import com.sphereon.openid.oid4vp.verifier.ParsedAuthorizationResponse
import com.sphereon.openid.oid4vp.verifier.ValidationResult
import com.sphereon.statuslist.CredentialStatusPolicy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Authorization session status aligned with Universal OID4VP.
 *
 * The `@SerialName` annotations map each constant to its snake_case wire format
 * (e.g. `"authorization_request_created"`). This is required because the wallet agent
 * (Node.js) returns status values in snake_case JSON, and without explicit serial names
 * kotlinx-serialization defaults to the uppercase enum constant names which causes
 * deserialization failures.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("AuthorizationSessionStatus", exact = true)
@JsExportCompat
@Serializable
enum class AuthorizationSessionStatus {
    @SerialName("authorization_request_created")
    AUTHORIZATION_REQUEST_CREATED,

    @SerialName("authorization_request_retrieved")
    AUTHORIZATION_REQUEST_RETRIEVED,

    @SerialName("authorization_response_received")
    AUTHORIZATION_RESPONSE_RECEIVED,

    @SerialName("authorization_response_verified")
    AUTHORIZATION_RESPONSE_VERIFIED,

    @SerialName("error")
    ERROR,
}

/**
 * Callback configuration for status updates (webhook).
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("AuthorizationSessionCallbackConfig", exact = true)
@JsExportCompat
data class AuthorizationSessionCallbackConfig(
    val url: String,
    /**
     * If empty, all status transitions may be emitted.
     */
    val statuses: List<AuthorizationSessionStatus> = emptyList(),
)

/**
 * Additional error information for a failed session.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("AuthorizationSessionError", exact = true)
@JsExportCompat
data class AuthorizationSessionError(
    val code: String,
    val message: String,
)

/**
 * Inputs for creating an authorization session.
 *
 * This is intentionally minimal in Phase 1; it will be extended in later phases
 * (configuration stores, request_uri handling, verified data, etc).
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("AuthorizationSessionCreateArgs", exact = true)
@JsExportCompat
data class AuthorizationSessionCreateArgs(
    /**
     * Optional reference to a pre-configured query (configuration mode).
     */
    val queryId: String? = null,
    /**
     * Inline DCQL query (direct mode).
     */
    val dcqlQuery: DcqlQuery? = null,
    val clientId: String,
    val responseMode: ResponseMode = ResponseMode.DIRECT_POST,
    val responseUri: String? = null,
    val redirectUri: String? = null,
    val nonce: String,
    val state: String? = null,
    val callback: AuthorizationSessionCallbackConfig? = null,
)

/**
 * Stored authorization session state.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("AuthorizationSession", exact = true)
@JsExportCompat
data class AuthorizationSession(
    val sessionId: String,
    /**
     * Business key / correlation id.
     */
    val correlationId: String,
    val queryId: String? = null,
    val dcqlQuery: DcqlQuery,
    /**
     * Version snapshot of the DCQL query this session was created against, when the query came
     * from a version-history store. The session resolves this exact `(dcqlQueryId,
     * dcqlQueryVersion)` for its full lifetime, so a later edit of the query header does not
     * change what an in-flight session validates against. Both null for inline DCQL queries or
     * stores without versioning.
     */
    val dcqlQueryId: String? = null,
    val dcqlQueryVersion: Int? = null,
    val authorizationRequest: AuthorizationRequest,
    val status: AuthorizationSessionStatus,
    val error: AuthorizationSessionError? = null,
    val parsedResponse: ParsedAuthorizationResponse? = null,
    val validationResult: ValidationResult? = null,
    val callback: AuthorizationSessionCallbackConfig? = null,
    /**
     * KMS reference (alias + provider id) for the ephemeral encryption keypair the
     * wallet uses to encrypt a `direct_post.jwt` JARM response. Set when the auth request
     * was created with `responseMode == DIRECT_POST_JWT`. Resolved back to a `KeyInfo` at
     * `/auth/response` time so the verifier can decrypt. NEVER stores key material here —
     * the private key lives in the ephemeral KMS provider (memory keystore, APP-scoped).
     */
    val jarmEncryptionKeyAlias: String? = null,
    val jarmEncryptionKeyProviderId: String? = null,
    /**
     * Optional one-time invitation token bound to this verifier session at create
     * time. Carried forward into [com.sphereon.openid.oid4vp.verifier.hook.PostPresentationHookArgs.boundInvitationToken]
     * so a downstream subscriber (e.g. VDX's `VerifierPresentationConsumeHookCommand`)
     * can correlate a successful presentation back to the originating invitation
     * and call `invitationService.redeem(...)`. Mirrors
     * `IssuanceSession.boundUsageToken` on the OID4VCI side.
     */
    val boundInvitationToken: String? = null,
    /**
     * Optional per-session allow list of post-presentation hook command IDs. The
     * dispatcher intersects the deployment-resolved hook set with this list before
     * invocation; null means "all deployment-registered hooks may fire". Mirrors
     * `IssuanceSession.postIssuanceHookAllowList` on the OID4VCI side.
     */
    val postPresentationHookAllowList: List<String>? = null,
    /**
     * Per-DCQL-credential-query credential status policy, keyed by the DCQL credential query `id`,
     * pinned for this session's lifetime. Read at response validation to decide whether a received
     * credential's resolved status is acceptable. Null / missing entry → the strict default
     * ([com.sphereon.statuslist.CredentialStatusPolicy] defaults). Only enforced when a non-empty set
     * of `CredentialStatusVerifier` implementations is on the verifier's classpath.
     */
    val credentialStatusPolicies: Map<String, CredentialStatusPolicy>? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val expiresAt: Long,
)
