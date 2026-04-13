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

package com.sphereon.crypto.key.persistence.impl

import com.sphereon.core.api.Ok
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.model.Origin
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.ManagedKeyInfoType
import com.sphereon.crypto.core.ManagedKeyReference
import com.sphereon.crypto.core.ResolvedKeyInfoType
import com.sphereon.crypto.core.kms.ManagedKeyStoreMode
import com.sphereon.crypto.core.kms.ManagedKeyStoreModeResolver
import com.sphereon.crypto.key.persistence.KeyReferenceRecord
import com.sphereon.crypto.key.persistence.KeyReferenceStore
import com.sphereon.crypto.kms.keystore.managed.ManagedKeyStoreWithProviderLookups
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock

class ManagedKeyStoreSelectorTest {
    private val iteratingStore = mockk<ManagedKeyStoreWithProviderLookups>()
    private val keyReferenceStore = mockk<KeyReferenceStore>()
    private val modeResolver = mockk<ManagedKeyStoreModeResolver>()
    private val registrar = mockk<ManagedKeyReferenceRegistrar>()
    private val execution =
        mockk<SessionExecution> {
            every { sessionContext } returns
                mockk {
                    every { context } returns
                        mockk {
                            every { tenant } returns
                                mockk {
                                    every { tenantId } returns "test-tenant"
                                }
                        }
                }
        }

    private val selector =
        ManagedKeyStoreSelector(
            iteratingStore = iteratingStore,
            keyReferenceStore = keyReferenceStore,
            modeResolver = modeResolver,
            registrar = registrar,
            execution = execution,
        )

    private val now = Clock.System.now()

    private val sampleRecord =
        KeyReferenceRecord(
            id = "rec-1",
            tenantId = "test-tenant",
            alias = "key-alias",
            kid = "kid-1",
            providerId = "provider-1",
            origin = Origin.MANAGED,
            createdAt = now,
            updatedAt = now,
        )

    private val sampleReference =
        ManagedKeyReference(
            alias = "key-alias",
            kid = "kid-1",
            providerId = "provider-1",
            origin = Origin.MANAGED,
        )

    @Test
    fun listKeysInIteratingMode() =
        runTest {
            every { modeResolver.resolve() } returns ManagedKeyStoreMode.ITERATING
            val expected = arrayOf(sampleReference)
            coEvery { iteratingStore.listKeys() } returns expected

            val result = selector.listKeys()

            assertContentEquals(expected, result)
            coVerify(exactly = 1) { iteratingStore.listKeys() }
            coVerify(exactly = 0) { keyReferenceStore.findAll(any(), any()) }
        }

    @Test
    fun listKeysInPersistentMode() =
        runTest {
            every { modeResolver.resolve() } returns ManagedKeyStoreMode.PERSISTENT
            every { keyReferenceStore.isAvailable } returns true
            coEvery { keyReferenceStore.findAll("test-tenant", null) } returns Ok(listOf(sampleRecord))

            val result = selector.listKeys()

            assertEquals(1, result.size)
            assertEquals("key-alias", result[0].alias)
            assertEquals("kid-1", result[0].kid)
            assertEquals("provider-1", result[0].providerId)
            coVerify(exactly = 0) { iteratingStore.listKeys() }
        }

    @Test
    fun listKeysAutoSelectsPersistentWhenAvailable() =
        runTest {
            every { modeResolver.resolve() } returns ManagedKeyStoreMode.AUTO
            every { keyReferenceStore.isAvailable } returns true
            coEvery { keyReferenceStore.findAll("test-tenant", null) } returns Ok(listOf(sampleRecord))

            val result = selector.listKeys()

            assertEquals(1, result.size)
            assertEquals("key-alias", result[0].alias)
            coVerify(exactly = 0) { iteratingStore.listKeys() }
        }

    @Test
    fun listKeysAutoFallsBackToIterating() =
        runTest {
            every { modeResolver.resolve() } returns ManagedKeyStoreMode.AUTO
            every { keyReferenceStore.isAvailable } returns false
            val expected = arrayOf(sampleReference)
            coEvery { iteratingStore.listKeys() } returns expected

            val result = selector.listKeys()

            assertContentEquals(expected, result)
            coVerify(exactly = 1) { iteratingStore.listKeys() }
            coVerify(exactly = 0) { keyReferenceStore.findAll(any(), any()) }
        }

    @Test
    fun persistentModeFailsWhenStoreUnavailable() =
        runTest {
            every { modeResolver.resolve() } returns ManagedKeyStoreMode.PERSISTENT
            every { keyReferenceStore.isAvailable } returns false

            assertFailsWith<IllegalStateException> {
                selector.listKeys()
            }
        }

    @Test
    fun getKeyAlwaysDelegatesToIteratingStore() =
        runTest {
            val keyInfo = mockk<KeyInfoType<*>>()
            val managedKeyInfo = mockk<ManagedKeyInfoType<*>>()
            coEvery { iteratingStore.getKey(keyInfo) } returns managedKeyInfo

            // Mode should not matter for getKey
            every { modeResolver.resolve() } returns ManagedKeyStoreMode.PERSISTENT

            val result = selector.getKey(keyInfo)

            assertEquals(managedKeyInfo, result)
            coVerify(exactly = 1) { iteratingStore.getKey(keyInfo) }
        }

    @Test
    fun storeKeyDelegatesToIteratingAndIndexes() =
        runTest {
            val keyInfo = mockk<ResolvedKeyInfoType<*>>()
            val managedKeyInfo = mockk<ManagedKeyInfoType<*>>()
            coEvery { iteratingStore.storeKey(keyInfo, "provider-1", "alias-1", null) } returns managedKeyInfo
            coEvery { registrar.indexManagedKey(managedKeyInfo) } returns Ok(null)

            val result = selector.storeKey(keyInfo, "provider-1", "alias-1", null)

            assertEquals(managedKeyInfo, result)
            coVerify(exactly = 1) { iteratingStore.storeKey(keyInfo, "provider-1", "alias-1", null) }
            coVerify(exactly = 1) { registrar.indexManagedKey(managedKeyInfo) }
        }

    @Test
    fun deleteKeyDelegatesToIteratingAndRemovesReference() =
        runTest {
            val keyInfo = mockk<KeyInfoType<*>>()
            coEvery { iteratingStore.deleteKey(keyInfo) } returns true
            coEvery { registrar.removeKeyReference(keyInfo) } returns Ok(true)

            val result = selector.deleteKey(keyInfo)

            assertTrue(result)
            coVerify(exactly = 1) { iteratingStore.deleteKey(keyInfo) }
            coVerify(exactly = 1) { registrar.removeKeyReference(keyInfo) }
        }

    @Test
    fun deleteKeyDoesNotRemoveReferenceWhenNotDeleted() =
        runTest {
            val keyInfo = mockk<KeyInfoType<*>>()
            coEvery { iteratingStore.deleteKey(keyInfo) } returns false

            val result = selector.deleteKey(keyInfo)

            assertFalse(result)
            coVerify(exactly = 1) { iteratingStore.deleteKey(keyInfo) }
            coVerify(exactly = 0) { registrar.removeKeyReference(any()) }
        }
}
