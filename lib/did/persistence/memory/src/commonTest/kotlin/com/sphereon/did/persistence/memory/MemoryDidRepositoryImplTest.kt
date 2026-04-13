/*
 * © 2025 Sphereon International B.V.
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

package com.sphereon.did.persistence.memory

import com.sphereon.did.manager.DidRole
import com.sphereon.did.persistence.DidKeyMappingRecord
import com.sphereon.did.persistence.DidRecord
import com.sphereon.did.persistence.DidRecordFilter
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for the in-memory DID repository implementation.
 */
class MemoryDidRepositoryImplTest {

    /**
     * Test saving and finding a DID record by DID.
     */
    @Test
    fun shouldSaveAndFindByDid(): TestResult = runTest {
        val repository = MemoryDidRepositoryImpl()

        val record = DidRecord(
            id = "record-1",
            did = "did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK",
            method = "key",
            alias = "my-key",
            documentJson = null,
            role = DidRole.MANAGED,
            deactivated = false,
            createdAt = "2025-01-01T00:00:00Z",
            updatedAt = "2025-01-01T00:00:00Z"
        )

        // Save
        val saveResult = repository.save(record)
        assertTrue(saveResult.isOk, "Save should succeed")

        // Find by DID
        val findResult = repository.findByDid(record.did)
        assertTrue(findResult.isOk)

        val found = findResult.getOrThrow()
        assertNotNull(found, "Should find the saved record")
        assertEquals(record.id, found.id)
        assertEquals(record.did, found.did)
        assertEquals(record.method, found.method)
        assertEquals(record.alias, found.alias)
    }

    /**
     * Test finding a DID record by alias.
     */
    @Test
    fun shouldFindByAlias(): TestResult = runTest {
        val repository = MemoryDidRepositoryImpl()

        val record = DidRecord(
            id = "record-1",
            did = "did:key:z6Mk123",
            method = "key",
            alias = "test-alias",
            documentJson = null,
            role = DidRole.MANAGED,
            deactivated = false,
            createdAt = "2025-01-01T00:00:00Z",
            updatedAt = "2025-01-01T00:00:00Z"
        )

        repository.save(record)

        val findResult = repository.findByAlias("test-alias")
        assertTrue(findResult.isOk)

        val found = findResult.getOrThrow()
        assertNotNull(found)
        assertEquals(record.did, found.did)
    }

    /**
     * Test that duplicate DIDs are rejected.
     */
    @Test
    fun shouldRejectDuplicateDid(): TestResult = runTest {
        val repository = MemoryDidRepositoryImpl()

        val record1 = DidRecord(
            id = "record-1",
            did = "did:key:z6Mk123",
            method = "key",
            alias = null,
            documentJson = null,
            role = DidRole.MANAGED,
            deactivated = false,
            createdAt = "2025-01-01T00:00:00Z",
            updatedAt = "2025-01-01T00:00:00Z"
        )

        val record2 = DidRecord(
            id = "record-2",
            did = "did:key:z6Mk123", // Same DID
            method = "key",
            alias = null,
            documentJson = null,
            role = DidRole.MANAGED,
            deactivated = false,
            createdAt = "2025-01-01T00:00:00Z",
            updatedAt = "2025-01-01T00:00:00Z"
        )

        repository.save(record1)
        val result = repository.save(record2)

        assertTrue(result.isErr, "Should reject duplicate DID")
    }

    /**
     * Test updating a DID record.
     */
    @Test
    fun shouldUpdateRecord(): TestResult = runTest {
        val repository = MemoryDidRepositoryImpl()

        val record = DidRecord(
            id = "record-1",
            did = "did:key:z6Mk123",
            method = "key",
            alias = "old-alias",
            documentJson = null,
            role = DidRole.MANAGED,
            deactivated = false,
            createdAt = "2025-01-01T00:00:00Z",
            updatedAt = "2025-01-01T00:00:00Z"
        )

        repository.save(record)

        val updatedRecord = record.copy(
            alias = "new-alias",
            updatedAt = "2025-01-02T00:00:00Z"
        )

        val updateResult = repository.update(updatedRecord)
        assertTrue(updateResult.isOk, "Update should succeed")

        // Verify the update
        val findResult = repository.findByDid(record.did)
        assertTrue(findResult.isOk)
        assertEquals("new-alias", findResult.getOrThrow()?.alias)

        // Should be findable by new alias
        val findByAliasResult = repository.findByAlias("new-alias")
        assertTrue(findByAliasResult.isOk)
        assertNotNull(findByAliasResult.getOrThrow())

        // Old alias should not find anything
        val oldAliasResult = repository.findByAlias("old-alias")
        assertTrue(oldAliasResult.isOk)
        assertNull(oldAliasResult.getOrThrow())
    }

    /**
     * Test deleting a DID record.
     */
    @Test
    fun shouldDeleteRecord(): TestResult = runTest {
        val repository = MemoryDidRepositoryImpl()

        val record = DidRecord(
            id = "record-1",
            did = "did:key:z6Mk123",
            method = "key",
            alias = "test-alias",
            documentJson = null,
            role = DidRole.MANAGED,
            deactivated = false,
            createdAt = "2025-01-01T00:00:00Z",
            updatedAt = "2025-01-01T00:00:00Z"
        )

        repository.save(record)

        // Delete
        val deleteResult = repository.delete(record.did)
        assertTrue(deleteResult.isOk, "Delete should succeed")

        // Should not find by DID anymore
        val findResult = repository.findByDid(record.did)
        assertTrue(findResult.isOk)
        assertNull(findResult.getOrThrow(), "Should not find deleted record")

        // Should not find by alias anymore
        val aliasResult = repository.findByAlias("test-alias")
        assertTrue(aliasResult.isOk)
        assertNull(aliasResult.getOrThrow())
    }

    /**
     * Test findAll with no filter.
     */
    @Test
    fun shouldFindAllRecords(): TestResult = runTest {
        val repository = MemoryDidRepositoryImpl()

        // Add multiple records
        repeat(3) { i ->
            repository.save(DidRecord(
                id = "record-$i",
                did = "did:key:z6Mk$i",
                method = "key",
                alias = null,
                documentJson = null,
                role = DidRole.MANAGED,
                deactivated = false,
                createdAt = "2025-01-01T00:00:00Z",
                updatedAt = "2025-01-01T00:00:00Z"
            ))
        }

        val findResult = repository.findAll()
        assertTrue(findResult.isOk)
        assertEquals(3, findResult.getOrThrow().size)
    }

    /**
     * Test findAll with method filter.
     */
    @Test
    fun shouldFilterByMethod(): TestResult = runTest {
        val repository = MemoryDidRepositoryImpl()

        repository.save(DidRecord(
            id = "record-1",
            did = "did:key:z6Mk1",
            method = "key",
            alias = null,
            documentJson = null,
            role = DidRole.MANAGED,
            deactivated = false,
            createdAt = "2025-01-01T00:00:00Z",
            updatedAt = "2025-01-01T00:00:00Z"
        ))

        repository.save(DidRecord(
            id = "record-2",
            did = "did:jwk:abc123",
            method = "jwk",
            alias = null,
            documentJson = null,
            role = DidRole.MANAGED,
            deactivated = false,
            createdAt = "2025-01-01T00:00:00Z",
            updatedAt = "2025-01-01T00:00:00Z"
        ))

        val keyRecords = repository.findAll(DidRecordFilter(method = "key"))
        assertTrue(keyRecords.isOk)
        val keyRecordsList = keyRecords.getOrThrow()
        assertEquals(1, keyRecordsList.size)
        assertEquals("key", keyRecordsList.first().method)
    }

    /**
     * Test findAll excluding deactivated records.
     */
    @Test
    fun shouldExcludeDeactivatedByDefault(): TestResult = runTest {
        val repository = MemoryDidRepositoryImpl()

        repository.save(DidRecord(
            id = "record-1",
            did = "did:key:z6Mk1",
            method = "key",
            alias = null,
            documentJson = null,
            role = DidRole.MANAGED,
            deactivated = false,
            createdAt = "2025-01-01T00:00:00Z",
            updatedAt = "2025-01-01T00:00:00Z"
        ))

        repository.save(DidRecord(
            id = "record-2",
            did = "did:key:z6Mk2",
            method = "key",
            alias = null,
            documentJson = null,
            role = DidRole.MANAGED,
            deactivated = true, // Deactivated
            createdAt = "2025-01-01T00:00:00Z",
            updatedAt = "2025-01-01T00:00:00Z"
        ))

        // Default filter excludes deactivated
        val activeRecords = repository.findAll(DidRecordFilter())
        assertTrue(activeRecords.isOk)
        assertEquals(1, activeRecords.getOrThrow().size)

        // Include deactivated
        val allRecords = repository.findAll(DidRecordFilter(includeDeactivated = true))
        assertTrue(allRecords.isOk)
        assertEquals(2, allRecords.getOrThrow().size)
    }

    /**
     * Test key mapping operations.
     */
    @Test
    fun shouldManageKeyMappings(): TestResult = runTest {
        val repository = MemoryDidRepositoryImpl()

        val record = DidRecord(
            id = "record-1",
            did = "did:key:z6Mk123",
            method = "key",
            alias = null,
            documentJson = null,
            role = DidRole.MANAGED,
            deactivated = false,
            createdAt = "2025-01-01T00:00:00Z",
            updatedAt = "2025-01-01T00:00:00Z"
        )
        repository.save(record)

        val mapping = DidKeyMappingRecord(
            id = "mapping-1",
            didRecordId = record.id,
            verificationMethodId = "#key-1",
            kmsKeyAlias = "my-key-alias",
            kmsProviderId = "local",
            purposesJson = """["authentication","assertionMethod"]"""
        )

        // Save mapping
        val saveResult = repository.saveKeyMapping(record.id, mapping)
        assertTrue(saveResult.isOk, "Save key mapping should succeed")

        // Get mappings
        val getMappingsResult = repository.getKeyMappings(record.id)
        assertTrue(getMappingsResult.isOk)
        val mappings = getMappingsResult.getOrThrow()
        assertEquals(1, mappings.size)
        assertEquals(mapping.id, mappings.first().id)

        // Delete mapping
        val deleteResult = repository.deleteKeyMapping(mapping.id)
        assertTrue(deleteResult.isOk, "Delete key mapping should succeed")

        // Verify deleted
        val afterDelete = repository.getKeyMappings(record.id)
        assertTrue(afterDelete.isOk)
        assertEquals(0, afterDelete.getOrThrow().size)
    }

    /**
     * Test clear operation.
     */
    @Test
    fun shouldClearAllData(): TestResult = runTest {
        val repository = MemoryDidRepositoryImpl()

        // Add some records
        repeat(3) { i ->
            repository.save(DidRecord(
                id = "record-$i",
                did = "did:key:z6Mk$i",
                method = "key",
                alias = null,
                documentJson = null,
                role = DidRole.MANAGED,
                deactivated = false,
                createdAt = "2025-01-01T00:00:00Z",
                updatedAt = "2025-01-01T00:00:00Z"
            ))
        }

        assertEquals(3, repository.count())

        repository.clear()

        assertEquals(0, repository.count())
    }
}
