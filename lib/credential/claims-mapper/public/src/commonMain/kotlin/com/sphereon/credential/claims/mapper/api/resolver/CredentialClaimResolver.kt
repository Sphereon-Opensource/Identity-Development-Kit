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
 *
 */

package com.sphereon.credential.claims.mapper.api.resolver

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.openid.oid4vp.common.CredentialFormat
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Interface for extracting claims from verifiable credentials.
 *
 * Implementations of this interface handle the format-specific logic
 * for extracting claims from different credential types (SD-JWT, mDoc, W3C VC, etc.).
 *
 * The resolver pattern allows the mapper service to support multiple
 * credential formats without knowing the details of each format.
 */
interface CredentialClaimResolver {
    /**
     * The credential formats supported by this resolver.
     *
     * The mapper service uses this to select the appropriate resolver
     * for each credential based on its format.
     */
    val supportedFormats: Set<CredentialFormat>

    /**
     * Extract all claims from a credential.
     *
     * This method extracts all available claims from the credential
     * and returns them as a map. The keys are claim paths represented
     * as strings (e.g., "given_name", "address.street").
     *
     * @param credential The raw credential payload
     * @param format The credential format
     * @param disclosedClaims Optional pre-decoded claims (for SD-JWT, these are
     *   the claims after disclosure reconstruction)
     * @return Map of claim paths to values, or error
     */
    suspend fun extractAllClaims(
        credential: String,
        format: CredentialFormat,
        disclosedClaims: JsonObject? = null,
    ): IdkResult<Map<String, JsonElement>, IdkError>

    /**
     * Extract specific claims from a credential.
     *
     * This method extracts only the claims at the specified paths.
     * This is more efficient than extractAllClaims when only specific
     * claims are needed.
     *
     * @param credential The raw credential payload
     * @param format The credential format
     * @param claimPaths List of claim paths to extract. Each path is a list
     *   of property names to navigate into the credential structure.
     * @param disclosedClaims Optional pre-decoded claims
     * @return Map of claim paths to values, or error
     */
    suspend fun extractClaims(
        credential: String,
        format: CredentialFormat,
        claimPaths: List<List<String>>,
        disclosedClaims: JsonObject? = null,
    ): IdkResult<Map<String, JsonElement>, IdkError>

    /**
     * Extract a single claim from a credential.
     *
     * @param credential The raw credential payload
     * @param format The credential format
     * @param claimPath The path to the claim to extract (list of property names)
     * @param disclosedClaims Optional pre-decoded claims
     * @return The claim value if found, null if not found, or error
     */
    suspend fun extractClaim(
        credential: String,
        format: CredentialFormat,
        claimPath: List<String>,
        disclosedClaims: JsonObject? = null,
    ): IdkResult<JsonElement?, IdkError>

    /**
     * Check if this resolver supports the given format.
     */
    fun supports(format: CredentialFormat): Boolean = format in supportedFormats
}
