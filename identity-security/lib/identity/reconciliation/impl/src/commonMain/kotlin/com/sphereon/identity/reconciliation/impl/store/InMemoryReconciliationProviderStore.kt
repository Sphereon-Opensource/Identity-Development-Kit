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

package com.sphereon.identity.reconciliation.impl.store

import com.sphereon.identity.reconciliation.model.ReconciliationProvider
import com.sphereon.identity.reconciliation.store.ReconciliationProviderStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<ReconciliationProviderStore>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("InMemoryReconciliationProviderStoreImpl", exact = true)
class InMemoryReconciliationProviderStore : ReconciliationProviderStore {
    private val providers = mutableMapOf<String, ReconciliationProvider>()

    override suspend fun findById(providerId: String): ReconciliationProvider? = providers[providerId]

    override suspend fun findAll(): List<ReconciliationProvider> = providers.values.toList()

    override suspend fun save(provider: ReconciliationProvider): ReconciliationProvider {
        providers[provider.id] = provider
        return provider
    }

    override suspend fun delete(providerId: String): Boolean = providers.remove(providerId) != null
}
