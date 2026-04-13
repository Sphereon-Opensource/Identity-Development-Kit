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
import com.sphereon.core.api.error.IdkError
import com.sphereon.oauth2.server.authorization.model.IaeSession

/**
 * Storage abstraction for IAE (Interactive Authorization Endpoint) sessions.
 *
 * OID4VCI 1.1 Section 6 — Interactive Authorization Endpoint
 *
 * IAE flows span multiple HTTP round-trips between the client and the AS:
 * 1. Initial request → session created (status = INITIAL → INTERACTION_REQUIRED)
 * 2. Follow-up with VP response or web-auth result → session verified and code issued
 *
 * The [IaeSession.authSession] token rotates on every server response to prevent replay.
 * [IaeSessionStore.getByAuthSession] must only match the current value.
 *
 * Implementation recommendations:
 * - Redis (short TTL, atomic CAS for auth_session rotation)
 * - In-memory (single-server / test deployments)
 * - SQL with TTL cleanup (service-data production deployments)
 *
 * Thread safety: Implementations MUST be thread-safe. Use atomic compare-and-swap
 * semantics when rotating auth_session values.
 */
interface IaeSessionStore {
    /**
     * Persist a newly created IAE session.
     *
     * @param session The session to store (sessionId and authSession must be unique).
     * @return The stored session, or an error if persistence fails.
     */
    suspend fun create(session: IaeSession): IdkResult<IaeSession, IdkError>

    /**
     * Retrieve a session by the current auth_session token.
     *
     * Returns null if no session matches (including after rotation — callers should
     * treat a missing auth_session as a replay or tampered request).
     *
     * @param authSession The auth_session value from the client request.
     * @return Matching session or null, or an error if the lookup fails.
     */
    suspend fun getByAuthSession(authSession: String): IdkResult<IaeSession?, IdkError>

    /**
     * Retrieve a session by the stable internal session ID.
     *
     * @param sessionId The internal session identifier.
     * @return Matching session or null, or an error if the lookup fails.
     */
    suspend fun getBySessionId(sessionId: String): IdkResult<IaeSession?, IdkError>

    /**
     * Persist an updated session state.
     *
     * Used after each round-trip to record status changes, auth_session rotation,
     * VP verification results, and the final authorization code.
     *
     * @param session Updated session data. The [IaeSession.sessionId] identifies
     *   the record to update.
     * @return The updated session, or an error if the session is not found or update fails.
     */
    suspend fun update(session: IaeSession): IdkResult<IaeSession, IdkError>

    /**
     * Remove a session from the store.
     *
     * Should be called after:
     * - Successful code issuance (status = AUTHORIZED, code consumed)
     * - Flow failure (status = FAILED)
     * - Explicit client cancellation
     *
     * @param sessionId The internal session identifier to delete.
     * @return true if the session was found and deleted, false if it did not exist,
     *   or an error if deletion fails.
     */
    suspend fun delete(sessionId: String): IdkResult<Boolean, IdkError>
}
