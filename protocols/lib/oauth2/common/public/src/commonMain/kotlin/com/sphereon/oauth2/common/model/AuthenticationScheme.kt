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
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * HTTP authentication schemes for OAuth 2.0 access tokens
 *
 * Used by:
 * - Authorization Servers to specify token_type in token responses
 * - Resource Servers to parse Authorization headers
 * - Clients to construct Authorization headers
 *
 * RFC 6750 Section 2: Bearer token usage in HTTP requests
 * RFC 9449 Section 7: DPoP HTTP authentication scheme
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("AuthenticationScheme", exact = true)
@JsExportCompat
enum class AuthenticationScheme(
    val value: String,
) {
    /**
     * Bearer token authentication (RFC 6750)
     *
     * Example: Authorization: Bearer <access_token>
     */
    BEARER("Bearer"),

    /**
     * DPoP token authentication (RFC 9449)
     *
     * Example:
     * Authorization: DPoP <access_token>
     * DPoP: <proof_jwt>
     */
    DPOP("DPoP"),
    ;

    companion object {
        /**
         * Parse authentication scheme from string value
         *
         * @param value The scheme string (case-insensitive)
         * @return Matching scheme or null if unknown
         */
        fun fromValue(value: String): AuthenticationScheme? = entries.find { it.value.equals(value, ignoreCase = true) }
    }
}
