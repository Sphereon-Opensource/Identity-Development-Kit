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
import kotlin.time.Instant

/**
 * Storage abstraction for OID4VCI pre-authorized codes
 *
 * Pre-authorized codes are single-use tokens issued by the credential issuer
 * and exchanged at the token endpoint. Security requirements mirror authorization
 * codes (RFC 6749 Section 10.5): atomic consume, short TTL, replay detection.
 */
interface PreAuthorizedCodeStorage {
    /**
     * Store a pre-authorized code with associated session data.
     */
    suspend fun storePreAuthorizedCode(
        code: String,
        data: PreAuthorizedCodeData,
    ): IdkResult<Unit, AuthorizationServerError.StorageError>

    /**
     * Atomically consume a pre-authorized code, returning its data.
     *
     * Returns null if the code does not exist or has already been consumed.
     * Must be atomic to prevent replay attacks.
     */
    suspend fun consumePreAuthorizedCode(code: String): IdkResult<PreAuthorizedCodeData?, AuthorizationServerError.StorageError>

    /**
     * Check whether a pre-authorized code has already been used.
     */
    suspend fun isCodeUsed(code: String): IdkResult<Boolean, AuthorizationServerError.StorageError>
}

/**
 * Data associated with a pre-authorized code.
 */
data class PreAuthorizedCodeData(
    val sessionId: String,
    val credentialConfigurationIds: List<String>,
    val subject: String? = null,
    val txCodeRequired: Boolean = false,
    val txCodeHash: String? = null,
    val clientId: String? = null,
    val issuerIdentifier: String? = null,
    val useCredentialIdentifiers: Boolean = true,
    val createdAt: Instant,
    val expiresAt: Instant,
)
