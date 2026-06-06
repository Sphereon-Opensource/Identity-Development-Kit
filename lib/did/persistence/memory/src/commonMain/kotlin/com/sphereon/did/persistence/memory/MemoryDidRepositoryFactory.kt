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
 *
 */

package com.sphereon.did.persistence.memory

import app.cash.sqldelight.db.SqlDriver
import com.sphereon.did.persistence.DidPersistenceConfig
import com.sphereon.did.persistence.DidRepository
import com.sphereon.did.persistence.DidRepositoryFactory

/**
 * Test/dev-grade [DidRepositoryFactory] backed by [MemoryDidRepositoryImpl].
 *
 * The factory itself carries no `@Inject` — DI wiring lives in
 * [MemoryDidPersistenceModule], which contributes this factory into the persistence
 * multimap keyed by [DidPersistenceConfig.TYPE_MEMORY]. Tests can also instantiate the
 * factory directly via the no-arg constructor without involving Metro.
 *
 * Both [createRepository] entry points return the same in-memory implementation —
 * memory ignores both [DidPersistenceConfig] and any supplied [SqlDriver].
 */
class MemoryDidRepositoryFactory : DidRepositoryFactory {
    override val type: String = DidPersistenceConfig.TYPE_MEMORY

    override fun createRepository(config: DidPersistenceConfig): DidRepository = MemoryDidRepositoryImpl()

    override fun createRepository(driver: SqlDriver?): DidRepository = MemoryDidRepositoryImpl()
}
