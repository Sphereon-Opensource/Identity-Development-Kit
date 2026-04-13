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
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vci.issuer.store.DeferredCredentialEntry
import com.sphereon.openid.oid4vci.issuer.store.DeferredCredentialStore
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.Json
import kotlin.math.max
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<DeferredCredentialStore>())
class KvDeferredCredentialStore(
    private val kvStoreManager: KvStoreManager,
    private val execution: SessionExecution,
) : DeferredCredentialStore {
    private val namespace =
        KvNamespace(
            name = "oid4vci.deferred",
            codec = KotlinxSerializationJsonKvCodec(json = Json, serializer = DeferredCredentialEntry.serializer()),
        )

    private val storeConfig: KvStoreConfigBase =
        InMemoryKvStoreConfig(
            id = "oid4vci.deferred",
            scopeBinding = KvStoreScopeBinding.APP,
        )

    private val kv: KvStore by lazy {
        kvStoreManager.createFromKvStoreConfig(storeConfig, execution)
    }

    override suspend fun create(entry: DeferredCredentialEntry): IdkResult<DeferredCredentialEntry, IdkError> {
        val ttl = (entry.expiresAt - entry.createdAt).seconds
        kv.put(namespace, entry.transactionId, entry, ttl = ttl).getOrElse { return Err(it) }
        return Ok(entry)
    }

    override suspend fun get(transactionId: String): IdkResult<DeferredCredentialEntry?, IdkError> = kv.get(namespace, transactionId)

    override suspend fun update(entry: DeferredCredentialEntry): IdkResult<DeferredCredentialEntry, IdkError> {
        val remainingSeconds = max(1, entry.expiresAt - Clock.System.now().epochSeconds)
        kv.put(namespace, entry.transactionId, entry, ttl = remainingSeconds.seconds).getOrElse { return Err(it) }
        return Ok(entry)
    }
}
