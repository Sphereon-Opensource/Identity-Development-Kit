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

package com.sphereon.crypto.core.sign.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import com.sphereon.core.compat.JsExportCompat
/**
 * Represents a digital signature form (format/standard).
 *
 * IDK defines the core forms: RAW, JWS, COSE.
 * EDK extends this with eIDAS forms (CAdES, PAdES, JAdES, XAdES, PKCS7)
 * via companion extension properties.
 *
 * Uses value-based equality so that registered and ad-hoc instances
 * with the same value are considered equal.
 */
@JsExportCompat
@Serializable(with = SignatureFormSerializer::class)
open class SignatureForm(val value: String) {

    override fun equals(other: Any?): Boolean = other is SignatureForm && value == other.value
    override fun hashCode(): Int = value.hashCode()
    override fun toString(): String = value

    companion object {
        /** Simply sign a digest or raw bytearray using the key */
        val RAW = SignatureForm("RAW")

        /** JSON Web Signature */
        val JWS = SignatureForm("JWS")

        /** CBOR Object Signing and Encryption */
        val COSE = SignatureForm("COSE")

        private val registry = mutableMapOf<String, SignatureForm>()

        init {
            listOf(RAW, JWS, COSE).forEach { registry[it.value] = it }
        }

        /**
         * Register a signature form in the global registry.
         * Used by EDK to register eIDAS forms (CAdES, PAdES, etc.).
         */
        fun register(form: SignatureForm): SignatureForm {
            registry[form.value] = form
            return form
        }

        /**
         * Look up a signature form by its string value.
         * Returns the registered instance if found, or creates an ad-hoc instance.
         */
        fun fromValue(value: String): SignatureForm = registry[value] ?: SignatureForm(value)

        /**
         * All currently registered signature forms.
         */
        val entries: List<SignatureForm> get() = registry.values.toList()
    }
}

/**
 * Serializes [SignatureForm] as its string value.
 */
object SignatureFormSerializer : KSerializer<SignatureForm> {
    override val descriptor = PrimitiveSerialDescriptor("SignatureForm", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: SignatureForm) = encoder.encodeString(value.value)
    override fun deserialize(decoder: Decoder): SignatureForm = SignatureForm.fromValue(decoder.decodeString())
}
