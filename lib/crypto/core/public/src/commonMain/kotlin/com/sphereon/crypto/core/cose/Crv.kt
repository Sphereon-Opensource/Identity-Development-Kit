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
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import com.sphereon.core.compat.JsExportCompat
import kotlin.js.JsName
import kotlin.js.JsStatic

/**
 * JwaCurve is an enumeration class that represents the available elliptic curves for JWA cryptography algorithms.
 *
 * @property curveName The name of the curve.
 *
 * @since 0.1.0
 */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("CoseCurve", exact = true)
@Serializable(with = CoseCurveSerializer::class)
enum class CoseCurve(val curveName: String, val value: Int, keyType: CoseKeyTypeEnum) {
    P_256("P-256", 1, CoseKeyTypeEnum.EC2),
    P_384("P-384", 2, CoseKeyTypeEnum.EC2),
    P_521("P-521", 3, CoseKeyTypeEnum.EC2),
    X25519("X25519", 4, CoseKeyTypeEnum.OKP),
    X448("X448", 5, CoseKeyTypeEnum.OKP),
    Ed25519("Ed25519", 6, CoseKeyTypeEnum.OKP),
    Ed448("Ed448", 7, CoseKeyTypeEnum.OKP),
    secp256k1("secp256k1", -1, CoseKeyTypeEnum.EC2),
    ;

    @JsName("toCbor")
    fun toCbor(): CborUInt {
        return CborUInt(this.value)
    }

    override fun toString() = "" + value

    companion object {
        @JsStatic
        @JsName("fromValue")
        fun fromValue(value: Int): CoseCurve {
            return entries.find { entry -> entry.value == value }
                ?: throw IllegalArgumentException("Unknown value $value")
        }

        @JsStatic
        @JsName("fromCurveName")
        fun fromCurveName(curveName: String): CoseCurve =
            entries.find { it.curveName == curveName }
                ?: throw IllegalArgumentException("Unknown curve name $curveName")
    }
}


internal object CoseCurveSerializer : KSerializer<CoseCurve> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("CoseCurve", PrimitiveKind.INT)

    override fun serialize(encoder: Encoder, value: CoseCurve) {
        encoder.encodeInt(value.value)
    }

    override fun deserialize(decoder: Decoder): CoseCurve {
        val value = decoder.decodeInt()
        return CoseCurve.fromValue(value)
    }
}
