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
 */

package com.sphereon.core.defaults.service

import com.sphereon.core.api.service.ServiceCommandGroupDescription
import com.sphereon.core.api.service.ServiceCommandGroupDescriptorProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DefaultServiceCommandGroupCatalogTest {

    // ========== Basic catalog construction ==========

    @Test
    fun catalogCollectsGroupDescriptors() {
        // Given
        val provider1 = stubProvider("kms.keys", "kms", "keys", "KMS Keys", listOf("kms.keys.get", "kms.keys.list"))
        val provider2 = stubProvider("party.manager", "party", "manager", "Party Manager", listOf("party.manager.create"))
        val catalog = DefaultServiceCommandGroupCatalog(setOf(provider1, provider2))

        // Then
        assertEquals(2, catalog.groups.size)
        assertEquals("kms.keys", catalog.groups[0].groupId)
        assertEquals("party.manager", catalog.groups[1].groupId)
    }

    @Test
    fun catalogSortsGroupsByGroupId() {
        // Given
        val providerZ = stubProvider("z.last", "z", "last", "Last", listOf("z.last.action"))
        val providerA = stubProvider("a.first", "a", "first", "First", listOf("a.first.action"))
        val catalog = DefaultServiceCommandGroupCatalog(setOf(providerZ, providerA))

        // Then
        assertEquals("a.first", catalog.groups[0].groupId)
        assertEquals("z.last", catalog.groups[1].groupId)
    }

    @Test
    fun catalogFiltersOutNoOpStub() {
        // Given
        val realProvider = stubProvider("kms.keys", "kms", "keys", "KMS Keys", listOf("kms.keys.get"))
        val noOpProvider = NoOpServiceCommandGroupDescriptorProvider()
        val catalog = DefaultServiceCommandGroupCatalog(setOf(realProvider, noOpProvider))

        // Then
        assertEquals(1, catalog.groups.size)
        assertEquals("kms.keys", catalog.groups[0].groupId)
    }

    @Test
    fun catalogHandlesEmptyProviderSet() {
        // Given — only NoOp stub
        val catalog = DefaultServiceCommandGroupCatalog(setOf(NoOpServiceCommandGroupDescriptorProvider()))

        // Then
        assertTrue(catalog.groups.isEmpty())
    }

    // ========== findByModule ==========

    @Test
    fun findByModuleReturnsMatchingGroups() {
        // Given
        val keysProvider = stubProvider("kms.keys", "kms", "keys", "Keys", listOf("kms.keys.get"))
        val sigProvider = stubProvider("kms.signatures", "kms", "signatures", "Sigs", listOf("kms.signatures.create"))
        val partyProvider = stubProvider("party.manager", "party", "manager", "Party", listOf("party.manager.create"))
        val catalog = DefaultServiceCommandGroupCatalog(setOf(keysProvider, sigProvider, partyProvider))

        // When
        val kmsGroups = catalog.findByModule("kms")

        // Then
        assertEquals(2, kmsGroups.size)
        assertTrue(kmsGroups.any { it.groupId == "kms.keys" })
        assertTrue(kmsGroups.any { it.groupId == "kms.signatures" })
    }

    @Test
    fun findByModuleReturnsEmptyForUnknownModule() {
        // Given
        val provider = stubProvider("kms.keys", "kms", "keys", "Keys", listOf("kms.keys.get"))
        val catalog = DefaultServiceCommandGroupCatalog(setOf(provider))

        // When/Then
        assertTrue(catalog.findByModule("unknown").isEmpty())
    }

    // ========== findByGroupId ==========

    @Test
    fun findByGroupIdReturnsMatchingGroup() {
        // Given
        val provider = stubProvider("kms.keys", "kms", "keys", "KMS Keys", listOf("kms.keys.get"))
        val catalog = DefaultServiceCommandGroupCatalog(setOf(provider))

        // When
        val group = catalog.findByGroupId("kms.keys")

        // Then
        assertNotNull(group)
        assertEquals("KMS Keys", group.displayName)
    }

    @Test
    fun findByGroupIdReturnsNullForUnknownGroup() {
        // Given
        val catalog = DefaultServiceCommandGroupCatalog(setOf(NoOpServiceCommandGroupDescriptorProvider()))

        // When/Then
        assertNull(catalog.findByGroupId("unknown.group"))
    }

    // ========== isCommandEnabled ==========

    @Test
    fun isCommandEnabledReturnsTrueForEnabledGroup() {
        // Given
        val provider = stubProvider("kms.keys", "kms", "keys", "Keys", listOf("kms.keys.get"), isEnabled = true)
        val catalog = DefaultServiceCommandGroupCatalog(setOf(provider))

        // Then
        assertTrue(catalog.isCommandEnabled("kms.keys.get"))
    }

    @Test
    fun isCommandEnabledReturnsFalseForDisabledGroup() {
        // Given
        val provider = stubProvider("kms.keys", "kms", "keys", "Keys", listOf("kms.keys.get"), isEnabled = false)
        val catalog = DefaultServiceCommandGroupCatalog(setOf(provider))

        // Then
        assertFalse(catalog.isCommandEnabled("kms.keys.get"))
    }

    @Test
    fun isCommandEnabledReturnsTrueForCommandNotInAnyGroup() {
        // Given
        val catalog = DefaultServiceCommandGroupCatalog(setOf(NoOpServiceCommandGroupDescriptorProvider()))

        // Then — unknown commands are considered enabled (not blocked by any group)
        assertTrue(catalog.isCommandEnabled("unknown.command"))
    }

    // ========== Helpers ==========

    private fun stubProvider(
        groupId: String,
        module: String,
        service: String,
        displayName: String,
        commandIds: List<String>,
        isEnabled: Boolean = true
    ): ServiceCommandGroupDescriptorProvider = object : ServiceCommandGroupDescriptorProvider {
        override val groupId = groupId
        override fun describe() = ServiceCommandGroupDescription(
            groupId = groupId,
            module = module,
            service = service,
            displayName = displayName,
            commandIds = commandIds,
            isEnabled = isEnabled
        )
    }
}
