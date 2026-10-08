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

package com.sphereon.crypto.core.cose

import com.sphereon.crypto.core.generic.SignatureAlgorithm
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.HMAC
import kotlinx.coroutines.CancellationException

/**
 * Verifies a COSE_Mac0 with a caller-supplied symmetric key.
 *
 * This is deliberately a crypto-core primitive: protocol adapters such as mdoc decide how to
 * derive and bind their key, payload, and external AAD, while this function only verifies the
 * canonical COSE Mac_structure.  A detached payload must be supplied when the wire object has no
 * payload.
 */
suspend fun defaultVerifyMac0(
    value: CoseMac0Cbor,
    sharedSecret: ByteArray,
    detachedPayload: ByteArray? = null,
    externalAad: ByteArray = ByteArray(0),
    provider: CryptographyProvider? = CryptographyProvider.Default,
    coseHeaderCborCodec: CoseHeaderCborCodec = CoseHeaderCborCodecImpl(),
): Boolean {
    return try {
        require(sharedSecret.isNotEmpty()) { "COSE_Mac0 verification key must not be empty" }
        val algorithm = value.protectedHeader.alg ?: return false
        val signatureAlgorithm = SignatureAlgorithm.fromCose(algorithm)
        val digest = signatureAlgorithm.digestAlgorithm?.toCryptoGraphicAlgorithm() ?: return false
        val payload = value.payload?.value ?: detachedPayload ?: return false
        val toBeMaced =
            createToBeMacedCbor(
                protectedHeader = value.protectedHeader,
                payload = payload,
                externalAad = externalAad,
                headerCodec = coseHeaderCborCodec,
            ).getOrElse { return false }
        val expectedTag =
            requireNotNull(provider) { "A cryptography provider is required for COSE_Mac0 verification" }
                .get(HMAC)
                .keyDecoder(digest)
                .decodeFromByteArray(HMAC.Key.Format.RAW, sharedSecret)
                .signatureGenerator()
                .generateSignature(toBeMaced.value)
        constantTimeEquals(expectedTag, value.tag.value)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Throwable) {
        false
    }
}

private fun constantTimeEquals(left: ByteArray, right: ByteArray): Boolean {
    var difference = left.size xor right.size
    val count = maxOf(left.size, right.size)
    for (index in 0 until count) {
        difference = difference or ((left.getOrNull(index)?.toInt() ?: 0) xor (right.getOrNull(index)?.toInt() ?: 0))
    }
    return difference == 0
}
