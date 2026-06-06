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

import app.cash.sqldelight.driver.jdbc.asJdbcDriver
import com.sphereon.did.persistence.DidRepository
import com.sphereon.did.persistence.testfixtures.DidRepositoryContract
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import kotlin.uuid.Uuid

/**
 * Validates [SqliteDidRepositoryImpl] against the cross-dialect [DidRepositoryContract].
 * The SQLite JDBC driver is backed by an in-memory file database with a shared cache so
 * multiple connections in the Hikari pool see the same rows.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SqliteDidRepositoryImplTest : DidRepositoryContract() {
    private lateinit var dataSource: HikariDataSource
    private lateinit var repo: SqliteDidRepositoryImpl

    @BeforeAll
    fun setup() {
        val config =
            HikariConfig().apply {
                jdbcUrl = "jdbc:sqlite:file:did_test_${Uuid.random()}?mode=memory&cache=shared"
                maximumPoolSize = 1
            }
        dataSource = HikariDataSource(config)
        val driver = dataSource.asJdbcDriver()
        driver.execute(null, "PRAGMA foreign_keys = ON", 0)
        repo = SqliteDidRepositoryFactory().createRepositoryFromDriver(driver)
    }

    @AfterAll
    fun teardown() {
        dataSource.close()
    }

    override fun repository(): DidRepository = repo

    override fun clearRows() {
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                listOf(
                    "did_key_mapping",
                    "did_service",
                    "did_verification_relationship",
                    "did_verification_method",
                    "did_document_context",
                    "did_equivalent_id",
                    "did_also_known_as",
                    "did_controller",
                    "did_record",
                ).forEach { table -> statement.executeUpdate("DELETE FROM $table") }
            }
        }
    }
}
