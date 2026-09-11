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

package com.sphereon.statuslist

import com.sphereon.core.api.error.ErrorCategory
import com.sphereon.core.api.error.IdkError

/** Stable, machine-readable domain errors for the status-list feature. */
object StatusListErrors {
    fun invalidMdocProfile(reason: String): IdkError =
        IdkError.fromString(
            code = "STATUSLIST_INVALID_MDOC_PROFILE",
            message = "Invalid ISO/IEC 18013-5 mdoc status-list profile: $reason",
            category = ErrorCategory.VALIDATION,
        )
    fun listNotFound(ref: String): IdkError =
        IdkError.fromString(
            code = "STATUSLIST_LIST_NOT_FOUND",
            message = "Status list not found: $ref",
            category = ErrorCategory.NOT_FOUND,
        )

    fun entryNotFound(ref: String): IdkError =
        IdkError.fromString(
            code = "STATUSLIST_ENTRY_NOT_FOUND",
            message = "Status list entry not found: $ref",
            category = ErrorCategory.NOT_FOUND,
        )

    fun duplicateCorrelationId(correlationId: String): IdkError =
        IdkError.fromString(
            code = "STATUSLIST_DUPLICATE_CORRELATION_ID",
            message = "A status list with correlationId '$correlationId' already exists",
            category = ErrorCategory.CONFLICT,
        )

    fun duplicateStatusListUri(statusListUri: String): IdkError =
        IdkError.fromString(
            code = "STATUSLIST_DUPLICATE_URI",
            message = "A status list is already hosted at '$statusListUri'",
            category = ErrorCategory.CONFLICT,
        )

    fun incompatibleDefinitionRefresh(
        correlationId: String,
        field: String,
    ): IdkError =
        IdkError.fromString(
            code = "STATUSLIST_INCOMPATIBLE_DEFINITION_REFRESH",
            message =
                "Status list '$correlationId' cannot change structural field '$field' after creation; " +
                    "create a new status-list resource instead",
            category = ErrorCategory.CONFLICT,
        )

    /** Structural fields cannot be changed without invalidating already-issued references/bits. */
    fun validateDefinitionRefresh(
        existing: StatusListResult,
        requested: CreateStatusListArgs,
    ): IdkError? =
        when {
            existing.correlationId != requested.correlationId -> incompatibleDefinitionRefresh(existing.correlationId, "correlationId")
            existing.spec != requested.spec -> incompatibleDefinitionRefresh(existing.correlationId, "spec")
            existing.proofFormat != requested.proofFormat -> incompatibleDefinitionRefresh(existing.correlationId, "proofFormat")
            existing.purposes != requested.purposes -> incompatibleDefinitionRefresh(existing.correlationId, "purposes")
            existing.bitsPerStatus != requested.bitsPerStatus -> incompatibleDefinitionRefresh(existing.correlationId, "bitsPerStatus")
            existing.length != requested.length -> incompatibleDefinitionRefresh(existing.correlationId, "length")
            existing.statusListUri != requested.statusListUri -> incompatibleDefinitionRefresh(existing.correlationId, "statusListUri")
            existing.mdocProfile != requested.mdocProfile -> incompatibleDefinitionRefresh(existing.correlationId, "mdocProfile")
            existing.aggregationUri != requested.aggregationUri -> incompatibleDefinitionRefresh(existing.correlationId, "aggregationUri")
            else -> null
        }

    fun indexInUse(index: Int): IdkError =
        IdkError.fromString(
            code = "STATUSLIST_INDEX_IN_USE",
            message = "Status list index $index is already in use",
            category = ErrorCategory.CONFLICT,
        )

    fun indexOutOfRange(
        index: Int,
        length: Int,
    ): IdkError =
        IdkError.fromString(
            code = "STATUSLIST_INDEX_OUT_OF_RANGE",
            message = "Status list index $index is out of range [0, $length)",
            category = ErrorCategory.VALIDATION,
        )

    fun listExhausted(ref: String): IdkError =
        IdkError.fromString(
            code = "STATUSLIST_EXHAUSTED",
            message = "Status list '$ref' has no free indices remaining",
            category = ErrorCategory.CONFLICT,
        )

    fun invalidStatusValue(
        value: Int,
        bitsPerStatus: Int,
    ): IdkError =
        IdkError.fromString(
            code = "STATUSLIST_INVALID_STATUS_VALUE",
            message = "Status value $value does not fit in $bitsPerStatus bit(s) (allowed [0, ${1 shl bitsPerStatus}))",
            category = ErrorCategory.VALIDATION,
        )

    fun unsupportedProofFormat(
        spec: StatusListSpec,
        format: StatusProofFormat,
    ): IdkError =
        IdkError.fromString(
            code = "STATUSLIST_UNSUPPORTED_PROOF_FORMAT",
            message = "Proof format $format is not supported for $spec",
            category = ErrorCategory.VALIDATION,
        )

    fun resolutionFailed(
        uri: String,
        reason: String,
        cause: Throwable? = null,
    ): IdkError =
        IdkError.fromString(
            code = "STATUSLIST_RESOLUTION_FAILED",
            message = "Failed to resolve status list at $uri: $reason",
            category = ErrorCategory.INTERNAL,
            exception = cause as? Exception,
        )

    fun verificationFailed(
        uri: String,
        reason: String,
        cause: Throwable? = null,
    ): IdkError =
        IdkError.fromString(
            code = "STATUSLIST_VERIFICATION_FAILED",
            message = "Status list at $uri failed verification: $reason",
            category = ErrorCategory.VALIDATION,
            exception = cause as? Exception,
        )

    fun bitstringListTooShort(bits: Long): IdkError =
        IdkError.fromString(
            code = "STATUSLIST_LENGTH_TOO_SHORT",
            message =
                "A Bitstring Status List must encode at least $MIN_BITSTRING_STATUS_LIST_BITS bits (16KB) " +
                    "for herd privacy (length * bitsPerStatus); got $bits",
            category = ErrorCategory.VALIDATION,
        )

    fun enricherUnavailable(credentialConfigurationId: String): IdkError =
        IdkError.fromString(
            code = "STATUSLIST_ENRICHER_UNAVAILABLE",
            message =
                "Credential configuration '$credentialConfigurationId' is bound to a status list, but no " +
                    "CredentialStatusEnricher is available on this deployment; refusing to issue a credential " +
                    "that could never be revoked",
            category = ErrorCategory.UNAVAILABLE,
        )

    fun enrichmentUnsupportedForFormat(
        credentialConfigurationId: String,
        format: String,
    ): IdkError =
        IdkError.fromString(
            code = "STATUSLIST_ENRICHMENT_UNSUPPORTED_FORMAT",
            message =
                "Credential configuration '$credentialConfigurationId' is bound to a status list, but the " +
                    "'$format' format handler does not support status enrichment; refusing to issue a credential " +
                    "that could never be revoked",
            category = ErrorCategory.UNAVAILABLE,
        )

    /**
     * An ISO mdoc status-list profile is meaningful only when it is embedded in an mso_mdoc MSO.
     * Silently treating such a binding as an ordinary JWT/VC status claim would produce a token
     * whose configured revocation representation cannot be resolved by an mdoc verifier.
     */
    fun mdocProfileUnsupportedForFormat(
        credentialConfigurationId: String,
        format: String,
    ): IdkError =
        IdkError.fromString(
            code = "STATUSLIST_MDOC_PROFILE_UNSUPPORTED_FORMAT",
            message =
                "Credential configuration '$credentialConfigurationId' binds an ISO 18013-5 " +
                    "status-list profile, but format '$format' is not mso_mdoc; refusing to issue " +
                    "a credential with an incompatible status representation",
            category = ErrorCategory.VALIDATION,
        )

    fun bindingDefinitionMismatch(
        statusListId: String,
        field: String,
    ): IdkError =
        IdkError.fromString(
            code = "STATUSLIST_BINDING_DEFINITION_MISMATCH",
            message =
                "Status-list binding for '$statusListId' does not match the stored definition at '$field'; " +
                    "refusing to issue with an ambiguous status representation",
            category = ErrorCategory.VALIDATION,
        )

    fun bindingUnresolvable(
        credentialConfigurationId: String,
        statusListId: String,
        reason: String,
    ): IdkError =
        IdkError.fromString(
            code = "STATUSLIST_BINDING_UNRESOLVABLE",
            message =
                "Credential configuration '$credentialConfigurationId' declares status list '$statusListId' " +
                    "but the binding cannot be resolved: $reason; refusing to issue a credential that could " +
                    "never be revoked",
            category = ErrorCategory.UNAVAILABLE,
        )

    /**
     * Single, uniform refusal raised by a signer that signs with a KMS key and was handed no key
     * name: no binding, a binding that cannot be honoured, or no configured key on a deployment that
     * manages its own. The wording never varies by cause, so the caller learns nothing about which
     * status lists hold which key material.
     */
    fun signingKeyUnresolvable(statusListRef: String): IdkError =
        IdkError.fromString(
            code = "STATUSLIST_SIGNING_KEY_UNRESOLVABLE",
            message =
                "Status list '$statusListRef' has no usable signing key binding; refusing to sign it " +
                    "rather than signing under a derived or defaulted key",
            category = ErrorCategory.UNAVAILABLE,
        )

    fun publisherNotImplemented(publisherId: String): IdkError =
        IdkError.fromString(
            code = "STATUSLIST_PUBLISHER_NOT_IMPLEMENTED",
            message = "Status-list publisher '$publisherId' does not support push publishing yet",
            category = ErrorCategory.INTERNAL,
        )

    /** Validate creation args against spec invariants. Returns null when valid. */
    fun validateCreateArgs(args: CreateStatusListArgs): IdkError? {
        if (args.mdocProfile != null) {
            if (args.length <= 0) return invalidMdocProfile("length must be greater than zero")
            if (args.bitsPerStatus !in intArrayOf(1, 2, 4, 8)) {
                return invalidMdocProfile("bitsPerStatus must be 1, 2, 4, or 8")
            }
            if (args.spec != StatusListSpec.TOKEN_STATUS_LIST) {
                return invalidMdocProfile("the mdoc profile requires the Token Status List spec")
            }
            if (args.proofFormat != StatusProofFormat.CWT) {
                return invalidMdocProfile("the mdoc profile requires CWT proof")
            }
            if (args.bitsPerStatus != 1) {
                return invalidMdocProfile("the mdoc profile requires bitsPerStatus=1")
            }
            if (args.validUntil == null) {
                return invalidMdocProfile("mdoc revocation CWT requires validUntil/exp")
            }
            if (args.mdocProfile == MdocStatusListProfile.IDENTIFIER_LIST &&
                args.purposes.any { it != StatusPurpose.REVOCATION }
            ) {
                return invalidMdocProfile("an Identifier List can only represent revocation")
            }
        }
        if (args.spec == StatusListSpec.BITSTRING_STATUS_LIST) {
            val bits = args.length.toLong() * args.bitsPerStatus
            if (bits < MIN_BITSTRING_STATUS_LIST_BITS) return bitstringListTooShort(bits)
        }
        return null
    }
}
