/*
 * © 2026 Sphereon International B.V.
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

package com.sphereon.identity.reconciliation.impl.store

import com.sphereon.attribute.mapping.AttributeMapping
import com.sphereon.identity.reconciliation.model.ReconciliationProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InMemoryReconciliationProviderStoreTest {
    private fun createStore() = InMemoryReconciliationProviderStore()

    private fun createProvider(
        id: String = "provider-1",
        name: String = "Test IdP",
        oidcClientId: String = "client-123",
    ) = ReconciliationProvider(
        id = id,
        name = name,
        oidcClientId = oidcClientId,
        attributeMappings = listOf(AttributeMapping(source = "sub", target = "externalId")),
        identifierAttributeName = "sub",
    )

    @Test
    fun saveAndFindById() =
        runTest {
            val store = createStore()
            val provider = createProvider()

            store.save(provider)

            val found = store.findById("provider-1")
            assertNotNull(found)
            assertEquals("Test IdP", found.name)
            assertEquals("client-123", found.oidcClientId)
        }

    @Test
    fun findByIdReturnsNullWhenNotFound() =
        runTest {
            val store = createStore()
            assertNull(store.findById("nonexistent"))
        }

    @Test
    fun findAllReturnsAllProviders() =
        runTest {
            val store = createStore()
            store.save(createProvider(id = "p1", name = "Provider 1"))
            store.save(createProvider(id = "p2", name = "Provider 2"))

            val all = store.findAll()
            assertEquals(2, all.size)
        }

    @Test
    fun findAllReturnsEmptyWhenNoProviders() =
        runTest {
            val store = createStore()
            assertTrue(store.findAll().isEmpty())
        }

    @Test
    fun saveOverwritesExistingProvider() =
        runTest {
            val store = createStore()
            store.save(createProvider(id = "p1", name = "Original"))
            store.save(createProvider(id = "p1", name = "Updated"))

            val found = store.findById("p1")
            assertNotNull(found)
            assertEquals("Updated", found.name)
            assertEquals(1, store.findAll().size)
        }

    @Test
    fun deleteProvider() =
        runTest {
            val store = createStore()
            store.save(createProvider())

            val deleted = store.delete("provider-1")
            assertTrue(deleted)
            assertNull(store.findById("provider-1"))
        }

    @Test
    fun deleteReturnsFalseWhenNotFound() =
        runTest {
            val store = createStore()
            assertTrue(!store.delete("nonexistent"))
        }

    @Test
    fun providerFieldsArePreserved() =
        runTest {
            val store = createStore()
            val provider = createProvider()
            store.save(provider)

            val found = store.findById("provider-1")!!
            assertEquals(listOf(AttributeMapping(source = "sub", target = "externalId")), found.attributeMappings)
            assertEquals("sub", found.identifierAttributeName)
            assertEquals("client-123", found.oidcClientId)
            assertTrue(found.enabled)
        }
}
