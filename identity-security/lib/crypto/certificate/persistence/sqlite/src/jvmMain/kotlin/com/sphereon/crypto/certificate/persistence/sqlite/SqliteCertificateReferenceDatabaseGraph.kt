/*
 * (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.crypto.certificate.persistence.sqlite

import app.cash.sqldelight.driver.jdbc.asJdbcDriver
import app.cash.sqldelight.db.SqlDriver
import com.sphereon.core.api.conf.AppConfigService
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import java.nio.file.Files
import java.nio.file.Path

/** Creates durable SQLite certificate-reference databases without hiding test-only in-memory use. */
object SqliteCertificateReferenceDatabases {
    const val JDBC_URL_PROPERTY: String = "database.certificate-reference.sqlite.url"
    const val DEFAULT_JDBC_URL: String = "jdbc:sqlite:./data/certificate-references.db"

    fun dataSource(configuredPathOrJdbcUrl: String? = null): HikariDataSource {
        val jdbcUrl = normalizeJdbcUrl(configuredPathOrJdbcUrl)
        createParentDirectory(jdbcUrl)
        return HikariDataSource(
            HikariConfig().apply {
                this.jdbcUrl = jdbcUrl
                maximumPoolSize = 1
            },
        )
    }

    fun database(driver: SqlDriver): CertificateReferenceDatabaseSqlite {
        CertificateReferenceDatabaseSqlite.Schema.create(driver)
        return CertificateReferenceDatabaseSqlite(driver)
    }

    private fun normalizeJdbcUrl(configuredPathOrJdbcUrl: String?): String {
        val configured = configuredPathOrJdbcUrl?.trim().orEmpty()
        return when {
            configured.isEmpty() -> DEFAULT_JDBC_URL
            configured.startsWith("jdbc:") -> configured
            else -> "jdbc:sqlite:$configured"
        }
    }

    private fun createParentDirectory(jdbcUrl: String) {
        val sqliteLocation = jdbcUrl.removePrefix("jdbc:sqlite:")
        if (sqliteLocation == ":memory:" || sqliteLocation.startsWith("file:")) return
        Path.of(sqliteLocation).toAbsolutePath().parent?.let(Files::createDirectories)
    }
}

/** Default JVM database wiring for the IDK SQLite certificate-reference store. */
@ContributesTo(AppScope::class)
interface SqliteCertificateReferenceDatabaseGraph {
    @Provides
    @SingleIn(AppScope::class)
    fun provideCertificateReferenceDatabaseSqlite(appConfig: AppConfigService): CertificateReferenceDatabaseSqlite {
        val dataSource = SqliteCertificateReferenceDatabases.dataSource(
            appConfig.getPropertyAsString(SqliteCertificateReferenceDatabases.JDBC_URL_PROPERTY),
        )
        val driver = dataSource.asJdbcDriver()
        return SqliteCertificateReferenceDatabases.database(driver)
    }
}
