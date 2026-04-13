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

/**
 * Storage abstraction for attestation challenge nonces.
 *
 * draft-ietf-oauth-attestation-based-client-auth Section 4:
 * The AS may require the client to include a challenge nonce in the
 * attestation PoP JWT to ensure freshness.
 *
 * Implementation requirements:
 * - Generate cryptographically random challenges (at least 128 bits entropy)
 * - Store with expiration (typically 60-120 seconds)
 * - One-time use (consumed on verification)
 * - Thread-safe and atomic operations
 */
interface AttestationChallengeStorage {

    /**
     * Generate and store a new attestation challenge nonce.
     *
     * @return The generated challenge string, or storage error
     */
    suspend fun generateChallenge(): IdkResult<String, AuthorizationServerError.StorageError>

    /**
     * Verify a challenge nonce and consume it (one-time use).
     *
     * Atomically checks that the challenge exists, is not expired,
     * has not been used, and marks it as consumed.
     *
     * @param challenge The challenge nonce to verify
     * @return Success if valid and consumed, error otherwise
     */
    suspend fun verifyAndConsumeChallenge(challenge: String): IdkResult<Unit, AuthorizationServerError>
}
