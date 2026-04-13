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

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.openid.oid4vp.dcql.DcqlError
import com.sphereon.openid.oid4vp.dcql.DcqlQuery
import com.sphereon.openid.oid4vp.dcql.ValidationErrorDetail
import com.sphereon.openid.oid4vp.dcql.toIdkResult
import com.sphereon.openid.oid4vp.dcql.validateDcqlQuery
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * Creates a DCQL query using a DSL builder.
 *
 * This is the main entry point for building DCQL queries. It provides a type-safe
 * DSL for constructing credential requests per OpenID4VP 1.0 Section 6.
 *
 * The built query is automatically validated. If validation fails, an exception is thrown
 * with details about the validation errors. Use [dcqlQueryResult] if you prefer
 * error handling via [IdkResult].
 *
 * ## Simple Query
 *
 * ```kotlin
 * val query = dcqlQuery {
 *     credential("identity") {
 *         sdJwtVc {
 *             vctValues("https://credentials.example.com/identity")
 *         }
 *         claim("given_name")
 *         claim("family_name")
 *         claim("birthdate")
 *     }
 * }
 * ```
 *
 * ## Complex Query with All Features
 *
 * ```kotlin
 * val query = dcqlQuery {
 *     credential("pid_credential") {
 *         // Type-safe format with metadata
 *         sdJwtVc {
 *             vctValues("https://credentials.example.com/pid")
 *             sdJwtAlgorithms("ES256", "ES384")
 *             kbJwtAlgorithms("ES256")
 *         }
 *
 *         // Simple claims
 *         claim("given_name")
 *         claim("family_name")
 *
 *         // Nested path (infix syntax)
 *         claim("address" then "street_address")
 *         claim("address" then "locality")
 *
 *         // Claim with value constraint
 *         claim(listOf("over_18")) {
 *             values(true)
 *         }
 *
 *         // Claim with intent to retain
 *         claim(listOf("email")) {
 *             intentToRetain()
 *         }
 *
 *         // Trusted authorities
 *         trustedAuthorities {
 *             openIdFederation("https://federation.example.com")
 *             etsiTrustedList("https://eidas.europa.eu/TL/EN_TL.xml")
 *         }
 *
 *         // Options
 *         requireHolderBinding(true)
 *         allowMultiple(false)
 *     }
 *
 *     // Alternative credentials (OR logic)
 *     credentialSet {
 *         required()
 *         option("passport")
 *         option("drivers_license")
 *         option("national_id")
 *     }
 * }
 * ```
 *
 * ## mDoc Query
 *
 * ```kotlin
 * val mdocQuery = dcqlQuery {
 *     credential("mdl") {
 *         mDoc {
 *             mDL()  // Convenience: sets doctype to "org.iso.18013.5.1.mDL"
 *             namespaces("org.iso.18013.5.1", "org.iso.18013.5.1.aamva")
 *         }
 *         claim("family_name")
 *         claim("given_name")
 *         claim("portrait")
 *     }
 * }
 * ```
 *
 * @param builderAction The DSL block to build the DCQL query
 * @return The constructed and validated [DcqlQuery]
 * @throws IllegalArgumentException if the query fails validation
 */
@OptIn(ExperimentalContracts::class)
inline fun dcqlQuery(builderAction: DcqlQueryScope.() -> Unit): DcqlQuery {
    contract {
        callsInPlace(builderAction, InvocationKind.EXACTLY_ONCE)
    }
    val scope = DcqlQueryScope()
    scope.builderAction()
    val query = scope.build()

    // Validate and throw on failure
    val result = validateDcqlQuery(query)
    if (result is io.konform.validation.Invalid) {
        val errors = result.errors.joinToString("; ") { "${it.path}: ${it.message}" }
        throw IllegalArgumentException("Invalid DCQL query: $errors")
    }

    return query
}

/**
 * Creates a DCQL query using a DSL builder, returning an [IdkResult].
 *
 * This is an alternative entry point that returns validation errors as [IdkResult.err]
 * instead of throwing exceptions. Use this when you prefer explicit error handling.
 *
 * Example:
 * ```kotlin
 * val result = dcqlQueryResult {
 *     credential("test") {
 *         sdJwtVc()
 *         claim("name")
 *     }
 * }
 *
 * when (result) {
 *     is Ok -> println("Query: ${result.value}")
 *     is Err -> println("Validation failed: ${result.error}")
 * }
 * ```
 *
 * @param builderAction The DSL block to build the DCQL query
 * @return [IdkResult.ok] with the query if valid, [IdkResult.err] with validation errors if invalid
 */
@OptIn(ExperimentalContracts::class)
inline fun dcqlQueryResult(builderAction: DcqlQueryScope.() -> Unit): IdkResult<DcqlQuery, DcqlError.ValidationError> {
    contract {
        callsInPlace(builderAction, InvocationKind.EXACTLY_ONCE)
    }
    val scope = DcqlQueryScope()
    scope.builderAction()

    // Build may throw if the query is fundamentally invalid (e.g., no credentials or credential_sets)
    val query =
        try {
            scope.build()
        } catch (e: IllegalArgumentException) {
            return com.sphereon.core.api.Err(
                DcqlError.ValidationError(
                    errors =
                        listOf(
                            ValidationErrorDetail(
                                path = "",
                                message = e.message ?: "Invalid DCQL query",
                            ),
                        ),
                ),
            )
        }

    return validateDcqlQuery(query).toIdkResult()
}
