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
import com.sphereon.oauth2.server.authorization.command.VerifiedAuthorizationRequest
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import com.sphereon.oauth2.server.authorization.storage.PushedAuthorizationRequestStorage
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * In-memory [PushedAuthorizationRequestStorage] backed by [InMemoryOAuth2BackingStorage]'s
 * existing `requestUris` partition map. Single-use semantics are enforced by removing the entry
 * inside [consumeRequest]; expired entries are evicted on read.
 *
 * Suitable only for single-instance and test deployments — multi-instance production setups need
 * a distributed cache so a `request_uri` issued by one node can be redeemed at another.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<PushedAuthorizationRequestStorage>())
class InMemoryPushedAuthorizationRequestStorageImpl(
    private val backingStorage: InMemoryOAuth2BackingStorage,
    private val clock: Clock,
) : PushedAuthorizationRequestStorage {
    private val partitionKey = OAuth2StoragePartitionKey.appLevel()
    private val partition get() = backingStorage.getPartition(partitionKey)

    override suspend fun storeRequest(
        requestUri: String,
        request: VerifiedAuthorizationRequest,
        expiresAt: Instant,
    ): IdkResult<Unit, AuthorizationServerError.StorageError> =
        try {
            partition.requestUris[requestUri] =
                StoredAuthorizationRequest(
                    requestUri = requestUri,
                    request = request,
                    expiresAt = expiresAt,
                )
            Ok(Unit)
        } catch (expected: Exception) {
            Err(
                AuthorizationServerError.StorageError(
                    operation = "storeRequest",
                    details = expected.message ?: "Unknown error",
                    exception = expected,
                ),
            )
        }

    override suspend fun lookupRequest(requestUri: String): IdkResult<VerifiedAuthorizationRequest?, AuthorizationServerError.StorageError> =
        try {
            val now = clock.now()
            val stored = partition.requestUris[requestUri]
            when {
                stored == null -> {
                    Ok(null)
                }

                stored.expiresAt <= now -> {
                    // Expired — clear it eagerly so we don't keep stale entries around, and
                    // surface as not found to the caller.
                    partition.requestUris.remove(requestUri)
                    Ok(null)
                }

                else -> {
                    Ok(stored.request)
                }
            }
        } catch (expected: Exception) {
            Err(
                AuthorizationServerError.StorageError(
                    operation = "lookupRequest",
                    details = expected.message ?: "Unknown error",
                    exception = expected,
                ),
            )
        }

    override suspend fun consumeRequest(requestUri: String,): IdkResult<VerifiedAuthorizationRequest?, AuthorizationServerError.StorageError> =
        try {
            val now = clock.now()
            val stored = partition.requestUris.remove(requestUri)
            if (stored == null) {
                Ok(null)
            } else if (stored.expiresAt <= now) {
                // Expired — single-use semantics already removed the entry above; surface as not found.
                Ok(null)
            } else {
                Ok(stored.request)
            }
        } catch (expected: Exception) {
            Err(
                AuthorizationServerError.StorageError(
                    operation = "consumeRequest",
                    details = expected.message ?: "Unknown error",
                    exception = expected,
                ),
            )
        }

    override suspend fun clear(): IdkResult<Unit, AuthorizationServerError.StorageError> =
        try {
            partition.requestUris.clear()
            Ok(Unit)
        } catch (expected: Exception) {
            Err(
                AuthorizationServerError.StorageError(
                    operation = "clear",
                    details = expected.message ?: "Unknown error",
                    exception = expected,
                ),
            )
        }
}
