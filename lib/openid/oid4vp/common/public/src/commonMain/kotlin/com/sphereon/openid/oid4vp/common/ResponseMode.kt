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

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/**
 * Response Mode for OpenID4VP
 *
 * OpenID4VP 1.0 Section 6.2:
 * "The response_mode parameter determines how the authorization response is returned to the Client.
 * OpenID4VP primarily uses direct_post for cross-device flows."
 */
@Serializable
@JsExportCompat
enum class ResponseMode(
    val value: String,
) {
    /**
     * Fragment response mode (OAuth 2.0 default)
     *
     * Authorization response parameters are encoded in the fragment graph of the redirect URI.
     */
    @SerialName("fragment")
    FRAGMENT("fragment"),

    /**
     * Direct Post response mode (OpenID4VP primary mode)
     *
     * The Wallet sends the authorization response as an HTTP POST request to the response_uri
     * provided by the verifier. The response may contain a redirect_uri for the user to return
     * to the verifier's website.
     *
     * Used for:
     * - Same-device flows (redirect after POST)
     * - Cross-device flows (no redirect after POST)
     */
    @SerialName("direct_post")
    DIRECT_POST("direct_post"),

    /**
     * Direct Post JWT response mode
     *
     * Same as direct_post, but the response parameters are encrypted in a JWT.
     * Provides additional privacy protection.
     */
    @SerialName("direct_post.jwt")
    DIRECT_POST_JWT("direct_post.jwt"),

    /**
     * Query response mode (OAuth 2.0)
     *
     * Authorization response parameters are encoded in the query graph of the redirect URI.
     * Less commonly used in OpenID4VP due to security considerations.
     */
    @SerialName("query")
    QUERY("query"),

    /**
     * IAE POST response mode (OID4VCI 1.1 Section 6 — Interactive Authorization Endpoint)
     *
     * The wallet POSTs the OpenID4VP authorization response directly back to the IAE endpoint.
     * Used when the verifier is the issuer itself and the presentation is part of an
     * authorization round-trip rather than a standalone verification flow.
     */
    @SerialName("iae_post")
    IAE_POST("iae_post"),

    /**
     * IAE POST JWT response mode
     *
     * Same as iae_post, but the response parameters are JWT-secured (JARM).
     * Provides additional privacy protection for the VP token in IAE flows.
     */
    @SerialName("iae_post.jwt")
    IAE_POST_JWT("iae_post.jwt"),
    ;

    companion object {
        /**
         * Parse response mode from string value
         *
         * @param value String value of response mode
         * @return ResponseMode enum value, or null if not recognized
         */
        @JvmStatic
        fun fromValue(value: String): ResponseMode? = entries.find { it.value == value }

        /**
         * Parse response mode from string value, with fallback
         *
         * @param value String value of response mode
         * @param default Default value if not recognized
         * @return ResponseMode enum value
         */
        @JvmStatic
        @JvmOverloads
        fun fromValueOrDefault(
            value: String,
            default: ResponseMode = FRAGMENT,
        ): ResponseMode = fromValue(value) ?: default
    }
}
