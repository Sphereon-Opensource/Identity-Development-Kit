/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.crypto.core.security

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the [AesGcmEncryptionService] contract: round-trip encrypt+decrypt recovers the
 * plaintext, AAD binding is enforced, an unknown key id surfaces as an error, and the
 * EncryptedBlob carries the active key id so a downstream rotation can route to the
 * historical key by id.
 */
class AesGcmEncryptionServiceTest {
    /** Fixed 32-byte key so tests are deterministic. */
    private val testKey =
        EncryptionKeyMaterial(
            keyId = "test-key-1",
            keyBytes = ByteArray(32) { it.toByte() },
            algorithm = EncryptedBlob.ALG_AES_256_GCM,
        )

    private fun service(provider: EncryptionKeyProvider): AesGcmEncryptionService = AesGcmEncryptionService(provider)

    private fun fixedKeyProvider(key: EncryptionKeyMaterial = testKey): EncryptionKeyProvider =
        object : EncryptionKeyProvider {
            override suspend fun activeKey(): EncryptionKeyMaterial = key

            override suspend fun findKey(keyId: String): EncryptionKeyMaterial? = if (keyId == key.keyId) key else null
        }

    @Test
    fun encryptThenDecryptRecoversPlaintext() =
        runTest {
            val svc = service(fixedKeyProvider())
            val plaintext = "hello, encrypted world".encodeToByteArray()

            val blobR = svc.encrypt(plaintext)
            assertTrue(blobR.isOk, "encrypt must succeed")
            val blob = blobR.value

            val recovered = svc.decrypt(blob)
            assertTrue(recovered.isOk, "decrypt must succeed")
            assertContentEquals(plaintext, recovered.value)
        }

    @Test
    fun encryptedBlobCarriesActiveKeyIdAndAlgorithm() =
        runTest {
            val svc = service(fixedKeyProvider())
            val blob = svc.encrypt("anything".encodeToByteArray()).value
            assertEquals("test-key-1", blob.keyId, "blob must carry the active key id")
            assertEquals(EncryptedBlob.ALG_AES_256_GCM, blob.algorithm)
            assertEquals(12, blob.iv.size, "AES-GCM iv must be 12 bytes")
            assertEquals(16, blob.authTag.size, "AES-GCM auth tag must be 16 bytes")
        }

    @Test
    fun decryptingWithDifferentAadFails() =
        runTest {
            val svc = service(fixedKeyProvider())
            val blob = svc.encrypt("payload".encodeToByteArray(), associatedData = "tenant-A".encodeToByteArray()).value
            // Replay against a different AAD (e.g. attempting to lift the ciphertext into
            // tenant-B). AES-GCM auth-tag check fails → decrypt returns Err.
            val replay = svc.decrypt(blob, associatedData = "tenant-B".encodeToByteArray())
            assertTrue(replay.isErr, "decrypt with different AAD must fail (cross-tenant lift defense)")
        }

    @Test
    fun decryptingWithUnknownKeyIdFails() =
        runTest {
            val svc = service(fixedKeyProvider())
            val blob = svc.encrypt("payload".encodeToByteArray()).value
            val tampered = blob.copy(keyId = "rotated-out-key-99")
            val result = svc.decrypt(tampered)
            assertTrue(result.isErr, "decrypt must fail when key id is unknown to the provider")
        }

    @Test
    fun ciphertextDiffersAcrossEncryptsForSamePlaintext() =
        runTest {
            // AES-GCM with fresh iv per encrypt → distinct ciphertexts even for the same
            // plaintext. Catches a regression where iv generation might be deterministic
            // (which would be catastrophic for AES-GCM nonce-misuse).
            val svc = service(fixedKeyProvider())
            val pt = "same".encodeToByteArray()
            val a = svc.encrypt(pt).value
            val b = svc.encrypt(pt).value
            assertTrue(!a.iv.contentEquals(b.iv), "iv must differ across encrypts of the same plaintext")
            assertTrue(!a.ciphertext.contentEquals(b.ciphertext), "ciphertext must differ across encrypts of the same plaintext")
        }

    @Test
    fun commandsAndFacadeProduceIdenticalResults() =
        runTest {
            // Routing parity: invoking via the command surface must return the same blob
            // shape as calling the facade directly. A regression in either path would let
            // a deployment that audited one and not the other miss events.
            val svc = service(fixedKeyProvider())
            val pt = "hello".encodeToByteArray()
            val viaFacade = svc.encrypt(pt).value
            val viaCommand =
                svc.commands.encrypt
                    .execute(
                        com.sphereon.crypto.core.security
                            .EncryptArgs(plaintext = pt),
                    ).value
            assertEquals(viaFacade.algorithm, viaCommand.algorithm)
            assertEquals(viaFacade.keyId, viaCommand.keyId)
            // ivs differ (each path generates its own); only the SHAPE matches.
            assertEquals(viaFacade.iv.size, viaCommand.iv.size)
            assertEquals(viaFacade.authTag.size, viaCommand.authTag.size)
        }

    @Test
    fun rotatesByLookingUpHistoricalKeyForOlderBlob() =
        runTest {
            // Rotation scenario: a row was encrypted under key k1, then the deployment
            // rotated the active key to k2. Decrypting an old blob must look up k1 by id
            // (not assume the active key) — proves the EncryptedBlob.keyId path works.
            val k1 = EncryptionKeyMaterial(keyId = "k1", keyBytes = ByteArray(32) { 1 })
            val k2 = EncryptionKeyMaterial(keyId = "k2", keyBytes = ByteArray(32) { 2 })
            // Stage 1: encrypt under k1.
            val svc1 =
                service(
                    object : EncryptionKeyProvider {
                        override suspend fun activeKey() = k1

                        override suspend fun findKey(keyId: String) = if (keyId == "k1") k1 else null
                    }
                )
            val blob = svc1.encrypt("legacy".encodeToByteArray()).value
            // Stage 2: rotate. New service has k2 active but still knows about k1 for decrypt.
            val svcRotated =
                service(
                    object : EncryptionKeyProvider {
                        override suspend fun activeKey() = k2

                        override suspend fun findKey(keyId: String) =
                            when (keyId) {
                                "k1" -> k1
                                "k2" -> k2
                                else -> null
                            }
                    }
                )
            val recovered = svcRotated.decrypt(blob)
            assertTrue(recovered.isOk, "decrypt of legacy-key blob must succeed via key-id lookup")
            assertContentEquals("legacy".encodeToByteArray(), recovered.value)
            // And new encrypts use k2.
            val freshBlob = svcRotated.encrypt("fresh".encodeToByteArray()).value
            assertEquals("k2", freshBlob.keyId, "fresh encrypt must use the active (rotated) key")
        }
}
