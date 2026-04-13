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

package com.sphereon.data.store.kv.impl

import com.sphereon.core.api.context.SessionExecution
import com.sphereon.data.store.kv.InMemoryKvStoreConfig
import com.sphereon.data.store.kv.KotlinxSerializationJsonKvCodec
import com.sphereon.data.store.kv.KvNamespace
import com.sphereon.data.store.kv.KvStore
import com.sphereon.data.store.kv.KvStoreScopeBinding
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * Composition helper that encapsulates the common KV store setup pattern.
 *
 * Use by composition instead of inheritance:
 *
 * ```kotlin
 * class MyStore(kvStoreManager: KvStoreManager, execution: SessionExecution) {
 *     private val delegate = KvStoreDelegate(kvStoreManager, execution, "my-namespace", MyModel.serializer())
 *     private val kv get() = delegate.kv
 *     private val namespace get() = delegate.namespace
 * }
 * ```
 *
 * @param kvStoreManager the application-scoped store manager
 * @param execution the session execution context used to partition the store
 * @param namespaceName unique name for the namespace (doubles as the store id)
 * @param serializer the kotlinx.serialization serializer for values stored in this namespace
 * @param json the [Json] instance used for encoding/decoding; defaults to [Json.Default]
 * @param scopeBinding data partitioning level; defaults to [KvStoreScopeBinding.APP]
 */
class KvStoreDelegate<T : Any>(
    kvStoreManager: KvStoreManager,
    execution: SessionExecution,
    namespaceName: String,
    serializer: KSerializer<T>,
    json: Json = Json,
    scopeBinding: KvStoreScopeBinding = KvStoreScopeBinding.APP,
) {
    val namespace: KvNamespace<T> =
        KvNamespace(
            name = namespaceName,
            codec = KotlinxSerializationJsonKvCodec(json = json, serializer = serializer),
        )

    val kv: KvStore by lazy {
        kvStoreManager.createFromKvStoreConfig(
            InMemoryKvStoreConfig(id = namespaceName, scopeBinding = scopeBinding),
            execution,
        )
    }
}
