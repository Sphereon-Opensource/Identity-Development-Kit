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

package com.sphereon.openid.oid4vp.verifier

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.resolution.managed.ManagedIdentifierOptsOrResult
import com.sphereon.oauth2.common.jarm.JarmMode
import com.sphereon.oauth2.common.model.AuthorizationRequest
import com.sphereon.openid.oid4vp.common.ClientIdScheme
import com.sphereon.openid.oid4vp.common.ClientMetadata
import com.sphereon.openid.oid4vp.common.ResponseMode
import com.sphereon.openid.oid4vp.common.VpToken
import com.sphereon.openid.oid4vp.dcql.DcqlQuery
import com.sphereon.statuslist.CredentialStatusPolicy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

// ============================================================================
// Args Types for Verifier Commands
// ============================================================================

/**
 * Arguments for creating an OpenID4VP authorization request.
 *
 * OpenID4VP 1.0 Final:
 * - dcqlQuery: REQUIRED - The DCQL query requesting specific credentials (NOT Presentation Exchange!)
 * - clientId: REQUIRED - The verifier's identifier
 * - responseUri: REQUIRED for direct_post - Where to send the response
 * - responseMode: How the wallet should respond (direct_post, fragment, query)
 * - nonce: REQUIRED - Replay protection
 * - state: OPTIONAL - Request correlation
 * - clientMetadata: OPTIONAL - Verifier metadata (inline or via client_metadata_uri)
 *
 * @property dcqlQuery The DCQL query for credential selection
 * @property clientId The verifier's client identifier
 * @property responseUri The URI where the wallet should send the response (for direct_post)
 * @property redirectUri The redirect URI (for fragment/query response modes)
 * @property responseMode The response mode (defaults to DIRECT_POST)
 * @property nonce Replay protection nonce (REQUIRED per OpenID4VP)
 * @property state Request correlation state
 * @property clientMetadata Optional client metadata to embed in the request
 * @property clientMetadataUri Optional URI to fetch client metadata from
 * @property clientIdScheme The client ID scheme to use (defaults to REDIRECT_URI)
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("CreateAuthorizationRequestArgs", exact = true)
@JsExportCompat
data class CreateAuthorizationRequestArgs(
    val dcqlQuery: DcqlQuery,
    val clientId: String,
    val responseUri: String? = null,
    val redirectUri: String? = null,
    val responseMode: ResponseMode = ResponseMode.DIRECT_POST,
    val nonce: String,
    val state: String? = null,
    val clientMetadata: ClientMetadata? = null,
    val clientMetadataUri: String? = null,
    val clientIdScheme: ClientIdScheme = ClientIdScheme.REDIRECT_URI,
    /**
     * KMS reference for the wallet→verifier JARM response encryption keypair. Caller
     * generates the keypair in an ephemeral KMS provider and passes the alias/providerId
     * so they land on the persisted `AuthorizationSession`; the response endpoint resolves
     * the alias back to a `KeyInfo` for JWE decryption. Required for `direct_post.jwt`;
     * ignored for unencrypted modes.
     */
    val jarmEncryptionKeyAlias: String? = null,
    val jarmEncryptionKeyProviderId: String? = null,
    /**
     * HTTP method the wallet must use when fetching the JAR from `request_uri` per OID4VP
     * §5.10 (`get` or `post`). Lands as a `request_uri_method` query parameter on the OUTER
     * OAuth2 authorization URL (the `openid4vp://` deeplink), NOT inside the signed Request
     * Object — the wallet needs it BEFORE fetching the JAR. Null means the parameter is
     * omitted entirely, in which case the wallet defaults to GET per RFC 9101.
     */
    val requestUriMethod: String? = null,
    /**
     * Optional one-time invitation token bound to this verifier session. Stored on the
     * resulting [com.sphereon.openid.oid4vp.verifier.model.AuthorizationSession.boundInvitationToken]
     * and surfaced again on
     * [com.sphereon.openid.oid4vp.verifier.hook.PostPresentationHookArgs.boundInvitationToken]
     * after a successful presentation, so a downstream subscriber can correlate the
     * presentation back to the originating invitation. Mirrors
     * [com.sphereon.openid.oid4vci.issuer.command.CreateCredentialOfferArgs.boundUsageToken]
     * on the OID4VCI side.
     */
    val boundInvitationToken: String? = null,
    /**
     * Optional per-session allow list of post-presentation hook command IDs. When non-null,
     * the dispatcher intersects the deployment-resolved hook set (everything matching
     * `hook.post-presentation.**`) with this list before invocation, so only the hooks
     * the caller explicitly authorised for THIS verifier session fire. Null means "all
     * deployment-registered hooks fire" (subject to each hook's own `supports()` check).
     *
     * Mirrors [com.sphereon.openid.oid4vci.issuer.command.CreateCredentialOfferArgs.postIssuanceHookAllowList]
     * on the OID4VCI side. Use when a deployment registers multiple hooks (audit,
     * analytics, redemption) and a specific flow / tenant wants to opt in to only some.
     */
    val postPresentationHookAllowList: List<String>? = null,
    /**
     * Version snapshot of the DCQL query this request was built from, when it came from a
     * version-history store. Threaded onto the resulting
     * [com.sphereon.openid.oid4vp.verifier.model.AuthorizationSession] so the session pins the
     * exact `(dcqlQueryId, dcqlQueryVersion)` for its lifetime. Both null for inline DCQL
     * queries or stores without versioning.
     */
    val dcqlQueryId: String? = null,
    val dcqlQueryVersion: Int? = null,
    /**
     * Optional per-DCQL-credential-query credential status policy, keyed by the DCQL credential query
     * `id`. Decides how the verifier treats a received credential's resolved status (accept revoked /
     * suspended, require a status list, fail-closed on unresolvable). Threaded onto the resulting
     * [com.sphereon.openid.oid4vp.verifier.model.AuthorizationSession] and applied at response
     * validation. A query with no entry (or null map) uses the strict default
     * ([CredentialStatusPolicy] defaults: reject revoked/suspended, status optional, fail closed).
     *
     * Deliberately NOT a field on the wire [com.sphereon.openid.oid4vp.dcql.DcqlCredentialQuery]:
     * OpenID4VP 1.0 final DCQL defines no status concept, so this verifier-internal policy stays out
     * of the signed `dcql_query` delivered to the wallet. Status checking only runs when a non-empty
     * set of `CredentialStatusVerifier` implementations is on the verifier's classpath.
     */
    val credentialStatusPolicies: Map<String, CredentialStatusPolicy>? = null,
)

/**
 * Result of creating an authorization request.
 *
 * @property request The created authorization request
 * @property requestUri Optional URI if the request was pushed to a PAR endpoint
 * @property sessionId Optional session ID for correlating responses
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("CreatedAuthorizationRequest", exact = true)
@JsExportCompat
data class CreatedAuthorizationRequest(
    val request: AuthorizationRequest,
    val requestUri: String? = null,
    val sessionId: String? = null,
)

/**
 * Arguments for parsing an authorization response.
 *
 * When response_mode is direct_post.jwt (JARM), the response contains a "response"
 * parameter with a JWT-secured authorization response instead of individual parameters.
 *
 * @property responseParams The response parameters (from POST body or URL query/fragment)
 * @property originalRequest The original authorization request for correlation
 * @property jarmDecryptionKey Optional decryption key for encrypted JARM responses
 * @property jarmExpectedAudience Expected audience for JARM JWT validation (verifier's client_id)
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ParseAuthorizationResponseArgs", exact = true)
@JsExportCompat
data class ParseAuthorizationResponseArgs(
    val responseParams: Map<String, String>,
    val originalRequest: AuthorizationRequest? = null,
    @kotlinx.serialization.Transient
    val jarmDecryptionKey: ManagedIdentifierOptsOrResult? = null,
    val jarmExpectedAudience: String? = null,
    @kotlinx.serialization.Transient
    val jarmSignerIdentifier: ManagedIdentifierOptsOrResult? = null,
)

/**
 * Parsed authorization response from the wallet.
 *
 * OpenID4VP 1.0 Final:
 * - vpToken: The VP token containing presentations (NOT presentation_submission!)
 * - state: State for request correlation
 * - nonce: From KB-JWT or mdoc DeviceAuth
 *
 * When response_mode is direct_post.jwt (JARM), additional fields indicate JARM processing:
 * - jarmMode: The JARM mode that was used (SIGNED, ENCRYPTED, SIGNED_ENCRYPTED)
 * - jarmIssuer: The wallet's issuer claim from JARM JWT
 *
 * @property vpToken The VP token (single or multiple presentations)
 * @property state The state from the original request
 * @property rawVpToken The raw vp_token string(s) before parsing
 * @property jarmMode The JARM mode if response was JWT-secured (null for plain responses)
 * @property jarmIssuer The wallet issuer from JARM JWT (null for plain responses)
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ParsedAuthorizationResponse", exact = true)
@JsExportCompat
data class ParsedAuthorizationResponse(
    val vpToken: VpToken,
    val state: String? = null,
    val rawVpToken: String,
    val jarmMode: JarmMode? = null,
    val jarmIssuer: String? = null,
)

/**
 * Arguments for validating an authorization response against the original request.
 *
 * @property parsedResponse The parsed authorization response
 * @property originalRequest The original authorization request
 * @property dcqlQuery The DCQL query from the original request
 * @property expectedNonce The expected nonce value
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ValidateAuthorizationResponseArgs", exact = true)
@JsExportCompat
data class ValidateAuthorizationResponseArgs(
    val parsedResponse: ParsedAuthorizationResponse,
    val originalRequest: AuthorizationRequest,
    val dcqlQuery: DcqlQuery,
    val expectedNonce: String,
    /**
     * mDoc-only: raw 32-byte SHA-256 thumbprint (RFC 7638) of the verifier's encryption-
     * key JWK, used to reconstruct the §B.2.6 OpenID4VPHandover for DeviceAuth signature
     * verification. Required for `direct_post.jwt`; null for plain `direct_post`.
     */
    val verifierEncryptionJwkThumbprint: ByteArray? = null,
)

/**
 * Result of validating an authorization response.
 *
 * @property valid Whether the response is valid
 * @property matchedCredentials Credentials that matched the DCQL query
 * @property errors Validation errors (if any)
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ValidationResult", exact = true)
@JsExportCompat
data class ValidationResult(
    val valid: Boolean,
    val matchedCredentials: List<MatchedCredential> = emptyList(),
    val errors: List<String> = emptyList(),
)

/**
 * A credential that matched a DCQL query.
 *
 * @property credentialQueryId The ID from the DCQL credential query
 * @property format The credential format (e.g., "dc+sd-jwt", "mso_mdoc")
 * @property presentation The presentation string
 * @property disclosedClaims Claims that were disclosed
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("MatchedCredential", exact = true)
@JsExportCompat
data class MatchedCredential(
    val credentialQueryId: String,
    val format: String,
    val presentation: String,
    val disclosedClaims: Map<String, Any?> = emptyMap(),
)

/**
 * Arguments for verifying holder binding in a VP token.
 *
 * @property presentation The presentation to verify
 * @property format The credential format
 * @property expectedNonce The expected nonce value
 * @property expectedAudience The expected audience (verifier client_id)
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("VerifyHolderBindingArgs", exact = true)
@JsExportCompat
data class VerifyHolderBindingArgs(
    val presentation: String,
    val format: String,
    val expectedNonce: String,
    val expectedAudience: String,
    /**
     * mDoc-only: the verifier's OID4VP `client_id` (after §5.9.3 prefixing). Used with
     * [responseUri] and [verifierEncryptionJwkThumbprint] to reconstruct the
     * SessionTranscript per OID4VP 1.0 final §B.2.6. Ignored for SD-JWT and JWT VP.
     */
    val clientId: String? = null,
    /**
     * mDoc-only: the verifier's `response_uri` from the OID4VP authorization request.
     */
    val responseUri: String? = null,
    /**
     * mDoc-only: raw 32-byte SHA-256 thumbprint (RFC 7638) of the verifier's encryption-
     * key JWK. Required for encrypted response modes (`direct_post.jwt`, `dc_api.jwt`);
     * null for plain modes per OID4VP 1.0 final §B.2.6.2.
     */
    val verifierEncryptionJwkThumbprint: ByteArray? = null,
)

/**
 * Result of verifying holder binding.
 *
 * OpenID4VP 1.0 Final Section 7.3 requires verification of cryptographic holder binding:
 * - SD-JWT: KB-JWT signature verification, nonce/audience validation, sd_hash verification
 * - mDoc: DeviceAuth COSE signature verification over SessionTranscript
 * - JWT VP: JWT proof signature verification with nonce/audience
 *
 * @property verified Whether the holder binding was cryptographically verified
 * @property holderKey The holder's public key in JWK format (if extractable)
 * @property bindingMethod The type of holder binding (kb-jwt, mdoc-device-auth, jwt-proof)
 * @property signatureValid Whether the signature verification passed
 * @property nonceValid Whether the nonce claim matched the expected value
 * @property audienceValid Whether the audience claim matched the expected value
 * @property sdHashValid For SD-JWT: whether sd_hash binding is valid
 * @property errors Verification errors (if any)
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("HolderBindingResult", exact = true)
@JsExportCompat
data class HolderBindingResult(
    val verified: Boolean,
    val holderKey: String? = null,
    val bindingMethod: String? = null,
    val signatureValid: Boolean = false,
    val nonceValid: Boolean = false,
    val audienceValid: Boolean = false,
    val sdHashValid: Boolean? = null,
    val errors: List<String> = emptyList(),
    /**
     * For SD-JWT credentials: did the verifier resolve a verification key for the
     * issuer JWT through its trust chain? `false` here means the failure is upstream
     * of the cryptographic check — the resolver chain (KMS / DID / x5c / pinned JWKS)
     * never produced a usable key. In that state [signatureValid] is misleading on
     * its own and you'd be chasing a "bad signature" that doesn't exist.
     *
     * Default: null (unknown / not surfaced by this binding method, e.g. mdoc).
     */
    val issuerTrustEstablished: Boolean? = null,
    /**
     * For SD-JWT credentials: result of the cryptographic check on the issuer JWT
     * once a key was resolved. null if [issuerTrustEstablished] is false (no key →
     * nothing to check) or if this binding method doesn't produce the field.
     */
    val issuerCryptoVerified: Boolean? = null,
)

/**
 * Arguments for building an authorization request URI.
 *
 * @property request The authorization request
 * @property scheme The URI scheme to use (defaults to openid4vp)
 * @property useRequestUri Whether to use request_uri parameter (requires prior PAR)
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("BuildAuthorizationRequestUriArgs", exact = true)
@JsExportCompat
data class BuildAuthorizationRequestUriArgs(
    val request: AuthorizationRequest,
    val scheme: Oid4vpUriScheme = Oid4vpUriScheme.OPENID4VP,
    val useRequestUri: Boolean = false,
    val requestUri: String? = null,
    /**
     * Full URI prefix to use instead of `<scheme>://`. Set when the wallet is reached via an
     * HTTPS endpoint rather than a custom mobile URI scheme. Covers:
     *
     *  - **Web wallets** running as a service (e.g. the OIDF conformance suite's
     *    `https://demo.certification.openid.net/test/a/Verifier-sdjwt/authorize`).
     *  - **Universal links / App Links** — HTTPS URLs claimed by a mobile-app handler.
     *  - Any other wallet endpoint published as a full URL rather than a scheme name.
     *
     * When non-null, [scheme] is ignored. The outer authorization-request URI is constructed
     * as `<deeplinkPrefix>?param=…` (or `&param=…` if the prefix already contains a `?`).
     */
    val deeplinkPrefix: String? = null,
)

/**
 * OpenID4VP URI schemes.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("Oid4vpUriScheme", exact = true)
@Serializable
@JsExportCompat
enum class Oid4vpUriScheme(
    val scheme: String,
) {
    /**
     * Standard OpenID4VP scheme
     */
    @SerialName("openid4vp")
    OPENID4VP("openid4vp"),

    /**
     * Legacy OpenID scheme
     */
    @SerialName("openid")
    OPENID("openid"),

    /**
     * Sphereon mobile-wallet vendor scheme (`oid4vp://`). Not spec-defined; included so
     * deployments targeting wallets that register this scheme can drive the outer
     * authorization-request URI without falling back to a string-only override.
     */
    @SerialName("oid4vp")
    OID4VP("oid4vp"),

    /**
     * HAIP (High Assurance Identity Profile) scheme
     */
    @SerialName("haip")
    HAIP("haip"),
    ;

    companion object {
        fun fromValue(value: String): Oid4vpUriScheme? = entries.find { it.scheme == value }
    }
}

// ============================================================================
// Session Management
// ============================================================================

/**
 * Represents an active OpenID4VP session on the verifier side.
 *
 * @property sessionId Unique session identifier
 * @property request The authorization request for this session
 * @property dcqlQuery The DCQL query for this session
 * @property state Request correlation state
 * @property nonce Replay protection nonce
 * @property createdAt Timestamp when session was created
 * @property expiresAt Timestamp when session expires
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("Oid4vpRpSession", exact = true)
@JsExportCompat
data class Oid4vpVerifierSession(
    val sessionId: String,
    val request: AuthorizationRequest,
    val dcqlQuery: DcqlQuery,
    val state: String?,
    val nonce: String,
    val createdAt: Long,
    val expiresAt: Long,
)

// ============================================================================
// Response Code Protection - OpenID4VP 1.0 Section 14.3.3
// ============================================================================

/**
 * Arguments for handling a direct_post authorization response.
 *
 * Per OpenID4VP 1.0 Section 14.3.3 - Protection of Authorization Response Data:
 * The RP backend receives the authorization response, validates it, and returns
 * a redirect_uri with a response_code. The frontend then uses the response_code
 * to retrieve the validated response data.
 *
 * @property responseParams The response parameters (from POST body)
 * @property originalRequest The original authorization request for correlation
 * @property dcqlQuery The DCQL query from the original request, used for validation
 * @property redirectUri The base redirect_uri to return to the wallet
 * @property jarmDecryptionKey Optional decryption key for encrypted JARM responses
 * @property jarmExpectedAudience Expected audience for JARM JWT validation (verifier's client_id)
 * @property responseCodeTtlSeconds Time-to-live for the response code in seconds (default: 300 = 5 minutes)
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("HandleDirectPostResponseArgs", exact = true)
@JsExportCompat
data class HandleDirectPostResponseArgs(
    val responseParams: Map<String, String>,
    val originalRequest: AuthorizationRequest,
    val dcqlQuery: DcqlQuery,
    val redirectUri: String,
    @kotlinx.serialization.Transient
    val jarmDecryptionKey: ManagedIdentifierOptsOrResult? = null,
    val jarmExpectedAudience: String? = null,
    @kotlinx.serialization.Transient
    val jarmSignerIdentifier: ManagedIdentifierOptsOrResult? = null,
    val responseCodeTtlSeconds: Long = 300,
    /**
     * mDoc-only: raw 32-byte SHA-256 thumbprint (RFC 7638) of the verifier's encryption-
     * key JWK, threaded into the §B.2.6 OpenID4VPHandover during DeviceAuth verification.
     * Required for `direct_post.jwt`; null for plain `direct_post`.
     */
    val verifierEncryptionJwkThumbprint: ByteArray? = null,
)

/**
 * Result of handling a direct_post response.
 *
 * Contains the redirect_uri with the response_code appended.
 * The wallet should redirect the user agent to this URI.
 *
 * @property redirectUri The redirect URI with response_code (e.g., "https://client.example/cb?response_code=xxx")
 * @property responseCode The generated response code
 * @property expiresAt Timestamp when the response code expires (epoch milliseconds)
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DirectPostHandledResponse", exact = true)
@Serializable
@JsExportCompat
data class DirectPostHandledResponse(
    val redirectUri: String,
    val responseCode: String,
    val expiresAt: Long,
)

/**
 * Arguments for retrieving an authorization response by response_code.
 *
 * Per OpenID4VP 1.0 Section 14.3.3:
 * The frontend uses the response_code to retrieve the validated response.
 * The response_code is single-use and expires after retrieval or timeout.
 *
 * @property responseCode The response code from the redirect URI
 * @property markAsUsed Whether to mark the response_code as used after retrieval (default: true)
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("RetrieveAuthorizationResponseArgs", exact = true)
@JsExportCompat
data class RetrieveAuthorizationResponseArgs(
    val responseCode: String,
    val markAsUsed: Boolean = true,
)

/**
 * Retrieved authorization response with validation result.
 *
 * @property parsedResponse The parsed authorization response
 * @property validationResult The validation result (if validation was performed during direct_post handling)
 * @property state The state from the original request
 * @property retrievedAt Timestamp when the response was retrieved (epoch milliseconds)
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("RetrievedAuthorizationResponse", exact = true)
@JsExportCompat
data class RetrievedAuthorizationResponse(
    val parsedResponse: ParsedAuthorizationResponse,
    val validationResult: ValidationResult? = null,
    val state: String? = null,
    val retrievedAt: Long,
)

/**
 * Stored authorization response entry in the response code store.
 *
 * This is an internal model used by ResponseCodeStore implementations.
 *
 * @property responseCode The unique response code
 * @property parsedResponse The parsed authorization response
 * @property validationResult Optional validation result
 * @property state The state from the original request
 * @property createdAt Timestamp when the entry was created (epoch milliseconds)
 * @property expiresAt Timestamp when the entry expires (epoch milliseconds)
 * @property used Whether the response code has been used
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("StoredAuthorizationResponse", exact = true)
@JsExportCompat
data class StoredAuthorizationResponse(
    val responseCode: String,
    val parsedResponse: ParsedAuthorizationResponse,
    val validationResult: ValidationResult? = null,
    val state: String? = null,
    val createdAt: Long,
    val expiresAt: Long,
    val used: Boolean = false,
)

// ============================================================================
// JAR (JWT-secured Authorization Request) Types - RFC 9101
// ============================================================================

/**
 * Arguments for creating a signed authorization request (JAR).
 *
 * Per RFC 9101:
 * - The authorization request is signed as a JWT
 * - The JWT includes all authorization request parameters as claims
 * - Standard JWT claims (iss, aud, exp, iat, jti) are added
 *
 * @property requestArgs The base authorization request arguments
 * @property signingKey The verifier's private key for signing the JAR
 * @property audience The wallet's expected issuer URL (JWT audience)
 * @property expirationSeconds JWT expiration time in seconds (default: 300 = 5 minutes)
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("CreateSignedAuthorizationRequestArgs", exact = true)
@JsExportCompat
data class CreateSignedAuthorizationRequestArgs(
    val requestArgs: CreateAuthorizationRequestArgs,
    val signingKey: KeyInfoType<*>,
    val audience: String,
    val expirationSeconds: Long = 300,
)

/**
 * Result of creating a signed authorization request.
 *
 * @property request The created authorization request
 * @property signedJar The signed JAR (JWT) string
 * @property sessionId Session identifier for correlation
 * @property requestUri Optional: If using PAR, this would be the request_uri to use
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("SignedAuthorizationRequestResult", exact = true)
@JsExportCompat
data class SignedAuthorizationRequestResult(
    val request: AuthorizationRequest,
    val signedJar: String,
    val sessionId: String? = null,
    val requestUri: String? = null,
)
