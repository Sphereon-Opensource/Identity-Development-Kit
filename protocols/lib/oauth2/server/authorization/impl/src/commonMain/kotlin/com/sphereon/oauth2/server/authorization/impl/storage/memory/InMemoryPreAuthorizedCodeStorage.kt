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

package com.sphereon.oauth2.server.authorization.impl.storage.memory

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import com.sphereon.oauth2.server.authorization.storage.PreAuthorizedCodeData
import com.sphereon.oauth2.server.authorization.storage.PreAuthorizedCodeStorage
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.atomicfu.locks.synchronized

/**
 * In-memory implementation of PreAuthorizedCodeStorage.
 * Development/testing only — production should use atomic storage.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<PreAuthorizedCodeStorage>())
class InMemoryPreAuthorizedCodeStorage(
    private val backingStorage: InMemoryOAuth2BackingStorage,
) : PreAuthorizedCodeStorage {
    private val partitionKey = OAuth2StoragePartitionKey.appLevel()
    private val partition get() = backingStorage.getPartition(partitionKey)

    override suspend fun storePreAuthorizedCode(
        code: String,
        data: PreAuthorizedCodeData,
    ): IdkResult<Unit, AuthorizationServerError.StorageError> =
        try {
            synchronized(partition) { partition.preAuthorizedCodes[code] = data }
            Ok(Unit)
        } catch (expected: Exception) {
            Err(
                AuthorizationServerError.StorageError(
                    operation = "storePreAuthorizedCode",
                    details = expected.message ?: "Unknown error",
                    exception = expected,
                ),
            )
        }

    override suspend fun findPreAuthorizedCode(code: String): IdkResult<PreAuthorizedCodeData?, AuthorizationServerError.StorageError> =
        try {
            Ok(synchronized(partition) { partition.preAuthorizedCodes[code] })
        } catch (expected: Exception) {
            Err(
                AuthorizationServerError.StorageError(
                    operation = "findPreAuthorizedCode",
                    details = expected.message ?: "Unknown error",
                    exception = expected,
                ),
            )
        }

    override suspend fun consumePreAuthorizedCodeIfValid(
        code: String,
        expectedData: PreAuthorizedCodeData,
        now: kotlin.time.Instant,
    ): IdkResult<PreAuthorizedCodeData?, AuthorizationServerError.StorageError> =
        try {
            val data =
                synchronized(partition) {
                    val current = partition.preAuthorizedCodes[code]
                    if (current == expectedData && current.expiresAt > now) {
                        partition.preAuthorizedCodes.remove(code)
                    } else {
                        null
                    }
                }
            Ok(data)
        } catch (expected: Exception) {
            Err(
                AuthorizationServerError.StorageError(
                    operation = "consumePreAuthorizedCodeIfValid",
                    details = expected.message ?: "Unknown error",
                    exception = expected,
                ),
            )
        }

    override suspend fun consumePreAuthorizedCode(code: String): IdkResult<PreAuthorizedCodeData?, AuthorizationServerError.StorageError> =
        try {
            val data = synchronized(partition) { partition.preAuthorizedCodes.remove(code) }
            Ok(data)
        } catch (expected: Exception) {
            Err(
                AuthorizationServerError.StorageError(
                    operation = "consumePreAuthorizedCode",
                    details = expected.message ?: "Unknown error",
                    exception = expected,
                ),
            )
        }

    override suspend fun isCodeUsed(code: String): IdkResult<Boolean, AuthorizationServerError.StorageError> =
        Ok(synchronized(partition) { code !in partition.preAuthorizedCodes })
}
