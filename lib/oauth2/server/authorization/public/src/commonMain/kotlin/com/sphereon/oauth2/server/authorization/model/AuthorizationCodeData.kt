/*
 * © 2025 Sphereon International B.V.
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
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.Contextual

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
     * User claims to carry through the authorization code exchange.
     * Populated during federation/wallet callback, used at token issuance
     * to populate id_token userClaims (scope-filtered) and kept out of access_token.
     */
    val userClaims: Map<String, @Contextual Any> = emptyMap(),

    /**
     * Additional authorization details (RFC 9396)
     * Can contain resource indicators, authorization_details, etc.
     */
    val additionalData: Map<String, @Contextual Any> = emptyMap()
)
