/*
 * Copyright 2026 Sphereon International B.V.
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

package com.sphereon.crypto.core.hpke

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.decodeFrom
import com.sphereon.core.api.Encoding
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.cose.CoseKey
import com.sphereon.crypto.core.generic.Curve
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.computeHmac
import com.sphereon.crypto.core.jose.JwaCurve
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.jose.cborToJwk
import com.sphereon.crypto.core.kms.EcdhUtils
import com.sphereon.di.session.SessionScope
import dev.whyoleg.cryptography.BinarySize.Companion.bits
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.algorithms.AES
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.CancellationException

/**
 * RFC 9180 Base-mode HPKE implementation for the suite exposed by the crypto-core API.
 *
 * Protocols choose the transcript-bound [info] and authenticated [associatedData]; this class
 * knows nothing about mdoc, DeviceResponse, or any other envelope.  Additional suites should
 * be added as separate implementations or explicit suite branches after independent vectors
 * have been verified.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<HpkeProvider>())
class Rfc9180HpkeProvider : HpkeProvider {
    override fun supports(suite: HpkeSuite, recipientPublicKey: CoseKey): Boolean =
        suite == HpkeSuite.DHKEM_P256_HKDF_SHA256_HKDF_SHA256_AES_128_GCM &&
            runCatching { requireP256Public(recipientPublicKey.cborToJwk()) }.isSuccess

    override suspend fun seal(
        plaintext: ByteArray,
        recipientPublicKey: CoseKey,
        suite: HpkeSuite,
        info: ByteArray,
        associatedData: ByteArray,
    ): IdkResult<HpkeCiphertext, IdkError> =
        try {
            requireSupportedSuite(suite)
            val recipient = requireP256Public(recipientPublicKey.cborToJwk())
            val ephemeral = EcdhUtils.generateEphemeralKeyPair(Curve.P_256)
            val enc = publicPoint(ephemeral.publicKeyJwk)
            val sharedSecret = EcdhUtils.performKeyAgreement(ephemeral.privateKeyDer, recipient, Curve.P_256)
            val keySchedule = setupBaseMode(
                sharedSecret = sharedSecret,
                enc = enc,
                recipientPublicPoint = publicPoint(recipient),
                info = info,
            )
            val cipherText = aesGcm(keySchedule.key, keySchedule.nonce, plaintext, associatedData, encrypt = true)
            Ok(HpkeCiphertext(enc = enc, cipherText = cipherText))
        } catch (e: IllegalArgumentException) {
            Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "HPKE seal failed: ${e.message}", throwable = e))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Err(IdkError.UNKNOWN_ERROR(message = "HPKE seal failed: ${e.message}", exception = e))
        }

    override suspend fun open(
        ciphertext: HpkeCiphertext,
        recipientPrivateKey: CoseKey,
        suite: HpkeSuite,
        info: ByteArray,
        associatedData: ByteArray,
        recipientPublicKey: CoseKey?,
    ): IdkResult<ByteArray, IdkError> =
        try {
            requireSupportedSuite(suite)
            val recipient = requireP256Private(recipientPrivateKey.cborToJwk())
            val configuredRecipient = recipientPublicKey?.let { requireP256Public(it.cborToJwk()) }
            val recipientPoint = publicPoint(configuredRecipient ?: recipient)
            if (configuredRecipient != null) {
                require(publicPoint(recipient).contentEquals(recipientPoint)) {
                    "HPKE private key does not match the requested recipient public key"
                }
            }
            val ephemeral = publicJwkFromPoint(ciphertext.enc)
            val sharedSecret = EcdhUtils.performKeyAgreementForDecryption(recipient, ephemeral, Curve.P_256)
            val keySchedule = setupBaseMode(
                sharedSecret = sharedSecret,
                enc = ciphertext.enc,
                recipientPublicPoint = recipientPoint,
                info = info,
            )
            Ok(aesGcm(keySchedule.key, keySchedule.nonce, ciphertext.cipherText, associatedData, encrypt = false))
        } catch (e: IllegalArgumentException) {
            Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "HPKE open failed: ${e.message}", throwable = e))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Err(IdkError.UNKNOWN_ERROR(message = "HPKE open failed: ${e.message}", exception = e))
        }

    private fun requireSupportedSuite(suite: HpkeSuite) {
        require(suite == HpkeSuite.DHKEM_P256_HKDF_SHA256_HKDF_SHA256_AES_128_GCM) {
            "Unsupported HPKE suite kem=${suite.kemId}, kdf=${suite.kdfId}, aead=${suite.aeadId}"
        }
    }

    private fun requireP256Public(key: com.sphereon.crypto.core.jose.JwkType): Jwk {
        require(key.kty == JwaKeyType.EC) { "HPKE key must be an EC key" }
        require(key.crv == JwaCurve.P_256) { "HPKE key must use P-256" }
        publicPoint(key)
        return Jwk.from(key)
    }

    private fun requireP256Private(key: com.sphereon.crypto.core.jose.JwkType): Jwk {
        val privateKey = requireP256Public(key)
        require(!privateKey.d.isNullOrBlank()) { "HPKE open requires a P-256 private key" }
        return privateKey
    }

    private fun publicJwkFromPoint(encoded: ByteArray): Jwk {
        require(encoded.size == P256_ENCODED_POINT_SIZE && encoded[0] == 0x04.toByte()) {
            "HPKE enc must be an uncompressed P-256 point"
        }
        return Jwk.Builder()
            .withKty(JwaKeyType.EC)
            .withCrv(JwaCurve.P_256)
            .withX(encoded.copyOfRange(1, 33).encodeToBase64Url())
            .withY(encoded.copyOfRange(33, 65).encodeToBase64Url())
            .build()
    }

    private fun publicPoint(key: com.sphereon.crypto.core.jose.JwkType): ByteArray {
        require(key.kty == JwaKeyType.EC && key.crv == JwaCurve.P_256) { "HPKE requires a P-256 EC key" }
        val x = key.x?.decodeFrom(Encoding.BASE64URL) ?: error("P-256 key is missing x")
        val y = key.y?.decodeFrom(Encoding.BASE64URL) ?: error("P-256 key is missing y")
        require(x.size == P256_COORDINATE_SIZE && y.size == P256_COORDINATE_SIZE) {
            "P-256 coordinates must be exactly 32 bytes"
        }
        return byteArrayOf(0x04) + x + y
    }

    private suspend fun setupBaseMode(
        sharedSecret: ByteArray,
        enc: ByteArray,
        recipientPublicPoint: ByteArray,
        info: ByteArray,
    ): KeySchedule {
        val kemSuiteId = "KEM".encodeToByteArray() + u16(KEM_P256_HKDF_SHA256)
        val hpkeSuiteId = "HPKE".encodeToByteArray() +
            u16(KEM_P256_HKDF_SHA256) + u16(KDF_HKDF_SHA256) + u16(AEAD_AES_128_GCM)
        val eaePrk = labeledExtract(kemSuiteId, ByteArray(0), "eae_prk", sharedSecret)
        val kemSharedSecret = labeledExpand(kemSuiteId, eaePrk, "shared_secret", enc + recipientPublicPoint, HASH_LENGTH)
        val pskIdHash = labeledExtract(hpkeSuiteId, ByteArray(0), "psk_id_hash", ByteArray(0))
        val infoHash = labeledExtract(hpkeSuiteId, ByteArray(0), "info_hash", info)
        val context = byteArrayOf(0) + pskIdHash + infoHash
        val secret = labeledExtract(hpkeSuiteId, kemSharedSecret, "secret", ByteArray(0))
        return KeySchedule(
            key = labeledExpand(hpkeSuiteId, secret, "key", context, AES_128_KEY_LENGTH),
            nonce = labeledExpand(hpkeSuiteId, secret, "base_nonce", context, AES_GCM_NONCE_LENGTH),
        )
    }

    private suspend fun labeledExtract(
        suiteId: ByteArray,
        salt: ByteArray,
        label: String,
        input: ByteArray,
    ): ByteArray =
        computeHmac(
            key = if (salt.isEmpty()) ByteArray(HASH_LENGTH) else salt,
            message = "HPKE-v1".encodeToByteArray() + suiteId + label.encodeToByteArray() + input,
            algorithm = DigestAlg.SHA256,
        )

    private suspend fun labeledExpand(
        suiteId: ByteArray,
        prk: ByteArray,
        label: String,
        info: ByteArray,
        length: Int,
    ): ByteArray =
        hkdfExpand(
            prk = prk,
            // RFC 9180 LabeledExpand: I2OSP(L, 2) precedes the domain-separation prefix.
            info = u16(length) + "HPKE-v1".encodeToByteArray() + suiteId + label.encodeToByteArray() + info,
            length = length,
        )

    private suspend fun hkdfExpand(prk: ByteArray, info: ByteArray, length: Int): ByteArray {
        require(length in 0..(255 * HASH_LENGTH)) { "HKDF output length is outside RFC 5869 bounds" }
        if (length == 0) return ByteArray(0)
        val result = ByteArray(length)
        var previous = ByteArray(0)
        var offset = 0
        var counter = 1
        while (offset < length) {
            previous = computeHmac(prk, previous + info + byteArrayOf(counter.toByte()), DigestAlg.SHA256)
            val count = minOf(previous.size, length - offset)
            previous.copyInto(result, destinationOffset = offset, endIndex = count)
            offset += count
            counter++
        }
        return result
    }

    @OptIn(DelicateCryptographyApi::class)
    private suspend fun aesGcm(
        key: ByteArray,
        nonce: ByteArray,
        input: ByteArray,
        associatedData: ByteArray,
        encrypt: Boolean,
    ): ByteArray {
        val aesKey = CryptographyProvider.Default.get(AES.GCM).keyDecoder().decodeFromByteArray(AES.Key.Format.RAW, key)
        val cipher = aesKey.cipher(tagSize = AES_GCM_TAG_BITS.bits)
        return if (encrypt) {
            cipher.encryptWithIv(iv = nonce, plaintext = input, associatedData = associatedData)
        } else {
            cipher.decryptWithIv(iv = nonce, ciphertext = input, associatedData = associatedData)
        }
    }

    private fun u16(value: Int): ByteArray = byteArrayOf((value ushr 8).toByte(), value.toByte())

    private data class KeySchedule(val key: ByteArray, val nonce: ByteArray)

    private companion object {
        const val KEM_P256_HKDF_SHA256 = 0x0010
        const val KDF_HKDF_SHA256 = 0x0001
        const val AEAD_AES_128_GCM = 0x0001
        const val HASH_LENGTH = 32
        const val AES_128_KEY_LENGTH = 16
        const val AES_GCM_NONCE_LENGTH = 12
        const val AES_GCM_TAG_BITS = 128
        const val P256_COORDINATE_SIZE = 32
        const val P256_ENCODED_POINT_SIZE = 65
    }
}
