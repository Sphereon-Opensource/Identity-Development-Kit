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

package com.sphereon.statuslist.spi

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.statuslist.StatusListSpec
import com.sphereon.statuslist.StatusPurpose
import com.sphereon.statuslist.MdocStatusListProfile
import com.sphereon.statuslist.StatusProofFormat
import kotlinx.serialization.json.JsonObject

/**
 * Issuance-side SPI used by the OID4VCI format handlers to attach a status entry to a credential.
 *
 * The status reference MUST be present **before** the credential is signed, so [reserve] runs
 * pre-sign (allocate an index, return the format-correct claim fragment to merge into the unsigned
 * payload) and [bind] runs post-sign (associate the reserved entry with the issued credential's id
 * and hash for later lookup/revocation). An issuer with no status list configured simply has no
 * enricher bound, leaving issuance unchanged.
 */
interface CredentialStatusEnricher {
    /** PRE-SIGN: reserve an entry and return the claim fragment + where to merge it. */
    suspend fun reserve(context: StatusEnrichmentContext): IdkResult<ReservedStatus, IdkError>

    /** POST-SIGN: bind the reserved entry to the freshly issued credential. */
    suspend fun bind(
        handle: StatusReservationHandle,
        credentialId: String?,
        credentialHash: String?,
    ): IdkResult<Unit, IdkError>

    /**
     * CANCEL: release a reservation that cannot be completed.
     *
     * Implementations must remove the entry and make its index available again. The operation is
     * idempotent so an issuer can safely retry cleanup after a transient persistence/signing error.
     */
    suspend fun cancel(handle: StatusReservationHandle): IdkResult<Unit, IdkError>
}

/** Inputs the enricher needs to choose a list and allocate an entry during issuance. */
data class StatusEnrichmentContext(
    val credentialConfigurationId: String,
    /** OID4VCI credential format value (e.g. `jwt_vc_json`, `dc+sd-jwt`, `mso_mdoc`). */
    val format: String,
    val spec: StatusListSpec,
    val purposes: List<StatusPurpose>,
    /** Business key of the status list to allocate from. */
    val statusListCorrelationId: String,
    val entryCorrelationId: String? = null,
    val credentialId: String? = null,
    /** Optional ISO mdoc aggregation endpoint to carry in StatusListInfo. */
    val aggregationUri: String? = null,
    /** Expected ISO profile, copied from the issuer binding for definition consistency checks. */
    val mdocProfile: MdocStatusListProfile? = null,
    /** Expected proof envelope, copied from the issuer binding for definition consistency checks. */
    val proofFormat: StatusProofFormat? = null,
)

/** Opaque handle to a reserved entry, returned by [CredentialStatusEnricher.reserve]. */
data class StatusReservationHandle(
    val statusListId: String,
    val statusListIndex: Int,
)

/** Where in the unsigned credential the [claim] fragment must be merged. */
enum class StatusClaimMergeTarget {
    /** Token Status List: top-level `status` object (SD-JWT VC). */
    TOP_LEVEL_STATUS,

    /** W3C: `credentialStatus` inside the `vc` payload (JWT-VC / VC-LD). */
    VC_CREDENTIAL_STATUS,

    /** ISO mdoc: status entry inside the MSO. */
    MDOC_STATUS,
}

/** The pre-sign result: a claim fragment plus the handle needed to bind it post-sign. */
data class ReservedStatus(
    val handle: StatusReservationHandle,
    val claim: JsonObject,
    val mergeTarget: StatusClaimMergeTarget,
    /** Binary identifier allocated for an ISO Identifier List, when applicable. */
    val identifier: ByteArray? = null,
)
