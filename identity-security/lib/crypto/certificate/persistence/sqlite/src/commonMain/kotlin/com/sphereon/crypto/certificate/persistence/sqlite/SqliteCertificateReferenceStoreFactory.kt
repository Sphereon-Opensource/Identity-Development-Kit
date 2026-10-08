/*
 * (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.crypto.certificate.persistence.sqlite

import com.sphereon.crypto.certificate.persistence.CertificateReferenceStore
import com.sphereon.crypto.certificate.persistence.CertificateReferenceStoreFactory
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.StringKey

@Inject
@SingleIn(AppScope::class)
class SqliteCertificateReferenceStoreFactory(
    private val databaseProvider: Provider<CertificateReferenceDatabaseSqlite>,
) : CertificateReferenceStoreFactory {
    override val type: String = CertificateReferenceStoreFactory.TYPE_SQLITE

    override fun createStore(): CertificateReferenceStore = SqliteCertificateReferenceStoreImpl(databaseProvider())
}

@ContributesTo(AppScope::class)
interface SqliteCertificateReferencePersistenceModule {
    @Provides
    @IntoMap
    @StringKey(CertificateReferenceStoreFactory.TYPE_SQLITE)
    @SingleIn(AppScope::class)
    fun provideSqliteCertificateReferenceStoreFactory(impl: SqliteCertificateReferenceStoreFactory): CertificateReferenceStoreFactory = impl
}
