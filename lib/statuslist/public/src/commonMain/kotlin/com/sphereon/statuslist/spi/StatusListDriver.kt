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
import com.sphereon.core.api.pagination.Page
import com.sphereon.statuslist.AllocateEntryArgs
import com.sphereon.statuslist.CreateStatusListArgs
import com.sphereon.statuslist.EntryRef
import com.sphereon.statuslist.ListStatusListsArgs
import com.sphereon.statuslist.StatusListEntry
import com.sphereon.statuslist.StatusListRef
import com.sphereon.statuslist.StatusListResult
import com.sphereon.statuslist.StatusListSummary
import com.sphereon.statuslist.StatusListToken
import com.sphereon.statuslist.UpdateEntryStatusArgs

/**
 * Persistence + management SPI for status lists, mirroring the SSI-SDK TypeScript driver.
 *
 * An in-memory implementation ships in `:lib-statuslist-impl`; durable Postgres/MySQL
 * implementations ship in EDK and replace it via DI. The SPI is spec-agnostic — the bit codec,
 * signing, and per-spec encoding live behind it.
 *
 * **Correlation ids / business keys.** A list carries a required, unique `correlationId`; an
 * entry carries an optional `entryCorrelationId` (plus `credentialId`/`credentialHash`), so an
 * operator can revoke "the credential for order #1234" without knowing its bit index.
 */
interface StatusListDriver {
    suspend fun createStatusList(args: CreateStatusListArgs): IdkResult<StatusListResult, IdkError>

    /**
     * Reconcile the mutable definition of an existing list and re-sign its current bitset.
     *
     * Implementations must preserve the list id, all allocated entries, and every encoded status
     * bit. Structural fields (correlation id, spec, proof format, purposes, bit width, length, and
     * public URI) are immutable once credentials can reference the list. The default keeps custom
     * drivers source-compatible; durable/reference drivers override it to apply mutable changes.
     */
    suspend fun refreshStatusListDefinition(args: CreateStatusListArgs): IdkResult<StatusListResult, IdkError> {
        val existing = getStatusList(StatusListRef(correlationId = args.correlationId))
        return existing.getOrElse { return com.sphereon.core.api.Err(it) }
            ?.let { com.sphereon.core.api.Ok(it) }
            ?: createStatusList(args)
    }

    /** Returns null when no list matches [ref]. */
    suspend fun getStatusList(ref: StatusListRef): IdkResult<StatusListResult?, IdkError>

    suspend fun listStatusLists(args: ListStatusListsArgs): IdkResult<Page<StatusListSummary>, IdkError>

    suspend fun deleteStatusList(ref: StatusListRef): IdkResult<Boolean, IdkError>

    /**
     * Reserve and persist an entry. Defaults to a **random unused index** (see [AllocateEntryArgs]);
     * fails with [com.sphereon.statuslist.StatusListErrors.listExhausted] when no free index remains.
     */
    suspend fun allocateEntry(args: AllocateEntryArgs): IdkResult<StatusListEntry, IdkError>

    /** Set the status value of an entry (revoke / suspend / reactivate) and re-sign the list. */
    suspend fun updateEntryStatus(args: UpdateEntryStatusArgs): IdkResult<StatusListEntry, IdkError>

    /**
     * Bind an allocated entry to an issued credential's id and/or hash (the post-sign half of the
     * reserve/bind issuance flow — the credential hash is only known after signing). Enables later
     * revocation by credentialId.
     */
    suspend fun bindCredential(
        entry: EntryRef,
        credentialId: String?,
        credentialHash: String?,
    ): IdkResult<StatusListEntry, IdkError>

    /** Returns null when no entry matches [ref]. */
    suspend fun getEntry(ref: EntryRef): IdkResult<StatusListEntry?, IdkError>

    /**
     * The stored, signed, hostable token projection reflecting the last persisted bit state; null
     * when [ref] is unknown. Implementations refresh this projection during create/status mutation
     * paths and must not re-sign on read, because public hosting reads can be anonymous.
     */
    suspend fun getStatusListToken(ref: StatusListRef): IdkResult<StatusListToken?, IdkError>
}
