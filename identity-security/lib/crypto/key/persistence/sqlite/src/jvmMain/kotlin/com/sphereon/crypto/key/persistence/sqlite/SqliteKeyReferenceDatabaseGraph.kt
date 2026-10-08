/*
 * (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.crypto.key.persistence.sqlite

import app.cash.sqldelight.driver.jdbc.asJdbcDriver
import com.sphereon.core.api.conf.AppConfigService
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import java.nio.file.Files
import java.nio.file.Path

/** Default JVM wiring for a durable SQLite key-reference database. */
@ContributesTo(AppScope::class)
interface SqliteKeyReferenceDatabaseGraph {
    @Provides
    @SingleIn(AppScope::class)
    fun provideKeyReferenceDatabaseSqlite(appConfig: AppConfigService): KeyReferenceDatabaseSqlite {
        val configured = appConfig.getPropertyAsString(SqliteKeyReferenceDatabaseSettings.JDBC_URL_PROPERTY)
        check(SqliteKeyReferenceDatabaseSettings.hasDurableOwnershipHistory(configured)) {
            "${SqliteKeyReferenceDatabaseSettings.JDBC_URL_PROPERTY} must be file-backed; in-memory SQLite cannot authorize durable key ownership operations"
        }

        val jdbcUrl = SqliteKeyReferenceDatabaseSettings.normalizeJdbcUrl(configured)
        val sqliteLocation = jdbcUrl.removePrefix("jdbc:sqlite:")
        if (!sqliteLocation.startsWith("file:")) {
            Path.of(sqliteLocation).toAbsolutePath().parent?.let(Files::createDirectories)
        }
        val dataSource =
            HikariDataSource(
                HikariConfig().apply {
                    this.jdbcUrl = jdbcUrl
                    maximumPoolSize = 1
                },
            )
        val driver = dataSource.asJdbcDriver()
        driver.execute(null, "PRAGMA foreign_keys = ON", 0)
        KeyReferenceDatabaseSqlite.Schema.create(driver)
        return KeyReferenceDatabaseSqlite(driver)
    }
}
