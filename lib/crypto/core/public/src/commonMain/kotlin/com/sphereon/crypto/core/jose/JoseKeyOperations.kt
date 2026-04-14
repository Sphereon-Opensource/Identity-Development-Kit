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

@JsExportCompat
@Serializable(with = JoseKeyOperationsSerializer::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("JoseKeyOperations", exact = true)
enum class JoseKeyOperations(
    val value: String,
    val explanation: String,
) {
    SIGN("sign", "The key is used to create signatures.  Requires private key fields"),
    VERIFY("verify", "The key is used for verification of signatures"),
    ENCRYPT("encrypt", "The key is used for key transport encryption."),
    DECRYPT("decrypt", "The key is used for key transport decryption"),
    WRAP_KEY("wrap key", "The key is used for key wrap encryption."),
    UNWRAP_KEY("unwrap key", "The key is used for key wrap decryption. Requires private key fields"),
    DERIVE_KEY("derive key", "The key is used for deriving keys.  Requires private key fields"),
    DERIVE_BITS(
        "derive bits",
        "The key is used for deriving bits not to be used as a key.  Requires private key fields.",
    ),
    MAC_CREATE("MAC create", "The key is used for creating MACs. "),
    MAC_VERIFY("MAC verify", "The key is used for validating MACs."),
    ;

    override fun toString() = value

    companion object {
        @JsStatic
        @JvmStatic
        fun fromValue(value: String): JoseKeyOperations =
            entries.find { entry -> entry.value == value }
                ?: throw IllegalArgumentException("Unknown value $value")
    }
}

internal object JoseKeyOperationsSerializer : KSerializer<JoseKeyOperations> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("JoseKeyOperations", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: JoseKeyOperations,
    ) {
        encoder.encodeString(value.value)
    }

    override fun deserialize(decoder: Decoder): JoseKeyOperations {
        val value = decoder.decodeString()
        return JoseKeyOperations.fromValue(value)
    }
}
