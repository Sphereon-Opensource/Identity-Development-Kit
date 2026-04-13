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

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * Access token data stored and managed by the authorization server
 *
 * RFC 6749 Section 1.4: Access tokens are credentials used to access protected resources.
 */
@Serializable
data class AccessTokenData(
    /**
     * The access token string (JWT or opaque)
     */
    val accessToken: String,
    /**
     * Token type (typically "Bearer" or "DPoP")
     * RFC 6749 Section 7.1
     */
    val tokenType: String,
    /**
     * Client ID this token was issued to
     */
    val clientId: String,
    /**
     * Resource owner identifier (user ID)
     * For client_credentials grant, this may be the client_id
     */
    val subject: String,
    /**
     * Granted scope
     */
    val scope: String? = null,
    /**
     * Token audience (resource servers)
     * RFC 8707: Resource Indicators
     */
    val audience: List<String> = emptyList(),
    /**
     * Authorization server issuer
     */
    val issuer: String,
    /**
     * When the token was issued
     */
    val issuedAt: Instant,
    /**
     * When the token expires
     */
    val expiresAt: Instant,
    /**
     * DPoP JWK thumbprint (if DPoP-bound)
     * RFC 9449: Stored in cnf.jkt claim in JWT
     */
    val dpopJkt: String? = null,
    /**
     * Whether this token has been revoked
     */
    val revoked: Boolean = false,
    /**
     * Associated refresh token ID (if any)
     * Used to revoke refresh token when access token is revoked
     */
    val refreshTokenId: String? = null,
    /**
     * Additional claims/metadata
     */
    val additionalData: Map<String, @Contextual Any> = emptyMap(),
)
