/*
 * Copyright 2025 Sphereon International B.V.
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

package com.sphereon.identity.matching.impl.crypto

import com.sphereon.crypto.core.kms.ContentEncryptionAlgorithm
import com.sphereon.identity.matching.crypto.EncryptedPayload
import com.sphereon.identity.matching.crypto.HashedIdentifier
import com.sphereon.identity.matching.crypto.ReconciliationCryptoService
import kotlinx.coroutines.test.runTest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for ReconciliationCryptoService contract using a pure-JVM implementation.
 *
 * This uses real HMAC-SHA256 and AES-256-GCM (via javax.crypto), not mocks.
 * Validates the behavioral contract that KmsBackedReconciliationCryptoService must satisfy:
 * - HMAC determinism
 * - Domain separation (different keys produce different hashes)
 * - Encrypt/decrypt round-trip
 * - Key version tracking
 * - Dual-read with previous key
 */
class ReconciliationCryptoServiceTest {

    /**
     * Pure-JVM implementation of ReconciliationCryptoService for testing.
     * Uses real javax.crypto HMAC-SHA256 and AES-256-GCM.
     */
    @OptIn(ExperimentalEncodingApi::class)
    private class JvmReconciliationCryptoService(
        private val holderKey: ByteArray,
        private val institutionKey: ByteArray,
        private val encryptionKey: ByteArray,
        private val holderKeyVersion: String = "A-v1",
        private val institutionKeyVersion: String = "B-v1",
        private val encryptionKeyVersion: String = "C-v1",
        private val previousHolderKey: ByteArray? = null,
        private val previousHolderKeyVersion: String? = null,
        private val previousInstitutionKey: ByteArray? = null,
        private val previousInstitutionKeyVersion: String? = null,
    ) : ReconciliationCryptoService {

        private val random = SecureRandom()

        private fun hmac(data: String, key: ByteArray): String {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(key, "HmacSHA256"))
            val hash = mac.doFinal(data.encodeToByteArray())
            // Encode as hex for readability (production uses multibase/multihash)
            return hash.joinToString("") { "%02x".format(it) }
        }

        override suspend fun hashHolderKey(holderKey: String): HashedIdentifier {
            return HashedIdentifier(
                hash = hmac(holderKey, this.holderKey),
                keyVersion = holderKeyVersion,
            )
        }

        override suspend fun hashExternalIdentifier(identifier: String): HashedIdentifier {
            return HashedIdentifier(
                hash = hmac(identifier, institutionKey),
                keyVersion = institutionKeyVersion,
            )
        }

        override suspend fun encrypt(plaintext: String): EncryptedPayload {
            val iv = ByteArray(12).also { random.nextBytes(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(encryptionKey, "AES"), GCMParameterSpec(128, iv))
            val ciphertext = cipher.doFinal(plaintext.encodeToByteArray())
            // GCM appends auth tag to ciphertext; split them
            val authTag = ciphertext.copyOfRange(ciphertext.size - 16, ciphertext.size)
            val ciphertextOnly = ciphertext.copyOfRange(0, ciphertext.size - 16)
            val combined = iv + authTag + ciphertextOnly
            return EncryptedPayload(
                ciphertext = Base64.UrlSafe.encode(combined),
                keyVersion = encryptionKeyVersion,
                algorithm = ContentEncryptionAlgorithm.A256GCM,
            )
        }

        override suspend fun decrypt(payload: EncryptedPayload): String {
            val combined = Base64.UrlSafe.decode(payload.ciphertext)
            require(combined.size >= 28) { "Invalid encrypted payload" }
            val iv = combined.copyOfRange(0, 12)
            val authTag = combined.copyOfRange(12, 28)
            val ciphertextOnly = combined.copyOfRange(28, combined.size)
            // Reconstruct GCM ciphertext (ciphertext + authTag)
            val gcmCiphertext = ciphertextOnly + authTag
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(encryptionKey, "AES"), GCMParameterSpec(128, iv))
            val plaintext = cipher.doFinal(gcmCiphertext)
            return plaintext.decodeToString()
        }

        override suspend fun hashHolderKeyWithPrevious(holderKey: String): HashedIdentifier? {
            val key = previousHolderKey ?: return null
            val version = previousHolderKeyVersion ?: return null
            return HashedIdentifier(
                hash = hmac(holderKey, key),
                keyVersion = version,
            )
        }

        override suspend fun hashExternalIdentifierWithPrevious(identifier: String): HashedIdentifier? {
            val key = previousInstitutionKey ?: return null
            val version = previousInstitutionKeyVersion ?: return null
            return HashedIdentifier(
                hash = hmac(identifier, key),
                keyVersion = version,
            )
        }
    }

    private fun generateKey(size: Int = 32): ByteArray = ByteArray(size).also { SecureRandom().nextBytes(it) }

    private fun createService(
        holderKey: ByteArray = generateKey(),
        institutionKey: ByteArray = generateKey(),
        encryptionKey: ByteArray = generateKey(),
        holderKeyVersion: String = "A-v1",
        institutionKeyVersion: String = "B-v1",
        encryptionKeyVersion: String = "C-v1",
        previousHolderKey: ByteArray? = null,
        previousHolderKeyVersion: String? = null,
        previousInstitutionKey: ByteArray? = null,
        previousInstitutionKeyVersion: String? = null,
    ): ReconciliationCryptoService = JvmReconciliationCryptoService(
        holderKey = holderKey,
        institutionKey = institutionKey,
        encryptionKey = encryptionKey,
        holderKeyVersion = holderKeyVersion,
        institutionKeyVersion = institutionKeyVersion,
        encryptionKeyVersion = encryptionKeyVersion,
        previousHolderKey = previousHolderKey,
        previousHolderKeyVersion = previousHolderKeyVersion,
        previousInstitutionKey = previousInstitutionKey,
        previousInstitutionKeyVersion = previousInstitutionKeyVersion,
    )

    // ---- Test 4a: HMAC determinism ----

    @Test
    fun hmacDeterminism_sameInputSameKey_sameHash() = runTest {
        val service = createService()
        val hash1 = service.hashHolderKey("holder-jwk-123")
        val hash2 = service.hashHolderKey("holder-jwk-123")
        assertEquals(hash1.hash, hash2.hash, "HMAC must be deterministic: same input + same key = same hash")
        assertEquals(hash1.keyVersion, hash2.keyVersion)
    }

    @Test
    fun hmacDeterminism_externalIdentifier() = runTest {
        val service = createService()
        val hash1 = service.hashExternalIdentifier("eduid:12345")
        val hash2 = service.hashExternalIdentifier("eduid:12345")
        assertEquals(hash1.hash, hash2.hash, "External identifier HMAC must be deterministic")
    }

    @Test
    fun hmacDifferentInputs_differentHashes() = runTest {
        val service = createService()
        val hash1 = service.hashHolderKey("holder-a")
        val hash2 = service.hashHolderKey("holder-b")
        assertNotEquals(hash1.hash, hash2.hash, "Different inputs must produce different hashes")
    }

    // ---- Test 4b: Domain separation ----

    @Test
    fun domainSeparation_differentKeysProduceDifferentHashes() = runTest {
        val service = createService()
        val holderHash = service.hashHolderKey("same-input")
        val institutionHash = service.hashExternalIdentifier("same-input")

        assertNotEquals(
            holderHash.hash, institutionHash.hash,
            "Domain separation: same input with different keys (holder vs institution) must produce different hashes"
        )
    }

    @Test
    fun domainSeparation_keyVersionsAreDifferent() = runTest {
        val service = createService()
        val holderHash = service.hashHolderKey("input")
        val institutionHash = service.hashExternalIdentifier("input")

        assertNotEquals(holderHash.keyVersion, institutionHash.keyVersion,
            "Holder and institution key versions should be distinct")
    }

    // ---- Test 4c: Encrypt/decrypt round-trip ----

    @Test
    fun encryptDecryptRoundTrip() = runTest {
        val service = createService()
        val plaintext = "sensitive-institution-id-12345"
        val encrypted = service.encrypt(plaintext)
        val decrypted = service.decrypt(encrypted)
        assertEquals(plaintext, decrypted, "Decrypt(Encrypt(plaintext)) must equal plaintext")
    }

    @Test
    fun encryptDecryptRoundTrip_emptyString() = runTest {
        val service = createService()
        val encrypted = service.encrypt("")
        val decrypted = service.decrypt(encrypted)
        assertEquals("", decrypted, "Round-trip must work for empty string")
    }

    @Test
    fun encryptDecryptRoundTrip_unicodeContent() = runTest {
        val service = createService()
        val plaintext = "student-name: Jan de Vries \u00e9\u00e8\u00ea\u00eb"
        val encrypted = service.encrypt(plaintext)
        val decrypted = service.decrypt(encrypted)
        assertEquals(plaintext, decrypted, "Round-trip must preserve unicode content")
    }

    @Test
    fun encryptProducesDifferentCiphertextsForSameInput() = runTest {
        val service = createService()
        val encrypted1 = service.encrypt("same-plaintext")
        val encrypted2 = service.encrypt("same-plaintext")
        assertNotEquals(
            encrypted1.ciphertext, encrypted2.ciphertext,
            "AES-GCM with random IV should produce different ciphertext each time"
        )
    }

    // ---- Test 4d: Key version tracking ----

    @Test
    fun hashHolderKeyReturnsNonEmptyKeyVersion() = runTest {
        val service = createService(holderKeyVersion = "A-v1")
        val result = service.hashHolderKey("holder-key")
        assertTrue(result.keyVersion.isNotEmpty(), "keyVersion must be non-empty")
        assertEquals("A-v1", result.keyVersion)
    }

    @Test
    fun hashExternalIdentifierReturnsNonEmptyKeyVersion() = runTest {
        val service = createService(institutionKeyVersion = "B-v1")
        val result = service.hashExternalIdentifier("external-id")
        assertTrue(result.keyVersion.isNotEmpty(), "keyVersion must be non-empty")
        assertEquals("B-v1", result.keyVersion)
    }

    @Test
    fun encryptReturnsNonEmptyKeyVersion() = runTest {
        val service = createService(encryptionKeyVersion = "C-v1")
        val result = service.encrypt("plaintext")
        assertTrue(result.keyVersion.isNotEmpty(), "keyVersion must be non-empty")
        assertEquals("C-v1", result.keyVersion)
    }

    // ---- Test 2 (partial): Dual-read with previous key ----

    @Test
    fun hashHolderKeyWithPrevious_returnsHashWithOldKey() = runTest {
        val oldKey = generateKey()
        val newKey = generateKey()
        val service = createService(
            holderKey = newKey,
            holderKeyVersion = "A-v2",
            previousHolderKey = oldKey,
            previousHolderKeyVersion = "A-v1",
        )

        val currentHash = service.hashHolderKey("holder-jwk-123")
        val previousHash = service.hashHolderKeyWithPrevious("holder-jwk-123")

        assertNotNull(previousHash, "Previous hash should be non-null when previous key is configured")
        assertNotEquals(currentHash.hash, previousHash.hash,
            "Current and previous hashes must differ (different keys)")
        assertEquals("A-v2", currentHash.keyVersion)
        assertEquals("A-v1", previousHash.keyVersion)
    }

    @Test
    fun hashHolderKeyWithPrevious_returnsNullWhenNoPreviousKey() = runTest {
        val service = createService()
        val result = service.hashHolderKeyWithPrevious("holder-jwk-123")
        assertNull(result, "Should return null when no previous key is configured")
    }

    @Test
    fun hashExternalIdentifierWithPrevious_returnsHashWithOldKey() = runTest {
        val oldKey = generateKey()
        val newKey = generateKey()
        val service = createService(
            institutionKey = newKey,
            institutionKeyVersion = "B-v2",
            previousInstitutionKey = oldKey,
            previousInstitutionKeyVersion = "B-v1",
        )

        val currentHash = service.hashExternalIdentifier("eduid:123")
        val previousHash = service.hashExternalIdentifierWithPrevious("eduid:123")

        assertNotNull(previousHash)
        assertNotEquals(currentHash.hash, previousHash.hash)
        assertEquals("B-v2", currentHash.keyVersion)
        assertEquals("B-v1", previousHash.keyVersion)
    }

    @Test
    fun dualReadScenario_lookupByPreviousHashSucceeds() = runTest {
        val oldKey = generateKey()
        val newKey = generateKey()

        // Phase 1: Service with old key stores a hash
        val serviceV1 = createService(holderKey = oldKey, holderKeyVersion = "A-v1")
        val storedHash = serviceV1.hashHolderKey("holder-jwk-123")

        // Phase 2: After rotation, service has new key but knows old key
        val serviceV2 = createService(
            holderKey = newKey,
            holderKeyVersion = "A-v2",
            previousHolderKey = oldKey,
            previousHolderKeyVersion = "A-v1",
        )

        // Current key produces different hash
        val currentHash = serviceV2.hashHolderKey("holder-jwk-123")
        assertNotEquals(storedHash.hash, currentHash.hash,
            "New key should produce different hash than stored hash")

        // Previous key produces the same hash as what's stored
        val previousHash = serviceV2.hashHolderKeyWithPrevious("holder-jwk-123")
        assertNotNull(previousHash)
        assertEquals(storedHash.hash, previousHash.hash,
            "Previous key hash must match the originally stored hash (dual-read fallback)")
    }
}
