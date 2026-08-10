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
import com.sphereon.oauth2.server.authorization.model.AccessTokenData
import com.sphereon.oauth2.server.authorization.model.RefreshTokenData
import com.sphereon.oauth2.server.authorization.storage.TokenStorage
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.atomicfu.locks.synchronized
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.time.Clock

/**
 * In-memory implementation of TokenStorage
 *
 * IMPORTANT: This is suitable for development/testing only.
 * Production deployments should use persistent storage (Redis, SQL, etc.)
 *
 * Thread Safety: Basic implementation - consider locking for production use.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<TokenStorage>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("InMemoryTokenStorageImpl", exact = true)
class InMemoryTokenStorageImpl(
    private val backingStorage: InMemoryOAuth2BackingStorage,
) : TokenStorage {
    private val partitionKey = OAuth2StoragePartitionKey.appLevel()
    private val partition get() = backingStorage.getPartition(partitionKey)

    // ============================================================================
    // Access Token Operations
    // ============================================================================

    override suspend fun storeAccessToken(
        token: String,
        data: AccessTokenData,
    ): IdkResult<Unit, AuthorizationServerError.StorageError> =
        try {
            partition.accessTokens[token] = data
            Ok(Unit)
        } catch (expected: Exception) {
            Err(
                AuthorizationServerError.StorageError(
                    operation = "storeAccessToken",
                    details = expected.message ?: "Unknown error",
                    exception = expected,
                ),
            )
        }

    override suspend fun getAccessToken(token: String): IdkResult<AccessTokenData?, AuthorizationServerError.StorageError> =
        try {
            Ok(partition.accessTokens[token])
        } catch (expected: Exception) {
            Err(
                AuthorizationServerError.StorageError(
                    operation = "getAccessToken",
                    details = expected.message ?: "Unknown error",
                    exception = expected,
                ),
            )
        }

    override suspend fun revokeAccessToken(token: String): IdkResult<Unit, AuthorizationServerError.StorageError> =
        try {
            val existing = partition.accessTokens[token]
            if (existing != null) {
                partition.accessTokens[token] = existing.copy(revoked = true)
            }
            Ok(Unit)
        } catch (expected: Exception) {
            Err(
                AuthorizationServerError.StorageError(
                    operation = "revokeAccessToken",
                    details = expected.message ?: "Unknown error",
                    exception = expected,
                ),
            )
        }

    override suspend fun findAccessTokensBySubject(subject: String): IdkResult<List<AccessTokenData>, AuthorizationServerError.StorageError> =
        try {
            val tokens = partition.accessTokens.values.filter { it.subject == subject }
            Ok(tokens)
        } catch (expected: Exception) {
            Err(
                AuthorizationServerError.StorageError(
                    operation = "findAccessTokensBySubject",
                    details = expected.message ?: "Unknown error",
                    exception = expected,
                ),
            )
        }

    override suspend fun findAccessTokensByClient(clientId: String): IdkResult<List<AccessTokenData>, AuthorizationServerError.StorageError> =
        try {
            val tokens = partition.accessTokens.values.filter { it.clientId == clientId }
            Ok(tokens)
        } catch (expected: Exception) {
            Err(
                AuthorizationServerError.StorageError(
                    operation = "findAccessTokensByClient",
                    details = expected.message ?: "Unknown error",
                    exception = expected,
                ),
            )
        }

    override suspend fun cleanupExpiredAccessTokens(): IdkResult<Int, AuthorizationServerError.StorageError> =
        try {
            val now = Clock.System.now()
            val expired =
                partition.accessTokens.filter { (_, token) ->
                    token.expiresAt?.let { it < now } == true
                }
            expired.keys.forEach { partition.accessTokens.remove(it) }
            Ok(expired.size)
        } catch (expected: Exception) {
            Err(
                AuthorizationServerError.StorageError(
                    operation = "cleanupExpiredAccessTokens",
                    details = expected.message ?: "Unknown error",
                    exception = expected,
                ),
            )
        }

    // ============================================================================
    // Refresh Token Operations
    // ============================================================================

    override suspend fun storeRefreshToken(
        token: String,
        data: RefreshTokenData,
    ): IdkResult<Unit, AuthorizationServerError.StorageError> =
        try {
            val current = partition
            synchronized(current) { current.refreshTokens[token] = data }
            Ok(Unit)
        } catch (expected: Exception) {
            Err(
                AuthorizationServerError.StorageError(
                    operation = "storeRefreshToken",
                    details = expected.message ?: "Unknown error",
                    exception = expected,
                ),
            )
        }

    override suspend fun getRefreshToken(token: String): IdkResult<RefreshTokenData?, AuthorizationServerError.StorageError> =
        try {
            val current = partition
            Ok(synchronized(current) { current.refreshTokens[token] })
        } catch (expected: Exception) {
            Err(
                AuthorizationServerError.StorageError(
                    operation = "getRefreshToken",
                    details = expected.message ?: "Unknown error",
                    exception = expected,
                ),
            )
        }

    override suspend fun consumeRefreshToken(
        token: String,
        revoke: Boolean,
    ): IdkResult<RefreshTokenData?, AuthorizationServerError.StorageError> =
        try {
            val current = partition
            Ok(
                synchronized(current) {
                    val existing = current.refreshTokens[token] ?: return@synchronized null
                    val updated =
                        existing.copy(
                            used = true,
                            revoked = if (revoke) true else existing.revoked,
                        )
                    current.refreshTokens[token] = updated
                    updated
                },
            )
        } catch (expected: Exception) {
            Err(
                AuthorizationServerError.StorageError(
                    operation = "consumeRefreshToken",
                    details = expected.message ?: "Unknown error",
                    exception = expected,
                ),
            )
        }

    override suspend fun rotateRefreshToken(
        token: String,
        replacementRefreshToken: String,
        rotatedAt: kotlin.time.Instant,
    ): IdkResult<RefreshTokenData?, AuthorizationServerError.StorageError> =
        try {
            val current = partition
            Ok(
                synchronized(current) {
                    val existing = current.refreshTokens[token] ?: return@synchronized null
                    if (existing.replacementRefreshToken != null) {
                        existing
                    } else {
                        existing
                            .copy(
                                used = true,
                                revoked = true,
                                rotatedAt = rotatedAt,
                                replacementRefreshToken = replacementRefreshToken,
                            ).also { current.refreshTokens[token] = it }
                    }
                },
            )
        } catch (expected: Exception) {
            Err(
                AuthorizationServerError.StorageError(
                    operation = "rotateRefreshToken",
                    details = expected.message ?: "Unknown error",
                    exception = expected,
                ),
            )
        }

    override suspend fun revokeRefreshToken(token: String): IdkResult<Unit, AuthorizationServerError.StorageError> =
        try {
            val existing = partition.refreshTokens[token]
            if (existing != null) {
                partition.refreshTokens[token] = existing.copy(revoked = true)
            }
            Ok(Unit)
        } catch (expected: Exception) {
            Err(
                AuthorizationServerError.StorageError(
                    operation = "revokeRefreshToken",
                    details = expected.message ?: "Unknown error",
                    exception = expected,
                ),
            )
        }

    override suspend fun findRefreshTokensBySubject(subject: String): IdkResult<List<RefreshTokenData>, AuthorizationServerError.StorageError> =
        try {
            val tokens = partition.refreshTokens.values.filter { it.subject == subject }
            Ok(tokens)
        } catch (expected: Exception) {
            Err(
                AuthorizationServerError.StorageError(
                    operation = "findRefreshTokensBySubject",
                    details = expected.message ?: "Unknown error",
                    exception = expected,
                ),
            )
        }

    override suspend fun cleanupExpiredRefreshTokens(): IdkResult<Int, AuthorizationServerError.StorageError> =
        try {
            val now = Clock.System.now()
            val expired =
                partition.refreshTokens.filter { (_, token) ->
                    token.expiresAt?.let { it < now } == true
                }
            expired.keys.forEach { partition.refreshTokens.remove(it) }
            Ok(expired.size)
        } catch (expected: Exception) {
            Err(
                AuthorizationServerError.StorageError(
                    operation = "cleanupExpiredRefreshTokens",
                    details = expected.message ?: "Unknown error",
                    exception = expected,
                ),
            )
        }

    // ============================================================================
    // Bulk Revocation Operations
    // ============================================================================

    override suspend fun revokeAllTokensForSubject(subject: String): IdkResult<Int, AuthorizationServerError.StorageError> =
        try {
            var count = 0

            // Revoke all access tokens for subject
            partition.accessTokens.forEach { (token, data) ->
                if (data.subject == subject && !data.revoked) {
                    partition.accessTokens[token] = data.copy(revoked = true)
                    count++
                }
            }

            // Revoke all refresh tokens for subject
            partition.refreshTokens.forEach { (token, data) ->
                if (data.subject == subject && !data.revoked) {
                    partition.refreshTokens[token] = data.copy(revoked = true)
                    count++
                }
            }

            Ok(count)
        } catch (expected: Exception) {
            Err(
                AuthorizationServerError.StorageError(
                    operation = "revokeAllTokensForSubject",
                    details = expected.message ?: "Unknown error",
                    exception = expected,
                ),
            )
        }

    override suspend fun revokeAllTokensForClient(clientId: String): IdkResult<Int, AuthorizationServerError.StorageError> =
        try {
            var count = 0

            // Revoke all access tokens for client
            partition.accessTokens.forEach { (token, data) ->
                if (data.clientId == clientId && !data.revoked) {
                    partition.accessTokens[token] = data.copy(revoked = true)
                    count++
                }
            }

            // Revoke all refresh tokens for client
            partition.refreshTokens.forEach { (token, data) ->
                if (data.clientId == clientId && !data.revoked) {
                    partition.refreshTokens[token] = data.copy(revoked = true)
                    count++
                }
            }

            Ok(count)
        } catch (expected: Exception) {
            Err(
                AuthorizationServerError.StorageError(
                    operation = "revokeAllTokensForClient",
                    details = expected.message ?: "Unknown error",
                    exception = expected,
                ),
            )
        }

    @ContributesTo(AppScope::class)
    interface Graph {
        val tokenStorage: TokenStorage
    }
}
