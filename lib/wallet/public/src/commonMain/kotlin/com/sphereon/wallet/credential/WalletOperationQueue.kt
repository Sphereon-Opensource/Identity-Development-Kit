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
import kotlinx.serialization.Serializable

/**
 * Per-wallet-unit operation log used by hybrid local-first storage.
 */
interface WalletOperationQueue {
    suspend fun enqueue(
        walletUnitId: String,
        operation: WalletOperation,
    ): IdkResult<WalletOperation, IdkError>

    suspend fun listPending(walletUnitId: String): IdkResult<List<WalletOperation>, IdkError>

    suspend fun remove(
        walletUnitId: String,
        operationId: String,
    ): IdkResult<Boolean, IdkError>
}

interface WalletOperationSyncService {
    suspend fun replayPending(walletUnitId: String): IdkResult<WalletOperationReplayResult, IdkError>
}

@Serializable
data class WalletOperationReplayResult(
    val attempted: Int,
    val applied: Int,
    val conflicts: List<WalletOperationReplayConflict> = emptyList(),
    val failures: List<WalletOperationReplayFailure> = emptyList(),
)

@Serializable
data class WalletOperationReplayConflict(
    val operationId: String,
    val credentialRecordId: String?,
    val baseRemoteRevision: String?,
    val remoteRevision: String?,
)

@Serializable
data class WalletOperationReplayFailure(
    val operationId: String,
    val credentialRecordId: String?,
    val code: String,
    val message: String,
)
