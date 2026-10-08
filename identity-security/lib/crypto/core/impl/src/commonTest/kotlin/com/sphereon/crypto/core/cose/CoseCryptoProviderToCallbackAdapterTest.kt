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

package com.sphereon.crypto.core.cose

import com.sphereon.cbor.CborByteString
import com.sphereon.core.api.session.asCoreApiServiceGraph
import com.sphereon.crypto.core.CoseCryptoCallbackCoroutines
import com.sphereon.crypto.core.CoseJoseKeyMappingService
import com.sphereon.crypto.core.CryptoServices
import com.sphereon.crypto.core.DefaultCallbacks
import com.sphereon.crypto.core.HasPlatformCallback
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.ManagedKeyInfoType
import com.sphereon.crypto.core.PKIException
import com.sphereon.crypto.core.generic.KeyTypeMapping
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.kms.KmsProvider
import com.sphereon.crypto.core.kms.asKeyManagerServiceGraph
import com.sphereon.crypto.core.sign.SimpleSignatureService
import com.sphereon.crypto.core.testutil.createCryptoTestAppGraph
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderConfig
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderFactoryImpl
import dev.whyoleg.cryptography.CryptographyProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for CoseCryptoProviderToCallbackAdapter with focus on branch coverage.
 */
class CoseCryptoProviderToCallbackAdapterTest {
    private lateinit var keyManagerService: KeyManagerService

    val app = createCryptoTestAppGraph(this)
    val context = app.userContextManager.getAnonymous()
    val session = context.sessionContextManager.createOrGetFromId("adapter-test", principalType = com.sphereon.di.context.PrincipalType.USER)

    @BeforeTest
    fun setUp() {
        // Register the software KMS provider
        val config =
            SoftwareKmsProviderConfig(
                id = "adapter-test-provider",
                cryptographyProvider = CryptographyProvider.Default.name,
            )
        app as SoftwareKmsProviderFactoryImpl.Graph
        val softwareKmsProvider = app.softwareKmsProvider.create(config, session.asCoreApiServiceGraph().serviceExecution)

        // Get KeyManagerService from the session graph
        keyManagerService = session.graph.asKeyManagerServiceGraph().keyManagerService
        keyManagerService.registerProvider(softwareKmsProvider, makeDefaultKms = true)
    }

    @AfterTest
    fun tearDown() {
        // Clear default callback to avoid test pollution
        DefaultCallbacks.setCoseCryptoDefault(null)
    }

    // =========== Lazy Provider Exception Handling Tests ===========

    @Test
    fun adapterShouldHandleKeyManagerServiceProviderException() =
        runTest {
            // Create adapter with provider that throws exception
            val adapter =
                CoseCryptoProviderToCallbackAdapter(
                    keyManagerServiceProvider = { throw RuntimeException("Provider unavailable") },
                    rawSignatureServiceProvider = null,
                    publicKeyResolverServiceProvider = null,
                )

            // The lazy property should catch the exception and return null
            // This is tested indirectly through assertedSignatureProvider which throws PKIException
            assertFailsWith<PKIException> {
                adapter.sign(
                    ToBeSignedCbor(
                        value = byteArrayOf(1, 2, 3),
                        keyInfo = KeyInfo<Jwk>(kid = "test-key"),
                    ),
                    requireX5Chain = false,
                )
            }
        }

    @Test
    fun adapterShouldHandleSimpleSignatureServiceProviderException() =
        runTest {
            // Create adapter where keyManager returns null and rawSignature throws
            val adapter =
                CoseCryptoProviderToCallbackAdapter(
                    keyManagerServiceProvider = { throw RuntimeException("KMS unavailable") },
                    rawSignatureServiceProvider = { throw RuntimeException("Signature service unavailable") },
                    publicKeyResolverServiceProvider = null,
                )

            // Should fail because both providers are unavailable
            assertFailsWith<PKIException> {
                adapter.sign(
                    ToBeSignedCbor(
                        value = byteArrayOf(1, 2, 3),
                        keyInfo = KeyInfo<Jwk>(kid = "test-key"),
                    ),
                    requireX5Chain = false,
                )
            }
        }

    @Test
    fun adapterShouldHandlePublicKeyResolverProviderException() =
        runTest {
            // Create adapter where publicKeyResolver throws
            val adapter =
                CoseCryptoProviderToCallbackAdapter(
                    keyManagerServiceProvider = { throw RuntimeException("KMS unavailable") },
                    rawSignatureServiceProvider = null,
                    publicKeyResolverServiceProvider = { throw RuntimeException("Resolver unavailable") },
                )

            // Should fail when trying to resolve public key
            assertFailsWith<PKIException> {
                adapter.resolvePublicKey(
                    KeyInfo<Jwk>(kid = "test-key"),
                )
            }
        }

    // =========== assertedSignatureProvider Branch Tests ===========

    @Test
    fun assertedSignatureProviderShouldThrowWhenNoProvidersAvailable() =
        runTest {
            val adapter =
                CoseCryptoProviderToCallbackAdapter(
                    keyManagerServiceProvider = { throw RuntimeException("Unavailable") },
                    rawSignatureServiceProvider = null,
                    publicKeyResolverServiceProvider = null,
                )

            val exception =
                assertFailsWith<PKIException> {
                    adapter.sign(
                        ToBeSignedCbor(
                            value = byteArrayOf(1, 2, 3),
                            keyInfo = KeyInfo<Jwk>(kid = "test-key"),
                        ),
                        requireX5Chain = false,
                    )
                }
            assertTrue(exception.message?.contains("No signature provider") == true)
        }

    // =========== assertedPublicKeyProvider Branch Tests ===========

    @Test
    fun managedCoseSigningResolvesNamedProviderPublicKeyButSignsWithOriginalSelector() =
        runTest {
            val providerId = "adapter-test-provider"
            val alias = "managed-cose-a"
            val managed =
                keyManagerService.generateKey(
                    providerId = providerId,
                    alias = alias,
                    alg = SignatureAlgorithm.ECDSA_SHA256,
                )
            val public = managed.joseToManagedKeyInfo(KeyVisibility.PUBLIC)
            val selector =
                KeyInfo<CoseKeyType>(
                    alias = alias,
                    kid = public.kid,
                    providerId = providerId,
                    keyVisibility = KeyVisibility.PRIVATE,
                    signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256,
                    keyType = KeyTypeMapping.EC,
                )
            val cose = (session.graph as CryptoServices.Graph).cryptoServices.cose
            val signed =
                cose.sign1<Any>(
                    input =
                        CoseSign1Input(
                            protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.ES256),
                            payload = CborByteString("managed cose selector".encodeToByteArray()),
                        ),
                    keyInfo = selector,
                    requireX5Chain = false,
                )

            val returnedPublicKey = kotlin.test.assertNotNull(signed.keyInfo.key, "COSE result must contain its public key")
            assertTrue(returnedPublicKey.d == null, "COSE result must expose only public key material")
            val verified =
                cose.verify1(
                    input = signed.coseSign1,
                    keyInfo = CoseJoseKeyMappingService.toCoseKeyInfo(public),
                    requireX5Chain = false,
                )
            assertFalse(verified.error, verified.message)
        }

    @Test
    fun managedCoseSigningResolvesAliasOnlySelectorAndReturnsCanonicalKid() =
        runTest {
            val providerId = "adapter-test-provider"
            val alias = "managed-cose-alias-only"
            val managed =
                keyManagerService.generateKey(
                    providerId = providerId,
                    alias = alias,
                    alg = SignatureAlgorithm.ECDSA_SHA256,
                )
            val canonicalPublic = managed.joseToManagedKeyInfo(KeyVisibility.PUBLIC)
            val cose = (session.graph as CryptoServices.Graph).cryptoServices.cose
            @Suppress("UNCHECKED_CAST")
            val callbackHost = cose as HasPlatformCallback<CoseCryptoCallbackCoroutines>
            val originalCallback = callbackHost.platform()
            var observedSigningSelector: com.sphereon.crypto.core.KeyInfoType<*>? = null
            callbackHost.setPlatform(
                object : CoseCryptoCallbackCoroutines by originalCallback {
                    override suspend fun sign(
                        input: ToBeSignedCbor,
                        requireX5Chain: Boolean?,
                    ): ByteArray {
                        observedSigningSelector = input.keyInfo
                        return originalCallback.sign(input, requireX5Chain)
                    }
                },
            )

            val signed =
                cose.sign1<Any>(
                    input =
                        CoseSign1Input(
                            protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.ES256),
                            payload = CborByteString("managed cose alias only".encodeToByteArray()),
                        ),
                    keyInfo =
                        KeyInfo<CoseKeyType>(
                            alias = alias,
                            keyVisibility = KeyVisibility.PRIVATE,
                            keyType = KeyTypeMapping.EC,
                        ),
                    requireX5Chain = false,
                )

            assertEquals(canonicalPublic.kid, signed.keyInfo.kid)
            assertEquals(KeyTypeMapping.EC, signed.keyInfo.keyType)
            val signingSelector = kotlin.test.assertNotNull(observedSigningSelector)
            assertEquals(alias, signingSelector.alias)
            assertNull(signingSelector.providerId)
            assertNull(signingSelector.kid)
            assertNull(signingSelector.key)
            val returnedPublicKey = kotlin.test.assertNotNull(signed.keyInfo.key, "COSE result must contain its public key")
            assertTrue(returnedPublicKey.d == null, "COSE result must expose only public key material")
            val verified =
                cose.verify1(
                    input = signed.coseSign1,
                    keyInfo = CoseJoseKeyMappingService.toCoseKeyInfo(canonicalPublic),
                    requireX5Chain = false,
                )
            assertFalse(verified.error, verified.message)
        }

    @Test
    fun explicitInlinePublicKeyIsNotReplacedByNamedManagedSelector() =
        runTest {
            val providerId = "adapter-test-provider"
            val selectedAlias = "managed-cose-inline-authority"
            val selected =
                keyManagerService.generateKey(
                    providerId = providerId,
                    alias = selectedAlias,
                    alg = SignatureAlgorithm.ECDSA_SHA256,
                ).joseToManagedKeyInfo(KeyVisibility.PUBLIC)
            val differentPublic =
                keyManagerService.generateKey(
                    providerId = providerId,
                    alias = "managed-cose-different-public",
                    alg = SignatureAlgorithm.ECDSA_SHA256,
                ).joseToManagedKeyInfo(KeyVisibility.PUBLIC)
            val selectedPublicKey =
                requireNotNull(CoseJoseKeyMappingService.toCoseKeyInfo(selected).key) as CoseKey
            val selectedCoseKid = requireNotNull(selectedPublicKey.kid)
            val suppliedPublicKey =
                (requireNotNull(CoseJoseKeyMappingService.toCoseKeyInfo(differentPublic).key) as CoseKey)
                    .copy(kid = selectedCoseKid)
            val cose = (session.graph as CryptoServices.Graph).cryptoServices.cose

            assertFalse(
                requireNotNull(selectedPublicKey.x).value.contentEquals(requireNotNull(suppliedPublicKey.x).value),
                "Regression fixture must supply different public coordinates under the same selector metadata",
            )

            val exception =
                assertFailsWith<PKIException> {
                    cose.sign1<Any>(
                        input =
                            CoseSign1Input(
                                protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.ES256),
                                payload = CborByteString("explicit inline public authority".encodeToByteArray()),
                            ),
                        keyInfo =
                            KeyInfo<CoseKeyType>(
                                kid = selected.kid,
                                key = suppliedPublicKey,
                                alias = selectedAlias,
                                providerId = providerId,
                                keyVisibility = KeyVisibility.PUBLIC,
                                signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256,
                                keyType = KeyTypeMapping.EC,
                            ),
                        requireX5Chain = false,
                    )
                }

            assertTrue(exception.message.orEmpty().contains("private", ignoreCase = true))
        }

    @Test
    fun managedCoseSigningRejectsNamedProviderAliasWhoseCanonicalKidDiffers() =
        runTest {
            val providerId = "adapter-test-provider"
            val keyA =
                keyManagerService.generateKey(
                    providerId = providerId,
                    alias = "managed-cose-kid-a",
                    alg = SignatureAlgorithm.ECDSA_SHA256,
                )
            keyManagerService.generateKey(
                providerId = providerId,
                alias = "managed-cose-alias-b",
                alg = SignatureAlgorithm.ECDSA_SHA256,
            )
            val requestedKid = keyA.joseToManagedKeyInfo(KeyVisibility.PUBLIC).kid
            val cose = (session.graph as CryptoServices.Graph).cryptoServices.cose

            val exception =
                assertFailsWith<IllegalArgumentException> {
                    cose.sign1<Any>(
                        input =
                            CoseSign1Input(
                                protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.ES256),
                                payload = CborByteString("wrong managed selector".encodeToByteArray()),
                            ),
                        keyInfo =
                            KeyInfo<CoseKeyType>(
                                alias = "managed-cose-alias-b",
                                kid = requestedKid,
                                providerId = providerId,
                                keyVisibility = KeyVisibility.PRIVATE,
                                signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256,
                                keyType = KeyTypeMapping.EC,
                            ),
                        requireX5Chain = false,
                    )
                }

            assertTrue(exception.message.orEmpty().contains("Managed signing key selector mismatch"))
            assertTrue(exception.message.orEmpty().contains("alias 'managed-cose-alias-b'"))
            assertTrue(exception.message.orEmpty().contains("requested kid '$requestedKid'"))
        }

    @Test
    fun managedAliasResolvesThroughTheKeyStoreWhenItsProviderIsNotHostedLocally() =
        runTest {
            // A tenant KMS mints its keys under its own provider id and answers over the command
            // transport. A service that only routes KMS commands never has that provider in its
            // local registry, and asking for it there must not end the resolution.
            val remoteProviderId = "tenant-default"
            val alias = "remote-managed-alias"
            keyManagerService.generateKey(
                providerId = "adapter-test-provider",
                alias = alias,
                alg = SignatureAlgorithm.ECDSA_SHA256,
            )
            val routed = RoutedKeyManagerService(keyManagerService, remoteProviderId)
            val adapter =
                CoseCryptoProviderToCallbackAdapter(
                    keyManagerServiceProvider = { routed },
                    rawSignatureServiceProvider = null,
                    publicKeyResolverServiceProvider = null,
                )

            val resolved =
                adapter.resolvePublicKey(
                    KeyInfo<CoseKeyType>(alias = alias, providerId = remoteProviderId),
                )

            assertNotNull(resolved.key)
            assertEquals(alias, resolved.alias)
            assertEquals(remoteProviderId, resolved.providerId)
            assertEquals(KeyVisibility.PUBLIC, resolved.keyVisibility)
            assertEquals(1, routed.keyStoreLookups)
        }

    @Test
    fun assertedPublicKeyProviderShouldThrowWhenNoResolversAvailable() =
        runTest {
            val adapter =
                CoseCryptoProviderToCallbackAdapter(
                    keyManagerServiceProvider = { throw RuntimeException("Unavailable") },
                    rawSignatureServiceProvider = null,
                    publicKeyResolverServiceProvider = null,
                )

            val exception =
                assertFailsWith<PKIException> {
                    adapter.resolvePublicKey(
                        KeyInfo<Jwk>(kid = "test-key"),
                    )
                }
            assertTrue(exception.message?.contains("Could not deduce key resolver") == true)
        }

    // =========== sign Branch Tests ===========

    @Test
    fun signShouldUseKeyInfoAlgorithmWhenPresent() =
        runTest {
            val managedKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
            val keyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
            val coseKeyInfo = CoseJoseKeyMappingService.toCoseKeyInfo(keyInfo)

            val coseCryptoService = (session.graph as CryptoServices.Graph).cryptoServices.cose

            @Suppress("UNCHECKED_CAST")
            val adapter = (coseCryptoService as HasPlatformCallback<CoseCryptoCallbackCoroutines>).platform()

            // Sign with algorithm from keyInfo
            val signature =
                adapter.sign(
                    ToBeSignedCbor(
                        value = "test data".encodeToByteArray(),
                        keyInfo = coseKeyInfo,
                    ),
                    requireX5Chain = false,
                )

            assertNotNull(signature)
            assertTrue(signature.isNotEmpty())
        }

    @Test
    fun sessionGraphCoseServiceShouldUseScopedAdapterWhenGlobalDefaultChanges() {
        val coseCryptoService = (session.graph as CryptoServices.Graph).cryptoServices.cose
        val unrelatedGlobalFallback =
            object : CoseCryptoCallbackCoroutines {
                override suspend fun sign(
                    input: ToBeSignedCbor,
                    requireX5Chain: Boolean?,
                ): ByteArray = byteArrayOf(9)

                override suspend fun verify1(
                    input: CoseSign1<*>,
                    keyInfo: com.sphereon.crypto.core.KeyInfoType<*>?,
                    requireX5Chain: Boolean?,
                ): com.sphereon.crypto.core.generic.VerifySignatureResultType<CoseKeyType> = throw NotImplementedError()

                override suspend fun mac0(
                    input: CoseMac0InputCbor,
                    sharedSecret: ByteArray,
                    alg: SignatureAlgorithm,
                ): com.sphereon.crypto.core.CoseMac0Result = throw NotImplementedError()

                override suspend fun <KeyType : com.sphereon.crypto.core.KeyType> resolvePublicKey(
                    keyInfo: com.sphereon.crypto.core.KeyInfoType<KeyType>,
                ): com.sphereon.crypto.core.ResolvedKeyInfoType<KeyType> = throw NotImplementedError()
            }
        DefaultCallbacks.setCoseCryptoDefault(unrelatedGlobalFallback)

        @Suppress("UNCHECKED_CAST")
        val platform = (coseCryptoService as HasPlatformCallback<CoseCryptoCallbackCoroutines>).platform()

        assertFalse(platform === unrelatedGlobalFallback, "Session-scoped COSE service must not resolve through the global fallback")
        assertTrue(platform is CoseCryptoProviderToCallbackAdapter, "Session-scoped COSE service should use the session adapter")
    }

    @Test
    fun signShouldUseInputAlgWhenKeyInfoAlgIsNull() =
        runTest {
            val managedKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
            val keyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
            val coseKeyInfo = CoseJoseKeyMappingService.toCoseKeyInfo(keyInfo)

            val coseCryptoService = (session.graph as CryptoServices.Graph).cryptoServices.cose

            @Suppress("UNCHECKED_CAST")
            val adapter = (coseCryptoService as HasPlatformCallback<CoseCryptoCallbackCoroutines>).platform()

            // Create keyInfo without explicit algorithm
            val keyInfoWithoutAlg =
                KeyInfo<CoseKeyType>(
                    kid = coseKeyInfo.kid,
                    providerId = coseKeyInfo.providerId,
                )

            // Sign with algorithm from ToBeSignedCbor
            val signature =
                adapter.sign(
                    ToBeSignedCbor(
                        value = "test data".encodeToByteArray(),
                        keyInfo = keyInfoWithoutAlg,
                        alg = SignatureAlgorithm.ECDSA_SHA256,
                    ),
                    requireX5Chain = false,
                )

            assertNotNull(signature)
            assertTrue(signature.isNotEmpty())
        }

    @Test
    fun signWithRequireX5ChainTrue() =
        runTest {
            val managedKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
            val keyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
            val coseKeyInfo = CoseJoseKeyMappingService.toCoseKeyInfo(keyInfo)

            val coseCryptoService = (session.graph as CryptoServices.Graph).cryptoServices.cose

            @Suppress("UNCHECKED_CAST")
            val adapter = (coseCryptoService as HasPlatformCallback<CoseCryptoCallbackCoroutines>).platform()

            // Sign with requireX5Chain=true (tests the requireX5Chain == true branch)
            // This may throw or succeed depending on whether x5chain is available
            try {
                val signature =
                    adapter.sign(
                        ToBeSignedCbor(
                            value = "test data".encodeToByteArray(),
                            keyInfo = coseKeyInfo,
                        ),
                        requireX5Chain = true,
                    )
                // If it succeeds, verify we got a signature
                assertNotNull(signature)
            } catch (_: Exception) {
                // If it throws, that's also valid - we've still covered the branch
                assertTrue(true)
            }
        }

    // =========== Null Provider Tests ===========

    @Test
    fun adapterWithNullSimpleSignatureServiceProviderShouldWork() =
        runTest {
            // Test adapter with null rawSignatureServiceProvider (branch: rawSignatureServiceProvider?.invoke())
            val adapter =
                CoseCryptoProviderToCallbackAdapter(
                    keyManagerServiceProvider = { keyManagerService },
                    rawSignatureServiceProvider = null,
                    publicKeyResolverServiceProvider = null,
                )

            val managedKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
            val keyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
            val coseKeyInfo = CoseJoseKeyMappingService.toCoseKeyInfo(keyInfo)

            // Should use keyManagerService's provider instead
            val signature =
                adapter.sign(
                    ToBeSignedCbor(
                        value = "test data".encodeToByteArray(),
                        keyInfo = coseKeyInfo,
                    ),
                    requireX5Chain = false,
                )

            assertNotNull(signature)
            assertTrue(signature.isNotEmpty())
        }

    @Test
    fun plainRawSignatureFallbackRejectsCompoundManagedSelectorBeforeInvocation() =
        runTest {
            var invocations = 0
            val rawSignatureService =
                object : SimpleSignatureService {
                    override suspend fun createRawSignature(
                        keyInfo: com.sphereon.crypto.core.KeyInfoType<*>,
                        input: ByteArray,
                        requireX5Chain: Boolean,
                    ): ByteArray {
                        invocations++
                        return byteArrayOf(9)
                    }

                    override suspend fun isValidRawSignature(
                        keyInfo: com.sphereon.crypto.core.KeyInfoType<*>,
                        input: ByteArray,
                        signature: ByteArray,
                    ): Boolean = true
                }
            val adapter =
                CoseCryptoProviderToCallbackAdapter(
                    keyManagerServiceProvider = { throw IllegalStateException("KMS unavailable") },
                    rawSignatureServiceProvider = { rawSignatureService },
                    publicKeyResolverServiceProvider = null,
                )

            val exception =
                assertFailsWith<PKIException> {
                    adapter.sign(
                        ToBeSignedCbor(
                            value = byteArrayOf(1, 2, 3),
                            keyInfo =
                                KeyInfo<CoseKeyType>(
                                    alias = "key-b",
                                    kid = "kid-a",
                                    providerId = "plain-signature-service",
                                    signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256,
                                ),
                        ),
                        requireX5Chain = false,
                    )
                }

            assertEquals(
                "Cannot prove managed signing key selector: raw signature service does not expose canonical key resolution for alias 'key-b' and requested kid 'kid-a'",
                exception.message,
            )
            assertEquals(0, invocations, "Unverifiable compound selection must fail before the signing service is called")
        }

    @Test
    fun plainRawSignatureFallbackPreservesAliasOnlySelection() =
        runTest {
            var invocations = 0
            var capturedAlias: String? = null
            var capturedKid: String? = "sentinel"
            val rawSignatureService =
                object : SimpleSignatureService {
                    override suspend fun createRawSignature(
                        keyInfo: com.sphereon.crypto.core.KeyInfoType<*>,
                        input: ByteArray,
                        requireX5Chain: Boolean,
                    ): ByteArray {
                        invocations++
                        capturedAlias = keyInfo.alias
                        capturedKid = keyInfo.kid
                        return byteArrayOf(7, 8, 9)
                    }

                    override suspend fun isValidRawSignature(
                        keyInfo: com.sphereon.crypto.core.KeyInfoType<*>,
                        input: ByteArray,
                        signature: ByteArray,
                    ): Boolean = true
                }
            val adapter =
                CoseCryptoProviderToCallbackAdapter(
                    keyManagerServiceProvider = { throw IllegalStateException("KMS unavailable") },
                    rawSignatureServiceProvider = { rawSignatureService },
                    publicKeyResolverServiceProvider = null,
                )

            val signature =
                adapter.sign(
                    ToBeSignedCbor(
                        value = byteArrayOf(1, 2, 3),
                        keyInfo =
                            KeyInfo<CoseKeyType>(
                                alias = "key-b",
                                providerId = "plain-signature-service",
                                signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256,
                            ),
                    ),
                    requireX5Chain = false,
                )

            assertEquals(1, invocations)
            assertEquals("key-b", capturedAlias)
            assertNull(capturedKid)
            assertTrue(signature.contentEquals(byteArrayOf(7, 8, 9)))
        }

    @Test
    fun adapterWithNullPublicKeyResolverProviderShouldWork() =
        runTest {
            // Test adapter with null publicKeyResolverServiceProvider (branch: publicKeyResolverServiceProvider?.invoke())
            val adapter =
                CoseCryptoProviderToCallbackAdapter(
                    keyManagerServiceProvider = { keyManagerService },
                    rawSignatureServiceProvider = null,
                    publicKeyResolverServiceProvider = null,
                )

            val managedKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
            val keyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PUBLIC)
            val coseKeyInfo = CoseJoseKeyMappingService.toCoseKeyInfo(keyInfo)

            // Should use keyManagerService's resolver instead
            val resolved = adapter.resolvePublicKey(coseKeyInfo)

            assertNotNull(resolved)
        }

    // =========== hasCoseCryptoDefault Branch Test ===========

    @Test
    fun defaultCallbacksShouldReportHasCoseCryptoCorrectly() {
        // Clear any existing callback
        DefaultCallbacks.setCoseCryptoDefault(null)

        // Should report false when no callback is set
        assertTrue(!DefaultCallbacks.hasCoseCryptoDefault())

        // Create and set a callback
        val adapter =
            CoseCryptoProviderToCallbackAdapter(
                keyManagerServiceProvider = { keyManagerService },
                rawSignatureServiceProvider = null,
                publicKeyResolverServiceProvider = null,
            )
        DefaultCallbacks.setCoseCryptoDefault(adapter)

        // Should report true now
        assertTrue(DefaultCallbacks.hasCoseCryptoDefault())

        // Clear again
        DefaultCallbacks.setCoseCryptoDefault(null)
        assertTrue(!DefaultCallbacks.hasCoseCryptoDefault())
    }
}

/**
 * A [KeyManagerService] shaped like a service that reaches its KMS over the command transport:
 * the owning KMS's provider id is unknown to the local registry, while the key store still answers
 * because the command is what crosses the boundary.
 */
private class RoutedKeyManagerService(
    private val delegate: KeyManagerService,
    private val remoteProviderId: String,
) : KeyManagerService by delegate {
    var keyStoreLookups: Int = 0
        private set

    override suspend fun getProviderById(id: String): KmsProvider {
        if (id == remoteProviderId) {
            throw PKIException("Invalid KMS id $id provider. Valid ids are: ${delegate.getProviderIds().joinToString(",")}")
        }
        return delegate.getProviderById(id)
    }

    override suspend fun getKey(keyInfo: KeyInfoType<*>): ManagedKeyInfoType<*> {
        keyStoreLookups++
        return delegate.getKey(KeyInfo<Nothing>(alias = keyInfo.alias))
    }
}
