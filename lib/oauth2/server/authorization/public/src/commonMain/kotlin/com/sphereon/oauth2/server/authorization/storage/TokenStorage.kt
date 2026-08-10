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

package com.sphereon.oauth2.server.authorization.storage

import com.sphereon.core.api.IdkResult
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import com.sphereon.oauth2.server.authorization.model.AccessTokenData
import com.sphereon.oauth2.server.authorization.model.RefreshTokenData
import kotlin.time.Instant

/**
 * Storage abstraction for access and refresh tokens
 *
 * Implementation must be provided by the application using the Authorization Server.
 * Consider using:
 * - In-memory storage (for development/testing)
 * - Redis (for distributed deployments with short-lived tokens)
 * - SQL database (for long-lived tokens with complex queries)
 * - NoSQL database (for high-throughput scenarios)
 *
 * Thread Safety: Implementations MUST be thread-safe as they will be accessed
 * concurrently by multiple requests.
 */
interface TokenStorage {
    // ============================================================================
    // Access Token Operations
    // ============================================================================

    /**
     * Store an access token
     *
     * @param token The access token string (used as key)
     * @param data The access token metadata
     * @return Success or storage error
     */
    suspend fun storeAccessToken(
        token: String,
        data: AccessTokenData,
    ): IdkResult<Unit, AuthorizationServerError.StorageError>

    /**
     * Retrieve access token data
     *
     * @param token The access token string
     * @return Token data if found, null if not found, or storage error
     */
    suspend fun getAccessToken(token: String): IdkResult<AccessTokenData?, AuthorizationServerError.StorageError>

    /**
     * Revoke an access token
     *
     * Sets the `revoked` flag to true. The token remains in storage for audit purposes
     * but should be rejected during verification.
     *
     * @param token The access token string
     * @return Success or storage error
     */
    suspend fun revokeAccessToken(token: String): IdkResult<Unit, AuthorizationServerError.StorageError>

    /**
     * Find all access tokens for a given subject (user)
     *
     * Useful for:
     * - Revoking all tokens on logout
     * - Displaying active sessions to user
     * - Security audits
     *
     * @param subject User ID
     * @return List of access tokens, or storage error
     */
    suspend fun findAccessTokensBySubject(subject: String): IdkResult<List<AccessTokenData>, AuthorizationServerError.StorageError>

    /**
     * Find all access tokens for a given client
     *
     * Useful for:
     * - Client-initiated revocation
     * - Security audits
     * - Client suspension
     *
     * @param clientId Client identifier
     * @return List of access tokens, or storage error
     */
    suspend fun findAccessTokensByClient(clientId: String): IdkResult<List<AccessTokenData>, AuthorizationServerError.StorageError>

    /**
     * Cleanup expired access tokens
     *
     * Should be called periodically to prevent unbounded growth.
     * Consider running as a scheduled background task.
     *
     * @return Number of tokens deleted, or storage error
     */
    suspend fun cleanupExpiredAccessTokens(): IdkResult<Int, AuthorizationServerError.StorageError>

    // ============================================================================
    // Refresh Token Operations
    // ============================================================================

    /**
     * Store a refresh token
     *
     * @param token The refresh token string (used as key)
     * @param data The refresh token metadata
     * @return Success or storage error
     */
    suspend fun storeRefreshToken(
        token: String,
        data: RefreshTokenData,
    ): IdkResult<Unit, AuthorizationServerError.StorageError>

    /**
     * Retrieve refresh token data
     *
     * @param token The refresh token string
     * @return Token data if found, null if not found, or storage error
     */
    suspend fun getRefreshToken(token: String): IdkResult<RefreshTokenData?, AuthorizationServerError.StorageError>

    /**
     * Consume a refresh token (mark as used and optionally revoke)
     *
     * For refresh token rotation (one-time use):
     * - Set `used = true`
     * - Optionally set `revoked = true` to prevent reuse
     *
     * This operation should be atomic to prevent race conditions where
     * the same refresh token is used multiple times concurrently.
     *
     * @param token The refresh token string
     * @param revoke If true, also mark as revoked
     * @return Updated token data or storage error
     */
    suspend fun consumeRefreshToken(
        token: String,
        revoke: Boolean = true,
    ): IdkResult<RefreshTokenData?, AuthorizationServerError.StorageError>

    /**
     * Atomically records the successor of a rotated refresh token.
     *
     * Implementations must keep the first recorded successor when concurrent requests race.
     * The returned row tells the caller which successor is authoritative. The default preserves
     * the legacy consume behavior for custom stores until they provide durable rotation lineage.
     */
    suspend fun rotateRefreshToken(
        token: String,
        replacementRefreshToken: String,
        rotatedAt: Instant,
    ): IdkResult<RefreshTokenData?, AuthorizationServerError.StorageError> = consumeRefreshToken(token, revoke = true)

    /**
     * Revoke a refresh token
     *
     * Sets the `revoked` flag to true.
     *
     * @param token The refresh token string
     * @return Success or storage error
     */
    suspend fun revokeRefreshToken(token: String): IdkResult<Unit, AuthorizationServerError.StorageError>

    /**
     * Find all refresh tokens for a given subject (user)
     *
     * Useful for:
     * - Revoking all tokens on logout
     * - Displaying active sessions to user
     * - Security audits
     *
     * @param subject User ID
     * @return List of refresh tokens, or storage error
     */
    suspend fun findRefreshTokensBySubject(subject: String): IdkResult<List<RefreshTokenData>, AuthorizationServerError.StorageError>

    /**
     * Cleanup expired refresh tokens
     *
     * Should be called periodically to prevent unbounded growth.
     *
     * @return Number of tokens deleted, or storage error
     */
    suspend fun cleanupExpiredRefreshTokens(): IdkResult<Int, AuthorizationServerError.StorageError>

    // ============================================================================
    // Batch Operations
    // ============================================================================

    /**
     * Revoke all tokens for a subject (user logout)
     *
     * Revokes both access and refresh tokens.
     * Useful for logout operations.
     *
     * @param subject User ID
     * @return Number of tokens revoked, or storage error
     */
    suspend fun revokeAllTokensForSubject(subject: String): IdkResult<Int, AuthorizationServerError.StorageError>

    /**
     * Revoke all tokens for a client
     *
     * Useful when a client is compromised or deregistered.
     *
     * @param clientId Client identifier
     * @return Number of tokens revoked, or storage error
     */
    suspend fun revokeAllTokensForClient(clientId: String): IdkResult<Int, AuthorizationServerError.StorageError>
}
