/*
 * © 2025 Sphereon International B.V.
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

package com.sphereon.openid.oid4vp.common

import com.sphereon.oauth2.common.model.AuthorizationRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Type-safe extensions for OpenID4VP Authorization Request parameters
 *
 * These extensions provide type-safe access to OpenID4VP-specific parameters
 * that are stored in the OAuth2 AuthorizationRequest.additionalParameters map.
 *
 * Developers can use these extension properties instead of manually accessing
 * the additionalParameters map with string keys.
 */

// Extension properties for reading OID4VP parameters

/**
 * DCQL query parameter (OpenID4VP 1.0 Section 6)
 *
 * The dcql_query parameter contains the query that describes what credentials
 * and claims the verifier is requesting from the wallet.
 */
val AuthorizationRequest.dcqlQuery: JsonObject?
    get() = additionalParameters["dcql_query"]?.jsonObject

/**
 * Client ID scheme parameter (OpenID4VP 1.0 Section 5.4)
 *
 * Specifies how the client (verifier) is identified:
 * - "pre-registered" (default)
 * - "redirect_uri"
 * - "verifier_attestation"
 * - "x509_san_dns"
 * - "x509_san_uri"
 * - "did"
 */
val AuthorizationRequest.clientIdScheme: String?
    get() = additionalParameters["client_id_scheme"]?.jsonPrimitive?.content

/**
 * Client metadata parameter (OpenID4VP 1.0 Section 5.5)
 *
 * Contains the verifier's metadata inline in the authorization request.
 * Mutually exclusive with client_metadata_uri.
 */
val AuthorizationRequest.clientMetadata: ClientMetadata?
    get() = additionalParameters["client_metadata"]?.let {
        try {
            Json.decodeFromJsonElement(ClientMetadata.serializer(), it)
        } catch (e: Exception) {
            null
        }
    }

/**
 * Client metadata URI parameter (OpenID4VP 1.0 Section 5.5)
 *
 * URL where the verifier's metadata can be fetched.
 * Mutually exclusive with client_metadata.
 */
val AuthorizationRequest.clientMetadataUri: String?
    get() = additionalParameters["client_metadata_uri"]?.jsonPrimitive?.content

/**
 * Request URI method parameter (JAR extension for OpenID4VP)
 *
 * Specifies the HTTP method to use when fetching the request object:
 * - "get" (default)
 * - "post"
 */
val AuthorizationRequest.requestUriMethod: String?
    get() = additionalParameters["request_uri_method"]?.jsonPrimitive?.content

/**
 * Transaction data parameter (OpenID4VP 1.0 Section 6.3)
 *
 * Optional transaction-specific data for the wallet.
 * Used for ISO mdoc session transcripts and handover data.
 */
val AuthorizationRequest.transactionData: String?
    get() = additionalParameters["transaction_data"]?.jsonPrimitive?.content

/**
 * Response URI parameter (OpenID4VP direct_post mode)
 *
 * The URI where the wallet should POST the authorization response when
 * using direct_post response mode.
 */
val AuthorizationRequest.responseUri: String?
    get() = additionalParameters["response_uri"]?.jsonPrimitive?.content

/**
 * Nonce parameter (OpenID4VP holder binding)
 *
 * Random value used for cryptographic holder binding in VP proofs.
 * Must be included in the VP proof JWT.
 */
val AuthorizationRequest.oid4vpNonce: String?
    get() = nonce ?: additionalParameters["nonce"]?.jsonPrimitive?.content

/**
 * Wallet nonce parameter (OpenID4VP 1.0 Section 5.2)
 *
 * Optional nonce value provided by the verifier for replay protection.
 * When present, the wallet MUST include this nonce when POSTing to request_uri
 * (if request_uri_method=post).
 *
 * The verifier uses this to correlate the request_uri POST with the original
 * authorization request, preventing replay attacks.
 */
val AuthorizationRequest.walletNonce: String?
    get() = additionalParameters["wallet_nonce"]?.jsonPrimitive?.content

/**
 * Verifier info parameter (OpenID4VP 1.0 Section 5.1.1)
 *
 * Contains an array of verifier attestations that prove the verifier's identity
 * and authorization to request specific credentials. Each attestation entry includes:
 * - format: The attestation format (e.g., "verifier-attestation+jwt")
 * - data: The attestation data (JWT string or JSON object)
 * - credential_ids: Optional list of credential IDs this attestation applies to
 *
 * This parameter is particularly relevant for the verifier_attestation client_id scheme.
 */
val AuthorizationRequest.verifierInfo: List<VerifierAttestation>?
    get() = additionalParameters["verifier_info"]?.let {
        try {
            Json.decodeFromJsonElement(kotlinx.serialization.builtins.ListSerializer(VerifierAttestation.serializer()), it)
        } catch (e: Exception) {
            null
        }
    }

/**
 * Check if this authorization request is an OpenID4VP request
 *
 * An authorization request is considered OpenID4VP if:
 * - response_type is "vp_token" or contains "vp_token"
 * - OR dcql_query parameter is present
 */
val AuthorizationRequest.isOid4vp: Boolean
    get() = responseType.contains("vp_token") || dcqlQuery != null

/**
 * Builder for creating OpenID4VP Authorization Requests
 *
 * Provides type-safe builder methods for OID4VP-specific parameters while
 * leveraging the OAuth2 AuthorizationRequest foundation.
 */
class Oid4vpAuthorizationRequestBuilder(
    private var clientId: String,
    private var redirectUri: String? = null,
    private var responseType: String = "vp_token"
) {
    private var scope: String? = null
    private var state: String? = null
    private var nonce: String? = null
    private var responseMode: String? = null
    private var requestUri: String? = null
    private var request: String? = null

    // OID4VP-specific parameters
    private var dcqlQuery: JsonObject? = null
    private var clientIdScheme: String? = null
    private var clientMetadata: ClientMetadata? = null
    private var clientMetadataUri: String? = null
    private var requestUriMethod: String? = null
    private var transactionData: String? = null
    private var responseUri: String? = null
    private var verifierInfo: List<VerifierAttestation>? = null

    private val additionalParams = mutableMapOf<String, JsonElement>()

    fun scope(scope: String) = apply { this.scope = scope }
    fun state(state: String) = apply { this.state = state }
    fun nonce(nonce: String) = apply { this.nonce = nonce }
    fun responseMode(mode: ResponseMode) = apply { this.responseMode = mode.value }
    fun responseMode(mode: String) = apply { this.responseMode = mode }
    fun requestUri(uri: String) = apply { this.requestUri = uri }
    fun request(jwt: String) = apply { this.request = jwt }

    // OID4VP-specific builder methods
    fun dcqlQuery(query: JsonObject) = apply { this.dcqlQuery = query }
    fun clientIdScheme(scheme: String) = apply { this.clientIdScheme = scheme }
    fun clientMetadata(metadata: ClientMetadata) = apply { this.clientMetadata = metadata }
    fun clientMetadataUri(uri: String) = apply { this.clientMetadataUri = uri }
    fun requestUriMethod(method: String) = apply { this.requestUriMethod = method }
    fun transactionData(data: String) = apply { this.transactionData = data }
    fun responseUri(uri: String) = apply { this.responseUri = uri }
    fun verifierInfo(attestations: List<VerifierAttestation>) = apply { this.verifierInfo = attestations }
    fun verifierInfo(vararg attestations: VerifierAttestation) = apply { 
        this.verifierInfo = attestations.toList().takeIf { it.isNotEmpty() }
    }

    fun additionalParameter(key: String, value: JsonElement) = apply {
        additionalParams[key] = value
    }

    fun build(): AuthorizationRequest {
        val allAdditionalParams = mutableMapOf<String, JsonElement>()

        // Add OID4VP-specific parameters
        dcqlQuery?.let { allAdditionalParams["dcql_query"] = it }
        clientIdScheme?.let { allAdditionalParams["client_id_scheme"] = JsonPrimitive(it) }
        clientMetadata?.let {
            allAdditionalParams["client_metadata"] = Json.encodeToJsonElement(ClientMetadata.serializer(), it)
        }
        clientMetadataUri?.let { allAdditionalParams["client_metadata_uri"] = JsonPrimitive(it) }
        requestUriMethod?.let { allAdditionalParams["request_uri_method"] = JsonPrimitive(it) }
        transactionData?.let { allAdditionalParams["transaction_data"] = JsonPrimitive(it) }
        responseUri?.let { allAdditionalParams["response_uri"] = JsonPrimitive(it) }
        verifierInfo?.let {
            allAdditionalParams["verifier_info"] = Json.encodeToJsonElement(
                kotlinx.serialization.builtins.ListSerializer(VerifierAttestation.serializer()), it
            )
        }

        // Add any custom additional parameters
        allAdditionalParams.putAll(additionalParams)

        return AuthorizationRequest(
            clientId = clientId,
            redirectUri = redirectUri,
            responseType = responseType,
            scope = scope,
            state = state,
            nonce = nonce,
            responseMode = responseMode,
            requestUri = requestUri,
            request = request,
            additionalParameters = allAdditionalParams
        )
    }
}

/**
 * Create an OpenID4VP authorization request using a type-safe builder
 *
 * Example:
 * ```kotlin
 * val request = buildOid4vpAuthorizationRequest(
 *     clientId = "https://verifier.example.com",
 *     redirectUri = "https://verifier.example.com/callback"
 * ) {
 *     responseMode(ResponseMode.DIRECT_POST)
 *     responseUri("https://verifier.example.com/response")
 *     dcqlQuery(myDcqlQuery)
 *     clientIdScheme("redirect_uri")
 *     nonce(generateNonce())
 * }
 * ```
 */
fun buildOid4vpAuthorizationRequest(
    clientId: String,
    redirectUri: String? = null,
    responseType: String = "vp_token",
    block: Oid4vpAuthorizationRequestBuilder.() -> Unit = {}
): AuthorizationRequest {
    return Oid4vpAuthorizationRequestBuilder(clientId, redirectUri, responseType)
        .apply(block)
        .build()
}
