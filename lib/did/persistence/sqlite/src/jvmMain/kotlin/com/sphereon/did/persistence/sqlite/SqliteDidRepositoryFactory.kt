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

package com.sphereon.did.persistence.sqlite

import app.cash.sqldelight.ColumnAdapter
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.asJdbcDriver
import com.sphereon.did.persistence.DidPersistenceConfig
import com.sphereon.did.persistence.DidRepository
import com.sphereon.did.persistence.DidRepositoryFactory
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlin.time.Instant

/**
 * SQLite [DidRepositoryFactory]. Builds a [DidDatabaseSqlite] over a [HikariDataSource]-backed
 * [SqlDriver], wires the timestamp ([InstantStringAdapter]) and ordinal ([IntColumnAdapter])
 * adapters that SQLDelight requires for every `AS Instant` / `AS Int` column, and returns a
 * [SqliteDidRepositoryImpl].
 *
 * Schema creation is idempotent (every table uses `CREATE TABLE IF NOT EXISTS`), so the
 * factory runs `Schema.create(driver)` directly — no migration tracking is needed during
 * the IDK genesis phase (D5).
 *
 * Connection target is read from [DidPersistenceConfig.connectionUrl]:
 * - `null` or blank → defaults to a private in-process SQLite (`jdbc:sqlite::memory:`).
 * - File path (`/var/lib/idk/dids.db`) → wrapped as `jdbc:sqlite:<path>`.
 * - JDBC URL starting with `jdbc:` → used verbatim.
 *
 * The factory is JVM-only because it depends on the Xerial SQLite JDBC driver and HikariCP,
 * neither of which has multiplatform equivalents. Non-JVM consumers should use the memory
 * dialect or wire a platform-specific driver themselves.
 */
class SqliteDidRepositoryFactory : DidRepositoryFactory {
    override val type: String = DidPersistenceConfig.TYPE_SQLITE

    override fun createRepository(config: DidPersistenceConfig): DidRepository {
        require(config.type == DidPersistenceConfig.TYPE_SQLITE) {
            "SqliteDidRepositoryFactory expected type=${DidPersistenceConfig.TYPE_SQLITE}, got '${config.type}'"
        }
        val dataSource = buildDataSource(config)
        return createRepositoryFromDriver(dataSource.asJdbcDriver(), dataSource::close)
    }

    /**
     * Driver-taking entry point. [driver] must point at a SQLite database. Use this when the
     * caller owns connection lifecycle (custom Hikari pools, shared-cache fixtures); the
     * config-taking [createRepository] is preferred for production wiring driven by config.
     */
    override fun createRepository(driver: SqlDriver?): DidRepository {
        val nonNull =
            driver ?: error(
                "SqliteDidRepositoryFactory requires a non-null SqlDriver when invoked via the driver-taking " +
                    "entry point; use createRepository(config) to provision a driver from connection config",
            )
        return createRepositoryFromDriver(nonNull)
    }

    /**
     * Lower-level entry point used by tests that want to control driver construction directly
     * (e.g. shared-cache in-memory databases, custom Hikari pools). Production code uses
     * [createRepository], which wires its own driver from config.
     */
    fun createRepositoryFromDriver(
        driver: SqlDriver,
        closeAction: (() -> Unit)? = null,
    ): SqliteDidRepositoryImpl {
        DidDatabaseSqlite.Schema.create(driver)
        val database =
            DidDatabaseSqlite(
                driver = driver,
                did_recordAdapter =
                    Did_record.Adapter(
                        created_atAdapter = InstantStringAdapter,
                        updated_atAdapter = InstantStringAdapter,
                        deleted_atAdapter = InstantStringAdapter,
                    ),
                did_controllerAdapter =
                    Did_controller.Adapter(
                        ordinalAdapter = IntColumnAdapter,
                        created_atAdapter = InstantStringAdapter,
                        updated_atAdapter = InstantStringAdapter,
                    ),
                did_also_known_asAdapter =
                    Did_also_known_as.Adapter(
                        ordinalAdapter = IntColumnAdapter,
                        created_atAdapter = InstantStringAdapter,
                        updated_atAdapter = InstantStringAdapter,
                    ),
                did_equivalent_idAdapter =
                    Did_equivalent_id.Adapter(
                        ordinalAdapter = IntColumnAdapter,
                        created_atAdapter = InstantStringAdapter,
                        updated_atAdapter = InstantStringAdapter,
                    ),
                did_document_contextAdapter =
                    Did_document_context.Adapter(
                        ordinalAdapter = IntColumnAdapter,
                        created_atAdapter = InstantStringAdapter,
                        updated_atAdapter = InstantStringAdapter,
                    ),
                did_verification_methodAdapter =
                    Did_verification_method.Adapter(
                        expires_atAdapter = InstantStringAdapter,
                        revoked_atAdapter = InstantStringAdapter,
                        ordinalAdapter = IntColumnAdapter,
                        created_atAdapter = InstantStringAdapter,
                        updated_atAdapter = InstantStringAdapter,
                    ),
                did_verification_relationshipAdapter =
                    Did_verification_relationship.Adapter(
                        ordinalAdapter = IntColumnAdapter,
                        created_atAdapter = InstantStringAdapter,
                        updated_atAdapter = InstantStringAdapter,
                    ),
                did_serviceAdapter =
                    Did_service.Adapter(
                        ordinalAdapter = IntColumnAdapter,
                        created_atAdapter = InstantStringAdapter,
                        updated_atAdapter = InstantStringAdapter,
                    ),
                did_key_mappingAdapter =
                    Did_key_mapping.Adapter(
                        created_atAdapter = InstantStringAdapter,
                        updated_atAdapter = InstantStringAdapter,
                    ),
            )
        return SqliteDidRepositoryImpl(database, closeAction)
    }

    private fun buildDataSource(config: DidPersistenceConfig): HikariDataSource {
        val raw = config.connectionUrl?.takeIf { it.isNotBlank() }
        val jdbcUrl =
            when {
                raw == null -> "jdbc:sqlite::memory:"
                raw.startsWith("jdbc:") -> raw
                else -> "jdbc:sqlite:$raw"
            }
        val hikari =
            HikariConfig().apply {
                this.jdbcUrl = jdbcUrl
                // SQLite serializes writes; one pooled connection is the default unless the user
                // configures shared-cache via the JDBC URL and overrides poolSize explicitly.
                maximumPoolSize = config.poolSize ?: 1
                connectionInitSql = "PRAGMA foreign_keys = ON"
                config.username?.let { this.username = it }
                config.password?.let { this.password = it }
            }
        return HikariDataSource(hikari)
    }
}

/**
 * Bridges SQLDelight `INTEGER AS Int` columns to the SQLite driver's native `Long` storage.
 * Defined per-dialect because each backend's `Long`/`Int` rules differ.
 */
private object IntColumnAdapter : ColumnAdapter<Int, Long> {
    override fun decode(databaseValue: Long): Int = databaseValue.toInt()

    override fun encode(value: Int): Long = value.toLong()
}

/**
 * Bridges SQLDelight `TEXT AS Instant` columns to ISO-8601 strings via [Instant.parse] /
 * [Instant.toString]. SQLite has no native timestamp type, so the canonical wire form is a
 * string column read/written as a [kotlin.time.Instant] in code.
 */
private object InstantStringAdapter : ColumnAdapter<Instant, String> {
    override fun decode(databaseValue: String): Instant = Instant.parse(databaseValue)

    override fun encode(value: Instant): String = value.toString()
}
