/*
 * Copyright 2023-2026 Sphereon International B.V.
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
 */

package com.sphereon.credential.claims.mapper.impl.store

import com.sphereon.credential.claims.mapper.api.model.ClaimMapping
import com.sphereon.credential.claims.mapper.api.model.ClaimMappingConfiguration
import com.sphereon.credential.claims.mapper.api.model.CredentialMapping
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClaimMappingConfigurationStoreTest {
    private val store = InMemoryClaimMappingConfigurationStore()

    private fun createTestConfig(
        id: String = "test-config",
        dcqlQueryId: String? = "test-query",
    ): ClaimMappingConfiguration =
        ClaimMappingConfiguration(
            id = id,
            name = "Test Configuration",
            credentialMappings =
                listOf(
                    CredentialMapping(
                        credentialId = "pid",
                        claimMappings =
                            listOf(
                                ClaimMapping(
                                    sourceClaimPath = listOf("given_name"),
                                    targetClaimPath = listOf("given_name"),
                                ),
                                ClaimMapping(
                                    sourceClaimPath = listOf("family_name"),
                                    targetClaimPath = listOf("family_name"),
                                ),
                            ),
                    ),
                ),
            queryId = dcqlQueryId,
        )

    @Test
    fun `save and findById should work`() =
        runTest {
            val config = createTestConfig()

            val saveResult = store.save(config)
            assertTrue(saveResult.isOk)
            assertEquals(config, saveResult.value)

            val findResult = store.findById(config.id)
            assertTrue(findResult.isOk)
            assertNotNull(findResult.value)
            assertEquals(config, findResult.value)
        }

    @Test
    fun `findById should return null for non-existent config`() =
        runTest {
            val result = store.findById("non-existent")
            assertTrue(result.isOk)
            assertNull(result.value)
        }

    @Test
    fun `findByQueryId should work`() =
        runTest {
            val config = createTestConfig()
            store.save(config)

            val findResult = store.findByQueryId(config.queryId!!)
            assertTrue(findResult.isOk)
            assertNotNull(findResult.value)
            assertEquals(config, findResult.value)
        }

    @Test
    fun `config without queryId should work`() =
        runTest {
            val config = createTestConfig(dcqlQueryId = null)
            store.save(config)

            val findResult = store.findById(config.id)
            assertTrue(findResult.isOk)
            assertNotNull(findResult.value)
            assertNull(findResult.value?.queryId)
        }

    @Test
    fun `findAll should return all configs`() =
        runTest {
            val config1 = createTestConfig("config-1", "query-1")
            val config2 = createTestConfig("config-2", "query-2")

            store.save(config1)
            store.save(config2)

            val result = store.findAll()
            assertTrue(result.isOk)
            assertEquals(2, result.value.size)
            assertTrue(result.value.contains(config1))
            assertTrue(result.value.contains(config2))
        }

    @Test
    fun `delete should remove config`() =
        runTest {
            val config = createTestConfig()
            store.save(config)

            val deleteResult = store.delete(config.id)
            assertTrue(deleteResult.isOk)
            assertTrue(deleteResult.value)

            val findResult = store.findById(config.id)
            assertTrue(findResult.isOk)
            assertNull(findResult.value)
        }

    @Test
    fun `delete should return false for non-existent config`() =
        runTest {
            val result = store.delete("non-existent")
            assertTrue(result.isOk)
            assertFalse(result.value)
        }

    @Test
    fun `exists should return true for existing config`() =
        runTest {
            val config = createTestConfig()
            store.save(config)

            val result = store.exists(config.id)
            assertTrue(result.isOk)
            assertTrue(result.value)
        }

    @Test
    fun `exists should return false for non-existent config`() =
        runTest {
            val result = store.exists("non-existent")
            assertTrue(result.isOk)
            assertFalse(result.value)
        }

    @Test
    fun `deleteAll should remove all configs`() =
        runTest {
            store.save(createTestConfig("config-1", "query-1"))
            store.save(createTestConfig("config-2", "query-2"))

            val deleteResult = store.deleteAll()
            assertTrue(deleteResult.isOk)
            assertEquals(2, deleteResult.value)

            val findResult = store.findAll()
            assertTrue(findResult.isOk)
            assertTrue(findResult.value.isEmpty())
        }

    @Test
    fun `save should update existing config`() =
        runTest {
            val config = createTestConfig()
            store.save(config)

            val updatedConfig = config.copy(name = "Updated Name")
            store.save(updatedConfig)

            val findResult = store.findById(config.id)
            assertTrue(findResult.isOk)
            assertEquals("Updated Name", findResult.value?.name)
        }

    @Test
    fun `save should update queryId index when query changes`() =
        runTest {
            val config = createTestConfig()
            store.save(config)

            val updatedConfig = config.copy(queryId = "new-query-id")
            store.save(updatedConfig)

            // Old query ID should no longer find it
            val oldQueryResult = store.findByQueryId("test-query")
            assertTrue(oldQueryResult.isOk)
            assertNull(oldQueryResult.value)

            // New query ID should find it
            val newQueryResult = store.findByQueryId("new-query-id")
            assertTrue(newQueryResult.isOk)
            assertNotNull(newQueryResult.value)
        }
}
