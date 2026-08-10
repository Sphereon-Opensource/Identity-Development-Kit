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

package com.sphereon.crypto.jose.jwe

import com.sphereon.core.api.session.asCoreApiServiceGraph
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.kms.asKeyManagerServiceGraph
import com.sphereon.crypto.core.testutil.createCryptoTestAppGraph
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderConfig
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderFactoryImpl
import com.sphereon.crypto.resolution.IdentifierContext
import com.sphereon.crypto.resolution.managed.ManagedOptsKeyInfo
import dev.whyoleg.cryptography.CryptographyProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for JWE JSON Serialization formats (Flattened and General).
 *
 * Tests the complete encrypt/decrypt flow using JSON serialization:
 * - JWE JSON Flattened Serialization (single recipient)
 * - JWE JSON General Serialization (multiple recipients)
 */
class JweJsonSerializationTest {
    private lateinit var keyManagerService: KeyManagerService
    private lateinit var jweService: JweService

    val app = createCryptoTestAppGraph(this)
    val context = app.userContextManager.getAnonymous()
    val session = context.sessionContextManager.createOrGetFromId("jwe-json-test", principalType = com.sphereon.di.context.PrincipalType.USER)

    @BeforeTest
    fun setUp() {
        // Register the software KMS provider
        val config =
            SoftwareKmsProviderConfig(
                id = "jwe-json-test-provider",
                cryptographyProvider = CryptographyProvider.Default.name,
            )
        app as SoftwareKmsProviderFactoryImpl.Graph
        val softwareKmsProvider = app.softwareKmsProvider.create(config, session.asCoreApiServiceGraph().serviceExecution)

        // Get KeyManagerService and JweService from the session graph
        keyManagerService = session.graph.asKeyManagerServiceGraph().keyManagerService
        keyManagerService.registerProvider(softwareKmsProvider, makeDefaultKms = true)

        jweService = (session.graph as JweServiceImpl.Graph).jweService
    }

    // ========== JWE JSON Flattened Tests ==========

    @Test
    fun testCreateJweJsonFlattenedWithRSA_OAEP_A256GCM() =
        runTest {
            // Generate RSA key pair for encryption
            val managedKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.RSA_SHA256)
            val keyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

            val recipient =
                ManagedOptsKeyInfo(
                    identifier = keyInfo,
                    context =
                        IdentifierContext(
                            clientId = "jwe-json-flattened-test",
                            clientIdScheme = "jwe_json",
                            issuer = "https://example.com/jwe-json-test",
                        ),
                )

            val plaintext = "Hello, JWE JSON Flattened World!".encodeToByteArray()

            // Prepare JWE
            val prepareResult =
                jweService.prepareJwe(
                    PrepareJweArgs(
                        plaintext = plaintext,
                        recipient = recipient,
                        keyEncryptionAlg = "RSA-OAEP",
                        contentEncryptionAlg = "A256GCM",
                    ),
                )
            assertTrue(prepareResult.isOk, "Prepare JWE should succeed: ${if (prepareResult.isErr) prepareResult.error else ""}")

            // Create JSON Flattened JWE
            val createResult =
                jweService.createJweJsonFlattened(
                    CreateJweJsonArgs(
                        preparedJwe = prepareResult.value,
                    ),
                )
            assertTrue(createResult.isOk, "Create JSON Flattened JWE should succeed: ${if (createResult.isErr) createResult.error else ""}")
            val jweJsonFlattened = createResult.value

            // Verify JSON structure
            assertNotNull(jweJsonFlattened.protected, "Protected header should exist")
            assertNotNull(jweJsonFlattened.iv, "IV should exist")
            assertNotNull(jweJsonFlattened.ciphertext, "Ciphertext should exist")
            assertNotNull(jweJsonFlattened.tag, "Auth tag should exist")
            assertNotNull(jweJsonFlattened.encrypted_key, "Encrypted key should exist")

            // Decrypt the JWE
            val decryptor =
                ManagedOptsKeyInfo(
                    identifier = keyInfo,
                    context =
                        IdentifierContext(
                            clientId = "jwe-json-flattened-test",
                            clientIdScheme = "jwe_json",
                            issuer = "https://example.com/jwe-json-test",
                        ),
                )

            val decryptResult =
                jweService.decryptJwe(
                    DecryptJweArgs(
                        jwe = jweJsonFlattened,
                        decryptor = decryptor,
                    ),
                )
            assertTrue(decryptResult.isOk, "Decrypt JWE should succeed: ${if (decryptResult.isErr) decryptResult.error else ""}")

            assertEquals(
                plaintext.decodeToString(),
                decryptResult.value.plaintext?.decodeToString(),
                "Decrypted plaintext should match original",
            )
        }

    @Test
    fun testJweJsonFlattenedSerializationAndParsing() =
        runTest {
            // Generate RSA key pair
            val managedKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.RSA_SHA256)
            val keyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

            val recipient =
                ManagedOptsKeyInfo(
                    identifier = keyInfo,
                    context =
                        IdentifierContext(
                            clientId = "test",
                            clientIdScheme = "jwe",
                            issuer = "https://example.com",
                        ),
                )

            val plaintext = "Testing JSON Flattened serialization".encodeToByteArray()

            // Create JWE
            val prepareResult =
                jweService.prepareJwe(
                    PrepareJweArgs(
                        plaintext = plaintext,
                        recipient = recipient,
                        keyEncryptionAlg = "RSA-OAEP",
                        contentEncryptionAlg = "A256GCM",
                    ),
                )
            assertTrue(prepareResult.isOk)

            val createResult =
                jweService.createJweJsonFlattened(
                    CreateJweJsonArgs(preparedJwe = prepareResult.value),
                )
            assertTrue(createResult.isOk)
            val jweJsonFlattened = createResult.value

            // Serialize to JSON string
            val jsonString = jweJsonFlattened.toJsonString()
            assertNotNull(jsonString)
            assertTrue(jsonString.contains("protected"))
            assertTrue(jsonString.contains("iv"))
            assertTrue(jsonString.contains("ciphertext"))
            assertTrue(jsonString.contains("tag"))

            // Parse back from JSON string
            val parsed = JweJsonFlattened.fromJson(jsonString)
            assertNotNull(parsed)

            // Verify parsed components match original
            assertEquals(jweJsonFlattened.protected, parsed.protected)
            assertEquals(jweJsonFlattened.iv, parsed.iv)
            assertEquals(jweJsonFlattened.ciphertext, parsed.ciphertext)
            assertEquals(jweJsonFlattened.tag, parsed.tag)
        }

    @Test
    fun testCreateJweJsonFlattenedWithECDH_ES_A256GCM() =
        runTest {
            // Generate EC P-256 key pair for ECDH-ES
            val managedKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
            val keyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

            val recipient =
                ManagedOptsKeyInfo(
                    identifier = keyInfo,
                    context =
                        IdentifierContext(
                            clientId = "ecdh-es-json-test",
                            clientIdScheme = "jwe_json",
                            issuer = "https://example.com/ecdh-test",
                        ),
                )

            val plaintext = "Testing ECDH-ES with JSON Flattened".encodeToByteArray()

            // Prepare JWE with ECDH-ES
            val prepareResult =
                jweService.prepareJwe(
                    PrepareJweArgs(
                        plaintext = plaintext,
                        recipient = recipient,
                        keyEncryptionAlg = "ECDH-ES",
                        contentEncryptionAlg = "A256GCM",
                    ),
                )
            assertTrue(prepareResult.isOk, "Prepare JWE should succeed: ${if (prepareResult.isErr) prepareResult.error else ""}")

            // Create JSON Flattened JWE
            val createResult =
                jweService.createJweJsonFlattened(
                    CreateJweJsonArgs(preparedJwe = prepareResult.value),
                )
            assertTrue(createResult.isOk, "Create JSON Flattened JWE should succeed: ${if (createResult.isErr) createResult.error else ""}")

            // Decrypt
            val decryptor =
                ManagedOptsKeyInfo(
                    identifier = keyInfo,
                    context =
                        IdentifierContext(
                            clientId = "ecdh-es-json-test",
                            clientIdScheme = "jwe_json",
                            issuer = "https://example.com/ecdh-test",
                        ),
                )

            val decryptResult =
                jweService.decryptJwe(
                    DecryptJweArgs(
                        jwe = createResult.value,
                        decryptor = decryptor,
                    ),
                )
            assertTrue(decryptResult.isOk, "Decrypt should succeed: ${if (decryptResult.isErr) decryptResult.error else ""}")
            assertEquals(plaintext.decodeToString(), decryptResult.value.plaintext?.decodeToString())
        }

    // ========== JWE JSON General Tests ==========

    @Test
    fun testCreateJweJsonGeneralWithSingleRecipient() =
        runTest {
            // Generate RSA key pair
            val managedKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.RSA_SHA256)
            val keyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

            val recipient =
                ManagedOptsKeyInfo(
                    identifier = keyInfo,
                    context =
                        IdentifierContext(
                            clientId = "jwe-json-general-test",
                            clientIdScheme = "jwe_json",
                            issuer = "https://example.com/jwe-json-test",
                        ),
                )

            val plaintext = "Hello, JWE JSON General World!".encodeToByteArray()

            // Prepare JWE
            val prepareResult =
                jweService.prepareJwe(
                    PrepareJweArgs(
                        plaintext = plaintext,
                        recipient = recipient,
                        keyEncryptionAlg = "RSA-OAEP",
                        contentEncryptionAlg = "A256GCM",
                    ),
                )
            assertTrue(prepareResult.isOk, "Prepare JWE should succeed: ${if (prepareResult.isErr) prepareResult.error else ""}")

            // Create JSON General JWE with single recipient
            val createResult =
                jweService.createJweJsonGeneral(
                    CreateJweJsonGeneralArgs(
                        preparedJwe = prepareResult.value,
                        additionalRecipients = emptyList(),
                    ),
                )
            assertTrue(createResult.isOk, "Create JSON General JWE should succeed: ${if (createResult.isErr) createResult.error else ""}")
            val jweJsonGeneral = createResult.value

            // Verify JSON structure
            assertNotNull(jweJsonGeneral.protected, "Protected header should exist")
            assertNotNull(jweJsonGeneral.iv, "IV should exist")
            assertNotNull(jweJsonGeneral.ciphertext, "Ciphertext should exist")
            assertNotNull(jweJsonGeneral.tag, "Auth tag should exist")
            assertTrue(jweJsonGeneral.recipients.isNotEmpty(), "Recipients should exist")

            // Decrypt the JWE
            val decryptor =
                ManagedOptsKeyInfo(
                    identifier = keyInfo,
                    context =
                        IdentifierContext(
                            clientId = "jwe-json-general-test",
                            clientIdScheme = "jwe_json",
                            issuer = "https://example.com/jwe-json-test",
                        ),
                )

            val decryptResult =
                jweService.decryptJwe(
                    DecryptJweArgs(
                        jwe = jweJsonGeneral,
                        decryptor = decryptor,
                    ),
                )
            assertTrue(decryptResult.isOk, "Decrypt JWE should succeed: ${if (decryptResult.isErr) decryptResult.error else ""}")

            assertEquals(
                plaintext.decodeToString(),
                decryptResult.value.plaintext?.decodeToString(),
                "Decrypted plaintext should match original",
            )
        }

    @Test
    fun testCreateJweJsonGeneralWithMultipleRecipients() =
        runTest {
            // Generate two RSA key pairs for different recipients
            val managedKeyPair1 = keyManagerService.generateKey(alg = SignatureAlgorithm.RSA_SHA256)
            val keyInfo1 = managedKeyPair1.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

            val managedKeyPair2 = keyManagerService.generateKey(alg = SignatureAlgorithm.RSA_SHA256)
            val keyInfo2 = managedKeyPair2.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

            val recipient1 =
                ManagedOptsKeyInfo(
                    identifier = keyInfo1,
                    context =
                        IdentifierContext(
                            clientId = "recipient-1",
                            clientIdScheme = "jwe_json",
                            issuer = "https://example.com/recipient-1",
                        ),
                )

            val recipient2 =
                ManagedOptsKeyInfo(
                    identifier = keyInfo2,
                    context =
                        IdentifierContext(
                            clientId = "recipient-2",
                            clientIdScheme = "jwe_json",
                            issuer = "https://example.com/recipient-2",
                        ),
                )

            val plaintext = "Encrypted for multiple recipients".encodeToByteArray()

            // Prepare JWE for first recipient
            val prepareResult =
                jweService.prepareJwe(
                    PrepareJweArgs(
                        plaintext = plaintext,
                        recipient = recipient1,
                        keyEncryptionAlg = "RSA-OAEP",
                        contentEncryptionAlg = "A256GCM",
                    ),
                )
            assertTrue(prepareResult.isOk, "Prepare JWE should succeed")

            // Create JSON General JWE with multiple recipients
            val createResult =
                jweService.createJweJsonGeneral(
                    CreateJweJsonGeneralArgs(
                        preparedJwe = prepareResult.value,
                        additionalRecipients = listOf(JweRecipientInfo(recipient = recipient2)),
                    ),
                )
            assertTrue(createResult.isOk, "Create JSON General JWE should succeed: ${if (createResult.isErr) createResult.error else ""}")
            val jweJsonGeneral = createResult.value

            // Verify multiple recipients
            assertEquals(2, jweJsonGeneral.recipients.size, "Should have 2 recipients")

            // Test decryption with first recipient's key
            val decryptor1 =
                ManagedOptsKeyInfo(
                    identifier = keyInfo1,
                    context =
                        IdentifierContext(
                            clientId = "recipient-1",
                            clientIdScheme = "jwe_json",
                            issuer = "https://example.com/recipient-1",
                        ),
                )

            val decryptResult1 =
                jweService.decryptJwe(
                    DecryptJweArgs(
                        jwe = jweJsonGeneral,
                        decryptor = decryptor1,
                    ),
                )
            assertTrue(decryptResult1.isOk, "Decrypt with first recipient should succeed: ${if (decryptResult1.isErr) decryptResult1.error else ""}")
            assertEquals(plaintext.decodeToString(), decryptResult1.value.plaintext?.decodeToString())

            // Test decryption with second recipient's key
            val decryptor2 =
                ManagedOptsKeyInfo(
                    identifier = keyInfo2,
                    context =
                        IdentifierContext(
                            clientId = "recipient-2",
                            clientIdScheme = "jwe_json",
                            issuer = "https://example.com/recipient-2",
                        ),
                )

            val decryptResult2 =
                jweService.decryptJwe(
                    DecryptJweArgs(
                        jwe = jweJsonGeneral,
                        decryptor = decryptor2,
                    ),
                )
            assertTrue(decryptResult2.isOk, "Decrypt with second recipient should succeed: ${if (decryptResult2.isErr) decryptResult2.error else ""}")
            assertEquals(plaintext.decodeToString(), decryptResult2.value.plaintext?.decodeToString())
        }

    @Test
    fun testJweJsonGeneralSerializationAndParsing() =
        runTest {
            // Generate RSA key pair
            val managedKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.RSA_SHA256)
            val keyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

            val recipient =
                ManagedOptsKeyInfo(
                    identifier = keyInfo,
                    context =
                        IdentifierContext(
                            clientId = "test",
                            clientIdScheme = "jwe",
                            issuer = "https://example.com",
                        ),
                )

            val plaintext = "Testing JSON General serialization".encodeToByteArray()

            // Create JWE
            val prepareResult =
                jweService.prepareJwe(
                    PrepareJweArgs(
                        plaintext = plaintext,
                        recipient = recipient,
                        keyEncryptionAlg = "RSA-OAEP",
                        contentEncryptionAlg = "A256GCM",
                    ),
                )
            assertTrue(prepareResult.isOk)

            val createResult =
                jweService.createJweJsonGeneral(
                    CreateJweJsonGeneralArgs(preparedJwe = prepareResult.value, additionalRecipients = emptyList()),
                )
            assertTrue(createResult.isOk)
            val jweJsonGeneral = createResult.value

            // Serialize to JSON string
            val jsonString = jweJsonGeneral.toJsonString()
            assertNotNull(jsonString)
            assertTrue(jsonString.contains("protected"))
            assertTrue(jsonString.contains("iv"))
            assertTrue(jsonString.contains("ciphertext"))
            assertTrue(jsonString.contains("tag"))
            assertTrue(jsonString.contains("recipients"))

            // Parse back from JSON string
            val parsed = JweJsonGeneral.fromJson(jsonString)
            assertNotNull(parsed)

            // Verify parsed components match original
            assertEquals(jweJsonGeneral.protected, parsed.protected)
            assertEquals(jweJsonGeneral.iv, parsed.iv)
            assertEquals(jweJsonGeneral.ciphertext, parsed.ciphertext)
            assertEquals(jweJsonGeneral.tag, parsed.tag)
            assertEquals(jweJsonGeneral.recipients.size, parsed.recipients.size)
        }

    @Test
    fun testCreateJweJsonGeneralWithECDH_ES_A128KW() =
        runTest {
            // Generate EC P-256 key pair for ECDH-ES+A128KW
            val managedKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
            val keyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

            val recipient =
                ManagedOptsKeyInfo(
                    identifier = keyInfo,
                    context =
                        IdentifierContext(
                            clientId = "ecdh-es-kw-test",
                            clientIdScheme = "jwe_json",
                            issuer = "https://example.com/ecdh-kw-test",
                        ),
                )

            val plaintext = "Testing ECDH-ES+A128KW with JSON General".encodeToByteArray()

            // Prepare JWE with ECDH-ES+A128KW
            val prepareResult =
                jweService.prepareJwe(
                    PrepareJweArgs(
                        plaintext = plaintext,
                        recipient = recipient,
                        keyEncryptionAlg = "ECDH-ES+A128KW",
                        contentEncryptionAlg = "A256GCM",
                    ),
                )
            assertTrue(prepareResult.isOk, "Prepare JWE should succeed: ${if (prepareResult.isErr) prepareResult.error else ""}")

            // Create JSON General JWE
            val createResult =
                jweService.createJweJsonGeneral(
                    CreateJweJsonGeneralArgs(preparedJwe = prepareResult.value, additionalRecipients = emptyList()),
                )
            assertTrue(createResult.isOk, "Create JSON General JWE should succeed: ${if (createResult.isErr) createResult.error else ""}")

            // Verify the encrypted key exists (ECDH-ES+A128KW wraps the CEK)
            val jweJsonGeneral = createResult.value
            assertTrue(jweJsonGeneral.recipients.isNotEmpty())
            assertNotNull(jweJsonGeneral.recipients[0].encrypted_key)

            // Decrypt
            val decryptor =
                ManagedOptsKeyInfo(
                    identifier = keyInfo,
                    context =
                        IdentifierContext(
                            clientId = "ecdh-es-kw-test",
                            clientIdScheme = "jwe_json",
                            issuer = "https://example.com/ecdh-kw-test",
                        ),
                )

            val decryptResult =
                jweService.decryptJwe(
                    DecryptJweArgs(
                        jwe = createResult.value,
                        decryptor = decryptor,
                    ),
                )
            assertTrue(decryptResult.isOk, "Decrypt should succeed: ${if (decryptResult.isErr) decryptResult.error else ""}")
            assertEquals(plaintext.decodeToString(), decryptResult.value.plaintext?.decodeToString())
        }

    // ========== Error Handling Tests ==========

    @Test
    fun testDecryptWithWrongKeyFails() =
        runTest {
            // Generate two different key pairs
            val managedKeyPair1 = keyManagerService.generateKey(alg = SignatureAlgorithm.RSA_SHA256)
            val keyInfo1 = managedKeyPair1.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

            val managedKeyPair2 = keyManagerService.generateKey(alg = SignatureAlgorithm.RSA_SHA256)
            val keyInfo2 = managedKeyPair2.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

            val recipient =
                ManagedOptsKeyInfo(
                    identifier = keyInfo1,
                    context =
                        IdentifierContext(
                            clientId = "test",
                            clientIdScheme = "jwe",
                            issuer = "https://example.com",
                        ),
                )

            val plaintext = "Secret message".encodeToByteArray()

            // Create JWE encrypted for key1
            val prepareResult =
                jweService.prepareJwe(
                    PrepareJweArgs(
                        plaintext = plaintext,
                        recipient = recipient,
                        keyEncryptionAlg = "RSA-OAEP",
                        contentEncryptionAlg = "A256GCM",
                    ),
                )
            assertTrue(prepareResult.isOk)

            val createResult =
                jweService.createJweJsonFlattened(
                    CreateJweJsonArgs(preparedJwe = prepareResult.value),
                )
            assertTrue(createResult.isOk)

            // Try to decrypt with wrong key (key2)
            val wrongDecryptor =
                ManagedOptsKeyInfo(
                    identifier = keyInfo2,
                    context =
                        IdentifierContext(
                            clientId = "wrong-key",
                            clientIdScheme = "jwe",
                            issuer = "https://example.com",
                        ),
                )

            // Decryption with wrong key should either return an error result or throw an exception
            try {
                val decryptResult =
                    jweService.decryptJwe(
                        DecryptJweArgs(
                            jwe = createResult.value,
                            decryptor = wrongDecryptor,
                        ),
                    )
                // If it doesn't throw, it should return an error
                assertTrue(decryptResult.isErr, "Decryption with wrong key should fail")
            } catch (e: Exception) {
                // Expected - decryption fails with wrong key
                // Various exception types are valid depending on the crypto implementation:
                // - IllegalStateException: general failure
                // - BadPaddingException: RSA decryption failure
                // - GeneralSecurityException: Java crypto failures
                // - AEADBadTagException: GCM authentication failure
                val isExpectedException =
                    e is IllegalStateException ||
                        e.message?.contains("decrypt", ignoreCase = true) == true ||
                        e.message?.contains("key", ignoreCase = true) == true ||
                        e.message?.contains("padding", ignoreCase = true) == true ||
                        e.cause?.message?.contains("decrypt", ignoreCase = true) == true ||
                        e.cause?.message?.contains("key", ignoreCase = true) == true ||
                        e.cause?.message?.contains("padding", ignoreCase = true) == true
                assertTrue(
                    isExpectedException,
                    "Expected decryption failure exception but got: ${e::class.simpleName}: ${e.message}, cause: ${e.cause?.let { "${it::class.simpleName}: ${it.message}" }}",
                )
            }
        }

    // ========== DSL Builder Integration Tests ==========

    @Test
    fun testDslBuilderIntegrationForJsonFlattened() =
        runTest {
            val managedKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.RSA_SHA256)
            val keyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

            val recipient =
                ManagedOptsKeyInfo(
                    identifier = keyInfo,
                    context =
                        IdentifierContext(
                            clientId = "dsl-test",
                            clientIdScheme = "jwe",
                            issuer = "https://example.com",
                        ),
                )

            val plaintext = "Testing DSL builders".encodeToByteArray()

            // Use DSL builders
            val prepareArgs =
                prepareJweArgs {
                    plaintext(plaintext)
                    recipient(recipient)
                    keyEncryptionAlg("RSA-OAEP")
                    contentEncryptionAlg("A256GCM")
                }

            val prepareResult = jweService.prepareJwe(prepareArgs)
            assertTrue(prepareResult.isOk)

            val createArgs =
                createJweJsonArgs {
                    preparedJwe(prepareResult.value)
                }

            val createResult = jweService.createJweJsonFlattened(createArgs)
            assertTrue(createResult.isOk)
            assertNotNull(createResult.value.ciphertext)
        }

    @Test
    fun testDslBuilderIntegrationForJsonGeneral() =
        runTest {
            val managedKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.RSA_SHA256)
            val keyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

            val recipient =
                ManagedOptsKeyInfo(
                    identifier = keyInfo,
                    context =
                        IdentifierContext(
                            clientId = "dsl-test-general",
                            clientIdScheme = "jwe",
                            issuer = "https://example.com",
                        ),
                )

            val plaintext = "Testing DSL builders for JSON General".encodeToByteArray()

            // Use DSL builders
            val prepareArgs =
                prepareJweArgs {
                    plaintext(plaintext)
                    recipient(recipient)
                    keyEncryptionAlg("RSA-OAEP")
                    contentEncryptionAlg("A256GCM")
                }

            val prepareResult = jweService.prepareJwe(prepareArgs)
            assertTrue(prepareResult.isOk)

            val createArgs =
                createJweJsonGeneralArgs {
                    preparedJwe(prepareResult.value)
                }

            val createResult = jweService.createJweJsonGeneral(createArgs)
            assertTrue(createResult.isOk)
            assertNotNull(createResult.value.ciphertext)
            assertTrue(createResult.value.recipients.isNotEmpty())
        }
}
