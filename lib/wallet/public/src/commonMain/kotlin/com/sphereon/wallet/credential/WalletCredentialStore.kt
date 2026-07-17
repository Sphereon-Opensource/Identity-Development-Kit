/*
 * Copyright 2026 Sphereon International B.V.
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

package com.sphereon.wallet.credential

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError

/**
 * Wallet credential persistence rooted in the platform blob/vault architecture.
 *
 * Implementations store credential bodies separately from [CredentialMetadata] sidecars.
 * Metadata APIs must not open or decrypt credential bodies.
 */
interface WalletCredentialStore {
    suspend fun putCredential(
        walletUnitId: String,
        record: CredentialRecord,
    ): IdkResult<CredentialRecord, IdkError>

    suspend fun getCredential(
        walletUnitId: String,
        credentialRecordId: String,
    ): IdkResult<CredentialRecord?, IdkError>

    suspend fun getMetadata(
        walletUnitId: String,
        credentialRecordId: String,
    ): IdkResult<CredentialMetadata?, IdkError>

    suspend fun listMetadata(
        walletUnitId: String,
        filter: CredentialMetadataFilter = CredentialMetadataFilter(),
    ): IdkResult<List<CredentialMetadata>, IdkError>

    suspend fun findByCredentialTypeRef(
        walletUnitId: String,
        ref: CredentialTypeRef,
    ): IdkResult<List<CredentialMetadata>, IdkError>

    suspend fun deleteCredential(
        walletUnitId: String,
        credentialRecordId: String,
    ): IdkResult<Boolean, IdkError>
}

/**
 * Local credential-store delegate backed by the platform blob abstraction.
 */
interface LocalWalletCredentialStore : WalletCredentialStore

/**
 * Remote credential-store delegate backed by the platform vault abstraction.
 */
interface RemoteWalletCredentialStore : WalletCredentialStore

/**
 * Hybrid credential-store delegate that composes local blob and remote vault delegates.
 */
interface HybridWalletCredentialStoreDelegate : WalletCredentialStore
