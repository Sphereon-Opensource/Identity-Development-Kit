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

package com.sphereon.did.persistence.memory

import com.sphereon.did.persistence.DidPersistenceConfig
import com.sphereon.did.persistence.DidRepositoryFactory
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.StringKey

/**
 * Contributes the in-memory [DidRepositoryFactory] into the persistence multimap keyed by
 * [DidPersistenceConfig.TYPE_MEMORY]. The selector in `lib-did-manager-impl` reads the
 * configured persistence type and looks up the matching factory at app-graph init.
 */
@ContributesTo(AppScope::class)
interface MemoryDidPersistenceModule {
    @Provides
    @IntoMap
    @StringKey(DidPersistenceConfig.TYPE_MEMORY)
    @SingleIn(AppScope::class)
    fun provideMemoryDidRepositoryFactory(): DidRepositoryFactory = MemoryDidRepositoryFactory()
}
