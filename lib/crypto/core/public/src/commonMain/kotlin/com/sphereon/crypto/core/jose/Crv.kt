/*
 * © 2026 Sphereon International B.V.
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

package com.sphereon.crypto.core.jose

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.experimental.ExperimentalObjCName
import kotlin.js.JsStatic
import kotlin.jvm.JvmStatic
import kotlin.native.ObjCName

/**
 * JwaCurve is an enumeration class that represents the available elliptic curves for JWA cryptography algorithms.
 *
 * @property value The name of the curve.
 *
 * @since 0.1.0
 */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("JwaCurve", exact = true)
@Serializable(with = JwaCurveSerializer::class)
enum class JwaCurve(
    val value: String,
) {
    P_256("P-256"),
    P_384("P-384"),
    P_521("P-521"),
    Ed25519("Ed25519"),
    X25519("X25519"),
    Secp256k1("secp256k1"),
    ;

    override fun toString() = value

    companion object {
        @JsStatic
        @JvmStatic
        fun fromValue(value: String?): JwaCurve? = entries.find { entry -> entry.value == value }
    }
}

internal object JwaCurveSerializer : KSerializer<JwaCurve> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("JwaCurve", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: JwaCurve,
    ) {
        encoder.encodeString(value.value)
    }

    override fun deserialize(decoder: Decoder): JwaCurve {
        val value = decoder.decodeString()
        return JwaCurve.fromValue(value) ?: throw IllegalArgumentException("Invalid jwa curve")
    }
}
