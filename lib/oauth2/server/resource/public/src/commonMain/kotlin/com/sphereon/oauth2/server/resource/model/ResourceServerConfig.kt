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

package com.sphereon.oauth2.server.resource.model

import com.sphereon.oauth2.common.model.ClientAuthenticationConfig
import com.sphereon.oauth2.server.resource.cache.DpopNonceCache
import com.sphereon.oauth2.server.resource.cache.TokenCache
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * Configuration for OAuth2 Resource Server.
 *
 * This configuration defines how the resource server validates access tokens
 * and protects resources.
 *
 * @property resourceServerIdentifier The identifier for this resource server (used for audience validation)
 * @property authorizationServers List of trusted authorization server URLs
 * @property clientAuthentication Client authentication config for token introspection (optional)
 * @property tokenValidationStrategy Strategy for validating tokens (JWT-first, introspection-only, etc.)
 * @property allowedAuthenticationSchemes Allowed authentication schemes (Bearer, DPoP)
 * @property tokenCacheTtl Default TTL for token cache entries
 * @property dpopReplayWindow Maximum age for DPoP proofs (replay protection)
 * @property dpopNonceTtl TTL for DPoP nonce cache entries
 * @property requireAudienceValidation Whether to require audience validation
 * @property clockSkewTolerance Clock skew tolerance for timestamp validation
 */
data class ResourceServerConfig(
    val resourceServerIdentifier: String,
    val authorizationServers: List<String>,
    val clientAuthentication: ClientAuthenticationConfig? = null,
    val tokenValidationStrategy: TokenValidationStrategy = TokenValidationStrategy.JWT_FIRST,
    val allowedAuthenticationSchemes: List<AuthenticationScheme> = listOf(
        AuthenticationScheme.BEARER,
        AuthenticationScheme.DPOP
    ),
    val tokenCacheTtl: Duration = 1.hours,
    val dpopReplayWindow: Duration = 1.minutes,
    val dpopNonceTtl: Duration = 5.minutes,
    val requireAudienceValidation: Boolean = true,
    val clockSkewTolerance: Duration = Duration.ZERO
)

/**
 * Token validation strategy.
 *
 * Determines how the resource server validates access tokens.
 */
enum class TokenValidationStrategy {
    /**
     * Try JWT verification first (fast, no network).
     * Fall back to introspection if JWT verification fails.
     *
     * This is the recommended strategy for most deployments.
     */
    JWT_FIRST,

    /**
     * Only use JWT verification.
     * Fail if token is not a valid JWT.
     *
     * Use this when you know all tokens are JWTs and want to avoid
     * the overhead of introspection endpoint configuration.
     */
    JWT_ONLY,

    /**
     * Only use token introspection.
     * Always call the authorization server's introspection endpoint.
     *
     * Use this when:
     * - Tokens are opaque (not JWTs)
     * - You need real-time revocation checking
     * - You want centralized token validation
     */
    INTROSPECTION_ONLY
}
