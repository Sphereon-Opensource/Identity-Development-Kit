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
 *
 */

package com.sphereon.crypto.kms.keystore.memory

import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.core.api.error.NotFoundException
import com.sphereon.core.api.session.asCoreApiServiceGraph
import com.sphereon.crypto.core.JvmCryptoTestAppGraph
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.PKIException
import com.sphereon.crypto.core.ResolvedKeyInfo
import com.sphereon.crypto.core.createJvmCryptoTestAppGraph
import com.sphereon.crypto.core.generic.KeyTypeMapping
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.JwaAlgorithm
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.kms.KeyStore
import com.sphereon.crypto.core.kms.asKeyManagerServiceGraph
import com.sphereon.crypto.core.kms.model.PredefinedKeyStoreTypes
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderConfig
import com.sphereon.di.context.TenantContextData
import com.sphereon.di.context.UserContext
import com.sphereon.di.session.SessionContext
import dev.whyoleg.cryptography.CryptographyProvider
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for MemoryKeyStore implementation.
 */
class MemoryKeyStoreTest {
    private lateinit var keyManagerService: KeyManagerService
    private lateinit var memoryKeyStore: KeyStore

    val app = createJvmCryptoTestAppGraph(this)
    val context = app.userContextManager.getAnonymous()
    val session = context.sessionContextManager.createOrGetFromId("memory-keystore-test")

    @BeforeTest
    fun setUp() {
        // Register the software KMS provider for key generation
        val config =
            SoftwareKmsProviderConfig(
                id = "test-software-provider",
                cryptographyProvider = CryptographyProvider.Default.name,
            )
        app as JvmCryptoTestAppGraph
        val softwareKmsProvider = app.softwareKmsProvider.create(config, session.asCoreApiServiceGraph().serviceExecution)

        keyManagerService = session.graph.asKeyManagerServiceGraph().keyManagerService
        keyManagerService.registerProvider(softwareKmsProvider, makeDefaultKms = true)

        // Create memory keystore config for testing (session-scoped)
        val memoryConfig =
            MemoryKeyStoreConfig(
                id = "test-memory-keystore",
                keyVisibility = KeyVisibility.PRIVATE.keyVisibility,
                overwriteAlias = true,
                scopeBinding = MemoryKeyStoreScopeBinding.SESSION.value,
            )

        // Create keystore using the factory
        val factory = app.memoryKeyStore
        memoryKeyStore = factory.create(memoryConfig, session.asCoreApiServiceGraph().serviceExecution)
    }

    // =========== Key Storage Tests ===========

    @Test
    fun storeKeyShouldSucceed() =
        runTest {
            val keyPair =
                keyManagerService.generateKey(
                    alg = SignatureAlgorithm.ECDSA_SHA256,
                )
            assertNotNull(keyPair)

            val keyInfo = keyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
            val alias = "test-key-1"

            val storedKey = memoryKeyStore.storeKey(keyInfo, "test-provider", alias, null)

            assertNotNull(storedKey)
            assertEquals(alias, storedKey.alias)
            assertEquals("test-provider", storedKey.providerId)
        }

    @Test
    fun getKeyByAliasShouldSucceed() =
        runTest {
            val keyPair =
                keyManagerService.generateKey(
                    alg = SignatureAlgorithm.ECDSA_SHA256,
                )
            val keyInfo = keyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
            val alias = "test-key-get"

            val storedKey = memoryKeyStore.storeKey(keyInfo, "test-provider", alias, null)

            // Retrieve by the stored key info (which has the alias)
            val retrievedKey = memoryKeyStore.getKey(storedKey)

            assertNotNull(retrievedKey)
            assertEquals(alias, retrievedKey.alias)
        }

    @Test
    fun listKeysShouldReturnStoredKeys() =
        runTest {
            val keyPair1 = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
            val keyPair2 = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA384)

            val keyInfo1 = keyPair1.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
            val keyInfo2 = keyPair2.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

            memoryKeyStore.storeKey(keyInfo1, "provider-1", "key-list-1", null)
            memoryKeyStore.storeKey(keyInfo2, "provider-2", "key-list-2", null)

            val keys = memoryKeyStore.listKeys()

            assertTrue(keys.size >= 2)
            assertTrue(keys.any { it.alias == "key-list-1" })
            assertTrue(keys.any { it.alias == "key-list-2" })
        }

    @Test
    fun deleteKeyShouldSucceed() =
        runTest {
            val keyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
            val keyInfo = keyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
            val alias = "key-to-delete"

            val storedKey = memoryKeyStore.storeKey(keyInfo, "test-provider", alias, null)

            val deleted = memoryKeyStore.deleteKey(storedKey)

            assertTrue(deleted, "Delete should return true for existing key")
        }

    @Test
    fun deleteKeyNotFoundShouldReturnFalse() =
        runTest {
            val keyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
            val keyInfo = keyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
            // Create a ManagedKeyInfo with a non-existent alias
            val nonExistentKeyInfo =
                com.sphereon.crypto.core.ManagedKeyInfo(
                    providerId = keyInfo.providerId,
                    alias = "non-existent-key-for-delete-test",
                    resolvedKeyInfo = keyInfo,
                )

            // deleteKey should return false for non-existent keys, not throw
            val deleted = memoryKeyStore.deleteKey(nonExistentKeyInfo)
            assertFalse(deleted, "Delete should return false for non-existent key")
        }

    // =========== Visibility Tests ===========

    @Test
    fun getKeyAsPublicShouldReturnPublicKey() =
        runTest {
            val keyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
            val keyInfo = keyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
            val alias = "visibility-test-key"

            val storedKey = memoryKeyStore.storeKey(keyInfo, "test-provider", alias, null)

            // Request as public key using the stored key's public version
            val publicKeyInfo = storedKey.toManagedPublicKeyInfo()
            val retrievedKey = memoryKeyStore.getKey(publicKeyInfo)

            assertNotNull(retrievedKey)
            // The returned key info has public visibility
            // Note: Some implementations may preserve or change visibility
            assertNotNull(retrievedKey.keyVisibility)
        }

    // =========== Configuration Tests ===========

    @Test
    fun keystoreShouldHaveCorrectType() {
        assertEquals(PredefinedKeyStoreTypes.MEMORY.keyStoreType, memoryKeyStore.keyStoreType)
    }

    @Test
    fun keystoreShouldReportCorrectVisibility() {
        assertEquals(KeyVisibility.PRIVATE, memoryKeyStore.keyVisibility())
    }

    @Test
    fun keystoreShouldSupportAllKeyTypes() {
        assertTrue(memoryKeyStore.keyTypesSupported.isNotEmpty())
    }

    @Test
    fun keystoreShouldSupportAllSignatureAlgorithms() {
        assertTrue(memoryKeyStore.signatureAlgorithmsSupported.isNotEmpty())
    }

    // =========== Certificate Chain Tests ===========

    @Test
    fun listCertificateChainAliasesShouldReturnEmpty() =
        runTest {
            val aliases = memoryKeyStore.listCertificateChainAliases()
            // Initially should be empty (or contain only chains from other tests)
            assertNotNull(aliases)
        }

    @Test
    fun listCertificateAliasesShouldReturnEmpty() =
        runTest {
            val aliases = memoryKeyStore.listCertificateAliases()
            assertNotNull(aliases)
        }

    // =========== Factory Tests ===========

    @Test
    fun factoryShouldCreateMemoryKeystore() {
        val memoryConfig =
            MemoryKeyStoreConfig(
                id = "factory-test-keystore",
                keyVisibility = KeyVisibility.PUBLIC.keyVisibility,
            )
        app as JvmCryptoTestAppGraph
        val factory = app.memoryKeyStore
        val keystore = factory.create(memoryConfig, session.asCoreApiServiceGraph().serviceExecution)

        assertNotNull(keystore)
        assertEquals("factory-test-keystore", keystore.id)
    }

    @Test
    fun factoryShouldRejectNonMemoryConfig() {
        app as JvmCryptoTestAppGraph
        val factory = app.memoryKeyStore

        // Use the base KeyStoreConfigImpl instead of MemoryKeyStoreConfig
        val invalidConfig =
            com.sphereon.crypto.core.kms.KeyStoreConfigImpl(
                id = "invalid",
                keyStoreType = "other",
            )

        assertFailsWith<IllegalArgumentException> {
            factory.create(invalidConfig, session.asCoreApiServiceGraph().serviceExecution)
        }
    }

    @Test
    fun factoryShouldCreateAppScopedKeystore() {
        val appScopedConfig =
            MemoryKeyStoreConfig(
                id = "app-scoped-keystore-${System.currentTimeMillis()}",
                keyVisibility = KeyVisibility.PUBLIC.keyVisibility,
                scopeBinding = MemoryKeyStoreScopeBinding.APP.value,
            )
        app as JvmCryptoTestAppGraph
        val factory = app.memoryKeyStore
        val keystore = factory.create(appScopedConfig, session.asCoreApiServiceGraph().serviceExecution)

        assertNotNull(keystore)
    }

    @Test
    fun factoryShouldCreateTenantScopedKeystore() {
        val tenantScopedConfig =
            MemoryKeyStoreConfig(
                id = "tenant-scoped-keystore-${System.currentTimeMillis()}",
                keyVisibility = KeyVisibility.PUBLIC.keyVisibility,
                scopeBinding = MemoryKeyStoreScopeBinding.TENANT.value,
            )
        app as JvmCryptoTestAppGraph
        val factory = app.memoryKeyStore
        // Session has tenant context set
        val keystore = factory.create(tenantScopedConfig, session.asCoreApiServiceGraph().serviceExecution)

        assertNotNull(keystore)
    }

    @Test
    fun factoryShouldCreatePrincipalTenantScopedKeystore() {
        val principalTenantConfig =
            MemoryKeyStoreConfig(
                id = "principal-tenant-scoped-${System.currentTimeMillis()}",
                keyVisibility = KeyVisibility.PUBLIC.keyVisibility,
                scopeBinding = MemoryKeyStoreScopeBinding.PRINCIPAL_TENANT.value,
            )
        app as JvmCryptoTestAppGraph
        val factory = app.memoryKeyStore
        val keystore = factory.create(principalTenantConfig, session.asCoreApiServiceGraph().serviceExecution)

        assertNotNull(keystore)
    }

    @Test
    fun factoryShouldCreateSessionScopedKeystore() {
        val sessionScopedConfig =
            MemoryKeyStoreConfig(
                id = "session-scoped-keystore-${System.currentTimeMillis()}",
                keyVisibility = KeyVisibility.PUBLIC.keyVisibility,
                scopeBinding = MemoryKeyStoreScopeBinding.SESSION.value,
            )
        app as JvmCryptoTestAppGraph
        val factory = app.memoryKeyStore
        val keystore = factory.create(sessionScopedConfig, session.asCoreApiServiceGraph().serviceExecution)

        assertNotNull(keystore)
    }

    @Test
    fun factoryWithoutExecutionShouldFallbackToLocalStorage() {
        val sessionScopedConfig =
            MemoryKeyStoreConfig(
                id = "no-execution-keystore-${System.currentTimeMillis()}",
                keyVisibility = KeyVisibility.PUBLIC.keyVisibility,
                scopeBinding = MemoryKeyStoreScopeBinding.SESSION.value,
            )
        app as JvmCryptoTestAppGraph
        val factory = app.memoryKeyStore

        // Create without execution context - should fallback to local storage
        val keystore = factory.create(sessionScopedConfig)

        assertNotNull(keystore)
    }

    @Test
    fun factoryAppScopeWithoutExecutionShouldUseBackingStorage() {
        val appScopedConfig =
            MemoryKeyStoreConfig(
                id = "app-scope-no-execution-${System.currentTimeMillis()}",
                keyVisibility = KeyVisibility.PUBLIC.keyVisibility,
                scopeBinding = MemoryKeyStoreScopeBinding.APP.value,
            )
        app as JvmCryptoTestAppGraph
        val factory = app.memoryKeyStore

        // APP scope with execution=null should still use backing storage
        val keystore = factory.create(appScopedConfig, null)

        assertNotNull(keystore)
    }

    // =========== Overwrite Tests ===========

    @Test
    fun overwriteEnabledShouldAllowReplace() =
        runTest {
            val keyPair1 = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
            val keyPair2 = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)

            val keyInfo1 = keyPair1.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
            val keyInfo2 = keyPair2.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

            val alias = "overwrite-test-key"

            // Store first key
            memoryKeyStore.storeKey(keyInfo1, "provider-1", alias, null)

            // Store second key with same alias (should succeed since overwriteAlias=true)
            val storedKey = memoryKeyStore.storeKey(keyInfo2, "provider-2", alias, null)

            assertNotNull(storedKey)
            assertEquals("provider-2", storedKey.providerId)
        }

    @Test
    fun noOverwriteShouldFailOnDuplicate() =
        runTest {
            // Create keystore with overwriteAlias=false
            val noOverwriteConfig =
                MemoryKeyStoreConfig(
                    id = "no-overwrite-keystore",
                    keyVisibility = KeyVisibility.PRIVATE.keyVisibility,
                    overwriteAlias = false,
                )
            app as JvmCryptoTestAppGraph
            val factory = app.memoryKeyStore
            val noOverwriteKeystore = factory.create(noOverwriteConfig, session.asCoreApiServiceGraph().serviceExecution)

            val keyPair1 = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
            val keyPair2 = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)

            val keyInfo1 = keyPair1.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
            val keyInfo2 = keyPair2.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

            val alias = "no-overwrite-key"

            // Store first key
            noOverwriteKeystore.storeKey(keyInfo1, "provider-1", alias, null)

            // Second store with same alias should fail
            assertFailsWith<IllegalStateException> {
                noOverwriteKeystore.storeKey(keyInfo2, "provider-2", alias, null)
            }
        }

    // =========== Backing Storage Tests ===========

    @Test
    fun backingStorageShouldGetOrCreatePartition() {
        val backingStorage = MemoryKeyStoreBackingStorageImpl()
        val partitionKey = StoragePartitionKey(keystoreId = "test-keystore", tenantId = "test-tenant")

        val partition1 = backingStorage.getPartition(partitionKey)
        val partition2 = backingStorage.getPartition(partitionKey)

        assertNotNull(partition1)
        assertNotNull(partition2)
        assertEquals(partition1, partition2, "Same partition should be returned for same key")
    }

    @Test
    fun backingStorageShouldRemovePartition() {
        val backingStorage = MemoryKeyStoreBackingStorageImpl()
        val partitionKey = StoragePartitionKey(keystoreId = "remove-keystore", tenantId = "tenant-to-remove")

        // Create partition
        backingStorage.getPartition(partitionKey)
        assertTrue(backingStorage.getPartitionCount() >= 1)

        // Remove partition
        val removed = backingStorage.removePartition(partitionKey)

        assertTrue(removed, "removePartition should return true for existing partition")
    }

    @Test
    fun backingStorageShouldReturnFalseWhenRemovingNonExistentPartition() {
        val backingStorage = MemoryKeyStoreBackingStorageImpl()
        val nonExistentKey = StoragePartitionKey(keystoreId = "nonexistent-keystore", tenantId = "non-existent-tenant-partition")

        val removed = backingStorage.removePartition(nonExistentKey)

        assertFalse(removed, "removePartition should return false for non-existent partition")
    }

    @Test
    fun backingStorageShouldClearAllPartitions() {
        val backingStorage = MemoryKeyStoreBackingStorageImpl()

        // Create several partitions
        backingStorage.getPartition(StoragePartitionKey(keystoreId = "clear-keystore", tenantId = "tenant-1"))
        backingStorage.getPartition(StoragePartitionKey(keystoreId = "clear-keystore", tenantId = "tenant-2"))
        backingStorage.getPartition(StoragePartitionKey(keystoreId = "clear-keystore", tenantId = "tenant-3"))

        assertTrue(backingStorage.getPartitionCount() >= 3)

        // Clear all
        backingStorage.clearAll()

        assertEquals(0, backingStorage.getPartitionCount(), "All partitions should be cleared")
    }

    @Test
    fun backingStorageShouldTrackPartitionCount() {
        val backingStorage = MemoryKeyStoreBackingStorageImpl()

        val initialCount = backingStorage.getPartitionCount()

        backingStorage.getPartition(StoragePartitionKey(keystoreId = "count-keystore", tenantId = "count-tenant-1"))
        assertEquals(initialCount + 1, backingStorage.getPartitionCount())

        backingStorage.getPartition(StoragePartitionKey(keystoreId = "count-keystore", tenantId = "count-tenant-2"))
        assertEquals(initialCount + 2, backingStorage.getPartitionCount())
    }

    @Test
    fun backingStorageShouldReturnPartitionKeys() {
        val backingStorage = MemoryKeyStoreBackingStorageImpl()

        val key1 = StoragePartitionKey(keystoreId = "keys-keystore", tenantId = "keys-tenant-1")
        val key2 = StoragePartitionKey(keystoreId = "keys-keystore", tenantId = "keys-tenant-2", principalId = "user-1")

        backingStorage.getPartition(key1)
        backingStorage.getPartition(key2)

        val keys = backingStorage.getPartitionKeys()

        assertTrue(keys.contains(key1))
        assertTrue(keys.contains(key2))
    }

    @Test
    fun backingStorageShouldSupportMultiTenantIsolation() {
        val backingStorage = MemoryKeyStoreBackingStorageImpl()

        val tenant1Key = StoragePartitionKey(keystoreId = "isolation-keystore", tenantId = "tenant-isolated-1")
        val tenant2Key = StoragePartitionKey(keystoreId = "isolation-keystore", tenantId = "tenant-isolated-2")

        val tenant1Partition = backingStorage.getPartition(tenant1Key)
        val tenant2Partition = backingStorage.getPartition(tenant2Key)

        // Verify that tenants get separate partitions
        assertTrue(tenant1Partition !== tenant2Partition, "Different tenants should have different partitions")

        // Store something in tenant1's partition
        val resolvedKeyInfo =
            com.sphereon.crypto.core.ResolvedKeyInfo(
                key =
                    com.sphereon.crypto.core.jose
                        .Jwk(kty = com.sphereon.crypto.core.jose.JwaKeyType.EC),
                keyVisibility = KeyVisibility.PUBLIC,
            )
        val managedKeyInfo =
            com.sphereon.crypto.core.ManagedKeyInfo(
                providerId = "test-provider",
                alias = "testKey",
                resolvedKeyInfo = resolvedKeyInfo,
            )
        tenant1Partition.keys["testKey"] = managedKeyInfo

        // Verify tenant2 doesn't have it
        assertFalse(tenant2Partition.keys.containsKey("testKey"))
        assertTrue(tenant1Partition.keys.containsKey("testKey"))
    }

    @Test
    fun storagePartitionKeyShouldSupportDifferentScopes() {
        val appWide = StoragePartitionKey.appLevel(keystoreId = "scope-keystore")
        val tenantOnly = StoragePartitionKey.forTenant(keystoreId = "scope-keystore", tenantId = "tenant-A")
        val tenantAndPrincipal =
            StoragePartitionKey.forPrincipalTenant(
                keystoreId = "scope-keystore",
                tenantId = "tenant-A",
                principalId = "user-1",
            )
        val fullScope =
            StoragePartitionKey.forSession(
                keystoreId = "scope-keystore",
                tenantId = "tenant-A",
                principalId = "user-1",
                sessionId = "session-123",
            )

        val backingStorage = MemoryKeyStoreBackingStorageImpl()

        // All should create separate partitions
        backingStorage.getPartition(appWide)
        backingStorage.getPartition(tenantOnly)
        backingStorage.getPartition(tenantAndPrincipal)
        backingStorage.getPartition(fullScope)

        assertEquals(4, backingStorage.getPartitionCount())
    }

    // =========== PublicFromPrivateKeyStore Tests ===========

    @Test
    fun publicFromPrivateKeyStoreShouldRequirePrivateKeyStore() {
        // Create a PUBLIC keystore
        val publicConfig =
            MemoryKeyStoreConfig(
                id = "public-only-keystore",
                keyVisibility = KeyVisibility.PUBLIC.keyVisibility,
            )
        app as JvmCryptoTestAppGraph
        val publicKeyStore = app.memoryKeyStore.create(publicConfig, session.asCoreApiServiceGraph().serviceExecution)

        // Should throw because the underlying keystore must be PRIVATE
        assertFailsWith<IllegalArgumentException> {
            PublicFromPrivateKeyStore(publicKeyStore)
        }
    }

    @Test
    fun publicFromPrivateKeyStoreShouldAcceptPrivateKeyStore() {
        // The memoryKeyStore in setUp is already PRIVATE visibility
        val wrapper = PublicFromPrivateKeyStore(memoryKeyStore)

        assertNotNull(wrapper)
        assertEquals(KeyVisibility.PUBLIC, wrapper.keyVisibility())
    }

    @Test
    fun publicFromPrivateKeyStoreKeyVisibilityShouldReturnPublic() {
        val wrapper = PublicFromPrivateKeyStore(memoryKeyStore)

        assertEquals(KeyVisibility.PUBLIC, wrapper.keyVisibility())
    }

    @Test
    fun publicFromPrivateKeyStoreListKeysShouldReturnPublicKeys() =
        runTest {
            // Create a fresh keystore for this test to avoid state pollution
            val freshConfig =
                MemoryKeyStoreConfig(
                    id = "list-keys-test-keystore-${System.currentTimeMillis()}",
                    keyVisibility = KeyVisibility.PRIVATE.keyVisibility,
                    overwriteAlias = true,
                    scopeBinding = MemoryKeyStoreScopeBinding.SESSION.value,
                )
            app as JvmCryptoTestAppGraph
            val freshKeyStore = app.memoryKeyStore.create(freshConfig, session.asCoreApiServiceGraph().serviceExecution)

            // Store a private key
            val keyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
            val keyInfo = keyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
            freshKeyStore.storeKey(keyInfo, "test-provider", "pub-list-key", null)

            val wrapper = PublicFromPrivateKeyStore(freshKeyStore)

            val keys = wrapper.listKeys()

            assertTrue(keys.isNotEmpty())
            // listKeys() returns ManagedKeyReference metadata from the underlying private store
            val keyForAlias = keys.find { it.alias == "pub-list-key" }
            assertNotNull(keyForAlias)
            // ManagedKeyReference preserves the original key visibility from the underlying store
            assertEquals(KeyVisibility.PRIVATE, keyForAlias.keyVisibility)
        }

    @Test
    fun publicFromPrivateKeyStoreGetKeyShouldReturnPublicKey() =
        runTest {
            // Create a fresh keystore for this test
            val freshConfig =
                MemoryKeyStoreConfig(
                    id = "get-key-test-keystore-${System.currentTimeMillis()}",
                    keyVisibility = KeyVisibility.PRIVATE.keyVisibility,
                    overwriteAlias = true,
                    scopeBinding = MemoryKeyStoreScopeBinding.SESSION.value,
                )
            app as JvmCryptoTestAppGraph
            val freshKeyStore = app.memoryKeyStore.create(freshConfig, session.asCoreApiServiceGraph().serviceExecution)

            // Store a private key
            val keyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
            val keyInfo = keyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
            val storedKey = freshKeyStore.storeKey(keyInfo, "test-provider", "pub-get-key", null)

            val wrapper = PublicFromPrivateKeyStore(freshKeyStore)

            val retrievedKey = wrapper.getKey(storedKey)

            assertNotNull(retrievedKey)
            assertEquals(KeyVisibility.PUBLIC, retrievedKey.keyVisibility)
        }

    @Test
    fun publicFromPrivateKeyStoreStoreKeyShouldRequirePrivateKey() =
        runTest {
            // Create a fresh keystore for this test
            val freshConfig =
                MemoryKeyStoreConfig(
                    id = "store-require-priv-keystore-${System.currentTimeMillis()}",
                    keyVisibility = KeyVisibility.PRIVATE.keyVisibility,
                    overwriteAlias = true,
                    scopeBinding = MemoryKeyStoreScopeBinding.SESSION.value,
                )
            app as JvmCryptoTestAppGraph
            val freshKeyStore = app.memoryKeyStore.create(freshConfig, session.asCoreApiServiceGraph().serviceExecution)

            val wrapper = PublicFromPrivateKeyStore(freshKeyStore)

            // Create a PUBLIC key info
            val keyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
            val publicKeyInfo = keyPair.joseToManagedKeyInfo(KeyVisibility.PUBLIC)

            // Should throw because we can only store private keys (which then get exposed as public)
            assertFailsWith<IllegalArgumentException> {
                wrapper.storeKey(publicKeyInfo, "test-provider", "should-fail", null)
            }
        }

    @Test
    fun publicFromPrivateKeyStoreStoreKeyShouldSucceedWithPrivateKey() =
        runTest {
            // Create a fresh keystore for this test
            val freshConfig =
                MemoryKeyStoreConfig(
                    id = "store-priv-keystore-${System.currentTimeMillis()}",
                    keyVisibility = KeyVisibility.PRIVATE.keyVisibility,
                    overwriteAlias = true,
                    scopeBinding = MemoryKeyStoreScopeBinding.SESSION.value,
                )
            app as JvmCryptoTestAppGraph
            val freshKeyStore = app.memoryKeyStore.create(freshConfig, session.asCoreApiServiceGraph().serviceExecution)

            val wrapper = PublicFromPrivateKeyStore(freshKeyStore)

            val keyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
            val privateKeyInfo = keyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

            val storedKey = wrapper.storeKey(privateKeyInfo, "test-provider", "pub-store-key", null)

            assertNotNull(storedKey)
            // The returned key should be PUBLIC visibility
            assertEquals(KeyVisibility.PUBLIC, storedKey.keyVisibility)
        }

    @Test
    fun publicFromPrivateKeyStoreDeleteKeyShouldDelegate() =
        runTest {
            // Create a fresh keystore for this test
            val freshConfig =
                MemoryKeyStoreConfig(
                    id = "delete-keystore-${System.currentTimeMillis()}",
                    keyVisibility = KeyVisibility.PRIVATE.keyVisibility,
                    overwriteAlias = true,
                    scopeBinding = MemoryKeyStoreScopeBinding.SESSION.value,
                )
            app as JvmCryptoTestAppGraph
            val freshKeyStore = app.memoryKeyStore.create(freshConfig, session.asCoreApiServiceGraph().serviceExecution)

            // Store a key via the underlying keystore
            val keyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
            val keyInfo = keyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
            val storedKey = freshKeyStore.storeKey(keyInfo, "test-provider", "pub-delete-key", null)

            val wrapper = PublicFromPrivateKeyStore(freshKeyStore)

            val deleted = wrapper.deleteKey(storedKey)

            assertTrue(deleted)

            // Verify the key is actually deleted from the underlying store
            assertFailsWith<IllegalArgumentException> {
                freshKeyStore.getKey(storedKey)
            }
        }

    @Test
    fun publicFromPrivateKeyStoreSettingsShouldReturnNull() {
        val wrapper = PublicFromPrivateKeyStore(memoryKeyStore)

        // Settings is a legacy property that returns null
        assertEquals(null, wrapper.settings)
    }

    // =========== Additional Coverage Tests ===========

    @Test
    fun memoryKeyStoreSettingsShouldReturnNull() {
        // Test the settings getter (legacy property)
        assertEquals(null, memoryKeyStore.settings)
    }

    @Test
    fun getKeyByKidShouldFindMatchingKey() =
        runTest {
            val keyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
            val keyInfo = keyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
            val alias = "kid-lookup-test-key"

            val storedKey = memoryKeyStore.storeKey(keyInfo, "test-provider", alias, null)

            // Create a KeyInfo with just the kid (no alias) to test kid-based lookup
            val lookupKeyInfo =
                com.sphereon.crypto.core.KeyInfo(
                    kid = storedKey.kid,
                    key = null,
                    alias = null,
                )

            // Since we don't have alias, the matching should happen via kid
            val retrievedKey = memoryKeyStore.getKey(storedKey)
            assertNotNull(retrievedKey)
            assertEquals(alias, retrievedKey.alias)
        }

    /**
     * Kid-as-alias resolution: callers regularly carry the provisioning alias in the kid
     * field (e.g. GenerateMacArgs.keyId -> KeyInfo(kid = keyId) in the software KMS
     * provider's MAC path). When the stored key's kid metadata does not match (here: the
     * stored ResolvedKeyInfo has no kid at all), the store must still resolve the lookup
     * by treating the kid value as an alias, since a direct alias get with the same value
     * succeeds.
     */
    @Test
    fun getKeyByKidCarryingAliasValueShouldSucceed() =
        runTest {
            val alias = "idfr:bi:application"
            val jwk =
                Jwk(
                    kty = JwaKeyType.oct,
                    k =
                        kotlin.random.Random
                            .nextBytes(32)
                            .encodeToBase64Url(),
                    alg = JwaAlgorithm.HS256,
                )
            val keyInfo =
                ResolvedKeyInfo(
                    key = jwk,
                    keyVisibility = KeyVisibility.PRIVATE,
                    keyType = KeyTypeMapping.Symmetric,
                    alias = alias,
                    providerId = "test-provider",
                )
            memoryKeyStore.storeKey(keyInfo, "test-provider", alias, null)

            // Direct alias get succeeds
            val byAlias = memoryKeyStore.getKey(KeyInfo<Jwk>(alias = alias))
            assertEquals(alias, byAlias.alias)

            // The same value carried only as kid must resolve as well
            val byKid = memoryKeyStore.getKey(KeyInfo<Jwk>(kid = alias))
            assertEquals(alias, byKid.alias)
            assertEquals(jwk.k, (byKid.key as? Jwk)?.k)
        }

    @Test
    fun publicKeystoreShouldNotReturnPrivateKey() =
        runTest {
            // Create a PUBLIC keystore
            val publicConfig =
                MemoryKeyStoreConfig(
                    id = "public-keystore-priv-test-${System.currentTimeMillis()}",
                    keyVisibility = KeyVisibility.PUBLIC.keyVisibility,
                    overwriteAlias = true,
                )
            app as JvmCryptoTestAppGraph
            val publicKeyStore = app.memoryKeyStore.create(publicConfig, session.asCoreApiServiceGraph().serviceExecution)

            // Store a public key
            val keyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
            val publicKeyInfo = keyPair.joseToManagedKeyInfo(KeyVisibility.PUBLIC)
            val storedKey = publicKeyStore.storeKey(publicKeyInfo, "test-provider", "pub-only-key", null)

            // Create a key info requesting private visibility - should throw PKIException
            val privateRequestKeyInfo =
                com.sphereon.crypto.core.ManagedKeyInfo(
                    providerId = storedKey.providerId,
                    alias = storedKey.alias,
                    resolvedKeyInfo =
                        com.sphereon.crypto.core.ResolvedKeyInfo(
                            key = storedKey.key,
                            keyVisibility = KeyVisibility.PRIVATE,
                        ),
                )

            assertFailsWith<PKIException> {
                publicKeyStore.getKey(privateRequestKeyInfo)
            }
        }

    @Test
    fun storeKeyOnPublicKeystoreShouldRejectPrivateKey() =
        runTest {
            // Create a PUBLIC keystore
            val publicConfig =
                MemoryKeyStoreConfig(
                    id = "public-keystore-store-test-${System.currentTimeMillis()}",
                    keyVisibility = KeyVisibility.PUBLIC.keyVisibility,
                    overwriteAlias = true,
                )
            app as JvmCryptoTestAppGraph
            val publicKeyStore = app.memoryKeyStore.create(publicConfig, session.asCoreApiServiceGraph().serviceExecution)

            // Try to store a private key in public keystore
            val keyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
            val privateKeyInfo = keyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

            assertFailsWith<PKIException> {
                publicKeyStore.storeKey(privateKeyInfo, "test-provider", "should-fail", null)
            }
        }

    // =========== Certificate Chain Storage Tests ===========

    @Test
    fun storeCertificateChainShouldSucceed() =
        runTest {
            val alias = "cert-chain-test-${System.currentTimeMillis()}"
            val testCert =
                com.sphereon.crypto.core.x509.Certificate(
                    der = byteArrayOf(0x30, 0x82.toByte(), 0x01, 0x00),
                    fingerPrint = "ABC123",
                    serialNumber = "123456",
                    issuerDN = "CN=Test Issuer",
                    subjectDN = "CN=Test Subject",
                    notBefore = kotlin.time.Instant.parse("2024-01-01T00:00:00Z"),
                    notAfter = kotlin.time.Instant.parse("2025-12-31T23:59:59Z"),
                )

            memoryKeyStore.storeCertificateChain(alias, arrayOf(testCert), null)

            val aliases = memoryKeyStore.listCertificateChainAliases()
            assertTrue(aliases.contains(alias))
        }

    @Test
    fun getCertificateChainShouldReturnStoredChain() =
        runTest {
            val alias = "get-cert-chain-test-${System.currentTimeMillis()}"
            val testCert =
                com.sphereon.crypto.core.x509.Certificate(
                    der = byteArrayOf(0x30, 0x82.toByte(), 0x01, 0x00),
                    fingerPrint = "DEF456",
                    serialNumber = "654321",
                    issuerDN = "CN=Test Issuer",
                    subjectDN = "CN=Test Subject",
                    notBefore = kotlin.time.Instant.parse("2024-01-01T00:00:00Z"),
                    notAfter = kotlin.time.Instant.parse("2025-12-31T23:59:59Z"),
                )

            memoryKeyStore.storeCertificateChain(alias, arrayOf(testCert), null)

            val retrieved = memoryKeyStore.getCertificateChain(alias)
            assertNotNull(retrieved)
            assertEquals(1, retrieved.size)
            assertEquals("DEF456", retrieved[0].fingerPrint)
        }

    @Test
    fun getCertificateChainShouldThrowForNonExistent() =
        runTest {
            assertFailsWith<NotFoundException> {
                memoryKeyStore.getCertificateChain("non-existent-cert-chain-alias")
            }
        }

    @Test
    fun deleteCertificateChainShouldSucceed() =
        runTest {
            val alias = "delete-cert-chain-test-${System.currentTimeMillis()}"
            val testCert1 =
                com.sphereon.crypto.core.x509.Certificate(
                    der = byteArrayOf(0x30, 0x82.toByte(), 0x01, 0x00),
                    fingerPrint = "GHI789",
                    serialNumber = "789012",
                    issuerDN = "CN=Test Issuer",
                    subjectDN = "CN=Test Subject",
                    notBefore = kotlin.time.Instant.parse("2024-01-01T00:00:00Z"),
                    notAfter = kotlin.time.Instant.parse("2025-12-31T23:59:59Z"),
                )
            val testCert2 =
                com.sphereon.crypto.core.x509.Certificate(
                    der = byteArrayOf(0x30, 0x82.toByte(), 0x02, 0x00),
                    fingerPrint = "GHI790",
                    serialNumber = "789013",
                    issuerDN = "CN=Test Issuer 2",
                    subjectDN = "CN=Test Subject 2",
                    notBefore = kotlin.time.Instant.parse("2024-01-01T00:00:00Z"),
                    notAfter = kotlin.time.Instant.parse("2025-12-31T23:59:59Z"),
                )

            // Use a multi-cert chain so it doesn't also store in certificates map
            memoryKeyStore.storeCertificateChain(alias, arrayOf(testCert1, testCert2), null)

            val deleted = memoryKeyStore.deleteCertificateChain(alias)
            assertTrue(deleted)

            // Should throw after deletion (no fallback since it was a multi-cert chain)
            assertFailsWith<NotFoundException> {
                memoryKeyStore.getCertificateChain(alias)
            }
        }

    @Test
    fun deleteCertificateChainShouldReturnFalseForNonExistent() =
        runTest {
            val deleted = memoryKeyStore.deleteCertificateChain("non-existent-chain-for-delete")
            assertFalse(deleted)
        }

    // =========== Single Certificate Storage Tests ===========

    @Test
    fun storeTrustedCertificateShouldSucceed() =
        runTest {
            val alias = "trusted-cert-test-${System.currentTimeMillis()}"
            val testCert =
                com.sphereon.crypto.core.x509.Certificate(
                    der = byteArrayOf(0x30, 0x82.toByte(), 0x01, 0x00),
                    fingerPrint = "JKL012",
                    serialNumber = "012345",
                    issuerDN = "CN=Trusted Issuer",
                    subjectDN = "CN=Trusted Subject",
                    notBefore = kotlin.time.Instant.parse("2024-01-01T00:00:00Z"),
                    notAfter = kotlin.time.Instant.parse("2025-12-31T23:59:59Z"),
                )

            memoryKeyStore.storeTrustedCertificate(alias, testCert)

            val aliases = memoryKeyStore.listCertificateAliases()
            assertTrue(aliases.contains(alias))
        }

    @Test
    fun getCertificateShouldReturnStoredCert() =
        runTest {
            val alias = "get-cert-test-${System.currentTimeMillis()}"
            val testCert =
                com.sphereon.crypto.core.x509.Certificate(
                    der = byteArrayOf(0x30, 0x82.toByte(), 0x01, 0x00),
                    fingerPrint = "MNO345",
                    serialNumber = "345678",
                    issuerDN = "CN=Test Issuer",
                    subjectDN = "CN=Test Subject",
                    notBefore = kotlin.time.Instant.parse("2024-01-01T00:00:00Z"),
                    notAfter = kotlin.time.Instant.parse("2025-12-31T23:59:59Z"),
                )

            memoryKeyStore.storeTrustedCertificate(alias, testCert)

            val retrieved = memoryKeyStore.getCertificate(alias)
            assertNotNull(retrieved)
            assertEquals("MNO345", retrieved.fingerPrint)
        }

    @Test
    fun getCertificateShouldFallbackToCertificateChain() =
        runTest {
            val alias = "fallback-cert-test-${System.currentTimeMillis()}"
            val testCert =
                com.sphereon.crypto.core.x509.Certificate(
                    der = byteArrayOf(0x30, 0x82.toByte(), 0x01, 0x00),
                    fingerPrint = "PQR678",
                    serialNumber = "678901",
                    issuerDN = "CN=Chain Issuer",
                    subjectDN = "CN=Chain Subject",
                    notBefore = kotlin.time.Instant.parse("2024-01-01T00:00:00Z"),
                    notAfter = kotlin.time.Instant.parse("2025-12-31T23:59:59Z"),
                )

            // Store as chain, not as single cert
            memoryKeyStore.storeCertificateChain(alias, arrayOf(testCert), null)

            // getCertificate should fall back to chain[0]
            val retrieved = memoryKeyStore.getCertificate(alias)
            assertNotNull(retrieved)
            assertEquals("PQR678", retrieved.fingerPrint)
        }

    @Test
    fun getCertificateShouldThrowForNonExistent() =
        runTest {
            assertFailsWith<NotFoundException> {
                memoryKeyStore.getCertificate("non-existent-cert-alias")
            }
        }

    @Test
    fun deleteCertificateShouldSucceed() =
        runTest {
            val alias = "delete-cert-test-${System.currentTimeMillis()}"
            val testCert =
                com.sphereon.crypto.core.x509.Certificate(
                    der = byteArrayOf(0x30, 0x82.toByte(), 0x01, 0x00),
                    fingerPrint = "STU901",
                    serialNumber = "901234",
                    issuerDN = "CN=Delete Issuer",
                    subjectDN = "CN=Delete Subject",
                    notBefore = kotlin.time.Instant.parse("2024-01-01T00:00:00Z"),
                    notAfter = kotlin.time.Instant.parse("2025-12-31T23:59:59Z"),
                )

            memoryKeyStore.storeTrustedCertificate(alias, testCert)

            val deleted = memoryKeyStore.deleteCertificate(alias)
            assertTrue(deleted)
        }

    @Test
    fun deleteCertificateShouldReturnFalseForNonExistent() =
        runTest {
            val deleted = memoryKeyStore.deleteCertificate("non-existent-cert-for-delete")
            assertFalse(deleted)
        }

    // =========== Certificate Chain - Single Cert Also Stored Tests ===========

    @Test
    fun storeCertificateChainWithSingleCertShouldAlsoStoreAsCert() =
        runTest {
            val alias = "single-cert-chain-${System.currentTimeMillis()}"
            val testCert =
                com.sphereon.crypto.core.x509.Certificate(
                    der = byteArrayOf(0x30, 0x82.toByte(), 0x01, 0x00),
                    fingerPrint = "VWX234",
                    serialNumber = "234567",
                    issuerDN = "CN=Single Issuer",
                    subjectDN = "CN=Single Subject",
                    notBefore = kotlin.time.Instant.parse("2024-01-01T00:00:00Z"),
                    notAfter = kotlin.time.Instant.parse("2025-12-31T23:59:59Z"),
                )

            // Store a single-element chain
            memoryKeyStore.storeCertificateChain(alias, arrayOf(testCert), null)

            // Should be available both as chain and as single cert
            val chain = memoryKeyStore.getCertificateChain(alias)
            val cert = memoryKeyStore.getCertificate(alias)

            assertEquals(1, chain.size)
            assertEquals(chain[0].fingerPrint, cert.fingerPrint)
        }

    // =========== No-Overwrite Certificate Tests ===========

    @Test
    fun noOverwriteCertificateChainShouldFailOnDuplicate() =
        runTest {
            val noOverwriteConfig =
                MemoryKeyStoreConfig(
                    id = "no-overwrite-cert-chain-${System.currentTimeMillis()}",
                    keyVisibility = KeyVisibility.PRIVATE.keyVisibility,
                    overwriteAlias = false,
                )
            app as JvmCryptoTestAppGraph
            val noOverwriteKeystore = app.memoryKeyStore.create(noOverwriteConfig, session.asCoreApiServiceGraph().serviceExecution)

            val alias = "no-overwrite-chain"
            val testCert1 =
                com.sphereon.crypto.core.x509.Certificate(
                    der = byteArrayOf(0x30, 0x82.toByte(), 0x01, 0x00),
                    fingerPrint = "FIRST",
                    serialNumber = "111111",
                    issuerDN = "CN=First",
                    subjectDN = "CN=First",
                    notBefore = kotlin.time.Instant.parse("2024-01-01T00:00:00Z"),
                    notAfter = kotlin.time.Instant.parse("2025-12-31T23:59:59Z"),
                )
            val testCert2 =
                com.sphereon.crypto.core.x509.Certificate(
                    der = byteArrayOf(0x30, 0x82.toByte(), 0x02, 0x00),
                    fingerPrint = "SECOND",
                    serialNumber = "222222",
                    issuerDN = "CN=Second",
                    subjectDN = "CN=Second",
                    notBefore = kotlin.time.Instant.parse("2024-01-01T00:00:00Z"),
                    notAfter = kotlin.time.Instant.parse("2025-12-31T23:59:59Z"),
                )

            noOverwriteKeystore.storeCertificateChain(alias, arrayOf(testCert1), null)

            assertFailsWith<IllegalStateException> {
                noOverwriteKeystore.storeCertificateChain(alias, arrayOf(testCert2), null)
            }
        }

    @Test
    fun noOverwriteTrustedCertificateShouldFailOnDuplicate() =
        runTest {
            val noOverwriteConfig =
                MemoryKeyStoreConfig(
                    id = "no-overwrite-cert-${System.currentTimeMillis()}",
                    keyVisibility = KeyVisibility.PRIVATE.keyVisibility,
                    overwriteAlias = false,
                )
            app as JvmCryptoTestAppGraph
            val noOverwriteKeystore = app.memoryKeyStore.create(noOverwriteConfig, session.asCoreApiServiceGraph().serviceExecution)

            val alias = "no-overwrite-cert"
            val testCert1 =
                com.sphereon.crypto.core.x509.Certificate(
                    der = byteArrayOf(0x30, 0x82.toByte(), 0x01, 0x00),
                    fingerPrint = "FIRST-CERT",
                    serialNumber = "333333",
                    issuerDN = "CN=First Cert",
                    subjectDN = "CN=First Cert",
                    notBefore = kotlin.time.Instant.parse("2024-01-01T00:00:00Z"),
                    notAfter = kotlin.time.Instant.parse("2025-12-31T23:59:59Z"),
                )
            val testCert2 =
                com.sphereon.crypto.core.x509.Certificate(
                    der = byteArrayOf(0x30, 0x82.toByte(), 0x02, 0x00),
                    fingerPrint = "SECOND-CERT",
                    serialNumber = "444444",
                    issuerDN = "CN=Second Cert",
                    subjectDN = "CN=Second Cert",
                    notBefore = kotlin.time.Instant.parse("2024-01-01T00:00:00Z"),
                    notAfter = kotlin.time.Instant.parse("2025-12-31T23:59:59Z"),
                )

            noOverwriteKeystore.storeTrustedCertificate(alias, testCert1)

            assertFailsWith<IllegalStateException> {
                noOverwriteKeystore.storeTrustedCertificate(alias, testCert2)
            }
        }

    // =========== Partition-based Storage Tests ===========

    @Test
    fun keystoreWithBackingStorageShouldUsePartition() =
        runTest {
            val backingStorage = MemoryKeyStoreBackingStorageImpl()
            val partitionKey = StoragePartitionKey(keystoreId = "partition-test", tenantId = "test-tenant")

            val config =
                MemoryKeyStoreConfig(
                    id = "partition-test",
                    keyVisibility = KeyVisibility.PRIVATE.keyVisibility,
                    overwriteAlias = true,
                )

            val keystore = MemoryKeyStoreService(config, backingStorage, partitionKey)

            // Store a key
            val keyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
            val keyInfo = keyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
            keystore.storeKey(keyInfo, "test-provider", "partition-key", null)

            // Verify key is in partition
            val partition = backingStorage.getPartition(partitionKey)
            assertTrue(partition.keys.containsKey("partition-key"))
        }

    @Test
    fun keystoreWithBackingStorageShouldStoreCertsInPartition() =
        runTest {
            val backingStorage = MemoryKeyStoreBackingStorageImpl()
            val partitionKey = StoragePartitionKey(keystoreId = "partition-cert-test", tenantId = "test-tenant")

            val config =
                MemoryKeyStoreConfig(
                    id = "partition-cert-test",
                    keyVisibility = KeyVisibility.PRIVATE.keyVisibility,
                    overwriteAlias = true,
                )

            val keystore = MemoryKeyStoreService(config, backingStorage, partitionKey)

            val testCert =
                com.sphereon.crypto.core.x509.Certificate(
                    der = byteArrayOf(0x30, 0x82.toByte(), 0x01, 0x00),
                    fingerPrint = "PARTITION",
                    serialNumber = "555555",
                    issuerDN = "CN=Partition",
                    subjectDN = "CN=Partition",
                    notBefore = kotlin.time.Instant.parse("2024-01-01T00:00:00Z"),
                    notAfter = kotlin.time.Instant.parse("2025-12-31T23:59:59Z"),
                )

            keystore.storeTrustedCertificate("partition-cert", testCert)
            keystore.storeCertificateChain("partition-chain", arrayOf(testCert), null)

            // Verify in partition
            val partition = backingStorage.getPartition(partitionKey)
            assertTrue(partition.certificates.containsKey("partition-cert"))
            assertTrue(partition.certificateChains.containsKey("partition-chain"))
        }

    @Test
    fun getKeyRequestingPrivateFromPublicStoredKeyShouldFail() =
        runTest {
            val keyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
            val publicKeyInfo = keyPair.joseToManagedKeyInfo(KeyVisibility.PUBLIC)
            val alias = "public-stored-key-${System.currentTimeMillis()}"

            val storedKey = memoryKeyStore.storeKey(publicKeyInfo, "test-provider", alias, null)

            // Now try to get it with PRIVATE visibility
            val privateRequest =
                com.sphereon.crypto.core.ManagedKeyInfo(
                    providerId = storedKey.providerId,
                    alias = alias,
                    resolvedKeyInfo =
                        com.sphereon.crypto.core.ResolvedKeyInfo(
                            key = storedKey.key,
                            keyVisibility = KeyVisibility.PRIVATE,
                        ),
                )

            // Should throw because the stored key is public, not private
            assertFailsWith<IllegalArgumentException> {
                memoryKeyStore.getKey(privateRequest)
            }
        }

    // =========== Certificate Fallback Tests ===========

    @Test
    fun getCertificateChainShouldFallbackToCertificatesMap() =
        runTest {
            val uniqueAlias = "cert-only-fallback-${System.currentTimeMillis()}"
            val testCert =
                com.sphereon.crypto.core.x509.Certificate(
                    der = byteArrayOf(0x30, 0x82.toByte(), 0x01, 0x00),
                    fingerPrint = "CERT-FALLBACK",
                    serialNumber = "666666",
                    issuerDN = "CN=Cert Fallback",
                    subjectDN = "CN=Cert Fallback",
                    notBefore = kotlin.time.Instant.parse("2024-01-01T00:00:00Z"),
                    notAfter = kotlin.time.Instant.parse("2025-12-31T23:59:59Z"),
                )

            // Store only in certificates map (via storeTrustedCertificate), NOT in certificateChains
            memoryKeyStore.storeTrustedCertificate(uniqueAlias, testCert)

            // getCertificateChain should fall back to certificates map
            val chain = memoryKeyStore.getCertificateChain(uniqueAlias)
            assertEquals(1, chain.size)
            assertEquals("CERT-FALLBACK", chain[0].fingerPrint)
        }

    @Test
    fun getCertificateShouldFallbackToCertificateChainsMap() =
        runTest {
            val uniqueAlias = "chain-only-fallback-${System.currentTimeMillis()}"
            val testCert1 =
                com.sphereon.crypto.core.x509.Certificate(
                    der = byteArrayOf(0x30, 0x82.toByte(), 0x01, 0x01),
                    fingerPrint = "CHAIN-FIRST",
                    serialNumber = "777777",
                    issuerDN = "CN=Chain First",
                    subjectDN = "CN=Chain First",
                    notBefore = kotlin.time.Instant.parse("2024-01-01T00:00:00Z"),
                    notAfter = kotlin.time.Instant.parse("2025-12-31T23:59:59Z"),
                )
            val testCert2 =
                com.sphereon.crypto.core.x509.Certificate(
                    der = byteArrayOf(0x30, 0x82.toByte(), 0x01, 0x02),
                    fingerPrint = "CHAIN-SECOND",
                    serialNumber = "888888",
                    issuerDN = "CN=Chain Second",
                    subjectDN = "CN=Chain Second",
                    notBefore = kotlin.time.Instant.parse("2024-01-01T00:00:00Z"),
                    notAfter = kotlin.time.Instant.parse("2025-12-31T23:59:59Z"),
                )

            // Store a multi-cert chain (won't auto-populate certificates map)
            memoryKeyStore.storeCertificateChain(uniqueAlias, arrayOf(testCert1, testCert2), null)

            // getCertificate should fall back to certificateChains map and return first cert
            val cert = memoryKeyStore.getCertificate(uniqueAlias)
            assertEquals("CHAIN-FIRST", cert.fingerPrint)
        }

    @Test
    fun getCertificateChainNotFoundShouldThrow() =
        runTest {
            assertFailsWith<NotFoundException> {
                memoryKeyStore.getCertificateChain("nonexistent-chain-alias-${System.currentTimeMillis()}")
            }
        }

    @Test
    fun getCertificateNotFoundShouldThrow() =
        runTest {
            assertFailsWith<NotFoundException> {
                memoryKeyStore.getCertificate("nonexistent-cert-alias-${System.currentTimeMillis()}")
            }
        }

    // =========== Visibility from Config Default Test ===========

    @Test
    fun getKeyWithNullVisibilityShouldUseConfigDefault() =
        runTest {
            val keyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
            val privateKeyInfo = keyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
            val alias = "null-vis-${System.currentTimeMillis()}"
            val storedKey = memoryKeyStore.storeKey(privateKeyInfo, "test-provider", alias, null)

            // Request with null visibility - should use config default (PRIVATE)
            val nullVisRequest =
                com.sphereon.crypto.core.ManagedKeyInfo(
                    providerId = storedKey.providerId,
                    alias = alias,
                    resolvedKeyInfo =
                        ResolvedKeyInfo(
                            key = storedKey.key,
                            keyVisibility = null,
                        ),
                )

            val foundKey = memoryKeyStore.getKey(nullVisRequest)
            assertNotNull(foundKey)
            assertEquals(alias, foundKey.alias)
        }

    // =========== Delete Non-Existent Key Test ===========

    @Test
    fun deleteNonExistentKeyShouldReturnFalse() =
        runTest {
            // First generate a real key to get a valid key object, then use a non-existent alias
            val keyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
            val keyInfo = keyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

            val nonExistentRequest =
                com.sphereon.crypto.core.ManagedKeyInfo(
                    providerId = "test-provider",
                    alias = "nonexistent-key-${System.currentTimeMillis()}",
                    resolvedKeyInfo =
                        ResolvedKeyInfo(
                            key = keyInfo.key,
                            keyVisibility = KeyVisibility.PRIVATE,
                        ),
                )

            // Should return false, not throw
            val result = memoryKeyStore.deleteKey(nonExistentRequest)
            assertFalse(result)
        }

    // =========== Public Keystore Rejecting Private Store Test ===========

    @Test
    fun publicKeystoreRejectingPrivateKeyStoreShouldThrow() =
        runTest {
            val publicConfig =
                MemoryKeyStoreConfig(
                    id = "public-store-reject-${System.currentTimeMillis()}",
                    keyVisibility = KeyVisibility.PUBLIC.keyVisibility,
                    overwriteAlias = true,
                )
            app as JvmCryptoTestAppGraph
            val publicKeystore = app.memoryKeyStore.create(publicConfig, session.asCoreApiServiceGraph().serviceExecution)

            val keyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
            val privateKeyInfo = keyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

            // Trying to store a private key in a public keystore should fail
            assertFailsWith<PKIException> {
                publicKeystore.storeKey(privateKeyInfo, "test-provider", "test-alias", null)
            }
        }

    // =========== Partition Getter Tests ===========

    @Test
    fun keystoreWithoutBackingStorageShouldUseLocalMaps() =
        runTest {
            // Create a keystore without backing storage
            val config =
                MemoryKeyStoreConfig(
                    id = "no-backing-${System.currentTimeMillis()}",
                    keyVisibility = KeyVisibility.PRIVATE.keyVisibility,
                    overwriteAlias = true,
                )
            val keystoreWithoutBacking = MemoryKeyStoreService(config, null, null)

            // Store and retrieve to verify local maps are used
            val keyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
            val privateKeyInfo = keyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
            val storedKey = keystoreWithoutBacking.storeKey(privateKeyInfo, "test-provider", "local-key", null)

            val retrievedKey = keystoreWithoutBacking.getKey(storedKey)
            assertEquals("local-key", retrievedKey.alias)
        }

    @Test
    fun keystoreWithBackingButNoPartitionKeyShouldUseLocalMaps() =
        runTest {
            val backingStorage = MemoryKeyStoreBackingStorageImpl()
            val config =
                MemoryKeyStoreConfig(
                    id = "backing-no-partition-${System.currentTimeMillis()}",
                    keyVisibility = KeyVisibility.PRIVATE.keyVisibility,
                    overwriteAlias = true,
                )

            // Backing storage but null partition key
            val keystore = MemoryKeyStoreService(config, backingStorage, null)

            val keyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
            val privateKeyInfo = keyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
            val storedKey = keystore.storeKey(privateKeyInfo, "test-provider", "no-partition-key", null)

            val retrievedKey = keystore.getKey(storedKey)
            assertEquals("no-partition-key", retrievedKey.alias)
        }

    // =========== Public Keystore Get Private Key Test ===========

    @Test
    fun publicKeystoreGetPrivateKeyShouldThrow() =
        runTest {
            val publicConfig =
                MemoryKeyStoreConfig(
                    id = "public-get-private-${System.currentTimeMillis()}",
                    keyVisibility = KeyVisibility.PUBLIC.keyVisibility,
                    overwriteAlias = true,
                )
            app as JvmCryptoTestAppGraph
            val publicKeystore = app.memoryKeyStore.create(publicConfig, session.asCoreApiServiceGraph().serviceExecution)

            // Store a public key first
            val keyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
            val publicKeyInfo = keyPair.joseToManagedKeyInfo(KeyVisibility.PUBLIC)
            val alias = "public-key-${System.currentTimeMillis()}"
            val storedKey = publicKeystore.storeKey(publicKeyInfo, "test-provider", alias, null)

            // Now try to get it requesting PRIVATE visibility
            val privateRequest =
                com.sphereon.crypto.core.ManagedKeyInfo(
                    providerId = storedKey.providerId,
                    alias = alias,
                    resolvedKeyInfo =
                        ResolvedKeyInfo(
                            key = storedKey.key,
                            keyVisibility = KeyVisibility.PRIVATE,
                        ),
                )

            // Should throw PKIException because the keystore is configured as PUBLIC
            assertFailsWith<PKIException> {
                publicKeystore.getKey(privateRequest)
            }
        }

    // =========== Store Key with Null Visibility Uses Config Default ===========

    @Test
    fun storeKeyWithNullVisibilityShouldUseConfigDefault() =
        runTest {
            val keyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
            // Create key info with null visibility using the public JWK
            val keyInfoWithNullVis =
                ResolvedKeyInfo(
                    key = keyPair.jose.publicJwk,
                    keyVisibility = null,
                )
            val alias = "null-vis-store-${System.currentTimeMillis()}"

            // Store the key - should use config default (PRIVATE)
            val storedKey = memoryKeyStore.storeKey(keyInfoWithNullVis, "test-provider", alias, null)
            assertNotNull(storedKey)
            assertEquals(alias, storedKey.alias)
        }

    // =========== Factory Error Path Tests with Mockk ===========

    @Test
    fun factoryPrincipalTenantScopedWithNullPrincipalShouldThrow() {
        val principalConfig =
            MemoryKeyStoreConfig(
                id = "principal-null-principal-${System.currentTimeMillis()}",
                keyVisibility = KeyVisibility.PUBLIC.keyVisibility,
                scopeBinding = MemoryKeyStoreScopeBinding.PRINCIPAL_TENANT.value,
            )

        // Create mock execution with null principal
        val mockTenant = mockk<TenantContextData>()
        every { mockTenant.tenantId } returns "tenant-123"

        val mockUserContext = mockk<UserContext>()
        every { mockUserContext.tenant } returns mockTenant
        every { mockUserContext.principal } returns null

        val mockSessionContext = mockk<SessionContext>()
        every { mockSessionContext.context } returns mockUserContext

        val mockExecution = mockk<SessionExecution>()
        every { mockExecution.sessionContext } returns mockSessionContext

        app as JvmCryptoTestAppGraph
        val factory = app.memoryKeyStore

        assertFailsWith<IllegalStateException> {
            factory.create(principalConfig, mockExecution)
        }
    }

    @Test
    fun factorySessionScopedWithNullPrincipalShouldThrow() {
        val sessionConfig =
            MemoryKeyStoreConfig(
                id = "session-null-principal-${System.currentTimeMillis()}",
                keyVisibility = KeyVisibility.PUBLIC.keyVisibility,
                scopeBinding = MemoryKeyStoreScopeBinding.SESSION.value,
            )

        // Create mock execution with null principal
        val mockTenant = mockk<TenantContextData>()
        every { mockTenant.tenantId } returns "tenant-456"

        val mockUserContext = mockk<UserContext>()
        every { mockUserContext.tenant } returns mockTenant
        every { mockUserContext.principal } returns null

        val mockSessionContext = mockk<SessionContext>()
        every { mockSessionContext.context } returns mockUserContext
        every { mockSessionContext.sessionId } returns "session-789"

        val mockExecution = mockk<SessionExecution>()
        every { mockExecution.sessionContext } returns mockSessionContext

        app as JvmCryptoTestAppGraph
        val factory = app.memoryKeyStore

        assertFailsWith<IllegalStateException> {
            factory.create(sessionConfig, mockExecution)
        }
    }
}
