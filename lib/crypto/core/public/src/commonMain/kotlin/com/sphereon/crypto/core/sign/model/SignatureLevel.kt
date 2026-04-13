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

package com.sphereon.crypto.core.sign.model

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Represents a digital signature compliance level, associated with a [SignatureForm].
 *
 * IDK defines core levels: RAW, JWS, COSE.
 * EDK extends this with eIDAS levels (CAdES_BASELINE_B, PAdES_BASELINE_LT, etc.)
 * via companion extension properties.
 *
 * Uses value-based equality so that registered and ad-hoc instances
 * with the same value are considered equal.
 *
 * @property value The string identifier for this level (e.g. "RAW", "CAdES_BASELINE_B")
 * @property form The signature form this level belongs to
 */
@JsExportCompat
@Serializable(with = SignatureLevelSerializer::class)
open class SignatureLevel(
    val value: String,
    val form: SignatureForm,
) {
    override fun equals(other: Any?): Boolean = other is SignatureLevel && value == other.value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = value

    companion object {
        val RAW = SignatureLevel("RAW", SignatureForm.RAW)
        val JWS = SignatureLevel("JWS", SignatureForm.JWS)
        val COSE = SignatureLevel("COSE", SignatureForm.COSE)

        private val registry = mutableMapOf<String, SignatureLevel>()

        /**
         * All currently registered signature levels.
         */
        val entries: List<SignatureLevel> get() = registry.values.toList()

        init {
            listOf(RAW, JWS, COSE).forEach { registry[it.value] = it }
        }

        /**
         * Register a signature level in the global registry.
         * Used by EDK to register eIDAS levels.
         */
        fun register(level: SignatureLevel): SignatureLevel {
            registry[level.value] = level
            return level
        }

        /**
         * Look up a signature level by its string value.
         * Returns null if not registered.
         */
        fun fromValue(value: String): SignatureLevel? = registry[value]
    }
}

/**
 * Serializes [SignatureLevel] as its string value.
 * On deserialization, looks up the registry. If the value is not registered,
 * creates an ad-hoc instance with an inferred form.
 */
object SignatureLevelSerializer : KSerializer<SignatureLevel> {
    override val descriptor = PrimitiveSerialDescriptor("SignatureLevel", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: SignatureLevel,
    ) = encoder.encodeString(value.value)

    override fun deserialize(decoder: Decoder): SignatureLevel {
        val value = decoder.decodeString()
        return SignatureLevel.fromValue(value) ?: SignatureLevel(value, inferForm(value))
    }

    private fun inferForm(value: String): SignatureForm =
        when {
            value.startsWith("CAdES") || value.startsWith("CMS") -> SignatureForm.fromValue("CAdES")
            value.startsWith("PAdES") -> SignatureForm.fromValue("PAdES")
            value.startsWith("PKCS7") || value.startsWith("PDF") -> SignatureForm.fromValue("PKCS7")
            value.startsWith("JAdES") || value.startsWith("JSON") -> SignatureForm.fromValue("JAdES")
            value.startsWith("XAdES") || value.startsWith("XML") -> SignatureForm.fromValue("XAdES")
            value == "JWS" -> SignatureForm.JWS
            value == "COSE" -> SignatureForm.COSE
            else -> SignatureForm.RAW
        }
}
