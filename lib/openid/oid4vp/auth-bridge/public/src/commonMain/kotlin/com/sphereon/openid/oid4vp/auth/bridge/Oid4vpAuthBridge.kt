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
 *
 */

package com.sphereon.openid.oid4vp.auth.bridge

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.openid.oid4vp.auth.http.model.Oid4vpAuthStatusResponse
import com.sphereon.openid.oid4vp.auth.model.Oid4vpAuthResult
import com.sphereon.openid.oid4vp.auth.model.Oid4vpAuthSession

/**
 * Arguments for creating a new OID4VP authentication session.
 */
data class CreateSessionArgs(
    /**
     * Query ID to use. If null, uses the default from configuration.
     */
    val queryId: String? = null,
    /**
     * OAuth2 session ID to link with this session.
     */
    val oauthSessionId: String? = null,
    /**
     * Return URL after successful authentication.
     */
    val returnUrl: String? = null,
    /**
     * Optional projection target the caller expects after authentication completes.
     * This is carried into reconciliation selector evaluation for plan selection.
     */
    val requestedProjection: String? = null,
    /**
     * Session TTL in seconds. If null, uses the default from configuration.
     */
    val ttlSeconds: Long? = null,
    /**
     * Force fresh reconciliation even if an existing identity link binding exists.
     * When true, the fast path (returning cached canonical claims) is skipped.
     */
    val forceReconciliation: Boolean = false,
)

/**
 * Result of creating a new OID4VP authentication session.
 */
data class CreateSessionResult(
    /**
     * The created session.
     */
    val session: Oid4vpAuthSession,
    /**
     * QR code as data URI (data:image/png;base64,...).
     * May be null if the agent doesn't generate a QR code image.
     */
    val qrCodeDataUri: String? = null,
    /**
     * Deep-link URI for wallet initiation (e.g., openid4vp://...).
     * Falls back to qrUri if not directly provided.
     */
    val requestUri: String? = null,
    /**
     * Status polling endpoint.
     */
    val statusUri: String,
    /**
     * QR code page endpoint (HTML page with polling).
     */
    val qrPageUri: String,
)

/**
 * OID4VP Authentication Bridge API interface.
 *
 * This interface provides methods for creating and managing OID4VP authentication sessions
 * that integrate with OAuth2/UserAuthenticationProvider.
 *
 * ## HTTP Endpoints
 *
 * The following HTTP endpoints are exposed via [Oid4vpAuthHttpAdapter]:
 * - POST /auth/oid4vp/sessions - Create a new session ([createSession])
 * - GET /auth/oid4vp/sessions/{id}/status - Poll session status ([getSessionStatus])
 * - POST /auth/oid4vp/sessions/{id}/complete - Complete authentication ([completeAuthentication])
 *
 * ## Thread Safety
 *
 * Implementations must be thread-safe for concurrent session operations.
 */
interface Oid4vpAuthBridge {
    /**
     * Create a new OID4VP authentication session.
     *
     * This creates a session using Universal OID4VP and returns the QR code data
     * for the wallet to scan.
     *
     * @param args Session creation arguments
     * @return Session with QR code data, or error
     */
    suspend fun createSession(args: CreateSessionArgs): IdkResult<CreateSessionResult, IdkError>

    /**
     * Get the current status of an authentication session.
     *
     * If the session is still PENDING, this will poll the Universal OID4VP backend
     * for any status updates (e.g., wallet scanned, credentials verified).
     *
     * @param sessionId The session ID to check
     * @return Current session status, or error if session not found
     */
    suspend fun getSessionStatus(sessionId: String): IdkResult<Oid4vpAuthStatusResponse, IdkError>

    /**
     * Complete authentication and exchange verified credentials for user identity.
     *
     * This method:
     * 1. Validates the session is in VERIFIED state
     * 2. Resolves the user identity from verified credentials
     * 3. Maps credential claims to OIDC claims
     * 4. Marks the session as COMPLETED
     *
     * @param sessionId The session ID to complete
     * @return Authentication result with user ID and claims, or error
     */
    suspend fun completeAuthentication(sessionId: String): IdkResult<Oid4vpAuthResult, IdkError>
}
