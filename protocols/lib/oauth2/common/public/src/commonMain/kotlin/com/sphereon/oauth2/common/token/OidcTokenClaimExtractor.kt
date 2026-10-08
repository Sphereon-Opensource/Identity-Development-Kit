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

package com.sphereon.oauth2.common.token

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.core.compat.JsExportIgnoreCompat
import kotlinx.serialization.json.JsonElement

/**
 * Utility for extracting claims from OIDC/OAuth2 JWT tokens (ID tokens, access tokens).
 *
 * Performs payload decoding only — no signature verification.
 * Used by reconciliation and potentially STS for claim extraction.
 */
@JsExportCompat
interface OidcTokenClaimExtractor {
    /**
     * Decode all claims from a JWT payload.
     *
     * @param jwt The compact JWT string (header.payload.signature)
     * @return All claims as a map, or error if the JWT is malformed
     */
    @JsExportIgnoreCompat
    fun extractAllClaims(jwt: String): IdkResult<Map<String, JsonElement>, IdkError>

    /**
     * Extract a specific claim by path (e.g., ["address", "street"] for nested claims).
     *
     * @param jwt The compact JWT string
     * @param claimPath Path segments to navigate through nested JSON objects
     * @return The claim value, null if the claim doesn't exist, or error if JWT is malformed
     */
    fun extractClaim(
        jwt: String,
        claimPath: List<String>,
    ): IdkResult<JsonElement?, IdkError>
}
