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

package com.sphereon.did.persistence

import app.cash.sqldelight.db.SqlDriver

/**
 * Produces [DidRepository] instances for a specific storage back-end.
 *
 * Each dialect module contributes one factory into the persistence multimap keyed by [type]
 * (`@ContributesIntoMap(AppScope::class) @StringKey(...)`). The selector in
 * `lib-did-manager-impl` reads the configured `did.persistence.type` and looks up the matching
 * factory at app-graph init.
 *
 * Two entry points coexist so dialects can pick the one that fits their connection model:
 *
 * - [createRepository] (config-taking) — the selector's default entry point. Dialects that own
 *   their connection lifecycle (memory, IDK SQLite via HikariCP) override this to provision the
 *   driver from [DidPersistenceConfig.connectionUrl]/credentials.
 *
 * - [createRepository] (driver-taking) — for dialects whose JDBC connection comes from a higher
 *   layer (EDK PostgreSQL/MySQL via `SchemaManager` and connection routing). The default
 *   `(config)` impl delegates here with `driver = null`; a driver-taking factory overrides this
 *   one, sourcing the driver however its environment dictates.
 */
interface DidRepositoryFactory {
    /** The dialect this factory produces (`memory`, `sqlite`, `postgresql`, `mysql`, …). */
    val type: String

    /**
     * Builds a repository against the supplied [config]. Default impl delegates to the
     * driver-taking variant with `driver = null` — adequate for dialects that source their
     * driver elsewhere. Factories that own the connection lifecycle should override this.
     */
    fun createRepository(config: DidPersistenceConfig = DidPersistenceConfig(type = type)): DidRepository = createRepository(driver = null)

    /**
     * Builds a repository over an optional [driver]. Memory ignores [driver]; SQL-backed
     * dialects that rely on an externally-supplied connection (e.g. EDK PostgreSQL/MySQL via a
     * routing-layer `SchemaManager`) require a non-null [SqlDriver] and fail fast otherwise.
     */
    fun createRepository(driver: SqlDriver? = null): DidRepository
}
