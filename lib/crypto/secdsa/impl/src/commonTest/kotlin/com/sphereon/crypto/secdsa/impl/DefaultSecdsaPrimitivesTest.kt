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

package com.sphereon.crypto.secdsa.impl

import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.generic.hash
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.kms.KmsProvider
import com.sphereon.crypto.secdsa.SecdsaDigestSigner
import com.sphereon.crypto.secdsa.SecdsaEqDlogPair
import com.sphereon.crypto.secdsa.SecdsaEqDlogProof
import com.sphereon.crypto.secdsa.SecdsaInternalCertificateMaterial
import com.sphereon.crypto.secdsa.SecdsaPoint
import com.sphereon.crypto.secdsa.SecdsaPointFormat
import com.sphereon.crypto.secdsa.SecdsaRawEcdsaSignature
import com.sphereon.crypto.secdsa.SecdsaScalar
import com.sphereon.crypto.secdsa.SecdsaTrustedChallengeResponse
import com.sphereon.crypto.secdsa.impl.testutil.SecdsaTestContext
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DefaultSecdsaPrimitivesTest {
    private val primitives = DefaultSecdsaPrimitives()

    @Test
    fun testP256PointEncodingAndValidation() {
        val generator = primitives.generator()

        assertTrue(primitives.validatePoint(generator))
        assertEquals(generator, primitives.decodePoint(primitives.encodePoint(generator, SecdsaPointFormat.UNCOMPRESSED)))
        assertEquals(generator, primitives.decodePoint(primitives.encodePoint(generator, SecdsaPointFormat.COMPRESSED)))
        assertFalse(primitives.validatePoint(SecdsaPoint(ByteArray(32), ByteArray(32))))
        assertFailsWith<IllegalArgumentException> {
            primitives.decodePoint(byteArrayOf(0x05))
        }
    }

    @Test
    fun testScalarInverseAndPointAlgebra() {
        val seven = scalar(7)
        val inverse = primitives.inverse(seven)
        val product = primitives.multiplyScalars(seven, inverse)

        assertContentEquals(scalar(1).value, product.value)

        val generator = primitives.generator()
        val triple = primitives.multiply(scalar(3), generator)
        val five = primitives.multiply(scalar(5), generator)
        val eight = primitives.multiply(scalar(8), generator)

        assertEquals(eight, primitives.add(triple, five))
        assertEquals(five, primitives.subtract(eight, triple))
    }

    @Test
    fun testRawEcdsaSignatureVerifiesAndCanBeLiftedToFullSignature() {
        val privateKey = scalar(13)
        val nonce = scalar(17)
        val publicKey = primitives.multiply(privateKey)
        val digest = hash("secdsa full ecdsa".encodeToByteArray(), DigestAlg.SHA256)
        val signature = deterministicEcdsaSignature(digest, privateKey, nonce)

        assertTrue(primitives.verifyDigestSignature(digest, publicKey, signature))
        assertFalse(primitives.verifyDigestSignature(hash("tampered".encodeToByteArray(), DigestAlg.SHA256), publicKey, signature))
        assertFalse(primitives.verifyDigestSignature(byteArrayOf(0x01, 0x02), publicKey, signature))
        assertFailsWith<IllegalArgumentException> {
            primitives.toFullEcdsaSignature(byteArrayOf(0x01, 0x02), publicKey, signature)
        }

        val fullSignature = primitives.toFullEcdsaSignature(digest, publicKey, signature)
        assertEquals(primitives.multiply(nonce), fullSignature.ephemeralPoint)
        assertContentEquals(signature.s, fullSignature.s.value)
    }

    @Test
    fun testEqDlogProofVerificationAndTamperRejection() {
        val privateScalar = scalar(7)
        val nonce = scalar(11)
        val generatorA = primitives.generator()
        val generatorB = primitives.multiply(scalar(3), generatorA)
        val publicA = primitives.multiply(privateScalar, generatorA)
        val publicB = primitives.multiply(privateScalar, generatorB)
        val pairs =
            listOf(
                SecdsaEqDlogPair(generatorA, publicA),
                SecdsaEqDlogPair(generatorB, publicB),
            )
        val aad = "eq-dlog-aad".encodeToByteArray()

        val proof =
            primitives.proveEqualDiscreteLog(
                privateScalar = privateScalar,
                pairs = pairs,
                aad = aad,
                nonceScalar = nonce,
            )

        assertTrue(primitives.verifyEqualDiscreteLog(pairs, proof, aad))
        assertFalse(primitives.verifyEqualDiscreteLog(pairs, proof, "wrong-aad".encodeToByteArray()))
        assertFalse(
            primitives.verifyEqualDiscreteLog(
                pairs,
                SecdsaEqDlogProof(tamper(proof.challenge), proof.response),
                aad,
            ),
        )
        assertFalse(primitives.verifyEqualDiscreteLog(pairs, SecdsaEqDlogProof(ByteArray(32), proof.response), aad))

        val possessionPair = listOf(SecdsaEqDlogPair(generatorA, publicA))
        val possessionProof = primitives.proveEqualDiscreteLog(privateScalar, possessionPair, scalar(13), aad)
        assertTrue(primitives.verifyEqualDiscreteLog(possessionPair, possessionProof, aad))
    }

    @Test
    fun testTrustedChallengeRoundTripAndResponseTamperRejection() =
        runTest {
            val kmsProvider = SecdsaTestContext("secdsa-trusted-challenge", this@DefaultSecdsaPrimitivesTest).softwareKmsProvider
            val commonKey = generateKmsP256Key(kmsProvider, "trusted-challenge-common")
            val signerPrivateScalar = scalar(13)
            val signerPublicKey = primitives.multiply(signerPrivateScalar)
            val generator = primitives.generator()
            val generators = listOf(generator, primitives.multiply(scalar(3), generator))
            val publicKeys = listOf(commonKey.publicPoint, primitives.multiply(scalar(3), commonKey.publicPoint))
            val pairs = generators.zip(publicKeys).map { (generatorPoint, publicKey) -> SecdsaEqDlogPair(generatorPoint, publicKey) }
            val aad = "trusted-challenge-aad".encodeToByteArray()
            val additionalSecretData = "issuer-session-state".encodeToByteArray()
            val seed = ByteArray(32) { (it + 1).toByte() }
            val iv = ByteArray(12) { (0xA0 + it).toByte() }
            val signer =
                object : SecdsaDigestSigner {
                    override suspend fun signDigest(digest: ByteArray): SecdsaRawEcdsaSignature =
                        deterministicEcdsaSignature(digest, signerPrivateScalar, scalar(19))
                }

            val bundle =
                primitives.createTrustedChallenge(
                    pairs = pairs,
                    aad = aad,
                    additionalSecretData = additionalSecretData,
                    signer = signer,
                    seed = seed,
                    iv = iv,
                )

            val response =
                primitives.respondToTrustedChallenge(
                    bundle = bundle,
                    pairs = pairs,
                    commonPrivateKeyInfo = commonKey.privateKeyInfo,
                    kmsProvider = kmsProvider,
                    challengeSignerPublicKey = signerPublicKey,
                )

            assertContentEquals(additionalSecretData, response.additionalSecretData)
            assertTrue(primitives.verifyTrustedChallengeResponse(bundle.challenge, response, pairs, signerPublicKey))
            assertFalse(
                primitives.verifyTrustedChallengeResponse(
                    bundle.challenge,
                    SecdsaTrustedChallengeResponse(tamper(response.primaryHash), response.additionalSecretData),
                    pairs,
                    signerPublicKey,
                ),
            )
        }

    @Test
    fun testRecoverProviderMultipliedPointUsesKmsRawX() =
        runTest {
            val kmsProvider = SecdsaTestContext("secdsa-point-recovery", this@DefaultSecdsaPrimitivesTest).softwareKmsProvider
            val blindingKey = generateKmsP256Key(kmsProvider, "blinding-key-recovery")
            val publicPoint = primitives.multiply(scalar(5))

            val recovered =
                primitives.recoverProviderMultipliedPoint(
                    privateKeyInfo = blindingKey.privateKeyInfo,
                    publicPoint = publicPoint,
                    blindingPublicKey = blindingKey.publicPoint,
                    kmsProvider = kmsProvider,
                )

            assertEquals(primitives.multiply(scalar(5), blindingKey.publicPoint), recovered)
        }

    @Test
    fun testBlindIssuanceRoundTripDoesNotExposeUnblindedSecdsaPublicKey() =
        runTest {
            val kmsProvider = SecdsaTestContext("secdsa-blind-issuance", this@DefaultSecdsaPrimitivesTest).softwareKmsProvider
            val nchKey = generateKmsP256Key(kmsProvider, "issuance-nch")
            val blindingKey = generateKmsP256Key(kmsProvider, "issuance-blinding")
            val pinScalar = scalar(7)
            val walletBlindScalar = scalar(11)
            val challenge = "issuance-challenge".encodeToByteArray()

            val request =
                primitives.createBlindIssuanceRequest(
                    nchPublicKey = nchKey.publicPoint,
                    pinScalar = pinScalar,
                    walletBlindScalar = walletBlindScalar,
                    proofNonceScalar = scalar(13),
                    challenge = challenge,
                )
            assertTrue(
                primitives.verifyEqualDiscreteLog(
                    pairs = listOf(SecdsaEqDlogPair(request.nchPublicKey, request.blindedSecdsaPublicKey)),
                    proof = request.pinPossessionProof,
                    aad = challenge,
                ),
            )

            val response =
                primitives.createBlindIssuanceResponse(
                    blindedSecdsaPublicKey = request.blindedSecdsaPublicKey,
                    blindingPublicKey = blindingKey.publicPoint,
                    blindingPrivateKeyInfo = blindingKey.privateKeyInfo,
                    kmsProvider = kmsProvider,
                )
            val internalBlindPublicKey = primitives.unblindInternalPublicKey(response.blindedInternalPublicKey, walletBlindScalar)
            val multipliedNch =
                primitives.recoverProviderMultipliedPoint(
                    privateKeyInfo = blindingKey.privateKeyInfo,
                    publicPoint = nchKey.publicPoint,
                    blindingPublicKey = blindingKey.publicPoint,
                    kmsProvider = kmsProvider,
                )

            assertEquals(primitives.multiply(pinScalar, multipliedNch), internalBlindPublicKey)
        }

    @Test
    fun testEncryptedSignedInstructionRoundTripUsesKmsForNchAndBlindingKeys() =
        runTest {
            val kmsProvider = SecdsaTestContext("secdsa-instruction", this@DefaultSecdsaPrimitivesTest).softwareKmsProvider
            val nchKey = generateKmsP256Key(kmsProvider, "instruction-nch")
            val blindingKey = generateKmsP256Key(kmsProvider, "instruction-blinding")
            val pinScalar = scalar(7)
            val walletBlindScalar = scalar(11)
            val issuanceRequest =
                primitives.createBlindIssuanceRequest(
                    nchPublicKey = nchKey.publicPoint,
                    pinScalar = pinScalar,
                    walletBlindScalar = walletBlindScalar,
                    proofNonceScalar = scalar(13),
                )
            val issuanceResponse =
                primitives.createBlindIssuanceResponse(
                    blindedSecdsaPublicKey = issuanceRequest.blindedSecdsaPublicKey,
                    blindingPublicKey = blindingKey.publicPoint,
                    blindingPrivateKeyInfo = blindingKey.privateKeyInfo,
                    kmsProvider = kmsProvider,
                )
            val internalCertificate =
                SecdsaInternalCertificateMaterial(
                    identifier = "ic-1",
                    blindingPublicKey = blindingKey.publicPoint,
                    blindSecdsaPublicKey = primitives.unblindInternalPublicKey(issuanceResponse.blindedInternalPublicKey, walletBlindScalar),
                    nchPublicKey = nchKey.publicPoint,
                )
            val instruction = "authorize credential presentation".encodeToByteArray()
            val extraData = "authorization-data".encodeToByteArray()
            val envelope =
                primitives.createEncryptedSignedInstruction(
                    instruction = instruction,
                    extraData = extraData,
                    sequenceNumber = 1,
                    internalCertificate = internalCertificate,
                    pinScalar = pinScalar,
                    nchKeyInfo = nchKey.privateKeyInfo,
                    kmsProvider = kmsProvider,
                    instructionBlindScalar = scalar(19),
                    proofNonceScalar = scalar(23),
                    iv = ByteArray(12) { (0xB0 + it).toByte() },
                )

            val executed =
                primitives.executeEncryptedSignedInstruction(
                    envelope = envelope,
                    internalCertificate = internalCertificate,
                    blindingPrivateKeyInfo = blindingKey.privateKeyInfo,
                    kmsProvider = kmsProvider,
                )

            assertContentEquals(instruction, executed.payload.instruction)
            assertContentEquals(extraData, executed.extraData)
            assertEquals(1, executed.payload.sequenceNumber)
            assertEquals(1, executed.newState.lastSequenceNumber)
            assertTrue(primitives.validatePoint(executed.originalSignaturePoint))
        }

    private fun deterministicEcdsaSignature(
        digest: ByteArray,
        privateScalar: SecdsaScalar,
        nonceScalar: SecdsaScalar,
    ): SecdsaRawEcdsaSignature {
        val publicKey = primitives.multiply(privateScalar)
        val r = primitives.scalar(primitives.multiply(nonceScalar).x)
        val e = primitives.digestToScalar(digest)
        val s =
            primitives.multiplyScalars(
                primitives.inverse(nonceScalar),
                primitives.addScalars(e, primitives.multiplyScalars(r, privateScalar)),
            )
        val signature = SecdsaRawEcdsaSignature(r = r.value, s = s.value)
        assertTrue(primitives.verifyDigestSignature(digest, publicKey, signature))
        return signature
    }

    private fun scalar(value: Int): SecdsaScalar = primitives.scalar(byteArrayOf(value.toByte()))

    private suspend fun generateKmsP256Key(
        kmsProvider: KmsProvider,
        alias: String,
    ): KmsP256Key {
        val key = kmsProvider.generateKeyAsync(alias = alias, alg = SignatureAlgorithm.ECDSA_SHA256)
        return KmsP256Key(
            privateKeyInfo =
                KeyInfo<Jwk>(
                    providerId = kmsProvider.id,
                    alias = key.alias,
                    keyVisibility = KeyVisibility.PRIVATE,
                    signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256,
                ),
            publicPoint = primitives.pointFromJwk(key.jose.publicJwk),
        )
    }

    private data class KmsP256Key(
        val privateKeyInfo: KeyInfo<Jwk>,
        val publicPoint: SecdsaPoint,
    )

    private fun tamper(value: ByteArray): ByteArray =
        value.copyOf().also { bytes ->
            bytes[0] = (bytes[0].toInt() xor 0x01).toByte()
        }
}
