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

package com.sphereon.did.models

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.experimental.ExperimentalObjCName
import kotlin.js.JsStatic
import kotlin.native.ObjCName

/**
 * Wrapper for verification method relationships that can be either:
 * - A string reference (absolute or relative DID URL)
 * - A full embedded VerificationMethod object
 *
 * This is used in DID Document verification relationship arrays (authentication,
 * assertionMethod, keyAgreement, capabilityInvocation, capabilityDelegation).
 *
 * Uses a custom serializer to handle JSON polymorphism where the value can be
 * either a string or an object.
 *
 * Plain data class for Obj-C/JS compatibility.
 *
 * @property reference String reference like "#key-1" or "did:example:123#key-1"
 * @property embedded Full embedded VerificationMethod object
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DidVerificationMethodOrReference", exact = true)
@JsExportCompat
@Serializable(with = VerificationMethodOrReferenceSerializer::class)
data class VerificationMethodOrReference(
    val reference: String? = null,
    val embedded: VerificationMethod? = null
) {
    init {
        require((reference != null) xor (embedded != null)) {
            "Must have exactly one of reference or embedded"
        }
    }

    companion object {
        /**
         * Creates a VerificationMethodOrReference from a string reference.
         *
         * @param ref The reference string (e.g., "#key-1" or "did:example:123#key-1")
         */
        @JsStatic
        fun fromReference(ref: String): VerificationMethodOrReference =
            VerificationMethodOrReference(reference = ref)

        /**
         * Creates a VerificationMethodOrReference from an embedded VerificationMethod.
         *
         * @param vm The embedded VerificationMethod object
         */
        @JsStatic
        fun fromEmbedded(vm: VerificationMethod): VerificationMethodOrReference =
            VerificationMethodOrReference(embedded = vm)
    }

    /**
     * Returns true if this is a reference (not an embedded object).
     */
    val isReference: Boolean get() = reference != null

    /**
     * Returns true if this is an embedded VerificationMethod object.
     */
    val isEmbedded: Boolean get() = embedded != null

    /**
     * Gets the verification method ID, whether from a reference or embedded object.
     *
     * @return The verification method ID
     */
    fun getId(): String = reference ?: embedded?.id ?: throw IllegalStateException("No reference or embedded VM")
}

/**
 * Custom serializer for VerificationMethodOrReference.
 *
 * Handles the W3C DID Document polymorphism where verification relationships
 * can be either string references or full embedded verification method objects.
 *
 * On serialization:
 * - If reference is set, outputs a JSON string
 * - If embedded is set, outputs a JSON object
 *
 * On deserialization:
 * - If input is a JSON string, creates a reference
 * - If input is a JSON object, creates an embedded VM
 */
internal object VerificationMethodOrReferenceSerializer : KSerializer<VerificationMethodOrReference> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("VerificationMethodOrReference")

    override fun serialize(encoder: Encoder, value: VerificationMethodOrReference) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: throw IllegalStateException("This serializer only works with JSON")

        if (value.reference != null) {
            jsonEncoder.encodeJsonElement(JsonPrimitive(value.reference))
        } else if (value.embedded != null) {
            jsonEncoder.encodeSerializableValue(VerificationMethod.serializer(), value.embedded)
        }
    }

    override fun deserialize(decoder: Decoder): VerificationMethodOrReference {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw IllegalStateException("This serializer only works with JSON")

        val element = jsonDecoder.decodeJsonElement()

        return when (element) {
            is JsonPrimitive -> {
                if (element.isString) {
                    VerificationMethodOrReference.fromReference(element.content)
                } else {
                    throw IllegalArgumentException("Expected string reference, got: $element")
                }
            }
            is JsonObject -> {
                val vm = jsonDecoder.json.decodeFromJsonElement(VerificationMethod.serializer(), element)
                VerificationMethodOrReference.fromEmbedded(vm)
            }
            else -> throw IllegalArgumentException("Expected string or object, got: $element")
        }
    }
}
