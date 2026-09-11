/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.catalog.persistence.sqlite

import app.cash.sqldelight.ColumnAdapter
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.asJdbcDriver
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource

/**
 * Builds a [CatalogDatabaseSqlite] over a JDBC [SqlDriver]. File-backed or custom pools
 * should call [create] with their own driver. Wallet JVM supplies a file-backed Metro binding.
 */
object SqliteCatalogDatabases {
    const val DEFAULT_JDBC_URL = "jdbc:sqlite::memory:"

    fun jdbcDriver(jdbcUrl: String = DEFAULT_JDBC_URL): SqlDriver {
        val dataSource =
            HikariDataSource(
                HikariConfig().apply {
                    this.jdbcUrl = jdbcUrl
                    maximumPoolSize = 1
                    connectionInitSql = "PRAGMA foreign_keys = ON"
                },
            )
        return dataSource.asJdbcDriver()
    }

    fun create(driver: SqlDriver): CatalogDatabaseSqlite {
        driver.execute(null, "PRAGMA foreign_keys = ON", 0)
        CatalogDatabaseSqlite.Schema.create(driver)
        migrateListingWindowColumns(driver)
        return CatalogDatabaseSqlite(
            driver = driver,
            attestation_catalogAdapter = Attestation_catalog.Adapter(versionAdapter = LongIdentityAdapter),
            attestation_schemaAdapter = Attestation_schema.Adapter(listed_fromAdapter = LongIdentityAdapter),
        )
    }

    fun createStore(driver: SqlDriver): SqliteCatalogStore = SqliteCatalogStore(create(driver))

    fun createStore(jdbcUrl: String = DEFAULT_JDBC_URL): SqliteCatalogStore = createStore(jdbcDriver(jdbcUrl))
}

internal fun migrateListingWindowColumns(driver: SqlDriver) {
    val columns = tableColumns(driver, "attestation_schema")
    if (columns.isEmpty() || "listed_from" in columns) return
    driver.execute(null, "ALTER TABLE attestation_schema ADD COLUMN listed_from INTEGER NOT NULL DEFAULT 0", 0)
    driver.execute(null, "ALTER TABLE attestation_schema ADD COLUMN listed_until INTEGER", 0)
    if ("listed" in columns) {
        driver.execute(
            null,
            "UPDATE attestation_schema SET listed_from = 0, listed_until = CASE WHEN listed = 1 THEN NULL ELSE 0 END",
            0,
        )
    }
}

private fun tableColumns(
    driver: SqlDriver,
    table: String
): Set<String> =
    driver
        .executeQuery(
            identifier = null,
            sql = "PRAGMA table_info($table)",
            mapper = { cursor ->
                val names = mutableSetOf<String>()
                while (cursor.next().value) {
                    cursor.getString(1)?.let { names += it }
                }
                QueryResult.Value(names)
            },
            parameters = 0,
        ).value

private object LongIdentityAdapter : ColumnAdapter<Long, Long> {
    override fun decode(databaseValue: Long): Long = databaseValue

    override fun encode(value: Long): Long = value
}
