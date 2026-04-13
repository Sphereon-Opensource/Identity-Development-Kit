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
import com.sphereon.openid.oid4vci.issuer.store.CredentialNonceStore
import com.sphereon.openid.oid4vci.issuer.store.NonceEntry
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<CredentialNonceStore>())
class KvCredentialNonceStore(
    private val kvStoreManager: KvStoreManager,
    private val execution: SessionExecution,
) : CredentialNonceStore {
    private val namespace =
        KvNamespace(
            name = "oid4vci.nonces",
            codec = KotlinxSerializationJsonKvCodec(json = Json, serializer = NonceEntry.serializer()),
        )

    private val storeConfig: KvStoreConfigBase =
        InMemoryKvStoreConfig(
            id = "oid4vci.nonces",
            scopeBinding = KvStoreScopeBinding.APP,
        )

    private val kv: KvStore by lazy {
        kvStoreManager.createFromKvStoreConfig(storeConfig, execution)
    }

    override suspend fun create(
        nonce: String,
        ttlSeconds: Long,
    ): IdkResult<NonceEntry, IdkError> {
        val now = Clock.System.now()
        val entry =
            NonceEntry(
                nonce = nonce,
                createdAt = now.epochSeconds,
                expiresAt = now.epochSeconds + ttlSeconds,
            )
        kv.put(namespace, nonce, entry, ttl = ttlSeconds.seconds).getOrElse { return Err(it) }
        return Ok(entry)
    }

    /**
     * Consume a nonce by reading and then deleting it.
     *
     * Concurrency note: KvStore does not expose an atomic get-and-delete (GETDEL) operation.
     * Two concurrent HTTP requests presenting the same nonce could both read it before either
     * deletes. A future KvStore GETDEL primitive would close this window. For production
     * deployments with high concurrency, use a KvStore backend that supports atomic operations
     * (e.g., Redis GETDEL).
     */
    override suspend fun consume(nonce: String): IdkResult<NonceEntry?, IdkError> {
        val entry =
            kv.get<NonceEntry>(namespace, nonce).getOrElse { return Err(it) }
                ?: return Ok(null)
        kv.delete(namespace, nonce)
        return Ok(entry)
    }
}
