/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.crypto.key.persistence.sqlite

import com.sphereon.crypto.key.persistence.KeyReferenceStore
import com.sphereon.crypto.key.persistence.KeyReferenceStoreFactory
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.StringKey

/**
 * Contributes the SQLite dialect into the database-dialect selection map
 * (see [KeyReferenceStoreFactory]). The backing [KeyReferenceDatabaseSqlite]
 * is resolved lazily via [Provider] so merely having this module on the
 * classpath never opens the SQLite database when another dialect is selected.
 */
@Inject
@SingleIn(AppScope::class)
class SqliteKeyReferenceStoreFactory(
    private val databaseProvider: Provider<KeyReferenceDatabaseSqlite>,
) : KeyReferenceStoreFactory {
    override val type: String = KeyReferenceStoreFactory.TYPE_SQLITE

    override fun createStore(): KeyReferenceStore = SqliteKeyReferenceStoreImpl(databaseProvider())
}

@ContributesTo(AppScope::class)
interface SqliteKeyReferencePersistenceModule {
    @Provides
    @IntoMap
    @StringKey(KeyReferenceStoreFactory.TYPE_SQLITE)
    @SingleIn(AppScope::class)
    fun provideSqliteKeyReferenceStoreFactory(impl: SqliteKeyReferenceStoreFactory): KeyReferenceStoreFactory = impl
}
