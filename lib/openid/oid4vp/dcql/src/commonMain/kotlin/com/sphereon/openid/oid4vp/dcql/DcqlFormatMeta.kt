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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonArray

/**
 * Format-specific metadata for credential queries
 *
 * Different credential formats support different metadata fields in the `meta` property
 * of a credential query. This sealed interface provides type-safe access to format-specific
 * metadata.
 *
 * Supported formats:
 * - SD-JWT VC (dc+sd-jwt)
 * - ISO mDoc (mso_mdoc)
 * - JWT VC JSON (jwt_vc_json)
 * - LDP VC (ldp_vc)
 *
 * @see DcqlCredentialQuery.meta
 */
@JsExportCompat
sealed interface DcqlFormatMeta

/**
 * SD-JWT VC Format Metadata
 *
 * Format-specific metadata for SD-JWT Verifiable Credentials (format: "dc+sd-jwt")
 *
 * OpenID4VP 1.0 Final Appendix B.3.5 defines only `vct_values` in the
 * Credential Query `meta` object. Algorithm capabilities belong to
 * `vp_formats_supported`, not DCQL.
 *
 * Example:
 * ```json
 * {
 *   "format": "dc+sd-jwt",
 *   "meta": {
 *     "vct_values": [
 *       "https://credentials.example.com/identity_credential",
 *       "https://credentials.example.com/resident_card"
 *     ]
 *   }
 * }
 * ```
 *
 * @property vct_values Array of acceptable Verifiable Credential Type URIs
 * @see DcqlCredentialQuery.meta
 */
@Serializable
data class SdJwtVcMeta(
    val vct_values: List<String>,
) : DcqlFormatMeta

/**
 * ISO mDoc Format Metadata
 *
 * Format-specific metadata for ISO/IEC 18013-5 mobile documents (format: "mso_mdoc")
 *
 * OpenID4VP 1.0 Final Appendix B.2.3 defines only `doctype_value` in the
 * Credential Query `meta` object. Namespaces are expressed by the two string
 * components of each mdoc Claims Path Pointer.
 *
 * Example:
 * ```json
 * {
 *   "format": "mso_mdoc",
 *   "meta": {"doctype_value": "org.iso.18013.5.1.mDL"}
 * }
 * ```
 *
 * @property doctype_value The document type identifier for this mDoc
 * @see DcqlCredentialQuery.meta
 */
@Serializable
data class MdocMeta(
    val doctype_value: String,
) : DcqlFormatMeta

/**
 * W3C Verifiable Credential Format Metadata
 *
 * OpenID4VP 1.0 Final Appendix B.1.1 defines only `type_values` in the
 * Credential Query `meta` object for W3C VC formats. Each inner array is one
 * alternative set of fully-expanded credential types that must all be present.
 *
 * Example:
 * ```json
 * {
 *   "format": "ldp_vc",
 *   "meta": {
 *     "type_values": [["VerifiableCredential", "UniversityDegreeCredential"]]
 *   }
 * }
 * ```
 *
 * @property type_values Alternative non-empty sets of acceptable credential types
 *
 * @see DcqlCredentialQuery.meta
 */
@Serializable
data class W3cVcMeta(
    val type_values: List<List<String>>,
) : DcqlFormatMeta

/** Creates the required OID4VP 1.0 Final Appendix B.3.5 SD-JWT VC metadata. */
fun sdJwtVcMeta(vararg vctValues: String): JsonObject =
    buildJsonObject {
        require(vctValues.isNotEmpty() && vctValues.all { it.isNotEmpty() }) { "vct_values must not be empty" }
        putJsonArray("vct_values") { vctValues.forEach { add(JsonPrimitive(it)) } }
    }

/** Creates the required OID4VP 1.0 Final Appendix B.2.3 mdoc metadata. */
fun mdocMeta(doctype: String): JsonObject =
    buildJsonObject {
        require(doctype.isNotEmpty()) { "doctype_value must not be empty" }
        put("doctype_value", JsonPrimitive(doctype))
    }

/** Creates the required OID4VP 1.0 Final Appendix B.1.1 W3C VC metadata. */
fun w3cVcMeta(vararg typeAlternatives: List<String>): JsonObject =
    buildJsonObject {
        require(typeAlternatives.isNotEmpty() && typeAlternatives.all { it.isNotEmpty() && it.all(String::isNotEmpty) }) {
            "type_values alternatives must not be empty"
        }
        putJsonArray("type_values") {
            typeAlternatives.forEach { alternative ->
                add(buildJsonArray { alternative.forEach { add(JsonPrimitive(it)) } })
            }
        }
    }
