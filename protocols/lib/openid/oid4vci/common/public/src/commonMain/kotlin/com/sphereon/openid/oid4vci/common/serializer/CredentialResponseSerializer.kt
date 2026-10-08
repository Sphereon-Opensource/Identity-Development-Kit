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

import com.sphereon.openid.oid4vci.common.model.CredentialResponse
import com.sphereon.openid.oid4vci.common.model.CredentialResponseItem
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Custom serializer for CredentialResponse that captures unknown extension parameters
 *
 * OID4VCI credential responses may include extensions beyond the core specification.
 * See: https://github.com/Kotlin/kotlinx.serialization/issues/1978
 */
internal object CredentialResponseSerializer : KSerializer<CredentialResponse> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("CredentialResponse")

    private const val KEY_CREDENTIALS = "credentials"
    private const val KEY_TRANSACTION_ID = "transaction_id"
    private const val KEY_NOTIFICATION_ID = "notification_id"
    private const val KEY_INTERVAL = "interval"

    private val knownJsonKeys =
        setOf(
            KEY_CREDENTIALS,
            KEY_TRANSACTION_ID,
            KEY_NOTIFICATION_ID,
            KEY_INTERVAL,
        )

    override fun serialize(
        encoder: Encoder,
        value: CredentialResponse,
    ) {
        require(encoder is JsonEncoder) { "CredentialResponseSerializer only works with JSON format" }
        val json = encoder.json

        val jsonObject =
            buildJsonObject {
                value.credentials?.let {
                    put(KEY_CREDENTIALS, json.encodeToJsonElement(ListSerializer(CredentialResponseItem.serializer()), it))
                }
                value.transactionId?.let { put(KEY_TRANSACTION_ID, JsonPrimitive(it)) }
                value.notificationId?.let { put(KEY_NOTIFICATION_ID, JsonPrimitive(it)) }
                value.interval?.let { put(KEY_INTERVAL, JsonPrimitive(it)) }

                value.additionalParameters.forEach { (key, jsonValue) ->
                    put(key, jsonValue)
                }
            }

        encoder.encodeJsonElement(jsonObject)
    }

    override fun deserialize(decoder: Decoder): CredentialResponse {
        require(decoder is JsonDecoder) { "CredentialResponseSerializer only works with JSON format" }
        val json = decoder.json

        val jsonObject = decoder.decodeJsonElement().jsonObject
        val additionalParameters = jsonObject.filterKeys { it !in knownJsonKeys }

        return CredentialResponse(
            credentials =
                jsonObject[KEY_CREDENTIALS]?.let {
                    json.decodeFromJsonElement(ListSerializer(CredentialResponseItem.serializer()), it)
                },
            transactionId = jsonObject[KEY_TRANSACTION_ID]?.jsonPrimitive?.content,
            notificationId = jsonObject[KEY_NOTIFICATION_ID]?.jsonPrimitive?.content,
            interval = jsonObject[KEY_INTERVAL]?.jsonPrimitive?.intOrNull,
            additionalParameters = additionalParameters,
        )
    }
}
