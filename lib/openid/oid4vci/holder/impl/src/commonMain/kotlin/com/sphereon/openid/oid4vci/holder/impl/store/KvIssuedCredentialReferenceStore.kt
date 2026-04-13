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
import com.sphereon.openid.oid4vci.holder.IssuedCredentialReference
import com.sphereon.openid.oid4vci.holder.IssuedCredentialReferenceStore
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.days

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<IssuedCredentialReferenceStore>())
class KvIssuedCredentialReferenceStore(
    private val kvStoreManager: KvStoreManager,
    private val execution: SessionExecution,
) : IssuedCredentialReferenceStore {
    private val namespace =
        KvNamespace(
            name = "oid4vci-holder-credential-refs",
            codec = KotlinxSerializationJsonKvCodec(json = Json, serializer = IssuedCredentialReference.serializer()),
        )

    /**
     * Index namespace: key is the issuer URL, value is the list of credential IDs for that issuer.
     */
    private val issuerIndexNamespace =
        KvNamespace(
            name = "oid4vci-holder-credential-refs.by-issuer",
            codec = KotlinxSerializationJsonKvCodec(json = Json, serializer = ListSerializer(String.serializer())),
        )

    private val storeConfig: KvStoreConfigBase =
        InMemoryKvStoreConfig(
            id = "oid4vci-holder-credential-refs",
            scopeBinding = KvStoreScopeBinding.APP,
        )

    private val kv: KvStore by lazy {
        kvStoreManager.createFromKvStoreConfig(storeConfig, execution)
    }

    override suspend fun store(ref: IssuedCredentialReference): IdkResult<Unit, IdkError> {
        kv.put(namespace, ref.credentialId, ref, ttl = 365.days).getOrElse { return Err(it) }
        val currentIds = kv.get(issuerIndexNamespace, ref.issuerUrl).getOrElse { return Err(it) } ?: emptyList()
        if (ref.credentialId !in currentIds) {
            val updated = currentIds + ref.credentialId
            kv.put(issuerIndexNamespace, ref.issuerUrl, updated, ttl = 365.days).getOrElse { return Err(it) }
        }
        return Ok(Unit)
    }

    override suspend fun getByIssuer(issuerUrl: String): IdkResult<List<IssuedCredentialReference>, IdkError> {
        val ids = kv.get(issuerIndexNamespace, issuerUrl).getOrElse { return Err(it) } ?: return Ok(emptyList())
        val refs = mutableListOf<IssuedCredentialReference>()
        for (id in ids) {
            val ref = kv.get(namespace, id).getOrElse { return Err(it) } ?: continue
            refs.add(ref)
        }
        return Ok(refs)
    }
}
