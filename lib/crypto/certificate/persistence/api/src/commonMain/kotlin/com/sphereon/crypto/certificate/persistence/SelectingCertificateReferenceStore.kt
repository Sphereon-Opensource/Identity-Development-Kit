/*
 * (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.crypto.certificate.persistence

import com.sphereon.core.api.conf.AppConfigService
import com.sphereon.core.api.log.AppLogManager
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Multibinds
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

@ContributesTo(AppScope::class)
interface CertificateReferenceStoreFactoryMultibindings {
    @Multibinds(allowEmpty = true)
    fun certificateReferenceStoreFactories(): Map<String, CertificateReferenceStoreFactory>
}

/**
 * Selects one contributed certificate store from the deployment's app database dialect. A
 * configured dialect without a matching factory fails closed; an absent dialect is allowed only
 * when zero or one factory is contributed.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(
    SessionScope::class,
    binding = binding<CertificateReferenceStore>(),
    replaces = [NoOpCertificateReferenceStore::class],
)
class SelectingCertificateReferenceStore(
    private val configService: AppConfigService,
    private val factories: Map<String, CertificateReferenceStoreFactory>,
    private val logManager: AppLogManager,
) : CertificateReferenceStore {
    private val delegate: CertificateReferenceStore by lazy {
        val databaseDialect =
            (
                configService.getPropertyAsString(PROPERTY_DATABASE_DIALECT, null)
                    ?: configService.getPropertyAsString(PROPERTY_DATABASE_DIALECT_DEFAULT_ENTRY, null)
            )?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }

        val factory =
            when {
                databaseDialect != null ->
                    factories[databaseDialect]
                        ?: error(
                            "$PROPERTY_DATABASE_DIALECT='$databaseDialect' but no CertificateReferenceStoreFactory " +
                                "for that dialect is on the classpath (contributed: " +
                                factories.keys.sorted().joinToString(", ").ifEmpty { "(none)" } + ")",
                        )
                factories.isEmpty() -> null
                factories.size == 1 -> factories.values.single()
                else ->
                    error(
                        "Certificate-reference dialect is ambiguous: multiple factories are contributed " +
                            "(${factories.keys.sorted().joinToString(", ")}) and $PROPERTY_DATABASE_DIALECT is not set",
                    )
            }

        logManager.withTag("CertificateReferencePersistence").debug(
            "Certificate-reference store: type=${factory?.type ?: "none"} (databaseDialect=${databaseDialect ?: "-"})",
        )
        factory?.createStore() ?: NoOpCertificateReferenceStore()
    }

    companion object {
        const val PROPERTY_DATABASE_DIALECT = "database.app.dialect"
        const val PROPERTY_DATABASE_DIALECT_DEFAULT_ENTRY = "database.app.default.dialect"
    }

    override val isAvailable: Boolean get() = delegate.isAvailable
    override val ownershipHistoryCapability: CertificateReferenceHistoryCapability
        get() = delegate.ownershipHistoryCapability

    override suspend fun save(record: CertificateReferenceRecord) = delegate.save(record)

    override suspend fun upsert(record: CertificateReferenceRecord) = delegate.upsert(record)

    override suspend fun findById(tenantId: String, id: String) = delegate.findById(tenantId, id)

    override suspend fun findByAlias(
        tenantId: String,
        alias: String,
        providerId: String,
        kind: CertificateReferenceKind,
    ) = delegate.findByAlias(tenantId, alias, providerId, kind)

    override suspend fun findAllByAliasIncludingDeleted(
        tenantId: String,
        alias: String,
        providerId: String?,
        kind: CertificateReferenceKind,
    ) = delegate.findAllByAliasIncludingDeleted(tenantId, alias, providerId, kind)

    override suspend fun findLatestByAliasIncludingDeleted(
        tenantId: String,
        alias: String,
        providerId: String?,
        kind: CertificateReferenceKind,
    ) = delegate.findLatestByAliasIncludingDeleted(tenantId, alias, providerId, kind)

    override suspend fun findByProviderCertificateId(
        tenantId: String,
        providerId: String,
        providerCertificateId: String,
    ) = delegate.findByProviderCertificateId(tenantId, providerId, providerCertificateId)

    override suspend fun findByLinkedKeyReferenceId(tenantId: String, linkedKeyReferenceId: String) =
        delegate.findByLinkedKeyReferenceId(tenantId, linkedKeyReferenceId)

    override suspend fun findAll(
        tenantId: String,
        providerId: String?,
        kind: CertificateReferenceKind?,
        source: CertificateReferenceSource?,
    ) = delegate.findAll(tenantId, providerId, kind, source)

    override suspend fun delete(
        tenantId: String,
        alias: String,
        providerId: String,
        kind: CertificateReferenceKind,
    ) = delegate.delete(tenantId, alias, providerId, kind)

    override suspend fun deleteById(tenantId: String, id: String) = delegate.deleteById(tenantId, id)
}
