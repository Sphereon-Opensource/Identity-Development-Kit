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

@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.sphereon.oauth2.server.authorization.storage

import com.sphereon.core.api.IdkResult
import kotlin.time.Instant

/**
 * Per-tenant durable record of which Terms-of-Service version each identity has
 * accepted. Consumed by the `MustAcceptTermsEvaluator` (EDK) to decide whether
 * the user must re-accept before the AS issues an authorization code, and by the
 * `AcceptTermsIdvMethodDriver` (EDK) to record the acceptance once the user
 * clicks through.
 *
 * **Versioning model.** A single ToS version per (tenant, identity) — the latest
 * accepted version overwrites the previous one. The store doesn't keep an
 * acceptance history; deployments that need an audit trail of every prior
 * acceptance write to the existing append-only `audit_event` chain instead.
 *
 * **Tenancy.** Every method takes a `tenantId` so a multi-tenant deployment
 * keeps acceptance state partitioned per realm — a user accepting tenant A's
 * ToS does NOT count as having accepted tenant B's, even if they share the same
 * underlying identity id.
 *
 * **Implementations.** IDK ships [InMemoryTermsAcceptanceStore]
 * (`oauth2-server-authorization-impl`) for tests + pure-IDK deployments. A
 * Postgres-backed impl ships in EDK `lib/oauth2/server/authorization/store-postgres/`
 * once a deployment requires acceptance state to survive restart.
 *
 * Stateless / AppScope. The evaluator and the IDV driver (both SessionScope) call
 * into this store and rely on it being thread-safe.
 */
interface TermsAcceptanceStore {
    /**
     * Returns the [TermsAcceptanceRecord] (version + acceptance timestamp) the
     * [identityId] under [tenantId] has accepted, or `null` when no acceptance has been
     * recorded. Callers compare against the tenant's current version to decide whether
     * to surface the `accept-terms` required-action; deployments that enforce a
     * sliding revalidation window also compare [TermsAcceptanceRecord.acceptedAt]
     * against the configured TTL.
     */
    suspend fun findAcceptance(
        tenantId: String,
        identityId: String,
    ): IdkResult<TermsAcceptanceRecord?, TermsAcceptanceStoreError>

    /**
     * Convenience accessor returning just the version string for callers that don't
     * need the acceptance timestamp. Default delegates to [findAcceptance] so
     * implementations only have to provide the canonical method.
     */
    suspend fun findAcceptedVersion(
        tenantId: String,
        identityId: String,
    ): IdkResult<String?, TermsAcceptanceStoreError> {
        val res = findAcceptance(tenantId, identityId)
        return if (res.isOk) {
            com.sphereon.core.api
                .Ok(res.value?.version)
        } else {
            res as IdkResult<String?, TermsAcceptanceStoreError>
        }
    }

    /**
     * Persist (or overwrite) the accepted version for [identityId] under [tenantId].
     * Idempotent on `(tenantId, identityId, version)` — re-recording the same version
     * is a no-op-equivalent overwrite that updates the timestamp; a different version
     * supersedes the previous record.
     */
    suspend fun recordAcceptance(
        tenantId: String,
        identityId: String,
        version: String,
        now: Instant,
    ): IdkResult<Unit, TermsAcceptanceStoreError>
}

/**
 * Persisted acceptance for a single (tenant, identity). The store keeps only the most
 * recent record; an audit history of every prior acceptance is the responsibility of
 * the append-only `audit_event` chain.
 */
data class TermsAcceptanceRecord(
    /** The version string the user clicked through on. Compared verbatim against the configured current version. */
    val version: String,
    /**
     * When the acceptance was recorded. Used by deployments enforcing a sliding
     * revalidation window: even if [version] matches the current version, the
     * evaluator can re-prompt once `now - acceptedAt > revalidation_period_sec`.
     */
    val acceptedAt: Instant,
)

/**
 * Typed error envelope. Operational failures (DB outage, in-memory map exception)
 * surface here wrapped in [com.sphereon.core.api.IdkResult.Err]; callers translate
 * them into the appropriate wire response (typically `server_error` since an
 * acceptance-store outage is an AS infrastructure problem, not a client issue).
 */
sealed class TermsAcceptanceStoreError {
    /**
     * Backend (DB, in-memory map) failed. [operation] names the SPI method that
     * triggered it; [details] is a free-text diagnostic that MUST NOT be surfaced
     * to RPs.
     */
    data class StorageFailure(
        val operation: String,
        val details: String,
    ) : TermsAcceptanceStoreError()
}
