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

package com.sphereon.oauth2.server.authorization.impl.store

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.data.store.kv.InMemoryKvStoreConfig
import com.sphereon.data.store.kv.KotlinxSerializationJsonKvCodec
import com.sphereon.data.store.kv.KvNamespace
import com.sphereon.data.store.kv.KvStore
import com.sphereon.data.store.kv.KvStoreConfigBase
import com.sphereon.data.store.kv.KvStoreScopeBinding
import com.sphereon.data.store.kv.impl.KvStoreManager
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.server.authorization.model.IaeSession
import com.sphereon.oauth2.server.authorization.storage.IaeSessionStore
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.time.Duration.Companion.seconds

/**
 * KV-backed implementation of [IaeSessionStore].
 *
 * Uses two KV namespaces:
 * - `"iae-sessions"` keyed by [IaeSession.sessionId] → stores full [IaeSession]
 * - `"iae-auth-session-index"` keyed by [IaeSession.authSession] → stores sessionId (String)
 *
 * The index enables O(1) lookup by the rotating auth_session token without a full scan.
 *
 * TTL: 600 seconds (10 minutes). IAE sessions are short-lived per OID4VCI 1.1 Section 6.
 *
 * Suitable for single-server and test deployments. Production systems with horizontal scaling
 * should substitute a Redis or distributed KV backend by providing a different [KvStoreConfigBase].
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<IaeSessionStore>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("KvIaeSessionStore", exact = true)
class KvIaeSessionStore(
    private val kvStoreManager: KvStoreManager,
    private val execution: SessionExecution,
) : IaeSessionStore {
    private val sessionNamespace =
        KvNamespace(
            name = "iae-sessions",
            codec = KotlinxSerializationJsonKvCodec(json = Json, serializer = IaeSession.serializer()),
        )

    private val indexNamespace =
        KvNamespace(
            name = "iae-auth-session-index",
            codec = KotlinxSerializationJsonKvCodec(json = Json, serializer = String.serializer()),
        )

    private val storeConfig: KvStoreConfigBase =
        InMemoryKvStoreConfig(
            id = "iae-sessions",
            scopeBinding = KvStoreScopeBinding.APP,
        )

    private val kv: KvStore by lazy {
        kvStoreManager.createFromKvStoreConfig(storeConfig, execution)
    }

    override suspend fun create(session: IaeSession): IdkResult<IaeSession, IdkError> {
        val putResult = kv.put(sessionNamespace, session.sessionId, session, ttl = SESSION_TTL.seconds)
        if (putResult.isErr) {
            return Err(putResult.error)
        }

        val indexResult = kv.put(indexNamespace, session.authSession, session.sessionId, ttl = SESSION_TTL.seconds)
        if (indexResult.isErr) {
            return Err(indexResult.error)
        }

        return Ok(session)
    }

    override suspend fun getByAuthSession(authSession: String): IdkResult<IaeSession?, IdkError> {
        val sessionIdResult = kv.get(indexNamespace, authSession)
        if (sessionIdResult.isErr) {
            return Err(sessionIdResult.error)
        }

        val sessionId = sessionIdResult.value ?: return Ok(null)
        return kv.get(sessionNamespace, sessionId)
    }

    override suspend fun getBySessionId(sessionId: String): IdkResult<IaeSession?, IdkError> = kv.get(sessionNamespace, sessionId)

    override suspend fun update(session: IaeSession): IdkResult<IaeSession, IdkError> {
        // Retrieve current session to find old authSession for index cleanup
        val currentResult = kv.get(sessionNamespace, session.sessionId)
        if (currentResult.isErr) {
            return Err(currentResult.error)
        }

        val current = currentResult.value
        if (current != null && current.authSession != session.authSession) {
            // Remove stale auth_session index entry
            val deleteResult = kv.delete(indexNamespace, current.authSession)
            if (deleteResult.isErr) {
                return Err(deleteResult.error)
            }

            // Write new index entry
            val indexResult = kv.put(indexNamespace, session.authSession, session.sessionId, ttl = SESSION_TTL.seconds)
            if (indexResult.isErr) {
                return Err(indexResult.error)
            }
        }

        val putResult = kv.put(sessionNamespace, session.sessionId, session, ttl = SESSION_TTL.seconds)
        if (putResult.isErr) {
            return Err(putResult.error)
        }

        return Ok(session)
    }

    override suspend fun delete(sessionId: String): IdkResult<Boolean, IdkError> {
        val currentResult = kv.get(sessionNamespace, sessionId)
        if (currentResult.isErr) {
            return Err(currentResult.error)
        }

        val current = currentResult.value
        if (current != null) {
            // Remove auth_session index entry
            val indexResult = kv.delete(indexNamespace, current.authSession)
            if (indexResult.isErr) {
                return Err(indexResult.error)
            }
        }

        return kv.delete(sessionNamespace, sessionId)
    }

    companion object {
        internal const val SESSION_TTL = 600L // 10 minutes
    }
}
