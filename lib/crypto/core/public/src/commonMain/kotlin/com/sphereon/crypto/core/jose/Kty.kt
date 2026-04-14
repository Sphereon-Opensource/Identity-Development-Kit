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
 * Represents the possible key types defined by the JSON Web Algorithms (JWA) specification.
 * These key types are used for cryptographic operations.
 *
 * @property value The identifier of the key type.
 */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("JwaKeyType", exact = true)
@Serializable(with = JwaKeyTypeSerializer::class)
enum class JwaKeyType(
    val value: String,
) {
    EC("EC"),
    RSA("RSA"),
    oct("oct"),
    OKP("OKP"),
    ;

    override fun toString() = value

    companion object {
        @JsStatic
        @JvmStatic
        fun fromValue(value: String): JwaKeyType =
            entries.find { entry -> value.startsWith(entry.value) } // i.e. RSA-HSM
                ?: throw IllegalArgumentException("Unknown value $value")
    }
}

internal object JwaKeyTypeSerializer : KSerializer<JwaKeyType> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("JwaKeyType", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: JwaKeyType,
    ) {
        encoder.encodeString(value.value)
    }

    override fun deserialize(decoder: Decoder): JwaKeyType {
        val value = decoder.decodeString()
        return JwaKeyType.fromValue(value)
    }
}
