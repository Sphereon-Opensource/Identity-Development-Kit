/*
 * Copyright 2023-2026 Sphereon International B.V.
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

package com.sphereon.oauth2.common.model

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
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
 * Custom serializer for PushedAuthorizationRequest
 */
internal object PushedAuthorizationRequestSerializer : KSerializer<PushedAuthorizationRequest> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("PushedAuthorizationRequest")

    private val knownJsonKeys = setOf("request_uri", "client_id")

    override fun serialize(
        encoder: Encoder,
        value: PushedAuthorizationRequest,
    ) {
        require(encoder is JsonEncoder)

        val jsonObject =
            buildJsonObject {
                put("request_uri", JsonPrimitive(value.requestUri))
                put("client_id", JsonPrimitive(value.clientId))
                value.additionalParameters.forEach { (key, jsonValue) -> put(key, jsonValue) }
            }

        encoder.encodeJsonElement(jsonObject)
    }

    override fun deserialize(decoder: Decoder): PushedAuthorizationRequest {
        require(decoder is JsonDecoder)

        val jsonObject = decoder.decodeJsonElement().jsonObject
        val additionalParameters = jsonObject.filterKeys { it !in knownJsonKeys }

        return PushedAuthorizationRequest(
            requestUri =
                jsonObject["request_uri"]?.jsonPrimitive?.content
                    ?: throw IllegalArgumentException("request_uri is required"),
            clientId =
                jsonObject["client_id"]?.jsonPrimitive?.content
                    ?: throw IllegalArgumentException("client_id is required"),
            additionalParameters = additionalParameters,
        )
    }
}

/**
 * Custom serializer for PushedAuthorizationResponse
 */
internal object PushedAuthorizationResponseSerializer : KSerializer<PushedAuthorizationResponse> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("PushedAuthorizationResponse")

    private val knownJsonKeys = setOf("request_uri", "expires_in")

    override fun serialize(
        encoder: Encoder,
        value: PushedAuthorizationResponse,
    ) {
        require(encoder is JsonEncoder)

        val jsonObject =
            buildJsonObject {
                put("request_uri", JsonPrimitive(value.requestUri))
                put("expires_in", JsonPrimitive(value.expiresIn))
                value.additionalParameters.forEach { (key, jsonValue) -> put(key, jsonValue) }
            }

        encoder.encodeJsonElement(jsonObject)
    }

    override fun deserialize(decoder: Decoder): PushedAuthorizationResponse {
        require(decoder is JsonDecoder)

        val jsonObject = decoder.decodeJsonElement().jsonObject
        val additionalParameters = jsonObject.filterKeys { it !in knownJsonKeys }

        return PushedAuthorizationResponse(
            requestUri =
                jsonObject["request_uri"]?.jsonPrimitive?.content
                    ?: throw IllegalArgumentException("request_uri is required"),
            expiresIn =
                jsonObject["expires_in"]?.jsonPrimitive?.content?.toInt()
                    ?: throw IllegalArgumentException("expires_in is required"),
            additionalParameters = additionalParameters,
        )
    }
}

/**
 * Custom serializer for AuthorizationResponse
 */
internal object AuthorizationResponseSerializer : KSerializer<AuthorizationResponse> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("AuthorizationResponse")

    private val knownJsonKeys = setOf("code", "state")

    override fun serialize(
        encoder: Encoder,
        value: AuthorizationResponse,
    ) {
        require(encoder is JsonEncoder)

        val jsonObject =
            buildJsonObject {
                put("code", JsonPrimitive(value.code))
                value.state?.let { put("state", JsonPrimitive(it)) }
                value.additionalParameters.forEach { (key, jsonValue) -> put(key, jsonValue) }
            }

        encoder.encodeJsonElement(jsonObject)
    }

    override fun deserialize(decoder: Decoder): AuthorizationResponse {
        require(decoder is JsonDecoder)

        val jsonObject = decoder.decodeJsonElement().jsonObject
        val additionalParameters = jsonObject.filterKeys { it !in knownJsonKeys }

        return AuthorizationResponse(
            code =
                jsonObject["code"]?.jsonPrimitive?.content
                    ?: throw IllegalArgumentException("code is required"),
            state = jsonObject["state"]?.jsonPrimitive?.content,
            additionalParameters = additionalParameters,
        )
    }
}

/**
 * Custom serializer for AuthorizationErrorResponse
 */
internal object AuthorizationErrorResponseSerializer : KSerializer<AuthorizationErrorResponse> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("AuthorizationErrorResponse")

    private val knownJsonKeys = setOf("error", "error_description", "error_uri", "state")

    override fun serialize(
        encoder: Encoder,
        value: AuthorizationErrorResponse,
    ) {
        require(encoder is JsonEncoder)

        val jsonObject =
            buildJsonObject {
                put("error", JsonPrimitive(value.error))
                value.errorDescription?.let { put("error_description", JsonPrimitive(it)) }
                value.errorUri?.let { put("error_uri", JsonPrimitive(it)) }
                value.state?.let { put("state", JsonPrimitive(it)) }
                value.additionalParameters.forEach { (key, jsonValue) -> put(key, jsonValue) }
            }

        encoder.encodeJsonElement(jsonObject)
    }

    override fun deserialize(decoder: Decoder): AuthorizationErrorResponse {
        require(decoder is JsonDecoder)

        val jsonObject = decoder.decodeJsonElement().jsonObject
        val additionalParameters = jsonObject.filterKeys { it !in knownJsonKeys }

        return AuthorizationErrorResponse(
            error =
                jsonObject["error"]?.jsonPrimitive?.content
                    ?: throw IllegalArgumentException("error is required"),
            errorDescription = jsonObject["error_description"]?.jsonPrimitive?.content,
            errorUri = jsonObject["error_uri"]?.jsonPrimitive?.content,
            state = jsonObject["state"]?.jsonPrimitive?.content,
            additionalParameters = additionalParameters,
        )
    }
}

/**
 * Pushed Authorization Request (PAR) (RFC 9126)
 *
 * Contains only the request_uri returned from PAR endpoint
 *
 * @property requestUri REQUIRED. URI reference returned from PAR endpoint
 * @property clientId REQUIRED. Client identifier
 * @property additionalParameters Additional extension parameters (auto-captured)
 */
@Serializable(with = PushedAuthorizationRequestSerializer::class)
data class PushedAuthorizationRequest(
    @SerialName("request_uri")
    val requestUri: String,
    @SerialName("client_id")
    val clientId: String,
    val additionalParameters: Map<String, JsonElement> = emptyMap(),
)

/**
 * Pushed Authorization Response (PAR) (RFC 9126)
 *
 * Response from the pushed authorization request endpoint
 *
 * @property requestUri REQUIRED. Request URI to use in authorization request
 * @property expiresIn REQUIRED. Lifetime in seconds of the request_uri
 * @property additionalParameters Additional extension parameters (auto-captured)
 */
@Serializable(with = PushedAuthorizationResponseSerializer::class)
data class PushedAuthorizationResponse(
    @SerialName("request_uri")
    val requestUri: String,
    @SerialName("expires_in")
    val expiresIn: Int,
    val additionalParameters: Map<String, JsonElement> = emptyMap(),
)

/**
 * OAuth 2.0 Authorization Response - Success (RFC 6749 Section 4.1.2)
 *
 * @property code REQUIRED. Authorization code
 * @property state OPTIONAL. State value from authorization request
 * @property additionalParameters Additional extension parameters (auto-captured)
 */
@Serializable(with = AuthorizationResponseSerializer::class)
@JsExportCompat
@Suppress("NON_EXPORTABLE_TYPE")
data class AuthorizationResponse(
    val code: String,
    val state: String? = null,
    val additionalParameters: Map<String, JsonElement> = emptyMap(),
)

/**
 * OAuth 2.0 Authorization Response - Error (RFC 6749 Section 4.1.2.1)
 *
 * @property error REQUIRED. Error code
 * @property errorDescription OPTIONAL. Human-readable error description
 * @property errorUri OPTIONAL. URI identifying human-readable error information
 * @property state OPTIONAL. State value from authorization request
 * @property additionalParameters Additional extension parameters (auto-captured)
 */
@Serializable(with = AuthorizationErrorResponseSerializer::class)
data class AuthorizationErrorResponse(
    val error: String,
    @SerialName("error_description")
    val errorDescription: String? = null,
    @SerialName("error_uri")
    val errorUri: String? = null,
    val state: String? = null,
    val additionalParameters: Map<String, JsonElement> = emptyMap(),
)
