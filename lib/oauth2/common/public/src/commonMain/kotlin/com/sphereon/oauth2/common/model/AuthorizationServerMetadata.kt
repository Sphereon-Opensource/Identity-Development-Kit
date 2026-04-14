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
 * Custom serializer for AuthorizationServerMetadata that captures unknown discovery parameters
 *
 * OAuth 2.0 discovery metadata frequently has extensions (OpenID Connect, OpenID4VCI, custom params, etc.)
 * See: https://github.com/Kotlin/kotlinx.serialization/issues/1978
 */
internal object AuthorizationServerMetadataSerializer : KSerializer<AuthorizationServerMetadata> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("AuthorizationServerMetadata")

    private val knownJsonKeys =
        setOf(
            "issuer",
            "token_endpoint",
            "authorization_endpoint",
            "jwks_uri",
            "grant_types_supported",
            "token_endpoint_auth_methods_supported",
            "code_challenge_methods_supported",
            "dpop_signing_alg_values_supported",
            "require_pushed_authorization_requests",
            "pushed_authorization_request_endpoint",
            "introspection_endpoint",
            "introspection_endpoint_auth_methods_supported",
            "introspection_endpoint_auth_signing_alg_values_supported",
            "authorization_challenge_endpoint",
            "pre-authorized_grant_anonymous_access_supported",
            "client_attestation_pop_nonce_required",
            "challenge_endpoint",
            "client_attestation_signing_alg_values_supported",
            "client_attestation_pop_signing_alg_values_supported",
            "revocation_endpoint",
            "revocation_endpoint_auth_methods_supported",
            "revocation_endpoint_auth_signing_alg_values_supported",
            "scopes_supported",
            "response_types_supported",
            "response_modes_supported",
            "token_endpoint_auth_signing_alg_values_supported",
            "service_documentation",
            "ui_locales_supported",
            // OIDC fields
            "userinfo_endpoint",
            "subject_types_supported",
            "id_token_signing_alg_values_supported",
            "claims_supported",
            "claims_parameter_supported",
            "request_parameter_supported",
            "request_uri_parameter_supported",
            // OID4VCI 1.1 Section 13.3 — IAE
            "interactive_authorization_endpoint",
            "require_interactive_authorization_request",
        )

    override fun serialize(
        encoder: Encoder,
        value: AuthorizationServerMetadata,
    ) {
        require(encoder is JsonEncoder) { "AuthorizationServerMetadataSerializer only works with JSON format" }

        val jsonObject =
            buildJsonObject {
                put("issuer", JsonPrimitive(value.issuer))
                put("token_endpoint", JsonPrimitive(value.tokenEndpoint))
                value.authorizationEndpoint?.let { put("authorization_endpoint", JsonPrimitive(it)) }
                value.jwksUri?.let { put("jwks_uri", JsonPrimitive(it)) }
                value.grantTypesSupported?.let { put("grant_types_supported", JsonArray(it.map { JsonPrimitive(it) })) }
                value.tokenEndpointAuthMethodsSupported?.let { put("token_endpoint_auth_methods_supported", JsonArray(it.map { JsonPrimitive(it) })) }
                value.codeChallengeMethodsSupported?.let { put("code_challenge_methods_supported", JsonArray(it.map { JsonPrimitive(it) })) }
                value.dpopSigningAlgValuesSupported?.let { put("dpop_signing_alg_values_supported", JsonArray(it.map { JsonPrimitive(it) })) }
                value.requirePushedAuthorizationRequests?.let { put("require_pushed_authorization_requests", JsonPrimitive(it)) }
                value.pushedAuthorizationRequestEndpoint?.let { put("pushed_authorization_request_endpoint", JsonPrimitive(it)) }
                value.introspectionEndpoint?.let { put("introspection_endpoint", JsonPrimitive(it)) }
                value.introspectionEndpointAuthMethodsSupported?.let { put("introspection_endpoint_auth_methods_supported", JsonArray(it.map { JsonPrimitive(it) })) }
                value.introspectionEndpointAuthSigningAlgValuesSupported?.let { put("introspection_endpoint_auth_signing_alg_values_supported", JsonArray(it.map { JsonPrimitive(it) })) }
                value.authorizationChallengeEndpoint?.let { put("authorization_challenge_endpoint", JsonPrimitive(it)) }
                value.preAuthorizedGrantAnonymousAccessSupported?.let { put("pre-authorized_grant_anonymous_access_supported", JsonPrimitive(it)) }
                value.clientAttestationPopNonceRequired?.let { put("client_attestation_pop_nonce_required", JsonPrimitive(it)) }
                value.challengeEndpoint?.let { put("challenge_endpoint", JsonPrimitive(it)) }
                value.clientAttestationSigningAlgValuesSupported?.let { put("client_attestation_signing_alg_values_supported", JsonArray(it.map { JsonPrimitive(it) })) }
                value.clientAttestationPopSigningAlgValuesSupported?.let { put("client_attestation_pop_signing_alg_values_supported", JsonArray(it.map { JsonPrimitive(it) })) }
                value.revocationEndpoint?.let { put("revocation_endpoint", JsonPrimitive(it)) }
                value.revocationEndpointAuthMethodsSupported?.let { put("revocation_endpoint_auth_methods_supported", JsonArray(it.map { JsonPrimitive(it) })) }
                value.revocationEndpointAuthSigningAlgValuesSupported?.let { put("revocation_endpoint_auth_signing_alg_values_supported", JsonArray(it.map { JsonPrimitive(it) })) }
                value.scopesSupported?.let { put("scopes_supported", JsonArray(it.map { JsonPrimitive(it) })) }
                value.responseTypesSupported?.let { put("response_types_supported", JsonArray(it.map { JsonPrimitive(it) })) }
                value.responseModesSupported?.let { put("response_modes_supported", JsonArray(it.map { JsonPrimitive(it) })) }
                value.tokenEndpointAuthSigningAlgValuesSupported?.let { put("token_endpoint_auth_signing_alg_values_supported", JsonArray(it.map { JsonPrimitive(it) })) }
                value.serviceDocumentation?.let { put("service_documentation", JsonPrimitive(it)) }
                value.uiLocalesSupported?.let { put("ui_locales_supported", JsonArray(it.map { JsonPrimitive(it) })) }

                // OIDC fields
                value.userinfoEndpoint?.let { put("userinfo_endpoint", JsonPrimitive(it)) }
                value.subjectTypesSupported?.let { put("subject_types_supported", JsonArray(it.map { JsonPrimitive(it) })) }
                value.idTokenSigningAlgValuesSupported?.let { put("id_token_signing_alg_values_supported", JsonArray(it.map { JsonPrimitive(it) })) }
                value.claimsSupported?.let { put("claims_supported", JsonArray(it.map { JsonPrimitive(it) })) }
                value.claimsParameterSupported?.let { put("claims_parameter_supported", JsonPrimitive(it)) }
                value.requestParameterSupported?.let { put("request_parameter_supported", JsonPrimitive(it)) }
                value.requestUriParameterSupported?.let { put("request_uri_parameter_supported", JsonPrimitive(it)) }

                // OID4VCI 1.1 Section 13.3 — IAE
                value.interactiveAuthorizationEndpoint?.let { put("interactive_authorization_endpoint", JsonPrimitive(it)) }
                value.requireInteractiveAuthorizationRequest?.let { put("require_interactive_authorization_request", JsonPrimitive(it)) }

                value.additionalMetadata.forEach { (key, jsonValue) ->
                    put(key, jsonValue)
                }
            }

        encoder.encodeJsonElement(jsonObject)
    }

    override fun deserialize(decoder: Decoder): AuthorizationServerMetadata {
        require(decoder is JsonDecoder) { "AuthorizationServerMetadataSerializer only works with JSON format" }

        val jsonObject = decoder.decodeJsonElement().jsonObject
        val additionalMetadata = jsonObject.filterKeys { it !in knownJsonKeys }

        return AuthorizationServerMetadata(
            issuer =
                jsonObject["issuer"]?.jsonPrimitive?.content
                    ?: throw IllegalArgumentException("issuer is required"),
            tokenEndpoint =
                jsonObject["token_endpoint"]?.jsonPrimitive?.content
                    ?: throw IllegalArgumentException("token_endpoint is required"),
            authorizationEndpoint = jsonObject["authorization_endpoint"]?.jsonPrimitive?.content,
            jwksUri = jsonObject["jwks_uri"]?.jsonPrimitive?.content,
            grantTypesSupported = jsonObject["grant_types_supported"]?.jsonArray?.map { it.jsonPrimitive.content },
            tokenEndpointAuthMethodsSupported = jsonObject["token_endpoint_auth_methods_supported"]?.jsonArray?.map { it.jsonPrimitive.content },
            codeChallengeMethodsSupported = jsonObject["code_challenge_methods_supported"]?.jsonArray?.map { it.jsonPrimitive.content },
            dpopSigningAlgValuesSupported = jsonObject["dpop_signing_alg_values_supported"]?.jsonArray?.map { it.jsonPrimitive.content },
            requirePushedAuthorizationRequests = jsonObject["require_pushed_authorization_requests"]?.jsonPrimitive?.content?.toBoolean(),
            pushedAuthorizationRequestEndpoint = jsonObject["pushed_authorization_request_endpoint"]?.jsonPrimitive?.content,
            introspectionEndpoint = jsonObject["introspection_endpoint"]?.jsonPrimitive?.content,
            introspectionEndpointAuthMethodsSupported = jsonObject["introspection_endpoint_auth_methods_supported"]?.jsonArray?.map { it.jsonPrimitive.content },
            introspectionEndpointAuthSigningAlgValuesSupported = jsonObject["introspection_endpoint_auth_signing_alg_values_supported"]?.jsonArray?.map { it.jsonPrimitive.content },
            authorizationChallengeEndpoint = jsonObject["authorization_challenge_endpoint"]?.jsonPrimitive?.content,
            preAuthorizedGrantAnonymousAccessSupported = jsonObject["pre-authorized_grant_anonymous_access_supported"]?.jsonPrimitive?.content?.toBoolean(),
            clientAttestationPopNonceRequired = jsonObject["client_attestation_pop_nonce_required"]?.jsonPrimitive?.content?.toBoolean(),
            challengeEndpoint = jsonObject["challenge_endpoint"]?.jsonPrimitive?.content,
            clientAttestationSigningAlgValuesSupported = jsonObject["client_attestation_signing_alg_values_supported"]?.jsonArray?.map { it.jsonPrimitive.content },
            clientAttestationPopSigningAlgValuesSupported = jsonObject["client_attestation_pop_signing_alg_values_supported"]?.jsonArray?.map { it.jsonPrimitive.content },
            revocationEndpoint = jsonObject["revocation_endpoint"]?.jsonPrimitive?.content,
            revocationEndpointAuthMethodsSupported = jsonObject["revocation_endpoint_auth_methods_supported"]?.jsonArray?.map { it.jsonPrimitive.content },
            revocationEndpointAuthSigningAlgValuesSupported = jsonObject["revocation_endpoint_auth_signing_alg_values_supported"]?.jsonArray?.map { it.jsonPrimitive.content },
            scopesSupported = jsonObject["scopes_supported"]?.jsonArray?.map { it.jsonPrimitive.content },
            responseTypesSupported = jsonObject["response_types_supported"]?.jsonArray?.map { it.jsonPrimitive.content },
            responseModesSupported = jsonObject["response_modes_supported"]?.jsonArray?.map { it.jsonPrimitive.content },
            tokenEndpointAuthSigningAlgValuesSupported = jsonObject["token_endpoint_auth_signing_alg_values_supported"]?.jsonArray?.map { it.jsonPrimitive.content },
            serviceDocumentation = jsonObject["service_documentation"]?.jsonPrimitive?.content,
            uiLocalesSupported = jsonObject["ui_locales_supported"]?.jsonArray?.map { it.jsonPrimitive.content },
            // OIDC fields
            userinfoEndpoint = jsonObject["userinfo_endpoint"]?.jsonPrimitive?.content,
            subjectTypesSupported = jsonObject["subject_types_supported"]?.jsonArray?.map { it.jsonPrimitive.content },
            idTokenSigningAlgValuesSupported = jsonObject["id_token_signing_alg_values_supported"]?.jsonArray?.map { it.jsonPrimitive.content },
            claimsSupported = jsonObject["claims_supported"]?.jsonArray?.map { it.jsonPrimitive.content },
            claimsParameterSupported = jsonObject["claims_parameter_supported"]?.jsonPrimitive?.content?.toBoolean(),
            requestParameterSupported = jsonObject["request_parameter_supported"]?.jsonPrimitive?.content?.toBoolean(),
            requestUriParameterSupported = jsonObject["request_uri_parameter_supported"]?.jsonPrimitive?.content?.toBoolean(),
            // OID4VCI 1.1 Section 13.3 — IAE
            interactiveAuthorizationEndpoint = jsonObject["interactive_authorization_endpoint"]?.jsonPrimitive?.content,
            requireInteractiveAuthorizationRequest = jsonObject["require_interactive_authorization_request"]?.jsonPrimitive?.content?.toBoolean(),
            additionalMetadata = additionalMetadata,
        )
    }
}

/**
 * OAuth 2.0 Authorization Server Metadata (RFC 8414)
 * Also supports OpenID Connect Discovery
 *
 * @property issuer REQUIRED. The authorization server's issuer identifier (HTTPS URL)
 * @property tokenEndpoint REQUIRED. URL of the OAuth 2.0 Token Endpoint
 * @property authorizationEndpoint URL of the OAuth 2.0 Authorization Endpoint
 * @property jwksUri URL of the authorization server's JWK Set document
 * @property grantTypesSupported JSON array of OAuth 2.0 grant types supported
 * @property tokenEndpointAuthMethodsSupported JSON array of client authentication methods supported
 * @property codeChallengeMethodsSupported PKCE code challenge methods supported (RFC 7636)
 * @property dpopSigningAlgValuesSupported DPoP signing algorithms supported (RFC 9449)
 * @property requirePushedAuthorizationRequests Whether PAR is required (RFC 9126)
 * @property pushedAuthorizationRequestEndpoint PAR endpoint URL (RFC 9126)
 * @property introspectionEndpoint Token introspection endpoint URL (RFC 7662)
 * @property introspectionEndpointAuthMethodsSupported Introspection endpoint auth methods
 * @property introspectionEndpointAuthSigningAlgValuesSupported Introspection endpoint signing algorithms
 * @property authorizationChallengeEndpoint Authorization challenge endpoint (FiPA - experimental)
 * @property preAuthorizedGrantAnonymousAccessSupported Anonymous access for pre-authorized grants (OpenID4VCI)
 * @property clientAttestationPopNonceRequired Client attestation PoP nonce required (draft spec)
 * @property revocationEndpoint Token revocation endpoint URL (RFC 7009)
 * @property revocationEndpointAuthMethodsSupported Revocation endpoint auth methods
 * @property revocationEndpointAuthSigningAlgValuesSupported Revocation endpoint signing algorithms
 * @property scopesSupported Scopes supported by the authorization server
 * @property responseTypesSupported Response types supported
 * @property responseModesSupported Response modes supported
 * @property tokenEndpointAuthSigningAlgValuesSupported Token endpoint auth signing algorithms
 * @property serviceDocumentation URL of the authorization server documentation
 * @property uiLocalesSupported UI locales supported
 * @property additionalMetadata Additional discovery metadata (auto-captured from unknown JSON fields)
 */
@Serializable(with = AuthorizationServerMetadataSerializer::class)
@JsExportCompat
@Suppress("NON_EXPORTABLE_TYPE")
data class AuthorizationServerMetadata(
    val issuer: String,
    @SerialName("token_endpoint")
    val tokenEndpoint: String,
    @SerialName("authorization_endpoint")
    val authorizationEndpoint: String? = null,
    @SerialName("jwks_uri")
    val jwksUri: String? = null,
    @SerialName("grant_types_supported")
    val grantTypesSupported: List<String>? = null,
    @SerialName("token_endpoint_auth_methods_supported")
    val tokenEndpointAuthMethodsSupported: List<String>? = null,
    // RFC 7636 - PKCE
    @SerialName("code_challenge_methods_supported")
    val codeChallengeMethodsSupported: List<String>? = null,
    // RFC 9449 - DPoP
    @SerialName("dpop_signing_alg_values_supported")
    val dpopSigningAlgValuesSupported: List<String>? = null,
    // RFC 9126 - PAR
    @SerialName("require_pushed_authorization_requests")
    val requirePushedAuthorizationRequests: Boolean? = null,
    @SerialName("pushed_authorization_request_endpoint")
    val pushedAuthorizationRequestEndpoint: String? = null,
    // RFC 7662 / RFC 9068 - Token Introspection
    @SerialName("introspection_endpoint")
    val introspectionEndpoint: String? = null,
    @SerialName("introspection_endpoint_auth_methods_supported")
    val introspectionEndpointAuthMethodsSupported: List<String>? = null,
    @SerialName("introspection_endpoint_auth_signing_alg_values_supported")
    val introspectionEndpointAuthSigningAlgValuesSupported: List<String>? = null,
    // FiPA (experimental - no RFC yet)
    @SerialName("authorization_challenge_endpoint")
    val authorizationChallengeEndpoint: String? = null,
    // OpenID4VCI extension
    @SerialName("pre-authorized_grant_anonymous_access_supported")
    val preAuthorizedGrantAnonymousAccessSupported: Boolean? = null,
    // Attestation Based Client Auth (draft-ietf-oauth-attestation-based-client-auth)
    @SerialName("client_attestation_pop_nonce_required")
    val clientAttestationPopNonceRequired: Boolean? = null,
    @SerialName("challenge_endpoint")
    val challengeEndpoint: String? = null,
    @SerialName("client_attestation_signing_alg_values_supported")
    val clientAttestationSigningAlgValuesSupported: List<String>? = null,
    @SerialName("client_attestation_pop_signing_alg_values_supported")
    val clientAttestationPopSigningAlgValuesSupported: List<String>? = null,
    // RFC 7009 - Token Revocation
    @SerialName("revocation_endpoint")
    val revocationEndpoint: String? = null,
    @SerialName("revocation_endpoint_auth_methods_supported")
    val revocationEndpointAuthMethodsSupported: List<String>? = null,
    @SerialName("revocation_endpoint_auth_signing_alg_values_supported")
    val revocationEndpointAuthSigningAlgValuesSupported: List<String>? = null,
    // RFC 8414 - Additional metadata fields
    @SerialName("scopes_supported")
    val scopesSupported: List<String>? = null,
    @SerialName("response_types_supported")
    val responseTypesSupported: List<String>? = null,
    @SerialName("response_modes_supported")
    val responseModesSupported: List<String>? = null,
    @SerialName("token_endpoint_auth_signing_alg_values_supported")
    val tokenEndpointAuthSigningAlgValuesSupported: List<String>? = null,
    @SerialName("service_documentation")
    val serviceDocumentation: String? = null,
    @SerialName("ui_locales_supported")
    val uiLocalesSupported: List<String>? = null,
    // OpenID Connect Discovery fields
    @SerialName("userinfo_endpoint")
    val userinfoEndpoint: String? = null,
    @SerialName("subject_types_supported")
    val subjectTypesSupported: List<String>? = null,
    @SerialName("id_token_signing_alg_values_supported")
    val idTokenSigningAlgValuesSupported: List<String>? = null,
    @SerialName("claims_supported")
    val claimsSupported: List<String>? = null,
    @SerialName("claims_parameter_supported")
    val claimsParameterSupported: Boolean? = null,
    @SerialName("request_parameter_supported")
    val requestParameterSupported: Boolean? = null,
    @SerialName("request_uri_parameter_supported")
    val requestUriParameterSupported: Boolean? = null,
    // OID4VCI 1.1 Section 13.3 — Interactive Authorization Endpoint
    @SerialName("interactive_authorization_endpoint")
    val interactiveAuthorizationEndpoint: String? = null,
    @SerialName("require_interactive_authorization_request")
    val requireInteractiveAuthorizationRequest: Boolean? = null,
    // Additional discovery metadata (auto-captured from unknown JSON fields)
    val additionalMetadata: Map<String, JsonElement> = emptyMap(),
)
