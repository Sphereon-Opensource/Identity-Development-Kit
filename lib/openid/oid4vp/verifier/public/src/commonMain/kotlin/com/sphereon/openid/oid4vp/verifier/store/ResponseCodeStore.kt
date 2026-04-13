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
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.openid.oid4vp.verifier.ParsedAuthorizationResponse
import com.sphereon.openid.oid4vp.verifier.StoredAuthorizationResponse
import com.sphereon.openid.oid4vp.verifier.ValidationResult
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Store for OpenID4VP 1.0 Section 14.3.3 response_code protection.
 *
 * A response_code:
 * - MUST be cryptographically secure random
 * - MUST be single-use (consume semantics)
 * - MUST expire (TTL)
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ResponseCodeStore", exact = true)
interface ResponseCodeStore : SessionStore<String, StoredAuthorizationResponse> {
    companion object {
        const val DEFAULT_TTL_SECONDS: Long = 300
    }

    /**
     * Create and store a response_code entry with [ttlSeconds].
     *
     * @return The response_code plus expiration timestamp (epoch millis).
     */
    suspend fun createResponseCode(
        parsedResponse: ParsedAuthorizationResponse,
        validationResult: ValidationResult? = null,
        state: String? = null,
        ttlSeconds: Long = DEFAULT_TTL_SECONDS,
    ): IdkResult<ResponseCodeStoreResult, IdkError>

    /**
     * Convenience wrapper for retrieving a response by code using consume semantics.
     */
    suspend fun retrieve(
        responseCode: String,
        markAsUsed: Boolean = true,
    ): IdkResult<StoredAuthorizationResponse, IdkError> =
        getAndConsume(
            key = responseCode,
            consume = markAsUsed,
        )
}

/**
 * Result of storing a response with a generated response_code.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ResponseCodeStoreResult", exact = true)
@JsExportCompat
data class ResponseCodeStoreResult(
    val responseCode: String,
    val expiresAt: Long,
)

/**
 * Error codes for response_code operations.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ResponseCodeError", exact = true)
@JsExportCompat
object ResponseCodeError {
    const val INVALID_RESPONSE_CODE = "invalid_response_code"
    const val EXPIRED_RESPONSE_CODE = "expired_response_code"
    const val USED_RESPONSE_CODE = "used_response_code"
    const val STORAGE_ERROR = "storage_error"
}
