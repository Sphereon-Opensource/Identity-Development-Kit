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

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * OAuth 2.0 / OIDC response-mode (OIDC Core §3.1.2.1, OAuth 2.0 Form Post Response Mode, JARM).
 *
 * Controls how the authorization server returns the authorization response to the client.
 * The OID4VP-specific `ResponseMode` at `com.sphereon.openid.oid4vp.common.ResponseMode` is
 * a separate concept (direct_post/iae_post/etc.) and does not overlap with this enum.
 *
 * The `*.jwt` variants are JARM (JWT Secured Authorization Response Mode) per OpenID Foundation
 * https://openid.net/specs/oauth-v2-jarm.html: the authorization response parameters become a
 * signed (and optionally encrypted) JWT carried in a single `response` parameter.
 */
@JsExportCompat
@Serializable
enum class OAuth2ResponseMode(
    val value: String,
    /**
     * `true` when this response mode requires JARM packaging (JWT response) per the OIDF JARM spec.
     * The underlying delivery mechanism is given by [carrier].
     */
    val isJarm: Boolean = false,
    /**
     * For JARM modes, the underlying carrier that delivers the response JWT (query, fragment, or
     * form post). For non-JARM modes, this is the mode itself.
     */
    val carrier: Carrier = Carrier.QUERY,
) {
    /** Response parameters appended as query string on the redirect URI (default for `code` flow). */
    @SerialName("query")
    QUERY("query", carrier = Carrier.QUERY),

    /** Response parameters appended as URL fragment (default for implicit/hybrid flows). */
    @SerialName("fragment")
    FRAGMENT("fragment", carrier = Carrier.FRAGMENT),

    /** Response parameters returned as HTML form auto-submitted via POST to the redirect URI. */
    @SerialName("form_post")
    FORM_POST("form_post", carrier = Carrier.FORM_POST),

    /**
     * JARM "jwt" mode (OIDF JARM §2.1): a single signed/encrypted JWT delivered via the carrier
     * derived from the response_type (query for `code`, fragment otherwise). The verifier resolves
     * this against the actual response_type before delivery and shapes accordingly.
     */
    @SerialName("jwt")
    JWT("jwt", isJarm = true, carrier = Carrier.QUERY),

    /** JARM `query.jwt`: single signed/encrypted JWT delivered as `?response=<jwt>` query param. */
    @SerialName("query.jwt")
    QUERY_JWT("query.jwt", isJarm = true, carrier = Carrier.QUERY),

    /** JARM `fragment.jwt`: single signed/encrypted JWT delivered as `#response=<jwt>` URL fragment. */
    @SerialName("fragment.jwt")
    FRAGMENT_JWT("fragment.jwt", isJarm = true, carrier = Carrier.FRAGMENT),

    /** JARM `form_post.jwt`: single signed/encrypted JWT delivered via auto-submitting HTML form. */
    @SerialName("form_post.jwt")
    FORM_POST_JWT("form_post.jwt", isJarm = true, carrier = Carrier.FORM_POST),
    ;

    /**
     * Mechanism a response mode uses to carry the response payload to the client. JARM modes
     * inherit a carrier from their `*.jwt` suffix (`query.jwt` → QUERY); the bare `jwt` mode
     * resolves its carrier from the requested response_type at use time.
     */
    public enum class Carrier {
        QUERY,
        FRAGMENT,
        FORM_POST,
    }

    public companion object {
        /**
         * Parse a wire-format response_mode string. Returns `null` for unrecognised values so the
         * verifier can surface `invalid_request` / `unsupported_response_mode` with its own
         * error mapping rather than throwing.
         */
        public fun parse(raw: String?): OAuth2ResponseMode? =
            raw?.let { r ->
                entries.firstOrNull { it.value.equals(r, ignoreCase = false) }
                    ?: entries.firstOrNull { it.value.equals(r, ignoreCase = true) }
            }

        /** Default response mode per OIDC Core §3.1.2.1 given a requested response_type. */
        public fun defaultFor(responseTypes: List<ResponseType>): OAuth2ResponseMode =
            when {
                // code only → query
                responseTypes.size == 1 && responseTypes.contains(ResponseType.CODE) -> QUERY

                // implicit or hybrid (includes id_token/token) → fragment
                responseTypes.contains(ResponseType.ID_TOKEN) || responseTypes.contains(ResponseType.TOKEN) -> FRAGMENT

                else -> QUERY
            }

        /**
         * Resolve the bare JARM `jwt` mode against a requested response_type per OIDF JARM §2.1:
         * `jwt` becomes `query.jwt` for `response_type=code`, `fragment.jwt` otherwise. Other
         * (already-explicit) JARM modes pass through unchanged.
         */
        public fun resolveJarmCarrier(
            mode: OAuth2ResponseMode,
            responseTypes: List<ResponseType>,
        ): OAuth2ResponseMode =
            if (mode == JWT) {
                if (responseTypes.size == 1 && responseTypes.contains(ResponseType.CODE)) QUERY_JWT else FRAGMENT_JWT
            } else {
                mode
            }
    }
}
