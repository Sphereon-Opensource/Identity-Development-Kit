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

@file:Suppress("unused")

package com.sphereon.crypto.kms.provider.azure

import com.sphereon.core.compat.Uuid
import com.sphereon.core.defaults.app.staticMinimalTestAppGraph
import com.sphereon.crypto.core.KeyEncoding
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.generic.Curve
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.KeyOperations
import com.sphereon.crypto.core.generic.KeyTypeMapping
import com.sphereon.crypto.core.generic.ManagedKeyPair
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.JwaAlgorithm
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.sign.model.SignInput
import com.sphereon.crypto.core.sign.model.Signature
import com.sphereon.crypto.core.sign.model.SignatureLevel
import com.sphereon.crypto.core.sign.model.SigningMode
import com.sphereon.crypto.kms.azure.BuildKonfig
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class AzureKeyVaultProviderTest {
    private lateinit var azureKeyVaultCryptoProvider: AzureKeyVaultCryptoProvider
    private var managedKeyPair: ManagedKeyPair? = null

    private val app = staticMinimalTestAppGraph(application = Any(), appId = "azure-keyvault-test", profile = "test", version = "1.0.0")
    private val session =
        app.userContextManager
            .getAnonymous()
            .sessionContextManager
            .getAnonymous()

    @BeforeTest
    fun setupProvider() {
        fun assertConfigValue(getter: () -> String?): String = getter() ?: throw IllegalStateException("Missing required configuration value")

        val azureConfig =
            AzureKmsProviderConfig(
                id = "azure-keyvault-test",
                applicationId = "azure-keyvault-test",
                keyvaultUrl = assertConfigValue { BuildKonfig.AZURE_KEYVAULT_URL },
                tenantId = assertConfigValue { BuildKonfig.AZURE_KEYVAULT_TENANT_ID },
                hsmType = HSMType.KEYVAULT,
                credentialOpts =
                    CredentialOpts(
                        credentialMode = CredentialMode.SERVICE_CLIENT_SECRET,
                        secretCredentialOpts =
                            SecretCredentialOpts(
                                clientId = assertConfigValue { BuildKonfig.AZURE_KEYVAULT_CLIENT_ID },
                                clientSecret = assertConfigValue { BuildKonfig.AZURE_KEYVAULT_CLIENT_SECRET },
                            ),
                    ),
                exponentialBackoffRetryOpts =
                    ExponentialBackoffRetryOpts(
                        maxRetries = 10,
                        baseDelayInMS = 500,
                        maxDelayInMS = 15000,
                    ),
            )

        azureKeyVaultCryptoProvider = AzureKeyVaultCryptoProvider(config = azureConfig)
    }

    private suspend fun getOrCreateTestKeyPair(): ManagedKeyPair {
        if (managedKeyPair == null) {
            managedKeyPair =
                azureKeyVaultCryptoProvider.generateKeyAsync(
                    alias = "azure-keyvault-test-${Uuid.v4String()}",
                    alg = SignatureAlgorithm.ECDSA_SHA256,
                    keyOperations = arrayOf(KeyOperations.SIGN, KeyOperations.VERIFY),
                )
        }
        return managedKeyPair!!
    }

    @AfterTest
    fun tearDown() =
        runTest {
            managedKeyPair?.let {
                try {
                    println("Deleting key in tearDown: ${it.kid}")
                    azureKeyVaultCryptoProvider.deleteKey(
                        it.toManagedKeyInfo<Jwk>(visibility = KeyVisibility.PUBLIC, keyEncoding = KeyEncoding.JOSE),
                    )
                    println("Key deleted successfully in tearDown")
                } catch (expected: Exception) {
                    println("Error deleting key in tearDown (might not have been created or already deleted): ${expected.message}")
                }
            }
            managedKeyPair = null
        }

    @Test
    fun testSupportedCurves() {
        val curves = azureKeyVaultCryptoProvider.supportedCurves()
        assertEquals(listOf(Curve.P_256, Curve.Secp256k1, Curve.P_384, Curve.P_521), curves.toList())
    }

    @Test
    fun testSupportedKeyTypes() {
        val keyTypes = azureKeyVaultCryptoProvider.supportedKeyTypes()
        assertEquals(listOf(KeyTypeMapping.EC, KeyTypeMapping.RSA), keyTypes.toList())
    }

    @Test
    fun testSupportedDigests() {
        val digests = azureKeyVaultCryptoProvider.supportedDigests()
        assertEquals(listOf(DigestAlg.SHA256, DigestAlg.SHA384, DigestAlg.SHA512), digests.toList())
    }

    @Test
    fun testGenerateKeyAsyncECDSA_SHA256() =
        runTest {
            val kp = getOrCreateTestKeyPair()
            assertNotNull(kp)
            assertNotNull(kp.joseToManagedKeyInfo().key.kid)
            assertEquals(JwaKeyType.EC, kp.jose.publicJwk.kty)
            assertEquals(JwaAlgorithm.ES256, kp.jose.publicJwk.alg)
            println(kp.jose.publicJwk.toString())
        }

    @Test
    fun testGenerateKeyAsyncECDSA_SHA384() =
        runTest {
            val managedKeyPair =
                azureKeyVaultCryptoProvider.generateKeyAsync(
                    alg = SignatureAlgorithm.ECDSA_SHA384,
                    keyOperations =
                        arrayOf(
                            KeyOperations.SIGN,
                            KeyOperations.VERIFY,
                        ),
                )
            assertNotNull(managedKeyPair)
            assertNotNull(managedKeyPair.joseToManagedKeyInfo().key.kid)
            assertEquals(JwaKeyType.EC, managedKeyPair.jose.publicJwk.kty)
            assertEquals(JwaAlgorithm.ES384, managedKeyPair.jose.publicJwk.alg)

            // Clean up by deleting the key
            azureKeyVaultCryptoProvider.deleteKey(
                managedKeyPair.toManagedKeyInfo<Jwk>(
                    visibility = KeyVisibility.PUBLIC,
                    keyEncoding = KeyEncoding.JOSE,
                ),
            )
        }

    @Test
    fun testGenerateKeyAsyncECDSA_SHA512() =
        runTest {
            val managedKeyPair =
                azureKeyVaultCryptoProvider.generateKeyAsync(
                    alg = SignatureAlgorithm.ECDSA_SHA512,
                    keyOperations =
                        arrayOf(
                            KeyOperations.SIGN,
                            KeyOperations.VERIFY,
                        ),
                )
            assertNotNull(managedKeyPair)
            assertNotNull(managedKeyPair.cborToManagedKeyInfo().key.kid)
            assertEquals(JwaKeyType.EC, managedKeyPair.jose.publicJwk.kty)
            assertEquals(JwaAlgorithm.ES512, managedKeyPair.jose.publicJwk.alg)

            // Clean up by deleting the key
            azureKeyVaultCryptoProvider.deleteKey(
                managedKeyPair.toManagedKeyInfo<Jwk>(
                    visibility = KeyVisibility.PUBLIC,
                    keyEncoding = KeyEncoding.JOSE,
                ),
            )
        }

    @Test
    fun testValidRawSignatureAndVerification() =
        runTest {
            val keyInfo = getOrCreateTestKeyPair().joseToManagedKeyInfo()
            assertNotNull(keyInfo)
            val signature =
                azureKeyVaultCryptoProvider.createRawSignature(
                    keyInfo = keyInfo,
                    input = "test".encodeToByteArray(),
                    requireX5Chain = false,
                )
            assertNotNull(signature)
            val verification =
                azureKeyVaultCryptoProvider.isValidRawSignature(
                    keyInfo = keyInfo,
                    signature = signature,
                    input = "test".encodeToByteArray(),
                )
            assertTrue(verification)
        }

    @Test
    fun testInvalidRawSignatureAndVerification() =
        runTest {
            val keyInfo = getOrCreateTestKeyPair().joseToManagedKeyInfo()
            assertNotNull(keyInfo)
            val signature =
                azureKeyVaultCryptoProvider.createRawSignature(
                    keyInfo = keyInfo,
                    input = "test".encodeToByteArray(),
                    requireX5Chain = false,
                )
            assertNotNull(signature)
            val verification =
                azureKeyVaultCryptoProvider.isValidRawSignature(
                    keyInfo = keyInfo,
                    signature = signature,
                    input = "test2".encodeToByteArray(),
                )
            assertFalse(verification)
        }

    @Test
    fun testGenerateKeyThrowsExceptionForUnsupportedAlgorithm() =
        runTest {
            val unsupportedAlg = SignatureAlgorithm.ED25519
            val exception =
                assertFailsWith<IllegalArgumentException> {
                    azureKeyVaultCryptoProvider.generateKeyAsync(alg = unsupportedAlg)
                }
            assertEquals("Signature algorithm ED25519 is not supported by Azure Key Vault", exception.message)
        }

    @Test
    fun testSupportedAlg() {
        val algorithms = azureKeyVaultCryptoProvider.supportedSignatureAlgorithms()
        assertEquals(
            listOf(
                SignatureAlgorithm.ECDSA_SHA256,
                SignatureAlgorithm.ECDSA_SHA384,
                SignatureAlgorithm.ECDSA_SHA512,
                SignatureAlgorithm.RSA_SSA_PSS_SHA256_MGF1,
                SignatureAlgorithm.RSA_SSA_PSS_SHA384_MGF1,
                SignatureAlgorithm.RSA_SSA_PSS_SHA512_MGF1,
                SignatureAlgorithm.RSA_SHA256,
                SignatureAlgorithm.RSA_SHA384,
                SignatureAlgorithm.RSA_SHA512,
            ),
            algorithms.toList(),
        )
    }

    @Test
    fun testCreateAndVerifySignature() =
        runTest {
            val keyInfo = getOrCreateTestKeyPair().joseToManagedKeyInfo()
            assertNotNull(keyInfo)

            val signInput =
                SignInput(
                    input = "test data to sign".encodeToByteArray(),
                    signMode = SigningMode.DOCUMENT,
                    name = "test-signature",
                )

            val signOutput =
                azureKeyVaultCryptoProvider.createSignature(
                    signInput = signInput,
                    keyInfo = keyInfo,
                    signatureAlgorithm = null, // Use the one from signInput
                )

            assertNotNull(signOutput)
            assertNotNull(signOutput.signedData)
            assertEquals(signInput.name, signOutput.name)

            // Verify via isValidRawSignature (takes raw input, hashes internally)
            // Use createRawSignature directly to avoid any intermediate processing by createSignature
            val rawSig = azureKeyVaultCryptoProvider.createRawSignature(keyInfo, signInput.input, false)
            assertTrue(rawSig.isNotEmpty(), "Raw signature should not be empty, got ${rawSig.size} bytes")

            val isValidRaw = azureKeyVaultCryptoProvider.isValidRawSignature(keyInfo, signInput.input, rawSig)
            assertTrue(isValidRaw, "Raw signature verification should succeed. Input size: ${signInput.input.size}, Signature size: ${rawSig.size}")

            // Verify via isValidSignature (structured verification with Signature object)
            val signature =
                Signature(
                    value = signOutput.signedData,
                    algorithm = SignatureAlgorithm.ECDSA_SHA256,
                    signMode = signInput.signMode,
                    keyInfo = keyInfo,
                    level = SignatureLevel.RAW,
                )
            val isValid = azureKeyVaultCryptoProvider.isValidSignature(signInput, signature)
            assertTrue(isValid, "Structured signature verification should succeed")
        }

    @Test
    @Ignore // takes too long in nodejs
    fun testListKeys() =
        runTest(timeout = 60_000.milliseconds) {
            // First ensure we have a test key
            val testKeyPair = getOrCreateTestKeyPair()

            val keys = azureKeyVaultCryptoProvider.listKeys()
            assertNotNull(keys)
            assertTrue(keys.isNotEmpty(), "Keys list should not be empty")

            // Verify that our test key is in the list
            val foundKey = keys.any { it.kid == testKeyPair.kid }
            assertTrue(foundKey, "Test key not found in listed keys")
        }

    @Test
    fun testGetKey() =
        runTest {
            val keyInfo = getOrCreateTestKeyPair().joseToManagedKeyInfo()
            val retrievedKey = azureKeyVaultCryptoProvider.getKey(keyInfo)

            assertNotNull(retrievedKey)
            assertEquals(keyInfo.kid, retrievedKey.kid)
            assertEquals(keyInfo.alias, retrievedKey.alias)
        }

//    @Test
//    fun testStoreAndDeleteKey() {
//        // Generate a key locally first
//        val ephemeralProvider = SoftwareKmsProviderImpl(
//            SoftwareKmsProviderConfig(
//                id = "ephemeral", exposePrivateKeysDuringGeneration = true // Important to get private key
//            ),
//            execution = session.asCoreApiServiceGraph().serviceExecution
//        )
//
//        // Generate a key with private key material
//        val localKeyPair = ephemeralProvider.generateKeyAsync(
//            alias = null, // Will use kid
//            use = JwkUse.sig,
//            keyOperations = arrayOf(KeyOperations.SIGN, KeyOperations.VERIFY),
//            alg = SignatureAlgorithm.ECDSA_SHA256
//        )
//
//        val resolvedKeyInfo = ResolvedKeyInfo(
//            key = localKeyPair.jose.privateJwk ?: localKeyPair.jose.publicJwk,
//            keyVisibility = KeyVisibility.PRIVATE,
//            keyType = KeyTypeMapping.EC,
//            alias = localKeyPair.alias,
//            providerId = localKeyPair.providerId,
//            kid = localKeyPair.kid,
//            signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256
//        )
//
//        // Store the key with a new reference
//        val alias = "test-store-key-${Uuid.v4String()}"
//        val storedKey = azureKeyVaultCryptoProvider.storeKey(
//            keyInfo = resolvedKeyInfo,
//            providerId = azureKeyVaultCryptoProvider.id,
//            alias = alias
//        )
//
//        assertNotNull(storedKey)
//        assertEquals(alias, storedKey.alias)
//
//        // Verify we can retrieve it
//        val retrievedKey = azureKeyVaultCryptoProvider.getKey(storedKey)
//        assertNotNull(retrievedKey)
//
//        // Delete the key
//        val deleted = azureKeyVaultCryptoProvider.deleteKey(storedKey)
//        assertTrue(deleted, "Key deletion failed")
//    }

    @Test
    fun testKeyVisibility() =
        runTest {
            val visibility = azureKeyVaultCryptoProvider.keyVisibility()
            assertEquals(KeyVisibility.PUBLIC, visibility)
        }

    @Test
    @Ignore // takse too long in nodejs
    fun testCertificateOperations() =
        runTest(timeout = 60_000.milliseconds) {
            // Skip test if using Managed HSM, which doesn't support certificates
            val config = azureKeyVaultCryptoProvider.config
            if (config.hsmType != HSMType.MANAGED_HSM) {
                // Test continues for standard Key Vault
                val keys = azureKeyVaultCryptoProvider.listKeys()
                assertNotNull(keys)
            } else {
                println("Skipping certificate test for Managed HSM")
            }
        }
}
