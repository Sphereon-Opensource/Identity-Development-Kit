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
import com.sphereon.oauth2.server.authorization.storage.ClientRegistrationStore
import com.sphereon.oauth2.server.authorization.storage.ClientRegistrationStoreError
import com.sphereon.oauth2.server.authorization.storage.ClientRegistrationStatus
import com.sphereon.oauth2.server.authorization.storage.StoredClientRegistration
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.time.Clock

/**
 * In-memory implementation of [ClientRegistrationStore]. Default binding for IDK consumers and
 * tests that do not have a Postgres-backed store on the classpath; production deployments
 * activate the EDK Postgres impl through its `replaces` binding with no other change.
 *
 * Concurrency model mirrors [InMemorySigningKeyStore]: per-tenant maps guarded by a single
 * synchronized monitor. Registration volume is operator-driven and tiny (screens, service
 * clients), so contention is negligible.
 *
 * Persistence: NONE by itself - registrations are lost on JVM restart exactly like the volatile
 * partition this store replaces. Deployments that need enrollment to survive deploys include
 * the EDK store-postgres module. Keeping the default non-durable preserves today's behaviour
 * for every existing consumer while making durability a classpath decision.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<ClientRegistrationStore>())
class InMemoryClientRegistrationStore(
    private val clock: Clock = Clock.System,
) : ClientRegistrationStore {
    /**
     * `tenantId → (authorizationServerId, clientId) → registration`. A client id is unique only
     * within one authorization server, so the inner key carries both. Insertion order is kept for
     * stable lists.
     */
    private val clientsByTenant: MutableMap<String, MutableMap<Pair<String, String>, StoredClientRegistration>> = mutableMapOf()

    override suspend fun save(
        tenantId: String,
        registration: StoredClientRegistration,
    ): IdkResult<Unit, ClientRegistrationStoreError> {
        val key = registration.authorizationServerId to registration.clientId
        val existing = findByClientId(tenantId, registration.authorizationServerId, registration.clientId)
        if (existing.isErr) return Err(existing.error)
        if (existing.value?.status == ClientRegistrationStatus.REVOKED && registration.status == ClientRegistrationStatus.ACTIVE) {
            return Err(
                ClientRegistrationStoreError.StorageFailure(
                    operation = "save",
                    details = "Client '${registration.clientId}' was revoked; revocation is one-way",
                ),
            )
        }
        val tenantClients = clientsByTenant.getOrPut(tenantId) { mutableMapOf() }
        val previous = tenantClients[key]
        if (previous != null && previous.status == ClientRegistrationStatus.ACTIVE && registration.status == ClientRegistrationStatus.ACTIVE) {
            // Update-in-place: same identity, refresh timestamps.
            tenantClients[key] = registration.copy(updatedAt = clock.now())
            return Ok(Unit)
        }
        if (previous == null && registration.status == ClientRegistrationStatus.REVOKED) {
            return Err(ClientRegistrationStoreError.ClientNotFound(tenantId = tenantId, clientId = registration.clientId))
        }
        tenantClients[key] = registration
        return Ok(Unit)
    }

    override suspend fun findByClientId(
        tenantId: String,
        authorizationServerId: String,
        clientId: String,
    ): IdkResult<StoredClientRegistration?, ClientRegistrationStoreError> =
        runCatching { Ok(clientsByTenant[tenantId]?.get(authorizationServerId to clientId)) }
            .getOrElse { e ->
                Err(ClientRegistrationStoreError.StorageFailure(operation = "findByClientId", details = e.message ?: "lookup failed"))
            }

    override suspend fun revoke(
        tenantId: String,
        authorizationServerId: String,
        clientId: String,
    ): IdkResult<Boolean, ClientRegistrationStoreError> {
        val current = findByClientId(tenantId, authorizationServerId, clientId)
        if (current.isErr) return Err(current.error)
        val existing = current.value ?: return Ok(false)
        if (existing.status == ClientRegistrationStatus.REVOKED) return Ok(false)
        clientsByTenant[tenantId]?.set(
            authorizationServerId to clientId,
            existing.copy(status = ClientRegistrationStatus.REVOKED, updatedAt = clock.now()),
        )
        return Ok(true)
    }

    override suspend fun delete(
        tenantId: String,
        authorizationServerId: String,
        clientId: String,
    ): IdkResult<StoredClientRegistration?, ClientRegistrationStoreError> =
        runCatching { Ok(clientsByTenant[tenantId]?.remove(authorizationServerId to clientId)) }
            .getOrElse { e ->
                Err(ClientRegistrationStoreError.StorageFailure(operation = "delete", details = e.message ?: "delete failed"))
            }

    override suspend fun list(
        tenantId: String,
        authorizationServerId: String,
        limit: Int,
        offset: Int,
    ): IdkResult<List<StoredClientRegistration>, ClientRegistrationStoreError> =
        runCatching {
            require(limit > 0) { "limit must be positive" }
            require(offset >= 0) { "offset must not be negative" }
            Ok(
                clientsByTenant[tenantId]
                    ?.entries
                    ?.filter { it.key.first == authorizationServerId }
                    ?.map { it.value }
                    ?.sortedByDescending { it.registeredAt }
                    ?.drop(offset)
                    ?.take(limit)
                    .orEmpty(),
            )
        }.getOrElse { e ->
            Err(ClientRegistrationStoreError.StorageFailure(operation = "list", details = e.message ?: "invalid pagination"))
        }
}
