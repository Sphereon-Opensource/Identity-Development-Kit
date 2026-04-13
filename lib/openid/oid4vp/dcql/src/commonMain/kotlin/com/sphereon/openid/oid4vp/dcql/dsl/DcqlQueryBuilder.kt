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
import com.sphereon.openid.oid4vp.dcql.DcqlClaimQuery
import com.sphereon.openid.oid4vp.dcql.DcqlClaimSet
import com.sphereon.openid.oid4vp.dcql.DcqlCredentialQuery
import com.sphereon.openid.oid4vp.dcql.DcqlCredentialSetOption
import com.sphereon.openid.oid4vp.dcql.DcqlCredentialSetQuery
import com.sphereon.openid.oid4vp.dcql.DcqlQuery
import com.sphereon.openid.oid4vp.dcql.DcqlTrustedAuthority
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.experimental.ExperimentalObjCName
import kotlin.js.JsName
import kotlin.native.ObjCName

// ============================================================================
// Query Builder (Top Level)
// ============================================================================

/**
 * Builder scope for DCQL queries.
 *
 * A DCQL query requests one or more credentials from a holder. It supports:
 * - Individual credential queries (for specific credentials)
 * - Credential set queries (for alternative credentials - OR logic)
 *
 * Example:
 * ```kotlin
 * val query = dcqlQuery {
 *     credential("identity") {
 *         sdJwtVc {
 *             vctValues("https://credentials.example.com/identity")
 *         }
 *         claim("given_name")
 *         claim("family_name")
 *     }
 *
 *     credentialSet {
 *         required()
 *         option("passport")
 *         option("drivers_license")
 *     }
 * }
 * ```
 */
@DcqlDslMarker
@OptIn(ExperimentalObjCName::class)
@ObjCName("DcqlQueryScope", exact = true)
@JsExportCompat
class DcqlQueryScope {
    private val credentials = mutableListOf<DcqlCredentialQuery>()
    private val credentialSets = mutableListOf<DcqlCredentialSetQuery>()

    /**
     * Adds a credential query for a specific credential.
     *
     * @param id Unique identifier for this credential query (used in responses)
     * @param builder Configuration for the credential query
     */
    fun credential(
        id: String,
        builder: DcqlCredentialQueryScope.() -> Unit,
    ) {
        val scope = DcqlCredentialQueryScope(id)
        scope.builder()
        credentials.add(scope.build())
    }

    /**
     * Adds a credential set query for alternative credentials (OR logic).
     *
     * Use this when any one of several credentials can satisfy a requirement.
     *
     * @param builder Configuration for the credential set query
     */
    fun credentialSet(builder: DcqlCredentialSetScope.() -> Unit) {
        val scope = DcqlCredentialSetScope()
        scope.builder()
        credentialSets.add(scope.build())
    }

    @PublishedApi
    internal fun build(): DcqlQuery =
        DcqlQuery(
            credentials = credentials.takeIf { it.isNotEmpty() },
            credential_sets = credentialSets.takeIf { it.isNotEmpty() },
        )
}

// ============================================================================
// Credential Query Builder
// ============================================================================

/**
 * Builder scope for individual credential queries.
 *
 * A credential query specifies:
 * - Format and format-specific metadata
 * - Required claims
 * - Trusted authorities
 * - Options like holder binding and multi-credential support
 *
 * Example:
 * ```kotlin
 * credential("identity") {
 *     sdJwtVc {
 *         vctValues("https://credentials.example.com/identity")
 *     }
 *     claim("given_name")
 *     claim("family_name")
 *     claim("address" then "street_address")  // Nested path
 *     trustedAuthorities {
 *         openIdFederation("https://federation.example.com")
 *     }
 *     requireHolderBinding(true)
 * }
 * ```
 */
@DcqlDslMarker
@OptIn(ExperimentalObjCName::class)
@ObjCName("DcqlCredentialQueryScope", exact = true)
@JsExportCompat
class DcqlCredentialQueryScope(
    private val id: String,
) {
    private var format: String? = null
    private var meta: JsonObject? = null
    private val claims = mutableListOf<DcqlClaimQuery>()
    private val claimSets = mutableListOf<DcqlClaimSet>()
    private var trustedAuthorities: List<DcqlTrustedAuthority>? = null
    private var requireCryptographicHolderBinding: Boolean = true
    private var multiple: Boolean = false

    // ========================================================================
    // Format Configuration
    // ========================================================================

    /**
     * Configures this credential query for SD-JWT VC format.
     *
     * Sets format to "dc+sd-jwt" and allows configuring SD-JWT specific metadata.
     */
    fun sdJwtVc(builder: SdJwtVcMetaScope.() -> Unit = {}) {
        format = DcqlFormats.SD_JWT_VC
        val scope = SdJwtVcMetaScope()
        scope.builder()
        meta = scope.buildMeta()
    }

    /**
     * Configures this credential query for SD-JWT VC format without metadata.
     *
     * Sets format to "dc+sd-jwt".
     */
    @JsName("sdJwtVcNoMeta")
    fun sdJwtVc() {
        format = DcqlFormats.SD_JWT_VC
        meta = null
    }

    /**
     * Configures this credential query for ISO mDoc format.
     *
     * Sets format to "mso_mdoc" and allows configuring mDoc specific metadata.
     */
    fun mDoc(builder: MdocMetaScope.() -> Unit = {}) {
        format = DcqlFormats.MSO_MDOC
        val scope = MdocMetaScope()
        scope.builder()
        meta = scope.buildMeta()
    }

    /**
     * Configures this credential query for JWT VC JSON format.
     *
     * Sets format to "jwt_vc_json" and allows configuring JWT VC specific metadata.
     */
    fun jwtVcJson(builder: JwtVcJsonMetaScope.() -> Unit = {}) {
        format = DcqlFormats.JWT_VC_JSON
        val scope = JwtVcJsonMetaScope()
        scope.builder()
        meta = scope.buildMeta()
    }

    /**
     * Configures this credential query for LDP VC format.
     *
     * Sets format to "ldp_vc" and allows configuring LDP VC specific metadata.
     */
    fun ldpVc(builder: LdpVcMetaScope.() -> Unit = {}) {
        format = DcqlFormats.LDP_VC
        val scope = LdpVcMetaScope()
        scope.builder()
        meta = scope.buildMeta()
    }

    // ========================================================================
    // Claims
    // ========================================================================

    /**
     * Adds a simple top-level claim request.
     *
     * @param name The claim name (e.g., "given_name", "email")
     */
    @JsName("claimSimple")
    fun claim(name: String) {
        claims.add(DcqlClaimQuery(path = listOf(name)))
    }

    /**
     * Adds a claim request with a path.
     *
     * @param path The path to the claim (e.g., listOf("address", "street_address"))
     */
    @JsName("claimPath")
    fun claim(path: List<String>) {
        claims.add(DcqlClaimQuery(path = path))
    }

    /**
     * Adds a claim request with a path and configuration.
     *
     * @param path The path to the claim
     * @param builder Configuration for value constraints and retention intent
     */
    fun claim(
        path: List<String>,
        builder: DcqlClaimQueryScope.() -> Unit,
    ) {
        val scope = DcqlClaimQueryScope(path)
        scope.builder()
        claims.add(scope.build())
    }

    /**
     * Adds a claim set (logical grouping of claims).
     *
     * @param id Unique identifier for this claim set
     * @param claimIds List of claim IDs belonging to this set
     */
    fun claimSet(
        id: String,
        claimIds: List<String>,
    ) {
        claimSets.add(DcqlClaimSet(id = id, claims = claimIds))
    }

    // ========================================================================
    // Trusted Authorities
    // ========================================================================

    /**
     * Configures trusted authorities for this credential query.
     *
     * @param builder Configuration for trusted authorities
     */
    fun trustedAuthorities(builder: TrustedAuthoritiesScope.() -> Unit) {
        val scope = TrustedAuthoritiesScope()
        scope.builder()
        trustedAuthorities = scope.build()
    }

    // ========================================================================
    // Options
    // ========================================================================

    /**
     * Sets whether cryptographic holder binding is required.
     *
     * When true (default), the credential must have holder binding that can be
     * cryptographically verified. When false, non-cryptographic binding is acceptable.
     *
     * @param required Whether to require holder binding
     */
    fun requireHolderBinding(required: Boolean) {
        requireCryptographicHolderBinding = required
    }

    /**
     * Sets whether multiple matching credentials can be presented.
     *
     * When true, the holder may present multiple credentials matching this query.
     * When false (default), only one credential should be presented.
     *
     * @param allow Whether to allow multiple credentials
     */
    fun allowMultiple(allow: Boolean) {
        multiple = allow
    }

    internal fun build(): DcqlCredentialQuery =
        DcqlCredentialQuery(
            id = id,
            format = format,
            meta = meta,
            claims = claims.takeIf { it.isNotEmpty() },
            claim_sets = claimSets.takeIf { it.isNotEmpty() },
            require_cryptographic_holder_binding = requireCryptographicHolderBinding,
            multiple = multiple,
            trusted_authorities = trustedAuthorities,
        )
}

// ============================================================================
// Claim Query Builder
// ============================================================================

/**
 * Builder scope for individual claim queries with constraints.
 *
 * Allows specifying:
 * - Value constraints (acceptable values)
 * - Intent to retain flag
 *
 * Example:
 * ```kotlin
 * claim(listOf("over_18")) {
 *     values(true)
 * }
 *
 * claim(listOf("email")) {
 *     intentToRetain()
 * }
 * ```
 */
@DcqlDslMarker
@OptIn(ExperimentalObjCName::class)
@ObjCName("DcqlClaimQueryScope", exact = true)
@JsExportCompat
class DcqlClaimQueryScope(
    private val path: List<String>,
) {
    private var values: List<JsonElement>? = null
    private var intentToRetain: Boolean? = null

    /**
     * Sets acceptable string values for this claim.
     *
     * @param acceptedValues One or more acceptable string values
     */
    @JsName("valuesString")
    fun values(vararg acceptedValues: String) {
        values = acceptedValues.map { JsonPrimitive(it) }
    }

    /**
     * Sets acceptable boolean values for this claim.
     *
     * @param acceptedValues One or more acceptable boolean values
     */
    @JsName("valuesBoolean")
    fun values(vararg acceptedValues: Boolean) {
        values = acceptedValues.map { JsonPrimitive(it) }
    }

    /**
     * Sets acceptable numeric values for this claim.
     *
     * @param acceptedValues One or more acceptable numeric values
     */
    @JsName("valuesNumber")
    fun values(vararg acceptedValues: Number) {
        values = acceptedValues.map { JsonPrimitive(it) }
    }

    /**
     * Sets acceptable JsonElement values for this claim.
     *
     * @param acceptedValues One or more acceptable JsonElement values
     */
    @JsName("valuesJsonElement")
    fun values(vararg acceptedValues: JsonElement) {
        values = acceptedValues.toList()
    }

    /**
     * Indicates that the verifier intends to retain this claim.
     *
     * This is an informational flag per privacy regulations.
     */
    fun intentToRetain() {
        intentToRetain = true
    }

    internal fun build(): DcqlClaimQuery =
        DcqlClaimQuery(
            path = path,
            values = values,
            intent_to_retain = intentToRetain,
        )
}

// ============================================================================
// Credential Set Builder
// ============================================================================

/**
 * Builder scope for credential set queries (OR logic).
 *
 * A credential set specifies alternative credentials that can satisfy the same
 * requirement. The holder may present any one of the options.
 *
 * Example:
 * ```kotlin
 * credentialSet {
 *     required()
 *     option("passport")
 *     option("drivers_license")
 *     option("national_id")
 * }
 * ```
 */
@DcqlDslMarker
@OptIn(ExperimentalObjCName::class)
@ObjCName("DcqlCredentialSetScope", exact = true)
@JsExportCompat
class DcqlCredentialSetScope {
    private var required: Boolean = false
    private val options = mutableListOf<DcqlCredentialSetOption>()

    /**
     * Marks this credential set as required.
     *
     * When required, at least one of the options MUST be satisfied.
     */
    fun required() {
        required = true
    }

    /**
     * Adds an option with a single credential ID.
     *
     * @param credentialId The credential ID that satisfies this option
     */
    @JsName("optionSingle")
    fun option(credentialId: String) {
        options.add(DcqlCredentialSetOption(credential_ids = listOf(credentialId)))
    }

    /**
     * Adds an option with multiple credential IDs.
     *
     * When multiple IDs are specified, ALL of those credentials must be
     * presented together to satisfy this option.
     *
     * @param credentialIds The credential IDs that together satisfy this option
     */
    @JsName("optionMultiple")
    fun option(vararg credentialIds: String) {
        options.add(DcqlCredentialSetOption(credential_ids = credentialIds.toList()))
    }

    internal fun build(): DcqlCredentialSetQuery =
        DcqlCredentialSetQuery(
            required = required,
            options = options,
        )
}

// ============================================================================
// Infix Functions for Path Building
// ============================================================================

/**
 * Infix function for building claim paths.
 *
 * Example:
 * ```kotlin
 * claim("address" then "street_address")  // Equivalent to claim(listOf("address", "street_address"))
 * ```
 */
infix fun String.then(other: String): List<String> = listOf(this, other)

/**
 * Infix function for extending claim paths.
 *
 * Example:
 * ```kotlin
 * claim("address" then "postal" then "code")  // path: ["address", "postal", "code"]
 * ```
 */
infix fun List<String>.then(other: String): List<String> = this + other
