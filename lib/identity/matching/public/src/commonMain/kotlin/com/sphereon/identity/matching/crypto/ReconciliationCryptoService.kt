/*
 * Copyright 2025 Sphereon International B.V.
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

package com.sphereon.identity.matching.crypto

/**
 * Provides domain-separated HMAC hashing and AES-256-GCM encryption
 * for identity reconciliation.
 *
 * Uses three distinct keys:
 * - Key A: HMAC-SHA256 for holder key identifiers
 * - Key B: HMAC-SHA256 for institution/external identifiers
 * - Key C: AES-256-GCM for reversible payload encryption
 *
 * Supports key rotation via dual-read methods that hash with the previous key version.
 */
interface ReconciliationCryptoService {

    /** HMAC-SHA256 with domain-separated Key A for holder key identifiers */
    suspend fun hashHolderKey(holderKey: String): HashedIdentifier

    /** HMAC-SHA256 with domain-separated Key B for institution identifiers */
    suspend fun hashExternalIdentifier(identifier: String): HashedIdentifier

    /** AES-256-GCM encrypt a reversible field */
    suspend fun encrypt(plaintext: String): EncryptedPayload

    /** AES-256-GCM decrypt (uses key version from payload metadata) */
    suspend fun decrypt(payload: EncryptedPayload): String

    /** HMAC-SHA256 with previous Key A (for dual-read during rotation). Returns null if no previous key exists. */
    suspend fun hashHolderKeyWithPrevious(holderKey: String): HashedIdentifier?

    /** HMAC-SHA256 with previous Key B (for dual-read during rotation). Returns null if no previous key exists. */
    suspend fun hashExternalIdentifierWithPrevious(identifier: String): HashedIdentifier?
}
