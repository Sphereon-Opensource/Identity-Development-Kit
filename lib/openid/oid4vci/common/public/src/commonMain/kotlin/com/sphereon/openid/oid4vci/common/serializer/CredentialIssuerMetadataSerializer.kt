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
import com.sphereon.openid.oid4vci.common.model.BatchCredentialIssuance
import com.sphereon.openid.oid4vci.common.model.CredentialConfigurationSupported
import com.sphereon.openid.oid4vci.common.model.CredentialIssuerMetadata
import com.sphereon.openid.oid4vci.common.model.MetadataCredentialRequestEncryption
import com.sphereon.openid.oid4vci.common.model.MetadataCredentialResponseEncryption
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
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

internal object CredentialIssuerMetadataSerializer : KSerializer<CredentialIssuerMetadata> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("CredentialIssuerMetadata")

    private const val KEY_CREDENTIAL_ISSUER = "credential_issuer"
    private const val KEY_AUTH_SERVERS = "authorization_servers"
    private const val KEY_CREDENTIAL_ENDPOINT = "credential_endpoint"
    private const val KEY_BATCH_ENDPOINT = "batch_credential_endpoint"
    private const val KEY_DEFERRED_ENDPOINT = "deferred_credential_endpoint"
    private const val KEY_NOTIFICATION_ENDPOINT = "notification_endpoint"
    private const val KEY_NONCE_ENDPOINT = "nonce_endpoint"
    private const val KEY_CONFIGS_SUPPORTED = "credential_configurations_supported"
    private const val KEY_SIGNED_METADATA = "signed_metadata"
    private const val KEY_DISPLAY = "display"
    private const val KEY_RESPONSE_ENCRYPTION = "credential_response_encryption"
    private const val KEY_REQUEST_ENCRYPTION = "credential_request_encryption"
    private const val KEY_BATCH_ISSUANCE = "batch_credential_issuance"

    private val knownJsonKeys =
        setOf(
            KEY_CREDENTIAL_ISSUER,
            KEY_AUTH_SERVERS,
            KEY_CREDENTIAL_ENDPOINT,
            KEY_BATCH_ENDPOINT,
            KEY_DEFERRED_ENDPOINT,
            KEY_NOTIFICATION_ENDPOINT,
            KEY_NONCE_ENDPOINT,
            KEY_CONFIGS_SUPPORTED,
            KEY_SIGNED_METADATA,
            KEY_DISPLAY,
            KEY_RESPONSE_ENCRYPTION,
            KEY_REQUEST_ENCRYPTION,
            KEY_BATCH_ISSUANCE,
        )

    override fun serialize(
        encoder: Encoder,
        value: CredentialIssuerMetadata,
    ) {
        require(encoder is JsonEncoder) { "CredentialIssuerMetadataSerializer only works with JSON format" }
        val json = encoder.json

        val jsonObject =
            buildJsonObject {
                put(KEY_CREDENTIAL_ISSUER, JsonPrimitive(value.credentialIssuer))
                value.authorizationServers?.let {
                    put(KEY_AUTH_SERVERS, json.encodeToJsonElement(ListSerializer(String.serializer()), it))
                }
                put(KEY_CREDENTIAL_ENDPOINT, JsonPrimitive(value.credentialEndpoint))
                value.batchCredentialEndpoint?.let { put(KEY_BATCH_ENDPOINT, JsonPrimitive(it)) }
                value.deferredCredentialEndpoint?.let { put(KEY_DEFERRED_ENDPOINT, JsonPrimitive(it)) }
                value.notificationEndpoint?.let { put(KEY_NOTIFICATION_ENDPOINT, JsonPrimitive(it)) }
                value.nonceEndpoint?.let { put(KEY_NONCE_ENDPOINT, JsonPrimitive(it)) }
                put(
                    KEY_CONFIGS_SUPPORTED,
                    buildJsonObject {
                        value.credentialConfigurationsSupported.forEach { (k, v) ->
                            put(k, json.encodeToJsonElement(CredentialConfigurationSupported.serializer(), v))
                        }
                    },
                )
                value.signedMetadata?.let { put(KEY_SIGNED_METADATA, JsonPrimitive(it)) }
                value.display?.let {
                    put(KEY_DISPLAY, json.encodeToJsonElement(ListSerializer(DisplayProperties.serializer()), it))
                }
                value.credentialResponseEncryption?.let {
                    put(KEY_RESPONSE_ENCRYPTION, json.encodeToJsonElement(MetadataCredentialResponseEncryption.serializer(), it))
                }
                value.credentialRequestEncryption?.let {
                    put(KEY_REQUEST_ENCRYPTION, json.encodeToJsonElement(MetadataCredentialRequestEncryption.serializer(), it))
                }
                value.batchCredentialIssuance?.let {
                    put(KEY_BATCH_ISSUANCE, json.encodeToJsonElement(BatchCredentialIssuance.serializer(), it))
                }

                value.additionalMetadata.forEach { (key, jsonValue) ->
                    put(key, jsonValue)
                }
            }

        encoder.encodeJsonElement(jsonObject)
    }

    override fun deserialize(decoder: Decoder): CredentialIssuerMetadata {
        require(decoder is JsonDecoder) { "CredentialIssuerMetadataSerializer only works with JSON format" }
        val json = decoder.json

        val jsonObject = decoder.decodeJsonElement().jsonObject
        val additionalMetadata = jsonObject.filterKeys { it !in knownJsonKeys }

        val configsObj =
            jsonObject[KEY_CONFIGS_SUPPORTED]?.jsonObject
                ?: throw IllegalArgumentException("credential_configurations_supported is required")
        val configs =
            configsObj.mapValues { (_, v) ->
                json.decodeFromJsonElement(CredentialConfigurationSupported.serializer(), v)
            }

        return CredentialIssuerMetadata(
            credentialIssuer =
                jsonObject[KEY_CREDENTIAL_ISSUER]?.jsonPrimitive?.content
                    ?: throw IllegalArgumentException("credential_issuer is required"),
            authorizationServers =
                jsonObject[KEY_AUTH_SERVERS]?.let {
                    json.decodeFromJsonElement(ListSerializer(String.serializer()), it)
                },
            credentialEndpoint =
                jsonObject[KEY_CREDENTIAL_ENDPOINT]?.jsonPrimitive?.content
                    ?: throw IllegalArgumentException("credential_endpoint is required"),
            batchCredentialEndpoint = jsonObject[KEY_BATCH_ENDPOINT]?.jsonPrimitive?.content,
            deferredCredentialEndpoint = jsonObject[KEY_DEFERRED_ENDPOINT]?.jsonPrimitive?.content,
            notificationEndpoint = jsonObject[KEY_NOTIFICATION_ENDPOINT]?.jsonPrimitive?.content,
            nonceEndpoint = jsonObject[KEY_NONCE_ENDPOINT]?.jsonPrimitive?.content,
            credentialConfigurationsSupported = configs,
            signedMetadata = jsonObject[KEY_SIGNED_METADATA]?.jsonPrimitive?.content,
            display =
                jsonObject[KEY_DISPLAY]?.let {
                    json.decodeFromJsonElement(ListSerializer(DisplayProperties.serializer()), it)
                },
            credentialResponseEncryption =
                jsonObject[KEY_RESPONSE_ENCRYPTION]?.let {
                    json.decodeFromJsonElement(MetadataCredentialResponseEncryption.serializer(), it)
                },
            credentialRequestEncryption =
                jsonObject[KEY_REQUEST_ENCRYPTION]?.let {
                    json.decodeFromJsonElement(MetadataCredentialRequestEncryption.serializer(), it)
                },
            batchCredentialIssuance =
                jsonObject[KEY_BATCH_ISSUANCE]?.let {
                    json.decodeFromJsonElement(BatchCredentialIssuance.serializer(), it)
                },
            additionalMetadata = additionalMetadata,
        )
    }
}
