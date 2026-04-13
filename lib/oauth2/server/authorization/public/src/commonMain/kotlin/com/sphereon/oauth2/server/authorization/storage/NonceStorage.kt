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

package com.sphereon.oauth2.server.authorization.storage

import com.sphereon.core.api.IdkResult
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import kotlinx.datetime.Instant

/**
 * Nonce storage abstraction for DPoP replay protection
 *
 * RFC 9449 Section 8: Server-Provided Nonce
 *
 * The authorization server can provide a nonce to the client which must be included
 * in subsequent DPoP proofs. This provides additional replay protection by:
 * 1. Preventing old DPoP proofs from being reused
 * 2. Binding DPoP proofs to a specific server interaction
 * 3. Limiting the window for replay attacks
 *
 * Implementation requirements:
 * - Store nonces with expiration (typically 60 seconds)
 * - Track which nonces have been used (replay detection)
 * - Support high throughput (every DPoP-bound request)
 * - Automatic cleanup of expired nonces
 *
 * Recommended storage:
 * - Redis with TTL (excellent performance, automatic expiration)
 * - In-memory cache with TTL (single-server deployments)
 * - Distributed cache (multi-server deployments)
 *
 * Thread Safety: Implementations MUST be thread-safe and provide atomic operations.
 */
interface NonceStorage {

    /**
     * Generate and store a new DPoP nonce
     *
     * The nonce should be:
     * - Cryptographically random
     * - Unique
     * - At least 128 bits of entropy
     *
     * RFC 9449 Section 8: The server-provided nonce MUST contain at least
     * 128 bits of entropy using a collision-resistant function
     *
     * @param expiresAt When the nonce expires (typically now + 60 seconds)
     * @return The generated nonce string, or storage error
     */
    suspend fun generateNonce(
        expiresAt: Instant
    ): IdkResult<String, AuthorizationServerError.StorageError>

    /**
     * Store a nonce (when generated externally)
     *
     * @param nonce The nonce string
     * @param expiresAt When the nonce expires
     * @return Success or storage error
     */
    suspend fun storeNonce(
        nonce: String,
        expiresAt: Instant
    ): IdkResult<Unit, AuthorizationServerError.StorageError>

    /**
     * Check if a nonce exists and is not expired
     *
     * Does not mark the nonce as used.
     *
     * @param nonce The nonce string
     * @return true if nonce exists and not expired, false otherwise, or storage error
     */
    suspend fun nonceExists(
        nonce: String
    ): IdkResult<Boolean, AuthorizationServerError.StorageError>

    /**
     * Check if a nonce has been used
     *
     * CRITICAL: This is used for replay detection.
     *
     * @param nonce The nonce string
     * @return true if nonce was already used, false if not used or not found, or storage error
     */
    suspend fun wasNonceUsed(
        nonce: String
    ): IdkResult<Boolean, AuthorizationServerError.StorageError>

    /**
     * Mark a nonce as used
     *
     * CRITICAL: This operation should be atomic with verification.
     *
     * Once marked as used, subsequent attempts to use the same nonce should fail.
     * This prevents replay attacks where an attacker captures and reuses a DPoP proof.
     *
     * The implementation should:
     * 1. Check if nonce exists and is not expired
     * 2. Check if nonce is already used
     * 3. Mark as used
     * 4. All in a single atomic operation
     *
     * @param nonce The nonce string
     * @return Success if nonce was valid and not used, error if already used or not found
     */
    suspend fun markNonceAsUsed(
        nonce: String
    ): IdkResult<Unit, AuthorizationServerError>

    /**
     * Verify and consume a nonce in one atomic operation
     *
     * This is the preferred method for nonce verification as it combines:
     * 1. Check nonce exists
     * 2. Check nonce not expired
     * 3. Check nonce not used
     * 4. Mark as used
     *
     * All in a single atomic operation to prevent race conditions.
     *
     * @param nonce The nonce string
     * @return Success if nonce was valid and consumed, error otherwise
     */
    suspend fun verifyAndConsumeNonce(
        nonce: String
    ): IdkResult<Unit, AuthorizationServerError>

    /**
     * Cleanup expired nonces
     *
     * Should be called periodically to prevent unbounded growth.
     * Many implementations (e.g., Redis) handle this automatically with TTL.
     *
     * @return Number of nonces deleted, or storage error
     */
    suspend fun cleanupExpiredNonces(): IdkResult<Int, AuthorizationServerError.StorageError>

    /**
     * Get nonce expiration time
     *
     * Useful for setting the DPoP-Nonce header with appropriate lifetime information.
     *
     * @param nonce The nonce string
     * @return Expiration time if nonce exists, null if not found, or storage error
     */
    suspend fun getNonceExpiration(
        nonce: String
    ): IdkResult<Instant?, AuthorizationServerError.StorageError>

    /**
     * Revoke a nonce (mark as invalid)
     *
     * Useful for:
     * - Security incident response
     * - Testing/debugging
     *
     * @param nonce The nonce string
     * @return Success or storage error
     */
    suspend fun revokeNonce(
        nonce: String
    ): IdkResult<Unit, AuthorizationServerError.StorageError>
}
