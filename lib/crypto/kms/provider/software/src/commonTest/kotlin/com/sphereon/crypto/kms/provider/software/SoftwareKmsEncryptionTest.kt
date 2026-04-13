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
 */

package com.sphereon.crypto.kms.provider.software

import com.sphereon.core.api.Encoding
import com.sphereon.core.api.encodeTo
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.ResolvedKeyInfo
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.KeyTypeMapping
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.jose.JwaAlgorithm
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.kms.ContentEncryptionAlgorithm
import com.sphereon.crypto.kms.provider.software.testutil.SoftwareKmsTestContext
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.random.CryptographyRandom
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests that mimic the reconciliation encryption flow:
 * 1. Store a symmetric AES-256 key by alias (like AuthBridgeKeyInitializer does)
 * 2. Encrypt using only KeyInfo(alias=..., providerId=...) — no key material
 * 3. Decrypt using only KeyInfo(alias=..., providerId=...) — no key material
 *
 * This is the exact pattern used by KmsBackedReconciliationCryptoService.
 */
class SoftwareKmsEncryptionTest {
    private lateinit var provider: SoftwareKmsProvider

    val ctx = SoftwareKmsTestContext("encryption-test", this)

    @BeforeTest
    fun setUp() {
        val config = SoftwareKmsProviderConfig(
            id = "software",
            cryptographyProvider = CryptographyProvider.Default.name,
            persistKeysDuringGeneration = true,
            exposePrivateKeysDuringGeneration = true,
        )
        provider = ctx.softwareKmsProviderFactory.create(config, ctx.session.sessionExecution)
    }

    private suspend fun storeSymmetricKey(alias: String, alg: JwaAlgorithm, keySize: Int = 32) {
        val rawKeyBytes = CryptographyRandom.nextBytes(keySize)
        val kBase64Url = rawKeyBytes.encodeTo(Encoding.BASE64URL)
        val jwk = Jwk(
            kty = JwaKeyType.oct,
            k = kBase64Url,
            alg = alg,
            kid = alias,
        )
        val resolvedKeyInfo = ResolvedKeyInfo(
            kid = alias,
            alias = alias,
            key = jwk,
            providerId = "software",
            keyVisibility = KeyVisibility.PRIVATE,
            keyType = KeyTypeMapping.Symmetric,
        )
        provider.storeKey(resolvedKeyInfo, "software", alias, null)
    }

    /**
     * Mimics AuthBridgeKeyInitializer.registerSymmetricKeyIfAbsent():
     * generates 32 random bytes, base64url-encodes them into a JWK with kty=oct,
     * stores via provider.storeKey, then encrypts/decrypts using only the alias.
     */
    @Test
    fun testEncryptDecryptByAliasOnly() = runTest {
        val alias = "reconciliation:encryption"
        storeSymmetricKey(alias, JwaAlgorithm.A256GCMKW)

        // Encrypt using only alias — no key material (this is what KmsBackedReconciliationCryptoService does)
        val plaintext = """{"holder_key":"did:jwk:abc","institution_id":"surf"}"""
        val encryptKeyInfo = KeyInfo<Nothing>(alias = alias, providerId = "software")

        val encryptionResult = provider.encrypt(
            keyInfo = encryptKeyInfo,
            plaintext = plaintext.encodeToByteArray(),
            algorithm = ContentEncryptionAlgorithm.A256GCM,
        )

        assertNotNull(encryptionResult.ciphertext)
        assertNotNull(encryptionResult.iv)
        assertNotNull(encryptionResult.authTag)
        assertEquals(12, encryptionResult.iv.size, "AES-GCM IV should be 12 bytes")
        assertEquals(16, encryptionResult.authTag.size, "AES-GCM auth tag should be 16 bytes")

        // Decrypt using only alias — no key material
        val decryptKeyInfo = KeyInfo<Nothing>(alias = alias, providerId = "software")

        val decrypted = provider.decrypt(
            keyInfo = decryptKeyInfo,
            ciphertext = encryptionResult.ciphertext,
            algorithm = ContentEncryptionAlgorithm.A256GCM,
            iv = encryptionResult.iv,
            authTag = encryptionResult.authTag,
        )

        assertEquals(plaintext, decrypted.decodeToString())
    }

    /**
     * Tests the full round-trip with combined IV+authTag+ciphertext format,
     * exactly as KmsBackedReconciliationCryptoService.encrypt()/decrypt() does.
     */
    @Test
    fun testEncryptDecryptCombinedFormat() = runTest {
        val alias = "reconciliation:encryption-combined"
        storeSymmetricKey(alias, JwaAlgorithm.A256GCMKW)

        val plaintext = "test reconciliation payload"
        val keyInfo = KeyInfo<Nothing>(alias = alias, providerId = "software")

        // Encrypt
        val result = provider.encrypt(
            keyInfo = keyInfo,
            plaintext = plaintext.encodeToByteArray(),
            algorithm = ContentEncryptionAlgorithm.A256GCM,
        )

        // Combine IV (12) + authTag (16) + ciphertext — same format as KmsBackedReconciliationCryptoService
        val combined = result.iv + result.authTag + result.ciphertext

        // Split back
        assertTrue(combined.size > 28, "Combined payload must be > 28 bytes")
        val iv = combined.copyOfRange(0, 12)
        val authTag = combined.copyOfRange(12, 28)
        val ciphertext = combined.copyOfRange(28, combined.size)

        // Decrypt
        val decrypted = provider.decrypt(
            keyInfo = keyInfo,
            ciphertext = ciphertext,
            algorithm = ContentEncryptionAlgorithm.A256GCM,
            iv = iv,
            authTag = authTag,
        )

        assertEquals(plaintext, decrypted.decodeToString())
    }

    /**
     * Tests HMAC + encrypt together — the full reconciliation crypto flow.
     */
    @Test
    fun testFullReconciliationCryptoFlow() = runTest {
        val holderAlias = "reconciliation:holder"
        val institutionAlias = "reconciliation:institution"
        val encryptionAlias = "reconciliation:encryption"

        // Store HMAC keys
        storeSymmetricKey(holderAlias, JwaAlgorithm.HS256)
        storeSymmetricKey(institutionAlias, JwaAlgorithm.HS256)
        // Store encryption key
        storeSymmetricKey(encryptionAlias, JwaAlgorithm.A256GCMKW)

        // HMAC operations (use kid, same as GenerateMacCommand)
        val holderMac = provider.generateMac(
            keyId = holderAlias,
            message = "did:jwk:abc123".encodeToByteArray(),
            digestAlgorithm = DigestAlg.SHA256,
        )
        assertNotNull(holderMac)
        assertTrue(holderMac.isNotEmpty())

        val institutionMac = provider.generateMac(
            keyId = institutionAlias,
            message = "surf-sub-id".encodeToByteArray(),
            digestAlgorithm = DigestAlg.SHA256,
        )
        assertNotNull(institutionMac)
        assertTrue(institutionMac.isNotEmpty())

        // Encryption by alias only — no key material
        val payload = """{"holder_key":"did:jwk:abc123","claims":{"given_name":"John"}}"""
        val encResult = provider.encrypt(
            keyInfo = KeyInfo<Nothing>(alias = encryptionAlias, providerId = "software"),
            plaintext = payload.encodeToByteArray(),
            algorithm = ContentEncryptionAlgorithm.A256GCM,
        )

        // Decrypt by alias only
        val decrypted = provider.decrypt(
            keyInfo = KeyInfo<Nothing>(alias = encryptionAlias, providerId = "software"),
            ciphertext = encResult.ciphertext,
            algorithm = ContentEncryptionAlgorithm.A256GCM,
            iv = encResult.iv,
            authTag = encResult.authTag,
        )

        assertEquals(payload, decrypted.decodeToString())
    }
}
