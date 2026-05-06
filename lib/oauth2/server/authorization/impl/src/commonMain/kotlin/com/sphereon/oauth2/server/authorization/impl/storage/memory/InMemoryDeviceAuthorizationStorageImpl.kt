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
import com.sphereon.oauth2.server.authorization.storage.DeviceAuthorizationRecord
import com.sphereon.oauth2.server.authorization.storage.DeviceAuthorizationState
import com.sphereon.oauth2.server.authorization.storage.DeviceAuthorizationStorage
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * In-memory implementation of [DeviceAuthorizationStorage].
 *
 * Suitable for development and testing only. Production deployments should bind a durable
 * implementation (Redis with TTL or SQL with an indexed `user_code` column) per the tenancy
 * partitioning contract on [DeviceAuthorizationStorage].
 *
 * Two indexes are kept on the partition: [OAuth2StoragePartition.deviceAuthorizations] is the
 * authoritative store keyed by `deviceCode`; [OAuth2StoragePartition.deviceAuthorizationUserCodeIndex]
 * is a `userCode -> deviceCode` lookup so the verification UI can resolve a typed code without
 * scanning. Both indexes are updated together on every mutation.
 *
 * `findBy*` returns expired and consumed records as-is so the verifier can distinguish
 * `expired_token` (RFC 8628 §3.5) and replay (`invalid_grant`, RFC 8628 §3.4) from a clean miss.
 * [consume] marks the record [DeviceAuthorizationState.CONSUMED] and retains it through its
 * existing `expiresAt` so a duplicate poll surfaces replay rather than degrading to a generic
 * "not found". The [cleanupExpired] sweep purges any record (including CONSUMED) whose
 * `expiresAt` has passed, mirroring [InMemoryAuthorizationCodeStorageImpl].
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<DeviceAuthorizationStorage>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("InMemoryDeviceAuthorizationStorageImpl", exact = true)
class InMemoryDeviceAuthorizationStorageImpl(
    private val backingStorage: InMemoryOAuth2BackingStorage,
) : DeviceAuthorizationStorage {
    private val partitionKey = OAuth2StoragePartitionKey.appLevel()
    private val partition get() = backingStorage.getPartition(partitionKey)

    override suspend fun create(record: DeviceAuthorizationRecord): IdkResult<DeviceAuthorizationRecord, AuthorizationServerError.StorageError> =
        try {
            val byDeviceCode = partition.deviceAuthorizations
            val byUserCode = partition.deviceAuthorizationUserCodeIndex
            if (byDeviceCode.containsKey(record.deviceCode)) {
                Err(
                    AuthorizationServerError.StorageError(
                        operation = "create",
                        details = "Device code collision",
                    ),
                )
            } else if (byUserCode.containsKey(record.userCode)) {
                Err(
                    AuthorizationServerError.StorageError(
                        operation = "create",
                        details = "User code collision",
                    ),
                )
            } else {
                byDeviceCode[record.deviceCode] = record
                byUserCode[record.userCode] = record.deviceCode
                Ok(record)
            }
        } catch (expected: Exception) {
            Err(
                AuthorizationServerError.StorageError(
                    operation = "create",
                    details = expected.message ?: "Unknown error",
                    exception = expected,
                ),
            )
        }

    override suspend fun findByDeviceCode(deviceCode: String): IdkResult<DeviceAuthorizationRecord?, AuthorizationServerError.StorageError> =
        try {
            Ok(partition.deviceAuthorizations[deviceCode])
        } catch (expected: Exception) {
            Err(
                AuthorizationServerError.StorageError(
                    operation = "findByDeviceCode",
                    details = expected.message ?: "Unknown error",
                    exception = expected,
                ),
            )
        }

    override suspend fun findByUserCode(userCode: String): IdkResult<DeviceAuthorizationRecord?, AuthorizationServerError.StorageError> =
        try {
            val deviceCode = partition.deviceAuthorizationUserCodeIndex[userCode]
            Ok(deviceCode?.let { partition.deviceAuthorizations[it] })
        } catch (expected: Exception) {
            Err(
                AuthorizationServerError.StorageError(
                    operation = "findByUserCode",
                    details = expected.message ?: "Unknown error",
                    exception = expected,
                ),
            )
        }

    override suspend fun update(record: DeviceAuthorizationRecord): IdkResult<DeviceAuthorizationRecord, AuthorizationServerError.StorageError> =
        try {
            val byDeviceCode = partition.deviceAuthorizations
            val existing = byDeviceCode[record.deviceCode]
            if (existing == null) {
                Err(
                    AuthorizationServerError.StorageError(
                        operation = "update",
                        details = "Record not found for device code",
                    ),
                )
            } else {
                // Keep the user-code index aligned: an update that rotates the user code is
                // unusual but the contract permits arbitrary record mutation, so re-thread the
                // index when it shifts.
                if (existing.userCode != record.userCode) {
                    partition.deviceAuthorizationUserCodeIndex.remove(existing.userCode)
                    partition.deviceAuthorizationUserCodeIndex[record.userCode] = record.deviceCode
                }
                byDeviceCode[record.deviceCode] = record
                Ok(record)
            }
        } catch (expected: Exception) {
            Err(
                AuthorizationServerError.StorageError(
                    operation = "update",
                    details = expected.message ?: "Unknown error",
                    exception = expected,
                ),
            )
        }

    override suspend fun consume(deviceCode: String): IdkResult<Unit, AuthorizationServerError.StorageError> =
        try {
            val byDeviceCode = partition.deviceAuthorizations
            val existing = byDeviceCode[deviceCode]
            if (existing != null) {
                byDeviceCode[deviceCode] = existing.copy(state = DeviceAuthorizationState.CONSUMED)
            }
            Ok(Unit)
        } catch (expected: Exception) {
            Err(
                AuthorizationServerError.StorageError(
                    operation = "consume",
                    details = expected.message ?: "Unknown error",
                    exception = expected,
                ),
            )
        }

    override suspend fun recordPolledAt(
        deviceCode: String,
        instant: Instant,
    ): IdkResult<Unit, AuthorizationServerError.StorageError> =
        try {
            val byDeviceCode = partition.deviceAuthorizations
            val existing = byDeviceCode[deviceCode]
            if (existing != null) {
                byDeviceCode[deviceCode] = existing.copy(lastPolledAt = instant)
            }
            Ok(Unit)
        } catch (expected: Exception) {
            Err(
                AuthorizationServerError.StorageError(
                    operation = "recordPolledAt",
                    details = expected.message ?: "Unknown error",
                    exception = expected,
                ),
            )
        }

    /**
     * Periodic sweep that removes records past their `expiresAt`, keeping the user-code index
     * aligned. Mirrors [InMemoryAuthorizationCodeStorageImpl.cleanupExpiredCodes]. Returns the
     * count of records removed.
     */
    suspend fun cleanupExpired(now: Instant = Clock.System.now()): IdkResult<Int, AuthorizationServerError.StorageError> =
        try {
            val byDeviceCode = partition.deviceAuthorizations
            val byUserCode = partition.deviceAuthorizationUserCodeIndex
            val expired = byDeviceCode.filterValues { it.expiresAt < now }
            expired.forEach { (deviceCode, record) ->
                byDeviceCode.remove(deviceCode)
                byUserCode.remove(record.userCode)
            }
            Ok(expired.size)
        } catch (expected: Exception) {
            Err(
                AuthorizationServerError.StorageError(
                    operation = "cleanupExpired",
                    details = expected.message ?: "Unknown error",
                    exception = expected,
                ),
            )
        }
}
