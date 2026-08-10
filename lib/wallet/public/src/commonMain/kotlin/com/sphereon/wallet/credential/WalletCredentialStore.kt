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
import com.sphereon.wallet.credential.store.WalletCredentialProtectedDocumentRole
import kotlinx.serialization.Serializable

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
 * Opaque, already unit-protected credential material produced by a wallet app or
 * a dedicated WSCD-confined ingress. The remote store can validate bindings and
 * persist these bytes without ever receiving a credential record in plaintext.
 */
@Serializable
data class ProtectedWalletCredentialDocument(
    val credentialInstanceId: String,
    val documentRole: WalletCredentialProtectedDocumentRole,
    val protectedBody: ByteArray,
) {
    init {
        require(credentialInstanceId.isNotBlank() && protectedBody.isNotEmpty()) {
            "protected_wallet_credential_document_invalid"
        }
    }
}

@Serializable
data class ProtectedWalletCredentialIngestion(
    val walletUnitId: String,
    val credentialRecordId: String,
    val protectedRecordEnvelope: ByteArray,
    val protectedInstanceBodies: List<ProtectedWalletCredentialDocument>,
) {
    init {
        require(walletUnitId.isNotBlank() && credentialRecordId.isNotBlank() && protectedRecordEnvelope.isNotEmpty()) {
            "protected_wallet_credential_ingestion_invalid"
        }
        require(protectedInstanceBodies.map { it.credentialInstanceId }.toSet().size == protectedInstanceBodies.size) {
            "protected_wallet_credential_instance_duplicate"
        }
        require(protectedInstanceBodies.all { it.documentRole == WalletCredentialProtectedDocumentRole.CREDENTIAL_INSTANCE_BODY }) {
            "protected_wallet_credential_document_role_invalid"
        }
    }
}

interface ProtectedRemoteWalletCredentialStore {
    suspend fun putProtectedCredential(
        ingestion: ProtectedWalletCredentialIngestion,
    ): IdkResult<Unit, IdkError>
}

/**
 * Hybrid credential-store delegate that composes local blob and remote vault delegates.
 */
interface HybridWalletCredentialStoreDelegate : WalletCredentialStore
