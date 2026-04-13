/*
 * (c) 2026 Sphereon International B.V.
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

package com.sphereon.openid.oid4vci.common.serializer

import com.sphereon.openid.oid4vci.common.model.CredentialRequestProofs
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Custom serializer for OID4VCI proofs container.
 *
 * Wire format: `{"jwt": ["eyJ...", "eyJ..."]}` where the proof type IS the JSON key
 * and the value is the array of proof values. Values are [JsonElement] to support both
 * string proofs (JWT, CWT, attestation) and object proofs (di_vp).
 */
internal object CredentialRequestProofsSerializer : KSerializer<CredentialRequestProofs> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("CredentialRequestProofs")

    override fun serialize(
        encoder: Encoder,
        value: CredentialRequestProofs,
    ) {
        require(encoder is JsonEncoder) { "CredentialRequestProofsSerializer only works with JSON format" }
        val json = encoder.json

        val jsonObject =
            buildJsonObject {
                put(value.proofType, json.encodeToJsonElement(ListSerializer(JsonElement.serializer()), value.proofValues))
            }

        encoder.encodeJsonElement(jsonObject)
    }

    override fun deserialize(decoder: Decoder): CredentialRequestProofs {
        require(decoder is JsonDecoder) { "CredentialRequestProofsSerializer only works with JSON format" }
        val json = decoder.json

        val jsonObject = decoder.decodeJsonElement().jsonObject
        require(jsonObject.size == 1) { "proofs object must have exactly one key (the proof type)" }

        val (proofType, proofValuesElement) = jsonObject.entries.first()
        val proofValues = json.decodeFromJsonElement(ListSerializer(JsonElement.serializer()), proofValuesElement)

        return CredentialRequestProofs(
            proofType = proofType,
            proofValues = proofValues,
        )
    }
}
