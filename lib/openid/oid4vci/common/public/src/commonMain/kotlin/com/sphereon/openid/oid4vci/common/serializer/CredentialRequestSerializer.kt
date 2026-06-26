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
 */

package com.sphereon.openid.oid4vci.common.serializer

import com.sphereon.core.api.log.Log
import com.sphereon.openid.oid4vci.common.model.CredentialRequest
import com.sphereon.openid.oid4vci.common.model.CredentialRequestProofs
import com.sphereon.openid.oid4vci.common.model.RequestedCredentialResponseEncryption
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal object CredentialRequestSerializer : KSerializer<CredentialRequest> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("CredentialRequest")
    private val logger = Log.app().withTag("CredentialRequestSerializer")

    private const val KEY_CONFIG_ID = "credential_configuration_id"
    private const val KEY_CREDENTIAL_ID = "credential_identifier"
    private const val KEY_FORMAT = "format"
    private const val KEY_PROOF = "proof"
    private const val KEY_PROOFS = "proofs"
    private const val KEY_RESPONSE_ENCRYPTION = "credential_response_encryption"
    private const val KEY_VCT = "vct"
    private const val KEY_DOCTYPE = "doctype"

    private val knownJsonKeys =
        setOf(
            KEY_CONFIG_ID,
            KEY_CREDENTIAL_ID,
            KEY_FORMAT,
            KEY_PROOF,
            KEY_PROOFS,
            KEY_RESPONSE_ENCRYPTION,
            KEY_VCT,
            KEY_DOCTYPE,
        )

    override fun serialize(
        encoder: Encoder,
        value: CredentialRequest,
    ) {
        require(encoder is JsonEncoder) { "CredentialRequestSerializer only works with JSON format" }
        val json = encoder.json

        val jsonObject =
            buildJsonObject {
                value.credentialConfigurationId?.let { put(KEY_CONFIG_ID, JsonPrimitive(it)) }
                value.credentialIdentifier?.let { put(KEY_CREDENTIAL_ID, JsonPrimitive(it)) }
                value.format?.let { put(KEY_FORMAT, JsonPrimitive(it)) }
                value.proofs?.let {
                    put(KEY_PROOFS, json.encodeToJsonElement(CredentialRequestProofs.serializer(), it))
                }
                value.credentialResponseEncryption?.let {
                    put(KEY_RESPONSE_ENCRYPTION, json.encodeToJsonElement(RequestedCredentialResponseEncryption.serializer(), it))
                }
                value.vct?.let { put(KEY_VCT, JsonPrimitive(it)) }
                value.doctype?.let { put(KEY_DOCTYPE, JsonPrimitive(it)) }

                value.additionalParameters.forEach { (key, jsonValue) ->
                    put(key, jsonValue)
                }
            }

        encoder.encodeJsonElement(jsonObject)
    }

    override fun deserialize(decoder: Decoder): CredentialRequest {
        require(decoder is JsonDecoder) { "CredentialRequestSerializer only works with JSON format" }
        val json = decoder.json

        val jsonObject = decoder.decodeJsonElement().jsonObject

        // OID4VCI 1.0+ uses "proofs" (plural). Upgrade legacy "proof" (singular) with a warning.
        val proofs =
            jsonObject[KEY_PROOFS]?.let {
                json.decodeFromJsonElement(CredentialRequestProofs.serializer(), it)
            } ?: jsonObject[KEY_PROOF]?.let { proofElement ->
                logger.warn("Credential request uses deprecated singular 'proof' field. OID4VCI 1.0+ requires 'proofs' (plural). Upgrading automatically.")
                val proofObj = proofElement.jsonObject
                val proofType = proofObj["proof_type"]?.jsonPrimitive?.content ?: return@let null
                val proofValue = proofObj[proofType] ?: return@let null
                CredentialRequestProofs(proofType = proofType, proofValues = listOf(proofValue))
            }

        val additionalParameters = jsonObject.filterKeys { it !in knownJsonKeys }

        return CredentialRequest(
            credentialConfigurationId = jsonObject[KEY_CONFIG_ID]?.jsonPrimitive?.content,
            credentialIdentifier = jsonObject[KEY_CREDENTIAL_ID]?.jsonPrimitive?.content,
            format = jsonObject[KEY_FORMAT]?.jsonPrimitive?.content,
            proofs = proofs,
            credentialResponseEncryption =
                jsonObject[KEY_RESPONSE_ENCRYPTION]?.let {
                    json.decodeFromJsonElement(RequestedCredentialResponseEncryption.serializer(), it)
                },
            vct = jsonObject[KEY_VCT]?.jsonPrimitive?.content,
            doctype = jsonObject[KEY_DOCTYPE]?.jsonPrimitive?.content,
            additionalParameters = additionalParameters,
        )
    }
}
