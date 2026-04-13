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

package com.sphereon.openid.oid4vp.common

import com.sphereon.oauth2.common.model.AuthorizationResponse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/*
 * Type-safe extensions for OpenID4VP Authorization Response parameters
 *
 * These extensions provide type-safe access to OpenID4VP-specific parameters
 * that are stored in the OAuth2 AuthorizationResponse.additionalParameters map.
 */

// Extension properties for reading OID4VP response parameters

/**
 * VP Token parameter (OpenID4VP 1.0 Section 6)
 *
 * Contains one or more Verifiable Presentations in the requested format.
 * Can be a single presentation (string) or multiple presentations (array).
 */
val AuthorizationResponse.vpToken: VpToken?
    get() =
        additionalParameters["vp_token"]?.let { jsonElement ->
            try {
                VpToken.fromJson(jsonElement)
            } catch (_: Exception) {
                null
            }
        }

/**
 * Check if this authorization response is an OpenID4VP response
 *
 * An authorization response is considered OpenID4VP if:
 * - vp_token parameter is present
 */
val AuthorizationResponse.isOid4vp: Boolean
    get() = vpToken != null

/**
 * Builder for creating OpenID4VP Authorization Responses
 *
 * Provides type-safe builder methods for OID4VP-specific parameters while
 * leveraging the OAuth2 AuthorizationResponse foundation.
 */
class Oid4vpAuthorizationResponseBuilder(
    private var code: String = "",
) {
    private var state: String? = null

    // OID4VP-specific parameters
    private var vpToken: VpToken? = null

    private val additionalParams = mutableMapOf<String, JsonElement>()

    fun code(code: String) = apply { this.code = code }

    fun state(state: String) = apply { this.state = state }

    // OID4VP-specific builder methods
    fun vpToken(token: VpToken) = apply { this.vpToken = token }

    /**
     * Set vp_token with a single presentation for a query.
     *
     * @param queryId The credential query ID from the DCQL query
     * @param presentation The presentation string
     */
    fun vpToken(
        queryId: String,
        presentation: String,
    ) = apply {
        this.vpToken = vpTokenOf(queryId, presentation)
    }

    /**
     * Set vp_token with multiple presentations mapped by query ID.
     *
     * @param entries Map of credential query IDs to presentation lists
     */
    fun vpToken(entries: Map<String, List<String>>) =
        apply {
            this.vpToken = vpTokenOf(entries)
        }

    fun additionalParameter(
        key: String,
        value: JsonElement,
    ) = apply {
        additionalParams[key] = value
    }

    fun build(): AuthorizationResponse {
        val allAdditionalParams = mutableMapOf<String, JsonElement>()

        // Add OID4VP-specific parameters
        vpToken?.let { allAdditionalParams["vp_token"] = VpToken.run { it.toJson() } }

        // Add any custom additional parameters
        allAdditionalParams.putAll(additionalParams)

        return AuthorizationResponse(
            code = code,
            state = state,
            additionalParameters = allAdditionalParams,
        )
    }
}

/**
 * Create an OpenID4VP authorization response using a type-safe builder
 *
 * Example:
 * ```kotlin
 * val response = buildOid4vpAuthorizationResponse {
 *     // DCQL format: query ID to presentation(s)
 *     vpToken("driver_license_query", sdJwtPresentation)
 *     state(originalRequest.state)
 * }
 * ```
 */
fun buildOid4vpAuthorizationResponse(block: Oid4vpAuthorizationResponseBuilder.() -> Unit = {}): AuthorizationResponse =
    Oid4vpAuthorizationResponseBuilder()
        .apply(block)
        .build()
