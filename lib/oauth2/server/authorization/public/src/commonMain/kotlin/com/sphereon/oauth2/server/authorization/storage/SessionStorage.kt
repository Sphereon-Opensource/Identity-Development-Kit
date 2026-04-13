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
import com.sphereon.oauth2.server.authorization.model.AuthorizationSession

/**
 * Session storage abstraction for tracking authorization flows
 *
 * Authorization flows span multiple HTTP requests:
 * 1. Initial authorization request → Session created
 * 2. User authentication → Session updated with user ID
 * 3. User consent → Session updated with consent decision
 * 4. Authorization response → Session marked as completed
 *
 * Sessions should be short-lived (typically 10-15 minutes) and cleaned up after use.
 *
 * Implementation recommendations:
 * - Redis (distributed sessions, automatic expiration)
 * - In-memory (single-server deployments)
 * - SQL database with TTL cleanup
 *
 * Thread Safety: Implementations MUST be thread-safe for concurrent access.
 * Consider optimistic locking for session updates.
 */
interface SessionStorage {
    /**
     * Create a new authorization session
     *
     * Generates a unique session ID and stores the session data.
     * Sessions should have a short TTL (10-15 minutes).
     *
     * @param session The authorization session data (sessionId should be unique)
     * @return The session ID, or storage error
     */
    suspend fun createSession(session: AuthorizationSession): IdkResult<String, AuthorizationServerError.StorageError>

    /**
     * Get an authorization session by ID
     *
     * Returns null if the session doesn't exist or has expired.
     *
     * @param sessionId The session identifier
     * @return Session data if found and not expired, null otherwise, or storage error
     */
    suspend fun getSession(sessionId: String): IdkResult<AuthorizationSession?, AuthorizationServerError.StorageError>

    /**
     * Update an authorization session
     *
     * Used to update session status as the flow progresses:
     * - After user authentication (add authenticated user ID)
     * - After user consent (add consent decision)
     * - After authorization response (mark as completed)
     *
     * If the session doesn't exist, returns SessionNotFound error.
     *
     * @param sessionId The session identifier
     * @param session Updated session data
     * @return Success or error
     */
    suspend fun updateSession(
        sessionId: String,
        session: AuthorizationSession,
    ): IdkResult<Unit, AuthorizationServerError>

    /**
     * Delete an authorization session
     *
     * Should be called after:
     * - Successful authorization response
     * - User denies authorization
     * - Session expires
     * - Error occurs
     *
     * @param sessionId The session identifier
     * @return Success or error if not found
     */
    suspend fun deleteSession(sessionId: String): IdkResult<Unit, AuthorizationServerError.StorageError>

    /**
     * Find sessions by authenticated user ID
     *
     * Useful for:
     * - Revoking all sessions on logout
     * - Displaying active authorization requests to user
     * - Security audits
     *
     * @param userId The authenticated user ID
     * @return List of sessions for the user, or storage error
     */
    suspend fun findSessionsByUser(userId: String): IdkResult<List<AuthorizationSession>, AuthorizationServerError.StorageError>

    /**
     * Find sessions by client ID
     *
     * Useful for:
     * - Client-initiated session cleanup
     * - Security audits
     * - Rate limiting
     *
     * @param clientId The client identifier
     * @return List of sessions for the client, or storage error
     */
    suspend fun findSessionsByClient(clientId: String): IdkResult<List<AuthorizationSession>, AuthorizationServerError.StorageError>

    /**
     * Cleanup expired sessions
     *
     * Should be called periodically to prevent unbounded growth.
     * Sessions typically expire after 10-15 minutes.
     *
     * @return Number of sessions deleted, or storage error
     */
    suspend fun cleanupExpiredSessions(): IdkResult<Int, AuthorizationServerError.StorageError>

    /**
     * Delete all sessions for a user
     *
     * Useful for:
     * - User logout (cancel all pending authorizations)
     * - Account deletion
     * - Security incident response
     *
     * @param userId The user identifier
     * @return Number of sessions deleted, or storage error
     */
    suspend fun deleteSessionsForUser(userId: String): IdkResult<Int, AuthorizationServerError.StorageError>

    /**
     * Delete all sessions for a client
     *
     * Useful when:
     * - Client is compromised
     * - Client is deregistered
     * - Security incident response
     *
     * @param clientId The client identifier
     * @return Number of sessions deleted, or storage error
     */
    suspend fun deleteSessionsForClient(clientId: String): IdkResult<Int, AuthorizationServerError.StorageError>

    /**
     * Check if a session exists and is not expired
     *
     * Lightweight check without retrieving full session data.
     *
     * @param sessionId The session identifier
     * @return true if session exists and is valid, false otherwise, or storage error
     */
    suspend fun sessionExists(sessionId: String): IdkResult<Boolean, AuthorizationServerError.StorageError>
}
