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
 * Custom serializer for AuthorizationRequest that captures unknown extension parameters
 */
internal object AuthorizationRequestSerializer : KSerializer<AuthorizationRequest> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("AuthorizationRequest")

    private val knownJsonKeys = setOf(
        "client_id", "redirect_uri", "response_type", "scope", "state",
        "code_challenge", "code_challenge_method", "nonce", "response_mode",
        "request_uri", "request", "resource", "issuer_state", "dpop_jkt",
        // OIDC parameters
        "prompt", "login_hint", "max_age", "ui_locales",
        "id_token_hint", "acr_values", "display"
    )

    override fun serialize(encoder: Encoder, value: AuthorizationRequest) {
        require(encoder is JsonEncoder) { "AuthorizationRequestSerializer only works with JSON format" }

        val jsonObject = buildJsonObject {
            put("client_id", JsonPrimitive(value.clientId))
            value.redirectUri?.let { put("redirect_uri", JsonPrimitive(it)) }
            put("response_type", JsonPrimitive(value.responseType))
            value.scope?.let { put("scope", JsonPrimitive(it)) }
            value.state?.let { put("state", JsonPrimitive(it)) }
            value.codeChallenge?.let { put("code_challenge", JsonPrimitive(it)) }
            value.codeChallengeMethod?.let { put("code_challenge_method", JsonPrimitive(it)) }
            value.nonce?.let { put("nonce", JsonPrimitive(it)) }
            value.responseMode?.let { put("response_mode", JsonPrimitive(it)) }
            value.requestUri?.let { put("request_uri", JsonPrimitive(it)) }
            value.request?.let { put("request", JsonPrimitive(it)) }
            value.resource?.let { put("resource", JsonPrimitive(it)) }
            value.issuerState?.let { put("issuer_state", JsonPrimitive(it)) }
            value.dpopJkt?.let { put("dpop_jkt", JsonPrimitive(it)) }
            // OIDC parameters
            value.prompt?.let { put("prompt", JsonPrimitive(it)) }
            value.loginHint?.let { put("login_hint", JsonPrimitive(it)) }
            value.maxAge?.let { put("max_age", JsonPrimitive(it)) }
            value.uiLocales?.let { put("ui_locales", JsonPrimitive(it)) }
            value.idTokenHint?.let { put("id_token_hint", JsonPrimitive(it)) }
            value.acrValues?.let { put("acr_values", JsonPrimitive(it)) }
            value.display?.let { put("display", JsonPrimitive(it)) }

            value.additionalParameters.forEach { (key, jsonValue) ->
                put(key, jsonValue)
            }
        }

        encoder.encodeJsonElement(jsonObject)
    }

    override fun deserialize(decoder: Decoder): AuthorizationRequest {
        require(decoder is JsonDecoder) { "AuthorizationRequestSerializer only works with JSON format" }

        val jsonObject = decoder.decodeJsonElement().jsonObject
        val additionalParameters = jsonObject.filterKeys { it !in knownJsonKeys }

        return AuthorizationRequest(
            clientId = jsonObject["client_id"]?.jsonPrimitive?.content
                ?: throw IllegalArgumentException("client_id is required"),
            redirectUri = jsonObject["redirect_uri"]?.jsonPrimitive?.content,
            responseType = jsonObject["response_type"]?.jsonPrimitive?.content ?: "code",
            scope = jsonObject["scope"]?.jsonPrimitive?.content,
            state = jsonObject["state"]?.jsonPrimitive?.content,
            codeChallenge = jsonObject["code_challenge"]?.jsonPrimitive?.content,
            codeChallengeMethod = jsonObject["code_challenge_method"]?.jsonPrimitive?.content,
            nonce = jsonObject["nonce"]?.jsonPrimitive?.content,
            responseMode = jsonObject["response_mode"]?.jsonPrimitive?.content,
            requestUri = jsonObject["request_uri"]?.jsonPrimitive?.content,
            request = jsonObject["request"]?.jsonPrimitive?.content,
            resource = jsonObject["resource"]?.jsonPrimitive?.content,
            issuerState = jsonObject["issuer_state"]?.jsonPrimitive?.content,
            dpopJkt = jsonObject["dpop_jkt"]?.jsonPrimitive?.content,
            // OIDC parameters
            prompt = jsonObject["prompt"]?.jsonPrimitive?.content,
            loginHint = jsonObject["login_hint"]?.jsonPrimitive?.content,
            maxAge = jsonObject["max_age"]?.jsonPrimitive?.content?.toLongOrNull(),
            uiLocales = jsonObject["ui_locales"]?.jsonPrimitive?.content,
            idTokenHint = jsonObject["id_token_hint"]?.jsonPrimitive?.content,
            acrValues = jsonObject["acr_values"]?.jsonPrimitive?.content,
            display = jsonObject["display"]?.jsonPrimitive?.content,
            additionalParameters = additionalParameters
        )
    }
}

/**
 * OAuth 2.0 Authorization Request (RFC 6749 Section 4.1.1)
 *
 * @property clientId REQUIRED. Client identifier
 * @property redirectUri REQUIRED. Redirection endpoint URI
 * @property responseType REQUIRED. Value MUST be "code" for authorization code flow
 * @property scope OPTIONAL. Scope of access request
 * @property state RECOMMENDED. Opaque value used to maintain state
 * @property codeChallenge OPTIONAL. PKCE code challenge (RFC 7636)
 * @property codeChallengeMethod OPTIONAL. PKCE code challenge method (RFC 7636)
 * @property nonce OPTIONAL. OpenID Connect nonce
 * @property responseMode OPTIONAL. Indicates how the authorization response is encoded
 * @property requestUri OPTIONAL. JAR request_uri (RFC 9101)
 * @property request OPTIONAL. JAR request object (RFC 9101)
 * @property resource OPTIONAL. Resource indicator (RFC 8707)
 * @property issuerState OPTIONAL. Issuer state for cross-device flows
 * @property dpopJkt OPTIONAL. DPoP JWK thumbprint (RFC 9449)
 * @property additionalParameters Additional extension parameters (auto-captured from unknown JSON fields)
 */
@Serializable(with = AuthorizationRequestSerializer::class)
@JsExportCompat
@Suppress("NON_EXPORTABLE_TYPE")
data class AuthorizationRequest(
    @SerialName("client_id") val clientId: String,
    @SerialName("redirect_uri") val redirectUri: String? = null,
    @SerialName("response_type") val responseType: String = "code",
    val scope: String? = null,
    val state: String? = null,
    @SerialName("code_challenge") val codeChallenge: String? = null,
    @SerialName("code_challenge_method") val codeChallengeMethod: String? = null,
    val nonce: String? = null,
    @SerialName("response_mode") val responseMode: String? = null,
    @SerialName("request_uri") val requestUri: String? = null,
    val request: String? = null,
    val resource: String? = null,
    @SerialName("issuer_state") val issuerState: String? = null,
    @SerialName("dpop_jkt") val dpopJkt: String? = null,

    // OpenID Connect parameters (OpenID Connect Core Section 3.1.2.1)
    val prompt: String? = null,
    @SerialName("login_hint") val loginHint: String? = null,
    @SerialName("max_age") val maxAge: Long? = null,
    @SerialName("ui_locales") val uiLocales: String? = null,
    @SerialName("id_token_hint") val idTokenHint: String? = null,
    @SerialName("acr_values") val acrValues: String? = null,
    val display: String? = null,

    // Additional extension parameters (auto-captured from unknown JSON fields)
    val additionalParameters: Map<String, JsonElement> = emptyMap()
)
