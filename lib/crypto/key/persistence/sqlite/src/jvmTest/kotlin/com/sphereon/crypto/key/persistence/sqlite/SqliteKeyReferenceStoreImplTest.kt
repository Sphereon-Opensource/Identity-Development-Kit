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

import app.cash.sqldelight.driver.jdbc.asJdbcDriver
import com.sphereon.core.api.model.Origin
import com.sphereon.crypto.core.ManagedKeyReferenceFilter
import com.sphereon.crypto.key.persistence.KeyReferenceRecord
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
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

    @BeforeAll
    fun setup() {
        val config =
            HikariConfig().apply {
                jdbcUrl = "jdbc:sqlite:file:keyref_test?mode=memory&cache=shared"
                maximumPoolSize = 1
            }
        dataSource = HikariDataSource(config)
        val driver = dataSource.asJdbcDriver()
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
    ) = KeyReferenceRecord(
        id = Uuid.random().toString(),
        tenantId = tenantId,
        alias = alias,
        kid = kid,
        providerId = providerId,
        origin = Origin.MANAGED,
        createdAt = Clock.System.now(),
        updatedAt = Clock.System.now(),
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
