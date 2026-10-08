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

package com.sphereon.identity.reconciliation.impl.store.kottage

import com.sphereon.data.store.kv.KotlinxSerializationJsonKvCodec
import com.sphereon.data.store.kv.KvNamespace
import com.sphereon.data.store.kv.KvStore
import com.sphereon.identity.reconciliation.model.ReconciliationSession
import com.sphereon.identity.reconciliation.store.ReconciliationSessionStore
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * KvStore-backed implementation of [ReconciliationSessionStore] providing persistent
 * storage for OIDC reconciliation sessions.
 *
 * Uses three KV namespaces:
 * - Primary: `recon-session` namespace -> ReconciliationSession
 * - State index: `recon-session-state-idx` namespace -> composite `{tenantId}:{sessionId}`
 * - Tenant index: `recon-session-tenant-idx` namespace -> list of sessionIds
 *
 * @param store The KvStore instance
 */
class KottageReconciliationSessionStore(
    private val store: KvStore,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : ReconciliationSessionStore {
    private val sessionNs =
        KvNamespace(
            name = "recon-session",
            codec = KotlinxSerializationJsonKvCodec(json, ReconciliationSession.serializer()),
        )
    private val stateIndexNs =
        KvNamespace(
            name = "recon-session-state-idx",
            codec = KotlinxSerializationJsonKvCodec(json, String.serializer()),
        )
    private val tenantIndexNs =
        KvNamespace(
            name = "recon-session-tenant-idx",
            codec = KotlinxSerializationJsonKvCodec(json, ListSerializer(String.serializer())),
        )

    private fun sessionKey(
        tenantId: String,
        sessionId: String,
    ) = "$tenantId:$sessionId"

    private fun tenantIndexKey(tenantId: String) = tenantId

    override suspend fun findById(
        tenantId: String,
        sessionId: String,
    ): ReconciliationSession? = store.get(sessionNs, sessionKey(tenantId, sessionId)).getOrNull()

    override suspend fun findByState(
        tenantId: String,
        state: String,
    ): ReconciliationSession? {
        val compositeId = store.get(stateIndexNs, state).getOrNull() ?: return null
        val parts = compositeId.split(":", limit = 2)
        if (parts.size != 2) {
            return null
        }
        val storedTenantId = parts[0]
        val sessionId = parts[1]
        if (storedTenantId != tenantId) {
            return null
        }
        return findById(tenantId, sessionId)
    }

    override suspend fun create(session: ReconciliationSession): ReconciliationSession {
        store.put(sessionNs, sessionKey(session.tenantId, session.id), session, Duration.INFINITE)
        session.state?.let { store.put(stateIndexNs, it, "${session.tenantId}:${session.id}", Duration.INFINITE) }

        // Update tenant index for expiry scanning
        addToTenantIndex(session.tenantId, session.id)

        return session
    }

    override suspend fun update(session: ReconciliationSession): ReconciliationSession {
        // Clean up old state index if changed
        val existing = findById(session.tenantId, session.id)
        val existingState = existing?.state
        if (existingState != null && existingState != session.state) {
            store.delete(stateIndexNs, existingState)
        }

        store.put(sessionNs, sessionKey(session.tenantId, session.id), session, Duration.INFINITE)
        session.state?.let { store.put(stateIndexNs, it, "${session.tenantId}:${session.id}", Duration.INFINITE) }

        return session
    }

    override suspend fun delete(
        tenantId: String,
        sessionId: String,
    ): Boolean {
        val session = findById(tenantId, sessionId) ?: return false
        session.state?.let { store.delete(stateIndexNs, it) }
        store.delete(sessionNs, sessionKey(tenantId, sessionId))

        // Remove from tenant index
        removeFromTenantIndex(tenantId, sessionId)

        return true
    }

    override suspend fun findExpired(
        tenantId: String,
        cutoff: Instant,
    ): List<ReconciliationSession> {
        val sessionIds = getTenantIndex(tenantId)
        return sessionIds
            .mapNotNull { sessionId ->
                findById(tenantId, sessionId)
            }.filter { session ->
                session.expiresAt < cutoff
            }
    }

    private suspend fun addToTenantIndex(
        tenantId: String,
        sessionId: String,
    ) {
        val ids = getTenantIndex(tenantId).toMutableList()
        if (sessionId !in ids) {
            ids.add(sessionId)
        }
        store.put(tenantIndexNs, tenantIndexKey(tenantId), ids, Duration.INFINITE)
    }

    private suspend fun removeFromTenantIndex(
        tenantId: String,
        sessionId: String,
    ) {
        val ids = getTenantIndex(tenantId).toMutableList()
        ids.remove(sessionId)
        if (ids.isEmpty()) {
            store.delete(tenantIndexNs, tenantIndexKey(tenantId))
        } else {
            store.put(tenantIndexNs, tenantIndexKey(tenantId), ids, Duration.INFINITE)
        }
    }

    private suspend fun getTenantIndex(tenantId: String): List<String> = store.get(tenantIndexNs, tenantIndexKey(tenantId)).getOrNull() ?: emptyList()
}
