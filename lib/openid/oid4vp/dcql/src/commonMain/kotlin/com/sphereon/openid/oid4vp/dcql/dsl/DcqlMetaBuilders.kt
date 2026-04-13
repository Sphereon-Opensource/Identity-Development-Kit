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
 * - `sd_jwt_alg_values`: Acceptable JWS algorithms for SD-JWT
 * - `kb_jwt_alg_values`: Acceptable JWS algorithms for Key Binding JWT
 *
 * Example:
 * ```kotlin
 * credential("identity") {
 *     sdJwtVc {
 *         vctValues("https://credentials.example.com/identity")
 *         sdJwtAlgorithms("ES256", "ES384")
 *         kbJwtAlgorithms("ES256")
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
    private val sdJwtAlgValues = mutableListOf<String>()
    private val kbJwtAlgValues = mutableListOf<String>()

    /**
     * Sets acceptable Verifiable Credential Type URIs.
     *
     * @param values One or more VCT URIs (e.g., "https://credentials.example.com/identity")
     */
    fun vctValues(vararg values: String) {
        vctValues.addAll(values)
    }

    /**
     * Sets acceptable JWS algorithms for SD-JWT signing.
     *
     * @param algorithms One or more algorithm names (e.g., "ES256", "RS256")
     */
    fun sdJwtAlgorithms(vararg algorithms: String) {
        sdJwtAlgValues.addAll(algorithms)
    }

    /**
     * Sets acceptable JWS algorithms for Key Binding JWT.
     *
     * @param algorithms One or more algorithm names (e.g., "ES256")
     */
    fun kbJwtAlgorithms(vararg algorithms: String) {
        kbJwtAlgValues.addAll(algorithms)
    }

    internal fun buildMeta(): JsonObject? {
        if (vctValues.isEmpty() && sdJwtAlgValues.isEmpty() && kbJwtAlgValues.isEmpty()) {
            return null
        }

        return buildJsonObject {
            if (vctValues.isNotEmpty()) {
                putJsonArray("vct_values") {
                    vctValues.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
                }
            }
            if (sdJwtAlgValues.isNotEmpty()) {
                putJsonArray("sd_jwt_alg_values") {
                    sdJwtAlgValues.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
                }
            }
            if (kbJwtAlgValues.isNotEmpty()) {
                putJsonArray("kb_jwt_alg_values") {
                    kbJwtAlgValues.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
                }
            }
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
 * - `namespace_values`: Acceptable namespace identifiers
 *
 * Example:
 * ```kotlin
 * credential("mdl") {
 *     mDoc {
 *         mDL()  // Convenience: sets doctype to MDL
 *         namespaces("org.iso.18013.5.1", "org.iso.18013.5.1.aamva")
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
    private val namespaceValues = mutableListOf<String>()

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

    /**
     * Sets acceptable namespace identifiers.
     *
     * @param namespaces One or more namespace identifiers
     */
    fun namespaces(vararg namespaces: String) {
        namespaceValues.addAll(namespaces)
    }

    internal fun buildMeta(): JsonObject? {
        if (doctypeValue == null && namespaceValues.isEmpty()) {
            return null
        }

        return buildJsonObject {
            doctypeValue?.let {
                put("doctype_value", kotlinx.serialization.json.JsonPrimitive(it))
            }
            if (namespaceValues.isNotEmpty()) {
                putJsonArray("namespace_values") {
                    namespaceValues.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
                }
            }
        }
    }
}

// ============================================================================
// JWT VC JSON Metadata Builder
// ============================================================================

/**
 * Builder scope for JWT VC JSON format metadata.
 *
 * JWT VC JSON (format: "jwt_vc_json") supports the following metadata properties:
 * - `type_values`: Acceptable credential type identifiers from the 'type' array
 * - `alg_values`: Acceptable JWS signing algorithms
 *
 * Example:
 * ```kotlin
 * credential("degree") {
 *     jwtVcJson {
 *         types("VerifiableCredential", "UniversityDegreeCredential")
 *         algorithms("ES256", "ES384")
 *     }
 * }
 * ```
 */
@DcqlDslMarker
@OptIn(ExperimentalObjCName::class)
@ObjCName("JwtVcJsonMetaScope", exact = true)
@JsExportCompat
class JwtVcJsonMetaScope {
    private val typeValues = mutableListOf<String>()
    private val algValues = mutableListOf<String>()

    /**
     * Sets acceptable credential type identifiers.
     *
     * @param types One or more type values (e.g., "VerifiableCredential", "UniversityDegreeCredential")
     */
    fun types(vararg types: String) {
        typeValues.addAll(types)
    }

    /**
     * Sets acceptable JWS signing algorithms.
     *
     * @param algorithms One or more algorithm names (e.g., "ES256", "RS256")
     */
    fun algorithms(vararg algorithms: String) {
        algValues.addAll(algorithms)
    }

    internal fun buildMeta(): JsonObject? {
        if (typeValues.isEmpty() && algValues.isEmpty()) {
            return null
        }

        return buildJsonObject {
            if (typeValues.isNotEmpty()) {
                putJsonArray("type_values") {
                    typeValues.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
                }
            }
            if (algValues.isNotEmpty()) {
                putJsonArray("alg_values") {
                    algValues.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
                }
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
 * - `type_values`: Acceptable credential type identifiers from the 'type' array
 * - `proof_type_values`: Acceptable Linked Data Proof types
 *
 * Example:
 * ```kotlin
 * credential("degree") {
 *     ldpVc {
 *         types("VerifiableCredential", "UniversityDegreeCredential")
 *         proofTypes("Ed25519Signature2020", "JsonWebSignature2020")
 *     }
 * }
 * ```
 */
@DcqlDslMarker
@OptIn(ExperimentalObjCName::class)
@ObjCName("LdpVcMetaScope", exact = true)
@JsExportCompat
class LdpVcMetaScope {
    private val typeValues = mutableListOf<String>()
    private val proofTypeValues = mutableListOf<String>()

    /**
     * Sets acceptable credential type identifiers.
     *
     * @param types One or more type values (e.g., "VerifiableCredential", "UniversityDegreeCredential")
     */
    fun types(vararg types: String) {
        typeValues.addAll(types)
    }

    /**
     * Sets acceptable Linked Data Proof types.
     *
     * @param proofTypes One or more proof type names (e.g., "Ed25519Signature2020")
     */
    fun proofTypes(vararg proofTypes: String) {
        proofTypeValues.addAll(proofTypes)
    }

    internal fun buildMeta(): JsonObject? {
        if (typeValues.isEmpty() && proofTypeValues.isEmpty()) {
            return null
        }

        return buildJsonObject {
            if (typeValues.isNotEmpty()) {
                putJsonArray("type_values") {
                    typeValues.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
                }
            }
            if (proofTypeValues.isNotEmpty()) {
                putJsonArray("proof_type_values") {
                    proofTypeValues.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
                }
            }
        }
    }
}
