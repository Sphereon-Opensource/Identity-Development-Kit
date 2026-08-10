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
import com.sphereon.core.api.error.ErrorCategory
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.compat.Uuid
import com.sphereon.wallet.credential.CredentialMetadata
import com.sphereon.wallet.credential.CredentialMetadataFilter
import com.sphereon.wallet.credential.CredentialRecord
import com.sphereon.wallet.credential.CredentialTypeRef
import com.sphereon.wallet.credential.HybridWalletCredentialStoreDelegate
import com.sphereon.wallet.credential.LocalWalletCredentialStore
import com.sphereon.wallet.credential.RemoteWalletCredentialStore
import com.sphereon.wallet.credential.WalletCredentialStore
import com.sphereon.wallet.credential.WalletDeviceIdProvider
import com.sphereon.wallet.credential.WalletOperation
import com.sphereon.wallet.credential.WalletOperationQueue
import com.sphereon.wallet.credential.WalletOperationType
import dev.zacsweers.metro.Inject
import kotlin.time.Clock

/**
 * Hybrid local-first [HybridWalletCredentialStoreDelegate].
 *
 * This adapter composes the existing local and remote wallet stores. It writes locally first,
 * records a wallet operation under `wallet-units/{walletUnitId}/ops/{operationId}`,
 * then attempts remote replication with a base-revision check.
 */
class HybridWalletCredentialStore private constructor(
    private val localStore: WalletCredentialStore,
    private val remoteStore: WalletCredentialStore,
    private val operationQueue: WalletOperationQueue,
    private val fixedDeviceId: String?,
    private val deviceIdProvider: WalletDeviceIdProvider?,
) : HybridWalletCredentialStoreDelegate {
    constructor(
        localStore: WalletCredentialStore,
        remoteStore: WalletCredentialStore,
        operationQueue: WalletOperationQueue,
        deviceId: String,
    ) : this(localStore, remoteStore, operationQueue, fixedDeviceId = deviceId, deviceIdProvider = null)

    @Inject
    constructor(
        localStore: LocalWalletCredentialStore,
        remoteStore: RemoteWalletCredentialStore,
        operationQueue: WalletOperationQueue,
        deviceIdProvider: WalletDeviceIdProvider,
    ) : this(localStore, remoteStore, operationQueue, fixedDeviceId = null, deviceIdProvider = deviceIdProvider)

    init {
        require(fixedDeviceId == null || fixedDeviceId.isNotBlank()) { "deviceId must not be blank" }
        require((fixedDeviceId != null) xor (deviceIdProvider != null)) { "Exactly one deviceId source must be configured" }
    }

    override suspend fun putCredential(
        walletUnitId: String,
        record: CredentialRecord,
    ): IdkResult<CredentialRecord, IdkError> {
        if (record.walletUnitId != walletUnitId) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "record.walletUnitId must match walletUnitId"))
        }
        val resolvedDeviceId = deviceId()
        if (resolvedDeviceId.isErr) return Err(resolvedDeviceId.error)
        val deviceId = resolvedDeviceId.value

        val operationId = Uuid.v4String()
        val operation =
            WalletOperation(
                id = operationId,
                walletUnitId = walletUnitId,
                credentialRecordId = record.id,
                operationType = WalletOperationType.PUT_CREDENTIAL,
                baseRemoteRevision = record.syncState.remoteRevision,
                createdByDeviceId = deviceId,
                createdAt = Clock.System.now(),
                payloadRef = record.instances.lastOrNull()?.bodyStorageRef,
            )
        val pendingRecord = record.withPendingOperation(operation)

        val localResult = localStore.putCredential(walletUnitId, pendingRecord)
        if (localResult.isErr) return Err(localResult.error)

        val enqueueResult = operationQueue.enqueue(walletUnitId, operation)
        if (enqueueResult.isErr) return Err(enqueueResult.error)

        val remoteExisting = remoteStore.getCredential(walletUnitId, record.id)
        if (remoteExisting.isErr) return Ok(pendingRecord)
        val conflict = remoteExisting.value.hasRemoteConflict(operation.baseRemoteRevision)
        if (conflict) return Err(syncConflict("Remote credential '${record.id}' changed since base revision '${operation.baseRemoteRevision}'"))

        val syncedRecord =
            pendingRecord.copy(
                syncState =
                    pendingRecord.syncState.copy(
                        remoteRevision = nextRemoteRevision(operation),
                        lastSyncedAt = Clock.System.now(),
                        pendingOperationIds = pendingRecord.syncState.pendingOperationIds - operation.id,
                    ),
            )
        val remotePut = remoteStore.putCredential(walletUnitId, syncedRecord)
        if (remotePut.isErr) return Ok(pendingRecord)

        val ackLocal = localStore.putCredential(walletUnitId, syncedRecord)
        if (ackLocal.isErr) return Err(ackLocal.error)
        operationQueue.remove(walletUnitId, operation.id)
        return Ok(syncedRecord)
    }

    override suspend fun getCredential(
        walletUnitId: String,
        credentialRecordId: String,
    ): IdkResult<CredentialRecord?, IdkError> {
        val localResult = localStore.getCredential(walletUnitId, credentialRecordId)
        if (localResult.isOk && localResult.value != null) return localResult

        val remoteResult = remoteStore.getCredential(walletUnitId, credentialRecordId)
        if (remoteResult.isErr) {
            return if (localResult.isErr) Err(localResult.error) else Err(remoteResult.error)
        }
        val remoteRecord =
            remoteResult.value
                ?: return if (localResult.isErr) Err(localResult.error) else Ok(null)

        val cacheResult = localStore.putCredential(walletUnitId, remoteRecord)
        return if (cacheResult.isOk) Ok(remoteRecord) else Err(cacheResult.error)
    }

    override suspend fun getMetadata(
        walletUnitId: String,
        credentialRecordId: String,
    ): IdkResult<CredentialMetadata?, IdkError> {
        val localResult = localStore.getMetadata(walletUnitId, credentialRecordId)
        if (localResult.isErr) return Err(localResult.error)
        if (localResult.value != null) return localResult
        return remoteStore.getMetadata(walletUnitId, credentialRecordId)
    }

    override suspend fun listMetadata(
        walletUnitId: String,
        filter: CredentialMetadataFilter,
    ): IdkResult<List<CredentialMetadata>, IdkError> {
        val localAllResult = localStore.listMetadata(walletUnitId, CredentialMetadataFilter(includeDeleted = true))
        if (localAllResult.isErr) {
            return remoteStore.listMetadata(walletUnitId, filter)
        }
        val remoteResult = remoteStore.listMetadata(walletUnitId, filter)
        if (remoteResult.isErr) return Ok(localAllResult.value.filter { it.matches(filter) })

        return Ok(mergeLocalOverrideRemote(localAllResult.value, remoteResult.value, filter))
    }

    override suspend fun findByCredentialTypeRef(
        walletUnitId: String,
        ref: CredentialTypeRef,
    ): IdkResult<List<CredentialMetadata>, IdkError> {
        val localAllResult = localStore.listMetadata(walletUnitId, CredentialMetadataFilter(includeDeleted = true))
        if (localAllResult.isErr) {
            return remoteStore.findByCredentialTypeRef(walletUnitId, ref)
        }
        val remoteResult = remoteStore.findByCredentialTypeRef(walletUnitId, ref)
        val filter = CredentialMetadataFilter(credentialTypeRefs = setOf(ref))
        if (remoteResult.isErr) return Ok(localAllResult.value.filter { it.matches(filter) })

        return Ok(mergeLocalOverrideRemote(localAllResult.value, remoteResult.value, filter))
    }

    override suspend fun deleteCredential(
        walletUnitId: String,
        credentialRecordId: String,
    ): IdkResult<Boolean, IdkError> {
        val localRecordResult = localStore.getCredential(walletUnitId, credentialRecordId)
        if (localRecordResult.isErr) return Err(localRecordResult.error)
        val localRecord = localRecordResult.value
        val baseRemoteRevision = localRecord?.syncState?.remoteRevision

        val remoteExisting = remoteStore.getCredential(walletUnitId, credentialRecordId)
        if (remoteExisting.isOk && remoteExisting.value.hasRemoteConflict(baseRemoteRevision)) {
            return Err(syncConflict("Remote credential '$credentialRecordId' changed since base revision '$baseRemoteRevision'"))
        }

        val resolvedDeviceId = deviceId()
        if (resolvedDeviceId.isErr) return Err(resolvedDeviceId.error)
        val deviceId = resolvedDeviceId.value
        val operation =
            WalletOperation(
                id = Uuid.v4String(),
                walletUnitId = walletUnitId,
                credentialRecordId = credentialRecordId,
                operationType = WalletOperationType.DELETE_CREDENTIAL,
                baseRemoteRevision = baseRemoteRevision,
                createdByDeviceId = deviceId,
                createdAt = Clock.System.now(),
            )

        val now = Clock.System.now()
        val localDelete =
            if (localRecord != null) {
                localStore
                    .putCredential(
                        walletUnitId,
                        localRecord.copy(
                            updatedAt = now,
                            deletedAt = now,
                            syncState =
                                localRecord.syncState.copy(
                                    localRevision = localRecord.syncState.localRevision + 1,
                                    deviceId = deviceId,
                                    pendingOperationIds = (localRecord.syncState.pendingOperationIds + operation.id).distinct(),
                                    tombstone = true,
                                ),
                        ),
                    ).mapToDeleteResult()
            } else {
                localStore.deleteCredential(walletUnitId, credentialRecordId)
            }
        if (localDelete.isErr) return Err(localDelete.error)

        val enqueueResult = operationQueue.enqueue(walletUnitId, operation)
        if (enqueueResult.isErr) return Err(enqueueResult.error)

        if (remoteExisting.isErr) return Ok(localDelete.value)

        val remoteDelete = remoteStore.deleteCredential(walletUnitId, credentialRecordId)
        if (remoteDelete.isErr) return Ok(localDelete.value)
        operationQueue.remove(walletUnitId, operation.id)
        return localDelete
    }

    private fun CredentialRecord.withPendingOperation(operation: WalletOperation): CredentialRecord =
        copy(
            syncState =
                syncState.copy(
                    localRevision = syncState.localRevision + 1,
                    deviceId = operation.createdByDeviceId,
                    pendingOperationIds = (syncState.pendingOperationIds + operation.id).distinct(),
                ),
        )

    private fun CredentialRecord?.hasRemoteConflict(baseRemoteRevision: String?): Boolean {
        if (this == null) return false
        return syncState.remoteRevision != baseRemoteRevision
    }

    private fun nextRemoteRevision(operation: WalletOperation): String = "${operation.createdByDeviceId}:${operation.id}"

    private suspend fun deviceId(): IdkResult<String, IdkError> {
        fixedDeviceId?.let { return Ok(it) }
        return deviceIdProvider?.deviceId()
            ?: Err(IdkError.UNKNOWN_ERROR(message = "No wallet device ID provider configured"))
    }

    private fun IdkResult<CredentialRecord, IdkError>.mapToDeleteResult(): IdkResult<Boolean, IdkError> = if (isOk) Ok(true) else Err(error)

    private fun mergeLocalOverrideRemote(
        localAll: List<CredentialMetadata>,
        remoteVisible: List<CredentialMetadata>,
        filter: CredentialMetadataFilter,
    ): List<CredentialMetadata> {
        val localRecordIds = localAll.mapTo(mutableSetOf()) { it.credentialRecordId }
        val merged = linkedMapOf<String, CredentialMetadata>()
        for (remote in remoteVisible) {
            if (remote.credentialRecordId !in localRecordIds) {
                merged[remote.credentialRecordId] = remote
            }
        }
        for (local in localAll) {
            if (local.matches(filter)) {
                merged[local.credentialRecordId] = local
            }
        }
        return merged.values.toList()
    }

    private fun syncConflict(message: String): IdkError =
        IdkError.fromString(
            message = message,
            code = "SYNC_CONFLICT",
            category = ErrorCategory.CONFLICT,
        )
}
