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

import io.konform.validation.Validation
import io.konform.validation.Valid
import io.konform.validation.constraints.minLength
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull

private val DCQL_ID = Regex("^[A-Za-z0-9_-]+$")

/**
 * Validates DCQL Query
 *
 * OpenID4VP 1.0 Section 6:
 * - `credentials` MUST be present and non-empty
 * - `credential_sets`, if present, MUST be non-empty and reference Credential Query IDs
 */
val validateDcqlQuery =
    Validation<DcqlQuery> {
        DcqlQuery::credentials {
            constrain("must contain at least one credential query") { it.isNotEmpty() }
            constrain("credential query IDs must be unique") { credentials ->
                credentials.map { it.id }.distinct().size == credentials.size
            }
            constrain("contains an invalid Credential Query") { credentials ->
                credentials.all { validateDcqlCredentialQuery(it) is Valid }
            }
        }

        DcqlQuery::credential_sets ifPresent {
            constrain("must contain at least one credential set") { it.isNotEmpty() }
            constrain("contains an invalid Credential Set Query") { credentialSets ->
                credentialSets.all { validateDcqlCredentialSetQuery(it) is Valid }
            }
        }

        run {
            constrain("credential set options must reference Credential Query IDs") { query ->
                val ids = query.credentials.map { it.id }.toSet()
                query.credential_sets.orEmpty().flatMap { it.options }.flatten().all { it in ids }
            }
        }
    }

/**
 * Validates DCQL Credential Query
 *
 * OpenID4VP 1.0 Section 6.1:
 * - `id` MUST be a non-empty string
 * - `format` MUST be a non-empty Credential Format Identifier
 * - `claims` if present MUST be a non-empty array
 * - `claim_sets` if present MUST be a non-empty array
 * - `trusted_authorities` if present MUST be a non-empty array
 */
val validateDcqlCredentialQuery =
    Validation<DcqlCredentialQuery> {
        DcqlCredentialQuery::id {
            minLength(1) hint "Credential ID cannot be empty"
            constrain("must contain only alphanumeric, underscore, or hyphen characters") { it.matches(DCQL_ID) }
        }

        DcqlCredentialQuery::format {
            minLength(1) hint "Credential format cannot be empty"
        }

        DcqlCredentialQuery::claims ifPresent {
            constrain("must contain at least one claim") { it.isNotEmpty() }
            constrain("contains an invalid Claims Query") { claims ->
                claims.all { validateDcqlClaimQuery(it) is Valid }
            }
        }

        DcqlCredentialQuery::claim_sets ifPresent {
            constrain("must contain at least one claim set") { it.isNotEmpty() }
            constrain("claim set options must be non-empty") { sets -> sets.all { it.isNotEmpty() } }
        }

        DcqlCredentialQuery::trusted_authorities ifPresent {
            constrain("must contain at least one trusted authority") { it.isNotEmpty() }
            constrain("contains an invalid Trusted Authorities Query") { authorities ->
                authorities.all { validateDcqlTrustedAuthority(it) is Valid }
            }
        }

        run {
            constrain("claim_sets requires claims and every claim to have a unique valid id") { query ->
                if (query.claim_sets == null) {
                    true
                } else {
                    val ids = query.claims.orEmpty().mapNotNull { it.id }
                    query.claims != null &&
                        ids.size == query.claims.size &&
                        ids.distinct().size == ids.size &&
                        ids.all { it.matches(DCQL_ID) }
                }
            }
            constrain("claim_sets must reference claim ids from the same Credential Query") { query ->
                val ids = query.claims.orEmpty().mapNotNull { it.id }.toSet()
                query.claim_sets.orEmpty().flatten().all { it in ids }
            }
            constrain("mso_mdoc claim paths must contain exactly two strings") { query ->
                query.format != "mso_mdoc" ||
                    query.claims.orEmpty().all { claim ->
                        claim.path.components.size == 2 &&
                            claim.path.components.all { it is JsonPrimitive && it.isString }
                    }
            }
        }
    }

/**
 * Validates DCQL Claim Query
 *
 * OpenID4VP 1.0 Final Sections 6.3 and 7:
 * - `path` MUST be a non-empty array
 * - `path` elements MUST be non-empty strings
 * - `values` if present MUST be non-empty
 */
val validateDcqlClaimQuery =
    Validation<DcqlClaimQuery> {
        DcqlClaimQuery::path {
            constrain("path cannot be empty") { it.components.isNotEmpty() }
        }

        DcqlClaimQuery::id ifPresent {
            constrain("must contain only alphanumeric, underscore, or hyphen characters") { it.matches(DCQL_ID) }
        }

        DcqlClaimQuery::values ifPresent {
            constrain("values array cannot be empty") { it.isNotEmpty() }
            constrain("values must contain only strings, integers, or booleans") { values ->
                values.all { value ->
                    value is JsonPrimitive &&
                        (value.isString || value.longOrNull != null || value.booleanOrNull != null)
                }
            }
        }
    }

/**
 * Validates DCQL Credential Set Query
 *
 * OpenID4VP 1.0 Section 6.4:
 * - `options` MUST be a non-empty array
 */
val validateDcqlCredentialSetQuery =
    Validation<DcqlCredentialSetQuery> {
        DcqlCredentialSetQuery::options {
            constrain("must contain at least one option") { it.isNotEmpty() }
            constrain("each option must reference at least one Credential Query") { options ->
                options.all { it.isNotEmpty() }
            }
            constrain("credential query IDs cannot be empty or malformed") { options ->
                options.flatten().all { it.matches(DCQL_ID) }
            }
        }
    }

/**
 * Validates SD-JWT VC format metadata
 *
 * OpenID4VP 1.0 Final Appendix B.3.5:
 * - `vct_values` is the only defined DCQL meta property for `dc+sd-jwt`
 * - `vct_values` is REQUIRED, non-empty, and contains non-empty type identifiers
 */
val validateSdJwtVcMeta =
    Validation<SdJwtVcMeta> {
        SdJwtVcMeta::vct_values {
            constrain("cannot be empty") { it.isNotEmpty() }
            constrain("VCT values cannot be empty strings") { it.all { s -> s.isNotEmpty() } }
        }
    }

/**
 * Validates ISO mDoc format metadata
 *
 * OpenID4VP 1.0 Final Appendix B.2.3:
 * - `doctype_value` is the only defined DCQL meta property for `mso_mdoc`
 * - `doctype_value` is REQUIRED and non-empty
 */
val validateMdocMeta =
    Validation<MdocMeta> {
        MdocMeta::doctype_value {
            minLength(1) hint "doctype_value cannot be empty"
        }
    }

/** Validates OpenID4VP 1.0 Final Appendix B.1.1 W3C VC `type_values`. */
val validateW3cVcMeta =
    Validation<W3cVcMeta> {
        W3cVcMeta::type_values {
            constrain("cannot be empty") { it.isNotEmpty() }
            constrain("each type_values alternative must be non-empty") { alternatives ->
                alternatives.all { it.isNotEmpty() }
            }
            constrain("type values cannot be empty strings") { alternatives ->
                alternatives.flatten().all { it.isNotEmpty() }
            }
        }
    }

/**
 * Validates DCQL Trusted Authority
 *
 * OpenID4VP 1.0 Section 6.1.1:
 * - `type` MUST be a non-empty string and one of the valid types
 * - `values` MUST be a non-empty array
 * - `values` elements MUST be non-empty strings
 * - For ETSI Trusted List: values MUST be HTTPS URLs
 * - For OpenID Federation: values MUST be HTTPS URLs
 */
val validateDcqlTrustedAuthority =
    Validation<DcqlTrustedAuthority> {
        DcqlTrustedAuthority::type {
            minLength(1) hint "Trusted authority type cannot be empty"
            constrain("must be one of: ${DcqlTrustedAuthority.VALID_TYPES.joinToString()}") {
                it in DcqlTrustedAuthority.VALID_TYPES
            }
        }

        DcqlTrustedAuthority::values {
            constrain("must contain at least one value") { it.isNotEmpty() }
            constrain("values cannot contain empty strings") { it.all { s -> s.isNotEmpty() } }
        }

        // Type-specific validation
        run {
            constrain("ETSI Trusted List values must be HTTPS URLs") { authority ->
                if (authority.type == DcqlTrustedAuthority.TYPE_ETSI_TL) {
                    authority.values.all { it.startsWith("https://", ignoreCase = true) }
                } else {
                    true
                }
            }

            constrain("OpenID Federation values must be HTTPS URLs") { authority ->
                if (authority.type == DcqlTrustedAuthority.TYPE_OPENID_FEDERATION) {
                    authority.values.all { it.startsWith("https://", ignoreCase = true) }
                } else {
                    true
                }
            }
        }
    }
