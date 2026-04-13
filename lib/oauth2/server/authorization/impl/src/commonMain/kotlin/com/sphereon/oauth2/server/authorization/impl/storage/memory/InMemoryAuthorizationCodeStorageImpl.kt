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
import com.sphereon.oauth2.server.authorization.model.AuthorizationCodeData
import com.sphereon.oauth2.server.authorization.storage.AuthorizationCodeStorage
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.time.Clock

/**
 * In-memory implementation of AuthorizationCodeStorage
 *
 * IMPORTANT: This is suitable for development/testing only.
 * Production deployments MUST use atomic storage (Redis, SQL with row locking).
 *
 * SECURITY WARNING: This simple implementation achieves atomicity through
 * synchronous map operations. Production implementations MUST use
 * database transactions or atomic Redis operations.
 *
 * Thread Safety: Basic implementation - consider locking for production use.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<AuthorizationCodeStorage>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("InMemoryAuthorizationCodeStorageImpl", exact = true)
class InMemoryAuthorizationCodeStorageImpl(
    private val backingStorage: InMemoryOAuth2BackingStorage,
) : AuthorizationCodeStorage {
    private val partitionKey = OAuth2StoragePartitionKey.appLevel()
    private val partition get() = backingStorage.getPartition(partitionKey)

    override suspend fun storeAuthorizationCode(
        code: String,
        data: AuthorizationCodeData,
    ): IdkResult<Unit, AuthorizationServerError.StorageError> =
        try {
            partition.authorizationCodes[code] = data
            Ok(Unit)
        } catch (expected: Exception) {
            Err(
                AuthorizationServerError.StorageError(
                    operation = "storeAuthorizationCode",
                    details = expected.message ?: "Unknown error",
                    exception = expected,
                ),
            )
        }

    override suspend fun consumeAuthorizationCode(code: String): IdkResult<AuthorizationCodeData?, AuthorizationServerError.StorageError> =
        try {
            val data = partition.authorizationCodes[code]
            if (data != null) {
                // Remove the code to prevent reuse (single-use requirement)
                partition.authorizationCodes.remove(code)
            }
            Ok(data)
        } catch (expected: Exception) {
            Err(
                AuthorizationServerError.StorageError(
                    operation = "consumeAuthorizationCode",
                    details = expected.message ?: "Unknown error",
                    exception = expected,
                ),
            )
        }

    override suspend fun isCodeUsed(code: String): IdkResult<Boolean, AuthorizationServerError.StorageError> =
        try {
            // If code is not in storage, it's either been used (consumed) or never existed
            // We return false since we can't distinguish between these cases in simple in-memory storage
            Ok(!partition.authorizationCodes.containsKey(code))
        } catch (expected: Exception) {
            Err(
                AuthorizationServerError.StorageError(
                    operation = "isCodeUsed",
                    details = expected.message ?: "Unknown error",
                    exception = expected,
                ),
            )
        }

    override suspend fun revokeCodesForClient(clientId: String): IdkResult<Int, AuthorizationServerError.StorageError> =
        try {
            val toRevoke =
                partition.authorizationCodes.filter { (_, data) ->
                    data.clientId == clientId
                }
            toRevoke.keys.forEach { partition.authorizationCodes.remove(it) }
            Ok(toRevoke.size)
        } catch (expected: Exception) {
            Err(
                AuthorizationServerError.StorageError(
                    operation = "revokeCodesForClient",
                    details = expected.message ?: "Unknown error",
                    exception = expected,
                ),
            )
        }

    override suspend fun findCodesBySubject(subject: String): IdkResult<List<AuthorizationCodeData>, AuthorizationServerError.StorageError> =
        try {
            val codes =
                partition.authorizationCodes.values.filter { data ->
                    data.subject == subject
                }
            Ok(codes.toList())
        } catch (expected: Exception) {
            Err(
                AuthorizationServerError.StorageError(
                    operation = "findCodesBySubject",
                    details = expected.message ?: "Unknown error",
                    exception = expected,
                ),
            )
        }

    override suspend fun cleanupExpiredCodes(): IdkResult<Int, AuthorizationServerError.StorageError> =
        try {
            val now = Clock.System.now()
            val expired =
                partition.authorizationCodes.filter { (_, data) ->
                    data.expiresAt < now
                }
            expired.keys.forEach { partition.authorizationCodes.remove(it) }
            Ok(expired.size)
        } catch (expected: Exception) {
            Err(
                AuthorizationServerError.StorageError(
                    operation = "cleanupExpiredCodes",
                    details = expected.message ?: "Unknown error",
                    exception = expected,
                ),
            )
        }

    @ContributesTo(AppScope::class)
    interface Graph {
        val authorizationCodeStorage: AuthorizationCodeStorage
    }
}
