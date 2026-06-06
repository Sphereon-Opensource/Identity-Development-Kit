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

package com.sphereon.openid.oid4vp.dcql.store

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.openid.oid4vp.dcql.DcqlQuery

/**
 * Resolves a stored DCQL query into the actual [DcqlQuery] used to build an authorization
 * request, and lets an authorization session pin the exact version it was created against.
 *
 * This is a replaceable SPI so that layers above can change *how* a `query_id` maps to a
 * [DcqlQuery]:
 *
 * - The default resolves the current configuration by `query_id` and reports no version.
 * - A version-aware implementation resolves through its version history and reports the
 *   active version, so the session can snapshot `(queryId, version)`.
 * - A verifier-aware implementation resolves through a verifier→binding→pinned-version chain.
 *
 * `CreateAuthRequestServiceCommand` injects this resolver instead of the store directly.
 */
interface DcqlQueryResolver {
    /**
     * Resolve a `query_id` at session-creation time. Returns the [DcqlQuery] plus the
     * `(queryId, version)` snapshot to persist on the authorization session.
     *
     * [verifierId] is an opaque business key naming the verifier the request is for. The
     * default and version-aware implementations ignore it and resolve by `query_id` alone; a
     * verifier-aware implementation resolves through a verifier→binding→pinned-version chain
     * when it is set.
     *
     * Fails with `NOT_FOUND` when the query does not exist for the tenant, and with
     * `ILLEGAL_ARGUMENT` when it exists but is disabled.
     */
    suspend fun resolveForCreate(
        queryId: String,
        verifierId: String? = null
    ): IdkResult<ResolvedDcqlQuery, IdkError>

    /**
     * Re-resolve a query for an existing session from its persisted `(queryId, version)`
     * snapshot. Implementations without version history ignore [version] and resolve the
     * current configuration; the EDK versioned store resolves the exact pinned version.
     */
    suspend fun resolvePinned(
        queryId: String,
        version: Int?
    ): IdkResult<DcqlQuery, IdkError>
}

/**
 * A resolved DCQL query plus the version snapshot to record on the authorization session.
 * [version] is null for stores without version history.
 */
data class ResolvedDcqlQuery(
    val dcqlQuery: DcqlQuery,
    val dcqlQueryId: String,
    val version: Int? = null,
)
