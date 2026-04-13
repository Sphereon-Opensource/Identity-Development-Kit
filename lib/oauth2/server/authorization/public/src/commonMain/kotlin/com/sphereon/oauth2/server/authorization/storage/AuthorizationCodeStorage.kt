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
import com.sphereon.oauth2.server.authorization.model.AuthorizationCodeData

/**
 * Storage abstraction for authorization codes
 *
 * CRITICAL SECURITY REQUIREMENTS:
 * 1. Authorization codes MUST be single-use only (RFC 6749 Section 10.5)
 * 2. The `consumeAuthorizationCode` operation MUST be atomic to prevent replay attacks
 * 3. Codes MUST have short lifetimes (max 10 minutes recommended)
 * 4. Failed attempts to reuse a code should trigger security alerts
 *
 * Implementation must be provided by the application.
 * Recommended storage:
 * - Redis with atomic operations (GETDEL command)
 * - SQL with row-level locking
 * - Any datastore supporting atomic read-and-delete
 *
 * Thread Safety: Implementations MUST be thread-safe and provide atomic operations.
 */
interface AuthorizationCodeStorage {

    /**
     * Store an authorization code
     *
     * The code should have a short TTL (typically 10 minutes) and be single-use only.
     *
     * @param code The authorization code string
     * @param data The authorization code metadata
     * @return Success or storage error
     */
    suspend fun storeAuthorizationCode(
        code: String,
        data: AuthorizationCodeData
    ): IdkResult<Unit, AuthorizationServerError.StorageError>

    /**
     * Retrieve and consume (delete) an authorization code
     *
     * CRITICAL: This operation MUST be atomic to prevent replay attacks.
     *
     * The implementation must:
     * 1. Check if the code exists
     * 2. Verify it hasn't been used
     * 3. Mark it as used OR delete it
     * 4. Return the code data
     * 5. All in a single atomic operation
     *
     * If the code has already been used, this indicates a potential security breach
     * and should be logged/alerted.
     *
     * RFC 6749 Section 10.5:
     * "If an authorization code is used more than once, the authorization server
     * MUST deny the request and SHOULD revoke (when possible) all tokens previously
     * issued based on that authorization code."
     *
     * @param code The authorization code string
     * @return Code data if valid and not used, null if not found or already used, or storage error
     */
    suspend fun consumeAuthorizationCode(
        code: String
    ): IdkResult<AuthorizationCodeData?, AuthorizationServerError.StorageError>

    /**
     * Check if an authorization code has been used (without consuming it)
     *
     * Useful for:
     * - Security audits
     * - Detecting replay attacks
     * - Testing/debugging
     *
     * @param code The authorization code string
     * @return true if code exists and has been used, false if not used or not found, or storage error
     */
    suspend fun isCodeUsed(
        code: String
    ): IdkResult<Boolean, AuthorizationServerError.StorageError>

    /**
     * Revoke all authorization codes for a client
     *
     * Useful when:
     * - A client is compromised
     * - A client is deregistered
     * - Security incident response
     *
     * @param clientId Client identifier
     * @return Number of codes revoked, or storage error
     */
    suspend fun revokeCodesForClient(
        clientId: String
    ): IdkResult<Int, AuthorizationServerError.StorageError>

    /**
     * Cleanup expired authorization codes
     *
     * Should be called periodically to prevent unbounded growth.
     * Codes typically expire after 10 minutes.
     *
     * @return Number of codes deleted, or storage error
     */
    suspend fun cleanupExpiredCodes(): IdkResult<Int, AuthorizationServerError.StorageError>

    /**
     * Find all authorization codes issued to a specific subject (user)
     *
     * Useful for:
     * - Security audits
     * - Detecting suspicious activity
     * - User session management
     *
     * @param subject User ID
     * @return List of authorization codes, or storage error
     */
    suspend fun findCodesBySubject(
        subject: String
    ): IdkResult<List<AuthorizationCodeData>, AuthorizationServerError.StorageError>
}
