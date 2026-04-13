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

package com.sphereon.crypto.core.cose

import com.sphereon.cbor.CborUInt
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.experimental.ExperimentalObjCName
import com.sphereon.core.compat.JsExportCompat
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.native.ObjCName


/**
 * This parameter is used to identify the family of keys for this
 *       structure and, thus, the set of key-type-specific parameters to be
 *       found. This parameter MUST be present in a key object.
 *       Implementations MUST verify that the key type is appropriate for
 *       the algorithm being processed.  The key type MUST be included as
 *       part of the trust decision process.
 */
@JsExportCompat
@Serializable(with = CoseKeyTypeSerializer::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("CoseKeyTypeEnum", exact = true)
enum class CoseKeyTypeEnum(val value: Int, val explanation: String) {
    OKP(1, "Octet Key Pair"),
    EC2(2, "Elliptic Curve Keys w/ x- and y-coordinate pair"),
    RSA(3, "RSA"),
    Symmetric(4, "Symmetric Keys"),
    Reserved(0, "Reserved");

    @JsName("toCbor")
    fun toCbor(): CborUInt {
        return CborUInt(this.value)
    }

    override fun toString() = "" + value

    companion object {
        @JsStatic
        @JsName("fromValue")
        fun fromValue(value: Long): CoseKeyTypeEnum {
            return entries.find { entry -> entry.value == value.toInt() }
                ?: throw IllegalArgumentException("Unknown value $value")
        }
    }
}

internal object CoseKeyTypeSerializer : KSerializer<CoseKeyTypeEnum> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("CoseKeyType", PrimitiveKind.INT)

    override fun serialize(encoder: Encoder, value: CoseKeyTypeEnum) {
        encoder.encodeInt(value.value)
    }

    override fun deserialize(decoder: Decoder): CoseKeyTypeEnum {
        val value = decoder.decodeLong()
        return CoseKeyTypeEnum.fromValue(value)
    }
}
