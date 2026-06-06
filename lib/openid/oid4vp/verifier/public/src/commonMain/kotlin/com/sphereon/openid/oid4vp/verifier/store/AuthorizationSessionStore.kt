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

package com.sphereon.openid.oid4vp.verifier.store

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.openid.oid4vp.common.store.Oid4vpStore
import com.sphereon.openid.oid4vp.verifier.ParsedAuthorizationResponse
import com.sphereon.openid.oid4vp.verifier.ValidationResult
import com.sphereon.openid.oid4vp.verifier.model.AuthorizationSession
import com.sphereon.openid.oid4vp.verifier.model.AuthorizationSessionCreateArgs
import com.sphereon.openid.oid4vp.verifier.model.AuthorizationSessionError
import com.sphereon.openid.oid4vp.verifier.model.AuthorizationSessionStatus
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Store for OID4VP authorization sessions (Universal OID4VP compatible).
 *
 * Implementations are provided in the corresponding `impl` module (for example a KV-backed store).
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("AuthorizationSessionStore", exact = true)
interface AuthorizationSessionStore : Oid4vpStore<String, AuthorizationSession> {
    companion object {
        /**
         * Default session TTL: 10 minutes.
         */
        const val DEFAULT_TTL_SECONDS: Long = 600
    }

    /**
     * Create a new authorization session.
     *
     * @param correlationId Optional external business key (auto-generated if null by implementation).
     * @param args Create arguments.
     * @param ttlSeconds Session lifetime in seconds.
     */
    suspend fun createSession(
        correlationId: String? = null,
        args: AuthorizationSessionCreateArgs,
        ttlSeconds: Long = DEFAULT_TTL_SECONDS,
    ): IdkResult<AuthorizationSession, IdkError>

    /**
     * Retrieve a session by correlation id.
     */
    suspend fun getByCorrelationId(correlationId: String): IdkResult<AuthorizationSession?, IdkError>

    /**
     * Update session status.
     */
    suspend fun updateStatus(
        correlationId: String,
        status: AuthorizationSessionStatus,
        error: AuthorizationSessionError? = null,
    ): IdkResult<AuthorizationSession, IdkError>

    /**
     * Store the parsed authorization response (transitions to RESPONSE_RECEIVED).
     */
    suspend fun storeResponse(
        correlationId: String,
        parsedResponse: ParsedAuthorizationResponse,
    ): IdkResult<AuthorizationSession, IdkError>

    /**
     * Store validation result (transitions to RESPONSE_VERIFIED on success; implementations may also store error on failure).
     */
    suspend fun storeValidationResult(
        correlationId: String,
        validationResult: ValidationResult,
    ): IdkResult<AuthorizationSession, IdkError>

    /**
     * Get a session for request_uri handling.
     *
     * Implementations should only return a session when it is still eligible for request retrieval,
     * and may atomically transition it to [AuthorizationSessionStatus.AUTHORIZATION_REQUEST_RETRIEVED]
     * when [markRetrieved] is true.
     */
    suspend fun getForRequestUri(
        correlationId: String,
        markRetrieved: Boolean = true,
    ): IdkResult<AuthorizationSession?, IdkError>
}
