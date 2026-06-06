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

package com.sphereon.openid.wallet

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError

/**
 * Persistence contract for [WalletDocument]s.
 * Implementations are responsible for durability; the in-memory default is provided by lib-wallet-impl.
 *
 * The interface separates cheap metadata reads from full-body loads so that callers
 * can match on [WalletDocumentMetadata] before loading the complete document (including
 * all credential instances). In the vault overlay only [get] decrypts the body; the
 * metadata path never needs to.
 */
interface WalletDocumentStore {
    suspend fun upsert(document: WalletDocument): IdkResult<WalletDocument, IdkError>

    /** Returns the full document including all credential instances. */
    suspend fun get(documentId: String): IdkResult<WalletDocument?, IdkError>

    /** Returns lightweight metadata for a single document without loading its credential instances. */
    suspend fun getMetadata(documentId: String): IdkResult<WalletDocumentMetadata?, IdkError>

    /** Returns lightweight metadata for every stored document. */
    suspend fun listMetadata(): IdkResult<List<WalletDocumentMetadata>, IdkError>

    /** Returns lightweight metadata for every document matching [credentialTypeId]. */
    suspend fun findMetadataByCredentialType(credentialTypeId: String): IdkResult<List<WalletDocumentMetadata>, IdkError>

    suspend fun delete(documentId: String): IdkResult<Boolean, IdkError>
}
