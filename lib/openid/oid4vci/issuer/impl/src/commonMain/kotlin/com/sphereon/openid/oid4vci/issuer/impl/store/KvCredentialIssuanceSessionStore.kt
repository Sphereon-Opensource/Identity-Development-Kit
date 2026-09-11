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

package com.sphereon.openid.oid4vci.issuer.impl.store

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
import com.sphereon.data.store.kv.impl.KvStoreService
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vci.issuer.store.CredentialIssuanceSessionStore
import com.sphereon.openid.oid4vci.issuer.store.IssuanceSession
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlin.math.max
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<CredentialIssuanceSessionStore>())
class KvCredentialIssuanceSessionStore(
    private val kvStoreManager: KvStoreManager,
    private val kvStoreService: KvStoreService,
    private val execution: SessionExecution,
) : CredentialIssuanceSessionStore {
    private val namespace =
        KvNamespace(
            name = "oid4vci.sessions",
            codec = KotlinxSerializationJsonKvCodec(json = Json, serializer = IssuanceSession.serializer()),
        )

    private val issuerStateNamespace =
        KvNamespace(
            name = "oid4vci.sessions.issuerstate",
            codec = KotlinxSerializationJsonKvCodec(json = Json, serializer = String.serializer()),
        )

    private val configIdNamespace =
        KvNamespace(
            name = "oid4vci.sessions.configid",
            codec = KotlinxSerializationJsonKvCodec(json = Json, serializer = String.serializer()),
        )

    private val storeConfig: KvStoreConfigBase =
        InMemoryKvStoreConfig(
            id = "oid4vci.sessions",
            scopeBinding = KvStoreScopeBinding.TENANT,
        )

    private val kv: KvStore by lazy {
        kvStoreManager.createFromKvStoreConfig(resolveEffectiveStoreConfig(), execution)
    }

    private fun resolveEffectiveStoreConfig(): KvStoreConfigBase {
        val configured = runCatching { kvStoreService.getStoreConfig(storeConfig.id) }.getOrNull()
        val effective = configured ?: storeConfig
        require(effective.scopeBinding == storeConfig.scopeBinding) {
            "KV store '${storeConfig.id}' must use scopeBinding=${storeConfig.scopeBinding}, but was ${effective.scopeBinding}"
        }
        return effective
    }

    override suspend fun create(session: IssuanceSession): IdkResult<IssuanceSession, IdkError> {
        val ttl = (session.expiresAt - session.createdAt).seconds
        kv.put(namespace, session.sessionId, session, ttl = ttl).getOrElse { return Err(it) }
        val issuerState = session.issuerState
        if (issuerState != null) {
            kv.put(issuerStateNamespace, issuerState, session.sessionId, ttl = ttl).getOrElse { return Err(it) }
        }
        for (configId in session.credentialConfigurationIds) {
            kv.put(configIdNamespace, configId, session.sessionId, ttl = ttl).getOrElse { return Err(it) }
        }
        return Ok(session)
    }

    override suspend fun get(sessionId: String): IdkResult<IssuanceSession?, IdkError> = kv.get(namespace, sessionId)

    override suspend fun findByCredentialConfigurationId(configId: String): IdkResult<IssuanceSession?, IdkError> {
        val sessionId =
            kv.get<String>(configIdNamespace, configId).getOrElse { return Err(it) }
                ?: return Ok(null)
        return get(sessionId)
    }

    override suspend fun getByIssuerState(state: String): IdkResult<IssuanceSession?, IdkError> {
        val sessionId =
            kv.get<String>(issuerStateNamespace, state).getOrElse { return Err(it) }
                ?: return Ok(null)
        return get(sessionId)
    }

    override suspend fun update(session: IssuanceSession): IdkResult<IssuanceSession, IdkError> {
        val remainingSeconds = max(1, session.expiresAt - Clock.System.now().epochSeconds)
        kv.put(namespace, session.sessionId, session, ttl = remainingSeconds.seconds).getOrElse { return Err(it) }
        return Ok(session)
    }
}
