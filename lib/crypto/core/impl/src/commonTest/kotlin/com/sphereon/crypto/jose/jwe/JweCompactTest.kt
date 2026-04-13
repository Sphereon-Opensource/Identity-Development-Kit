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

import com.sphereon.core.api.Encoding
import com.sphereon.core.api.encodeTo
import com.sphereon.core.api.session.asCoreApiServiceGraph
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.generic.KeyOperations
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.jose.JwkUse
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.kms.asKeyManagerServiceGraph
import com.sphereon.crypto.core.testutil.createCryptoTestAppGraph
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderConfig
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderFactoryImpl
import com.sphereon.crypto.resolution.IdentifierContext
import com.sphereon.crypto.resolution.managed.ManagedOptsKeyInfo
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.random.CryptographyRandom
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for JWE Compact Serialization format.
 *
 * Tests the complete encrypt/decrypt flow using compact serialization:
 * - header.encryptedKey.iv.ciphertext.tag
 */
class JweCompactTest {
    private lateinit var keyManagerService: KeyManagerService
    private lateinit var jweService: JweService

    val app = createCryptoTestAppGraph(this)
    val context = app.userContextManager.getAnonymous()
    val session = context.sessionContextManager.createOrGetFromId("jwe-compact-test")

    @BeforeTest
    fun setUp() {
        // Register the software KMS provider
        val config =
            SoftwareKmsProviderConfig(
                id = "jwe-compact-test-provider",
                cryptographyProvider = CryptographyProvider.Default.name,
            )
        app as SoftwareKmsProviderFactoryImpl.Graph
        val softwareKmsProvider = app.softwareKmsProvider.create(config, session.asCoreApiServiceGraph().serviceExecution)

        // Get KeyManagerService and JweService from the session graph
        keyManagerService = session.graph.asKeyManagerServiceGraph().keyManagerService
        keyManagerService.registerProvider(softwareKmsProvider, makeDefaultKms = true)

        jweService = (session.graph as JweServiceImpl.Graph).jweService
    }

    @Test
    fun testEncryptAndDecryptCompactWithRSA_OAEP_A256GCM() =
        runTest {
            // Generate RSA key pair for encryption
            val managedKeyPair = keyManagerService.generateKeyAsync(alg = SignatureAlgorithm.RSA_SHA256)
            val keyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

            // Create recipient and decryptor identifiers
            val recipient =
                ManagedOptsKeyInfo(
                    identifier = keyInfo,
                    context =
                        IdentifierContext(
                            clientId = "jwe-test-client",
                            clientIdScheme = "jwe_compact",
                            issuer = "https://example.com/jwe-test",
                        ),
                )
            val decryptor =
                ManagedOptsKeyInfo(
                    identifier = keyInfo,
                    context =
                        IdentifierContext(
                            clientId = "jwe-test-client",
                            clientIdScheme = "jwe_compact",
                            issuer = "https://example.com/jwe-test",
                        ),
                )

            // Original plaintext
            val plaintext = "Hello, JWE Compact World!".encodeToByteArray()

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
            assertTrue(prepareResult.isOk, "Prepare JWE should succeed")
            val preparedJwe = prepareResult.value

            // Create compact JWE
            val createResult =
                jweService.createJweCompact(
                    CreateJweCompactArgs(
                        preparedJwe = preparedJwe,
                    ),
                )
            assertTrue(createResult.isOk, "Create compact JWE should succeed")
            val jweCompact = createResult.value

            // Verify compact format structure (5 parts separated by dots)
            val parts = jweCompact.serialize().split(".")
            assertEquals(5, parts.size, "Compact JWE should have 5 parts")
            assertNotNull(parts[0], "Protected header should exist")
            assertNotNull(parts[1], "Encrypted key should exist")
            assertNotNull(parts[2], "IV should exist")
            assertNotNull(parts[3], "Ciphertext should exist")
            assertNotNull(parts[4], "Auth tag should exist")

            // Decrypt the JWE
            val decryptResult =
                jweService.decryptJwe(
                    DecryptJweArgs(
                        jwe = jweCompact,
                        decryptor = decryptor,
                    ),
                )
            assertTrue(decryptResult.isOk, "Decrypt JWE should succeed")
            val decrypted = decryptResult.value

            // Verify plaintext matches
            assertEquals(
                plaintext.decodeToString(),
                decrypted.plaintext?.decodeToString(),
                "Decrypted plaintext should match original",
            )

            // Verify header
            assertEquals("RSA-OAEP", decrypted.header.alg, "Algorithm should be RSA-OAEP")
            assertEquals("A256GCM", decrypted.header.enc, "Encryption should be A256GCM")
        }

    @Test
    fun testEncryptAndDecryptCompactWithRSA_OAEP_256_A128GCM() =
        runTest {
            // Generate RSA key pair
            val managedKeyPair = keyManagerService.generateKeyAsync(alg = SignatureAlgorithm.RSA_SHA256)
            val keyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

            val recipient =
                ManagedOptsKeyInfo(
                    identifier = keyInfo,
                    context =
                        IdentifierContext(
                            clientId = "jwe-test",
                            clientIdScheme = "jwe",
                            issuer = "https://example.com",
                        ),
                )
            val decryptor =
                ManagedOptsKeyInfo(
                    identifier = keyInfo,
                    context =
                        IdentifierContext(
                            clientId = "jwe-test",
                            clientIdScheme = "jwe",
                            issuer = "https://example.com",
                        ),
                )

            val plaintext = "Testing RSA-OAEP-256 with A128GCM".encodeToByteArray()

            // Prepare with RSA-OAEP-256 and A128GCM
            val prepareResult =
                jweService.prepareJwe(
                    PrepareJweArgs(
                        plaintext = plaintext,
                        recipient = recipient,
                        keyEncryptionAlg = "RSA-OAEP-256",
                        contentEncryptionAlg = "A128GCM",
                    ),
                )
            assertTrue(prepareResult.isOk)

            val createResult =
                jweService.createJweCompact(
                    CreateJweCompactArgs(preparedJwe = prepareResult.value),
                )
            assertTrue(createResult.isOk)

            val decryptResult =
                jweService.decryptJwe(
                    DecryptJweArgs(
                        jwe = createResult.value,
                        decryptor = decryptor,
                    ),
                )
            assertTrue(decryptResult.isOk)
            assertEquals(
                plaintext.decodeToString(),
                decryptResult.value.plaintext?.decodeToString(),
            )
        }

    @Test
    fun testCompactSerializationAndParsing() =
        runTest {
            println("=== TEST START: testCompactSerializationAndParsing ===")
            // Generate key
            println("Generating key...")
            val managedKeyPair = keyManagerService.generateKeyAsync(alg = SignatureAlgorithm.RSA_SHA256)
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

            val plaintext = "Testing serialization".encodeToByteArray()
            println("Plaintext: ${plaintext.decodeToString()}")

            // Create JWE
            println("Preparing JWE...")
            val prepareResult =
                jweService.prepareJwe(
                    PrepareJweArgs(
                        plaintext = plaintext,
                        recipient = recipient,
                        keyEncryptionAlg = "RSA-OAEP",
                        contentEncryptionAlg = "A256GCM",
                    ),
                )
            println("Prepare result: isOk=${prepareResult.isOk}")
            if (!prepareResult.isOk) {
                println("Prepare failed: ${prepareResult.error}")
            }
            assertTrue(prepareResult.isOk, "Prepare JWE should succeed: ${if (prepareResult.isErr) prepareResult.error else ""}")

            println("Creating compact JWE...")
            val createResult =
                jweService.createJweCompact(
                    CreateJweCompactArgs(preparedJwe = prepareResult.value),
                )
            if (!createResult.isOk) {
                println("Create JWE failed: ${createResult.error}")
            }
            assertTrue(createResult.isOk, "Create JWE should succeed: ${if (createResult.isErr) createResult.error else ""}")
            val jweCompact = createResult.value

            // Serialize to string
            val serialized = jweCompact.serialize()
            assertNotNull(serialized)
            assertTrue(serialized.contains("."), "Serialized form should contain dots")

            // Parse back from string
            val parsed = JweCompact.parse(serialized)
            assertNotNull(parsed)

            // Verify parsed components match original
            assertEquals(jweCompact.header.alg, parsed.header.alg)
            assertEquals(jweCompact.header.enc, parsed.header.enc)
            assertEquals(jweCompact.encryptedKey.size, parsed.encryptedKey.size)
            assertEquals(jweCompact.iv.size, parsed.iv.size)
            assertEquals(jweCompact.ciphertext.size, parsed.ciphertext.size)
            assertEquals(jweCompact.authTag.size, parsed.authTag.size)
        }

    @Test
    fun testMultipleEncryptDecryptCycles() =
        runTest {
            // Test that we can encrypt/decrypt multiple times with same key
            val managedKeyPair = keyManagerService.generateKeyAsync(alg = SignatureAlgorithm.RSA_SHA256)
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
            val decryptor =
                ManagedOptsKeyInfo(
                    identifier = keyInfo,
                    context =
                        IdentifierContext(
                            clientId = "test",
                            clientIdScheme = "jwe",
                            issuer = "https://example.com",
                        ),
                )

            val messages =
                listOf(
                    "First message",
                    "Second message with more data",
                    "Third message: 你好世界", // Unicode test
                )

            messages.forEach { message ->
                val plaintext = message.encodeToByteArray()

                val prepareResult =
                    jweService.prepareJwe(
                        PrepareJweArgs(
                            plaintext = plaintext,
                            recipient = recipient,
                            keyEncryptionAlg = "RSA-OAEP",
                            contentEncryptionAlg = "A256GCM",
                        ),
                    )
                assertTrue(prepareResult.isOk, "Prepare should succeed for: $message")

                val createResult =
                    jweService.createJweCompact(
                        CreateJweCompactArgs(preparedJwe = prepareResult.value),
                    )
                assertTrue(createResult.isOk, "Create should succeed for: $message")

                val decryptResult =
                    jweService.decryptJwe(
                        DecryptJweArgs(
                            jwe = createResult.value,
                            decryptor = decryptor,
                        ),
                    )
                assertTrue(decryptResult.isOk, "Decrypt should succeed for: $message")
                assertEquals(
                    message,
                    decryptResult.value.plaintext?.decodeToString(),
                    "Decrypted message should match original",
                )
            }
        }

    @Test
    fun testEncryptAndDecryptCompactWithECDH_ES_A256GCM() =
        runTest {
            // Generate EC P-256 key pair for encryption (ECDH-ES uses EC keys)
            val managedKeyPair = keyManagerService.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
            val keyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

            // Create recipient and decryptor identifiers
            val recipient =
                ManagedOptsKeyInfo(
                    identifier = keyInfo,
                    context =
                        IdentifierContext(
                            clientId = "ecdh-es-test-client",
                            clientIdScheme = "jwe_compact",
                            issuer = "https://example.com/ecdh-es-test",
                        ),
                )
            val decryptor =
                ManagedOptsKeyInfo(
                    identifier = keyInfo,
                    context =
                        IdentifierContext(
                            clientId = "ecdh-es-test-client",
                            clientIdScheme = "jwe_compact",
                            issuer = "https://example.com/ecdh-es-test",
                        ),
                )

            // Original plaintext
            val plaintext = "Hello, ECDH-ES JWE Compact World!".encodeToByteArray()

            // Prepare JWE with ECDH-ES key agreement
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
            val preparedJwe = prepareResult.value

            // Create compact JWE
            val createResult =
                jweService.createJweCompact(
                    CreateJweCompactArgs(
                        preparedJwe = preparedJwe,
                    ),
                )
            assertTrue(createResult.isOk, "Create compact JWE should succeed: ${if (createResult.isErr) createResult.error else ""}")
            val jweCompact = createResult.value

            // Verify compact format structure (5 parts separated by dots)
            val parts = jweCompact.serialize().split(".")
            assertEquals(5, parts.size, "Compact JWE should have 5 parts")
            assertNotNull(parts[0], "Protected header should exist")

            // For ECDH-ES (direct key agreement), encrypted key should be empty
            assertEquals("", parts[1], "Encrypted key should be empty for ECDH-ES direct key agreement")

            assertNotNull(parts[2], "IV should exist")
            assertNotNull(parts[3], "Ciphertext should exist")
            assertNotNull(parts[4], "Auth tag should exist")

            // Verify header contains epk (ephemeral public key)
            assertNotNull(jweCompact.header.epk, "Header should contain epk (ephemeral public key)")
            assertEquals("ECDH-ES", jweCompact.header.alg, "Algorithm should be ECDH-ES")
            assertEquals("A256GCM", jweCompact.header.enc, "Encryption should be A256GCM")

            // Decrypt the JWE
            val decryptResult =
                jweService.decryptJwe(
                    DecryptJweArgs(
                        jwe = jweCompact,
                        decryptor = decryptor,
                    ),
                )
            assertTrue(decryptResult.isOk, "Decrypt JWE should succeed: ${if (decryptResult.isErr) decryptResult.error else ""}")
            val decrypted = decryptResult.value

            // Verify plaintext matches
            assertEquals(
                plaintext.decodeToString(),
                decrypted.plaintext?.decodeToString(),
                "Decrypted plaintext should match original",
            )

            // Verify header
            assertEquals("ECDH-ES", decrypted.header.alg, "Algorithm should be ECDH-ES")
            assertEquals("A256GCM", decrypted.header.enc, "Encryption should be A256GCM")
        }

    /**
     * Test for ECDH-ES+A128KW (key agreement + key wrap).
     */
    @Test
    fun testEncryptAndDecryptCompactWithECDH_ES_A128KW() =
        runTest {
            // Generate EC P-256 key pair for ECDH-ES+A128KW
            val managedKeyPair = keyManagerService.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
            val keyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

            val recipient =
                ManagedOptsKeyInfo(
                    identifier = keyInfo,
                    context =
                        IdentifierContext(
                            clientId = "ecdh-es-kw-test",
                            clientIdScheme = "jwe",
                            issuer = "https://example.com",
                        ),
                )
            val decryptor =
                ManagedOptsKeyInfo(
                    identifier = keyInfo,
                    context =
                        IdentifierContext(
                            clientId = "ecdh-es-kw-test",
                            clientIdScheme = "jwe",
                            issuer = "https://example.com",
                        ),
                )

            val plaintext = "Testing ECDH-ES+A128KW with A256GCM".encodeToByteArray()

            // Prepare with ECDH-ES+A128KW (key agreement + key wrap)
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

            val createResult =
                jweService.createJweCompact(
                    CreateJweCompactArgs(preparedJwe = prepareResult.value),
                )
            assertTrue(createResult.isOk, "Create JWE should succeed: ${if (createResult.isErr) createResult.error else ""}")
            val jweCompact = createResult.value

            // Verify header contains epk
            assertNotNull(jweCompact.header.epk, "Header should contain epk")
            assertEquals("ECDH-ES+A128KW", jweCompact.header.alg)

            // For ECDH-ES+A128KW, encrypted key should NOT be empty (contains wrapped CEK)
            val parts = jweCompact.serialize().split(".")
            assertTrue(parts[1].isNotEmpty(), "Encrypted key should NOT be empty for ECDH-ES+A128KW")

            val decryptResult =
                jweService.decryptJwe(
                    DecryptJweArgs(
                        jwe = jweCompact,
                        decryptor = decryptor,
                    ),
                )
            assertTrue(decryptResult.isOk, "Decrypt JWE should succeed: ${if (decryptResult.isErr) decryptResult.error else ""}")
            assertEquals(
                plaintext.decodeToString(),
                decryptResult.value.plaintext?.decodeToString(),
            )
        }

    @Test
    fun testECDH_ES_WithDifferentCurves() =
        runTest {
            // Test ECDH-ES with P-384 curve
            val managedKeyPair384 = keyManagerService.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA384)
            val keyInfo384 = managedKeyPair384.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

            val recipient384 =
                ManagedOptsKeyInfo(
                    identifier = keyInfo384,
                    context =
                        IdentifierContext(
                            clientId = "ecdh-es-p384-test",
                            clientIdScheme = "jwe",
                            issuer = "https://example.com",
                        ),
                )
            val decryptor384 =
                ManagedOptsKeyInfo(
                    identifier = keyInfo384,
                    context =
                        IdentifierContext(
                            clientId = "ecdh-es-p384-test",
                            clientIdScheme = "jwe",
                            issuer = "https://example.com",
                        ),
                )

            val plaintext = "Testing ECDH-ES with P-384 curve".encodeToByteArray()

            val prepareResult =
                jweService.prepareJwe(
                    PrepareJweArgs(
                        plaintext = plaintext,
                        recipient = recipient384,
                        keyEncryptionAlg = "ECDH-ES",
                        contentEncryptionAlg = "A256GCM",
                    ),
                )
            assertTrue(prepareResult.isOk, "Prepare JWE with P-384 should succeed: ${if (prepareResult.isErr) prepareResult.error else ""}")

            val createResult =
                jweService.createJweCompact(
                    CreateJweCompactArgs(preparedJwe = prepareResult.value),
                )
            assertTrue(createResult.isOk, "Create JWE with P-384 should succeed: ${if (createResult.isErr) createResult.error else ""}")

            // Verify epk uses same curve
            val epk = createResult.value.header.epk
            assertNotNull(epk, "Header should contain epk")
            assertEquals("P-384", epk.crv?.value, "EPK should use P-384 curve")

            val decryptResult =
                jweService.decryptJwe(
                    DecryptJweArgs(
                        jwe = createResult.value,
                        decryptor = decryptor384,
                    ),
                )
            assertTrue(decryptResult.isOk, "Decrypt JWE with P-384 should succeed: ${if (decryptResult.isErr) decryptResult.error else ""}")
            assertEquals(
                plaintext.decodeToString(),
                decryptResult.value.plaintext?.decodeToString(),
            )
        }

    // ========================================================================
    // Direct Encryption (dir) Mode Tests
    // ========================================================================

    /**
     * Creates a ManagedOptsKeyInfo for a symmetric key (pre-shared secret) stored in KMS.
     */
    private suspend fun createStoredSymmetricKeyOpts(
        keyBytes: ByteArray,
        alias: String,
    ): ManagedOptsKeyInfo {
        val jwk =
            Jwk(
                kty = JwaKeyType.oct,
                k = keyBytes.encodeTo(Encoding.BASE64URL),
            )
        val resolvedKeyInfo =
            com.sphereon.crypto.core.ResolvedKeyInfo(
                key = jwk,
                keyVisibility = KeyVisibility.PRIVATE,
                alias = alias,
            )
        // Store the key in the KMS
        val managedKeyInfo =
            keyManagerService.storeKey(
                keyInfo = resolvedKeyInfo,
                providerId = "jwe-compact-test-provider",
                alias = alias,
            )
        return ManagedOptsKeyInfo(
            identifier = managedKeyInfo,
            context =
                IdentifierContext(
                    clientId = "dir-test",
                    clientIdScheme = "jwe",
                    issuer = "https://example.com",
                ),
        )
    }

    @Test
    fun testEncryptAndDecryptCompactWithDir_A256GCM() =
        runTest {
            // 256-bit symmetric key (32 bytes) for A256GCM
            val sharedKey = CryptographyRandom.nextBytes(32)
            val keyOpts = createStoredSymmetricKeyOpts(sharedKey, "dir-test-a256gcm")

            // Original plaintext
            val plaintext = "Hello, Direct Encryption World!".encodeToByteArray()

            // Prepare JWE with direct encryption
            val prepareResult =
                jweService.prepareJwe(
                    PrepareJweArgs(
                        plaintext = plaintext,
                        recipient = keyOpts,
                        keyEncryptionAlg = "dir",
                        contentEncryptionAlg = "A256GCM",
                    ),
                )
            assertTrue(prepareResult.isOk, "Prepare JWE should succeed: ${if (prepareResult.isErr) prepareResult.error else ""}")
            val preparedJwe = prepareResult.value

            // Create compact JWE
            val createResult =
                jweService.createJweCompact(
                    CreateJweCompactArgs(
                        preparedJwe = preparedJwe,
                    ),
                )
            assertTrue(createResult.isOk, "Create compact JWE should succeed: ${if (createResult.isErr) createResult.error else ""}")
            val jweCompact = createResult.value

            // Verify compact format structure (5 parts separated by dots)
            val parts = jweCompact.serialize().split(".")
            assertEquals(5, parts.size, "Compact JWE should have 5 parts")
            assertNotNull(parts[0], "Protected header should exist")

            // For dir (direct encryption), encrypted key should be empty
            assertEquals("", parts[1], "Encrypted key should be empty for direct encryption")

            assertNotNull(parts[2], "IV should exist")
            assertNotNull(parts[3], "Ciphertext should exist")
            assertNotNull(parts[4], "Auth tag should exist")

            // Verify header
            assertEquals("dir", jweCompact.header.alg, "Algorithm should be dir")
            assertEquals("A256GCM", jweCompact.header.enc, "Encryption should be A256GCM")

            // Decrypt the JWE using the same shared key
            val decryptResult =
                jweService.decryptJwe(
                    DecryptJweArgs(
                        jwe = jweCompact,
                        decryptor = keyOpts,
                    ),
                )
            assertTrue(decryptResult.isOk, "Decrypt JWE should succeed: ${if (decryptResult.isErr) decryptResult.error else ""}")
            val decrypted = decryptResult.value

            // Verify plaintext matches
            assertEquals(
                plaintext.decodeToString(),
                decrypted.plaintext?.decodeToString(),
                "Decrypted plaintext should match original",
            )

            // Verify header
            assertEquals("dir", decrypted.header.alg, "Algorithm should be dir")
            assertEquals("A256GCM", decrypted.header.enc, "Encryption should be A256GCM")
        }

    @Test
    fun testEncryptAndDecryptCompactWithDir_A128GCM() =
        runTest {
            // 128-bit symmetric key (16 bytes) for A128GCM
            val sharedKey = CryptographyRandom.nextBytes(16)
            val keyOpts = createStoredSymmetricKeyOpts(sharedKey, "dir-test-a128gcm")

            val plaintext = "Testing direct encryption with A128GCM".encodeToByteArray()

            // Prepare with dir and A128GCM
            val prepareResult =
                jweService.prepareJwe(
                    PrepareJweArgs(
                        plaintext = plaintext,
                        recipient = keyOpts,
                        keyEncryptionAlg = "dir",
                        contentEncryptionAlg = "A128GCM",
                    ),
                )
            assertTrue(prepareResult.isOk, "Prepare JWE should succeed: ${if (prepareResult.isErr) prepareResult.error else ""}")

            val createResult =
                jweService.createJweCompact(
                    CreateJweCompactArgs(preparedJwe = prepareResult.value),
                )
            assertTrue(createResult.isOk, "Create JWE should succeed: ${if (createResult.isErr) createResult.error else ""}")
            val jweCompact = createResult.value

            // Verify encrypted key is empty for dir
            val parts = jweCompact.serialize().split(".")
            assertEquals("", parts[1], "Encrypted key should be empty for direct encryption")

            val decryptResult =
                jweService.decryptJwe(
                    DecryptJweArgs(
                        jwe = jweCompact,
                        decryptor = keyOpts,
                    ),
                )
            assertTrue(decryptResult.isOk, "Decrypt JWE should succeed: ${if (decryptResult.isErr) decryptResult.error else ""}")
            assertEquals(
                plaintext.decodeToString(),
                decryptResult.value.plaintext?.decodeToString(),
            )
        }

    @Test
    fun testEncryptAndDecryptCompactWithDir_A192GCM() =
        runTest {
            // 192-bit symmetric key (24 bytes) for A192GCM
            val sharedKey = CryptographyRandom.nextBytes(24)
            val keyOpts = createStoredSymmetricKeyOpts(sharedKey, "dir-test-a192gcm")

            val plaintext = "Testing direct encryption with A192GCM".encodeToByteArray()

            val prepareResult =
                jweService.prepareJwe(
                    PrepareJweArgs(
                        plaintext = plaintext,
                        recipient = keyOpts,
                        keyEncryptionAlg = "dir",
                        contentEncryptionAlg = "A192GCM",
                    ),
                )
            assertTrue(prepareResult.isOk, "Prepare JWE should succeed: ${if (prepareResult.isErr) prepareResult.error else ""}")

            val createResult =
                jweService.createJweCompact(
                    CreateJweCompactArgs(preparedJwe = prepareResult.value),
                )
            assertTrue(createResult.isOk, "Create JWE should succeed: ${if (createResult.isErr) createResult.error else ""}")

            val decryptResult =
                jweService.decryptJwe(
                    DecryptJweArgs(
                        jwe = createResult.value,
                        decryptor = keyOpts,
                    ),
                )
            assertTrue(decryptResult.isOk, "Decrypt JWE should succeed: ${if (decryptResult.isErr) decryptResult.error else ""}")
            assertEquals(
                plaintext.decodeToString(),
                decryptResult.value.plaintext?.decodeToString(),
            )
        }

    @Test
    fun testDir_WrongKeySizeForAlgorithm() =
        runTest {
            // 128-bit key (16 bytes) but trying to use with A256GCM (requires 32 bytes)
            val wrongSizeKey = CryptographyRandom.nextBytes(16)
            val keyOpts = createStoredSymmetricKeyOpts(wrongSizeKey, "dir-test-wrong-size")

            val plaintext = "This should fail".encodeToByteArray()

            // Prepare should fail due to key size mismatch
            val prepareResult =
                jweService.prepareJwe(
                    PrepareJweArgs(
                        plaintext = plaintext,
                        recipient = keyOpts,
                        keyEncryptionAlg = "dir",
                        contentEncryptionAlg = "A256GCM", // Requires 32-byte key
                    ),
                )
            assertTrue(prepareResult.isErr, "Prepare should fail due to key size mismatch")
            assertTrue(
                prepareResult.error.toString().contains("size") || prepareResult.error.toString().contains("16"),
                "Error should mention key size issue",
            )
        }

    @Test
    fun testMultipleDir_EncryptDecryptCycles() =
        runTest {
            // Test that we can encrypt/decrypt multiple times with same shared key
            val sharedKey = CryptographyRandom.nextBytes(32)
            val keyOpts = createStoredSymmetricKeyOpts(sharedKey, "dir-test-multiple-cycles")

            val messages =
                listOf(
                    "First direct encryption message",
                    "Second message with more data",
                    "Third message: 你好世界", // Unicode test
                )

            messages.forEach { message ->
                val plaintext = message.encodeToByteArray()

                val prepareResult =
                    jweService.prepareJwe(
                        PrepareJweArgs(
                            plaintext = plaintext,
                            recipient = keyOpts,
                            keyEncryptionAlg = "dir",
                            contentEncryptionAlg = "A256GCM",
                        ),
                    )
                assertTrue(prepareResult.isOk, "Prepare should succeed for: $message")

                val createResult =
                    jweService.createJweCompact(
                        CreateJweCompactArgs(preparedJwe = prepareResult.value),
                    )
                assertTrue(createResult.isOk, "Create should succeed for: $message")

                val decryptResult =
                    jweService.decryptJwe(
                        DecryptJweArgs(
                            jwe = createResult.value,
                            decryptor = keyOpts,
                        ),
                    )
                assertTrue(decryptResult.isOk, "Decrypt should succeed for: $message")
                assertEquals(
                    message,
                    decryptResult.value.plaintext?.decodeToString(),
                    "Decrypted message should match original",
                )
            }
        }

    @Test
    fun testDir_DecryptWithWrongKey() =
        runTest {
            // Create JWE with one key
            val encryptKey = CryptographyRandom.nextBytes(32)
            val encryptKeyOpts = createStoredSymmetricKeyOpts(encryptKey, "dir-test-encrypt-key")

            val plaintext = "Secret message".encodeToByteArray()

            val prepareResult =
                jweService.prepareJwe(
                    PrepareJweArgs(
                        plaintext = plaintext,
                        recipient = encryptKeyOpts,
                        keyEncryptionAlg = "dir",
                        contentEncryptionAlg = "A256GCM",
                    ),
                )
            assertTrue(prepareResult.isOk)

            val createResult =
                jweService.createJweCompact(
                    CreateJweCompactArgs(preparedJwe = prepareResult.value),
                )
            assertTrue(createResult.isOk)

            // Try to decrypt with a different key
            val wrongKey = CryptographyRandom.nextBytes(32)
            val wrongKeyOpts = createStoredSymmetricKeyOpts(wrongKey, "dir-test-wrong-key")

            // Decryption should fail with wrong key (auth tag won't match)
            // AES-GCM authentication failure may throw an exception or return an error
            val failed =
                try {
                    val decryptResult =
                        jweService.decryptJwe(
                            DecryptJweArgs(
                                jwe = createResult.value,
                                decryptor = wrongKeyOpts,
                            ),
                        )
                    decryptResult.isErr
                } catch (expected: Exception) {
                    // AEADBadTagException or similar is expected when wrong key is used
                    true
                }
            assertTrue(failed, "Decrypt should fail with wrong key")
        }
}
