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
import com.sphereon.data.store.blob.BlobInfo
import com.sphereon.data.store.blob.BlobService
import com.sphereon.data.store.blob.MetadataSearchQuery
import com.sphereon.di.session.SessionScope
import com.sphereon.wallet.credential.WalletOperation
import com.sphereon.wallet.credential.WalletOperationQueue
import com.sphereon.wallet.credential.walletPathSegment
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val operationJson = Json { ignoreUnknownKeys = true }

private const val OPERATION_CONTENT_TYPE = "application/vnd.sphereon.wallet.operation+json"
private const val META_WALLET_INSTANCE_ID = "walletInstanceId"
private const val META_OPERATION_ID = "operationId"
private const val META_CREDENTIAL_RECORD_ID = "credentialRecordId"
private const val META_OPERATION_TYPE = "operationType"

/**
 * Blob-backed per-wallet operation queue used by hybrid local-first storage.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<WalletOperationQueue>())
class BlobWalletOperationQueue(
    private val blobService: BlobService,
) : WalletOperationQueue {
    override suspend fun enqueue(
        walletInstanceId: String,
        operation: WalletOperation,
    ): IdkResult<WalletOperation, IdkError> {
        if (operation.walletInstanceId != walletInstanceId) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "operation.walletInstanceId must match walletInstanceId"))
        }

        val result =
            blobService.storeBlob(
                target =
                    BlobInfo(
                        path = operationPath(walletInstanceId, operation.id),
                        contentType = OPERATION_CONTENT_TYPE,
                        metadata = operationIndex(operation),
                    ),
                data = operationJson.encodeToString(operation).encodeToByteArray(),
            )
        return if (result.isOk) Ok(operation) else Err(result.error)
    }

    override suspend fun listPending(walletInstanceId: String): IdkResult<List<WalletOperation>, IdkError> {
        val findResult =
            blobService.findByMetadata(
                info = BlobInfo(),
                query =
                    MetadataSearchQuery(
                        pathPrefix = operationPrefix(walletInstanceId),
                        customMetadata = mapOf(META_WALLET_INSTANCE_ID to walletInstanceId),
                        maxResults = 1000,
                    ),
            )
        if (findResult.isErr) return Err(findResult.error)

        val operations = mutableListOf<WalletOperation>()
        for (descriptor in findResult.value) {
            val getResult = blobService.getBlob(BlobInfo(path = descriptor.path, storeId = descriptor.storeId))
            if (getResult.isOk) {
                val operation = operationJson.decodeFromString<WalletOperation>(getResult.value.data.decodeToString())
                if (operation.walletInstanceId == walletInstanceId) operations += operation
            }
        }
        return Ok(operations.sortedBy { it.createdAt })
    }

    override suspend fun remove(
        walletInstanceId: String,
        operationId: String,
    ): IdkResult<Boolean, IdkError> {
        val result = blobService.deleteBlob(BlobInfo(path = operationPath(walletInstanceId, operationId)))
        return if (result.isOk) Ok(result.value) else Err(result.error)
    }

    private fun operationIndex(operation: WalletOperation): Map<String, String> =
        buildMap {
            put(META_WALLET_INSTANCE_ID, operation.walletInstanceId)
            put(META_OPERATION_ID, operation.id)
            put(META_OPERATION_TYPE, operation.operationType.name)
            operation.credentialRecordId?.let { put(META_CREDENTIAL_RECORD_ID, it) }
        }
}

internal fun operationPrefix(walletInstanceId: String): String = "wallet-instances/${walletPathSegment(walletInstanceId, "walletInstanceId")}/ops/"

internal fun operationPath(
    walletInstanceId: String,
    operationId: String,
): String = operationPrefix(walletInstanceId) + walletPathSegment(operationId, "operationId")
