/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.crypto.key.persistence

/**
 * Factory SPI for [KeyReferenceStore] dialects, keyed by [type].
 *
 * Each persistence dialect module (SQLite in IDK, PostgreSQL/MySQL in EDK)
 * contributes its factory into a `Map<String, KeyReferenceStoreFactory>`
 * multibinding instead of binding [KeyReferenceStore] directly.
 * [SelectingKeyReferenceStore] dispatches to the factory matching the
 * deployment's shared database dialect (`database.app.default.dialect`), so
 * key references land in the same database as every other router-backed
 * store and multiple dialect modules can coexist on one classpath.
 */
interface KeyReferenceStoreFactory {
    /** The dialect this factory produces (`sqlite`, `postgresql`, `mysql`, …). */
    val type: String

    /**
     * Creates a store instance for the current session. Implementations must
     * defer any database connection/bootstrap work until this call (or later)
     * so that unselected dialects on the classpath never touch their backing
     * database.
     */
    fun createStore(): KeyReferenceStore

    companion object {
        const val TYPE_SQLITE = "sqlite"
        const val TYPE_POSTGRESQL = "postgresql"
        const val TYPE_MYSQL = "mysql"
    }
}
