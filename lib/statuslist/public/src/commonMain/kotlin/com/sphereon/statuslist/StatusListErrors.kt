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
    ): IdkError =
        IdkError.fromString(
            code = "STATUSLIST_VERIFICATION_FAILED",
            message = "Status list at $uri failed verification: $reason",
            category = ErrorCategory.VALIDATION,
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

    fun publisherNotImplemented(publisherId: String): IdkError =
        IdkError.fromString(
            code = "STATUSLIST_PUBLISHER_NOT_IMPLEMENTED",
            message = "Status-list publisher '$publisherId' does not support push publishing yet",
            category = ErrorCategory.INTERNAL,
        )

    /** Validate creation args against spec invariants. Returns null when valid. */
    fun validateCreateArgs(args: CreateStatusListArgs): IdkError? {
        if (args.spec == StatusListSpec.BITSTRING_STATUS_LIST) {
            val bits = args.length.toLong() * args.bitsPerStatus
            if (bits < MIN_BITSTRING_STATUS_LIST_BITS) return bitstringListTooShort(bits)
        }
        return null
    }
}
