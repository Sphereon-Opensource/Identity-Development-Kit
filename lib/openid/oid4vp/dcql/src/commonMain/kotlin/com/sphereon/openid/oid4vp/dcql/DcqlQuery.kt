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

package com.sphereon.openid.oid4vp.dcql

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Digital Credentials Query Language (DCQL) Query
 *
 * Top-level query object for requesting credentials from a holder.
 *
 * OpenID4VP 1.0 Section 6:
 * "DCQL is a JSON-based query language that allows a verifier to request specific credentials
 * and claims from a holder. It provides a simpler and more flexible alternative to DIF
 * Presentation Exchange."
 *
 * A query MUST contain at least one of:
 * - `credentials`: A list of specific credential requests
 * - `credential_sets`: A list of credential set requests (where one option must be satisfied)
 *
 * Example:
 * ```json
 * {
 *   "credentials": [
 *     {
 *       "id": "my_credential",
 *       "format": "dc+sd-jwt",
 *       "claims": [
 *         {"path": ["last_name"]},
 *         {"path": ["first_name"]}
 *       ]
 *     }
 *   ]
 * }
 * ```
 *
 * @property credentials List of individual credential queries
 * @property credential_sets List of credential set queries (disjunctions)
 *
 * @see DcqlCredentialQuery
 * @see DcqlCredentialSetQuery
 */
@Serializable
data class DcqlQuery(
    val credentials: List<DcqlCredentialQuery>? = null,
    val credential_sets: List<DcqlCredentialSetQuery>? = null
) {
    init {
        require(credentials != null || credential_sets != null) {
            "At least one of 'credentials' or 'credential_sets' must be present"
        }
    }
}

/**
 * DCQL Credential Query
 *
 * Requests a specific credential with optional constraints on format, metadata, and claims.
 *
 * OpenID4VP 1.0 Section 6.1:
 * "A credential query specifies a single credential that the verifier requests from the holder.
 * It can include constraints on the credential format, format-specific metadata, and specific
 * claims to be disclosed."
 *
 * Example:
 * ```json
 * {
 *   "id": "identity_credential",
 *   "format": "dc+sd-jwt",
 *   "meta": {
 *     "vct_values": ["https://credentials.example.com/identity_credential"]
 *   },
 *   "claims": [
 *     {"path": ["last_name"]},
 *     {"path": ["first_name"]},
 *     {"path": ["birth_date"]}
 *   ],
 *   "require_cryptographic_holder_binding": true,
 *   "multiple": false,
 *   "trusted_authorities": [
 *     {
 *       "type": "openid_federation",
 *       "values": ["https://federation.example.com"]
 *     }
 *   ]
 * }
 * ```
 *
 * @property id Unique identifier for this credential query (used in responses)
 * @property format Optional credential format (e.g., "dc+sd-jwt", "mso_mdoc", "jwt_vc_json")
 * @property meta Optional format-specific metadata (e.g., vct_values for SD-JWT VC, doctype_value for mDoc)
 * @property claims Optional list of specific claims to request from this credential
 * @property claim_sets Optional list of claim sets (logical groupings of claims)
 * @property require_cryptographic_holder_binding Whether cryptographic holder binding is required (default: true)
 * @property multiple Whether multiple matching credentials can be presented (default: false)
 * @property trusted_authorities Optional list of trusted authorities that may have issued the credential
 *
 * @see DcqlClaimQuery
 * @see DcqlClaimSet
 * @see DcqlTrustedAuthority
 * @see SdJwtVcMeta
 * @see MdocMeta
 */
@Serializable
data class DcqlCredentialQuery(
    val id: String,
    val format: String? = null,
    val meta: JsonObject? = null,
    val claims: List<DcqlClaimQuery>? = null,
    val claim_sets: List<DcqlClaimSet>? = null,
    val require_cryptographic_holder_binding: Boolean = true,
    val multiple: Boolean = false,

    @SerialName("trusted_authorities")
    val trusted_authorities: List<DcqlTrustedAuthority>? = null
) {
    /**
     * Normalized trusted authorities with duplicate types automatically combined.
     *
     * When multiple authority entries of the same type exist in trusted_authorities,
     * they are automatically combined into a single entry with all values merged.
     * This prevents logic/validation problems by external parties.
     *
     * This property is computed in the init block and always reflects the deduplicated state.
     *
     * Example:
     * ```kotlin
     * // Input during construction/deserialization:
     * listOf(
     *   DcqlTrustedAuthority("openid_federation", listOf("https://fed1.com")),
     *   DcqlTrustedAuthority("openid_federation", listOf("https://fed2.com"))
     * )
     * // Automatically normalized to:
     * listOf(
     *   DcqlTrustedAuthority("openid_federation", listOf("https://fed1.com", "https://fed2.com"))
     * )
     * ```
     */
    @Transient
    val normalizedTrustedAuthorities: List<DcqlTrustedAuthority>? = trusted_authorities?.let { normalizeTrustedAuthorities(it) }

    companion object {
        /**
         * Combines multiple trusted authority entries of the same type into single entries.
         *
         * @param authorities Original list that may contain duplicate types
         * @return List with duplicate types merged, maintaining order of first occurrence
         */
        private fun normalizeTrustedAuthorities(authorities: List<DcqlTrustedAuthority>): List<DcqlTrustedAuthority> {
            if (authorities.isEmpty()) return authorities

            // Group by type and combine all values
            val grouped = authorities.groupBy { it.type }

            // Maintain order of first occurrence for each type
            return authorities
                .distinctBy { it.type }
                .map { firstOfType ->
                    val allValues = grouped[firstOfType.type]!!.flatMap { it.values }.distinct()
                    DcqlTrustedAuthority(
                        type = firstOfType.type,
                        values = allValues
                    )
                }
        }
    }
}

/**
 * DCQL Claim Query
 *
 * Requests a specific claim from a credential using a JSON path.
 *
 * OpenID4VP 1.0 Section 6.2:
 * "A claim query specifies a single claim that the verifier requests from a credential.
 * The claim is identified by a path (array of strings) that navigates the credential's
 * JSON structure."
 *
 * Examples:
 * ```json
 * // Simple top-level claim
 * {"path": ["last_name"]}
 *
 * // Nested claim
 * {"path": ["address", "street_address"]}
 *
 * // Array element
 * {"path": ["degrees", "0", "name"]}
 *
 * // With value constraint
 * {"path": ["over_18"], "values": [true]}
 *
 * // With intent to retain
 * {"path": ["email"], "intent_to_retain": true}
 * ```
 *
 * @property path JSON path to the claim (array of property names/indices)
 * @property values Optional list of acceptable values for this claim (constraint)
 * @property intent_to_retain Optional flag indicating verifier intends to retain this claim
 *
 * @see DcqlCredentialQuery
 */
@Serializable
data class DcqlClaimQuery(
    val path: List<String>,
    val values: List<JsonElement>? = null,
    val intent_to_retain: Boolean? = null
)

/**
 * DCQL Claim Set
 *
 * Logical grouping of claims that can be referenced by ID.
 *
 * OpenID4VP 1.0 Section 6.3:
 * "A claim set allows grouping multiple claims under a single identifier. This is useful
 * for organizing related claims and for expressing disjunctions (OR logic) between different
 * sets of claims."
 *
 * Example:
 * ```json
 * {
 *   "id": "basic_identity",
 *   "claims": ["first_name", "last_name", "birth_date"]
 * }
 * ```
 *
 * @property id Unique identifier for this claim set
 * @property claims List of claim IDs or paths belonging to this set
 *
 * @see DcqlCredentialQuery
 */
@Serializable
data class DcqlClaimSet(
    val id: String,
    val claims: List<String>
)

/**
 * DCQL Credential Set Query
 *
 * Requests one credential from a set of alternative options (disjunction/OR logic).
 *
 * OpenID4VP 1.0 Section 6.4:
 * "A credential set query allows the verifier to specify alternative credentials that
 * can satisfy the same requirement. The holder may present any one of the options.
 * This is useful when multiple credential types can satisfy the same verification need."
 *
 * Example:
 * ```json
 * {
 *   "required": true,
 *   "options": [
 *     {"credential_ids": ["passport"]},
 *     {"credential_ids": ["drivers_license"]},
 *     {"credential_ids": ["national_id"]}
 *   ]
 * }
 * ```
 *
 * This means: "Present EITHER a passport OR a driver's license OR a national ID"
 *
 * @property required Whether at least one option from this set MUST be satisfied (default: false)
 * @property options List of alternative credential options (any one can satisfy the requirement)
 *
 * @see DcqlCredentialSetOption
 * @see DcqlQuery
 */
@Serializable
data class DcqlCredentialSetQuery(
    val required: Boolean = false,
    val options: List<DcqlCredentialSetOption>
)

/**
 * DCQL Credential Set Option
 *
 * One alternative option in a credential set query.
 *
 * OpenID4VP 1.0 Section 6.4:
 * "Each option in a credential set specifies one or more credential IDs that together
 * satisfy the requirement. If multiple IDs are listed, ALL of those credentials must
 * be presented together."
 *
 * Example:
 * ```json
 * // Single credential option
 * {"credential_ids": ["passport"]}
 *
 * // Multiple credentials required together
 * {"credential_ids": ["university_id", "transcript"]}
 * ```
 *
 * @property credential_ids List of credential IDs that satisfy this option
 *
 * @see DcqlCredentialSetQuery
 */
@Serializable
data class DcqlCredentialSetOption(
    val credential_ids: List<String>
)
