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

import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.jose.JwkSet
import com.sphereon.oauth2.common.model.ClientAuthenticationMethod
import com.sphereon.oauth2.common.model.GrantType
import com.sphereon.oauth2.common.model.ResponseType
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

/**
 * Client registration data
 *
 * RFC 7591: OAuth 2.0 Dynamic Client Registration Protocol
 * RFC 7592: OAuth 2.0 Dynamic Client Registration Management Protocol
 */
@Serializable
data class ClientRegistration(
    /**
     * Unique client identifier
     */
    val clientId: String,

    /**
     * Client secret (if applicable)
     * Only present for confidential clients
     */
    val clientSecret: String? = null,

    /**
     * Client name
     */
    val clientName: String? = null,

    /**
     * Client type
     */
    val clientType: ClientType = ClientType.CONFIDENTIAL,

    /**
     * Allowed grant types
     * RFC 6749 Section 1.3
     */
    val grantTypes: List<GrantType>,

    /**
     * Allowed response types
     * RFC 6749 Section 3.1.1
     */
    val responseTypes: List<ResponseType> = emptyList(),

    /**
     * Registered redirect URIs
     * RFC 6749 Section 3.1.2: The authorization server MUST require public clients
     * and SHOULD require confidential clients to register their redirection URIs
     */
    val redirectUris: List<String> = emptyList(),

    /**
     * Allowed scopes for this client
     */
    val allowedScopes: List<String>? = null, // null = all scopes allowed

    /**
     * Client authentication method
     * RFC 7591 Section 2: token_endpoint_auth_method
     */
    val tokenEndpointAuthMethod: ClientAuthenticationMethod = ClientAuthenticationMethod.CLIENT_SECRET_BASIC,

    /**
     * JWKs for client authentication or encryption
     * Used with private_key_jwt or client_secret_jwt
     */
    val jwks: List<Jwk>? = null,

    /**
     * JWK Set URI
     * Alternative to embedding JWKs directly
     */
    val jwksUri: String? = null,

    /**
     * Whether PKCE is required for this client
     * RFC 7636: REQUIRED for public clients
     */
    val requirePkce: Boolean = clientType == ClientType.PUBLIC,

    /**
     * Whether PAR (Pushed Authorization Requests) is required
     * RFC 9126
     */
    val requirePushedAuthorizationRequests: Boolean = false,

    /**
     * Whether DPoP is supported/required
     * RFC 9449
     */
    val dpopBoundAccessTokens: Boolean = false,

    /**
     * Access token lifetime in seconds
     * Default is typically 3600 (1 hour)
     */
    val accessTokenLifetime: Int = 3600,

    /**
     * Refresh token lifetime in seconds
     * null = no expiration
     */
    val refreshTokenLifetime: Int? = null,

    /**
     * Authorization code lifetime in seconds
     * Default is 600 (10 minutes), MUST be short-lived
     */
    val authorizationCodeLifetime: Int = 600,

    /**
     * Trusted attester issuers for attestation-based client auth.
     * List of `iss` values accepted in the client attestation JWT.
     */
    val trustedAttesterIssuers: List<String>? = null,

    /**
     * JWKS URIs for trusted attesters, keyed by issuer.
     * Used to resolve the attester's public key for signature verification.
     */
    val trustedAttesterJwksUris: Map<String, String>? = null,

    /**
     * Inline JWKs for trusted attesters, keyed by issuer.
     * Alternative to JWKS URIs when keys are known at registration time.
     */
    val trustedAttesterJwks: Map<String, JwkSet>? = null,

    /**
     * Additional client metadata
     */
    val additionalMetadata: Map<String, @Contextual Any> = emptyMap()
)

/**
 * Client type
 *
 * RFC 6749 Section 2.1: Clients are categorized into confidential and public clients
 */
enum class ClientType {
    /**
     * Confidential clients are capable of maintaining the confidentiality of their credentials
     * (e.g., server-side applications)
     */
    CONFIDENTIAL,

    /**
     * Public clients are incapable of maintaining the confidentiality of their credentials
     * (e.g., mobile apps, SPAs, native apps)
     */
    PUBLIC
}
