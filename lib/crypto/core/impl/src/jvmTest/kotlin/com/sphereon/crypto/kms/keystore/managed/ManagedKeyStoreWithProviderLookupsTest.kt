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

package com.sphereon.crypto.kms.keystore.managed

import com.sphereon.core.api.error.NotFoundException
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.PKIException
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.ManagedKeyInfo
import com.sphereon.crypto.core.ManagedKeyInfoType
import com.sphereon.crypto.core.ResolvedKeyInfo
import com.sphereon.crypto.core.ResolvedKeyInfoType
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.generic.KeyTypeMapping
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.kms.KeyResolverRegistry
import com.sphereon.crypto.core.kms.KeyResolverService
import com.sphereon.crypto.core.kms.KmsProvider
import com.sphereon.crypto.core.kms.KmsProviderRegistry
import com.sphereon.crypto.core.kms.model.IdentifierMethod
import com.sphereon.crypto.core.x509.Certificate
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for ManagedKeyStoreWithProviderLookups implementation.
 */
class ManagedKeyStoreWithProviderLookupsTest {

    private val testJwk = Jwk(kty = JwaKeyType.EC, crv = com.sphereon.crypto.core.jose.JwaCurve.P_256, x = "testX", y = "testY")
    private val testResolvedKeyInfo = ResolvedKeyInfo<KeyType>(key = testJwk, keyVisibility = KeyVisibility.PRIVATE)
    private val testManagedKeyInfo = ManagedKeyInfo<KeyType>(
        providerId = "test-provider",
        alias = "test-alias",
        resolvedKeyInfo = testResolvedKeyInfo
    )

    /**
     * Simple test implementation of KmsProviderRegistry that wraps a set of providers.
     */
    private class TestProviderRegistry(providers: Set<KmsProvider>) : KmsProviderRegistry {
        private val providerMap = providers.associateBy { it.id }
        override fun defaultProviderId() = providerMap.keys.firstOrNull() ?: "default"
        override fun getProviderIds() = providerMap.keys.toTypedArray()
        override fun getProviderById(id: String) = providerMap[id]
            ?: throw PKIException("Invalid KMS id $id provider. Valid ids are: ${getProviderIds().joinToString(",")}")
        override fun getProvider(providerId: String?, alg: SignatureAlgorithm?) =
            getProviderById(providerId ?: defaultProviderId())
        override fun getKmsBySignatureAlgorithm(signatureAlgorithm: SignatureAlgorithm) =
            providerMap.values.firstOrNull { it.supportedSignatureAlgorithms().contains(signatureAlgorithm) }
                ?: throw IllegalArgumentException("No provider for algorithm: $signatureAlgorithm")
        override fun registerProvider(provider: KmsProvider, makeDefaultKms: Boolean?) {
            // Not needed for tests
        }
    }

    /**
     * Simple test implementation of KeyResolverRegistry that wraps a set of resolvers.
     */
    private class TestResolverRegistry(resolvers: Set<KeyResolverService>) : KeyResolverRegistry {
        private val resolverMap = resolvers.associateBy { it.getId() }
        override fun defaultResolverId() = resolverMap.keys.firstOrNull() ?: "default"
        override fun getResolverIds() = resolverMap.keys.toTypedArray()
        override fun getResolverById(id: String) = resolverMap[id]
            ?: throw PKIException("Invalid Resolver id $id. Valid ids are: ${getResolverIds().joinToString(",")}")
        override fun getResolverByKeyTypeOrIdentifier(
            identifierMethod: IdentifierMethod?,
            keyType: KeyTypeMapping?,
            resolverId: String?
        ) = resolverMap.values.firstOrNull() ?: throw IllegalArgumentException("No resolvers available")
        override fun registerResolver(resolver: KeyResolverService, makeDefaultResolver: Boolean?) {
            // Not needed for tests
        }
    }

    private fun testProviderRegistry(providers: Set<KmsProvider>) = TestProviderRegistry(providers)
    private fun testResolverRegistry(resolvers: Set<KeyResolverService>) = TestResolverRegistry(resolvers)

    // =========== Settings Property Tests ===========

    @Test
    fun settingsShouldBeNull() {
        val keyStore = ManagedKeyStoreWithProviderLookups(
            testProviderRegistry(emptySet()),
            testResolverRegistry(emptySet())
        )
        assertNull(keyStore.settings)
    }

    // =========== keyVisibility Tests ===========

    @Test
    fun keyVisibilityShouldReturnPrivate() {
        val keyStore = ManagedKeyStoreWithProviderLookups(
            testProviderRegistry(emptySet()),
            testResolverRegistry(emptySet())
        )
        assertEquals(KeyVisibility.PRIVATE, keyStore.keyVisibility())
    }

    // =========== listKeys Tests ===========

    @Test
    fun listKeysShouldAggregateFromAllProviders() = runTest {
        val provider1 = mockk<KmsProvider>()
        val provider2 = mockk<KmsProvider>()

        val key1 = ManagedKeyInfo<KeyType>(providerId = "provider1", alias = "key1", resolvedKeyInfo = testResolvedKeyInfo)
        val key2 = ManagedKeyInfo<KeyType>(providerId = "provider2", alias = "key2", resolvedKeyInfo = testResolvedKeyInfo)

        every { provider1.id } returns "provider1"
        every { provider2.id } returns "provider2"
        coEvery { provider1.listKeys() } returns arrayOf(key1)
        coEvery { provider2.listKeys() } returns arrayOf(key2)

        val keyStore = ManagedKeyStoreWithProviderLookups(
            testProviderRegistry(setOf(provider1, provider2)),
            testResolverRegistry(emptySet())
        )

        val keys = keyStore.listKeys()

        assertEquals(2, keys.size)
        assertTrue(keys.any { it.alias == "key1" })
        assertTrue(keys.any { it.alias == "key2" })
    }

    @Test
    fun listKeysShouldIgnoreProviderExceptions() = runTest {
        val provider1 = mockk<KmsProvider>()
        val provider2 = mockk<KmsProvider>()

        val key1 = ManagedKeyInfo<KeyType>(providerId = "provider1", alias = "key1", resolvedKeyInfo = testResolvedKeyInfo)

        every { provider1.id } returns "provider1"
        every { provider2.id } returns "provider2"
        coEvery { provider1.listKeys() } returns arrayOf(key1)
        coEvery { provider2.listKeys() } throws RuntimeException("Provider doesn't support listing")

        val keyStore = ManagedKeyStoreWithProviderLookups(testProviderRegistry(setOf(provider1, provider2)), testResolverRegistry(emptySet()))

        val keys = keyStore.listKeys()

        // Should still get keys from provider1, despite provider2 throwing
        assertEquals(1, keys.size)
        assertEquals("key1", keys[0].alias)
    }

    @Test
    fun listKeysShouldReturnEmptyWhenNoProviders() = runTest {
        val keyStore = ManagedKeyStoreWithProviderLookups(testProviderRegistry(emptySet()), testResolverRegistry(emptySet()))

        val keys = keyStore.listKeys()

        assertTrue(keys.isEmpty())
    }

    // =========== getKey Tests ===========

    @Test
    fun getKeyShouldUseProviderIdDirectly() = runTest {
        val provider = mockk<KmsProvider>()
        every { provider.id } returns "test-provider"
        coEvery { provider.getKey(any()) } returns testManagedKeyInfo

        val keyStore = ManagedKeyStoreWithProviderLookups(testProviderRegistry(setOf(provider)), testResolverRegistry(emptySet()))

        val keyInfo = KeyInfo<KeyType>(providerId = "test-provider", alias = "test-alias")
        val result = keyStore.getKey(keyInfo)

        assertNotNull(result)
        assertEquals("test-alias", result.alias)
        coVerify { provider.getKey(keyInfo) }
    }

    @Test
    fun getKeyShouldTryResolversFirst() = runTest {
        val provider = mockk<KmsProvider>()
        val resolver = mockk<KeyResolverService>()

        val resolvedWithProvider = ResolvedKeyInfo<KeyType>(
            key = testJwk,
            keyVisibility = KeyVisibility.PRIVATE,
            providerId = "test-provider"
        )

        every { provider.id } returns "test-provider"
        every { resolver.getId() } returns "test-resolver"
        coEvery { resolver.resolvePublicKey(any<KeyInfoType<*>>()) } returns resolvedWithProvider
        coEvery { provider.getKey(any<ResolvedKeyInfoType<*>>()) } returns testManagedKeyInfo

        val keyStore = ManagedKeyStoreWithProviderLookups(testProviderRegistry(setOf(provider)), testResolverRegistry(setOf(resolver)))

        val keyInfo = KeyInfo<KeyType>(alias = "test-alias")
        val result = keyStore.getKey(keyInfo)

        assertNotNull(result)
        coVerify { resolver.resolvePublicKey(keyInfo) }
        coVerify { provider.getKey(resolvedWithProvider) }
    }

    @Test
    fun getKeyShouldFallbackToProvidersWhenResolverFails() = runTest {
        val provider = mockk<KmsProvider>()
        val resolver = mockk<KeyResolverService>()

        every { provider.id } returns "test-provider"
        every { resolver.getId() } returns "test-resolver"
        coEvery { resolver.resolvePublicKey(any<KeyInfoType<*>>()) } throws RuntimeException("Resolver failed")
        coEvery { provider.getKey(any<KeyInfoType<*>>()) } returns testManagedKeyInfo

        val keyStore = ManagedKeyStoreWithProviderLookups(testProviderRegistry(setOf(provider)), testResolverRegistry(setOf(resolver)))

        val keyInfo = KeyInfo<KeyType>(alias = "test-alias")
        val result = keyStore.getKey(keyInfo)

        assertNotNull(result)
        coVerify { provider.getKey(keyInfo) }
    }

    @Test
    fun getKeyShouldTryNextProviderWhenFirstFails() = runTest {
        val provider1 = mockk<KmsProvider>()
        val provider2 = mockk<KmsProvider>()

        every { provider1.id } returns "provider1"
        every { provider2.id } returns "provider2"
        coEvery { provider1.getKey(any<KeyInfoType<*>>()) } throws RuntimeException("Key not found in provider1")
        coEvery { provider2.getKey(any<KeyInfoType<*>>()) } returns testManagedKeyInfo

        val keyStore = ManagedKeyStoreWithProviderLookups(testProviderRegistry(setOf(provider1, provider2)), testResolverRegistry(emptySet()))

        val keyInfo = KeyInfo<KeyType>(alias = "test-alias")
        val result = keyStore.getKey(keyInfo)

        assertNotNull(result)
        coVerify { provider1.getKey(keyInfo) }
        coVerify { provider2.getKey(keyInfo) }
    }

    @Test
    fun getKeyShouldThrowWhenAllProvidersFail() = runTest {
        val provider1 = mockk<KmsProvider>()
        val provider2 = mockk<KmsProvider>()

        every { provider1.id } returns "provider1"
        every { provider2.id } returns "provider2"
        coEvery { provider1.getKey(any<KeyInfoType<*>>()) } throws RuntimeException("Key not found")
        coEvery { provider2.getKey(any<KeyInfoType<*>>()) } throws RuntimeException("Key not found")

        val keyStore = ManagedKeyStoreWithProviderLookups(testProviderRegistry(setOf(provider1, provider2)), testResolverRegistry(emptySet()))

        val keyInfo = KeyInfo<KeyType>(alias = "test-alias")

        val exception = assertFailsWith<IllegalArgumentException> {
            keyStore.getKey(keyInfo)
        }

        assertTrue(exception.message?.contains("Could not find key") == true)
        assertTrue(exception.message?.contains("Tried:") == true)
    }

    @Test
    fun getKeyShouldThrowWhenNoAliasOrKid() = runTest {
        val keyStore = ManagedKeyStoreWithProviderLookups(testProviderRegistry(emptySet()), testResolverRegistry(emptySet()))

        val keyInfo = KeyInfo<KeyType>(providerId = null, alias = null, kid = null)

        val exception = assertFailsWith<IllegalArgumentException> {
            keyStore.getKey(keyInfo)
        }

        assertTrue(exception.message?.contains("no alias or kid provided") == true)
    }

    @Test
    fun getKeyShouldThrowWithEmptyTriedSourcesWhenNoProvidersOrResolvers() = runTest {
        // Test the empty triedSources branch: when alias/kid is provided but no providers or resolvers exist
        val keyStore = ManagedKeyStoreWithProviderLookups(testProviderRegistry(emptySet()), testResolverRegistry(emptySet()))

        val keyInfo = KeyInfo<KeyType>(alias = "test-alias")

        val exception = assertFailsWith<IllegalArgumentException> {
            keyStore.getKey(keyInfo)
        }

        // When no sources were tried, the error message should NOT contain "Tried:"
        assertTrue(exception.message?.contains("Could not find key") == true)
        assertFalse(exception.message?.contains("Tried:") == true, "Message should not contain 'Tried:' when no sources were tried")
    }

    @Test
    fun getKeyShouldWorkWithKidOnly() = runTest {
        val provider = mockk<KmsProvider>()
        every { provider.id } returns "test-provider"
        coEvery { provider.getKey(any<KeyInfoType<*>>()) } returns testManagedKeyInfo

        val keyStore = ManagedKeyStoreWithProviderLookups(testProviderRegistry(setOf(provider)), testResolverRegistry(emptySet()))

        val keyInfo = KeyInfo<KeyType>(kid = "test-kid")
        val result = keyStore.getKey(keyInfo)

        assertNotNull(result)
    }

    @Test
    fun getKeyShouldSkipResolverWhenResolvedKeyHasNoProviderId() = runTest {
        val provider = mockk<KmsProvider>()
        val resolver = mockk<KeyResolverService>()

        // Resolver returns a key without providerId
        val resolvedWithoutProvider = ResolvedKeyInfo<KeyType>(
            key = testJwk,
            keyVisibility = KeyVisibility.PRIVATE,
            providerId = null
        )

        every { provider.id } returns "test-provider"
        every { resolver.getId() } returns "test-resolver"
        coEvery { resolver.resolvePublicKey(any<KeyInfoType<*>>()) } returns resolvedWithoutProvider
        coEvery { provider.getKey(any<KeyInfoType<*>>()) } returns testManagedKeyInfo

        val keyStore = ManagedKeyStoreWithProviderLookups(testProviderRegistry(setOf(provider)), testResolverRegistry(setOf(resolver)))

        val keyInfo = KeyInfo<KeyType>(alias = "test-alias")
        val result = keyStore.getKey(keyInfo)

        assertNotNull(result)
        // Should fall through to provider.getKey with original keyInfo since resolved has no providerId
        coVerify { provider.getKey(keyInfo) }
    }

    // =========== storeKey Tests ===========

    @Test
    fun storeKeyShouldDelegateToProvider() = runTest {
        val provider = mockk<KmsProvider>()
        every { provider.id } returns "test-provider"
        coEvery { provider.storeKey(any(), any(), any(), any()) } returns testManagedKeyInfo

        val keyStore = ManagedKeyStoreWithProviderLookups(testProviderRegistry(setOf(provider)), testResolverRegistry(emptySet()))

        val result = keyStore.storeKey(testResolvedKeyInfo, "test-provider", "test-alias", null)

        assertNotNull(result)
        coVerify { provider.storeKey(testResolvedKeyInfo, "test-provider", "test-alias", null) }
    }

    @Test
    fun storeKeyShouldThrowWhenProviderIdMismatch() = runTest {
        val provider = mockk<KmsProvider>()
        every { provider.id } returns "test-provider"

        val keyStore = ManagedKeyStoreWithProviderLookups(testProviderRegistry(setOf(provider)), testResolverRegistry(emptySet()))

        val keyInfoWithDifferentProvider = ResolvedKeyInfo<KeyType>(
            key = testJwk,
            keyVisibility = KeyVisibility.PRIVATE,
            providerId = "different-provider"
        )

        val exception = assertFailsWith<IllegalArgumentException> {
            keyStore.storeKey(keyInfoWithDifferentProvider, "test-provider", "test-alias", null)
        }

        assertTrue(exception.message?.contains("does not match") == true)
    }

    @Test
    fun storeKeyShouldThrowWhenAliasMismatch() = runTest {
        val provider = mockk<KmsProvider>()
        every { provider.id } returns "test-provider"

        val keyStore = ManagedKeyStoreWithProviderLookups(testProviderRegistry(setOf(provider)), testResolverRegistry(emptySet()))

        val keyInfoWithDifferentAlias = ResolvedKeyInfo<KeyType>(
            key = testJwk,
            keyVisibility = KeyVisibility.PRIVATE,
            alias = "different-alias"
        )

        val exception = assertFailsWith<IllegalArgumentException> {
            keyStore.storeKey(keyInfoWithDifferentAlias, "test-provider", "test-alias", null)
        }

        assertTrue(exception.message?.contains("does not match") == true)
    }

    @Test
    fun storeKeyShouldSucceedWhenProviderIdMatches() = runTest {
        val provider = mockk<KmsProvider>()
        every { provider.id } returns "test-provider"
        coEvery { provider.storeKey(any(), any(), any(), any()) } returns testManagedKeyInfo

        val keyStore = ManagedKeyStoreWithProviderLookups(testProviderRegistry(setOf(provider)), testResolverRegistry(emptySet()))

        val keyInfoWithMatchingProvider = ResolvedKeyInfo<KeyType>(
            key = testJwk,
            keyVisibility = KeyVisibility.PRIVATE,
            providerId = "test-provider"
        )

        val result = keyStore.storeKey(keyInfoWithMatchingProvider, "test-provider", "test-alias", null)

        assertNotNull(result)
    }

    @Test
    fun storeKeyShouldSucceedWhenAliasMatches() = runTest {
        val provider = mockk<KmsProvider>()
        every { provider.id } returns "test-provider"
        coEvery { provider.storeKey(any(), any(), any(), any()) } returns testManagedKeyInfo

        val keyStore = ManagedKeyStoreWithProviderLookups(testProviderRegistry(setOf(provider)), testResolverRegistry(emptySet()))

        val keyInfoWithMatchingAlias = ResolvedKeyInfo<KeyType>(
            key = testJwk,
            keyVisibility = KeyVisibility.PRIVATE,
            alias = "test-alias"
        )

        val result = keyStore.storeKey(keyInfoWithMatchingAlias, "test-provider", "test-alias", null)

        assertNotNull(result)
    }

    // =========== deleteKey Tests ===========

    @Test
    fun deleteKeyShouldThrowWhenNoAliasOrKid() = runTest {
        val keyStore = ManagedKeyStoreWithProviderLookups(testProviderRegistry(emptySet()), testResolverRegistry(emptySet()))

        val keyInfo = KeyInfo<KeyType>(alias = null, kid = null)

        val exception = assertFailsWith<IllegalArgumentException> {
            keyStore.deleteKey(keyInfo)
        }

        assertTrue(exception.message?.contains("without alias or kid") == true)
    }

    @Test
    fun deleteKeyShouldReturnFalseWhenProviderNotFound() = runTest {
        val provider = mockk<KmsProvider>()
        every { provider.id } returns "existing-provider"

        val keyStore = ManagedKeyStoreWithProviderLookups(testProviderRegistry(setOf(provider)), testResolverRegistry(emptySet()))

        val keyInfo = KeyInfo<KeyType>(alias = "test-alias", providerId = "non-existent-provider")

        val result = keyStore.deleteKey(keyInfo)

        assertFalse(result)
    }

    @Test
    fun deleteKeyShouldDelegateToProvider() = runTest {
        val provider = mockk<KmsProvider>()
        every { provider.id } returns "test-provider"
        coEvery { provider.deleteKey(any()) } returns true

        val keyStore = ManagedKeyStoreWithProviderLookups(testProviderRegistry(setOf(provider)), testResolverRegistry(emptySet()))

        val keyInfo = KeyInfo<KeyType>(alias = "test-alias", providerId = "test-provider")

        val result = keyStore.deleteKey(keyInfo)

        assertTrue(result)
        coVerify { provider.deleteKey(keyInfo) }
    }

    @Test
    fun deleteKeyShouldReturnFalseOnNotFoundException() = runTest {
        val provider = mockk<KmsProvider>()
        every { provider.id } returns "test-provider"
        coEvery { provider.deleteKey(any()) } throws NotFoundException("Key not found")

        val keyStore = ManagedKeyStoreWithProviderLookups(testProviderRegistry(setOf(provider)), testResolverRegistry(emptySet()))

        val keyInfo = KeyInfo<KeyType>(alias = "test-alias", providerId = "test-provider")

        val result = keyStore.deleteKey(keyInfo)

        assertFalse(result)
    }

    @Test
    fun deleteKeyShouldWorkWithKidOnly() = runTest {
        val provider = mockk<KmsProvider>()
        every { provider.id } returns "test-provider"
        coEvery { provider.deleteKey(any()) } returns true

        val keyStore = ManagedKeyStoreWithProviderLookups(testProviderRegistry(setOf(provider)), testResolverRegistry(emptySet()))

        val keyInfo = KeyInfo<KeyType>(kid = "test-kid", providerId = "test-provider")

        val result = keyStore.deleteKey(keyInfo)

        assertTrue(result)
    }
}
