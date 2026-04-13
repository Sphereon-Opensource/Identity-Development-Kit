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

package com.sphereon.oauth2.common.model

import com.sphereon.crypto.core.jose.JwkSet
import io.konform.validation.Validation
import io.konform.validation.jsonschema.minItems
import io.konform.validation.jsonschema.minLength
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
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

internal object ClientRegistrationSerializer : KSerializer<ClientRegistration> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("ClientRegistration")

    private val knownJsonKeys =
        setOf(
            "client_id",
            "client_secret",
            "client_name",
            "client_uri",
            "logo_uri",
            "client_type",
            "grant_types",
            "response_types",
            "redirect_uris",
            "allowed_scopes",
            "scope",
            "token_endpoint_auth_method",
            "jwks",
            "jwks_uri",
            "contacts",
            "tos_uri",
            "policy_uri",
            "software_id",
            "software_version",
            "software_statement",
            "require_pkce",
            "require_pushed_authorization_requests",
            "dpop_bound_access_tokens",
            "access_token_lifetime",
            "refresh_token_lifetime",
            "authorization_code_lifetime",
        )

    override fun serialize(
        encoder: Encoder,
        value: ClientRegistration,
    ) {
        require(encoder is JsonEncoder) { "ClientRegistrationSerializer only works with JSON format" }

        val jsonObject =
            buildJsonObject {
                put("client_id", JsonPrimitive(value.clientId))
                value.clientSecret?.let { put("client_secret", JsonPrimitive(it)) }
                value.clientName?.let { put("client_name", JsonPrimitive(it)) }
                value.clientUri?.let { put("client_uri", JsonPrimitive(it)) }
                value.logoUri?.let { put("logo_uri", JsonPrimitive(it)) }
                put("client_type", JsonPrimitive(value.clientType.name))
                put("grant_types", encoder.json.encodeToJsonElement(value.grantTypes))
                put("response_types", encoder.json.encodeToJsonElement(value.responseTypes))
                put("redirect_uris", encoder.json.encodeToJsonElement(value.redirectUris))
                value.allowedScopes?.let { put("allowed_scopes", encoder.json.encodeToJsonElement(it)) }
                value.scope?.let { put("scope", JsonPrimitive(it)) }
                put("token_endpoint_auth_method", JsonPrimitive(value.tokenEndpointAuthMethod.name))
                value.jwks?.let { put("jwks", encoder.json.encodeToJsonElement(it)) }
                value.jwksUri?.let { put("jwks_uri", JsonPrimitive(it)) }
                value.contacts?.let { put("contacts", encoder.json.encodeToJsonElement(it)) }
                value.tosUri?.let { put("tos_uri", JsonPrimitive(it)) }
                value.policyUri?.let { put("policy_uri", JsonPrimitive(it)) }
                value.softwareId?.let { put("software_id", JsonPrimitive(it)) }
                value.softwareVersion?.let { put("software_version", JsonPrimitive(it)) }
                value.softwareStatement?.let { put("software_statement", JsonPrimitive(it)) }
                put("require_pkce", JsonPrimitive(value.requirePkce))
                put("require_pushed_authorization_requests", JsonPrimitive(value.requirePushedAuthorizationRequests))
                put("dpop_bound_access_tokens", JsonPrimitive(value.dpopBoundAccessTokens))
                put("access_token_lifetime", JsonPrimitive(value.accessTokenLifetime))
                value.refreshTokenLifetime?.let { put("refresh_token_lifetime", JsonPrimitive(it)) }
                put("authorization_code_lifetime", JsonPrimitive(value.authorizationCodeLifetime))

                value.additionalParameters.forEach { (key, jsonValue) ->
                    put(key, jsonValue)
                }
            }

        encoder.encodeJsonElement(jsonObject)
    }

    override fun deserialize(decoder: Decoder): ClientRegistration {
        require(decoder is JsonDecoder) { "ClientRegistrationSerializer only works with JSON format" }

        val jsonObject = decoder.decodeJsonElement().jsonObject
        val additionalParameters = jsonObject.filterKeys { it !in knownJsonKeys }

        return ClientRegistration(
            clientId =
                jsonObject["client_id"]?.let { decoder.json.decodeFromJsonElement(it) }
                    ?: throw IllegalArgumentException("client_id is required"),
            clientSecret = jsonObject["client_secret"]?.let { decoder.json.decodeFromJsonElement(it) },
            clientName = jsonObject["client_name"]?.let { decoder.json.decodeFromJsonElement(it) },
            clientUri = jsonObject["client_uri"]?.let { decoder.json.decodeFromJsonElement(it) },
            logoUri = jsonObject["logo_uri"]?.let { decoder.json.decodeFromJsonElement(it) },
            clientType =
                jsonObject["client_type"]?.let { decoder.json.decodeFromJsonElement<ClientType>(it) }
                    ?: ClientType.CONFIDENTIAL,
            grantTypes =
                jsonObject["grant_types"]?.let { decoder.json.decodeFromJsonElement(it) }
                    ?: throw IllegalArgumentException("grant_types is required"),
            responseTypes =
                jsonObject["response_types"]?.let { decoder.json.decodeFromJsonElement(it) }
                    ?: emptyList(),
            redirectUris =
                jsonObject["redirect_uris"]?.let { decoder.json.decodeFromJsonElement(it) }
                    ?: emptyList(),
            allowedScopes = jsonObject["allowed_scopes"]?.let { decoder.json.decodeFromJsonElement(it) },
            scope = jsonObject["scope"]?.let { decoder.json.decodeFromJsonElement(it) },
            tokenEndpointAuthMethod =
                jsonObject["token_endpoint_auth_method"]?.let {
                    decoder.json.decodeFromJsonElement<ClientAuthenticationMethod>(it)
                } ?: ClientAuthenticationMethod.CLIENT_SECRET_BASIC,
            jwks = jsonObject["jwks"]?.let { decoder.json.decodeFromJsonElement(it) },
            jwksUri = jsonObject["jwks_uri"]?.let { decoder.json.decodeFromJsonElement(it) },
            contacts = jsonObject["contacts"]?.let { decoder.json.decodeFromJsonElement(it) },
            tosUri = jsonObject["tos_uri"]?.let { decoder.json.decodeFromJsonElement(it) },
            policyUri = jsonObject["policy_uri"]?.let { decoder.json.decodeFromJsonElement(it) },
            softwareId = jsonObject["software_id"]?.let { decoder.json.decodeFromJsonElement(it) },
            softwareVersion = jsonObject["software_version"]?.let { decoder.json.decodeFromJsonElement(it) },
            softwareStatement = jsonObject["software_statement"]?.let { decoder.json.decodeFromJsonElement(it) },
            requirePkce =
                jsonObject["require_pkce"]?.let { decoder.json.decodeFromJsonElement<Boolean>(it) }
                    ?: (
                        (
                            jsonObject["client_type"]?.let { decoder.json.decodeFromJsonElement<ClientType>(it) }
                                ?: ClientType.CONFIDENTIAL
                        ) == ClientType.PUBLIC
                    ),
            requirePushedAuthorizationRequests =
                jsonObject["require_pushed_authorization_requests"]?.let {
                    decoder.json.decodeFromJsonElement<Boolean>(it)
                } ?: false,
            dpopBoundAccessTokens =
                jsonObject["dpop_bound_access_tokens"]?.let {
                    decoder.json.decodeFromJsonElement<Boolean>(it)
                } ?: false,
            accessTokenLifetime =
                jsonObject["access_token_lifetime"]?.let { decoder.json.decodeFromJsonElement(it) }
                    ?: 3600,
            refreshTokenLifetime = jsonObject["refresh_token_lifetime"]?.let { decoder.json.decodeFromJsonElement(it) },
            authorizationCodeLifetime =
                jsonObject["authorization_code_lifetime"]?.let {
                    decoder.json.decodeFromJsonElement(it)
                } ?: 600,
            additionalParameters = additionalParameters,
        )
    }
}

@Serializable(with = ClientRegistrationSerializer::class)
data class ClientRegistration(
    @SerialName("client_id")
    val clientId: String,
    @SerialName("client_secret")
    val clientSecret: String? = null,
    @SerialName("client_name")
    val clientName: String? = null,
    @SerialName("client_uri")
    val clientUri: String? = null,
    @SerialName("logo_uri")
    val logoUri: String? = null,
    @SerialName("client_type")
    val clientType: ClientType = ClientType.CONFIDENTIAL,
    @SerialName("grant_types")
    val grantTypes: List<GrantType>,
    @SerialName("response_types")
    val responseTypes: List<ResponseType> = emptyList(),
    @SerialName("redirect_uris")
    val redirectUris: List<String> = emptyList(),
    @SerialName("allowed_scopes")
    val allowedScopes: List<String>? = null,
    val scope: String? = null,
    @SerialName("token_endpoint_auth_method")
    val tokenEndpointAuthMethod: ClientAuthenticationMethod = ClientAuthenticationMethod.CLIENT_SECRET_BASIC,
    val jwks: JwkSet? = null,
    @SerialName("jwks_uri")
    val jwksUri: String? = null,
    val contacts: List<String>? = null,
    @SerialName("tos_uri")
    val tosUri: String? = null,
    @SerialName("policy_uri")
    val policyUri: String? = null,
    @SerialName("software_id")
    val softwareId: String? = null,
    @SerialName("software_version")
    val softwareVersion: String? = null,
    @SerialName("software_statement")
    val softwareStatement: String? = null,
    @SerialName("require_pkce")
    val requirePkce: Boolean = clientType == ClientType.PUBLIC,
    @SerialName("require_pushed_authorization_requests")
    val requirePushedAuthorizationRequests: Boolean = false,
    @SerialName("dpop_bound_access_tokens")
    val dpopBoundAccessTokens: Boolean = false,
    @SerialName("access_token_lifetime")
    val accessTokenLifetime: Int = 3600,
    @SerialName("refresh_token_lifetime")
    val refreshTokenLifetime: Int? = null,
    @SerialName("authorization_code_lifetime")
    val authorizationCodeLifetime: Int = 600,
    val additionalParameters: Map<String, JsonElement> = emptyMap(),
)

@Serializable
enum class ClientType {
    CONFIDENTIAL,
    PUBLIC,
}

val validateClientRegistration =
    Validation<ClientRegistration> {
        ClientRegistration::clientId {
            minLength(1) hint "client_id cannot be empty"
        }
        ClientRegistration::clientName ifPresent {
            minLength(1) hint "client_name cannot be empty"
        }
        ClientRegistration::grantTypes {
            minItems(1) hint "grant_types must contain at least one grant type"
        }
        run {
            constrain("redirect_uris required for authorization_code grants") { registration ->
                val requiresRedirectUri =
                    registration.grantTypes.any {
                        it == GrantType.AUTHORIZATION_CODE
                    }
                !requiresRedirectUri || registration.redirectUris.isNotEmpty()
            }
        }
        ClientRegistration::scope ifPresent {
            minLength(1) hint "scope cannot be empty"
        }
        ClientRegistration::contacts ifPresent {
            minItems(1) hint "contacts must contain at least one contact"
        }
        ClientRegistration::accessTokenLifetime {
            constrain("must be positive") { it > 0 }
        }
        ClientRegistration::authorizationCodeLifetime {
            constrain("must be positive") { it > 0 }
        }
        ClientRegistration::refreshTokenLifetime ifPresent {
            constrain("must be positive") { it > 0 }
        }
    }
