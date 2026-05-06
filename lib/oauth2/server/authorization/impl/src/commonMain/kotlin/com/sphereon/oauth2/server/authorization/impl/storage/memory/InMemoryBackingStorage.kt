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

import com.sphereon.oauth2.server.authorization.model.AccessTokenData
import com.sphereon.oauth2.server.authorization.model.AuthorizationCodeData
import com.sphereon.oauth2.server.authorization.model.AuthorizationSession
import com.sphereon.oauth2.server.authorization.model.ClientRegistration
import com.sphereon.oauth2.server.authorization.model.RefreshTokenData
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.time.Instant

/**
 * Storage partition key for OAuth2 Authorization Server storage
 *
 * Allows isolation of OAuth2 data by tenant, principal, or app-wide.
 * Server-side OAuth2 operations typically use tenant-level or app-level partitioning
 * since sessions are per-request.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("OAuth2StoragePartitionKey", exact = true)
data class OAuth2StoragePartitionKey(
    val tenantId: String? = null,
    val principalId: String? = null,
) {
    companion object {
        /**
         * App-wide storage (no partitioning)
         * Suitable for single-tenant deployments
         */
        fun appLevel() = OAuth2StoragePartitionKey()

        /**
         * Tenant-level storage
         * Suitable for multi-tenant deployments where each tenant's data is isolated
         */
        fun forTenant(tenantId: String) = OAuth2StoragePartitionKey(tenantId = tenantId)

        /**
         * Principal + tenant level storage
         * Suitable when user-specific isolation is needed beyond tenant
         */
        fun forPrincipalTenant(
            tenantId: String,
            principalId: String,
        ) = OAuth2StoragePartitionKey(tenantId = tenantId, principalId = principalId)
    }
}

/**
 * Storage partition holding all OAuth2 data for a specific partition
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("OAuth2StoragePartition", exact = true)
data class OAuth2StoragePartition(
    // Token storage
    val accessTokens: MutableMap<String, AccessTokenData> = mutableMapOf(),
    val refreshTokens: MutableMap<String, RefreshTokenData> = mutableMapOf(),
    // Authorization code storage
    val authorizationCodes: MutableMap<String, AuthorizationCodeData> = mutableMapOf(),
    // Client registry
    val clients: MutableMap<String, ClientRegistration> = mutableMapOf(),
    // Session storage
    val sessions: MutableMap<String, AuthorizationSession> = mutableMapOf(),
    // DPoP nonce storage (stores InMemoryNonceStorageImpl.NonceData internally)
    val nonces: MutableMap<String, Any> = mutableMapOf(),
    // PAR request URI storage
    val requestUris: MutableMap<String, StoredAuthorizationRequest> = mutableMapOf(),
    // Pre-authorized code storage (OID4VCI)
    val preAuthorizedCodes: MutableMap<String, com.sphereon.oauth2.server.authorization.storage.PreAuthorizedCodeData> = mutableMapOf(),
    // Device authorization grant storage (RFC 8628). Records are keyed by `device_code`; an
    // additional `userCode -> deviceCode` index is kept on the storage implementation so the
    // verification UI can resolve a typed user code to its record without scanning.
    val deviceAuthorizations: MutableMap<String, com.sphereon.oauth2.server.authorization.storage.DeviceAuthorizationRecord> = mutableMapOf(),
    val deviceAuthorizationUserCodeIndex: MutableMap<String, String> = mutableMapOf(),
)

/**
 * Stored authorization request for PAR
 */
data class StoredAuthorizationRequest(
    val requestUri: String,
    val request: com.sphereon.oauth2.server.authorization.command.VerifiedAuthorizationRequest,
    val expiresAt: Instant,
)

/**
 * App-scoped backing storage for OAuth2 Authorization Server
 *
 * This singleton maintains separate storage partitions for multi-tenant support.
 * All OAuth2 data (tokens, codes, clients, sessions, nonces) is stored in memory
 * and partitioned by tenant/principal as needed.
 *
 * IMPORTANT: This is a reference implementation for development/testing.
 * Production deployments should use persistent storage (Redis, SQL, etc.)
 *
 * Thread Safety: This implementation uses simple Kotlin collections which are
 * not fully thread-safe. For production use with high concurrency, consider:
 * - Platform-specific concurrent collections
 * - Explicit locking mechanisms
 * - Distributed storage (Redis, Hazelcast, etc.)
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("InMemoryOAuth2BackingStorageImpl", exact = true)
class InMemoryOAuth2BackingStorageImpl : InMemoryOAuth2BackingStorage {
    private val partitions = mutableMapOf<OAuth2StoragePartitionKey, OAuth2StoragePartition>()

    override fun getPartition(partitionKey: OAuth2StoragePartitionKey): OAuth2StoragePartition = partitions.getOrPut(partitionKey) { OAuth2StoragePartition() }

    override fun removePartition(partitionKey: OAuth2StoragePartitionKey): Boolean = partitions.remove(partitionKey) != null

    override fun clearAll() {
        partitions.clear()
    }

    override fun getPartitionCount(): Int = partitions.size

    override fun getPartitionKeys(): Set<OAuth2StoragePartitionKey> = partitions.keys.toSet()
}

/**
 * Interface for OAuth2 backing storage
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("InMemoryOAuth2BackingStorage", exact = true)
interface InMemoryOAuth2BackingStorage {
    /**
     * Gets or creates a storage partition for the given key
     */
    fun getPartition(partitionKey: OAuth2StoragePartitionKey): OAuth2StoragePartition

    /**
     * Removes a storage partition, clearing all associated data
     */
    fun removePartition(partitionKey: OAuth2StoragePartitionKey): Boolean

    /**
     * Clears all storage partitions
     * CAUTION: Use only for testing or shutdown
     */
    fun clearAll()

    /**
     * Returns the number of active storage partitions
     */
    fun getPartitionCount(): Int

    /**
     * Returns all partition keys (for diagnostics/monitoring)
     */
    fun getPartitionKeys(): Set<OAuth2StoragePartitionKey>
}
