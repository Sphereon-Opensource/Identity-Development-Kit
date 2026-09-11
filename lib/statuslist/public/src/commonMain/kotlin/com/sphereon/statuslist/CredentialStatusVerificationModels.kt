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

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.statuslist.spi.CredentialStatusVerifier
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** A single status reference extracted from a credential, ready to resolve. */
@Serializable
@JsExportCompat
data class CredentialStatusReference(
    val mechanism: String,
    val uri: String,
    val index: Int,
    val purpose: StatusPurpose? = null,
    /** Binary identifier used by the ISO/IEC 18013 Identifier List profile. */
    val identifier: ByteArray? = null,
    /** Optional MSO-supplied certificate that pins the revocation CWT chain; it is not a trust root. */
    val certificate: ByteArray? = null,
)

/**
 * Per-(DCQL-query) verifier policy for how to treat a credential's resolved status.
 *
 * The default rejects every non-active status (revoked and suspended) and fails closed when a status
 * cannot be resolved, but does not require a status to be present. Set [requireStatus] to reject a
 * credential that carries no recognized status reference at all.
 */
@Serializable
@JsExportCompat
data class CredentialStatusPolicy(
    /** Reject a credential that carries no status reference any configured verifier recognizes. */
    val requireStatus: Boolean = false,
    /** Accept a credential whose status resolves to revoked ([StatusValues.INVALID]). */
    val acceptRevoked: Boolean = false,
    /** Accept a credential whose status resolves to suspended ([StatusValues.SUSPENDED]). */
    val acceptSuspended: Boolean = false,
    /** Additional raw status values accepted (for application-specific multi-bit lists). */
    val acceptStatusValues: Set<Int> = emptySet(),
    /** Reject (fail closed) when a present status reference cannot be resolved. */
    val rejectOnUnresolvable: Boolean = true,
) {
    /** True when [value] is acceptable under this policy ([StatusValues.VALID] is always accepted). */
    fun accepts(value: Int): Boolean =
        when (value) {
            StatusValues.VALID -> true
            StatusValues.INVALID -> acceptRevoked
            StatusValues.SUSPENDED -> acceptSuspended
            else -> value in acceptStatusValues
        }
}

/**
 * Human-readable status name for a resolved status. Prefers the W3C status purpose when present
 * (a Bitstring list states its own meaning), else maps the canonical Token Status List values
 * (`0` valid, `1` revoked, `2` suspended); anything else is reported as `status <n>`.
 */
fun describeStatus(status: ResolvedStatus): String =
    when {
        status.value == StatusValues.VALID -> "valid"
        status.purpose == StatusPurpose.SUSPENSION -> "suspended"
        status.purpose == StatusPurpose.REVOCATION -> "revoked"
        status.value == StatusValues.INVALID -> "revoked"
        status.value == StatusValues.SUSPENDED -> "suspended"
        else -> "status ${status.value}"
    }

@JsExportCompat
enum class CredentialStatusDecision {
    /** The credential's status is acceptable under the policy (or no status was required/present). */
    ACCEPT,

    /** The credential must be discarded: a status is not accepted, unresolvable, or required-but-absent. */
    REJECT,

    /** No verifiers configured; status checking did not run. */
    SKIPPED,
}

/**
 * Outcome of [evaluateCredentialStatus].
 *
 * [reason] is a TECHNICAL diagnostic (includes the status list URI / index) — for logs and events,
 * not for end users. [rejectedStatus] is the resolved status that caused a REJECT on a non-accepted
 * value (null for require-but-absent / unresolvable rejections), so a caller can render a short
 * user-facing word via [describeStatus].
 */
@JsExportCompat
data class CredentialStatusEvaluation(
    val decision: CredentialStatusDecision,
    val resolved: List<ResolvedStatus> = emptyList(),
    val reason: String? = null,
    val rejectedStatus: ResolvedStatus? = null,
)

/**
 * Evaluate a credential's status against [policy] using the configured [verifiers].
 *
 * - Empty [verifiers] → [CredentialStatusDecision.SKIPPED] (nothing wired to check with).
 * - No reference any verifier recognizes → REJECT iff [CredentialStatusPolicy.requireStatus], else ACCEPT.
 * - Otherwise resolve every recognized reference; REJECT on the first non-accepted value, or on an
 *   unresolvable reference when [CredentialStatusPolicy.rejectOnUnresolvable]; else ACCEPT.
 */
suspend fun evaluateCredentialStatus(
    verifiers: Set<CredentialStatusVerifier>,
    claims: JsonObject,
    policy: CredentialStatusPolicy,
): CredentialStatusEvaluation =
    evaluateCredentialStatus(verifiers, CredentialStatusInput(claims = claims), policy)

/** Evaluate status using both ordinary claims and authenticated format-specific metadata. */
suspend fun evaluateCredentialStatus(
    verifiers: Set<CredentialStatusVerifier>,
    input: CredentialStatusInput,
    policy: CredentialStatusPolicy,
): CredentialStatusEvaluation {
    if (verifiers.isEmpty()) {
        return CredentialStatusEvaluation(CredentialStatusDecision.SKIPPED, reason = "no credential status verifiers configured")
    }
    val references = verifiers.flatMap { verifier -> verifier.references(input).map { verifier to it } }
    if (references.isEmpty()) {
        return if (policy.requireStatus) {
            CredentialStatusEvaluation(
                CredentialStatusDecision.REJECT,
                reason = "credential carries no status list reference but the policy requires one",
            )
        } else {
            CredentialStatusEvaluation(CredentialStatusDecision.ACCEPT, reason = "no status reference present")
        }
    }
    val resolved = mutableListOf<ResolvedStatus>()
    for ((verifier, reference) in references) {
        val status =
            verifier.resolve(reference).getOrElse { error ->
                if (policy.rejectOnUnresolvable) {
                    return CredentialStatusEvaluation(
                        CredentialStatusDecision.REJECT,
                        resolved = resolved,
                        reason = "status could not be resolved for ${reference.uri}#${reference.index}: ${error.message.defaultMessage}",
                    )
                }
                continue
            }
        resolved += status
        if (!policy.accepts(status.value)) {
            return CredentialStatusEvaluation(
                CredentialStatusDecision.REJECT,
                resolved = resolved,
                reason = "credential is ${describeStatus(status)} (status list ${reference.uri}, index ${reference.index})",
                rejectedStatus = status,
            )
        }
    }
    return CredentialStatusEvaluation(CredentialStatusDecision.ACCEPT, resolved = resolved)
}
