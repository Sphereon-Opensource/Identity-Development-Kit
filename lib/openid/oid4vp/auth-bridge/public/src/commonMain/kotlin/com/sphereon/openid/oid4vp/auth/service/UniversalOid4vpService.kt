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

package com.sphereon.openid.oid4vp.auth.service

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.openid.oid4vp.universal.CreateAuthorizationRequestInput
import com.sphereon.openid.oid4vp.universal.CreateAuthorizationRequestOutput
import com.sphereon.openid.oid4vp.universal.GetAuthorizationRequestStatusOutput

/**
 * Client interface for Universal OID4VP REST API operations.
 *
 * This interface matches the Universal OID4VP specification:
 * - POST /oid4vp/backend/auth/requests - Create authorization request
 * - GET /oid4vp/backend/auth/requests/{correlation_id} - Get status
 * - DELETE /oid4vp/backend/auth/requests/{correlation_id} - Delete request
 *
 * ## Implementations
 *
 * Two implementations are supported:
 * 1. **InternalUniversalOid4vpClient** - Uses the internal Oid4vpVerifierService directly.
 *    Suitable for deployments where the verifier runs in the same process.
 *
 * 2. **HttpUniversalOid4vpClient** (future) - Calls an external Universal OID4VP REST API.
 *    Suitable for deployments where the verifier runs as a separate microservice.
 *
 * ## Usage
 *
 * This client is used by [Oid4vpUserAuthenticationProviderImpl] to interact with
 * the OID4VP verifier for creating authorization requests and polling status.
 *
 * ## Thread Safety
 *
 * Implementations must be thread-safe for concurrent operations.
 */
interface UniversalOid4vpService {
    /**
     * Create a new OID4VP authorization request.
     *
     * Corresponds to: POST /oid4vp/backend/auth/requests
     *
     * This creates a new authorization session that can be used to request
     * verifiable credentials from a wallet. The response includes a QR code
     * and deep-link URI for wallet initiation.
     *
     * @param input Request parameters including query ID or inline DCQL query
     * @return Authorization request output with correlation ID, QR code, and status URI
     */
    suspend fun createAuthorizationRequest(input: CreateAuthorizationRequestInput): IdkResult<CreateAuthorizationRequestOutput, IdkError>

    /**
     * Get the current status of an authorization request.
     *
     * Corresponds to: GET /oid4vp/backend/auth/requests/{correlation_id}
     *
     * Returns the current status of the authorization session, including
     * verified credential data when the status is AUTHORIZATION_RESPONSE_VERIFIED.
     *
     * @param correlationId The correlation ID returned from createAuthorizationRequest
     * @return Current status including verified data when available
     */
    suspend fun getAuthorizationRequestStatus(correlationId: String): IdkResult<GetAuthorizationRequestStatusOutput, IdkError>

    /**
     * Delete an authorization request and its associated state.
     *
     * Corresponds to: DELETE /oid4vp/backend/auth/requests/{correlation_id}
     *
     * Use this for cleanup after completion or timeout.
     *
     * @param correlationId The correlation ID of the request to delete
     * @return Unit on success, or error if not found
     */
    suspend fun deleteAuthorizationRequest(correlationId: String): IdkResult<Unit, IdkError>
}
