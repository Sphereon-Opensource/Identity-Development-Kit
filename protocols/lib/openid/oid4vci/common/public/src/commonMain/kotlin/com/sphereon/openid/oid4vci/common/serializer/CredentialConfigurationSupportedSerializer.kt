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

import com.sphereon.openid.oid4vc.common.DisplayProperties
import com.sphereon.openid.oid4vci.common.model.CredentialClaim
import com.sphereon.openid.oid4vci.common.model.CredentialConfigurationSupported
import com.sphereon.openid.oid4vci.common.model.CredentialDefinition
import com.sphereon.openid.oid4vci.common.model.CredentialMetadata
import com.sphereon.openid.oid4vci.common.model.CredentialResponseEncryption
import com.sphereon.openid.oid4vci.common.model.ProofTypeSupported
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Custom serializer for CredentialConfigurationSupported that captures unknown extension parameters
 *
 * OID4VCI credential configuration metadata frequently has format-specific extensions.
 * See: https://github.com/Kotlin/kotlinx.serialization/issues/1978
 */
internal object CredentialConfigurationSupportedSerializer : KSerializer<CredentialConfigurationSupported> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("CredentialConfigurationSupported")

    private const val KEY_FORMAT = "format"
    private const val KEY_SCOPE = "scope"
    private const val KEY_CRYPTO_BINDING = "cryptographic_binding_methods_supported"
    private const val KEY_SIGNING_ALG = "credential_signing_alg_values_supported"
    private const val KEY_PROOF_TYPES = "proof_types_supported"
    private const val KEY_DISPLAY = "display"
    private const val KEY_CREDENTIAL_DEFINITION = "credential_definition"
    private const val KEY_VCT = "vct"
    private const val KEY_CLAIMS = "claims"
    private const val KEY_DOCTYPE = "doctype"
    private const val KEY_ORDER = "order"
    private const val KEY_RESPONSE_ENCRYPTION = "credential_response_encryption"
    private const val KEY_CREDENTIAL_METADATA = "credential_metadata"

    private val knownJsonKeys =
        setOf(
            KEY_FORMAT,
            KEY_SCOPE,
            KEY_CRYPTO_BINDING,
            KEY_SIGNING_ALG,
            KEY_PROOF_TYPES,
            KEY_DISPLAY,
            KEY_CREDENTIAL_DEFINITION,
            KEY_VCT,
            KEY_CLAIMS,
            KEY_DOCTYPE,
            KEY_ORDER,
            KEY_RESPONSE_ENCRYPTION,
            KEY_CREDENTIAL_METADATA,
        )

    override fun serialize(
        encoder: Encoder,
        value: CredentialConfigurationSupported,
    ) {
        require(encoder is JsonEncoder) { "CredentialConfigurationSupportedSerializer only works with JSON format" }
        val json = encoder.json

        val jsonObject =
            buildJsonObject {
                put(KEY_FORMAT, JsonPrimitive(value.format))
                value.scope?.let { put(KEY_SCOPE, JsonPrimitive(it)) }
                value.cryptographicBindingMethodsSupported?.let {
                    put(KEY_CRYPTO_BINDING, json.encodeToJsonElement(ListSerializer(String.serializer()), it))
                }
                value.credentialSigningAlgValuesSupported?.let {
                    put(KEY_SIGNING_ALG, json.encodeToJsonElement(ListSerializer(JsonElement.serializer()), it))
                }
                value.proofTypesSupported?.let {
                    put(KEY_PROOF_TYPES, json.encodeToJsonElement(MapSerializer(String.serializer(), ProofTypeSupported.serializer()), it))
                }
                value.display?.let {
                    put(KEY_DISPLAY, json.encodeToJsonElement(ListSerializer(DisplayProperties.serializer()), it))
                }
                value.credentialDefinition?.let {
                    put(KEY_CREDENTIAL_DEFINITION, json.encodeToJsonElement(CredentialDefinition.serializer(), it))
                }
                value.vct?.let { put(KEY_VCT, JsonPrimitive(it)) }
                value.claims?.let {
                    put(KEY_CLAIMS, json.encodeToJsonElement(ListSerializer(CredentialClaim.serializer()), it))
                }
                value.doctype?.let { put(KEY_DOCTYPE, JsonPrimitive(it)) }
                value.order?.let {
                    put(KEY_ORDER, json.encodeToJsonElement(ListSerializer(String.serializer()), it))
                }
                value.credentialResponseEncryption?.let {
                    put(KEY_RESPONSE_ENCRYPTION, json.encodeToJsonElement(CredentialResponseEncryption.serializer(), it))
                }
                value.credentialMetadata?.let {
                    put(KEY_CREDENTIAL_METADATA, json.encodeToJsonElement(CredentialMetadata.serializer(), it))
                }

                value.additionalParameters.forEach { (key, jsonValue) ->
                    put(key, jsonValue)
                }
            }

        encoder.encodeJsonElement(jsonObject)
    }

    override fun deserialize(decoder: Decoder): CredentialConfigurationSupported {
        require(decoder is JsonDecoder) { "CredentialConfigurationSupportedSerializer only works with JSON format" }
        val json = decoder.json

        val jsonObject = decoder.decodeJsonElement().jsonObject
        val additionalParameters = jsonObject.filterKeys { it !in knownJsonKeys }

        return CredentialConfigurationSupported(
            format =
                jsonObject[KEY_FORMAT]?.jsonPrimitive?.content
                    ?: throw IllegalArgumentException("format is required"),
            scope = jsonObject[KEY_SCOPE]?.jsonPrimitive?.content,
            cryptographicBindingMethodsSupported =
                jsonObject[KEY_CRYPTO_BINDING]?.let {
                    json.decodeFromJsonElement(ListSerializer(String.serializer()), it)
                },
            credentialSigningAlgValuesSupported =
                jsonObject[KEY_SIGNING_ALG]?.let {
                    json.decodeFromJsonElement(ListSerializer(JsonElement.serializer()), it)
                },
            proofTypesSupported =
                jsonObject[KEY_PROOF_TYPES]?.let {
                    json.decodeFromJsonElement(MapSerializer(String.serializer(), ProofTypeSupported.serializer()), it)
                },
            display =
                jsonObject[KEY_DISPLAY]?.let {
                    json.decodeFromJsonElement(ListSerializer(DisplayProperties.serializer()), it)
                },
            credentialDefinition =
                jsonObject[KEY_CREDENTIAL_DEFINITION]?.let {
                    json.decodeFromJsonElement(CredentialDefinition.serializer(), it)
                },
            vct = jsonObject[KEY_VCT]?.jsonPrimitive?.content,
            claims =
                jsonObject[KEY_CLAIMS]?.let {
                    json.decodeFromJsonElement(ListSerializer(CredentialClaim.serializer()), it)
                },
            doctype = jsonObject[KEY_DOCTYPE]?.jsonPrimitive?.content,
            order =
                jsonObject[KEY_ORDER]?.let {
                    json.decodeFromJsonElement(ListSerializer(String.serializer()), it)
                },
            credentialResponseEncryption =
                jsonObject[KEY_RESPONSE_ENCRYPTION]?.let {
                    json.decodeFromJsonElement(CredentialResponseEncryption.serializer(), it)
                },
            credentialMetadata =
                jsonObject[KEY_CREDENTIAL_METADATA]?.let {
                    json.decodeFromJsonElement(CredentialMetadata.serializer(), it)
                },
            additionalParameters = additionalParameters,
        )
    }
}
