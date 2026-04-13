/*
 * © 2025 Sphereon International B.V.
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

package com.sphereon.identity.reconciliation.impl.store

import com.sphereon.identity.reconciliation.model.ReconciliationSession
import com.sphereon.identity.reconciliation.store.ReconciliationSessionStore
import kotlinx.datetime.Instant
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<ReconciliationSessionStore>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("InMemoryReconciliationSessionStoreImpl", exact = true)
class InMemoryReconciliationSessionStore : ReconciliationSessionStore {

    private val byId = mutableMapOf<String, ReconciliationSession>()
    private val byState = mutableMapOf<String, String>()

    private fun compositeId(tenantId: String, sessionId: String) = "$tenantId:$sessionId"

    override suspend fun findById(tenantId: String, sessionId: String): ReconciliationSession? {
        return byId[compositeId(tenantId, sessionId)]
    }

    override suspend fun findByState(tenantId: String, state: String): ReconciliationSession? {
        println("[ReconciliationStore] findByState: tenantId=$tenantId, state=$state")
        println("[ReconciliationStore] byState keys: ${byState.keys}")
        println("[ReconciliationStore] byState contains state? ${byState.containsKey(state)}")
        val sessionKey = byState[state] ?: run {
            println("[ReconciliationStore] No sessionKey found for state=$state")
            return null
        }
        val session = byId[sessionKey] ?: run {
            println("[ReconciliationStore] No session found for key=$sessionKey")
            return null
        }
        println("[ReconciliationStore] Found session: id=${session.id}, tenantId=${session.tenantId}")
        return if (session.tenantId == tenantId) session else null
    }

    override suspend fun create(session: ReconciliationSession): ReconciliationSession {
        val cid = compositeId(session.tenantId, session.id)
        byId[cid] = session
        session.state?.let { byState[it] = cid }
        println("[ReconciliationStore] Created session: id=${session.id}, tenantId=${session.tenantId}, state=${session.state}, cid=$cid")
        println("[ReconciliationStore] Store now has ${byId.size} sessions, ${byState.size} state mappings")
        println("[ReconciliationStore] Store instance: ${this.hashCode()}")
        return session
    }

    override suspend fun update(session: ReconciliationSession): ReconciliationSession {
        val cid = compositeId(session.tenantId, session.id)
        // Remove old state mapping if changed
        val old = byId[cid]
        if (old?.state != null && old.state != session.state) {
            byState.remove(old.state)
        }
        byId[cid] = session
        session.state?.let { byState[it] = cid }
        return session
    }

    override suspend fun delete(tenantId: String, sessionId: String): Boolean {
        val cid = compositeId(tenantId, sessionId)
        val session = byId.remove(cid) ?: return false
        session.state?.let { byState.remove(it) }
        return true
    }

    override suspend fun findExpired(tenantId: String, cutoff: Instant): List<ReconciliationSession> {
        return byId.values.filter { it.tenantId == tenantId && it.expiresAt < cutoff }
    }
}
