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

package com.sphereon.crypto.kms

import com.sphereon.core.api.session.asCoreApiServiceGraph
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.ManagedKeyInfo
import com.sphereon.crypto.core.PKIException
import com.sphereon.crypto.core.ResolvedKeyInfo
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.generic.hash
import com.sphereon.crypto.core.jose.JwaCurve
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.kms.KeyAgreementAlgorithm
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.kms.asKeyManagerServiceGraph
import com.sphereon.crypto.core.kms.command.SignatureEncoding
import com.sphereon.crypto.core.kms.kmsQuery
import com.sphereon.crypto.core.kms.model.IdentifierMethod
import com.sphereon.crypto.key.persistence.KeyReferenceStoreErrorCodes
import com.sphereon.crypto.core.testutil.createCryptoTestAppGraph
import com.sphereon.crypto.core.testutil.supportsDigestSignatureRoundTrip
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderConfig
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderFactoryImpl
import dev.whyoleg.cryptography.CryptographyProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Comprehensive tests for KeyManagerServiceImpl to improve branch coverage.
 */
class KeyManagerServiceImplTest {
    private lateinit var keyManagerService: KeyManagerService

    val app = createCryptoTestAppGraph(this)
    val context = app.userContextManager.getAnonymous()
    val session = context.sessionContextManager.createOrGetFromId("kms-impl-test", principalType = com.sphereon.di.context.PrincipalType.USER)

    @BeforeTest
    fun setUp() {
        // Register the software KMS provider
        val config =
            SoftwareKmsProviderConfig(
                id = "test-software-provider",
                cryptographyProvider = CryptographyProvider.Default.name,
            )
        app as SoftwareKmsProviderFactoryImpl.Graph
        val softwareKmsProvider = app.softwareKmsProvider.create(config, session.asCoreApiServiceGraph().serviceExecution)

        keyManagerService = session.graph.asKeyManagerServiceGraph().keyManagerService
        keyManagerService.registerProvider(softwareKmsProvider, makeDefaultKms = true)
    }

    // =========== Provider Management Tests ===========

    @Test
    fun getProviderByIdShouldThrowForInvalidId() =
        runTest {
            assertFailsWith<PKIException> {
                keyManagerService.getProviderById("non-existent-provider")
            }
        }

    @Test
    fun getKmsBySignatureAlgorithmShouldFindProvider() =
        runTest {
            val provider = keyManagerService.getKmsBySignatureAlgorithm(SignatureAlgorithm.ECDSA_SHA256)
            assertNotNull(provider)
            assertTrue(provider.supportedSignatureAlgorithms().contains(SignatureAlgorithm.ECDSA_SHA256))
        }

    @Test
    fun getKmsBySignatureAlgorithmShouldSupportMultipleAlgorithms() =
        runTest {
            // Test that multiple common algorithms are supported
            val sha384Provider = keyManagerService.getKmsBySignatureAlgorithm(SignatureAlgorithm.ECDSA_SHA384)
            assertNotNull(sha384Provider)

            val sha512Provider = keyManagerService.getKmsBySignatureAlgorithm(SignatureAlgorithm.ECDSA_SHA512)
            assertNotNull(sha512Provider)
        }

    @Test
    fun registerProviderShouldAddProvider() {
        val config2 =
            SoftwareKmsProviderConfig(
                id = "second-software-provider",
                cryptographyProvider = CryptographyProvider.Default.name,
            )
        app as SoftwareKmsProviderFactoryImpl.Graph
        val provider2 = app.softwareKmsProvider.create(config2, session.asCoreApiServiceGraph().serviceExecution)

        keyManagerService.registerProvider(provider2, makeDefaultKms = false)

        val ids = keyManagerService.getProviderIds()
        assertTrue(ids.contains("second-software-provider"))
    }

    @Test
    fun registerProviderWithMakeDefaultShouldUpdateDefault() {
        val config2 =
            SoftwareKmsProviderConfig(
                id = "new-default-provider",
                cryptographyProvider = CryptographyProvider.Default.name,
            )
        app as SoftwareKmsProviderFactoryImpl.Graph
        val provider2 = app.softwareKmsProvider.create(config2, session.asCoreApiServiceGraph().serviceExecution)

        keyManagerService.registerProvider(provider2, makeDefaultKms = true)

        assertEquals("new-default-provider", keyManagerService.defaultProviderId())
    }

    @Test
    fun algorithmConstrainedLookupPrefersTheRegisteredDefaultWhenItSupportsTheAlgorithm() =
        runTest {
            val provider =
                (app as SoftwareKmsProviderFactoryImpl.Graph).softwareKmsProvider.create(
                    SoftwareKmsProviderConfig(
                        id = "preferred-default-provider",
                        cryptographyProvider = CryptographyProvider.Default.name,
                    ),
                    session.asCoreApiServiceGraph().serviceExecution,
                )
            keyManagerService.registerProvider(provider, makeDefaultKms = true)

            assertEquals(
                "preferred-default-provider",
                keyManagerService.getProvider(providerId = null, alg = SignatureAlgorithm.ECDSA_SHA256).id,
            )
        }

    // =========== getProvider() Branch Tests ===========

    @Test
    fun getProviderWithNullProviderIdAndAlgShouldUseAlgorithm() =
        runTest {
            val provider = keyManagerService.getProvider(null, SignatureAlgorithm.ECDSA_SHA256)
            assertNotNull(provider)
            assertTrue(provider.supportedSignatureAlgorithms().contains(SignatureAlgorithm.ECDSA_SHA256))
        }

    @Test
    fun getProviderWithProviderIdShouldUseProviderId() =
        runTest {
            val provider = keyManagerService.getProvider("test-software-provider", SignatureAlgorithm.ECDSA_SHA256)
            assertNotNull(provider)
            assertEquals("test-software-provider", provider.id)
        }

    @Test
    fun getProviderWithProviderIdAndUnsupportedAlgorithmFailsClosed() =
        runTest {
            val error =
                assertFailsWith<PKIException> {
                    keyManagerService.getProvider("test-software-provider", SignatureAlgorithm.ES256K)
                }

            assertTrue(error.message.orEmpty().contains("does not support signature algorithm"))
        }

    @Test
    fun getProviderWithBothNullShouldUseDefault() =
        runTest {
            val provider = keyManagerService.getProvider(null, null)
            assertNotNull(provider)
            assertEquals(keyManagerService.defaultProviderId(), provider.id)
        }

    // =========== Resolver Tests ===========

    @Test
    fun getResolverIdsShouldReturnResolvers() {
        val resolverIds = keyManagerService.getResolverIds()
        assertTrue(resolverIds.isNotEmpty(), "Should have at least one resolver")
    }

    @Test
    fun getResolverByIdShouldThrowForInvalidId() {
        assertFailsWith<PKIException> {
            keyManagerService.getResolverById("non-existent-resolver")
        }
    }

    @Test
    fun getResolverByKeyTypeOrIdentifierWithResolverIdShouldMatch() {
        val resolverIds = keyManagerService.getResolverIds()
        assertTrue(resolverIds.isNotEmpty())

        val resolver =
            keyManagerService.getResolverByKeyTypeOrIdentifier(
                identifierMethod = null,
                keyType = null,
                resolverId = resolverIds.first(),
            )
        assertNotNull(resolver)
        assertEquals(resolverIds.first(), resolver.getId())
    }

    @Test
    fun getResolverByKeyTypeOrIdentifierWithIdentifierMethodShouldMatch() {
        // Get resolver by identifier method - JWK method should be supported
        val resolver =
            keyManagerService.getResolverByKeyTypeOrIdentifier(
                identifierMethod = IdentifierMethod.jwk,
                keyType = null,
                resolverId = null,
            )
        assertNotNull(resolver)
    }

    @Test
    fun defaultResolverIdShouldBeSet() {
        val defaultId = keyManagerService.defaultResolverId()
        assertNotNull(defaultId)
        assertTrue(defaultId.isNotEmpty())
    }

    // =========== Key Generation Tests ===========

    @Test
    fun generateKeyWithAliasShouldSucceed() =
        runTest {
            val keyPair =
                keyManagerService.generateKey(
                    alias = "test-key-alias",
                    alg = SignatureAlgorithm.ECDSA_SHA256,
                )
            assertNotNull(keyPair)
            assertNotNull(keyPair.jose.publicJwk)
        }

    @Test
    fun generateKeyWithProviderIdShouldSucceed() =
        runTest {
            val keyPair =
                keyManagerService.generateKey(
                    providerId = "test-software-provider",
                    alg = SignatureAlgorithm.ECDSA_SHA256,
                )
            assertNotNull(keyPair)
        }

    // =========== Raw Signature Tests ===========

    @Test
    fun createRawSignatureShouldSucceed() =
        runTest {
            val keyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
            val keyInfo = keyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

            val data = "test data".encodeToByteArray()
            val signature = keyManagerService.createRawSignature(keyInfo, data, false)

            assertNotNull(signature)
            assertTrue(signature.isNotEmpty())
        }

    @Test
    fun rawSigningEnforcesCompoundManagedSelectorAndPreservesSingleSelectorCompatibility() =
        runTest {
            val keyA = keyManagerService.generateKey(alias = "raw-selector-a", alg = SignatureAlgorithm.ECDSA_SHA256)
            val keyB = keyManagerService.generateKey(alias = "raw-selector-b", alg = SignatureAlgorithm.ECDSA_SHA256)
            val privateA = keyA.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
            val publicA = keyA.joseToManagedKeyInfo(KeyVisibility.PUBLIC)
            val publicB = keyB.joseToManagedKeyInfo(KeyVisibility.PUBLIC)
            val aliasA = requireNotNull(privateA.alias)
            val aliasB = requireNotNull(publicB.alias)
            val kidA = requireNotNull(privateA.kid)
            val kidB = requireNotNull(publicB.kid)
            val providerId = requireNotNull(publicB.providerId)
            val input = "compound raw selector".encodeToByteArray()

            val mismatch =
                assertFailsWith<PKIException> {
                    keyManagerService.createRawSignature(
                        managedSelector(providerId, alias = aliasB, kid = kidA),
                        input,
                        requireX5Chain = false,
                    )
                }
            assertManagedSelectorMismatch(mismatch, providerId, aliasB, kidB, kidA)

            val matching = keyManagerService.createRawSignature(managedSelector(providerId, aliasB, kidB), input, false)
            assertTrue(keyManagerService.isValidRawSignature(publicB, input, matching))
            assertFalse(keyManagerService.isValidRawSignature(publicA, input, matching))

            val aliasOnly = keyManagerService.createRawSignature(managedSelector(providerId, alias = aliasB), input, false)
            assertTrue(keyManagerService.isValidRawSignature(publicB, input, aliasOnly))

            val kidOnly = keyManagerService.createRawSignature(managedSelector(providerId, kid = kidA), input, false)
            assertTrue(keyManagerService.isValidRawSignature(publicA, input, kidOnly))

            val legacyAliasShapedKid = keyManagerService.createRawSignature(managedSelector(providerId, kid = aliasA), input, false)
            assertTrue(keyManagerService.isValidRawSignature(publicA, input, legacyAliasShapedKid))
        }

    @Test
    fun inlineSigningKeyRemainsAuthoritativeEvenWhenItCarriesAnotherManagedAlias() =
        runTest {
            val keyA = keyManagerService.generateKey(alias = "inline-authority-a", alg = SignatureAlgorithm.ECDSA_SHA256)
            val keyB = keyManagerService.generateKey(alias = "inline-authority-b", alg = SignatureAlgorithm.ECDSA_SHA256)
            val privateA = keyA.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
            val publicA = keyA.joseToManagedKeyInfo(KeyVisibility.PUBLIC)
            val publicB = keyB.joseToManagedKeyInfo(KeyVisibility.PUBLIC)
            val inlineAWithAliasB =
                KeyInfo(
                    key = privateA.key,
                    alias = publicB.alias,
                    kid = privateA.kid,
                    providerId = publicB.providerId,
                    signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256,
                )
            val input = "inline authority".encodeToByteArray()

            val raw = keyManagerService.createRawSignature(inlineAWithAliasB, input, false)
            assertTrue(keyManagerService.isValidRawSignature(publicA, input, raw))
            assertFalse(keyManagerService.isValidRawSignature(publicB, input, raw))

            val digest = hash(input, DigestAlg.SHA256)
            val digestSignature =
                keyManagerService.signDigest(
                    inlineAWithAliasB,
                    digest,
                    SignatureAlgorithm.ECDSA_SHA256,
                    SignatureEncoding.RAW,
                )
            assertTrue(
                keyManagerService.verifyDigest(
                    publicA,
                    digest,
                    digestSignature,
                    SignatureAlgorithm.ECDSA_SHA256,
                    SignatureEncoding.RAW,
                ),
            )
            assertFalse(
                keyManagerService.verifyDigest(
                    publicB,
                    digest,
                    digestSignature,
                    SignatureAlgorithm.ECDSA_SHA256,
                    SignatureEncoding.RAW,
                ),
            )
        }

    @Test
    fun isValidRawSignatureShouldReturnTrueForValidSignature() =
        runTest {
            val keyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
            val privateKeyInfo = keyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
            val publicKeyInfo = keyPair.joseToManagedKeyInfo(KeyVisibility.PUBLIC)

            val data = "test data".encodeToByteArray()
            val signature = keyManagerService.createRawSignature(privateKeyInfo, data, false)

            val isValid = keyManagerService.isValidRawSignature(publicKeyInfo, data, signature)
            assertTrue(isValid)
        }

    @Test
    fun isValidRawSignatureShouldReturnFalseForInvalidSignature() =
        runTest {
            val keyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
            val publicKeyInfo = keyPair.joseToManagedKeyInfo(KeyVisibility.PUBLIC)

            val data = "test data".encodeToByteArray()
            val invalidSignature = ByteArray(64) { 0 }

            val isValid = keyManagerService.isValidRawSignature(publicKeyInfo, data, invalidSignature)
            assertFalse(isValid)
        }

    @Test
    fun isValidRawSignatureWithProviderIdShouldUseProvider() =
        runTest {
            val keyPair =
                keyManagerService.generateKey(
                    providerId = "test-software-provider",
                    alg = SignatureAlgorithm.ECDSA_SHA256,
                )
            val privateKeyInfo = keyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
            val publicKeyInfo = keyPair.joseToManagedKeyInfo(KeyVisibility.PUBLIC)

            val data = "test data".encodeToByteArray()
            val signature = keyManagerService.createRawSignature(privateKeyInfo, data, false)

            // Create key info with explicit provider ID
            val keyInfoWithProvider =
                ManagedKeyInfo(
                    providerId = "test-software-provider",
                    alias = publicKeyInfo.alias,
                    resolvedKeyInfo = publicKeyInfo,
                )

            val isValid = keyManagerService.isValidRawSignature(keyInfoWithProvider, data, signature)
            assertTrue(isValid)
        }

    @Test
    fun signDigestShouldUseDigestSignatureCapability() =
        runTest {
            if (!supportsDigestSignatureRoundTrip()) {
                return@runTest
            }

            val keyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
            val privateKeyInfo = keyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
            val publicKeyInfo = keyPair.joseToManagedKeyInfo(KeyVisibility.PUBLIC)
            val digest = hash("service-level digest signature".encodeToByteArray(), DigestAlg.SHA256)

            val signature =
                keyManagerService.signDigest(
                    keyInfo = privateKeyInfo,
                    digest = digest,
                    signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256,
                    signatureEncoding = SignatureEncoding.RAW,
                )

            assertTrue(
                keyManagerService.verifyDigest(
                    keyInfo = publicKeyInfo,
                    digest = digest,
                    signature = signature,
                    signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256,
                    signatureEncoding = SignatureEncoding.RAW,
                ),
            )
            assertFalse(keyManagerService.isValidRawSignature(publicKeyInfo, digest, signature))
        }

    @Test
    fun digestSigningEnforcesCompoundManagedSelectorAndPreservesSingleSelectorCompatibility() =
        runTest {
            val keyA = keyManagerService.generateKey(alias = "digest-selector-a", alg = SignatureAlgorithm.ECDSA_SHA256)
            val keyB = keyManagerService.generateKey(alias = "digest-selector-b", alg = SignatureAlgorithm.ECDSA_SHA256)
            val privateA = keyA.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
            val publicA = keyA.joseToManagedKeyInfo(KeyVisibility.PUBLIC)
            val publicB = keyB.joseToManagedKeyInfo(KeyVisibility.PUBLIC)
            val aliasA = requireNotNull(privateA.alias)
            val aliasB = requireNotNull(publicB.alias)
            val kidA = requireNotNull(privateA.kid)
            val kidB = requireNotNull(publicB.kid)
            val providerId = requireNotNull(publicB.providerId)
            val digest = hash("compound digest selector".encodeToByteArray(), DigestAlg.SHA256)

            val mismatch =
                assertFailsWith<PKIException> {
                    keyManagerService.signDigest(
                        keyInfo = managedSelector(providerId, alias = aliasB, kid = kidA),
                        digest = digest,
                        signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256,
                        signatureEncoding = SignatureEncoding.RAW,
                    )
                }
            assertManagedSelectorMismatch(mismatch, providerId, aliasB, kidB, kidA)

            suspend fun assertSignsWith(
                selector: KeyInfo<KeyType>,
                expectedPublicKey: com.sphereon.crypto.core.KeyInfoType<*>,
            ) {
                val signature =
                    keyManagerService.signDigest(
                        keyInfo = selector,
                        digest = digest,
                        signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256,
                        signatureEncoding = SignatureEncoding.RAW,
                    )
                assertTrue(
                    keyManagerService.verifyDigest(
                        keyInfo = expectedPublicKey,
                        digest = digest,
                        signature = signature,
                        signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256,
                        signatureEncoding = SignatureEncoding.RAW,
                    ),
                )
            }

            assertSignsWith(managedSelector(providerId, aliasB, kidB), publicB)
            assertSignsWith(managedSelector(providerId, alias = aliasB), publicB)
            assertSignsWith(managedSelector(providerId, kid = kidA), publicA)
            assertSignsWith(managedSelector(providerId, kid = aliasA), publicA)
        }

    private fun managedSelector(
        providerId: String,
        alias: String? = null,
        kid: String? = null,
    ): KeyInfo<KeyType> =
        KeyInfo(
            providerId = providerId,
            alias = alias,
            kid = kid,
            signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256,
        )

    private fun assertManagedSelectorMismatch(
        failure: PKIException,
        providerId: String,
        alias: String,
        canonicalKid: String,
        requestedKid: String,
    ) {
        val message = failure.message.orEmpty()
        assertContains(message, "Managed signing key selector mismatch")
        assertContains(message, "alias '$alias'")
        assertContains(message, "kid '$canonicalKid'")
        assertContains(message, "provider '$providerId'")
        assertContains(message, "not requested kid '$requestedKid'")
    }

    // =========== Key Agreement Tests ===========

    @Test
    fun performKeyAgreementShouldSucceedWithValidKeys() =
        runTest {
            // Generate two key pairs for key agreement
            val aliceKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
            val bobKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)

            val alicePrivate = aliceKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
            val bobPublic = bobKeyPair.joseToManagedKeyInfo(KeyVisibility.PUBLIC)

            val sharedSecret =
                keyManagerService.performKeyAgreement(
                    privateKeyInfo = alicePrivate,
                    publicKeyInfo = bobPublic,
                    algorithm = KeyAgreementAlgorithm.ECDH_ES,
                    keyDataLen = null,
                )

            assertNotNull(sharedSecret)
            assertTrue(sharedSecret.isNotEmpty())
        }

    @Test
    fun performKeyAgreementShouldFailWithoutPrivateKey() =
        runTest {
            val keyPair1 = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
            val keyPair2 = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)

            // Use public key as "private" - should fail because no 'd' parameter
            val publicAsPrivate = keyPair1.joseToManagedKeyInfo(KeyVisibility.PUBLIC)
            val bobPublic = keyPair2.joseToManagedKeyInfo(KeyVisibility.PUBLIC)

            // With command pattern, exceptions are wrapped as PKIException
            assertFailsWith<Exception> {
                keyManagerService.performKeyAgreement(
                    privateKeyInfo = publicAsPrivate,
                    publicKeyInfo = bobPublic,
                    algorithm = KeyAgreementAlgorithm.ECDH_ES,
                    keyDataLen = null,
                )
            }
        }

    @Test
    fun performKeyAgreementShouldProduceSameSecretBothWays() =
        runTest {
            // Generate two key pairs
            val aliceKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
            val bobKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)

            val alicePrivate = aliceKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
            val alicePublic = aliceKeyPair.joseToManagedKeyInfo(KeyVisibility.PUBLIC)
            val bobPrivate = bobKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
            val bobPublic = bobKeyPair.joseToManagedKeyInfo(KeyVisibility.PUBLIC)

            // Alice computes shared secret with her private + Bob's public
            val aliceShared =
                keyManagerService.performKeyAgreement(
                    privateKeyInfo = alicePrivate,
                    publicKeyInfo = bobPublic,
                    algorithm = KeyAgreementAlgorithm.ECDH_ES,
                    keyDataLen = null,
                )

            // Bob computes shared secret with his private + Alice's public
            val bobShared =
                keyManagerService.performKeyAgreement(
                    privateKeyInfo = bobPrivate,
                    publicKeyInfo = alicePublic,
                    algorithm = KeyAgreementAlgorithm.ECDH_ES,
                    keyDataLen = null,
                )

            // Both should arrive at the same shared secret
            assertTrue(aliceShared.contentEquals(bobShared), "ECDH shared secrets should match")
        }

    @Test
    fun performKeyAgreementWithP384ShouldSucceed() =
        runTest {
            val aliceKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA384)
            val bobKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA384)

            val alicePrivate = aliceKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
            val bobPublic = bobKeyPair.joseToManagedKeyInfo(KeyVisibility.PUBLIC)

            val sharedSecret =
                keyManagerService.performKeyAgreement(
                    privateKeyInfo = alicePrivate,
                    publicKeyInfo = bobPublic,
                    algorithm = KeyAgreementAlgorithm.ECDH_ES,
                    keyDataLen = null,
                )

            assertNotNull(sharedSecret)
            assertTrue(sharedSecret.isNotEmpty())
        }

    // Note: Query method tests are in KmsQueryFunctionalityTest.kt which properly injects query commands

    // =========== KeyStore Delegation Tests ===========

    @Test
    fun keyStoreShouldBeAccessible() {
        val keyStore = keyManagerService.keyStore
        assertNotNull(keyStore)
    }

    @Test
    fun keyVisibilityShouldReturnValue() {
        val visibility = keyManagerService.keyVisibility()
        assertNotNull(visibility)
    }

    @Test
    fun listKeysShouldReturnArray() =
        runTest {
            val keys = keyManagerService.listKeys()
            assertNotNull(keys)
        }

    @Test
    fun storeAndGetKeyShouldWork() =
        runTest {
            val keyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
            // Create ResolvedKeyInfo without alias/providerId to avoid validation conflicts
            val resolvedKeyInfo =
                ResolvedKeyInfo(
                    key = keyPair.jose.privateJwk!!,
                    alias = null,
                    providerId = null,
                )

            // Store the key
            val storedKey =
                keyManagerService.storeKey(
                    keyInfo = resolvedKeyInfo,
                    providerId = "test-software-provider",
                    alias = "stored-key-test",
                    certChain = null,
                )

            assertNotNull(storedKey)
            assertEquals("stored-key-test", storedKey.alias)

            // Retrieve the key
            val retrievedKey = keyManagerService.getKey(storedKey)
            assertNotNull(retrievedKey)
            assertEquals("stored-key-test", retrievedKey.alias)
        }

    @Test
    fun deleteKeyShouldFailClosedWithoutDurableOwnershipHistory() =
        runTest {
            val keyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
            // Create ResolvedKeyInfo without alias/providerId to avoid validation conflicts
            val resolvedKeyInfo =
                ResolvedKeyInfo(
                    key = keyPair.jose.privateJwk!!,
                    alias = null,
                    providerId = null,
                )

            val storedKey =
                keyManagerService.storeKey(
                    keyInfo = resolvedKeyInfo,
                    providerId = "test-software-provider",
                    alias = "key-to-delete-test",
                    certChain = null,
                )

            val result = keyManagerService.deleteKeyResult(storedKey)
            assertTrue(result.isErr)
            assertEquals(KeyReferenceStoreErrorCodes.DURABLE_HISTORY_UNSUPPORTED, result.error.code)

            val failure =
                assertFailsWith<PKIException> {
                    keyManagerService.deleteKey(storedKey)
                }
            assertTrue(failure.message.orEmpty().lowercase().contains("ownership history"))
        }

    // =========== Resolve Public Key Tests ===========

    @Test
    fun resolvePublicKeyShouldSucceed() =
        runTest {
            val keyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
            val keyInfo = keyPair.joseToManagedKeyInfo(KeyVisibility.PUBLIC)

            val resolved =
                keyManagerService.resolvePublicKey(
                    keyInfo = keyInfo,
                    identifierMethod = null,
                    trustedCerts = null,
                    verifyX509CertificateChain = false,
                )

            assertNotNull(resolved)
            assertNotNull(resolved.key)
        }
}
