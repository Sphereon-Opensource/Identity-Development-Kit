/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sphereon.identity.reconciliation.impl.store
import com.sphereon.identity.reconciliation.model.OidcClientConfig
import com.sphereon.identity.reconciliation.store.OidcClientConfigStore
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<OidcClientConfigStore>())
class InMemoryOidcClientConfigStore : OidcClientConfigStore {
    private val configs = mutableMapOf<String, OidcClientConfig>()
    override suspend fun findById(clientId: String): OidcClientConfig? = configs[clientId]
    override suspend fun findAll(): List<OidcClientConfig> = configs.values.toList()
    override suspend fun save(config: OidcClientConfig): OidcClientConfig {
        configs[config.id] = config
        return config
    }
    override suspend fun delete(clientId: String): Boolean = configs.remove(clientId) != null
}
