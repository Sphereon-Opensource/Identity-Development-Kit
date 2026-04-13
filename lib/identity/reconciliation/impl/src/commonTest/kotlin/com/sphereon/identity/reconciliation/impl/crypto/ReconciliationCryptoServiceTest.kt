/*
 * Copyright 2023-2026 Sphereon International B.V.
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

package com.sphereon.identity.reconciliation.impl.crypto

import com.sphereon.crypto.core.jose.JwaAlgorithm
import com.sphereon.crypto.core.kms.ContentEncryptionAlgorithm
import com.sphereon.identity.matching.crypto.EncryptedPayload
import com.sphereon.identity.matching.crypto.HashedIdentifier
import com.sphereon.identity.matching.crypto.ReconciliationCryptoService
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for ReconciliationCryptoService interface contract.
 *
 * Uses a deterministic in-memory implementation that mimics the HMAC and AES
 * behavior (domain-separated hashing, encrypt/decrypt round-trips, key versioning)
 * without requiring a live KMS.
 *
 * Live KMS-backed tests (KmsBackedReconciliationCryptoService with real crypto)
 * run in the jvmTest source set where the software KMS provider is available.
 */
@OptIn(ExperimentalEncodingApi::class)
class ReconciliationCryptoServiceTest {
    /**
     * Deterministic in-memory implementation of [ReconciliationCryptoService]
     * using simple domain-separated hashing and XOR-based encryption.
     * NOT a mock -- implements real deterministic behavior.
     */
    private class TestReconciliationCryptoService(
        private val holderKeyAlias: String = "holder-key",
        private val institutionKeyAlias: String = "institution-key",
        private val encryptionKey: String = "encryption-key",
        private val holderKeyVersion: String = "v1",
        private val institutionKeyVersion: String = "v1",
        private val encryptionKeyVersion: String = "v1",
        private val previousHolderKeyAlias: String? = null,
        private val previousInstitutionKeyAlias: String? = null,
        private val previousHolderKeyVersion: String? = null,
        private val previousInstitutionKeyVersion: String? = null,
    ) : ReconciliationCryptoService {
        /**
         * Deterministic hash: combines domain key alias with input to produce a
         * repeatable hash string. Uses a simple hash algorithm that provides
         * the same properties as HMAC (determinism, domain separation) without
         * requiring real crypto primitives.
         */
        private fun deterministicHash(
            input: String,
            keyAlias: String,
        ): String {
            // Simple deterministic hash: fold key+input bytes into a 32-byte hash
            val combined = "$keyAlias:$input"
            val bytes = combined.encodeToByteArray()
            val hash = ByteArray(32)
            for (i in bytes.indices) {
                hash[i % 32] = (hash[i % 32].toInt() xor bytes[i].toInt()).toByte()
            }
            return "f1220" + hash.joinToString("") { it.toUByte().toString(16).padStart(2, '0') }
        }

        override suspend fun hashHolderKey(holderKey: String): HashedIdentifier =
            HashedIdentifier(
                hash = deterministicHash(holderKey, holderKeyAlias),
                keyVersion = holderKeyVersion,
            )

        override suspend fun hashExternalIdentifier(identifier: String): HashedIdentifier =
            HashedIdentifier(
                hash = deterministicHash(identifier, institutionKeyAlias),
                keyVersion = institutionKeyVersion,
            )

        override suspend fun encrypt(plaintext: String): EncryptedPayload {
            // Simple reversible XOR encryption with the encryption key for testing
            val keyBytes = encryptionKey.encodeToByteArray()
            val plainBytes = plaintext.encodeToByteArray()
            val encrypted =
                ByteArray(plainBytes.size) { i ->
                    (plainBytes[i].toInt() xor keyBytes[i % keyBytes.size].toInt()).toByte()
                }
            return EncryptedPayload(
                ciphertext = Base64.UrlSafe.encode(encrypted),
                keyVersion = encryptionKeyVersion,
            )
        }

        override suspend fun decrypt(payload: EncryptedPayload): String {
            val encrypted = Base64.UrlSafe.decode(payload.ciphertext)
            val keyBytes = encryptionKey.encodeToByteArray()
            val decrypted =
                ByteArray(encrypted.size) { i ->
                    (encrypted[i].toInt() xor keyBytes[i % keyBytes.size].toInt()).toByte()
                }
            return decrypted.decodeToString()
        }

        override suspend fun hashHolderKeyWithPrevious(holderKey: String): HashedIdentifier? {
            val alias = previousHolderKeyAlias ?: return null
            val version = previousHolderKeyVersion ?: return null
            return HashedIdentifier(
                hash = deterministicHash(holderKey, alias),
                keyVersion = version,
            )
        }

        override suspend fun hashExternalIdentifierWithPrevious(identifier: String): HashedIdentifier? {
            val alias = previousInstitutionKeyAlias ?: return null
            val version = previousInstitutionKeyVersion ?: return null
            return HashedIdentifier(
                hash = deterministicHash(identifier, alias),
                keyVersion = version,
            )
        }
    }

    private val cryptoService = TestReconciliationCryptoService()

    @Test
    fun hmacDeterminism() =
        runTest {
            val input = "test-holder-key-12345"
            val hashes = (1..100).map { cryptoService.hashHolderKey(input) }
            assertTrue(
                hashes.all { it.hash == hashes.first().hash },
                "All 100 hashes should be identical for the same input",
            )
            assertTrue(hashes.all { it.keyVersion == "v1" })
            assertTrue(hashes.all { it.algorithm == JwaAlgorithm.HS256 })
        }

    @Test
    fun domainSeparation_holderKeyVsExternalIdentifier() =
        runTest {
            val sameInput = "same-input-value"
            val holderHash = cryptoService.hashHolderKey(sameInput)
            val externalHash = cryptoService.hashExternalIdentifier(sameInput)
            assertNotEquals(
                holderHash.hash,
                externalHash.hash,
                "hashHolderKey and hashExternalIdentifier must produce different hashes for the same input",
            )
        }

    @Test
    fun encryptDecryptRoundTrip() =
        runTest {
            val plaintext = """{"email":"user@example.com","name":"Test User"}"""
            val encrypted = cryptoService.encrypt(plaintext)
            val decrypted = cryptoService.decrypt(encrypted)
            assertEquals(plaintext, decrypted)
        }

    @Test
    fun encryptDecryptRoundTrip_emptyPayload() =
        runTest {
            val encrypted = cryptoService.encrypt("")
            val decrypted = cryptoService.decrypt(encrypted)
            assertEquals("", decrypted)
        }

    @Test
    fun encryptDecryptRoundTrip_largePayload() =
        runTest {
            val largePlaintext = "x".repeat(100_000)
            val encrypted = cryptoService.encrypt(largePlaintext)
            val decrypted = cryptoService.decrypt(encrypted)
            assertEquals(largePlaintext, decrypted)
        }

    @Test
    fun keyVersionTracking_hashedIdentifier() =
        runTest {
            val hashed = cryptoService.hashHolderKey("input")
            assertEquals("v1", hashed.keyVersion)

            val extHashed = cryptoService.hashExternalIdentifier("input")
            assertEquals("v1", extHashed.keyVersion)
        }

    @Test
    fun keyVersionTracking_encryptedPayload() =
        runTest {
            val encrypted = cryptoService.encrypt("payload")
            assertEquals("v1", encrypted.keyVersion)
            assertEquals(ContentEncryptionAlgorithm.A256GCM, encrypted.algorithm)
        }

    @Test
    fun previousKeyHash_rotationSupport() =
        runTest {
            val input = "test-holder-for-rotation"

            // Service without previous keys returns null
            val noPrevious = cryptoService.hashHolderKeyWithPrevious(input)
            assertNull(noPrevious)

            // Service with previous keys returns hash using old key
            val rotatedService =
                TestReconciliationCryptoService(
                    holderKeyAlias = "holder-key-v2",
                    holderKeyVersion = "v2",
                    previousHolderKeyAlias = "holder-key",
                    previousHolderKeyVersion = "v1",
                )

            val currentHash = rotatedService.hashHolderKey(input)
            val previousHash = rotatedService.hashHolderKeyWithPrevious(input)

            assertNotNull(previousHash, "Previous key hash should be non-null after rotation")
            assertEquals("v2", currentHash.keyVersion)
            assertEquals("v1", previousHash.keyVersion)

            // The original service's hash should match the rotated service's previous hash
            val originalHash = cryptoService.hashHolderKey(input)
            assertEquals(
                originalHash.hash,
                previousHash.hash,
                "Previous key hash should match what the old key produced",
            )
            assertNotEquals(
                currentHash.hash,
                previousHash.hash,
                "Current and previous key hashes must differ",
            )
        }

    @Test
    fun differentKeysProduceDifferentHashes() =
        runTest {
            val serviceA = TestReconciliationCryptoService(holderKeyAlias = "key-A")
            val serviceB = TestReconciliationCryptoService(holderKeyAlias = "key-B")
            val hashA = serviceA.hashHolderKey("input")
            val hashB = serviceB.hashHolderKey("input")
            assertNotEquals(
                hashA.hash,
                hashB.hash,
                "Same input with different keys must produce different hashes",
            )
        }

    @Test
    fun differentKeysProduceDifferentCiphertext() =
        runTest {
            val serviceA = TestReconciliationCryptoService(encryptionKey = "key-AAAAAAAAAA")
            val serviceB = TestReconciliationCryptoService(encryptionKey = "key-BBBBBBBBBB")
            val encA = serviceA.encrypt("payload")
            val encB = serviceB.encrypt("payload")
            assertNotEquals(
                encA.ciphertext,
                encB.ciphertext,
                "Same plaintext with different keys must produce different ciphertext",
            )
        }
}
