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

package com.sphereon.openid.oid4vp.verifier

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Credential-status ground on which a submitted credential was discarded.
 *
 * Status grounds only. Credential expiry is not reported here: it is a temporal fact of a credential
 * that did verify, and reaches the relying party as the temporal facts on
 * [VerifiedCredentialEvidence] instead. Deriving a business verdict from a presentation that did not
 * cryptographically verify is never permitted, so every rejection below is recorded only after
 * issuer signature, holder binding and disclosure integrity have already succeeded.
 */
@Serializable
@JsExportCompat
enum class CredentialValidationRejectionReason {
    /**
     * The status list asserted revocation: a `revocation` status purpose, or the canonical Token
     * Status List value for an invalid credential. Distinct from [STATUS_NOT_ACCEPTED], which carries
     * no revocation semantics of its own.
     */
    @SerialName("revoked")
    REVOKED,

    /** The status list asserted suspension: a `suspension` status purpose, or the canonical suspended value. */
    @SerialName("suspended")
    SUSPENDED,

    /**
     * The resolved status value is simply not in the policy's accepted set, and asserts neither
     * revocation nor suspension - an issuer-defined multi-bit state such as "under review". The raw
     * value is in [CredentialValidationRejection.statusValue]; nothing further may be inferred from
     * it, and in particular it must never be reported as a revocation.
     */
    @SerialName("status_not_accepted")
    STATUS_NOT_ACCEPTED,

    /** A status reference is present but could not be resolved, and the policy fails closed. */
    @SerialName("status_unresolvable")
    STATUS_UNRESOLVABLE,

    /** The policy requires a status reference and the credential carries none any verifier recognizes. */
    @SerialName("status_required_but_absent")
    STATUS_REQUIRED_BUT_ABSENT,
}

/**
 * A credential the verifier discarded on credential-status grounds, reported beside - never instead
 * of - the short prose error on [ValidationResult].
 *
 * Deliberately omits the status list URI, the resolved index and every technical diagnostic: those
 * are operator detail, emitted as an event, and must not reach a relying party through the
 * validation result. [statusValue] is the raw multi-bit status value, not a locator; it carries no
 * addressing information and is null when nothing resolved.
 *
 * [credentialQueryId] is the DCQL credential query the discarded credential was submitted for. A
 * credential nested inside a submitted VCDM presentation is reported under that same parent query
 * identity - the nested child has no query id of its own - so a rejection list may hold more than
 * one entry per query.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("CredentialValidationRejection", exact = true)
@Serializable
@JsExportCompat
data class CredentialValidationRejection(
    @SerialName("credential_query_id")
    val credentialQueryId: String,
    val reason: CredentialValidationRejectionReason,
    @SerialName("checked_at_epoch_millis")
    val checkedAtEpochMillis: Long,
    @SerialName("status_value")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val statusValue: Int? = null,
)
