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
import com.sphereon.openid.oid4vci.common.model.CredentialOffer
import com.sphereon.openid.oid4vci.issuer.store.CredentialOfferStore
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<CredentialOfferStore>())
class KvCredentialOfferStore(
    private val kvStoreManager: KvStoreManager,
    private val kvStoreService: KvStoreService,
    private val execution: SessionExecution,
) : CredentialOfferStore {
    private val namespace =
        KvNamespace(
            name = "oid4vci.offers",
            codec = KotlinxSerializationJsonKvCodec(json = Json, serializer = CredentialOffer.serializer()),
        )

    private val sessionIdNamespace =
        KvNamespace(
            name = "oid4vci.offers.session-mapping",
            codec = KotlinxSerializationJsonKvCodec(json = Json, serializer = kotlinx.serialization.serializer<String>()),
        )

    private val storeConfig: KvStoreConfigBase =
        InMemoryKvStoreConfig(
            id = "oid4vci.offers",
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

    override suspend fun store(
        offerId: String,
        offer: CredentialOffer,
        ttlSeconds: Long,
        sessionId: String?,
    ): IdkResult<Unit, IdkError> {
        val result = kv.put(namespace, offerId, offer, ttl = ttlSeconds.seconds).map { }
        if (sessionId != null) {
            kv.put(sessionIdNamespace, offerId, sessionId, ttl = ttlSeconds.seconds)
        }
        return result
    }

    override suspend fun get(offerId: String): IdkResult<CredentialOffer?, IdkError> = kv.get(namespace, offerId)

    override suspend fun getSessionId(offerId: String): IdkResult<String?, IdkError> = kv.get(sessionIdNamespace, offerId).map { it as? String }

    override suspend fun delete(offerId: String): IdkResult<Boolean, IdkError> {
        kv.delete(sessionIdNamespace, offerId)
        return kv.delete(namespace, offerId)
    }
}
