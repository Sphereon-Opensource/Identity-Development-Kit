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

package com.sphereon.wallet.credential.store

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.wallet.credential.CredentialRecord
import com.sphereon.wallet.credential.LocalWalletCredentialStore
import com.sphereon.wallet.credential.RemoteWalletCredentialStore
import com.sphereon.wallet.credential.WalletCredentialStore
import com.sphereon.wallet.credential.WalletOperation
import com.sphereon.wallet.credential.WalletOperationQueue
import com.sphereon.wallet.credential.WalletOperationReplayConflict
import com.sphereon.wallet.credential.WalletOperationReplayFailure
import com.sphereon.wallet.credential.WalletOperationReplayResult
import com.sphereon.wallet.credential.WalletOperationSyncService
import com.sphereon.wallet.credential.WalletOperationType
import kotlin.time.Clock

class HybridWalletOperationSyncService(
    private val localStore: WalletCredentialStore,
    private val remoteStore: WalletCredentialStore,
    private val operationQueue: WalletOperationQueue,
) : WalletOperationSyncService {
    constructor(
        localStore: LocalWalletCredentialStore,
        remoteStore: RemoteWalletCredentialStore,
        operationQueue: WalletOperationQueue,
    ) : this(localStore as WalletCredentialStore, remoteStore as WalletCredentialStore, operationQueue)

    override suspend fun replayPending(walletUnitId: String): IdkResult<WalletOperationReplayResult, IdkError> {
        val pending = operationQueue.listPending(walletUnitId)
        if (pending.isErr) return Err(pending.error)

        var applied = 0
        val conflicts = mutableListOf<WalletOperationReplayConflict>()
        val failures = mutableListOf<WalletOperationReplayFailure>()
        for (operation in pending.value) {
            when (operation.operationType) {
                WalletOperationType.PUT_CREDENTIAL -> {
                    val result = replayPut(walletUnitId, operation)
                    when (result) {
                        ReplayOutcome.APPLIED -> applied++
                        is ReplayOutcome.CONFLICT -> conflicts += result.conflict
                        is ReplayOutcome.FAILED -> failures += result.failure
                    }
                }

                WalletOperationType.DELETE_CREDENTIAL -> {
                    val result = replayDelete(walletUnitId, operation)
                    when (result) {
                        ReplayOutcome.APPLIED -> applied++
                        is ReplayOutcome.CONFLICT -> conflicts += result.conflict
                        is ReplayOutcome.FAILED -> failures += result.failure
                    }
                }

                WalletOperationType.UPDATE_METADATA,
                WalletOperationType.APPEND_PRESENTATION_BINDING,
                -> {
                    val result = replayPut(walletUnitId, operation)
                    when (result) {
                        ReplayOutcome.APPLIED -> applied++
                        is ReplayOutcome.CONFLICT -> conflicts += result.conflict
                        is ReplayOutcome.FAILED -> failures += result.failure
                    }
                }
            }
        }

        return Ok(
            WalletOperationReplayResult(
                attempted = pending.value.size,
                applied = applied,
                conflicts = conflicts,
                failures = failures,
            ),
        )
    }

    private suspend fun replayPut(
        walletUnitId: String,
        operation: WalletOperation,
    ): ReplayOutcome {
        val credentialRecordId =
            operation.credentialRecordId
                ?: return ReplayOutcome.FAILED(operation.failure("WALLET_OPERATION_MISSING_RECORD_ID", "PUT_CREDENTIAL operation has no credentialRecordId"))
        val local = localStore.getCredential(walletUnitId, credentialRecordId)
        if (local.isErr) return ReplayOutcome.FAILED(operation.failureFrom(local.error))
        val localRecord =
            local.value
                ?: return ReplayOutcome.FAILED(operation.failure("WALLET_OPERATION_LOCAL_RECORD_MISSING", "Local credential '$credentialRecordId' is missing"))

        val remote = remoteStore.getCredential(walletUnitId, credentialRecordId)
        if (remote.isErr) return ReplayOutcome.FAILED(operation.failureFrom(remote.error))
        if (remote.value?.syncState?.remoteRevision == nextRemoteRevision(operation)) {
            return acknowledgeAppliedPut(walletUnitId, localRecord, operation)
        }
        if (remote.value.hasRemoteConflict(operation.baseRemoteRevision)) {
            return ReplayOutcome.CONFLICT(operation.conflict(remote.value))
        }

        val syncedRecord = localRecord.withSyncedOperation(operation)
        val remotePut = remoteStore.putCredential(walletUnitId, syncedRecord)
        if (remotePut.isErr) return ReplayOutcome.FAILED(operation.failureFrom(remotePut.error))

        return acknowledgeAppliedPut(walletUnitId, syncedRecord, operation)
    }

    private suspend fun acknowledgeAppliedPut(
        walletUnitId: String,
        record: CredentialRecord,
        operation: WalletOperation,
    ): ReplayOutcome {
        val syncedRecord = record.withSyncedOperation(operation)
        val ackLocal = localStore.putCredential(walletUnitId, syncedRecord)
        if (ackLocal.isErr) return ReplayOutcome.FAILED(operation.failureFrom(ackLocal.error))

        val removed = operationQueue.remove(walletUnitId, operation.id)
        return if (removed.isOk) ReplayOutcome.APPLIED else ReplayOutcome.FAILED(operation.failureFrom(removed.error))
    }

    private suspend fun replayDelete(
        walletUnitId: String,
        operation: WalletOperation,
    ): ReplayOutcome {
        val credentialRecordId =
            operation.credentialRecordId
                ?: return ReplayOutcome.FAILED(operation.failure("WALLET_OPERATION_MISSING_RECORD_ID", "DELETE_CREDENTIAL operation has no credentialRecordId"))

        val remote = remoteStore.getCredential(walletUnitId, credentialRecordId)
        if (remote.isErr) return ReplayOutcome.FAILED(operation.failureFrom(remote.error))
        if (remote.value.hasRemoteConflict(operation.baseRemoteRevision)) {
            return ReplayOutcome.CONFLICT(operation.conflict(remote.value))
        }

        val remoteDelete = remoteStore.deleteCredential(walletUnitId, credentialRecordId)
        if (remoteDelete.isErr) return ReplayOutcome.FAILED(operation.failureFrom(remoteDelete.error))

        val local = localStore.getCredential(walletUnitId, credentialRecordId)
        if (local.isErr) return ReplayOutcome.FAILED(operation.failureFrom(local.error))
        val localRecord = local.value
        if (localRecord != null) {
            val ackLocal = localStore.putCredential(walletUnitId, localRecord.withSyncedOperation(operation))
            if (ackLocal.isErr) return ReplayOutcome.FAILED(operation.failureFrom(ackLocal.error))
        }

        val removed = operationQueue.remove(walletUnitId, operation.id)
        return if (removed.isOk) ReplayOutcome.APPLIED else ReplayOutcome.FAILED(operation.failureFrom(removed.error))
    }

    private fun CredentialRecord.withSyncedOperation(operation: WalletOperation): CredentialRecord =
        copy(
            syncState =
                syncState.copy(
                    remoteRevision = nextRemoteRevision(operation),
                    lastSyncedAt = Clock.System.now(),
                    pendingOperationIds = syncState.pendingOperationIds - operation.id,
                ),
        )

    private fun CredentialRecord?.hasRemoteConflict(baseRemoteRevision: String?): Boolean {
        if (this == null) return false
        return syncState.remoteRevision != baseRemoteRevision
    }

    private fun nextRemoteRevision(operation: WalletOperation): String = "${operation.createdByDeviceId}:${operation.id}"

    private fun WalletOperation.conflict(remoteRecord: CredentialRecord?): WalletOperationReplayConflict =
        WalletOperationReplayConflict(
            operationId = id,
            credentialRecordId = credentialRecordId,
            baseRemoteRevision = baseRemoteRevision,
            remoteRevision = remoteRecord?.syncState?.remoteRevision,
        )

    private fun WalletOperation.failureFrom(error: IdkError): WalletOperationReplayFailure =
        failure(
            code = error.code,
            message = error.message.defaultMessage,
        )

    private fun WalletOperation.failure(
        code: String,
        message: String,
    ): WalletOperationReplayFailure =
        WalletOperationReplayFailure(
            operationId = id,
            credentialRecordId = credentialRecordId,
            code = code,
            message = message,
        )

    private sealed interface ReplayOutcome {
        data object APPLIED : ReplayOutcome

        data class CONFLICT(
            val conflict: WalletOperationReplayConflict
        ) : ReplayOutcome

        data class FAILED(
            val failure: WalletOperationReplayFailure
        ) : ReplayOutcome
    }
}
