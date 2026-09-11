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
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.crypto.core.cose.CoseKey
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * RFC 9180 HPKE suite identifiers.
 *
 * The identifiers are kept as unsigned 16-bit values because they are encoded as I2OSP(2)
 * values in the HPKE labeled extract/expand inputs.  A suite is deliberately independent of
 * mdoc: callers bind its [info] and [associatedData] to their own protocol transcript.
 */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("HpkeSuite", exact = true)
data class HpkeSuite(
    val kemId: UInt,
    val kdfId: UInt,
    val aeadId: UInt,
) {
    init {
        require(kemId <= UINT16_MAX) { "HPKE KEM identifier must fit in 16 bits" }
        require(kdfId <= UINT16_MAX) { "HPKE KDF identifier must fit in 16 bits" }
        require(aeadId <= UINT16_MAX) { "HPKE AEAD identifier must fit in 16 bits" }
    }

    companion object {
        /** DHKEM(P-256, HKDF-SHA256), HKDF-SHA256, AES-128-GCM. */
        val DHKEM_P256_HKDF_SHA256_HKDF_SHA256_AES_128_GCM: HpkeSuite = HpkeSuite(
            kemId = 0x0010u,
            kdfId = 0x0001u,
            aeadId = 0x0001u,
        )

        private const val UINT16_MAX: UInt = 0xffffu
    }
}

/** The generic HPKE Base-mode output. The [enc] value is the sender's KEM encapsulated key. */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("HpkeCiphertext", exact = true)
data class HpkeCiphertext(
    val enc: ByteArray,
    val cipherText: ByteArray,
) {
    init {
        require(enc.isNotEmpty()) { "HPKE encapsulated key must not be empty" }
        require(cipherText.isNotEmpty()) { "HPKE ciphertext must not be empty" }
    }

    override fun equals(other: Any?): Boolean =
        other is HpkeCiphertext && enc.contentEquals(other.enc) && cipherText.contentEquals(other.cipherText)

    override fun hashCode(): Int = 31 * enc.contentHashCode() + cipherText.contentHashCode()
}

/**
 * Generic HPKE Base-mode service.
 *
 * Implementations must authenticate [associatedData] and bind both operations to [info].  The
 * private key is supplied only to [open]; implementations must not export or log it.  Protocol
 * packages such as mdoc should encode their own envelopes and transcript-derived [info] around
 * this primitive rather than adding protocol-specific fields here.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("HpkeProvider", exact = true)
@JsExportCompat
interface HpkeProvider {
    fun supports(
        suite: HpkeSuite,
        recipientPublicKey: CoseKey,
    ): Boolean

    suspend fun seal(
        plaintext: ByteArray,
        recipientPublicKey: CoseKey,
        suite: HpkeSuite = HpkeSuite.DHKEM_P256_HKDF_SHA256_HKDF_SHA256_AES_128_GCM,
        info: ByteArray = ByteArray(0),
        associatedData: ByteArray = ByteArray(0),
    ): IdkResult<HpkeCiphertext, IdkError>

    suspend fun open(
        ciphertext: HpkeCiphertext,
        recipientPrivateKey: CoseKey,
        suite: HpkeSuite = HpkeSuite.DHKEM_P256_HKDF_SHA256_HKDF_SHA256_AES_128_GCM,
        info: ByteArray = ByteArray(0),
        associatedData: ByteArray = ByteArray(0),
        /** Optional public-key binding when the enclosing protocol carries the requested key. */
        recipientPublicKey: CoseKey? = null,
    ): IdkResult<ByteArray, IdkError>
}

/** Consistent failure helper for adapters that require a particular HPKE suite. */
fun unsupportedHpkeSuite(suite: HpkeSuite): IdkResult<Nothing, IdkError> =
    Err(
        IdkError.ILLEGAL_ARGUMENT_ERROR(
            message = "Unsupported HPKE suite kem=${suite.kemId}, kdf=${suite.kdfId}, aead=${suite.aeadId}",
        ),
    )
