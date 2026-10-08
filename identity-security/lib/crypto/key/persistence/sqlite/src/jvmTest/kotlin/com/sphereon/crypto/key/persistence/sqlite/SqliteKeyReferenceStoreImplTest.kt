/*
 * (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.sphereon.crypto.key.persistence.sqlite

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.asJdbcDriver
import com.sphereon.core.api.model.Origin
import com.sphereon.crypto.core.ManagedKeyReferenceFilter
import com.sphereon.crypto.core.ResourceControlMode
import com.sphereon.crypto.key.persistence.KeyReferenceRecord
import com.sphereon.crypto.key.persistence.KeyReferenceHistoryCapability
import com.sphereon.crypto.key.persistence.KeyReferenceStoreErrorCodes
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SqliteKeyReferenceStoreImplTest {
    private lateinit var store: SqliteKeyReferenceStoreImpl
    private lateinit var dataSource: HikariDataSource
    private lateinit var driver: SqlDriver

    @BeforeAll
    fun setup() {
        val config =
            HikariConfig().apply {
                jdbcUrl = "jdbc:sqlite:file:keyref_test?mode=memory&cache=shared"
                maximumPoolSize = 1
            }
        dataSource = HikariDataSource(config)
        driver = dataSource.asJdbcDriver()
        KeyReferenceDatabaseSqlite.Schema.create(driver)
        val database = KeyReferenceDatabaseSqlite(driver)
        store = SqliteKeyReferenceStoreImpl(database)
    }

    @AfterAll
    fun teardown() {
        dataSource.close()
    }

    private fun testRecord(
        tenantId: String = "tenant-1",
        alias: String = "test-key",
        providerId: String = "software-provider",
        kid: String? = "kid-123",
        controlMode: ResourceControlMode = ResourceControlMode.PLATFORM_MANAGED,
    ) = KeyReferenceRecord(
        id = Uuid.random().toString(),
        tenantId = tenantId,
        alias = alias,
        kid = kid,
        providerId = providerId,
        origin = Origin.MANAGED,
        controlMode = controlMode,
        createdAt = Clock.System.now(),
        updatedAt = Clock.System.now(),
    )

    @Test
    fun supportsDurableAllMatchOwnershipHistoryAcrossProviders() =
        runTest {
            val tenantId = "all-match-tenant-${Uuid.random()}"
            val alias = "all-match-alias-${Uuid.random()}"
            val kid = "all-match-kid-${Uuid.random()}"
            val first = testRecord(tenantId, alias, "provider-a-${Uuid.random()}", kid)
            val second = testRecord(tenantId, alias, "provider-b-${Uuid.random()}", kid)

            assertEquals(KeyReferenceHistoryCapability.UNSUPPORTED, store.ownershipHistoryCapability)
            assertTrue(store.save(first).isOk)
            assertTrue(store.save(second).isOk)
            assertEquals(setOf(first.id, second.id), store.findAllActiveByAlias(tenantId, alias).value.map { it.id }.toSet())
            assertEquals(setOf(first.id, second.id), store.findAllActiveByKid(tenantId, kid).value.map { it.id }.toSet())

            assertTrue(store.delete(first.tenantId, first.alias, first.providerId).value)
            assertTrue(store.delete(second.tenantId, second.alias, second.providerId).value)
            assertEquals(
                setOf(first.id, second.id),
                store.findAllByAliasIncludingDeleted(tenantId, alias).value.map { it.id }.toSet(),
            )
            assertEquals(
                setOf(first.id, second.id),
                store.findAllByKidIncludingDeleted(tenantId, kid).value.map { it.id }.toSet(),
            )
            assertTrue(store.findAllByAliasIncludingDeleted("other-tenant", alias).value.isEmpty())
        }

    @Test
    fun upsertCanonicalKidConstraintConflictHasStableRegistrationCode() =
        runTest {
            val tenantId = "constraint-tenant-${Uuid.random()}"
            val providerId = "constraint-provider-${Uuid.random()}"
            val kid = "constraint-kid-${Uuid.random()}"
            assertTrue(store.save(testRecord(tenantId, "constraint-alias-a", providerId, kid)).isOk)

            val result = store.upsert(testRecord(tenantId, "constraint-alias-b", providerId, kid))

            assertTrue(result.isErr)
            assertEquals(KeyReferenceStoreErrorCodes.EXTERNAL_KEY_REGISTRATION_CONFLICT, result.error.code)
        }

    @Test
    fun concurrentCanonicalKidUpsertsProduceOneSuccessAndOneStableConflict() =
        runTest {
            val databaseFile = Files.createTempFile("key-reference-race-", ".sqlite")
            val jdbcUrl = "jdbc:sqlite:${databaseFile.toAbsolutePath()}"
            fun newSource() =
                HikariDataSource(
                    HikariConfig().apply {
                        this.jdbcUrl = jdbcUrl
                        maximumPoolSize = 1
                    },
                )

            try {
                newSource().use { firstSource ->
                    newSource().use { secondSource ->
                        val firstDriver = firstSource.asJdbcDriver()
                        val secondDriver = secondSource.asJdbcDriver()
                        firstDriver.execute(null, "PRAGMA busy_timeout = 5000", 0)
                        secondDriver.execute(null, "PRAGMA busy_timeout = 5000", 0)
                        KeyReferenceDatabaseSqlite.Schema.create(firstDriver)
                        val firstStore = SqliteKeyReferenceStoreImpl(KeyReferenceDatabaseSqlite(firstDriver))
                        val secondStore = SqliteKeyReferenceStoreImpl(KeyReferenceDatabaseSqlite(secondDriver))
                        val tenantId = "race-tenant-${Uuid.random()}"
                        val providerId = "race-provider-${Uuid.random()}"
                        val canonicalKid = "race-kid-${Uuid.random()}"
                        val ready = CountDownLatch(2)
                        val start = CountDownLatch(1)

                        val results =
                            coroutineScope {
                                val deferred =
                                    listOf(
                                        async(Dispatchers.IO) {
                                            ready.countDown()
                                            check(start.await(10, TimeUnit.SECONDS))
                                            firstStore.upsert(testRecord(tenantId, "race-alias-a", providerId, canonicalKid))
                                        },
                                        async(Dispatchers.IO) {
                                            ready.countDown()
                                            check(start.await(10, TimeUnit.SECONDS))
                                            secondStore.upsert(testRecord(tenantId, "race-alias-b", providerId, canonicalKid))
                                        },
                                    )
                                check(ready.await(10, TimeUnit.SECONDS))
                                start.countDown()
                                deferred.awaitAll()
                            }

                        assertEquals(1, results.count { it.isOk })
                        val conflict = results.single { it.isErr }.error
                        assertEquals(KeyReferenceStoreErrorCodes.EXTERNAL_KEY_REGISTRATION_CONFLICT, conflict.code)
                    }
                }
            } finally {
                Files.deleteIfExists(databaseFile)
            }
        }

    @Test
    fun unrelatedUpsertConstraintFailureRemainsUnknownError() =
        runTest {
            val first = testRecord(alias = "primary-id-a-${Uuid.random()}", kid = "primary-kid-a-${Uuid.random()}")
            assertTrue(store.save(first).isOk)

            val result =
                store.upsert(
                    testRecord(alias = "primary-id-b-${Uuid.random()}", kid = "primary-kid-b-${Uuid.random()}").copy(id = first.id),
                )

            assertTrue(result.isErr)
            assertEquals("UNKNOWN_ERROR", result.error.code)
        }

    @Test
    fun externallyManagedRoundTripsThroughSaveUpsertAliasKidAndList() =
        runTest {
            val tenantId = "external-round-trip-tenant-${Uuid.random()}"
            val alias = "external-round-trip-${Uuid.random()}"
            val providerId = "external-round-trip-provider-${Uuid.random()}"
            val originalKid = "external-round-trip-original-kid-${Uuid.random()}"
            val updatedKid = "external-round-trip-updated-kid-${Uuid.random()}"
            val platformManaged = testRecord(
                tenantId = tenantId,
                alias = alias,
                providerId = providerId,
                kid = originalKid,
            )

            assertTrue(store.save(platformManaged).isOk)
            val externallyManaged = platformManaged.copy(
                id = Uuid.random().toString(),
                kid = updatedKid,
                controlMode = ResourceControlMode.EXTERNALLY_MANAGED,
                updatedAt = Clock.System.now(),
            )
            assertTrue(store.upsert(externallyManaged).isOk)

            assertEquals(
                "externally_managed",
                scalar("SELECT control_mode FROM key_reference WHERE id = '${platformManaged.id}'"),
            )
            val byAlias = store.findByAlias(tenantId, alias, providerId).value
            assertNotNull(byAlias)
            assertEquals(updatedKid, byAlias.kid)
            assertEquals(
                ResourceControlMode.EXTERNALLY_MANAGED,
                byAlias.controlMode,
            )
            assertNull(store.findByKid(tenantId, originalKid, providerId).value)
            val byKid = store.findByKid(tenantId, updatedKid, providerId).value
            assertNotNull(byKid)
            assertEquals(
                ResourceControlMode.EXTERNALLY_MANAGED,
                byKid.controlMode,
            )
            val listed = store.findAll(tenantId).value.single { it.alias == alias }
            assertEquals(updatedKid, listed.kid)
            assertEquals(ResourceControlMode.EXTERNALLY_MANAGED, listed.controlMode)
        }

    @Test
    fun upsertCannotRelabelAnExistingWalletUnitOwner() =
        runTest {
            val tenantId = "immutable-owner-tenant-${Uuid.random()}"
            val alias = "immutable-owner-alias-${Uuid.random()}"
            val providerId = "immutable-owner-provider-${Uuid.random()}"
            val original =
                testRecord(
                    tenantId = tenantId,
                    alias = alias,
                    providerId = providerId,
                ).copy(walletUnitId = "wallet-owner-a")
            assertTrue(store.save(original).isOk)

            val attemptedRelabel =
                store.upsert(
                    original.copy(
                        id = Uuid.random().toString(),
                        walletUnitId = "wallet-owner-b",
                        updatedAt = Clock.System.now(),
                    ),
                )

            assertTrue(attemptedRelabel.isOk)
            assertEquals("wallet-owner-a", attemptedRelabel.value.walletUnitId)
            val persisted = store.findByAlias(tenantId, alias, providerId).value
            assertNotNull(persisted)
            assertEquals("wallet-owner-a", persisted.walletUnitId)
        }

    @Test
    fun includeDeletedLookupsAreTenantAndProviderScoped() =
        runTest {
            val record = testRecord(
                tenantId = "history-tenant-${Uuid.random()}",
                alias = "history-alias-${Uuid.random()}",
                providerId = "history-provider-${Uuid.random()}",
                kid = "history-kid-${Uuid.random()}",
                controlMode = ResourceControlMode.EXTERNALLY_MANAGED,
            )
            assertTrue(store.save(record).isOk)
            assertTrue(store.delete(record.tenantId, record.alias, record.providerId).value)

            val byAlias = store.findLatestByAliasIncludingDeleted(record.tenantId, record.alias).value
            val byKid = store.findLatestByKidIncludingDeleted(record.tenantId, record.kid!!).value
            assertNotNull(byAlias)
            assertNotNull(byKid)
            assertEquals(record.id, byAlias.id)
            assertEquals(record.id, byKid.id)
            assertEquals(ResourceControlMode.EXTERNALLY_MANAGED, byAlias.controlMode)
            assertNotNull(byAlias.deletedAt)
            assertNull(store.findLatestByAliasIncludingDeleted("other-tenant", record.alias).value)
            assertNull(store.findLatestByAliasIncludingDeleted(record.tenantId, record.alias, "other-provider").value)
        }

    @Test
    fun fileBackedStoreSurvivesCloseAndReopenWithExternalOwnershipHistory() =
        runTest {
            val databaseFile = Files.createTempFile("key-reference-restart-", ".sqlite")
            Files.deleteIfExists(databaseFile)
            val jdbcUrl = "jdbc:sqlite:${databaseFile.toAbsolutePath()}"
            val record =
                testRecord(
                    tenantId = "restart-tenant-${Uuid.random()}",
                    alias = "restart-alias-${Uuid.random()}",
                    providerId = "restart-provider-${Uuid.random()}",
                    kid = "restart-kid-${Uuid.random()}",
                    controlMode = ResourceControlMode.EXTERNALLY_MANAGED,
                )

            fun open(): Pair<HikariDataSource, SqliteKeyReferenceStoreImpl> {
                val source =
                    HikariDataSource(
                        HikariConfig().apply {
                            this.jdbcUrl = jdbcUrl
                            maximumPoolSize = 1
                        },
                    )
                val driver = source.asJdbcDriver()
                KeyReferenceDatabaseSqlite.Schema.create(driver)
                return source to
                    SqliteKeyReferenceStoreImpl(
                        KeyReferenceDatabaseSqlite(driver),
                        KeyReferenceHistoryCapability.DURABLE,
                    )
            }

            try {
                val (firstSource, firstStore) = open()
                assertEquals(KeyReferenceHistoryCapability.DURABLE, firstStore.ownershipHistoryCapability)
                assertTrue(firstStore.save(record).isOk)
                assertTrue(firstStore.delete(record.tenantId, record.alias, record.providerId).value)
                firstSource.close()

                val (reopenedSource, reopenedStore) = open()
                try {
                    assertEquals(KeyReferenceHistoryCapability.DURABLE, reopenedStore.ownershipHistoryCapability)
                    val history = reopenedStore.findLatestByAliasIncludingDeleted(record.tenantId, record.alias, record.providerId).value
                    assertNotNull(history)
                    assertEquals(ResourceControlMode.EXTERNALLY_MANAGED, history.controlMode)
                    assertNotNull(history.deletedAt)
                    assertEquals(record.id, history.id)
                } finally {
                    reopenedSource.close()
                }
            } finally {
                Files.deleteIfExists(databaseFile)
            }
        }

    @Test
    fun unknownStoredControlModeFailsLoudly() =
        runTest {
            val record = testRecord(tenantId = "unknown-control-mode-tenant", alias = "unknown-control-mode")
            assertTrue(store.save(record).isOk)
            driver.execute(
                null,
                "UPDATE key_reference SET control_mode = 'corrupted_mode' WHERE id = '${record.id}'",
                0,
            )

            val result = store.findByAlias(record.tenantId, record.alias, record.providerId)
            assertTrue(result.isErr, "unknown persisted control_mode must not default silently")
        }

    @Test
    fun everySupportedMigrationStartConvergesWithFreshSchema() =
        runTest {
            val freshShape = withIsolatedSqliteDatabase("fresh") { source, isolatedDriver ->
                KeyReferenceDatabaseSqlite.Schema.create(isolatedDriver)
                keyReferenceSchemaShape(source)
            }

            listOf(0L, 1L).forEach { startVersion ->
                val upgradedShape = withIsolatedSqliteDatabase("upgrade_$startVersion") { source, isolatedDriver ->
                    if (startVersion == 1L) {
                        KeyReferenceDatabaseSqlite.Schema.migrate(isolatedDriver, oldVersion = 0, newVersion = 1)
                        insertLegacyRow(isolatedDriver, "legacy-v1")
                    }
                    KeyReferenceDatabaseSqlite.Schema.migrate(
                        isolatedDriver,
                        oldVersion = startVersion,
                        newVersion = KeyReferenceDatabaseSqlite.Schema.version,
                    )
                    if (startVersion == 1L) {
                        assertEquals(
                            "platform_managed",
                            scalar(source, "SELECT control_mode FROM key_reference WHERE id = 'legacy-v1'"),
                        )
                    }
                    keyReferenceSchemaShape(source)
                }

                assertEquals(
                    freshShape,
                    upgradedShape,
                    "SQLite schema migrated from version $startVersion must match fresh schema in column and index order",
                )
            }
        }

    private fun scalar(sql: String): String? =
        scalar(dataSource, sql)

    private fun scalar(source: HikariDataSource, sql: String): String? =
        source.connection.use { connection ->
            connection.createStatement().executeQuery(sql).use { resultSet ->
                if (resultSet.next()) resultSet.getString(1) else null
            }
        }

    private fun insertLegacyRow(isolatedDriver: SqlDriver, id: String) {
        isolatedDriver.execute(
            null,
            """
            INSERT INTO key_reference(
                id, tenant_id, alias, kid, provider_id, origin,
                key_type, signature_algorithm, key_visibility, key_encoding,
                created_at, created_by_id, updated_at, updated_by_id
            ) VALUES (
                '$id', 'legacy-tenant', 'legacy-alias', 'legacy-kid', 'legacy-provider', 'managed',
                NULL, NULL, NULL, NULL,
                '2026-01-01T00:00:00Z', NULL, '2026-01-01T00:00:00Z', NULL
            )
            """.trimIndent(),
            0,
        )
    }

    private fun <T> withIsolatedSqliteDatabase(
        label: String,
        block: (HikariDataSource, SqlDriver) -> T,
    ): T {
        val isolatedConfig = HikariConfig().apply {
            jdbcUrl = "jdbc:sqlite:file:keyref_${label}_${Uuid.random()}?mode=memory&cache=shared"
            maximumPoolSize = 1
        }
        return HikariDataSource(isolatedConfig).use { source ->
            block(source, source.asJdbcDriver())
        }
    }

    private fun keyReferenceSchemaShape(source: HikariDataSource): SqliteSchemaShape =
        source.connection.use { connection ->
            val columns = connection.createStatement().use { statement ->
                statement.executeQuery("PRAGMA table_xinfo(key_reference)").use { resultSet ->
                    buildList {
                        while (resultSet.next()) {
                            add(
                                SqliteColumnShape(
                                    ordinal = resultSet.getInt("cid"),
                                    name = resultSet.getString("name"),
                                    type = resultSet.getString("type"),
                                    nullable = resultSet.getInt("notnull") == 0,
                                    defaultValue = resultSet.getString("dflt_value"),
                                    primaryKeyPosition = resultSet.getInt("pk"),
                                    hidden = resultSet.getInt("hidden"),
                                ),
                            )
                        }
                    }
                }
            }
            data class IndexHeader(
                val name: String,
                val unique: Boolean,
                val origin: String,
                val partial: Boolean,
            )
            val headers = connection.createStatement().use { statement ->
                statement.executeQuery("PRAGMA index_list(key_reference)").use { resultSet ->
                    buildList {
                        while (resultSet.next()) {
                            add(
                                IndexHeader(
                                    name = resultSet.getString("name"),
                                    unique = resultSet.getInt("unique") == 1,
                                    origin = resultSet.getString("origin"),
                                    partial = resultSet.getInt("partial") == 1,
                                ),
                            )
                        }
                    }
                }
            }
            val indexes = headers.map { header ->
                val indexColumns = connection.createStatement().use { statement ->
                    statement.executeQuery("PRAGMA index_xinfo('${header.name}')").use { resultSet ->
                        buildList {
                            while (resultSet.next()) {
                                if (resultSet.getInt("key") == 1) {
                                    add(
                                        SqliteIndexColumnShape(
                                            ordinal = resultSet.getInt("seqno"),
                                            name = resultSet.getString("name"),
                                            descending = resultSet.getInt("desc") == 1,
                                            collation = resultSet.getString("coll"),
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
                val definition = connection.prepareStatement(
                    "SELECT sql FROM sqlite_master WHERE type = 'index' AND name = ?",
                ).use { statement ->
                    statement.setString(1, header.name)
                    statement.executeQuery().use { resultSet ->
                        if (resultSet.next()) normalizeSql(resultSet.getString("sql")) else null
                    }
                }
                SqliteIndexShape(
                    name = header.name,
                    unique = header.unique,
                    origin = header.origin,
                    partial = header.partial,
                    columns = indexColumns,
                    definition = definition,
                )
            }
            SqliteSchemaShape(columns = columns, indexes = indexes.sortedBy { it.name })
        }

    private fun normalizeSql(value: String?): String? = value?.trim()?.replace(Regex("\\s+"), " ")?.lowercase()

    private data class SqliteSchemaShape(
        val columns: List<SqliteColumnShape>,
        val indexes: List<SqliteIndexShape>,
    )

    private data class SqliteColumnShape(
        val ordinal: Int,
        val name: String,
        val type: String,
        val nullable: Boolean,
        val defaultValue: String?,
        val primaryKeyPosition: Int,
        val hidden: Int,
    )

    private data class SqliteIndexShape(
        val name: String,
        val unique: Boolean,
        val origin: String,
        val partial: Boolean,
        val columns: List<SqliteIndexColumnShape>,
        val definition: String?,
    )

    private data class SqliteIndexColumnShape(
        val ordinal: Int,
        val name: String?,
        val descending: Boolean,
        val collation: String?,
    )

    // -- save --

    @Test
    fun saveAndRetrieveByKid() =
        runTest {
            val record = testRecord(alias = "save-kid-test", kid = "save-kid-1")
            val saveResult = store.save(record)
            assertTrue(saveResult.isOk)
            assertEquals(record.id, saveResult.value.id)

            val findResult = store.findByKid("tenant-1", "save-kid-1")
            assertTrue(findResult.isOk)
            val found = findResult.value
            assertNotNull(found)
            assertEquals(record.alias, found.alias)
            assertEquals(record.kid, found.kid)
            assertEquals(record.providerId, found.providerId)
            assertEquals(Origin.MANAGED, found.origin)
        }

    @Test
    fun saveAndRetrieveByAlias() =
        runTest {
            val record = testRecord(alias = "save-alias-test", kid = "save-alias-kid")
            store.save(record)

            val findResult = store.findByAlias("tenant-1", "save-alias-test")
            assertTrue(findResult.isOk)
            val found = findResult.value
            assertNotNull(found)
            assertEquals("save-alias-test", found.alias)
            assertEquals("save-alias-kid", found.kid)
        }

    @Test
    fun findByAliasWithProviderId() =
        runTest {
            val record = testRecord(alias = "alias-prov-test", providerId = "prov-A", kid = "kid-prov-a")
            store.save(record)

            val withProvider = store.findByAlias("tenant-1", "alias-prov-test", "prov-A")
            assertTrue(withProvider.isOk)
            assertNotNull(withProvider.value)

            val wrongProvider = store.findByAlias("tenant-1", "alias-prov-test", "prov-B")
            assertTrue(wrongProvider.isOk)
            assertNull(wrongProvider.value)
        }

    @Test
    fun findByKidReturnsNullForMissing() =
        runTest {
            val result = store.findByKid("tenant-1", "nonexistent-kid")
            assertTrue(result.isOk)
            assertNull(result.value)
        }

    @Test
    fun findByAliasReturnsNullForMissing() =
        runTest {
            val result = store.findByAlias("tenant-1", "nonexistent-alias")
            assertTrue(result.isOk)
            assertNull(result.value)
        }

    // -- findAll --

    @Test
    fun findAllReturnsTenantRecords() =
        runTest {
            val tenantId = "tenant-findall"
            store.save(testRecord(tenantId = tenantId, alias = "fa-1", kid = "fa-kid-1"))
            store.save(testRecord(tenantId = tenantId, alias = "fa-2", kid = "fa-kid-2"))

            val result = store.findAll(tenantId)
            assertTrue(result.isOk)
            assertEquals(2, result.value.size)
        }

    // -- findAllFiltered --

    @Test
    fun findAllFilteredByProviderId() =
        runTest {
            val tenantId = "tenant-filter-prov"
            store.save(testRecord(tenantId = tenantId, alias = "fp-1", providerId = "provX", kid = "fp-kid-1"))
            store.save(testRecord(tenantId = tenantId, alias = "fp-2", providerId = "provY", kid = "fp-kid-2"))

            val filter = ManagedKeyReferenceFilter(providerId = "provX")
            val result = store.findAll(tenantId, filter)
            assertTrue(result.isOk)
            assertEquals(1, result.value.size)
            assertEquals("provX", result.value.first().providerId)
        }

    @Test
    fun findAllFilteredByAlias() =
        runTest {
            val tenantId = "tenant-filter-alias"
            store.save(testRecord(tenantId = tenantId, alias = "filt-a", kid = "filt-a-kid"))
            store.save(testRecord(tenantId = tenantId, alias = "filt-b", kid = "filt-b-kid"))

            val filter = ManagedKeyReferenceFilter(alias = "filt-a")
            val result = store.findAll(tenantId, filter)
            assertTrue(result.isOk)
            assertEquals(1, result.value.size)
            assertEquals("filt-a", result.value.first().alias)
        }

    @Test
    fun findAllFilteredByKid() =
        runTest {
            val tenantId = "tenant-filter-kid"
            store.save(testRecord(tenantId = tenantId, alias = "fk-1", kid = "fk-kid-match"))
            store.save(testRecord(tenantId = tenantId, alias = "fk-2", kid = "fk-kid-other"))

            val filter = ManagedKeyReferenceFilter(kid = "fk-kid-match")
            val result = store.findAll(tenantId, filter)
            assertTrue(result.isOk)
            assertEquals(1, result.value.size)
            assertEquals("fk-kid-match", result.value.first().kid)
        }

    @Test
    fun findAllFilteredByOrigin() =
        runTest {
            val tenantId = "tenant-filter-origin"
            store.save(testRecord(tenantId = tenantId, alias = "fo-1", kid = "fo-kid-1"))
            // All test records use Origin.MANAGED, so filter for MANAGED should return the record
            val filter = ManagedKeyReferenceFilter(origin = Origin.MANAGED)
            val result = store.findAll(tenantId, filter)
            assertTrue(result.isOk)
            assertTrue(result.value.isNotEmpty())
        }

    @Test
    fun findAllFilteredReturnsEmptyForNoMatch() =
        runTest {
            val tenantId = "tenant-filter-empty"
            store.save(testRecord(tenantId = tenantId, alias = "fe-1", kid = "fe-kid-1"))

            val filter = ManagedKeyReferenceFilter(kid = "nonexistent")
            val result = store.findAll(tenantId, filter)
            assertTrue(result.isOk)
            assertTrue(result.value.isEmpty())
        }

    // -- upsert --

    @Test
    fun upsertInsertsNewRecord() =
        runTest {
            val record = testRecord(alias = "upsert-new", kid = "upsert-new-kid")
            val result = store.upsert(record)
            assertTrue(result.isOk)

            val found = store.findByAlias("tenant-1", "upsert-new")
            assertTrue(found.isOk)
            assertNotNull(found.value)
            assertEquals("upsert-new-kid", found.value!!.kid)
        }

    @Test
    fun upsertUpdatesExistingRecord() =
        runTest {
            val record = testRecord(alias = "upsert-existing", kid = "kid-original")
            store.save(record)

            val updated = record.copy(kid = "kid-updated")
            val result = store.upsert(updated)
            assertTrue(result.isOk)

            val found = store.findByAlias("tenant-1", "upsert-existing", "software-provider")
            assertTrue(found.isOk)
            assertNotNull(found.value)
            assertEquals("kid-updated", found.value!!.kid)
        }

    // -- delete (soft) --

    @Test
    fun deleteByAliasAndProviderSoftDeletes() =
        runTest {
            val tenantId = "tenant-del"
            store.save(testRecord(tenantId = tenantId, alias = "del-1", kid = "del-kid-1"))

            val deleteResult = store.delete(tenantId, "del-1", "software-provider")
            assertTrue(deleteResult.isOk)
            assertTrue(deleteResult.value)

            // Record should no longer be found (soft-deleted)
            val findResult = store.findByAlias(tenantId, "del-1")
            assertTrue(findResult.isOk)
            assertNull(findResult.value)
        }

    @Test
    fun deleteByKidSoftDeletes() =
        runTest {
            val tenantId = "tenant-delkid"
            store.save(testRecord(tenantId = tenantId, alias = "delk-1", kid = "delk-kid-1"))

            val deleteResult = store.deleteByKid(tenantId, "delk-kid-1")
            assertTrue(deleteResult.isOk)
            assertTrue(deleteResult.value)

            val findResult = store.findByKid(tenantId, "delk-kid-1")
            assertTrue(findResult.isOk)
            assertNull(findResult.value)
        }

    @Test
    fun softDeletedRecordsNotInFindAll() =
        runTest {
            val tenantId = "tenant-del-findall"
            store.save(testRecord(tenantId = tenantId, alias = "dfa-1", kid = "dfa-kid-1"))
            store.save(testRecord(tenantId = tenantId, alias = "dfa-2", kid = "dfa-kid-2"))

            store.delete(tenantId, "dfa-1", "software-provider")

            val result = store.findAll(tenantId)
            assertTrue(result.isOk)
            assertEquals(1, result.value.size)
            assertEquals("dfa-2", result.value.first().alias)
        }

    @Test
    fun softDeletedRecordNotInExists() =
        runTest {
            val tenantId = "tenant-del-exists"
            store.save(testRecord(tenantId = tenantId, alias = "de-1", kid = "de-kid-1"))

            val beforeDelete = store.exists(tenantId, "de-1", "software-provider")
            assertTrue(beforeDelete.isOk)
            assertTrue(beforeDelete.value)

            store.delete(tenantId, "de-1", "software-provider")

            val afterDelete = store.exists(tenantId, "de-1", "software-provider")
            assertTrue(afterDelete.isOk)
            assertFalse(afterDelete.value)
        }

    // -- exists --

    @Test
    fun existsReturnsTrueForExistingRecord() =
        runTest {
            val tenantId = "tenant-exists"
            store.save(testRecord(tenantId = tenantId, alias = "ex-1", kid = "ex-kid-1"))

            val result = store.exists(tenantId, "ex-1", "software-provider")
            assertTrue(result.isOk)
            assertTrue(result.value)
        }

    @Test
    fun existsReturnsFalseForNonExisting() =
        runTest {
            val result = store.exists("tenant-exists", "nonexistent", "software-provider")
            assertTrue(result.isOk)
            assertFalse(result.value)
        }

    // -- tenant isolation --

    @Test
    fun tenantIsolationFindByKid() =
        runTest {
            store.save(testRecord(tenantId = "iso-t1", alias = "iso-1", kid = "iso-kid-shared"))
            store.save(testRecord(tenantId = "iso-t2", alias = "iso-1", kid = "iso-kid-shared"))

            val t1Result = store.findByKid("iso-t1", "iso-kid-shared")
            assertTrue(t1Result.isOk)
            assertNotNull(t1Result.value)
            assertEquals("iso-t1", t1Result.value!!.tenantId)

            val t2Result = store.findByKid("iso-t2", "iso-kid-shared")
            assertTrue(t2Result.isOk)
            assertNotNull(t2Result.value)
            assertEquals("iso-t2", t2Result.value!!.tenantId)
        }

    @Test
    fun tenantIsolationFindAll() =
        runTest {
            val t1 = "iso-findall-t1"
            val t2 = "iso-findall-t2"
            store.save(testRecord(tenantId = t1, alias = "ifa-1", kid = "ifa-kid-1"))
            store.save(testRecord(tenantId = t1, alias = "ifa-2", kid = "ifa-kid-2"))
            store.save(testRecord(tenantId = t2, alias = "ifa-3", kid = "ifa-kid-3"))

            val t1Result = store.findAll(t1)
            assertTrue(t1Result.isOk)
            assertEquals(2, t1Result.value.size)
            assertTrue(t1Result.value.all { it.tenantId == t1 })

            val t2Result = store.findAll(t2)
            assertTrue(t2Result.isOk)
            assertEquals(1, t2Result.value.size)
            assertEquals(t2, t2Result.value.first().tenantId)
        }

    @Test
    fun tenantIsolationDeleteDoesNotAffectOtherTenants() =
        runTest {
            val t1 = "iso-del-t1"
            val t2 = "iso-del-t2"
            store.save(testRecord(tenantId = t1, alias = "id-1", kid = "id-kid-1"))
            store.save(testRecord(tenantId = t2, alias = "id-1", kid = "id-kid-1"))

            store.delete(t1, "id-1", "software-provider")

            val t1Result = store.findByAlias(t1, "id-1")
            assertTrue(t1Result.isOk)
            assertNull(t1Result.value)

            val t2Result = store.findByAlias(t2, "id-1")
            assertTrue(t2Result.isOk)
            assertNotNull(t2Result.value)
        }

    @Test
    fun tenantIsolationExistsScoped() =
        runTest {
            store.save(testRecord(tenantId = "iso-ex-t1", alias = "iex-1", kid = "iex-kid-1"))

            val sameResult = store.exists("iso-ex-t1", "iex-1", "software-provider")
            assertTrue(sameResult.isOk)
            assertTrue(sameResult.value)

            val otherResult = store.exists("iso-ex-t2", "iex-1", "software-provider")
            assertTrue(otherResult.isOk)
            assertFalse(otherResult.value)
        }

    // -- unique constraint enforcement --

    @Test
    fun saveDuplicateAliasAndProviderFails() =
        runTest {
            val tenantId = "tenant-dup"
            store.save(testRecord(tenantId = tenantId, alias = "dup-1", providerId = "prov-dup", kid = "dup-kid-1"))

            val duplicate = testRecord(tenantId = tenantId, alias = "dup-1", providerId = "prov-dup", kid = "dup-kid-2")
            val result = store.save(duplicate)
            assertTrue(result.isErr)
        }

    @Test
    fun saveSameAliasAndDifferentProviderSucceeds() =
        runTest {
            val tenantId = "tenant-dup-ok"
            store.save(testRecord(tenantId = tenantId, alias = "dupok-1", providerId = "prov-A", kid = "dupok-kid-a"))

            val differentProvider = testRecord(tenantId = tenantId, alias = "dupok-1", providerId = "prov-B", kid = "dupok-kid-b")
            val result = store.save(differentProvider)
            assertTrue(result.isOk)
        }
}
