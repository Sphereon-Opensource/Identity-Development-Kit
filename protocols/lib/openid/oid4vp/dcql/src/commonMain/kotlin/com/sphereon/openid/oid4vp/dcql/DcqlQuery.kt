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

package com.sphereon.openid.oid4vp.dcql

import com.sphereon.core.compat.JsExportCompat
import io.konform.validation.Invalid
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.jvm.JvmInline

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
 * A query MUST contain a non-empty `credentials` array. `credential_sets`, when present, adds
 * constraints over identifiers from that array; it is not an alternative to `credentials`.
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
@JsExportCompat
data class DcqlQuery(
    val credentials: List<DcqlCredentialQuery>,
    val credential_sets: List<DcqlCredentialSetQuery>? = null,
) {
    init {
        require(credentials.isNotEmpty()) { "'credentials' must contain at least one Credential Query" }
        requireValid("DcqlQuery", validateDcqlQuery(this))
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
 * @property format Required credential format (e.g., "dc+sd-jwt" or "mso_mdoc")
 * @property meta Required format-specific metadata. An empty object applies no metadata constraint
 * for extension formats; Appendix B defines required properties for the Final standard formats.
 * @property claims Optional list of specific claims to request from this credential
 * @property claim_sets Optional list of claim sets (logical groupings of claims)
 * @property require_cryptographic_holder_binding Whether cryptographic Holder Binding is required (default: true)
 * @property multiple Whether multiple matching credentials can be presented (default: false)
 * @property trusted_authorities Optional list of trusted authorities that may have issued the credential
 *
 * @see DcqlClaimQuery
 * @see DcqlTrustedAuthority
 * @see SdJwtVcMeta
 * @see MdocMeta
 */
@Serializable
@JsExportCompat
data class DcqlCredentialQuery(
    val id: String,
    val format: String,
    val meta: JsonObject,
    val claims: List<DcqlClaimQuery>? = null,
    val claim_sets: List<List<String>>? = null,
    val require_cryptographic_holder_binding: Boolean = true,
    val multiple: Boolean = false,
    @SerialName("trusted_authorities")
    val trusted_authorities: List<DcqlTrustedAuthority>? = null,
) {
    init {
        when (format) {
            "dc+sd-jwt" -> {
                val values = meta["vct_values"] as? JsonArray
                require(values != null && values.isNotEmpty() && values.all { it is JsonPrimitive && it.isString && it.content.isNotEmpty() }) {
                    "dc+sd-jwt meta.vct_values is required and must be a non-empty array of non-empty type identifiers"
                }
            }

            "mso_mdoc" -> {
                val doctype = meta["doctype_value"] as? JsonPrimitive
                require(doctype?.isString == true && doctype.content.isNotEmpty()) {
                    "mso_mdoc meta.doctype_value is required and must be a non-empty doctype identifier"
                }
            }

            "jwt_vc_json", "ldp_vc" -> {
                val alternatives = meta["type_values"] as? JsonArray
                require(
                    alternatives != null && alternatives.isNotEmpty() && alternatives.all { alternative ->
                        alternative is JsonArray && alternative.isNotEmpty() &&
                            alternative.all { it is JsonPrimitive && it.isString && it.content.isNotEmpty() }
                    },
                ) {
                    "$format meta.type_values is required and must contain non-empty alternative sets of fully expanded types"
                }
            }
        }
        require(format == "mso_mdoc" || claims.orEmpty().none { it.intent_to_retain != null }) {
            "intent_to_retain is only defined for mso_mdoc Claims Queries in OpenID4VP 1.0 Final"
        }
        requireValid("DcqlCredentialQuery", validateDcqlCredentialQuery(this))
    }

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
            if (authorities.isEmpty()) {
                return authorities
            }

            // Group by type and combine all values
            val grouped = authorities.groupBy { it.type }

            // Maintain order of first occurrence for each type
            return authorities
                .distinctBy { it.type }
                .map { firstOfType ->
                    val allValues = grouped[firstOfType.type]!!.flatMap { it.values }.distinct()
                    DcqlTrustedAuthority(
                        type = firstOfType.type,
                        values = allValues,
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
 * OpenID4VP 1.0 Final Section 6.3 and Appendix B.2.4:
 * "A claim query specifies a single claim that the verifier requests from a credential.
 * The claim is identified by a Claims Path Pointer containing strings, nulls, and non-negative
 * integers as defined by OpenID4VP 1.0 Final Section 7.
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
 * {"path": ["degrees", 0, "name"]}
 *
 * // With value constraint
 * {"path": ["over_18"], "values": [true]}
 *
 * ```
 *
 * @property path JSON path to the claim (array of property names/indices)
 * @property values Optional list of acceptable values for this claim (constraint)
 * @property intent_to_retain Optional ISO mdoc IntentToRetain value; invalid for other formats
 *
 * @see DcqlCredentialQuery
 */
@Serializable
@JsExportCompat
data class DcqlClaimQuery(
    val path: ClaimsPathPointer,
    val id: String? = null,
    val values: List<JsonElement>? = null,
    val intent_to_retain: Boolean? = null,
) {
    init {
        requireValid("DcqlClaimQuery", validateDcqlClaimQuery(this))
    }
}

/**
 * OpenID4VP 1.0 Final Claims Path Pointer.
 *
 * This value class serializes as the JSON array itself. It prevents draft-era string-only paths
 * from leaking into protocol logic while keeping traversal independent of credential format.
 */
@Serializable
@JvmInline
value class ClaimsPathPointer(
    val components: List<JsonElement>,
) {
    init {
        require(components.isNotEmpty()) { "Claims Path Pointer must not be empty" }
        components.forEachIndexed { index, component ->
            require(
                component is JsonNull ||
                    component is JsonPrimitive &&
                    (component.isString || component.longOrNull?.let { it >= 0 } == true),
            ) {
                "Claims Path Pointer component at index $index must be a string, null, or non-negative integer"
            }
        }
    }
}

/** Creates a Final Claims Path Pointer containing string path components. */
fun claimsPathPointer(vararg components: String): ClaimsPathPointer =
    ClaimsPathPointer(components.map(::JsonPrimitive))

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
 *     ["passport"],
 *     ["drivers_license"],
 *     ["national_id"]
 *   ]
 * }
 * ```
 *
 * This means: "Present EITHER a passport OR a driver's license OR a national ID"
 *
 * @property required Whether at least one option from this set MUST be satisfied (default: true)
 * @property options List of alternative credential options (any one can satisfy the requirement)
 *
 * @see DcqlQuery
 */
@Serializable
@JsExportCompat
data class DcqlCredentialSetQuery(
    val required: Boolean = true,
    val options: List<List<String>>,
) {
    init {
        requireValid("DcqlCredentialSetQuery", validateDcqlCredentialSetQuery(this))
    }
}

private fun requireValid(
    type: String,
    validationResult: io.konform.validation.ValidationResult<*>,
) {
    if (validationResult is Invalid) {
        val errors = validationResult.errors.joinToString("; ") { "${it.path}: ${it.message}" }
        throw IllegalArgumentException("Invalid $type: $errors")
    }
}
