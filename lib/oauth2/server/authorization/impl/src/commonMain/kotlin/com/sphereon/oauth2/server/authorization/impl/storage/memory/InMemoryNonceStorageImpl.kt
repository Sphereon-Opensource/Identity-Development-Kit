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

import com.sphereon.core.api.Encoding
import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.random.SecureRandom
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import com.sphereon.oauth2.server.authorization.storage.NonceStorage
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
 * In-memory implementation of NonceStorage
 *
 * IMPORTANT: This is suitable for development/testing only.
 * Production deployments should use Redis with TTL for automatic expiration.
 *
 * SECURITY CRITICAL: verifyAndConsumeNonce MUST be atomic to prevent replay attacks.
 * This simple implementation achieves atomicity through synchronous map operations.
 * Production implementations MUST use database transactions or atomic Redis operations.
 *
 * Thread Safety: Basic implementation - consider locking for production use.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<NonceStorage>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("InMemoryNonceStorageImpl", exact = true)
class InMemoryNonceStorageImpl(
    private val backingStorage: InMemoryOAuth2BackingStorage,
    private val secureRandom: SecureRandom,
) : NonceStorage {
    private val partitionKey = OAuth2StoragePartitionKey.appLevel()
    private val partition get() = backingStorage.getPartition(partitionKey)

    private data class NonceData(
        val expiresAt: Instant,
        val used: Boolean = false,
    )

    override suspend fun generateNonce(expiresAt: Instant): IdkResult<String, AuthorizationServerError.StorageError> =
        try {
            // Generate 128-bit (16 bytes) random nonce, hex-encoded.
            val nonce = secureRandom.newToken(lengthBytes = NONCE_BYTE_SIZE, encoding = Encoding.HEX)
            partition.nonces[nonce] = NonceData(expiresAt = expiresAt, used = false)
            Ok(nonce)
        } catch (expected: Exception) {
            Err(
                AuthorizationServerError.StorageError(
                    operation = "generateNonce",
                    details = expected.message ?: "Unknown error",
                    exception = expected,
                ),
            )
        }

    override suspend fun storeNonce(
        nonce: String,
        expiresAt: Instant,
    ): IdkResult<Unit, AuthorizationServerError.StorageError> =
        try {
            partition.nonces[nonce] = NonceData(expiresAt = expiresAt, used = false)
            Ok(Unit)
        } catch (expected: Exception) {
            Err(
                AuthorizationServerError.StorageError(
                    operation = "storeNonce",
                    details = expected.message ?: "Unknown error",
                    exception = expected,
                ),
            )
        }

    override suspend fun nonceExists(nonce: String): IdkResult<Boolean, AuthorizationServerError.StorageError> =
        try {
            val data = partition.nonces[nonce] as? NonceData
            val exists = data != null && data.expiresAt >= Clock.System.now()
            Ok(exists)
        } catch (expected: Exception) {
            Err(
                AuthorizationServerError.StorageError(
                    operation = "nonceExists",
                    details = expected.message ?: "Unknown error",
                    exception = expected,
                ),
            )
        }

    override suspend fun wasNonceUsed(nonce: String): IdkResult<Boolean, AuthorizationServerError.StorageError> =
        try {
            val data = partition.nonces[nonce] as? NonceData
            Ok(data?.used == true)
        } catch (expected: Exception) {
            Err(
                AuthorizationServerError.StorageError(
                    operation = "wasNonceUsed",
                    details = expected.message ?: "Unknown error",
                    exception = expected,
                ),
            )
        }

    override suspend fun markNonceAsUsed(nonce: String): IdkResult<Unit, AuthorizationServerError> {
        return try {
            val data = partition.nonces[nonce] as? NonceData
            if (data == null) {
                return Err(
                    AuthorizationServerError.InvalidDpopProof(
                        details = "Nonce not found: $nonce",
                    ),
                )
            }

            if (data.expiresAt < Clock.System.now()) {
                return Err(
                    AuthorizationServerError.InvalidDpopProof(
                        details = "Nonce expired: $nonce",
                    ),
                )
            }

            if (data.used) {
                return Err(
                    AuthorizationServerError.InvalidDpopProof(
                        details = "Nonce already used: $nonce",
                    ),
                )
            }

            partition.nonces[nonce] = data.copy(used = true)
            Ok(Unit)
        } catch (expected: Exception) {
            Err(
                AuthorizationServerError.StorageError(
                    operation = "markNonceAsUsed",
                    details = expected.message ?: "Unknown error",
                    exception = expected,
                ),
            )
        }
    }

    override suspend fun verifyAndConsumeNonce(nonce: String): IdkResult<Unit, AuthorizationServerError> {
        return try {
            val data = partition.nonces[nonce] as? NonceData
            if (data == null) {
                return Err(
                    AuthorizationServerError.InvalidDpopProof(
                        details = "Nonce not found: $nonce",
                    ),
                )
            }

            val now = Clock.System.now()
            if (data.expiresAt < now) {
                return Err(
                    AuthorizationServerError.InvalidDpopProof(
                        details = "Nonce expired: $nonce",
                    ),
                )
            }

            if (data.used) {
                return Err(
                    AuthorizationServerError.InvalidDpopProof(
                        details = "Nonce already used (replay attack detected): $nonce",
                    ),
                )
            }

            // Mark as used atomically (in production, use database transaction)
            partition.nonces[nonce] = data.copy(used = true)
            Ok(Unit)
        } catch (expected: Exception) {
            Err(
                AuthorizationServerError.StorageError(
                    operation = "verifyAndConsumeNonce",
                    details = expected.message ?: "Unknown error",
                    exception = expected,
                ),
            )
        }
    }

    override suspend fun revokeNonce(nonce: String): IdkResult<Unit, AuthorizationServerError.StorageError> =
        try {
            partition.nonces.remove(nonce)
            Ok(Unit)
        } catch (expected: Exception) {
            Err(
                AuthorizationServerError.StorageError(
                    operation = "revokeNonce",
                    details = expected.message ?: "Unknown error",
                    exception = expected,
                ),
            )
        }

    override suspend fun cleanupExpiredNonces(): IdkResult<Int, AuthorizationServerError.StorageError> =
        try {
            val now = Clock.System.now()
            val expired =
                partition.nonces.filter { (_, value) ->
                    (value as? NonceData)?.expiresAt?.let { it < now } ?: false
                }
            expired.keys.forEach { partition.nonces.remove(it) }
            Ok(expired.size)
        } catch (expected: Exception) {
            Err(
                AuthorizationServerError.StorageError(
                    operation = "cleanupExpiredNonces",
                    details = expected.message ?: "Unknown error",
                    exception = expected,
                ),
            )
        }

    override suspend fun getNonceExpiration(nonce: String): IdkResult<Instant?, AuthorizationServerError.StorageError> =
        try {
            val data = partition.nonces[nonce] as? NonceData
            Ok(data?.expiresAt)
        } catch (expected: Exception) {
            Err(
                AuthorizationServerError.StorageError(
                    operation = "getNonceExpiration",
                    details = expected.message ?: "Unknown error",
                    exception = expected,
                ),
            )
        }

    companion object {
        private const val NONCE_BYTE_SIZE = 16
    }
}
