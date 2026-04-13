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
 *
 */

package com.sphereon.oauth2.jwt.validation

import com.sphereon.core.api.IdkResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * JWT Validation Service for zero-trust authentication.
 *
 * Validates JWT access tokens and ID tokens using the configured
 * Identity Provider registry. Leverages IDK's VerifyJwtCommand and
 * identifier resolution services for signature verification.
 *
 * Key features:
 * - Multi-IdP support with per-tenant routing
 * - JWKS caching with background refresh
 * - Clock skew tolerance
 * - Configurable algorithm restrictions
 *
 * Designed for integration points:
 * - Ktor: JwtAuthenticationInterceptor
 * - Spring: JwtAuthenticationFilter
 */
interface JwtValidationService {
    /**
     * Validates an access token.
     *
     * Performs the following checks:
     * 1. Token structure validation (3-part JWT)
     * 2. Signature verification using JWKS from IdP
     * 3. Expiration check (exp claim)
     * 4. Not-before check (nbf claim)
     * 5. Issuer validation (iss claim against trusted issuers)
     * 6. Audience validation (aud claim if expected audience configured)
     * 7. Algorithm validation (alg header against allowed algorithms)
     *
     * @param token The raw JWT access token (without Bearer prefix)
     * @param options Additional validation options
     * @return Validated token with extracted claims or validation error
     */
    suspend fun validateAccessToken(
        token: String,
        options: AccessTokenValidationOptions = AccessTokenValidationOptions()
    ): IdkResult<ValidatedAccessToken, JwtValidationError>

    /**
     * Validates an ID token.
     *
     * Similar to access token validation but with ID token semantics:
     * - Validates nonce if provided
     * - Extracts identity claims (name, email, etc.)
     *
     * @param token The raw JWT ID token
     * @param options Additional validation options
     * @return Validated ID token with identity claims or validation error
     */
    suspend fun validateIdToken(
        token: String,
        options: IdTokenValidationOptions = IdTokenValidationOptions()
    ): IdkResult<ValidatedIdToken, JwtValidationError>

    /**
     * Extract claims from a token without full validation.
     *
     * Useful for inspecting tokens during debugging or when
     * signature verification is not needed.
     *
     * WARNING: Do not use for authorization decisions.
     *
     * @param token The raw JWT token
     * @return Parsed claims or error if token format is invalid
     */
    suspend fun extractClaims(token: String): IdkResult<TokenClaims, JwtValidationError>
}

/**
 * Options for access token validation.
 */
@Serializable
data class AccessTokenValidationOptions(
    /** Override expected audience (uses IdP config if null) */
    val expectedAudience: String? = null,

    /** Specific IdP to use for validation (uses issuer-based lookup if null) */
    val idpId: String? = null,

    /** Tenant hint for IdP selection */
    val tenantHint: String? = null,

    /** Additional claims to require */
    val requiredClaims: List<String> = emptyList(),

    /** Required scopes (from scope claim) */
    val requiredScopes: Set<String> = emptySet(),

    /** Allow clock skew override (seconds) */
    val clockSkewSeconds: Long? = null
)

/**
 * Options for ID token validation.
 */
@Serializable
data class IdTokenValidationOptions(
    /** Expected nonce value (from authentication request) */
    val expectedNonce: String? = null,

    /** Override expected audience */
    val expectedAudience: String? = null,

    /** Specific IdP to use for validation */
    val idpId: String? = null,

    /** Tenant hint for IdP selection */
    val tenantHint: String? = null
)

/**
 * Result of a successful access token validation.
 */
@Serializable
data class ValidatedAccessToken(
    /** Subject from validated 'sub' claim */
    val subject: String,

    /** Verified issuer URL */
    val issuer: String,

    /** Validated audiences */
    val audiences: List<String>,

    /** Token expiration timestamp (seconds since epoch) */
    val expiresAt: Long,

    /** Token issued at timestamp (seconds since epoch) */
    val issuedAt: Long,

    /** Token not-before timestamp (seconds since epoch, may be null) */
    val notBefore: Long?,

    /** OAuth scopes from 'scope' claim */
    val scopes: Set<String>,

    /** Extracted tenant identifier (from configured tenant claim) */
    val tenantId: String?,

    /** Client ID (from azp or client_id claim) */
    val clientId: String?,

    /** JWT ID (jti claim) for token tracking */
    val jwtId: String?,

    /** Original raw token for forwarding to downstream services/PDP */
    val rawToken: String,

    /** All claims from the token payload */
    val claims: Map<String, JsonElement>,

    /** IdP configuration used for validation */
    val idpId: String
) {
    /**
     * Check if the token has a specific scope.
     */
    fun hasScope(scope: String): Boolean = scopes.contains(scope)

    /**
     * Check if the token has all specified scopes.
     */
    fun hasAllScopes(requiredScopes: Set<String>): Boolean = scopes.containsAll(requiredScopes)

    /**
     * Check if the token has any of the specified scopes.
     */
    fun hasAnyScope(allowedScopes: Set<String>): Boolean = scopes.any { allowedScopes.contains(it) }
}

/**
 * Result of a successful ID token validation.
 */
@Serializable
data class ValidatedIdToken(
    /** Subject from validated 'sub' claim */
    val subject: String,

    /** Verified issuer URL */
    val issuer: String,

    /** Validated audiences */
    val audiences: List<String>,

    /** Token expiration timestamp */
    val expiresAt: Long,

    /** Token issued at timestamp */
    val issuedAt: Long,

    /** Authentication time (auth_time claim) */
    val authTime: Long?,

    /** Nonce for replay protection */
    val nonce: String?,

    /** User's name (name claim) */
    val name: String?,

    /** User's email (email claim) */
    val email: String?,

    /** Whether email is verified */
    val emailVerified: Boolean?,

    /** User's preferred username */
    val preferredUsername: String?,

    /** User's given name (first name) */
    val givenName: String?,

    /** User's family name (last name) */
    val familyName: String?,

    /** Extracted tenant identifier */
    val tenantId: String?,

    /** Original raw token */
    val rawToken: String,

    /** All claims from the token payload */
    val claims: Map<String, JsonElement>,

    /** IdP configuration used for validation */
    val idpId: String
)

/**
 * Parsed token claims without validation.
 */
@Serializable
data class TokenClaims(
    /** Token header claims (alg, kid, typ) */
    val header: Map<String, JsonElement>,

    /** Token payload claims */
    val payload: Map<String, JsonElement>,

    /** Issuer (iss claim) */
    val issuer: String?,

    /** Subject (sub claim) */
    val subject: String?,

    /** Audiences (aud claim) */
    val audiences: List<String>,

    /** Expiration (exp claim) */
    val expiresAt: Long?,

    /** Issued at (iat claim) */
    val issuedAt: Long?
)
