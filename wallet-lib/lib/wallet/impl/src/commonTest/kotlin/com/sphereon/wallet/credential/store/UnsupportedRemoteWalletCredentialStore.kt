/*
 * Copyright 2026 Sphereon International B.V.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sphereon.wallet.credential.store

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.wallet.credential.CredentialMetadata
import com.sphereon.wallet.credential.CredentialMetadataFilter
import com.sphereon.wallet.credential.CredentialRecord
import com.sphereon.wallet.credential.CredentialTypeRef
import com.sphereon.wallet.credential.ProtectedRemoteWalletCredentialStore
import com.sphereon.wallet.credential.ProtectedWalletCredentialIngestion
import com.sphereon.wallet.credential.RemoteWalletCredentialStore

/** Test double used to prove local-store routing never invokes a remote authority. */
class UnsupportedRemoteWalletCredentialStore : RemoteWalletCredentialStore, ProtectedRemoteWalletCredentialStore {
    override suspend fun putCredential(
        walletUnitId: String,
        record: CredentialRecord,
    ): IdkResult<CredentialRecord, IdkError> = unsupported()

    override suspend fun getCredential(
        walletUnitId: String,
        credentialRecordId: String,
    ): IdkResult<CredentialRecord?, IdkError> = unsupported()

    override suspend fun getMetadata(
        walletUnitId: String,
        credentialRecordId: String,
    ): IdkResult<CredentialMetadata?, IdkError> = unsupported()

    override suspend fun listMetadata(
        walletUnitId: String,
        filter: CredentialMetadataFilter,
    ): IdkResult<List<CredentialMetadata>, IdkError> = unsupported()

    override suspend fun findByCredentialTypeRef(
        walletUnitId: String,
        ref: CredentialTypeRef,
    ): IdkResult<List<CredentialMetadata>, IdkError> = unsupported()

    override suspend fun deleteCredential(
        walletUnitId: String,
        credentialRecordId: String,
    ): IdkResult<Boolean, IdkError> = unsupported()

    override suspend fun putProtectedCredential(
        ingestion: ProtectedWalletCredentialIngestion,
    ): IdkResult<Unit, IdkError> = unsupported()

    private fun <T> unsupported(): IdkResult<T, IdkError> =
        Err(IdkError.UNSUPPORTED_OPERATION_ERROR(operation = "test-remote-wallet-credential-store"))
}
