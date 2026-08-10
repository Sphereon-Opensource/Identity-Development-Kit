/*
 * Copyright (c) 2026 Sphereon B.V.
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
package com.sphereon.openid.oid4vp.dcql.dsl

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonArray
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

// ============================================================================
// SD-JWT VC Metadata Builder
// ============================================================================

/**
 * Builder scope for SD-JWT VC format metadata.
 *
 * SD-JWT VC (format: "dc+sd-jwt") supports the following metadata properties:
 * - `vct_values`: Acceptable Verifiable Credential Type URIs
 *
 * Example:
 * ```kotlin
 * credential("identity") {
 *     sdJwtVc {
 *         vctValues("https://credentials.example.com/identity")
 *     }
 * }
 * ```
 */
@DcqlDslMarker
@OptIn(ExperimentalObjCName::class)
@ObjCName("SdJwtVcMetaScope", exact = true)
@JsExportCompat
class SdJwtVcMetaScope {
    private val vctValues = mutableListOf<String>()

    /**
     * Sets acceptable Verifiable Credential Type URIs.
     *
     * @param values One or more VCT URIs (e.g., "https://credentials.example.com/identity")
     */
    fun vctValues(vararg values: String) {
        vctValues.addAll(values)
    }

    internal fun buildMeta(): JsonObject =
        buildJsonObject {
            require(vctValues.isNotEmpty() && vctValues.all { it.isNotEmpty() }) {
                "dc+sd-jwt requires at least one non-empty vct_values entry"
            }
            putJsonArray("vct_values") {
                vctValues.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
            }
        }
}

// ============================================================================
// mDoc Metadata Builder
// ============================================================================

/**
 * Builder scope for ISO mDoc format metadata.
 *
 * mDoc (format: "mso_mdoc") supports the following metadata properties:
 * - `doctype_value`: Document type identifier (e.g., "org.iso.18013.5.1.mDL")
 *
 * Example:
 * ```kotlin
 * credential("mdl") {
 *     mDoc {
 *         mDL()  // Convenience: sets doctype to MDL
 *     }
 * }
 * ```
 */
@DcqlDslMarker
@OptIn(ExperimentalObjCName::class)
@ObjCName("MdocMetaScope", exact = true)
@JsExportCompat
class MdocMetaScope {
    private var doctypeValue: String? = null

    /**
     * Sets the document type identifier.
     *
     * @param doctype The doctype value (e.g., "org.iso.18013.5.1.mDL")
     */
    fun doctype(doctype: String) {
        doctypeValue = doctype
    }

    /**
     * Convenience method to set doctype to ISO/IEC 18013-5 Mobile Driving License.
     *
     * Equivalent to `doctype("org.iso.18013.5.1.mDL")`
     */
    fun mDL() {
        doctypeValue = MdocDoctypes.MDL
    }

    internal fun buildMeta(): JsonObject =
        buildJsonObject {
            val doctype = requireNotNull(doctypeValue) { "mso_mdoc requires doctype_value" }
            require(doctype.isNotEmpty()) { "mso_mdoc doctype_value must not be empty" }
            put("doctype_value", kotlinx.serialization.json.JsonPrimitive(doctype))
        }
}

// ============================================================================
// JWT VC JSON Metadata Builder
// ============================================================================

/**
 * Builder scope for JWT VC JSON format metadata.
 *
 * JWT VC JSON (format: "jwt_vc_json") supports the following metadata properties:
 * - `type_values`: Alternative sets of fully-expanded credential type identifiers
 *
 * Example:
 * ```kotlin
 * credential("degree") {
 *     jwtVcJson {
 *         typeValues("VerifiableCredential", "UniversityDegreeCredential")
 *     }
 * }
 * ```
 */
@DcqlDslMarker
@OptIn(ExperimentalObjCName::class)
@ObjCName("JwtVcJsonMetaScope", exact = true)
@JsExportCompat
class JwtVcJsonMetaScope {
    private val typeValues = mutableListOf<List<String>>()

    /**
     * Sets acceptable credential type identifiers.
     *
     * @param types One or more type values (e.g., "VerifiableCredential", "UniversityDegreeCredential")
     */
    fun typeValues(vararg types: String) {
        require(types.isNotEmpty()) { "A type_values alternative must contain at least one type" }
        typeValues.add(types.toList())
    }

    internal fun buildMeta(): JsonObject =
        buildJsonObject {
            require(typeValues.isNotEmpty() && typeValues.flatten().all { it.isNotEmpty() }) {
                "jwt_vc_json requires non-empty type_values alternatives"
            }
            putJsonArray("type_values") {
                typeValues.forEach { alternative ->
                    add(kotlinx.serialization.json.buildJsonArray {
                        alternative.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
                    })
                }
            }
        }
}

// ============================================================================
// LDP VC Metadata Builder
// ============================================================================

/**
 * Builder scope for LDP VC format metadata.
 *
 * LDP VC (format: "ldp_vc") supports the following metadata properties:
 * - `type_values`: Alternative sets of fully-expanded credential type identifiers
 *
 * Example:
 * ```kotlin
 * credential("degree") {
 *     ldpVc {
 *         typeValues("VerifiableCredential", "UniversityDegreeCredential")
 *     }
 * }
 * ```
 */
@DcqlDslMarker
@OptIn(ExperimentalObjCName::class)
@ObjCName("LdpVcMetaScope", exact = true)
@JsExportCompat
class LdpVcMetaScope {
    private val typeValues = mutableListOf<List<String>>()

    /**
     * Sets acceptable credential type identifiers.
     *
     * @param types One or more type values (e.g., "VerifiableCredential", "UniversityDegreeCredential")
     */
    fun typeValues(vararg types: String) {
        require(types.isNotEmpty()) { "A type_values alternative must contain at least one type" }
        typeValues.add(types.toList())
    }

    internal fun buildMeta(): JsonObject =
        buildJsonObject {
            require(typeValues.isNotEmpty() && typeValues.flatten().all { it.isNotEmpty() }) {
                "ldp_vc requires non-empty type_values alternatives"
            }
            putJsonArray("type_values") {
                typeValues.forEach { alternative ->
                    add(kotlinx.serialization.json.buildJsonArray {
                        alternative.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
                    })
                }
            }
        }
}
