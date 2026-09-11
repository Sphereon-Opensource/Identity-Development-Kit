/*
 * (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.crypto.certificate.persistence.sqlite

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.asJdbcDriver
import com.sphereon.crypto.certificate.persistence.CertificateReferenceKind
import com.sphereon.crypto.certificate.persistence.CertificateReferenceRecord
import com.sphereon.crypto.certificate.persistence.CertificateReferenceSource
import com.sphereon.crypto.core.ResourceControlMode
import com.sphereon.crypto.certificate.persistence.CertificateReferenceStoreErrorCodes
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.io.path.createTempDirectory
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SqliteCertificateReferenceStoreImplTest {
    private lateinit var dataSource: HikariDataSource
    private lateinit var driver: SqlDriver
    private lateinit var database: CertificateReferenceDatabaseSqlite
    private lateinit var store: SqliteCertificateReferenceStoreImpl

    @BeforeAll
    fun setup() {
        dataSource = HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = "jdbc:sqlite:file:certificate_reference_test?mode=memory&cache=shared"
                maximumPoolSize = 1
            },
        )
        driver = dataSource.asJdbcDriver()
        CertificateReferenceDatabaseSqlite.Schema.create(driver)
        database = CertificateReferenceDatabaseSqlite(driver)
        store = SqliteCertificateReferenceStoreImpl(database)
    }

    @AfterAll
    fun teardown() {
        dataSource.close()
    }

    @Test
    fun tenantIsolationLookupAndPublicMaterialRoundTrip() = runTest {
        val alias = "certificate-${Uuid.random()}"
        val provider = "provider-${Uuid.random()}"
        val tenantA = record(tenantId = "tenant-a-${Uuid.random()}", alias = alias, providerId = provider)
        val tenantB = tenantA.copy(id = Uuid.random().toString(), tenantId = "tenant-b-${Uuid.random()}")

        assertTrue(store.save(tenantA).isOk)
        assertTrue(store.save(tenantB).isOk)
        val found = store.findByAlias(tenantA.tenantId, alias, provider, tenantA.kind).value
        assertNotNull(found)
        assertEquals(tenantA.tenantId, found.tenantId)
        assertNull(store.findById(tenantB.tenantId, tenantA.id).value)
        assertContentEquals(tenantA.certificateChainDer, found.certificateChainDer)
        assertContentEquals(tenantA.certificateFingerprint, found.certificateFingerprint)
        assertContentEquals(tenantA.publicKeyFingerprint, found.publicKeyFingerprint)
        assertEquals(ResourceControlMode.EXTERNALLY_MANAGED, found.controlMode)
    }

    @Test
    fun savePersistsAndReturnsCompleteDeletedRecord() = runTest {
        val persisted = record(
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
            createdById = "creator-1",
            updatedAt = Instant.parse("2026-01-02T00:00:00Z"),
            updatedById = "updater-1",
            deletedAt = Instant.parse("2026-01-03T00:00:00Z"),
            deletedById = "deleter-1",
        )

        val saved = store.save(persisted).value

        assertRecordContentEquals(persisted, saved)
        assertRecordContentEquals(persisted, readPersistedRecord(dataSource, persisted.tenantId, persisted.id)!!)
        assertNull(store.findById(persisted.tenantId, persisted.id).value)
    }

    @Test
    fun upsertPersistsDeletionMetadataAndReturnsCompletePersistedRecord() = runTest {
        val original = record(
            alias = "deleted-upsert-${Uuid.random()}",
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
            createdById = "original-creator",
        )
        assertTrue(store.save(original).isOk)
        val incoming = original.copy(
            id = Uuid.random().toString(),
            providerCertificateId = "version-2",
            certificateChainDer = CertificateReferenceRecord.encodeCertificateChain(listOf(byteArrayOf(9, 8, 7))),
            createdAt = Instant.parse("2026-02-01T00:00:00Z"),
            createdById = "incoming-creator",
            updatedAt = Instant.parse("2026-02-02T00:00:00Z"),
            updatedById = "updater-2",
            deletedAt = Instant.parse("2026-02-03T00:00:00Z"),
            deletedById = "deleter-2",
        )
        val expected = incoming.copy(
            id = original.id,
            createdAt = original.createdAt,
            createdById = original.createdById,
        )

        val upserted = store.upsert(incoming).value

        assertRecordContentEquals(expected, upserted)
        assertRecordContentEquals(expected, readPersistedRecord(dataSource, expected.tenantId, expected.id)!!)
        assertNull(store.findById(expected.tenantId, expected.id).value)
    }

    @Test
    fun upsertUsesTenantAliasProviderKindAndProviderAndKeyIndexesAreQueryable() = runTest {
        val original = record(alias = "upsert-${Uuid.random()}").copy(
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
            createdById = "original-creator",
        )
        assertTrue(store.save(original).isOk)
        val updated = original.copy(
            id = Uuid.random().toString(),
            providerCertificateId = "version-2",
            certificateChainDer = CertificateReferenceRecord.encodeCertificateChain(listOf(byteArrayOf(9, 8, 7))),
            createdAt = Instant.parse("2026-02-01T00:00:00Z"),
            createdById = "incoming-creator",
            updatedAt = Clock.System.now(),
        )
        val upserted = store.upsert(updated).value
        assertEquals(original.id, upserted.id)
        assertEquals(original.createdAt, upserted.createdAt)
        assertEquals(original.createdById, upserted.createdById)
        assertEquals("version-2", store.findByProviderCertificateId(original.tenantId, original.providerId, "version-2").value.single().providerCertificateId)
        assertEquals(1, store.findByLinkedKeyReferenceId(original.tenantId, original.linkedKeyReferenceId!!).value.size)
        assertContentEquals(updated.certificateChainDer, store.findById(original.tenantId, original.id).value?.certificateChainDer)
    }

    @Test
    fun upsertFailsWhenAnExistingRowCannotBeUpdated() = runTest {
        val original = record(alias = "ignored-update-${Uuid.random()}")
        assertTrue(store.save(original).isOk)
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    "CREATE TRIGGER ignore_certificate_reference_update " +
                        "BEFORE UPDATE ON certificate_reference WHEN OLD.id = '${original.id}' " +
                        "BEGIN SELECT RAISE(IGNORE); END",
                )
            }
        }
        try {
            val result = store.upsert(original.copy(providerCertificateId = "must-not-report-success"))

            assertTrue(result.isErr)
            assertEquals("certificate-1", store.findById(original.tenantId, original.id).value?.providerCertificateId)
        } finally {
            dataSource.connection.use { connection ->
                connection.createStatement().use { statement -> statement.execute("DROP TRIGGER ignore_certificate_reference_update") }
            }
        }
    }

    @Test
    fun activeIdentityIsUniqueButReusableAfterSoftDelete() = runTest {
        val original = record(alias = "reuse-${Uuid.random()}")
        assertTrue(store.save(original).isOk)
        assertTrue(store.save(original.copy(id = Uuid.random().toString())).isErr)
        assertTrue(store.delete(original.tenantId, original.alias, original.providerId, original.kind).value)
        assertNull(store.findByAlias(original.tenantId, original.alias, original.providerId, original.kind).value)
        assertTrue(store.save(original.copy(id = Uuid.random().toString())).isOk)
    }

    @Test
    fun deleteReportsOnlyAnActiveTenantOwnedRowChange() = runTest {
        val byAlias = record(alias = "delete-alias-${Uuid.random()}")
        val byId = record(alias = "delete-id-${Uuid.random()}")
        assertTrue(store.save(byAlias).isOk)
        assertTrue(store.save(byId).isOk)

        assertEquals(false, store.delete(byAlias.tenantId, "missing", byAlias.providerId, byAlias.kind).value)
        assertEquals(false, store.delete("wrong-tenant", byAlias.alias, byAlias.providerId, byAlias.kind).value)
        assertEquals(true, store.delete(byAlias.tenantId, byAlias.alias, byAlias.providerId, byAlias.kind).value)
        assertEquals(false, store.delete(byAlias.tenantId, byAlias.alias, byAlias.providerId, byAlias.kind).value)

        assertEquals(false, store.deleteById("wrong-tenant", byId.id).value)
        assertEquals(false, store.deleteById(byId.tenantId, "missing").value)
        assertEquals(true, store.deleteById(byId.tenantId, byId.id).value)
        assertEquals(false, store.deleteById(byId.tenantId, byId.id).value)
    }

    @Test
    fun latestAliasLookupRetainsDeletedOwnershipAfterActiveAliasReuse() = runTest {
        val tenantId = "tenant-history-${Uuid.random()}"
        val alias = "history-alias-${Uuid.random()}"
        val providerId = "provider-history-${Uuid.random()}"
        val kind = CertificateReferenceKind.TRUSTED_CERTIFICATE
        val deletedReference = record(
            tenantId = tenantId,
            alias = alias,
            providerId = providerId,
            kind = kind,
            linkedKeyReferenceId = null,
        )
        assertTrue(store.save(deletedReference).isOk)
        assertTrue(store.delete(tenantId, alias, providerId, kind).value)

        val activeReference = record(
            tenantId = tenantId,
            alias = alias,
            providerId = providerId,
            kind = kind,
            linkedKeyReferenceId = null,
        )
        assertTrue(store.save(activeReference).isOk)
        assertEquals(
            activeReference.id,
            store.findLatestByAliasIncludingDeleted(tenantId, alias, providerId, kind).value?.id,
        )

        assertTrue(store.delete(tenantId, alias, providerId, kind).value)
        val latestDeleted = store.findLatestByAliasIncludingDeleted(tenantId, alias, providerId, kind).value
        assertEquals(activeReference.id, latestDeleted?.id)
        assertNotNull(latestDeleted?.deletedAt)
    }

    @Test
    fun crossTenantSameIdUpsertFailsWithoutMutatingEitherTenant() = runTest {
        val owner = record(tenantId = "tenant-owner-${Uuid.random()}", providerCertificateId = "owner-certificate")
        val contender = record(
            id = owner.id,
            tenantId = "tenant-contender-${Uuid.random()}",
            providerCertificateId = "contender-certificate",
        )
        assertTrue(store.save(owner).isOk)

        val result = store.upsert(contender)

        assertTrue(result.isErr)
        assertEquals(CertificateReferenceStoreErrorCodes.PERSISTENCE_CONFLICT, result.error.code)
        assertEquals("owner-certificate", store.findById(owner.tenantId, owner.id).value?.providerCertificateId)
        assertTrue(store.findAll(contender.tenantId).value.isEmpty())
    }

    @Test
    fun updateRecordRequiresMatchingTenantId() = runTest {
        val owner = record(tenantId = "tenant-owner-${Uuid.random()}", providerCertificateId = "owner-certificate")
        assertTrue(store.save(owner).isOk)

        database.certificateReferenceQueries.updateRecord(
            id = owner.id,
            tenantId = "tenant-other-${Uuid.random()}",
            providerCertificateId = "mutated-certificate",
            source = owner.source.toStorageValue(),
            controlMode = "externally_managed",
            linkedKeyReferenceId = owner.linkedKeyReferenceId,
            certificateChainDer = owner.certificateChainDer,
            certificateFingerprint = owner.certificateFingerprint,
            publicKeyFingerprint = owner.publicKeyFingerprint,
            updatedAt = Clock.System.now().toString(),
            updatedById = null,
            deletedAt = null,
            deletedById = null,
        )

        assertEquals("owner-certificate", store.findById(owner.tenantId, owner.id).value?.providerCertificateId)
    }

    @Test
    fun providerCertificateLookupReturnsAllTenantMatchesInDeterministicOrder() = runTest {
        val tenantId = "tenant-provider-lookup-${Uuid.random()}"
        val providerId = "provider-${Uuid.random()}"
        val providerCertificateId = "certificate-${Uuid.random()}"
        val trusted = record(
            tenantId = tenantId,
            alias = "a-trusted",
            providerId = providerId,
            providerCertificateId = providerCertificateId,
            kind = CertificateReferenceKind.TRUSTED_CERTIFICATE,
            source = CertificateReferenceSource.PROVIDER_NATIVE,
            linkedKeyReferenceId = null,
        )
        val chain = record(
            tenantId = tenantId,
            alias = "b-chain",
            providerId = providerId,
            providerCertificateId = providerCertificateId,
            source = CertificateReferenceSource.PROVIDER_NATIVE,
        )
        val otherTenant = trusted.copy(id = Uuid.random().toString(), tenantId = "tenant-other-${Uuid.random()}")
        assertTrue(store.save(chain).isOk)
        assertTrue(store.save(trusted).isOk)
        assertTrue(store.save(otherTenant).isOk)

        val matches = store.findByProviderCertificateId(tenantId, providerId, providerCertificateId).value

        assertEquals(listOf("a-trusted", "b-chain"), matches.map { it.alias })
    }

    @Test
    fun fileBackedConfigurationSurvivesCloseAndReopenWithExternalOwnershipMetadata() = runTest {
        val directory = createTempDirectory("certificate-reference-durability-")
        val databasePath = directory.resolve("certificate-references.db")
        val persisted = record(
            tenantId = "durable-tenant-${Uuid.random()}",
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
            createdById = "creator-1",
            updatedAt = Instant.parse("2026-01-02T00:00:00Z"),
            updatedById = "updater-1",
            deletedAt = Instant.parse("2026-01-03T00:00:00Z"),
            deletedById = "deleter-1",
        )
        try {
            SqliteCertificateReferenceDatabases.dataSource(databasePath.toString()).use { firstSource ->
                val firstStore = SqliteCertificateReferenceStoreImpl(
                    SqliteCertificateReferenceDatabases.database(firstSource.asJdbcDriver()),
                )
                assertRecordContentEquals(persisted, firstStore.save(persisted).value)
            }

            SqliteCertificateReferenceDatabases.dataSource(databasePath.toString()).use { reopenedSource ->
                val reopenedStore = SqliteCertificateReferenceStoreImpl(
                    SqliteCertificateReferenceDatabases.database(reopenedSource.asJdbcDriver()),
                )
                val restored = readPersistedRecord(reopenedSource, persisted.tenantId, persisted.id)
                assertNotNull(restored)
                assertRecordContentEquals(persisted, restored)
                assertNull(reopenedStore.findById(persisted.tenantId, persisted.id).value)
            }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun freshSchemaAndBaselineMigrationHaveTheSameOrderedShape() = runTest {
        val fresh = withDatabase("fresh") { source, isolatedDriver ->
            CertificateReferenceDatabaseSqlite.Schema.create(isolatedDriver)
            sqliteShape(source)
        }
        val migrated = withDatabase("migrated") { source, isolatedDriver ->
            CertificateReferenceDatabaseSqlite.Schema.migrate(
                isolatedDriver,
                oldVersion = 0,
                newVersion = CertificateReferenceDatabaseSqlite.Schema.version,
            )
            sqliteShape(source)
        }
        assertEquals(fresh, migrated)
    }

    private fun record(
        id: String = Uuid.random().toString(),
        tenantId: String = "tenant-${Uuid.random()}",
        alias: String = "alias-${Uuid.random()}",
        providerId: String = "provider-${Uuid.random()}",
        providerCertificateId: String? = "certificate-1",
        kind: CertificateReferenceKind = CertificateReferenceKind.KEY_CERTIFICATE_CHAIN,
        source: CertificateReferenceSource = CertificateReferenceSource.STORED_PUBLIC_MATERIAL,
        linkedKeyReferenceId: String? = "key-reference-${Uuid.random()}",
        createdAt: Instant = Clock.System.now(),
        createdById: String? = null,
        updatedAt: Instant = Clock.System.now(),
        updatedById: String? = null,
        deletedAt: Instant? = null,
        deletedById: String? = null,
    ): CertificateReferenceRecord {
        val leaf = byteArrayOf(0x30, 0x03, 0x01, 0x01, 0x00)
        val root = byteArrayOf(0x30, 0x02, 0x05, 0x00)
        return CertificateReferenceRecord(
            id = id,
            tenantId = tenantId,
            alias = alias,
            providerId = providerId,
            providerCertificateId = providerCertificateId,
            kind = kind,
            source = source,
            controlMode = ResourceControlMode.EXTERNALLY_MANAGED,
            linkedKeyReferenceId = linkedKeyReferenceId,
            certificateChainDer = if (source == CertificateReferenceSource.STORED_PUBLIC_MATERIAL) {
                CertificateReferenceRecord.encodeCertificateChain(listOf(leaf, root))
            } else {
                null
            },
            certificateFingerprint = CertificateReferenceRecord.certificateFingerprintOf(leaf),
            publicKeyFingerprint = CertificateReferenceRecord.publicKeyFingerprintOfCanonicalSpki(byteArrayOf(0x30, 0x01, 0x00)),
            createdAt = createdAt,
            createdById = createdById,
            updatedAt = updatedAt,
            updatedById = updatedById,
            deletedAt = deletedAt,
            deletedById = deletedById,
        )
    }

    private fun readPersistedRecord(
        source: HikariDataSource,
        tenantId: String,
        id: String,
    ): CertificateReferenceRecord? = source.connection.use { connection ->
        connection.prepareStatement(
            """
            SELECT id, tenant_id, alias, provider_id, provider_certificate_id, kind, source, control_mode,
                   linked_key_reference_id, certificate_chain_der, certificate_fingerprint, public_key_fingerprint,
                   created_at, created_by_id, updated_at, updated_by_id, deleted_at, deleted_by_id
            FROM certificate_reference
            WHERE tenant_id = ? AND id = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, tenantId)
            statement.setString(2, id)
            statement.executeQuery().use { rows ->
                if (!rows.next()) {
                    return@use null
                }
                CertificateReferenceRecord(
                    id = rows.getString("id"),
                    tenantId = rows.getString("tenant_id"),
                    alias = rows.getString("alias"),
                    providerId = rows.getString("provider_id"),
                    providerCertificateId = rows.getString("provider_certificate_id"),
                    kind = CertificateReferenceKind.fromStorageValue(rows.getString("kind")),
                    source = CertificateReferenceSource.fromStorageValue(rows.getString("source")),
                    controlMode = when (rows.getString("control_mode")) {
                        "platform_managed" -> ResourceControlMode.PLATFORM_MANAGED
                        "externally_managed" -> ResourceControlMode.EXTERNALLY_MANAGED
                        else -> error("Unknown certificate reference control mode")
                    },
                    linkedKeyReferenceId = rows.getString("linked_key_reference_id"),
                    certificateChainDer = rows.getBytes("certificate_chain_der"),
                    certificateFingerprint = rows.getBytes("certificate_fingerprint"),
                    publicKeyFingerprint = rows.getBytes("public_key_fingerprint"),
                    createdAt = Instant.parse(rows.getString("created_at")),
                    createdById = rows.getString("created_by_id"),
                    updatedAt = Instant.parse(rows.getString("updated_at")),
                    updatedById = rows.getString("updated_by_id"),
                    deletedAt = rows.getString("deleted_at")?.let(Instant::parse),
                    deletedById = rows.getString("deleted_by_id"),
                )
            }
        }
    }

    private fun assertRecordContentEquals(
        expected: CertificateReferenceRecord,
        actual: CertificateReferenceRecord,
    ) {
        assertEquals(expected.id, actual.id)
        assertEquals(expected.tenantId, actual.tenantId)
        assertEquals(expected.alias, actual.alias)
        assertEquals(expected.providerId, actual.providerId)
        assertEquals(expected.providerCertificateId, actual.providerCertificateId)
        assertEquals(expected.kind, actual.kind)
        assertEquals(expected.source, actual.source)
        assertEquals(expected.controlMode, actual.controlMode)
        assertEquals(expected.linkedKeyReferenceId, actual.linkedKeyReferenceId)
        assertByteArrayContentEquals(expected.certificateChainDer, actual.certificateChainDer)
        assertByteArrayContentEquals(expected.certificateFingerprint, actual.certificateFingerprint)
        assertByteArrayContentEquals(expected.publicKeyFingerprint, actual.publicKeyFingerprint)
        assertEquals(expected.createdAt, actual.createdAt)
        assertEquals(expected.createdById, actual.createdById)
        assertEquals(expected.updatedAt, actual.updatedAt)
        assertEquals(expected.updatedById, actual.updatedById)
        assertEquals(expected.deletedAt, actual.deletedAt)
        assertEquals(expected.deletedById, actual.deletedById)
    }

    private fun assertByteArrayContentEquals(expected: ByteArray?, actual: ByteArray?) {
        if (expected == null) {
            assertNull(actual)
        } else {
            assertContentEquals(expected, assertNotNull(actual))
        }
    }

    private fun sqliteShape(source: HikariDataSource): List<String> = buildList {
        source.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("PRAGMA table_info(certificate_reference)").use { rows ->
                    while (rows.next()) add("column:${rows.getInt("cid")}:${rows.getString("name")}:${rows.getString("type")}:${rows.getInt("notnull")}:${rows.getString("dflt_value")}")
                }
                statement.executeQuery("SELECT name, sql FROM sqlite_master WHERE type = 'index' AND tbl_name = 'certificate_reference' ORDER BY name").use { rows ->
                    while (rows.next()) add("index:${rows.getString(1)}:${rows.getString(2)}")
                }
            }
        }
    }

    private fun <T> withDatabase(name: String, block: (HikariDataSource, SqlDriver) -> T): T {
        val source = HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = "jdbc:sqlite:file:certificate_reference_$name?mode=memory&cache=shared"
                maximumPoolSize = 1
            },
        )
        return source.use { block(it, it.asJdbcDriver()) }
    }
}
