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

package com.sphereon.crypto.key.persistence.sqlite

import app.cash.sqldelight.driver.jdbc.asJdbcDriver
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/**
 * Default JVM wiring for the SQLite-backed [KeyReferenceDatabaseSqlite]. Without this provider
 * the [SqliteKeyReferenceStoreImpl] auto-binding is unreachable: the store takes a
 * [KeyReferenceDatabaseSqlite] in its constructor and the Metro graph has no other source
 * for one. With this module on the classpath, dropping `lib-crypto-key-persistence-sqlite`
 * into a service is sufficient to get a working in-process keyref store.
 *
 * The driver is a single-connection [HikariDataSource] pointed at `jdbc:sqlite::memory:` —
 * matching the [SqliteDidRepositoryFactory] default. Schema creation runs once at graph init
 * (`CREATE TABLE IF NOT EXISTS` is idempotent), and `PRAGMA foreign_keys = ON` is applied
 * because SQLite ships with FK enforcement off by default. Pool size is pinned to 1 because
 * SQLite serializes writes and a private in-memory database is not visible across
 * connections.
 *
 * Replacing this default for production deployments (file-backed SQLite, shared-cache
 * fixtures, custom Hikari pools) is done by contributing a competing `@Provides` for
 * [KeyReferenceDatabaseSqlite] from a downstream module — there is no `replaces` target on
 * this binding, so the override path is "shadow with a more specific @ContributesTo module
 * and remove this one from the classpath" rather than `replaces=[…]`.
 */
@ContributesTo(AppScope::class)
interface SqliteKeyReferenceDatabaseGraph {
    @Provides
    @SingleIn(AppScope::class)
    fun provideKeyReferenceDatabaseSqlite(): KeyReferenceDatabaseSqlite {
        val dataSource = HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = "jdbc:sqlite::memory:"
                maximumPoolSize = 1
            },
        )
        val driver = dataSource.asJdbcDriver()
        driver.execute(null, "PRAGMA foreign_keys = ON", 0)
        KeyReferenceDatabaseSqlite.Schema.create(driver)
        return KeyReferenceDatabaseSqlite(driver)
    }
}
