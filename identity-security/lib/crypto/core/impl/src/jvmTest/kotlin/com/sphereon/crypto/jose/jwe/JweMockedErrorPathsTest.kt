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

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.ManagedKeyInfo
import com.sphereon.crypto.core.ResolvedKeyInfo
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.JwaCurve
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.jose.JwkType
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.kms.command.EcdhDeriveCommand
import com.sphereon.crypto.core.kms.command.EcdhDeriveResult
import com.sphereon.crypto.jose.jwe.command.CreateJweCompactCommandImpl
import com.sphereon.crypto.jose.jwe.command.CreateJweJsonFlattenedCommandImpl
import com.sphereon.crypto.jose.jwe.command.CreateJweJsonGeneralCommandImpl
import com.sphereon.crypto.jose.jwe.command.DecryptJweCommandImpl
import com.sphereon.crypto.jose.jwe.command.PrepareJweCommandImpl
import com.sphereon.crypto.resolution.IdentifierContext
import com.sphereon.crypto.resolution.managed.ManagedIdentifierJwkResult
import com.sphereon.crypto.resolution.managed.ManagedIdentifierOptsOrResult
import com.sphereon.crypto.resolution.managed.ManagedIdentifierResult
import com.sphereon.crypto.resolution.managed.ManagedOptsKeyInfo
import com.sphereon.crypto.resolution.managed.MultiManagedIdentifierService
import com.sphereon.di.context.createAnonymousSessionContext
import dev.whyoleg.cryptography.random.CryptographyRandom
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Mock-based tests for JWE command error paths.
 *
 * These tests use mockk to simulate various error conditions that are
 * difficult to trigger with integration tests alone.
 */
class JweMockedErrorPathsTest {
    private val mockSessionContext = createAnonymousSessionContext("mock-test-session", "mock-test-session-correlation")

    // ========================================================================
    // PrepareJweCommandImpl Error Path Tests
    // ========================================================================

    @Test
    fun testPrepareJwe_IdentifierResolutionFails() =
        runTest {
            val mockExecution = mockk<SessionExecution>(relaxed = true)
            val mockIdentifierService = mockk<MultiManagedIdentifierService>()

            // Mock identifier resolution to fail
            coEvery { mockIdentifierService.resolve(any<ManagedIdentifierOptsOrResult>()) } returns
                IdkResult.err(IdkError.fromString("Failed to resolve identifier"))

            val command =
                PrepareJweCommandImpl(
                    execution = mockExecution,
                    identifierService = mockIdentifierService,
                )

            val recipient = mockk<ManagedIdentifierOptsOrResult>()
            val args =
                PrepareJweArgs(
                    plaintext = "test".encodeToByteArray(),
                    recipient = recipient,
                    keyEncryptionAlg = "RSA-OAEP",
                    contentEncryptionAlg = "A256GCM",
                )

            val result = command.execute(args)

            assertTrue(result.isErr, "Should fail when identifier resolution fails")
            assertNotNull(result.error.message)
        }

    @Test
    fun testPrepareJwe_NullPlaintext() =
        runTest {
            val mockExecution = mockk<SessionExecution>(relaxed = true)
            val mockIdentifierService = mockk<MultiManagedIdentifierService>()

            val command =
                PrepareJweCommandImpl(
                    execution = mockExecution,
                    identifierService = mockIdentifierService,
                )

            val args =
                PrepareJweArgs(
                    plaintext = null,
                    recipient = mockk(),
                    keyEncryptionAlg = "RSA-OAEP",
                    contentEncryptionAlg = "A256GCM",
                )

            val result = command.execute(args)

            assertTrue(result.isErr, "Should fail when plaintext is null")
            assertNotNull(result.error.message)
        }

    @Test
    fun testPrepareJwe_NullRecipient() =
        runTest {
            val mockExecution = mockk<SessionExecution>(relaxed = true)
            val mockIdentifierService = mockk<MultiManagedIdentifierService>()

            val command =
                PrepareJweCommandImpl(
                    execution = mockExecution,
                    identifierService = mockIdentifierService,
                )

            val args =
                PrepareJweArgs(
                    plaintext = "test".encodeToByteArray(),
                    recipient = null,
                    keyEncryptionAlg = "RSA-OAEP",
                    contentEncryptionAlg = "A256GCM",
                )

            val result = command.execute(args)

            assertTrue(result.isErr, "Should fail when recipient is null")
            assertNotNull(result.error.message)
        }

    // ========================================================================
    // CreateJweJsonGeneralCommandImpl Error Path Tests
    // ========================================================================

    @Test
    fun testCreateJweJsonGeneral_NullPreparedJwe() =
        runTest {
            val mockExecution = mockk<SessionExecution>(relaxed = true)
            val mockKeyManagerService = mockk<KeyManagerService>()
            val mockIdentifierService = mockk<MultiManagedIdentifierService>()

            val command =
                CreateJweJsonGeneralCommandImpl(
                    execution = mockExecution,
                    keyManagerService = mockKeyManagerService,
                    identifierService = mockIdentifierService,
                )

            val args =
                CreateJweJsonGeneralArgs(
                    preparedJwe = null,
                )

            val result = command.execute(args)

            assertTrue(result.isErr, "Should fail when preparedJwe is null")
            assertNotNull(result.error.message)
        }

    @Test
    fun testCreateJweJsonGeneral_NullPlaintext() =
        runTest {
            val mockExecution = mockk<SessionExecution>(relaxed = true)
            val mockKeyManagerService = mockk<KeyManagerService>()
            val mockIdentifierService = mockk<MultiManagedIdentifierService>()

            val command =
                CreateJweJsonGeneralCommandImpl(
                    execution = mockExecution,
                    keyManagerService = mockKeyManagerService,
                    identifierService = mockIdentifierService,
                )

            val header = JweHeader()
            header.alg = "RSA-OAEP"
            header.enc = "A256GCM"

            val preparedJwe =
                PreparedJwe(
                    header = header,
                    plaintext = null,
                    cek = CryptographyRandom.nextBytes(32),
                    recipient = mockk(),
                )

            val args = CreateJweJsonGeneralArgs(preparedJwe = preparedJwe)

            val result = command.execute(args)

            assertTrue(result.isErr, "Should fail when plaintext is null")
            assertNotNull(result.error.message)
        }

    @Test
    fun testCreateJweJsonGeneral_NullCek() =
        runTest {
            val mockExecution = mockk<SessionExecution>(relaxed = true)
            val mockKeyManagerService = mockk<KeyManagerService>()
            val mockIdentifierService = mockk<MultiManagedIdentifierService>()

            val command =
                CreateJweJsonGeneralCommandImpl(
                    execution = mockExecution,
                    keyManagerService = mockKeyManagerService,
                    identifierService = mockIdentifierService,
                )

            val header = JweHeader()
            header.alg = "RSA-OAEP"
            header.enc = "A256GCM"

            val preparedJwe =
                PreparedJwe(
                    header = header,
                    plaintext = "test".encodeToByteArray(),
                    cek = null,
                    recipient = mockk(),
                )

            val args = CreateJweJsonGeneralArgs(preparedJwe = preparedJwe)

            val result = command.execute(args)

            assertTrue(result.isErr, "Should fail when CEK is null")
            assertNotNull(result.error.message)
        }

    @Test
    fun testCreateJweJsonGeneral_NullRecipient() =
        runTest {
            val mockExecution = mockk<SessionExecution>(relaxed = true)
            val mockKeyManagerService = mockk<KeyManagerService>()
            val mockIdentifierService = mockk<MultiManagedIdentifierService>()

            val command =
                CreateJweJsonGeneralCommandImpl(
                    execution = mockExecution,
                    keyManagerService = mockKeyManagerService,
                    identifierService = mockIdentifierService,
                )

            val header = JweHeader()
            header.alg = "RSA-OAEP"
            header.enc = "A256GCM"

            val preparedJwe =
                PreparedJwe(
                    header = header,
                    plaintext = "test".encodeToByteArray(),
                    cek = CryptographyRandom.nextBytes(32),
                    recipient = null,
                )

            val args = CreateJweJsonGeneralArgs(preparedJwe = preparedJwe)

            val result = command.execute(args)

            assertTrue(result.isErr, "Should fail when recipient is null")
            assertNotNull(result.error.message)
        }

    @Test
    fun testCreateJweJsonGeneral_NullAlgHeader() =
        runTest {
            val mockExecution = mockk<SessionExecution>(relaxed = true)
            val mockKeyManagerService = mockk<KeyManagerService>()
            val mockIdentifierService = mockk<MultiManagedIdentifierService>()

            val command =
                CreateJweJsonGeneralCommandImpl(
                    execution = mockExecution,
                    keyManagerService = mockKeyManagerService,
                    identifierService = mockIdentifierService,
                )

            val header = JweHeader()
            header.alg = null // Missing alg
            header.enc = "A256GCM"

            val preparedJwe =
                PreparedJwe(
                    header = header,
                    plaintext = "test".encodeToByteArray(),
                    cek = CryptographyRandom.nextBytes(32),
                    recipient = mockk(),
                )

            val args = CreateJweJsonGeneralArgs(preparedJwe = preparedJwe)

            val result = command.execute(args)

            assertTrue(result.isErr, "Should fail when alg header is null")
            assertNotNull(result.error.message)
        }

    @Test
    fun testCreateJweJsonGeneral_NullEncHeader() =
        runTest {
            val mockExecution = mockk<SessionExecution>(relaxed = true)
            val mockKeyManagerService = mockk<KeyManagerService>()
            val mockIdentifierService = mockk<MultiManagedIdentifierService>()

            val command =
                CreateJweJsonGeneralCommandImpl(
                    execution = mockExecution,
                    keyManagerService = mockKeyManagerService,
                    identifierService = mockIdentifierService,
                )

            val header = JweHeader()
            header.alg = "RSA-OAEP"
            header.enc = null // Missing enc

            val preparedJwe =
                PreparedJwe(
                    header = header,
                    plaintext = "test".encodeToByteArray(),
                    cek = CryptographyRandom.nextBytes(32),
                    recipient = mockk(),
                )

            val args = CreateJweJsonGeneralArgs(preparedJwe = preparedJwe)

            val result = command.execute(args)

            assertTrue(result.isErr, "Should fail when enc header is null")
            assertNotNull(result.error.message)
        }

    @Test
    fun testCreateJweJsonGeneral_UnsupportedContentEncAlg() =
        runTest {
            val mockExecution = mockk<SessionExecution>(relaxed = true)
            val mockKeyManagerService = mockk<KeyManagerService>()
            val mockIdentifierService = mockk<MultiManagedIdentifierService>()

            val command =
                CreateJweJsonGeneralCommandImpl(
                    execution = mockExecution,
                    keyManagerService = mockKeyManagerService,
                    identifierService = mockIdentifierService,
                )

            val header = JweHeader()
            header.alg = "RSA-OAEP"
            header.enc = "INVALID-ENC" // Unsupported

            val preparedJwe =
                PreparedJwe(
                    header = header,
                    plaintext = "test".encodeToByteArray(),
                    cek = CryptographyRandom.nextBytes(32),
                    recipient = mockk(),
                )

            val args = CreateJweJsonGeneralArgs(preparedJwe = preparedJwe)

            val result = command.execute(args)

            assertTrue(result.isErr, "Should fail when enc algorithm is unsupported")
            assertNotNull(result.error.message)
        }

    @Test
    fun testCreateJweJsonGeneral_IdentifierResolutionFails() =
        runTest {
            val mockExecution = mockk<SessionExecution>(relaxed = true)
            val mockKeyManagerService = mockk<KeyManagerService>()
            val mockIdentifierService = mockk<MultiManagedIdentifierService>()

            // Mock encrypt to succeed (called first before resolve)
            val mockEncryptionResult = mockk<com.sphereon.crypto.core.kms.EncryptionResult>()
            every { mockEncryptionResult.iv } returns ByteArray(12)
            every { mockEncryptionResult.authTag } returns ByteArray(16)
            every { mockEncryptionResult.ciphertext } returns ByteArray(32)
            coEvery { mockKeyManagerService.encrypt(any(), any(), any(), any()) } returns mockEncryptionResult

            // Mock identifier resolution to fail (called in wrapKeyForRecipient after encrypt)
            coEvery { mockIdentifierService.resolve(any<ManagedIdentifierOptsOrResult>()) } returns
                IdkResult.err(IdkError.fromString("Identifier resolution failed"))

            val command =
                CreateJweJsonGeneralCommandImpl(
                    execution = mockExecution,
                    keyManagerService = mockKeyManagerService,
                    identifierService = mockIdentifierService,
                )

            val header = JweHeader()
            header.alg = "RSA-OAEP"
            header.enc = "A256GCM"

            val mockRecipient = mockk<ManagedIdentifierOptsOrResult>()

            val preparedJwe =
                PreparedJwe(
                    header = header,
                    plaintext = "test".encodeToByteArray(),
                    cek = CryptographyRandom.nextBytes(32),
                    recipient = mockRecipient,
                )

            val args = CreateJweJsonGeneralArgs(preparedJwe = preparedJwe)

            val result = command.execute(args)

            assertTrue(result.isErr, "Should fail when identifier resolution fails")
            assertNotNull(result.error.message)
        }

    @Test
    fun testCreateJweJsonGeneral_UnsupportedKeyEncAlg() =
        runTest {
            val mockExecution = mockk<SessionExecution>(relaxed = true)
            val mockKeyManagerService = mockk<KeyManagerService>()
            val mockIdentifierService = mockk<MultiManagedIdentifierService>()

            // Mock encrypt to succeed (called first before resolve)
            val mockEncryptionResult = mockk<com.sphereon.crypto.core.kms.EncryptionResult>()
            every { mockEncryptionResult.iv } returns ByteArray(12)
            every { mockEncryptionResult.authTag } returns ByteArray(16)
            every { mockEncryptionResult.ciphertext } returns ByteArray(32)
            coEvery { mockKeyManagerService.encrypt(any(), any(), any(), any()) } returns mockEncryptionResult

            // Setup mock for successful identifier resolution using concrete types
            val rsaJwk = Jwk(kty = JwaKeyType.RSA, n = "test", e = "AQAB")
            val resolvedKeyInfo =
                ResolvedKeyInfo<JwkType>(
                    key = rsaJwk,
                    keyVisibility = KeyVisibility.PUBLIC,
                )
            val managedKeyInfo =
                ManagedKeyInfo<JwkType>(
                    alias = "test-key",
                    providerId = "test-provider",
                    resolvedKeyInfo = resolvedKeyInfo,
                )
            // Create a concrete result instance instead of mocking abstract class
            val resolvedRecipient =
                ManagedIdentifierJwkResult(
                    context = IdentifierContext(),
                    keyInfo = managedKeyInfo,
                    identifier = rsaJwk,
                )

            @Suppress("UNCHECKED_CAST")
            val mockResult =
                IdkResult.ok<ManagedIdentifierResult<KeyType>, IdkErrorType>(
                    resolvedRecipient as ManagedIdentifierResult<KeyType>,
                )
            coEvery { mockIdentifierService.resolve(any<ManagedIdentifierOptsOrResult>()) } returns mockResult

            val command =
                CreateJweJsonGeneralCommandImpl(
                    execution = mockExecution,
                    keyManagerService = mockKeyManagerService,
                    identifierService = mockIdentifierService,
                )

            val header = JweHeader()
            header.alg = "UNSUPPORTED-ALG" // Not RSA-OAEP, not ECDH-ES
            header.enc = "A256GCM"

            val mockRecipient = mockk<ManagedIdentifierOptsOrResult>()

            val preparedJwe =
                PreparedJwe(
                    header = header,
                    plaintext = "test".encodeToByteArray(),
                    cek = CryptographyRandom.nextBytes(32),
                    recipient = mockRecipient,
                )

            val args = CreateJweJsonGeneralArgs(preparedJwe = preparedJwe)

            val result = command.execute(args)

            assertTrue(result.isErr, "Should fail when key encryption algorithm is unsupported")
            assertNotNull(result.error.message)
        }

    @Test
    fun testCreateJweJsonGeneral_ECDHWithNonECKey() =
        runTest {
            val mockExecution = mockk<SessionExecution>(relaxed = true)
            val mockKeyManagerService = mockk<KeyManagerService>()
            val mockIdentifierService = mockk<MultiManagedIdentifierService>()

            // Mock encrypt to succeed (called first before resolve)
            val mockEncryptionResult = mockk<com.sphereon.crypto.core.kms.EncryptionResult>()
            every { mockEncryptionResult.iv } returns ByteArray(12)
            every { mockEncryptionResult.authTag } returns ByteArray(16)
            every { mockEncryptionResult.ciphertext } returns ByteArray(32)
            coEvery { mockKeyManagerService.encrypt(any(), any(), any(), any()) } returns mockEncryptionResult

            // Setup mock for successful identifier resolution with RSA key (not EC)
            val rsaJwk = Jwk(kty = JwaKeyType.RSA, n = "test", e = "AQAB") // RSA, not EC
            val resolvedKeyInfo =
                ResolvedKeyInfo<JwkType>(
                    key = rsaJwk,
                    keyVisibility = KeyVisibility.PUBLIC,
                )
            val managedKeyInfo =
                ManagedKeyInfo<JwkType>(
                    alias = "test-key",
                    providerId = "test-provider",
                    resolvedKeyInfo = resolvedKeyInfo,
                )
            // Create a concrete result instance instead of mocking abstract class
            val resolvedRecipient =
                ManagedIdentifierJwkResult(
                    context = IdentifierContext(),
                    keyInfo = managedKeyInfo,
                    identifier = rsaJwk,
                )

            @Suppress("UNCHECKED_CAST")
            val mockResult =
                IdkResult.ok<ManagedIdentifierResult<KeyType>, IdkErrorType>(
                    resolvedRecipient as ManagedIdentifierResult<KeyType>,
                )
            coEvery { mockIdentifierService.resolve(any<ManagedIdentifierOptsOrResult>()) } returns mockResult

            val command =
                CreateJweJsonGeneralCommandImpl(
                    execution = mockExecution,
                    keyManagerService = mockKeyManagerService,
                    identifierService = mockIdentifierService,
                )

            val header = JweHeader()
            header.alg = "ECDH-ES+A128KW" // ECDH requires EC key
            header.enc = "A256GCM"

            val mockRecipient = mockk<ManagedIdentifierOptsOrResult>()

            val preparedJwe =
                PreparedJwe(
                    header = header,
                    plaintext = "test".encodeToByteArray(),
                    cek = CryptographyRandom.nextBytes(32),
                    recipient = mockRecipient,
                )

            val args = CreateJweJsonGeneralArgs(preparedJwe = preparedJwe)

            val result = command.execute(args)

            assertTrue(result.isErr, "Should fail when key is not EC for ECDH-ES")
            assertNotNull(result.error.message)
        }

    // ========================================================================
    // CreateJweCompactCommandImpl Error Path Tests
    // Note: CreateJweCompactCommandImpl does NOT have identifierService
    // ========================================================================

    @Test
    fun testCreateJweCompact_NullPreparedJwe() =
        runTest {
            val mockExecution = mockk<SessionExecution>(relaxed = true)
            val mockKeyManagerService = mockk<KeyManagerService>()

            val command =
                CreateJweCompactCommandImpl(
                    execution = mockExecution,
                    keyManagerService = mockKeyManagerService,
                )

            val args =
                CreateJweCompactArgs(
                    preparedJwe = null,
                )

            val result = command.execute(args)

            assertTrue(result.isErr, "Should fail when preparedJwe is null")
            assertNotNull(result.error.message)
        }

    // ========================================================================
    // CreateJweJsonFlattenedCommandImpl Error Path Tests
    // Note: CreateJweJsonFlattenedCommandImpl does NOT have identifierService
    // ========================================================================

    @Test
    fun testCreateJweJsonFlattened_NullPreparedJwe() =
        runTest {
            val mockExecution = mockk<SessionExecution>(relaxed = true)
            val mockKeyManagerService = mockk<KeyManagerService>()

            val command =
                CreateJweJsonFlattenedCommandImpl(
                    execution = mockExecution,
                    keyManagerService = mockKeyManagerService,
                )

            val args =
                CreateJweJsonArgs(
                    preparedJwe = null,
                )

            val result = command.execute(args)

            assertTrue(result.isErr, "Should fail when preparedJwe is null")
            assertNotNull(result.error.message)
        }

    // ========================================================================
    // DecryptJweCommandImpl Error Path Tests
    // ========================================================================

    @Test
    fun testDecryptJwe_ProviderBackedEcdhUsesDeriveCommandWithoutResolvingPrivateKey() =
        runTest {
            val mockExecution = mockk<SessionExecution>(relaxed = true)
            val mockKeyManagerService = mockk<KeyManagerService>()
            val mockIdentifierService = mockk<MultiManagedIdentifierService>()
            val mockEcdhDeriveCommand = mockk<EcdhDeriveCommand>()
            val plaintext = "provider-routed-jwe".encodeToByteArray()

            coEvery { mockEcdhDeriveCommand.execute(any()) } returns
                IdkResult.ok(EcdhDeriveResult(derivedSecret = ByteArray(32) { 7 }))
            coEvery {
                mockKeyManagerService.decrypt(any(), any(), any(), any(), any(), any())
            } returns plaintext

            val command =
                DecryptJweCommandImpl(
                    execution = mockExecution,
                    identifierService = mockIdentifierService,
                    keyManagerService = mockKeyManagerService,
                    ecdhDeriveCommand = mockEcdhDeriveCommand,
                )
            val header =
                JweHeader().apply {
                    alg = "ECDH-ES"
                    enc = "A256GCM"
                    epk =
                        Jwk(
                            kty = JwaKeyType.EC,
                            crv = JwaCurve.P_256,
                            x = "WKn-ZIGevcwGFOMJ0GeEei2HiGCt9c1i9o6n3y8T7jc",
                            y = "y77t-RvAHRKTsSGdIYUfweuOvwrvDD-Q3Hv5J0fSKbE",
                        )
                }
            val decryptor =
                ManagedOptsKeyInfo(
                    identifier =
                        KeyInfo<Nothing>(
                            alias = "oidf-jarm-key",
                            providerId = "customer-provider",
                            keyVisibility = KeyVisibility.PRIVATE,
                        ),
                )

            val result =
                command.execute(
                    DecryptJweArgs(
                        jwe =
                            JweCompact(
                                header = header,
                                encryptedKey = ByteArray(0),
                                iv = ByteArray(12),
                                ciphertext = ByteArray(16),
                                authTag = ByteArray(16),
                            ),
                        decryptor = decryptor,
                    ),
                )

            assertTrue(result.isOk, "Provider-backed ECDH JWE should decrypt: ${result.errorOrNull()}")
            assertContentEquals(plaintext, result.value.plaintext)
            coVerify(exactly = 1) { mockEcdhDeriveCommand.execute(any()) }
            coVerify(exactly = 0) { mockIdentifierService.resolve(any<ManagedIdentifierOptsOrResult>()) }
        }

    @Test
    fun testDecryptJwe_NullJwe() =
        runTest {
            val mockExecution = mockk<SessionExecution>(relaxed = true)
            val mockKeyManagerService = mockk<KeyManagerService>()
            val mockIdentifierService = mockk<MultiManagedIdentifierService>()

            val command =
                DecryptJweCommandImpl(
                    execution = mockExecution,
                    identifierService = mockIdentifierService,
                    keyManagerService = mockKeyManagerService,
                    ecdhDeriveCommand = mockk(),
                )

            val args =
                DecryptJweArgs(
                    jwe = null,
                    decryptor = mockk(),
                )

            val result = command.execute(args)

            assertTrue(result.isErr, "Should fail when JWE is null")
            assertNotNull(result.error.message)
        }

    @Test
    fun testDecryptJwe_NullDecryptor() =
        runTest {
            val mockExecution = mockk<SessionExecution>(relaxed = true)
            val mockKeyManagerService = mockk<KeyManagerService>()
            val mockIdentifierService = mockk<MultiManagedIdentifierService>()

            val command =
                DecryptJweCommandImpl(
                    execution = mockExecution,
                    identifierService = mockIdentifierService,
                    keyManagerService = mockKeyManagerService,
                    ecdhDeriveCommand = mockk(),
                )

            // Create a properly constructed JweCompact
            val header = JweHeader()
            header.alg = "RSA-OAEP"
            header.enc = "A256GCM"

            val jweCompact =
                JweCompact(
                    header = header,
                    encryptedKey = ByteArray(256),
                    iv = ByteArray(12),
                    ciphertext = ByteArray(32),
                    authTag = ByteArray(16),
                )

            val args =
                DecryptJweArgs(
                    jwe = jweCompact,
                    decryptor = null,
                )

            val result = command.execute(args)

            assertTrue(result.isErr, "Should fail when decryptor is null")
            assertNotNull(result.error.message)
        }

    @Test
    fun testDecryptJwe_IdentifierResolutionFails() =
        runTest {
            val mockExecution = mockk<SessionExecution>(relaxed = true)
            val mockKeyManagerService = mockk<KeyManagerService>()
            val mockIdentifierService = mockk<MultiManagedIdentifierService>()

            // Mock identifier resolution to fail
            coEvery { mockIdentifierService.resolve(any<ManagedIdentifierOptsOrResult>()) } returns
                IdkResult.err(IdkError.fromString("Decryptor identifier resolution failed"))

            val command =
                DecryptJweCommandImpl(
                    execution = mockExecution,
                    identifierService = mockIdentifierService,
                    keyManagerService = mockKeyManagerService,
                    ecdhDeriveCommand = mockk(),
                )

            // Create a properly constructed JweCompact
            val header = JweHeader()
            header.alg = "RSA-OAEP"
            header.enc = "A256GCM"

            val jweCompact =
                JweCompact(
                    header = header,
                    encryptedKey = ByteArray(256),
                    iv = ByteArray(12),
                    ciphertext = ByteArray(32),
                    authTag = ByteArray(16),
                )

            val mockDecryptor = mockk<ManagedIdentifierOptsOrResult>()

            val args =
                DecryptJweArgs(
                    jwe = jweCompact,
                    decryptor = mockDecryptor,
                )

            val result = command.execute(args)

            assertTrue(result.isErr, "Should fail when decryptor identifier resolution fails")
            assertNotNull(result.error.message)
        }

    @Test
    fun testDecryptJwe_UnsupportedAlgorithm() =
        runTest {
            val mockExecution = mockk<SessionExecution>(relaxed = true)
            val mockKeyManagerService = mockk<KeyManagerService>()
            val mockIdentifierService = mockk<MultiManagedIdentifierService>()

            // Setup mock for successful identifier resolution using concrete types
            val rsaJwk = Jwk(kty = JwaKeyType.RSA, n = "test", e = "AQAB", d = "private")
            val resolvedKeyInfo =
                ResolvedKeyInfo<JwkType>(
                    key = rsaJwk,
                    keyVisibility = KeyVisibility.PRIVATE,
                )
            val managedKeyInfo =
                ManagedKeyInfo<JwkType>(
                    alias = "test-key",
                    providerId = "test-provider",
                    resolvedKeyInfo = resolvedKeyInfo,
                )
            // Create a concrete result instance instead of mocking abstract class
            val resolvedDecryptor =
                ManagedIdentifierJwkResult(
                    context = IdentifierContext(),
                    keyInfo = managedKeyInfo,
                    identifier = rsaJwk,
                )

            @Suppress("UNCHECKED_CAST")
            val mockResult =
                IdkResult.ok<ManagedIdentifierResult<KeyType>, IdkErrorType>(
                    resolvedDecryptor as ManagedIdentifierResult<KeyType>,
                )
            coEvery { mockIdentifierService.resolve(any<ManagedIdentifierOptsOrResult>()) } returns mockResult

            val command =
                DecryptJweCommandImpl(
                    execution = mockExecution,
                    identifierService = mockIdentifierService,
                    keyManagerService = mockKeyManagerService,
                    ecdhDeriveCommand = mockk(),
                )

            // Create a JweCompact with unsupported algorithm
            val header = JweHeader()
            header.alg = "UNSUPPORTED-ALG"
            header.enc = "A256GCM"

            val jweCompact =
                JweCompact(
                    header = header,
                    encryptedKey = ByteArray(256),
                    iv = ByteArray(12),
                    ciphertext = ByteArray(32),
                    authTag = ByteArray(16),
                )

            val mockDecryptor = mockk<ManagedIdentifierOptsOrResult>()

            val args =
                DecryptJweArgs(
                    jwe = jweCompact,
                    decryptor = mockDecryptor,
                )

            val result = command.execute(args)

            assertTrue(result.isErr, "Should fail when algorithm is unsupported")
            assertNotNull(result.error.message)
        }
}
