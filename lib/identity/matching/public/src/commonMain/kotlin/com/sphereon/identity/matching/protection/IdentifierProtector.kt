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

package com.sphereon.identity.matching.protection

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.data.store.party.model.IdentifierType
import com.sphereon.data.store.party.model.ProtectedIdentifierValue

/**
 * Turns a readable identifier into a [ProtectedIdentifierValue] (and back) according to a
 * resolved [IdentifierProtectionPolicy], reusing the platform KMS for all cryptography.
 *
 * The protector never invents algorithms: blind indexing is a domain-separated HMAC via the
 * KMS MAC command and reversible encryption is AES-256-GCM via the KMS. All three operations
 * are tenant-scoped so identifiers cannot collide or be resolved across tenants.
 */
@JsExportCompat
interface IdentifierProtector {
    /**
     * Protects [plaintext] for storage. The returned envelope carries whatever combination of
     * blind index and ciphertext the [policy] mode requires. [salt] is required for
     * SALTED_BLINDED and ignored otherwise.
     */
    suspend fun protect(
        tenantId: String,
        identityId: String?,
        type: IdentifierType,
        plaintext: String,
        policy: IdentifierProtectionPolicy,
        salt: ByteArray? = null,
    ): IdkResult<ProtectedIdentifierValue, IdkError>

    /**
     * Computes the deterministic blind index for [plaintext] under [policy], used to look up a
     * previously stored value. Returns the same value that [protect] stores in
     * [ProtectedIdentifierValue.valueHmac] for an equivalent input.
     */
    suspend fun blindIndex(
        tenantId: String,
        type: IdentifierType,
        plaintext: String,
        policy: IdentifierProtectionPolicy,
        salt: ByteArray? = null,
    ): IdkResult<String, IdkError>

    /**
     * Decrypts a previously protected value back to its normalized plaintext. Fails if the
     * envelope carries no ciphertext (for example a plaintext or blind-index-only value).
     */
    suspend fun reveal(
        protected: ProtectedIdentifierValue,
        tenantId: String,
        identityId: String?,
        type: IdentifierType,
    ): IdkResult<String, IdkError>
}
