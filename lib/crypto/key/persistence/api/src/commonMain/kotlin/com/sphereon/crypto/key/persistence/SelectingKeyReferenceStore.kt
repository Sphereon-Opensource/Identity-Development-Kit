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

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.conf.AppConfigService
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.log.AppLogManager
import com.sphereon.crypto.core.ManagedKeyReferenceFilter
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Multibinds
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Declares the dialect-factory multibinding so graphs without any persistence
 * dialect module on the classpath still resolve (empty map → behaves like the
 * historical [NoOpKeyReferenceStore] default).
 */
@ContributesTo(AppScope::class)
interface KeyReferenceStoreFactoryMultibindings {
    @Multibinds(allowEmpty = true)
    fun keyReferenceStoreFactories(): Map<String, KeyReferenceStoreFactory>
}

/**
 * [KeyReferenceStore] front that delegates every call to the store created by
 * the contributed [KeyReferenceStoreFactory] matching the deployment's shared
 * database dialect ([PROPERTY_DATABASE_DIALECT] `database.app.dialect`, falling
 * back to the router registry's [PROPERTY_DATABASE_DIALECT_DEFAULT_ENTRY]
 * `database.app.default.dialect`) — the same single source of truth the EDK
 * DatabaseRouter and every router-backed store (party, auth, invitation,
 * DID-postgres, …) already follow, so key references land in the same database
 * as the rest of the deployment. The end user picks the database in `application.yml` /
 * environment at deploy time; the classpath only determines which dialects
 * are *available*.
 *
 * A configured dialect without a matching factory on the classpath is a hard
 * error (silently falling back to another dialect would split the deployment
 * across databases). Without a configured dialect: the single contributed
 * dialect, [NoOpKeyReferenceStore] when none is contributed (iterating
 * key-store mode), and an error when several are contributed (ambiguous).
 *
 * Replaces the [NoOpKeyReferenceStore] contribution. Test graphs that bind
 * their own in-memory store must replace BOTH this class and
 * [NoOpKeyReferenceStore].
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(
    SessionScope::class,
    binding = binding<KeyReferenceStore>(),
    replaces = [NoOpKeyReferenceStore::class],
)
class SelectingKeyReferenceStore(
    private val configService: AppConfigService,
    private val factories: Map<String, KeyReferenceStoreFactory>,
    private val logManager: AppLogManager,
) : KeyReferenceStore {
    private val delegate: KeyReferenceStore by lazy {
        val databaseDialect =
            (
                configService.getPropertyAsString(PROPERTY_DATABASE_DIALECT, null)
                    ?: configService.getPropertyAsString(PROPERTY_DATABASE_DIALECT_DEFAULT_ENTRY, null)
            )?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }

        val factory =
            when {
                // Follow the deployment's database dialect — the same source of truth the
                // DatabaseRouter-backed stores use — so key references live in the same
                // database as everything else.
                databaseDialect != null -> {
                    factories[databaseDialect]
                        ?: error(
                            "$PROPERTY_DATABASE_DIALECT='$databaseDialect' but no KeyReferenceStoreFactory for that " +
                                "dialect is on the classpath (contributed: " +
                                factories.keys
                                    .sorted()
                                    .joinToString(", ")
                                    .ifEmpty { "(none)" } +
                                "). Add the matching lib-crypto-key-persistence module.",
                        )
                }

                else -> {
                    when (factories.size) {
                        0 -> {
                            null
                        }

                        1 -> {
                            factories.values.single()
                        }

                        else -> {
                            error(
                                "Key-reference dialect is ambiguous: multiple dialects on the classpath " +
                                    "(${factories.keys.sorted().joinToString(", ")}) and " +
                                    "$PROPERTY_DATABASE_DIALECT is not set.",
                            )
                        }
                    }
                }
            }

        logManager.withTag("KeyReferencePersistence").debug(
            "Key-reference store: type=${factory?.type ?: "none"} (databaseDialect=${databaseDialect ?: "-"})",
        )
        factory?.createStore() ?: NoOpKeyReferenceStore()
    }

    companion object {
        /** The app database dialect — the deployment-wide source of truth this store follows. */
        const val PROPERTY_DATABASE_DIALECT = "database.app.dialect"

        /**
         * Fallback: the EDK DatabaseRouter registry's per-entry key
         * (`database.app.<name>.dialect` with the `default` entry), as set by
         * existing deployments via `DATABASE_APP_DEFAULT_DIALECT`. Referenced as a
         * literal because the routing config module lives in EDK and this module is
         * IDK open-core.
         */
        const val PROPERTY_DATABASE_DIALECT_DEFAULT_ENTRY = "database.app.default.dialect"
    }

    override val isAvailable: Boolean get() = delegate.isAvailable
    override val ownershipHistoryCapability: KeyReferenceHistoryCapability get() = delegate.ownershipHistoryCapability

    override suspend fun save(record: KeyReferenceRecord): IdkResult<KeyReferenceRecord, IdkError> = delegate.save(record)

    override suspend fun upsert(record: KeyReferenceRecord): IdkResult<KeyReferenceRecord, IdkError> = delegate.upsert(record)

    override suspend fun findById(
        tenantId: String,
        id: String,
    ): IdkResult<KeyReferenceRecord?, IdkError> = delegate.findById(tenantId, id)

    override suspend fun findByKid(
        tenantId: String,
        kid: String,
        providerId: String?,
    ): IdkResult<KeyReferenceRecord?, IdkError> = delegate.findByKid(tenantId, kid, providerId)

    override suspend fun findByAlias(
        tenantId: String,
        alias: String,
        providerId: String?,
    ): IdkResult<KeyReferenceRecord?, IdkError> = delegate.findByAlias(tenantId, alias, providerId)

    override suspend fun findAllActiveByAlias(
        tenantId: String,
        alias: String,
        providerId: String?,
    ): IdkResult<List<KeyReferenceRecord>, IdkError> = delegate.findAllActiveByAlias(tenantId, alias, providerId)

    override suspend fun findAllActiveByKid(
        tenantId: String,
        kid: String,
        providerId: String?,
    ): IdkResult<List<KeyReferenceRecord>, IdkError> = delegate.findAllActiveByKid(tenantId, kid, providerId)

    override suspend fun findAllByAliasIncludingDeleted(
        tenantId: String,
        alias: String,
        providerId: String?,
    ): IdkResult<List<KeyReferenceRecord>, IdkError> = delegate.findAllByAliasIncludingDeleted(tenantId, alias, providerId)

    override suspend fun findAllByKidIncludingDeleted(
        tenantId: String,
        kid: String,
        providerId: String?,
    ): IdkResult<List<KeyReferenceRecord>, IdkError> = delegate.findAllByKidIncludingDeleted(tenantId, kid, providerId)

    override suspend fun findLatestByAliasIncludingDeleted(
        tenantId: String,
        alias: String,
        providerId: String?,
    ): IdkResult<KeyReferenceRecord?, IdkError> = delegate.findLatestByAliasIncludingDeleted(tenantId, alias, providerId)

    override suspend fun findLatestByKidIncludingDeleted(
        tenantId: String,
        kid: String,
        providerId: String?,
    ): IdkResult<KeyReferenceRecord?, IdkError> = delegate.findLatestByKidIncludingDeleted(tenantId, kid, providerId)

    override suspend fun findAll(
        tenantId: String,
        filter: ManagedKeyReferenceFilter?,
    ): IdkResult<List<KeyReferenceRecord>, IdkError> = delegate.findAll(tenantId, filter)

    override suspend fun delete(
        tenantId: String,
        alias: String,
        providerId: String,
    ): IdkResult<Boolean, IdkError> = delegate.delete(tenantId, alias, providerId)

    override suspend fun deleteByKid(
        tenantId: String,
        kid: String,
        providerId: String?,
    ): IdkResult<Boolean, IdkError> = delegate.deleteByKid(tenantId, kid, providerId)

    override suspend fun exists(
        tenantId: String,
        alias: String,
        providerId: String,
    ): IdkResult<Boolean, IdkError> = delegate.exists(tenantId, alias, providerId)
}
