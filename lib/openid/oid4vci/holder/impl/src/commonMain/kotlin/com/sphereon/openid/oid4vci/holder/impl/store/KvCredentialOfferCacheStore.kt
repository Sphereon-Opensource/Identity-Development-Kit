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

package com.sphereon.openid.oid4vci.holder.impl.store

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
import com.sphereon.openid.oid4vci.holder.CachedOffer
import com.sphereon.openid.oid4vci.holder.CredentialOfferCacheStore
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.hours

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<CredentialOfferCacheStore>())
class KvCredentialOfferCacheStore(
    private val kvStoreManager: KvStoreManager,
    private val execution: SessionExecution,
) : CredentialOfferCacheStore {
    private val namespace =
        KvNamespace(
            name = "oid4vci-holder-offer-cache",
            codec = KotlinxSerializationJsonKvCodec(json = Json, serializer = CachedOffer.serializer()),
        )

    private val storeConfig: KvStoreConfigBase =
        InMemoryKvStoreConfig(
            id = "oid4vci-holder-offer-cache",
            scopeBinding = KvStoreScopeBinding.APP,
        )

    private val kv: KvStore by lazy {
        kvStoreManager.createFromKvStoreConfig(storeConfig, execution)
    }

    override suspend fun cache(
        offerId: String,
        cached: CachedOffer,
    ): IdkResult<Unit, IdkError> {
        kv.put(namespace, offerId, cached, ttl = 24.hours).getOrElse { return Err(it) }
        return Ok(Unit)
    }

    override suspend fun get(offerId: String): IdkResult<CachedOffer?, IdkError> = kv.get(namespace, offerId)

    override suspend fun delete(offerId: String): IdkResult<Boolean, IdkError> = kv.delete(namespace, offerId)
}
