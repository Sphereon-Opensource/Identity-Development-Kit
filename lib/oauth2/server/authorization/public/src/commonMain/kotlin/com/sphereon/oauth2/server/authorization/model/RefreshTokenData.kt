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
 * Refresh token data stored by the authorization server
 *
 * RFC 6749 Section 1.5: Refresh tokens are credentials used to obtain access tokens.
 * Refresh tokens are issued to the client by the authorization server and are used
 * to obtain a new access token when the current access token becomes invalid or expires.
 */
@Serializable
data class RefreshTokenData(
    /**
     * The refresh token string
     */
    val refreshToken: String,
    /**
     * Client ID this token was issued to
     */
    val clientId: String,
    /**
     * Resource owner identifier (user ID)
     */
    val subject: String,
    /**
     * Granted scope
     * RFC 6749 Section 6: The requested scope MUST NOT include any scope not originally granted
     */
    val scope: String? = null,
    /**
     * When the token was issued
     */
    val issuedAt: Instant,
    /**
     * When the token expires
     * Refresh tokens typically have longer lifetimes than access tokens (null = never expires)
     */
    val expiresAt: Instant? = null,
    /**
     * Whether this token has been revoked
     */
    val revoked: Boolean = false,
    /**
     * Whether this token has been used
     * Some deployments use one-time refresh tokens (rotate on use)
     */
    val used: Boolean = false,
    /**
     * DPoP JWK thumbprint (if DPoP-bound)
     * RFC 9449: Refresh tokens can also be DPoP-bound
     */
    val dpopJkt: String? = null,
    /**
     * Additional metadata
     */
    val additionalData: Map<String, @Contextual Any> = emptyMap(),
)
