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

package com.sphereon.oauth2.server.authorization.model

import com.sphereon.oauth2.common.model.PkceMethod
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * Authorization code data stored by the authorization server
 *
 * RFC 6749 Section 4.1.2: The authorization code MUST expire shortly after it is issued
 * to mitigate the risk of leaks. A maximum authorization code lifetime of 10 minutes is RECOMMENDED.
 */
@Serializable
data class AuthorizationCodeData(
    /**
     * The authorization code string
     */
    val code: String,
    /**
     * Client ID this code was issued to
     */
    val clientId: String,
    /**
     * The authenticated user ID
     */
    val subject: String,
    /**
     * Redirect URI from the authorization request
     * MUST be validated when exchanging the code
     */
    val redirectUri: String,
    /**
     * Granted scope (may be subset of requested scope)
     */
    val scope: String? = null,
    /** RFC 8707 resource indicators bound at authorization time. */
    val resource: List<String> = emptyList(),
    /** Exact AS-client default audience bound when the authorization request omitted resource. */
    val defaultAccessTokenAudience: String? = null,
    /**
     * PKCE code challenge (if PKCE was used)
     */
    val codeChallenge: String? = null,
    /**
     * PKCE code challenge method (if PKCE was used)
     */
    val codeChallengeMethod: PkceMethod? = null,
    /**
     * DPoP JWK thumbprint (if DPoP was requested)
     * RFC 9449 Section 10: The authorization server binds the public key to the access token
     */
    val dpopJkt: String? = null,
    /**
     * OpenID Connect nonce propagated from authorization session
     * Included in ID token when code is exchanged
     */
    val nonce: String? = null,
    /**
     * Epoch seconds when user authenticated
     * Propagated from session for auth_time claim in ID token
     */
    val authTime: Long? = null,
    /**
     * When the code was issued
     */
    val issuedAt: Instant,
    /**
     * When the code expires (typically issuedAt + 10 minutes)
     */
    val expiresAt: Instant,
    /**
     * Whether this code has been used
     * CRITICAL: Codes MUST be one-time use only (RFC 6749 Section 10.5)
     */
    val used: Boolean = false,
    /**
     * Authentication Context Class Reference (OpenID Connect Core Section 2).
     * Propagated from the authenticated user to the ID token's acr claim.
     */
    val acr: String? = null,
    /**
     * Authentication Methods References (OpenID Connect Core Section 2).
     * Propagated from the authenticated user to the ID token's amr claim.
     */
    val amr: List<String>? = null,
    /**
     * User claims to carry through the authorization code exchange.
     * Populated during federation/wallet callback, used at token issuance
     * to populate id_token userClaims (scope-filtered) and kept out of access_token.
     */
    val userClaims: Map<String, @Contextual Any> = emptyMap(),
    /**
     * Additional authorization details (RFC 9396)
     * Can contain resource indicators, authorization_details, etc.
     */
    val additionalData: Map<String, @Contextual Any> = emptyMap(),
    /**
     * Authorization session identifier that produced this code. Propagated
     * into `CreateIdTokenArgs.sessionId` at token-exchange so the AS's
     * [com.sphereon.oauth2.server.authorization.provider.SessionParticipationRecorder]
     * can record `(sessionId, clientId)` for OIDC Back-Channel Logout 1.0 §2.4
     * recipient selection. Null when the code came from a flow that doesn't
     * bind to a session (pre-authorized credential offer, machine-to-machine).
     */
    val sessionId: String? = null,
    /**
     * Access token minted from this authorization code on first redemption.
     *
     * RFC 6749 §10.5: "If an authorization code is used more than once, the authorization
     * server MUST deny the request and SHOULD revoke (when possible) all tokens previously
     * issued based on that authorization code." We retain the token string here so a replay
     * detection at the storage layer can pass it to [TokenStorage.revokeAccessToken] without
     * a side-channel. Null until the first successful redemption records the issued token.
     */
    val issuedAccessToken: String? = null,
    /**
     * Refresh token minted from this authorization code on first redemption. Same revocation
     * rationale as [issuedAccessToken]; null when no refresh token was issued or before the
     * first successful redemption.
     */
    val issuedRefreshToken: String? = null,
)
