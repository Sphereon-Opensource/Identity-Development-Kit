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

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.model.Origin
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.ManagedKeyInfoType
import com.sphereon.crypto.core.ManagedKeyReference
import com.sphereon.crypto.core.ManagedKeyReferenceFilter
import com.sphereon.crypto.core.ResolvedKeyInfoType
import com.sphereon.crypto.core.ResourceControlMode
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.kms.ManagedKeyStoreMode
import com.sphereon.crypto.core.kms.ManagedKeyStoreModeResolver
import com.sphereon.crypto.key.persistence.KeyReferenceHistoryCapability
import com.sphereon.crypto.key.persistence.KeyReferenceRecord
import com.sphereon.crypto.key.persistence.KeyReferenceResolutionException
import com.sphereon.crypto.key.persistence.KeyReferenceStore
import com.sphereon.crypto.key.persistence.KeyReferenceStoreErrorCodes
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
import kotlin.test.assertNull
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

    private fun restartedSelector() =
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
            coEvery { iteratingStore.maintainsKeyReferenceIndex("provider-1") } returns false
            coEvery { registrar.indexManagedKey(managedKeyInfo) } returns Ok(null)

            val result = selector.storeKey(keyInfo, "provider-1", "alias-1", null)

            assertEquals(managedKeyInfo, result)
            coVerify(exactly = 1) { iteratingStore.storeKey(keyInfo, "provider-1", "alias-1", null) }
            coVerify(exactly = 1) { registrar.indexManagedKey(managedKeyInfo) }
        }

    /**
     * A provider that wrote the authoritative index row itself must not have it written again. The
     * repeat would land under the provider id the returned key reports, and a provider reachable
     * under more than one id then ends up with two index rows for one key.
     */
    @Test
    fun storeKeyDoesNotIndexAgainWhenTheProviderMaintainsTheIndexItself() =
        runTest {
            val keyInfo = mockk<ResolvedKeyInfoType<*>>()
            val managedKeyInfo = mockk<ManagedKeyInfoType<*>>()
            coEvery { iteratingStore.storeKey(keyInfo, "provider-1", "alias-1", null) } returns managedKeyInfo
            coEvery { iteratingStore.maintainsKeyReferenceIndex("provider-1") } returns true

            val result = selector.storeKey(keyInfo, "provider-1", "alias-1", null)

            assertEquals(managedKeyInfo, result)
            coVerify(exactly = 0) { registrar.indexManagedKey(any()) }
        }

    @Test
    fun unavailableHistoryStoreRejectsProviderScopedDeleteBeforeProviderAccess() =
        runTest {
            every { keyReferenceStore.isAvailable } returns false
            val keyInfo = KeyInfo<Jwk>(alias = "possibly-indexed", providerId = "provider-1")

            val error = assertFailsWith<KeyReferenceResolutionException> { selector.deleteKey(keyInfo) }

            assertEquals(KeyReferenceStoreErrorCodes.DURABLE_HISTORY_UNSUPPORTED, error.code)
            coVerify(exactly = 0) { iteratingStore.deleteKey(any()) }
            coVerify(exactly = 0) { registrar.removeKeyReference(any()) }
        }

    @Test
    fun unsupportedHistoryRejectsKnownActiveExternalReferenceWithoutMutation() =
        runTest {
            every { keyReferenceStore.isAvailable } returns true
            every { keyReferenceStore.ownershipHistoryCapability } returns KeyReferenceHistoryCapability.UNSUPPORTED
            val keyInfo = KeyInfo<Jwk>(alias = "key-alias", providerId = "provider-1")

            val error = assertFailsWith<KeyReferenceResolutionException> { selector.deleteKey(keyInfo) }

            assertEquals(KeyReferenceStoreErrorCodes.DURABLE_HISTORY_UNSUPPORTED, error.code)
            coVerify(exactly = 0) { keyReferenceStore.delete(any(), any(), any()) }
            coVerify(exactly = 0) { iteratingStore.deleteKey(any()) }
            coVerify(exactly = 0) { registrar.removeKeyReference(any()) }
        }

    @Test
    fun externalDeleteSoftDeletesTenantScopedReferenceWithoutProviderCalls() =
        runTest {
            every { keyReferenceStore.isAvailable } returns true
            every { keyReferenceStore.ownershipHistoryCapability } returns KeyReferenceHistoryCapability.DURABLE
            val keyInfo = KeyInfo<Jwk>(alias = "key-alias", providerId = "provider-1")
            val external =
                sampleRecord.copy(
                    origin = Origin.EXTERNAL,
                    controlMode = ResourceControlMode.EXTERNALLY_MANAGED,
                )
            coEvery { keyReferenceStore.findByAlias("test-tenant", "key-alias", "provider-1") } returns
                Ok(external)
            coEvery { keyReferenceStore.findAllByAliasIncludingDeleted("test-tenant", "key-alias", "provider-1") } returns
                Ok(listOf(external))
            coEvery { keyReferenceStore.findAllByKidIncludingDeleted("test-tenant", "key-alias", "provider-1") } returns
                Ok(emptyList())
            coEvery { keyReferenceStore.delete("test-tenant", "key-alias", "provider-1") } returns Ok(true)

            val result = selector.deleteKey(keyInfo)

            assertTrue(result)
            coVerify(exactly = 1) {
                keyReferenceStore.findAllByAliasIncludingDeleted("test-tenant", "key-alias", "provider-1")
            }
            coVerify(exactly = 1) { keyReferenceStore.delete("test-tenant", "key-alias", "provider-1") }
            coVerify(exactly = 0) { iteratingStore.getKey(any()) }
            coVerify(exactly = 0) { iteratingStore.deleteKey(any()) }
            coVerify(exactly = 0) { registrar.removeKeyReference(any()) }
        }

    @Test
    fun externalDeleteByKidUsesTenantAndProviderScopeWithoutProviderCalls() =
        runTest {
            every { keyReferenceStore.isAvailable } returns true
            every { keyReferenceStore.ownershipHistoryCapability } returns KeyReferenceHistoryCapability.DURABLE
            val keyInfo = KeyInfo<Jwk>(kid = "kid-1", providerId = "provider-1")
            val external =
                sampleRecord.copy(
                    origin = Origin.EXTERNAL,
                    controlMode = ResourceControlMode.EXTERNALLY_MANAGED,
                )
            coEvery { keyReferenceStore.findByAlias("test-tenant", "kid-1", "provider-1") } returns Ok(null)
            coEvery { keyReferenceStore.findByKid("test-tenant", "kid-1", "provider-1") } returns
                Ok(external)
            coEvery { keyReferenceStore.findAllByAliasIncludingDeleted("test-tenant", "kid-1", "provider-1") } returns
                Ok(emptyList())
            coEvery { keyReferenceStore.findAllByKidIncludingDeleted("test-tenant", "kid-1", "provider-1") } returns
                Ok(listOf(external))
            coEvery { keyReferenceStore.delete("test-tenant", "key-alias", "provider-1") } returns Ok(true)

            val result = selector.deleteKey(keyInfo)

            assertTrue(result)
            coVerify(exactly = 1) {
                keyReferenceStore.findAllByKidIncludingDeleted("test-tenant", "kid-1", "provider-1")
            }
            coVerify(exactly = 1) { keyReferenceStore.delete("test-tenant", "key-alias", "provider-1") }
            coVerify(exactly = 0) { iteratingStore.deleteKey(any()) }
        }

    @Test
    fun providerLessDeleteRejectsDuplicateAliasAcrossProvidersBeforeProviderAccess() =
        runTest {
            every { keyReferenceStore.isAvailable } returns true
            every { keyReferenceStore.ownershipHistoryCapability } returns KeyReferenceHistoryCapability.DURABLE
            val first =
                sampleRecord.copy(
                    id = "provider-a-reference",
                    providerId = "provider-a",
                    controlMode = ResourceControlMode.EXTERNALLY_MANAGED,
                )
            val second = first.copy(id = "provider-b-reference", providerId = "provider-b")
            coEvery { keyReferenceStore.findAllActiveByAlias("test-tenant", "key-alias", null) } returns Ok(listOf(first, second))
            coEvery { keyReferenceStore.findAllActiveByKid("test-tenant", "key-alias", null) } returns Ok(emptyList())
            coEvery { keyReferenceStore.findAllByAliasIncludingDeleted("test-tenant", "key-alias", null) } returns
                Ok(listOf(first, second))
            coEvery { keyReferenceStore.findAllByKidIncludingDeleted("test-tenant", "key-alias", null) } returns Ok(emptyList())

            val error =
                assertFailsWith<KeyReferenceResolutionException> {
                    selector.deleteKey(KeyInfo<Jwk>(alias = "key-alias"))
                }

            assertEquals(KeyReferenceStoreErrorCodes.AMBIGUOUS_REFERENCE, error.code)
            coVerify(exactly = 0) { iteratingStore.getKey(any()) }
            coVerify(exactly = 0) { iteratingStore.deleteKey(any()) }
            coVerify(exactly = 0) { keyReferenceStore.delete(any(), any(), any()) }
        }

    @Test
    fun providerLessDeleteRejectsDuplicateKidAcrossProvidersBeforeProviderAccess() =
        runTest {
            every { keyReferenceStore.isAvailable } returns true
            every { keyReferenceStore.ownershipHistoryCapability } returns KeyReferenceHistoryCapability.DURABLE
            val first = sampleRecord.copy(id = "provider-a-reference", providerId = "provider-a")
            val second = first.copy(id = "provider-b-reference", providerId = "provider-b")
            coEvery { keyReferenceStore.findAllActiveByAlias("test-tenant", "kid-1", null) } returns Ok(emptyList())
            coEvery { keyReferenceStore.findAllActiveByKid("test-tenant", "kid-1", null) } returns Ok(listOf(first, second))
            coEvery { keyReferenceStore.findAllByAliasIncludingDeleted("test-tenant", "kid-1", null) } returns Ok(emptyList())
            coEvery { keyReferenceStore.findAllByKidIncludingDeleted("test-tenant", "kid-1", null) } returns
                Ok(listOf(first, second))

            val error =
                assertFailsWith<KeyReferenceResolutionException> {
                    selector.deleteKey(KeyInfo<Jwk>(kid = "kid-1"))
                }

            assertEquals(KeyReferenceStoreErrorCodes.AMBIGUOUS_REFERENCE, error.code)
            coVerify(exactly = 0) { iteratingStore.getKey(any()) }
            coVerify(exactly = 0) { iteratingStore.deleteKey(any()) }
            coVerify(exactly = 0) { keyReferenceStore.delete(any(), any(), any()) }
        }

    @Test
    fun providerLessDeleteDeduplicatesAliasAndKidMatchForSameReference() =
        runTest {
            every { keyReferenceStore.isAvailable } returns true
            every { keyReferenceStore.ownershipHistoryCapability } returns KeyReferenceHistoryCapability.DURABLE
            val external =
                sampleRecord.copy(
                    alias = "shared-identifier",
                    kid = "shared-identifier",
                    controlMode = ResourceControlMode.EXTERNALLY_MANAGED,
                )
            coEvery { keyReferenceStore.findAllActiveByAlias("test-tenant", "shared-identifier", null) } returns Ok(listOf(external))
            coEvery { keyReferenceStore.findAllActiveByKid("test-tenant", "shared-identifier", null) } returns Ok(listOf(external))
            coEvery {
                keyReferenceStore.findAllByAliasIncludingDeleted("test-tenant", "shared-identifier", null)
            } returns Ok(listOf(external))
            coEvery {
                keyReferenceStore.findAllByKidIncludingDeleted("test-tenant", "shared-identifier", null)
            } returns Ok(listOf(external))
            coEvery { keyReferenceStore.delete("test-tenant", "shared-identifier", "provider-1") } returns Ok(true)

            assertTrue(selector.deleteKey(KeyInfo<Jwk>(alias = "shared-identifier")))

            coVerify(exactly = 1) { keyReferenceStore.delete("test-tenant", "shared-identifier", "provider-1") }
            coVerify(exactly = 1) {
                keyReferenceStore.findAllByAliasIncludingDeleted("test-tenant", "shared-identifier", null)
            }
            coVerify(exactly = 1) {
                keyReferenceStore.findAllByKidIncludingDeleted("test-tenant", "shared-identifier", null)
            }
            coVerify(exactly = 0) { iteratingStore.deleteKey(any()) }
        }

    @Test
    fun providerLessDeleteRejectsActivePlatformReferenceConflictingWithExternalHistoryBeforeProviderAccess() =
        runTest {
            every { keyReferenceStore.isAvailable } returns true
            every { keyReferenceStore.ownershipHistoryCapability } returns KeyReferenceHistoryCapability.DURABLE
            val active =
                sampleRecord.copy(
                    id = "active-platform-reference",
                    providerId = "provider-active",
                    controlMode = ResourceControlMode.PLATFORM_MANAGED,
                )
            val historical =
                sampleRecord.copy(
                    id = "historical-external-reference",
                    providerId = "provider-historical",
                    origin = Origin.EXTERNAL,
                    controlMode = ResourceControlMode.EXTERNALLY_MANAGED,
                    deletedAt = now,
                )
            coEvery { keyReferenceStore.findAllActiveByAlias("test-tenant", "key-alias", null) } returns Ok(listOf(active))
            coEvery { keyReferenceStore.findAllActiveByKid("test-tenant", "key-alias", null) } returns Ok(emptyList())
            coEvery { keyReferenceStore.findAllByAliasIncludingDeleted("test-tenant", "key-alias", null) } returns
                Ok(listOf(active, historical))
            coEvery { keyReferenceStore.findAllByKidIncludingDeleted("test-tenant", "key-alias", null) } returns Ok(emptyList())

            val error =
                assertFailsWith<KeyReferenceResolutionException> {
                    selector.deleteKey(KeyInfo<Jwk>(alias = "key-alias"))
                }

            assertEquals(KeyReferenceStoreErrorCodes.AMBIGUOUS_REFERENCE, error.code)
            coVerify(exactly = 0) { iteratingStore.getKey(any()) }
            coVerify(exactly = 0) { iteratingStore.deleteKey(any()) }
            coVerify(exactly = 0) { keyReferenceStore.delete(any(), any(), any()) }
        }

    @Test
    fun providerLessDeleteRejectsAmbiguousDeletedHistoryBeforeProviderAccess() =
        runTest {
            every { keyReferenceStore.isAvailable } returns true
            every { keyReferenceStore.ownershipHistoryCapability } returns KeyReferenceHistoryCapability.DURABLE
            val first = sampleRecord.copy(id = "deleted-provider-a", providerId = "provider-a", deletedAt = now)
            val second = first.copy(id = "deleted-provider-b", providerId = "provider-b")
            coEvery { keyReferenceStore.findAllActiveByAlias("test-tenant", "key-alias", null) } returns Ok(emptyList())
            coEvery { keyReferenceStore.findAllActiveByKid("test-tenant", "key-alias", null) } returns Ok(emptyList())
            coEvery { keyReferenceStore.findAllByAliasIncludingDeleted("test-tenant", "key-alias", null) } returns Ok(listOf(first, second))
            coEvery { keyReferenceStore.findAllByKidIncludingDeleted("test-tenant", "key-alias", null) } returns Ok(emptyList())

            val error =
                assertFailsWith<KeyReferenceResolutionException> {
                    selector.deleteKey(KeyInfo<Jwk>(alias = "key-alias"))
                }

            assertEquals(KeyReferenceStoreErrorCodes.AMBIGUOUS_REFERENCE, error.code)
            coVerify(exactly = 0) { iteratingStore.getKey(any()) }
            coVerify(exactly = 0) { iteratingStore.deleteKey(any()) }
        }

    @Test
    fun providerLessDeleteWithNoPersistedReferenceReturnsFalseWithoutProviderAccess() =
        runTest {
            every { keyReferenceStore.isAvailable } returns true
            every { keyReferenceStore.ownershipHistoryCapability } returns KeyReferenceHistoryCapability.DURABLE
            coEvery { keyReferenceStore.findAllActiveByAlias("test-tenant", "unindexed", null) } returns Ok(emptyList())
            coEvery { keyReferenceStore.findAllActiveByKid("test-tenant", "unindexed", null) } returns Ok(emptyList())
            coEvery { keyReferenceStore.findAllByAliasIncludingDeleted("test-tenant", "unindexed", null) } returns Ok(emptyList())
            coEvery { keyReferenceStore.findAllByKidIncludingDeleted("test-tenant", "unindexed", null) } returns Ok(emptyList())

            assertFalse(selector.deleteKey(KeyInfo<Jwk>(alias = "unindexed")))

            coVerify(exactly = 0) { iteratingStore.getKey(any()) }
            coVerify(exactly = 0) { iteratingStore.deleteKey(any()) }
        }

    @Test
    fun unsupportedHistoryNeverAuthorizesProviderDelete() =
        runTest {
            every { keyReferenceStore.isAvailable } returns true
            every { keyReferenceStore.ownershipHistoryCapability } returns KeyReferenceHistoryCapability.UNSUPPORTED
            val keyInfo = KeyInfo<Jwk>(alias = "possibly-deleted", providerId = "provider-1")
            coEvery { keyReferenceStore.findByAlias("test-tenant", "possibly-deleted", "provider-1") } returns Ok(null)
            coEvery { keyReferenceStore.findByKid("test-tenant", "possibly-deleted", "provider-1") } returns Ok(null)

            val error =
                assertFailsWith<KeyReferenceResolutionException> {
                    selector.deleteKey(keyInfo)
                }

            assertEquals(KeyReferenceStoreErrorCodes.DURABLE_HISTORY_UNSUPPORTED, error.code)
            coVerify(exactly = 0) { iteratingStore.getKey(any()) }
            coVerify(exactly = 0) { iteratingStore.deleteKey(any()) }
        }

    @Test
    fun repeatedExternalDeleteByAliasDoesNotFallThroughToProviderAfterSoftDelete() =
        runTest {
            every { keyReferenceStore.isAvailable } returns true
            every { keyReferenceStore.ownershipHistoryCapability } returns KeyReferenceHistoryCapability.DURABLE
            val keyInfo = KeyInfo<Jwk>(alias = "key-alias", providerId = "provider-1")
            val externalRecord =
                sampleRecord.copy(
                    origin = Origin.EXTERNAL,
                    controlMode = ResourceControlMode.EXTERNALLY_MANAGED,
                )
            var lookupCount = 0
            coEvery { keyReferenceStore.findByAlias("test-tenant", "key-alias", "provider-1") } answers {
                if (lookupCount++ == 0) Ok(externalRecord) else Ok(null)
            }
            coEvery { keyReferenceStore.findByKid("test-tenant", "key-alias", "provider-1") } returns Ok(null)
            var historyLookupCount = 0
            coEvery { keyReferenceStore.findAllByAliasIncludingDeleted("test-tenant", "key-alias", "provider-1") } answers {
                if (historyLookupCount++ == 0) Ok(listOf(externalRecord)) else Ok(listOf(externalRecord.copy(deletedAt = now)))
            }
            coEvery { keyReferenceStore.findAllByKidIncludingDeleted("test-tenant", "key-alias", "provider-1") } returns
                Ok(emptyList())
            coEvery { keyReferenceStore.delete("test-tenant", "key-alias", "provider-1") } returns Ok(true)

            assertTrue(selector.deleteKey(keyInfo))
            assertTrue(restartedSelector().deleteKey(keyInfo))

            coVerify(exactly = 1) { keyReferenceStore.delete("test-tenant", "key-alias", "provider-1") }
            coVerify(exactly = 0) { iteratingStore.deleteKey(any()) }
            coVerify(exactly = 0) { iteratingStore.getKey(any()) }
        }

    @Test
    fun repeatedExternalDeleteWithoutProviderIdDoesNotFallThroughToProviderAfterSoftDelete() =
        runTest {
            val keyInfo = KeyInfo<Jwk>(alias = "key-alias")
            val externalRecord =
                sampleRecord.copy(
                    origin = Origin.EXTERNAL,
                    controlMode = ResourceControlMode.EXTERNALLY_MANAGED,
                )
            val persistedRecords = mutableListOf<KeyReferenceRecord>()
            val firstStore = durableStoreSession(persistedRecords)
            assertTrue(firstStore.save(externalRecord).isOk)
            assertTrue(selectorFor(firstStore).deleteKey(keyInfo))

            val reopenedStore = durableStoreSession(persistedRecords)
            assertTrue(selectorFor(reopenedStore).deleteKey(keyInfo))
            assertEquals(
                ResourceControlMode.EXTERNALLY_MANAGED,
                reopenedStore.findLatestByAliasIncludingDeleted("test-tenant", "key-alias", "provider-1").value!!.controlMode,
            )
            coVerify(exactly = 0) { iteratingStore.deleteKey(any()) }
        }

    @Test
    fun repeatedExternalDeleteByKidDoesNotFallThroughToProviderAfterAliasSoftDelete() =
        runTest {
            val aliasKeyInfo = KeyInfo<Jwk>(alias = "key-alias", providerId = "provider-1")
            val kidKeyInfo = KeyInfo<Jwk>(kid = "kid-1", providerId = "provider-1")
            val externalRecord =
                sampleRecord.copy(
                    origin = Origin.EXTERNAL,
                    controlMode = ResourceControlMode.EXTERNALLY_MANAGED,
                )
            val persistedRecords = mutableListOf<KeyReferenceRecord>()
            val firstStore = durableStoreSession(persistedRecords)
            assertTrue(firstStore.save(externalRecord).isOk)
            assertTrue(selectorFor(firstStore).deleteKey(aliasKeyInfo))

            val reopenedStore = durableStoreSession(persistedRecords)
            assertTrue(selectorFor(reopenedStore).deleteKey(kidKeyInfo))
            assertEquals(
                ResourceControlMode.EXTERNALLY_MANAGED,
                reopenedStore.findLatestByKidIncludingDeleted("test-tenant", "kid-1", "provider-1").value!!.controlMode,
            )
            coVerify(exactly = 0) { iteratingStore.deleteKey(any()) }
        }

    private fun selectorFor(store: KeyReferenceStore): ManagedKeyStoreSelector =
        ManagedKeyStoreSelector(
            iteratingStore = iteratingStore,
            keyReferenceStore = store,
            modeResolver = modeResolver,
            registrar = registrar,
            execution = execution,
        )

    private fun durableStoreSession(records: MutableList<KeyReferenceRecord>): KeyReferenceStore =
        DurableKeyReferenceStoreDouble(records)

    @Test
    fun registeredReferenceLookupUsesPersistentIndexWithoutProviderInventory() =
        runTest {
            every { keyReferenceStore.isAvailable } returns true
            val external =
                sampleRecord.copy(
                    origin = Origin.EXTERNAL,
                    controlMode = ResourceControlMode.EXTERNALLY_MANAGED,
                )
            coEvery { keyReferenceStore.findByAlias("test-tenant", "kid-1", "provider-1") } returns Ok(null)
            coEvery { keyReferenceStore.findByKid("test-tenant", "kid-1", "provider-1") } returns Ok(external)

            val result = selector.findRegisteredKeyReference("kid-1", "provider-1")

            assertEquals(Origin.EXTERNAL, result?.origin)
            assertEquals(ResourceControlMode.EXTERNALLY_MANAGED, result?.controlMode)
            coVerify(exactly = 0) { iteratingStore.listKeys() }
        }

    @Test
    fun registeredReferenceLookupReturnsNullWhenNoPersistentIndexExists() =
        runTest {
            every { keyReferenceStore.isAvailable } returns false

            assertNull(selector.findRegisteredKeyReference("provider-key", "provider-1"))

            coVerify(exactly = 0) { iteratingStore.listKeys() }
            coVerify(exactly = 0) { keyReferenceStore.findByAlias(any(), any(), any()) }
            coVerify(exactly = 0) { keyReferenceStore.findByKid(any(), any(), any()) }
        }

    @Test
    fun unindexedProviderDeletePreservesLegacyFallback() =
        runTest {
            every { keyReferenceStore.isAvailable } returns true
            every { keyReferenceStore.ownershipHistoryCapability } returns KeyReferenceHistoryCapability.DURABLE
            val keyInfo = KeyInfo<Jwk>(alias = "unindexed-alias", providerId = "provider-1")
            coEvery { keyReferenceStore.findByAlias("test-tenant", "unindexed-alias", "provider-1") } returns Ok(null)
            coEvery { keyReferenceStore.findByKid("test-tenant", "unindexed-alias", "provider-1") } returns Ok(null)
            coEvery { keyReferenceStore.findAllByAliasIncludingDeleted("test-tenant", "unindexed-alias", "provider-1") } returns Ok(emptyList())
            coEvery { keyReferenceStore.findAllByKidIncludingDeleted("test-tenant", "unindexed-alias", "provider-1") } returns Ok(emptyList())
            coEvery { iteratingStore.deleteKey(keyInfo) } returns true
            coEvery { registrar.removeKeyReference(keyInfo) } returns Ok(true)

            assertTrue(selector.deleteKey(keyInfo))

            coVerify(exactly = 1) { iteratingStore.deleteKey(keyInfo) }
            coVerify(exactly = 1) { registrar.removeKeyReference(keyInfo) }
        }

    @Test
    fun platformDeleteCallsProviderFirstAndRemovesReferenceAfterSuccess() =
        runTest {
            every { keyReferenceStore.isAvailable } returns true
            every { keyReferenceStore.ownershipHistoryCapability } returns KeyReferenceHistoryCapability.DURABLE
            val keyInfo = KeyInfo<Jwk>(alias = "key-alias", providerId = "provider-1")
            val providerKeyInfo = KeyInfo<Jwk>(kid = "kid-1", providerId = "provider-1")
            coEvery { keyReferenceStore.findByAlias("test-tenant", "key-alias", "provider-1") } returns Ok(sampleRecord)
            coEvery { keyReferenceStore.findAllByAliasIncludingDeleted("test-tenant", "key-alias", "provider-1") } returns
                Ok(listOf(sampleRecord))
            coEvery { keyReferenceStore.findAllByKidIncludingDeleted("test-tenant", "key-alias", "provider-1") } returns
                Ok(emptyList())
            coEvery { iteratingStore.deleteKey(providerKeyInfo) } returns true
            coEvery { registrar.removeKeyReference(any()) } returns Ok(true)

            val result = selector.deleteKey(keyInfo)

            assertTrue(result)
            coVerify(exactly = 1) { iteratingStore.deleteKey(providerKeyInfo) }
            coVerify(exactly = 1) { registrar.removeKeyReference(any()) }
            coVerify(exactly = 0) { keyReferenceStore.delete(any(), any(), any()) }
        }

    @Test
    fun platformDeleteFailureLeavesReferenceAndDoesNotRemoveIt() =
        runTest {
            every { keyReferenceStore.isAvailable } returns true
            every { keyReferenceStore.ownershipHistoryCapability } returns KeyReferenceHistoryCapability.DURABLE
            val keyInfo = KeyInfo<Jwk>(alias = "key-alias", providerId = "provider-1")
            val providerKeyInfo = KeyInfo<Jwk>(kid = "kid-1", providerId = "provider-1")
            coEvery { keyReferenceStore.findByAlias("test-tenant", "key-alias", "provider-1") } returns Ok(sampleRecord)
            coEvery { keyReferenceStore.findAllByAliasIncludingDeleted("test-tenant", "key-alias", "provider-1") } returns
                Ok(listOf(sampleRecord))
            coEvery { keyReferenceStore.findAllByKidIncludingDeleted("test-tenant", "key-alias", "provider-1") } returns
                Ok(emptyList())
            coEvery { iteratingStore.deleteKey(providerKeyInfo) } returns false

            val result = selector.deleteKey(keyInfo)

            assertFalse(result)
            coVerify(exactly = 1) { iteratingStore.deleteKey(providerKeyInfo) }
            coVerify(exactly = 0) { keyReferenceStore.delete(any(), any(), any()) }
            coVerify(exactly = 0) { registrar.removeKeyReference(any()) }
        }
}

/**
 * A durable-history double for selector tests. The mutable row collection represents the
 * persistence boundary and is deliberately shared by separately constructed store instances.
 * The SQLite module owns the real file-backed close/reopen proof.
 */
private class DurableKeyReferenceStoreDouble(
    private val rows: MutableList<KeyReferenceRecord>,
) : KeyReferenceStore {
    override val ownershipHistoryCapability: KeyReferenceHistoryCapability = KeyReferenceHistoryCapability.DURABLE

    override suspend fun save(record: KeyReferenceRecord): IdkResult<KeyReferenceRecord, IdkError> {
        rows += record
        return Ok(record)
    }

    override suspend fun upsert(record: KeyReferenceRecord): IdkResult<KeyReferenceRecord, IdkError> {
        val index = rows.indexOfFirst { it.tenantId == record.tenantId && it.alias == record.alias && it.providerId == record.providerId }
        if (index >= 0) rows[index] = record else rows += record
        return Ok(record)
    }

    override suspend fun findById(
        tenantId: String,
        id: String,
    ): IdkResult<KeyReferenceRecord?, IdkError> = Ok(rows.firstOrNull { it.tenantId == tenantId && it.id == id && it.deletedAt == null })

    override suspend fun findByKid(
        tenantId: String,
        kid: String,
        providerId: String?,
    ): IdkResult<KeyReferenceRecord?, IdkError> = Ok(activeMatches(tenantId, providerId) { it.kid == kid }.singleOrNull())

    override suspend fun findByAlias(
        tenantId: String,
        alias: String,
        providerId: String?,
    ): IdkResult<KeyReferenceRecord?, IdkError> = Ok(activeMatches(tenantId, providerId) { it.alias == alias }.singleOrNull())

    override suspend fun findAllByAliasIncludingDeleted(
        tenantId: String,
        alias: String,
        providerId: String?,
    ): IdkResult<List<KeyReferenceRecord>, IdkError> = Ok(allMatches(tenantId, providerId) { it.alias == alias })

    override suspend fun findAllByKidIncludingDeleted(
        tenantId: String,
        kid: String,
        providerId: String?,
    ): IdkResult<List<KeyReferenceRecord>, IdkError> = Ok(allMatches(tenantId, providerId) { it.kid == kid })

    override suspend fun findLatestByAliasIncludingDeleted(
        tenantId: String,
        alias: String,
        providerId: String?,
    ): IdkResult<KeyReferenceRecord?, IdkError> = Ok(allMatches(tenantId, providerId) { it.alias == alias }.maxByOrNull { it.updatedAt })

    override suspend fun findLatestByKidIncludingDeleted(
        tenantId: String,
        kid: String,
        providerId: String?,
    ): IdkResult<KeyReferenceRecord?, IdkError> = Ok(allMatches(tenantId, providerId) { it.kid == kid }.maxByOrNull { it.updatedAt })

    override suspend fun findAll(
        tenantId: String,
        filter: ManagedKeyReferenceFilter?,
    ): IdkResult<List<KeyReferenceRecord>, IdkError> = Ok(rows.filter { it.tenantId == tenantId && it.deletedAt == null })

    override suspend fun delete(
        tenantId: String,
        alias: String,
        providerId: String,
    ): IdkResult<Boolean, IdkError> = softDelete { it.tenantId == tenantId && it.alias == alias && it.providerId == providerId }

    override suspend fun deleteByKid(
        tenantId: String,
        kid: String,
        providerId: String?,
    ): IdkResult<Boolean, IdkError> = softDelete { it.tenantId == tenantId && it.kid == kid && (providerId == null || it.providerId == providerId) }

    override suspend fun exists(
        tenantId: String,
        alias: String,
        providerId: String,
    ): IdkResult<Boolean, IdkError> = Ok(rows.any { it.tenantId == tenantId && it.alias == alias && it.providerId == providerId && it.deletedAt == null })

    private fun activeMatches(
        tenantId: String,
        providerId: String?,
        predicate: (KeyReferenceRecord) -> Boolean,
    ): List<KeyReferenceRecord> = allMatches(tenantId, providerId, predicate).filter { it.deletedAt == null }

    private fun allMatches(
        tenantId: String,
        providerId: String?,
        predicate: (KeyReferenceRecord) -> Boolean,
    ): List<KeyReferenceRecord> = rows.filter { it.tenantId == tenantId && (providerId == null || it.providerId == providerId) && predicate(it) }

    private fun softDelete(predicate: (KeyReferenceRecord) -> Boolean): IdkResult<Boolean, IdkError> {
        val index = rows.indexOfFirst { it.deletedAt == null && predicate(it) }
        if (index < 0) return Ok(false)
        val now = Clock.System.now()
        rows[index] = rows[index].copy(deletedAt = now, updatedAt = now)
        return Ok(true)
    }
}
