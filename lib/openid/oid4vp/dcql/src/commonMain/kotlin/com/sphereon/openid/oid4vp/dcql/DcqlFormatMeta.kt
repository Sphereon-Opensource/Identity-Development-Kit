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

import kotlinx.serialization.Serializable

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
sealed interface DcqlFormatMeta

/**
 * SD-JWT VC Format Metadata
 *
 * Format-specific metadata for SD-JWT Verifiable Credentials (format: "dc+sd-jwt")
 *
 * OpenID4VP 1.0 Appendix A.1 (SD-JWT VC):
 * "The meta object for SD-JWT VC format supports the following properties:
 * - vct_values: Array of acceptable Verifiable Credential Types
 * - sd_jwt_alg_values: Array of acceptable signing algorithms for the SD-JWT
 * - kb_jwt_alg_values: Array of acceptable signing algorithms for the Key Binding JWT"
 *
 * Example:
 * ```json
 * {
 *   "format": "dc+sd-jwt",
 *   "meta": {
 *     "vct_values": [
 *       "https://credentials.example.com/identity_credential",
 *       "https://credentials.example.com/resident_card"
 *     ],
 *     "sd_jwt_alg_values": ["ES256", "ES384"],
 *     "kb_jwt_alg_values": ["ES256"]
 *   }
 * }
 * ```
 *
 * @property vct_values Array of acceptable Verifiable Credential Type URIs
 * @property sd_jwt_alg_values Array of acceptable JWS algorithms for SD-JWT (e.g., "ES256", "RS256")
 * @property kb_jwt_alg_values Array of acceptable JWS algorithms for Key Binding JWT
 *
 * @see DcqlCredentialQuery.meta
 */
@Serializable
data class SdJwtVcMeta(
    val vct_values: List<String>? = null,
    val sd_jwt_alg_values: List<String>? = null,
    val kb_jwt_alg_values: List<String>? = null
) : DcqlFormatMeta

/**
 * ISO mDoc Format Metadata
 *
 * Format-specific metadata for ISO/IEC 18013-5 mobile documents (format: "mso_mdoc")
 *
 * OpenID4VP 1.0 Appendix A.2 (ISO mDoc):
 * "The meta object for mso_mdoc format supports the following properties:
 * - doctype_value: The document type identifier (e.g., 'org.iso.18013.5.1.mDL')
 * - namespace_values: Array of acceptable namespace identifiers"
 *
 * Example:
 * ```json
 * {
 *   "format": "mso_mdoc",
 *   "meta": {
 *     "doctype_value": "org.iso.18013.5.1.mDL",
 *     "namespace_values": [
 *       "org.iso.18013.5.1",
 *       "org.iso.18013.5.1.aamva"
 *     ]
 *   }
 * }
 * ```
 *
 * @property doctype_value The document type identifier for this mDoc
 * @property namespace_values Array of acceptable namespace identifiers for claims
 *
 * @see DcqlCredentialQuery.meta
 */
@Serializable
data class MdocMeta(
    val doctype_value: String? = null,
    val namespace_values: List<String>? = null
) : DcqlFormatMeta

/**
 * JWT VC JSON Format Metadata
 *
 * Format-specific metadata for W3C Verifiable Credentials in JWT format (format: "jwt_vc_json")
 *
 * OpenID4VP 1.0 Appendix A.3 (JWT VC JSON):
 * "The meta object for jwt_vc_json format supports the following properties:
 * - type_values: Array of acceptable credential type identifiers from the 'type' array
 * - alg_values: Array of acceptable JWS signing algorithms"
 *
 * Example:
 * ```json
 * {
 *   "format": "jwt_vc_json",
 *   "meta": {
 *     "type_values": ["VerifiableCredential", "UniversityDegreeCredential"],
 *     "alg_values": ["ES256", "ES384"]
 *   }
 * }
 * ```
 *
 * @property type_values Array of acceptable credential types from the VC 'type' array
 * @property alg_values Array of acceptable JWS signing algorithms
 *
 * @see DcqlCredentialQuery.meta
 */
@Serializable
data class JwtVcJsonMeta(
    val type_values: List<String>? = null,
    val alg_values: List<String>? = null
) : DcqlFormatMeta

/**
 * LDP VC Format Metadata
 *
 * Format-specific metadata for W3C Verifiable Credentials with Linked Data Proofs (format: "ldp_vc")
 *
 * OpenID4VP 1.0 Appendix A.4 (LDP VC):
 * "The meta object for ldp_vc format supports the following properties:
 * - type_values: Array of acceptable credential type identifiers from the 'type' array
 * - proof_type_values: Array of acceptable proof types (e.g., 'Ed25519Signature2020')"
 *
 * Example:
 * ```json
 * {
 *   "format": "ldp_vc",
 *   "meta": {
 *     "type_values": ["VerifiableCredential", "UniversityDegreeCredential"],
 *     "proof_type_values": ["Ed25519Signature2020", "JsonWebSignature2020"]
 *   }
 * }
 * ```
 *
 * @property type_values Array of acceptable credential types from the VC 'type' array
 * @property proof_type_values Array of acceptable Linked Data Proof types
 *
 * @see DcqlCredentialQuery.meta
 */
@Serializable
data class LdpVcMeta(
    val type_values: List<String>? = null,
    val proof_type_values: List<String>? = null
) : DcqlFormatMeta
