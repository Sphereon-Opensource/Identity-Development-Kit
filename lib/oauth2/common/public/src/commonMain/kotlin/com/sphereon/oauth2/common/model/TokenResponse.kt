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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Custom serializer for TokenResponse that captures unknown extension parameters
 *
 * OAuth 2.0 responses frequently have extensions (OpenID4VCI, custom claims, etc.)
 * See: https://github.com/Kotlin/kotlinx.serialization/issues/1978
 */
internal object TokenResponseSerializer : KSerializer<TokenResponse> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("TokenResponse")

    private val knownJsonKeys =
        setOf(
            "access_token",
            "token_type",
            "expires_in",
            "refresh_token",
            "scope",
            "id_token",
            "c_nonce",
            "c_nonce_expires_in",
            "authorization_details",
            "dpop_nonce",
            "issued_token_type",
        )

    override fun serialize(
        encoder: Encoder,
        value: TokenResponse,
    ) {
        require(encoder is JsonEncoder) { "TokenResponseSerializer only works with JSON format" }

        val jsonObject =
            buildJsonObject {
                put("access_token", JsonPrimitive(value.accessToken))
                put("token_type", JsonPrimitive(value.tokenType))
                value.expiresIn?.let { put("expires_in", JsonPrimitive(it)) }
                value.refreshToken?.let { put("refresh_token", JsonPrimitive(it)) }
                value.scope?.let { put("scope", JsonPrimitive(it)) }
                value.idToken?.let { put("id_token", JsonPrimitive(it)) }
                value.cNonce?.let { put("c_nonce", JsonPrimitive(it)) }
                value.cNonceExpiresIn?.let { put("c_nonce_expires_in", JsonPrimitive(it)) }
                value.authorizationDetails?.let { put("authorization_details", it) }
                value.dpopNonce?.let { put("dpop_nonce", JsonPrimitive(it)) }
                value.issuedTokenType?.let { put("issued_token_type", JsonPrimitive(it)) }

                value.additionalParameters.forEach { (key, jsonValue) ->
                    put(key, jsonValue)
                }
            }

        encoder.encodeJsonElement(jsonObject)
    }

    override fun deserialize(decoder: Decoder): TokenResponse {
        require(decoder is JsonDecoder) { "TokenResponseSerializer only works with JSON format" }

        val jsonObject = decoder.decodeJsonElement().jsonObject
        val additionalParameters = jsonObject.filterKeys { it !in knownJsonKeys }

        return TokenResponse(
            accessToken =
                jsonObject["access_token"]?.jsonPrimitive?.content
                    ?: throw IllegalArgumentException("access_token is required"),
            tokenType =
                jsonObject["token_type"]?.jsonPrimitive?.content
                    ?: throw IllegalArgumentException("token_type is required"),
            expiresIn = jsonObject["expires_in"]?.jsonPrimitive?.content?.toInt(),
            refreshToken = jsonObject["refresh_token"]?.jsonPrimitive?.content,
            scope = jsonObject["scope"]?.jsonPrimitive?.content,
            idToken = jsonObject["id_token"]?.jsonPrimitive?.content,
            cNonce = jsonObject["c_nonce"]?.jsonPrimitive?.content,
            cNonceExpiresIn = jsonObject["c_nonce_expires_in"]?.jsonPrimitive?.content?.toInt(),
            authorizationDetails = jsonObject["authorization_details"],
            dpopNonce = jsonObject["dpop_nonce"]?.jsonPrimitive?.content,
            issuedTokenType = jsonObject["issued_token_type"]?.jsonPrimitive?.content,
            additionalParameters = additionalParameters,
        )
    }
}

/**
 * OAuth 2.0 Token Response (RFC 6749)
 *
 * Includes extensions for:
 * - OpenID Connect (id_token)
 * - OpenID4VCI (c_nonce, c_nonce_expires_in, authorization_details)
 * - DPoP (RFC 9449)
 * - Token Exchange (RFC 8693) — issued_token_type
 * - Custom extensions via additionalParameters
 */
@Serializable(with = TokenResponseSerializer::class)
@JsExportCompat
@Suppress("NON_EXPORTABLE_TYPE")
data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String,
    @SerialName("expires_in") val expiresIn: Int? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
    val scope: String? = null,
    // OpenID Connect
    @SerialName("id_token") val idToken: String? = null,
    // OpenID4VCI extensions
    @SerialName("c_nonce") val cNonce: String? = null,
    @SerialName("c_nonce_expires_in") val cNonceExpiresIn: Int? = null,
    @SerialName("authorization_details") val authorizationDetails: JsonElement? = null,
    // DPoP (RFC 9449)
    @SerialName("dpop_nonce") val dpopNonce: String? = null,
    // Token Exchange (RFC 8693)
    @SerialName("issued_token_type") val issuedTokenType: String? = null,
    // Additional extension parameters (auto-captured from unknown JSON fields)
    val additionalParameters: Map<String, JsonElement> = emptyMap(),
)

/**
 * OAuth 2.0 Token Error Response (RFC 6749 Section 5.2)
 */
@JsExportCompat
@Serializable
data class TokenErrorResponse(
    val error: String,
    @SerialName("error_description") val errorDescription: String? = null,
    @SerialName("error_uri") val errorUri: String? = null,
)

/**
 * Custom serializer for TokenRequest that captures unknown extension parameters
 */
internal object TokenRequestSerializer : KSerializer<TokenRequest> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("TokenRequest")

    private val knownJsonKeys =
        setOf(
            "grant_type",
            "code",
            "redirect_uri",
            "code_verifier",
            "refresh_token",
            "pre-authorized_code",
            "tx_code",
            "client_id",
            "client_secret",
            "client_assertion_type",
            "client_assertion",
            "scope",
            "resource",
            "dpop",
            "authorization_details",
            "audience",
            "subject_token",
            "subject_token_type",
            "actor_token",
            "actor_token_type",
            "requested_token_type",
        )

    override fun serialize(
        encoder: Encoder,
        value: TokenRequest,
    ) {
        require(encoder is JsonEncoder) { "TokenRequestSerializer only works with JSON format" }

        val jsonObject =
            buildJsonObject {
                put("grant_type", JsonPrimitive(value.grantType))
                value.code?.let { put("code", JsonPrimitive(it)) }
                value.redirectUri?.let { put("redirect_uri", JsonPrimitive(it)) }
                value.codeVerifier?.let { put("code_verifier", JsonPrimitive(it)) }
                value.refreshToken?.let { put("refresh_token", JsonPrimitive(it)) }
                value.preAuthorizedCode?.let { put("pre-authorized_code", JsonPrimitive(it)) }
                value.txCode?.let { put("tx_code", JsonPrimitive(it)) }
                value.clientId?.let { put("client_id", JsonPrimitive(it)) }
                value.clientSecret?.let { put("client_secret", JsonPrimitive(it)) }
                value.clientAssertionType?.let { put("client_assertion_type", JsonPrimitive(it)) }
                value.clientAssertion?.let { put("client_assertion", JsonPrimitive(it)) }
                value.scope?.let { put("scope", JsonPrimitive(it)) }
                if (value.resource.isNotEmpty()) {
                    put("resource", JsonArray(value.resource.map { JsonPrimitive(it) }))
                }
                if (value.audience.isNotEmpty()) {
                    put("audience", JsonArray(value.audience.map { JsonPrimitive(it) }))
                }
                value.dpop?.let { put("dpop", JsonPrimitive(it)) }
                value.authorizationDetails?.let { put("authorization_details", it) }

                // Token Exchange (RFC 8693) fields
                value.subjectToken?.let { put("subject_token", JsonPrimitive(it)) }
                value.subjectTokenType?.let { put("subject_token_type", JsonPrimitive(it)) }
                value.actorToken?.let { put("actor_token", JsonPrimitive(it)) }
                value.actorTokenType?.let { put("actor_token_type", JsonPrimitive(it)) }
                value.requestedTokenType?.let { put("requested_token_type", JsonPrimitive(it)) }

                value.additionalParameters.forEach { (key, jsonValue) ->
                    put(key, jsonValue)
                }
            }

        encoder.encodeJsonElement(jsonObject)
    }

    override fun deserialize(decoder: Decoder): TokenRequest {
        require(decoder is JsonDecoder) { "TokenRequestSerializer only works with JSON format" }

        val jsonObject = decoder.decodeJsonElement().jsonObject
        val additionalParameters = jsonObject.filterKeys { it !in knownJsonKeys }

        // resource can be a single string or an array
        val resource =
            when (val resourceElement = jsonObject["resource"]) {
                is JsonArray -> resourceElement.map { it.jsonPrimitive.content }
                is JsonPrimitive -> listOf(resourceElement.content)
                else -> emptyList()
            }

        // audience can be a single string or an array
        val audience =
            when (val audienceElement = jsonObject["audience"]) {
                is JsonArray -> audienceElement.map { it.jsonPrimitive.content }
                is JsonPrimitive -> listOf(audienceElement.content)
                else -> emptyList()
            }

        return TokenRequest(
            grantType =
                jsonObject["grant_type"]?.jsonPrimitive?.content
                    ?: throw IllegalArgumentException("grant_type is required"),
            code = jsonObject["code"]?.jsonPrimitive?.content,
            redirectUri = jsonObject["redirect_uri"]?.jsonPrimitive?.content,
            codeVerifier = jsonObject["code_verifier"]?.jsonPrimitive?.content,
            refreshToken = jsonObject["refresh_token"]?.jsonPrimitive?.content,
            preAuthorizedCode = jsonObject["pre-authorized_code"]?.jsonPrimitive?.content,
            txCode = jsonObject["tx_code"]?.jsonPrimitive?.content,
            clientId = jsonObject["client_id"]?.jsonPrimitive?.content,
            clientSecret = jsonObject["client_secret"]?.jsonPrimitive?.content,
            clientAssertionType = jsonObject["client_assertion_type"]?.jsonPrimitive?.content,
            clientAssertion = jsonObject["client_assertion"]?.jsonPrimitive?.content,
            scope = jsonObject["scope"]?.jsonPrimitive?.content,
            resource = resource,
            audience = audience,
            dpop = jsonObject["dpop"]?.jsonPrimitive?.content,
            authorizationDetails = jsonObject["authorization_details"],
            subjectToken = jsonObject["subject_token"]?.jsonPrimitive?.content,
            subjectTokenType = jsonObject["subject_token_type"]?.jsonPrimitive?.content,
            actorToken = jsonObject["actor_token"]?.jsonPrimitive?.content,
            actorTokenType = jsonObject["actor_token_type"]?.jsonPrimitive?.content,
            requestedTokenType = jsonObject["requested_token_type"]?.jsonPrimitive?.content,
            additionalParameters = additionalParameters,
        )
    }
}

/**
 * OAuth 2.0 Token Request (RFC 6749)
 *
 * Supports grant types:
 * - authorization_code (RFC 6749 Section 4.1.3)
 * - refresh_token (RFC 6749 Section 6)
 * - client_credentials (RFC 6749 Section 4.4.2)
 * - urn:ietf:params:oauth:grant-type:pre-authorized_code (OpenID4VCI)
 * - urn:ietf:params:oauth:grant-type:token-exchange (RFC 8693)
 *
 * Includes extensions for:
 * - PKCE (RFC 7636)
 * - Resource Indicators (RFC 8707)
 * - DPoP (RFC 9449)
 * - Token Exchange (RFC 8693)
 * - OpenID4VCI
 * - Custom extensions via additionalParameters
 */
@Serializable(with = TokenRequestSerializer::class)
@JsExportCompat
@Suppress("NON_EXPORTABLE_TYPE")
data class TokenRequest(
    @SerialName("grant_type") val grantType: String,
    // authorization_code grant
    val code: String? = null,
    @SerialName("redirect_uri") val redirectUri: String? = null,
    @SerialName("code_verifier") val codeVerifier: String? = null,
    // refresh_token grant
    @SerialName("refresh_token") val refreshToken: String? = null,
    // client_credentials grant (no additional parameters)
    // pre-authorized_code grant (OpenID4VCI)
    @SerialName("pre-authorized_code") val preAuthorizedCode: String? = null,
    @SerialName("tx_code") val txCode: String? = null,
    // Client authentication
    @SerialName("client_id") val clientId: String? = null,
    @SerialName("client_secret") val clientSecret: String? = null,
    @SerialName("client_assertion_type") val clientAssertionType: String? = null,
    @SerialName("client_assertion") val clientAssertion: String? = null,
    // Common parameters
    val scope: String? = null,
    val resource: List<String> = emptyList(), // RFC 8707 + RFC 8693 (multi-value)
    val audience: List<String> = emptyList(), // RFC 8693 (multi-value)
    // DPoP (RFC 9449)
    val dpop: String? = null,
    // OpenID4VCI extensions
    @SerialName("authorization_details") val authorizationDetails: JsonElement? = null,
    // Token Exchange (RFC 8693)
    @SerialName("subject_token") val subjectToken: String? = null,
    @SerialName("subject_token_type") val subjectTokenType: String? = null,
    @SerialName("actor_token") val actorToken: String? = null,
    @SerialName("actor_token_type") val actorTokenType: String? = null,
    @SerialName("requested_token_type") val requestedTokenType: String? = null,
    // Additional extension parameters (auto-captured from unknown JSON fields)
    val additionalParameters: Map<String, JsonElement> = emptyMap(),
    /**
     * Selects the OAuth 2.0 client authentication method applied to this token request.
     *
     * `null` lets the exchange command pick the legacy default (body-only client_secret_post).
     * Set explicitly to [ClientAuthenticationMethod.CLIENT_SECRET_BASIC] to force HTTP Basic
     * (RFC 6749 Section 2.3.1) on the token endpoint, matching OIDF conformance suites that
     * require `Authorization: Basic` and forbid body credentials.
     *
     * Not serialized over the wire (the custom [TokenRequestSerializer] omits it); this is a
     * client-side directive for the exchange command only.
     */
    val tokenEndpointAuthMethod: ClientAuthenticationMethod? = null,
)
