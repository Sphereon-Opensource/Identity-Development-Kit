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
import io.konform.validation.constraints.minLength

/**
 * Known credential formats per OpenID4VP 1.0
 */
private val KNOWN_FORMATS =
    setOf(
        "dc+sd-jwt", // SD-JWT VC
        "mso_mdoc", // ISO mDoc
        "jwt_vc_json", // W3C VC JWT
        "ldp_vc", // W3C VC LDP
        "jwt_vp", // JWT VP
        "ldp_vp", // LDP VP
    )

/**
 * Validates DCQL Query
 *
 * OpenID4VP 1.0 Section 6:
 * - At least one of `credentials` or `credential_sets` MUST be present
 * - If present, arrays MUST be non-empty
 */
val validateDcqlQuery =
    Validation<DcqlQuery> {
        // At least one must be present (enforced by data class init)

        DcqlQuery::credentials ifPresent {
            constrain("must contain at least one credential query") { it.isNotEmpty() }
        }

        DcqlQuery::credential_sets ifPresent {
            constrain("must contain at least one credential set") { it.isNotEmpty() }
        }
    }

/**
 * Validates DCQL Credential Query
 *
 * OpenID4VP 1.0 Section 6.1:
 * - `id` MUST be a non-empty string
 * - `format` if present MUST be a known format identifier
 * - `claims` if present MUST be a non-empty array
 * - `claim_sets` if present MUST be a non-empty array
 * - `trusted_authorities` if present MUST be a non-empty array
 */
val validateDcqlCredentialQuery =
    Validation<DcqlCredentialQuery> {
        DcqlCredentialQuery::id {
            minLength(1) hint "Credential ID cannot be empty"
        }

        DcqlCredentialQuery::format ifPresent {
            constrain("must be a known credential format: ${KNOWN_FORMATS.joinToString()}") { it in KNOWN_FORMATS }
        }

        DcqlCredentialQuery::claims ifPresent {
            constrain("must contain at least one claim") { it.isNotEmpty() }
        }

        DcqlCredentialQuery::claim_sets ifPresent {
            constrain("must contain at least one claim set") { it.isNotEmpty() }
        }

        DcqlCredentialQuery::trusted_authorities ifPresent {
            constrain("must contain at least one trusted authority") { it.isNotEmpty() }
        }
    }

/**
 * Validates DCQL Claim Query
 *
 * OpenID4VP 1.0 Section 6.2:
 * - `path` MUST be a non-empty array
 * - `path` elements MUST be non-empty strings
 * - `values` if present MUST be non-empty
 */
val validateDcqlClaimQuery =
    Validation<DcqlClaimQuery> {
        DcqlClaimQuery::path {
            constrain("path cannot be empty") { it.isNotEmpty() }
            constrain("path elements cannot be empty strings") { it.all { s -> s.isNotEmpty() } }
        }

        DcqlClaimQuery::values ifPresent {
            constrain("values array cannot be empty") { it.isNotEmpty() }
        }
    }

/**
 * Validates DCQL Claim Set
 *
 * OpenID4VP 1.0 Section 6.3:
 * - `id` MUST be a non-empty string
 * - `claims` MUST be a non-empty array
 * - `claims` elements MUST be non-empty strings
 */
val validateDcqlClaimSet =
    Validation<DcqlClaimSet> {
        DcqlClaimSet::id {
            minLength(1) hint "Claim set ID cannot be empty"
        }

        DcqlClaimSet::claims {
            constrain("must contain at least one claim") { it.isNotEmpty() }
            constrain("claim identifiers cannot be empty strings") { it.all { s -> s.isNotEmpty() } }
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
        }
    }

/**
 * Validates DCQL Credential Set Option
 *
 * OpenID4VP 1.0 Section 6.4:
 * - `credential_ids` MUST be a non-empty array
 * - `credential_ids` elements MUST be non-empty strings
 */
val validateDcqlCredentialSetOption =
    Validation<DcqlCredentialSetOption> {
        DcqlCredentialSetOption::credential_ids {
            constrain("must reference at least one credential") { it.isNotEmpty() }
            constrain("credential IDs cannot be empty strings") { it.all { s -> s.isNotEmpty() } }
        }
    }

/**
 * Validates DCQL Response
 *
 * OpenID4VP 1.0 Section 6.5:
 * - At least one of `credential_matches` or `credential_set_matches` SHOULD be present
 * - If present, arrays MUST be non-empty
 */
val validateDcqlResponse =
    Validation<DcqlResponse> {
        DcqlResponse::credential_matches ifPresent {
            constrain("cannot be empty if present") { it.isNotEmpty() }
        }

        DcqlResponse::credential_set_matches ifPresent {
            constrain("cannot be empty if present") { it.isNotEmpty() }
        }
    }

/**
 * Validates DCQL Credential Match
 *
 * OpenID4VP 1.0 Section 6.5.1:
 * - `credential_id` MUST be a non-empty string
 * - `claims_satisfied` if present MUST be non-empty
 */
val validateDcqlCredentialMatch =
    Validation<DcqlCredentialMatch> {
        DcqlCredentialMatch::credential_id {
            minLength(1) hint "Credential ID cannot be empty"
        }

        DcqlCredentialMatch::claims_satisfied ifPresent {
            constrain("cannot be empty if present") { it.isNotEmpty() }
            constrain("claim paths cannot be empty strings") { it.all { s -> s.isNotEmpty() } }
        }
    }

/**
 * Validates DCQL Credential Set Match
 *
 * OpenID4VP 1.0 Section 6.5.2:
 * - `credential_set_id` MUST be a non-empty string
 * - `credential_id` MUST be a non-empty string
 */
val validateDcqlCredentialSetMatch =
    Validation<DcqlCredentialSetMatch> {
        DcqlCredentialSetMatch::credential_set_id {
            minLength(1) hint "Credential set ID cannot be empty"
        }

        DcqlCredentialSetMatch::credential_id {
            minLength(1) hint "Credential ID cannot be empty"
        }
    }

/**
 * Validates SD-JWT VC format metadata
 *
 * OpenID4VP 1.0 Appendix A.1:
 * - `vct_values` if present MUST be non-empty and contain valid URIs
 * - `sd_jwt_alg_values` if present MUST be non-empty
 * - `kb_jwt_alg_values` if present MUST be non-empty
 */
val validateSdJwtVcMeta =
    Validation<SdJwtVcMeta> {
        SdJwtVcMeta::vct_values ifPresent {
            constrain("cannot be empty if present") { it.isNotEmpty() }
            constrain("VCT values cannot be empty strings") { it.all { s -> s.isNotEmpty() } }
        }

        SdJwtVcMeta::sd_jwt_alg_values ifPresent {
            constrain("cannot be empty if present") { it.isNotEmpty() }
            constrain("algorithm values cannot be empty strings") { it.all { s -> s.isNotEmpty() } }
        }

        SdJwtVcMeta::kb_jwt_alg_values ifPresent {
            constrain("cannot be empty if present") { it.isNotEmpty() }
            constrain("algorithm values cannot be empty strings") { it.all { s -> s.isNotEmpty() } }
        }
    }

/**
 * Validates ISO mDoc format metadata
 *
 * OpenID4VP 1.0 Appendix A.2:
 * - `doctype_value` if present MUST be non-empty
 * - `namespace_values` if present MUST be non-empty
 */
val validateMdocMeta =
    Validation<MdocMeta> {
        MdocMeta::doctype_value ifPresent {
            minLength(1) hint "doctype_value cannot be empty if present"
        }

        MdocMeta::namespace_values ifPresent {
            constrain("cannot be empty if present") { it.isNotEmpty() }
            constrain("namespace values cannot be empty strings") { it.all { s -> s.isNotEmpty() } }
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
                if (authority.type == DcqlTrustedAuthority.TYPE_ETSI_TRUSTED_LIST) {
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
