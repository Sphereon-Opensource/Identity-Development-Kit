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
 *
 */

@file:OptIn(ExperimentalJsStatic::class)

package com.sphereon.crypto.core.x509

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.js.ExperimentalJsStatic
import com.sphereon.core.compat.JsExportCompat
import kotlin.js.JsStatic

/**
 * Standard X.509 key usage extension flags
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc5280#section-4.2.1.3">RFC 5280 Section 4.2.1.3</a>
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("KeyUsageFlag", exact = true)
@JsExportCompat
@Serializable
enum class KeyUsageFlag(val position: Int, val value: String) {
    @SerialName("digitalSignature")
    DIGITAL_SIGNATURE(0, "digitalSignature"),

    @SerialName("nonRepudiation")
    NON_REPUDIATION(1, "nonRepudiation"),

    @SerialName("keyEncipherment")
    KEY_ENCIPHERMENT(2, "keyEncipherment"),

    @SerialName("dataEncipherment")
    DATA_ENCIPHERMENT(3, "dataEncipherment"),

    @SerialName("keyAgreement")
    KEY_AGREEMENT(4, "keyAgreement"),

    @SerialName("keyCertSign")
    KEY_CERT_SIGN(5, "keyCertSign"),

    @SerialName("cRLSign")
    CRL_SIGN(6, "cRLSign"),

    @SerialName("encipherOnly")
    ENCIPHER_ONLY(7, "encipherOnly"),

    @SerialName("decipherOnly")
    DECIPHER_ONLY(8, "decipherOnly");

    companion object {

        @JsStatic
        fun fromValue(value: String): KeyUsageFlag = entries.find { it.value == value } ?: throw IllegalArgumentException("Unknown value key usage value: $value")
    }
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("KeyUsage", exact = true)
@Serializable
@JsExportCompat
data class KeyUsage(val flags: Map<KeyUsageFlag, Boolean>): Map<KeyUsageFlag, Boolean> by flags {
    fun has(usage: KeyUsageFlag): Boolean = flags[usage] == true

    companion object {
        /** Parses the KeyUsage BIT STRING into KeyUsage data class. */
        @JsStatic
        fun fromDerBitString(raw: ByteArray): KeyUsage {
            if (raw.isEmpty() || raw[0] != 0x03.toByte()) {
                error("Invalid BIT STRING tag or empty input")
            }
            val length = raw[1].toInt() and 0xFF
            if (raw.size != length + 2) {
                error("BIT STRING length mismatch")
            }
            if (length < 1) {
                error("BIT STRING length invalid (must include unused bits byte)")
            }

            val unusedBits = raw[2].toInt() and 0xFF
            if (unusedBits > 7) {
                error("Invalid unused bits count")
            }

            val dataBytes = raw.drop(3)

            val allBits = dataBytes.flatMap { byte ->
                (0..7).map { i -> ((byte.toInt() shr (7 - i)) and 1) == 1 }
            }

            val totalBits = dataBytes.size * 8
            val usedBitCount = totalBits - unusedBits
            if (usedBitCount < 0) {
                error("Inconsistent bit string length and unused bits")
            }

            val usedBits = allBits.take(usedBitCount)

            return KeyUsage(KeyUsageFlag.entries.associateWith { flag ->
                usedBits.getOrElse(flag.position) { false }
            })
        }
    }
}
