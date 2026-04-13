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

package com.sphereon.openid.oid4vp.auth.store

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.openid.oid4vp.auth.model.Oid4vpAuthSession
import kotlin.time.Duration

/**
 * Storage interface for OID4VP authentication sessions.
 *
 * Implementations of this interface handle the persistence of authentication
 * sessions. The [InMemoryOid4vpAuthSessionStore] provides a simple in-memory
 * implementation for development and testing. For production use, implement
 * a store backed by a persistent KV store or database.
 */
interface Oid4vpAuthSessionStore {

    /**
     * Store a session with the given TTL.
     *
     * @param sessionId The session ID
     * @param session The session to store
     * @param ttl Time-to-live for the session
     * @return Ok if successful, Err if storage fails
     */
    suspend fun put(
        sessionId: String,
        session: Oid4vpAuthSession,
        ttl: Duration
    ): IdkResult<Unit, IdkError>

    /**
     * Retrieve a session by ID.
     *
     * @param sessionId The session ID
     * @return The session if found, null if not found, or Err on failure
     */
    suspend fun get(sessionId: String): IdkResult<Oid4vpAuthSession?, IdkError>

    /**
     * Delete a session by ID.
     *
     * @param sessionId The session ID
     * @return Ok if successful (even if session didn't exist), Err on failure
     */
    suspend fun delete(sessionId: String): IdkResult<Unit, IdkError>

    /**
     * Check if a session exists.
     *
     * @param sessionId The session ID
     * @return True if exists, false if not
     */
    suspend fun exists(sessionId: String): IdkResult<Boolean, IdkError>

    /**
     * Find a session by its linked reconciliation session ID.
     *
     * @param reconciliationSessionId The reconciliation session ID
     * @return The session if found, null if not found, or Err on failure
     */
    suspend fun findByReconciliationSessionId(reconciliationSessionId: String): IdkResult<Oid4vpAuthSession?, IdkError>

    companion object {
        const val NAMESPACE = "oid4vp-auth-sessions"
    }
}
