/*
 * (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.crypto.key.persistence.sqlite

/** Shared configuration rules for the JVM SQLite key-reference graph and its store factory. */
object SqliteKeyReferenceDatabaseSettings {
    const val JDBC_URL_PROPERTY: String = "database.key-reference.sqlite.url"
    const val DEFAULT_JDBC_URL: String = "jdbc:sqlite:./data/key-references.db"

    fun normalizeJdbcUrl(configuredPathOrJdbcUrl: String?): String {
        val configured = configuredPathOrJdbcUrl?.trim().orEmpty()
        return when {
            configured.isEmpty() -> DEFAULT_JDBC_URL
            configured.startsWith("jdbc:") -> configured
            else -> "jdbc:sqlite:$configured"
        }
    }

    /** An in-memory SQLite URL cannot prove ownership history after a process restart. */
    fun hasDurableOwnershipHistory(configuredPathOrJdbcUrl: String?): Boolean {
        val jdbcUrl = normalizeJdbcUrl(configuredPathOrJdbcUrl).lowercase()
        val location = jdbcUrl.removePrefix("jdbc:sqlite:")
        return location != ":memory:" &&
            !location.contains(":memory:") &&
            !(location.startsWith("file:") && location.contains("mode=memory"))
    }
}
