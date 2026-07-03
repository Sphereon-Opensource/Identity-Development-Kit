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

package com.sphereon.statuslist.impl.driver

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.pagination.Page
import com.sphereon.di.session.SessionScope
import com.sphereon.statuslist.AllocateEntryArgs
import com.sphereon.statuslist.CreateStatusListArgs
import com.sphereon.statuslist.EntryRef
import com.sphereon.statuslist.ListStatusListsArgs
import com.sphereon.statuslist.StatusListEntry
import com.sphereon.statuslist.StatusListErrors
import com.sphereon.statuslist.StatusListRef
import com.sphereon.statuslist.StatusListResult
import com.sphereon.statuslist.StatusListSortField
import com.sphereon.statuslist.StatusListSummary
import com.sphereon.statuslist.StatusListToken
import com.sphereon.statuslist.StatusPurpose
import com.sphereon.statuslist.UpdateEntryStatusArgs
import com.sphereon.statuslist.impl.codec.StatusBitset
import com.sphereon.statuslist.impl.codec.StatusListCodec
import com.sphereon.statuslist.spi.SignStatusListTokenArgs
import com.sphereon.statuslist.spi.StatusListDriver
import com.sphereon.statuslist.spi.StatusListSigner
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.random.Random
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Live state of one in-memory status list. */
internal class ListState(
    val id: String,
    val tenantId: String,
    val args: CreateStatusListArgs,
    val bitset: StatusBitset,
    val createdAt: Instant,
    var updatedAt: Instant,
) {
    val entriesByIndex = mutableMapOf<Int, StatusListEntry>()
    val indexByEntryCorrelationId = mutableMapOf<String, Int>()
    val indexByCredentialId = mutableMapOf<String, Int>()
    var signedToken: StatusListToken? = null
}

/** Per-tenant in-memory state: lists keyed by id, with a correlation-id index. */
internal class TenantState {
    val lists = mutableMapOf<String, ListState>()
    val correlationToId = mutableMapOf<String, String>()
}

/**
 * In-memory state store for status lists, shared across sessions ([AppScope]). Holds list metadata,
 * the live bit array, and entry rows; performs storage + index allocation but does **not** sign —
 * signing is session-scoped and lives in [InMemoryStatusListDriver]. EDK replaces the whole driver
 * (and thus this store) with a durable Postgres/MySQL implementation.
 *
 * Management state is partitioned per tenant. Status lists are globally unique by their hosting URL,
 * so a tenant-agnostic [uriToId] index allows the public hosting endpoint to resolve a list across
 * tenants by its full URL.
 */
@OptIn(ExperimentalUuidApi::class)
@Inject
@SingleIn(AppScope::class)
class InMemoryStatusListStore {
    private val mutex = Mutex()
    private val tenants = mutableMapOf<String, TenantState>()

    // Global, tenant-agnostic index from full hosting URL to (tenantId, listId).
    private val uriToId = mutableMapOf<String, Pair<String, String>>()

    private fun tenantState(tenantId: String): TenantState = tenants.getOrPut(tenantId) { TenantState() }

    internal suspend fun createStatusList(
        tenantId: String,
        args: CreateStatusListArgs
    ): IdkResult<ListState, IdkError> =
        mutex.withLock {
            val tenant = tenantState(tenantId)
            if (tenant.correlationToId.containsKey(args.correlationId)) {
                return@withLock Err(StatusListErrors.duplicateCorrelationId(args.correlationId))
            }
            if (args.bitsPerStatus !in intArrayOf(1, 2, 4, 8)) {
                return@withLock Err(StatusListErrors.invalidStatusValue(args.bitsPerStatus, args.bitsPerStatus))
            }
            val now = Clock.System.now()
            val state =
                ListState(
                    id = Uuid.random().toString(),
                    tenantId = tenantId,
                    args = args,
                    bitset = StatusBitset.create(args.length, args.bitsPerStatus, StatusListCodec.bitOrderFor(args.spec)),
                    createdAt = now,
                    updatedAt = now,
                )
            tenant.lists[state.id] = state
            tenant.correlationToId[args.correlationId] = state.id
            uriToId[args.statusListUri] = tenantId to state.id
            Ok(state)
        }

    internal suspend fun getState(
        tenantId: String,
        ref: StatusListRef
    ): ListState? = mutex.withLock { resolveList(tenantId, ref) }

    internal suspend fun getToken(
        tenantId: String,
        ref: StatusListRef
    ): StatusListToken? = mutex.withLock { resolveList(tenantId, ref)?.signedToken }

    internal suspend fun updateToken(
        tenantId: String,
        ref: StatusListRef,
        token: StatusListToken,
    ): IdkResult<Unit, IdkError> =
        mutex.withLock {
            val state = resolveList(tenantId, ref) ?: return@withLock Err(listNotFound(ref))
            state.signedToken = token
            Ok(Unit)
        }

    suspend fun listSummaries(
        tenantId: String,
        args: ListStatusListsArgs
    ): Page<StatusListSummary> =
        mutex.withLock {
            val filtered =
                tenantState(tenantId).lists.values.filter { s ->
                    (args.filter.spec == null || s.args.spec == args.filter.spec) &&
                        (args.filter.purpose == null || args.filter.purpose in s.args.purposes) &&
                        (args.filter.correlationId == null || s.args.correlationId == args.filter.correlationId)
                }
            val comparator =
                when (args.sortField) {
                    StatusListSortField.CREATED_AT -> compareBy<ListState> { it.createdAt }
                    StatusListSortField.UPDATED_AT -> compareBy<ListState> { it.updatedAt }
                    StatusListSortField.CORRELATION_ID -> compareBy<ListState> { it.args.correlationId }
                }
            val sorted = filtered.sortedWith(if (args.descending) comparator.reversed() else comparator)
            val window = sorted.drop(args.offset).take(args.limit).map { it.toSummary() }
            Page(items = window, totalCount = filtered.size.toLong(), limit = args.limit, offset = args.offset)
        }

    suspend fun deleteStatusList(
        tenantId: String,
        ref: StatusListRef
    ): Boolean =
        mutex.withLock {
            val state = resolveList(tenantId, ref) ?: return@withLock false
            val tenant = tenantState(state.tenantId)
            tenant.lists.remove(state.id)
            tenant.correlationToId.remove(state.args.correlationId)
            uriToId.remove(state.args.statusListUri)
            true
        }

    suspend fun allocateEntry(
        tenantId: String,
        args: AllocateEntryArgs
    ): IdkResult<StatusListEntry, IdkError> =
        mutex.withLock {
            val state = resolveList(tenantId, args.statusList) ?: return@withLock Err(listNotFound(args.statusList))
            val index = pickIndex(state, args.explicitIndex).getOrElse { return@withLock Err(it) }
            if (args.initialValue !in 0 until (1 shl state.args.bitsPerStatus)) {
                return@withLock Err(StatusListErrors.invalidStatusValue(args.initialValue, state.args.bitsPerStatus))
            }
            state.bitset.set(index, args.initialValue)
            val entry =
                StatusListEntry(
                    statusListId = state.id,
                    statusListIndex = index,
                    entryCorrelationId = args.entryCorrelationId,
                    credentialId = args.credentialId,
                    credentialHash = args.credentialHash,
                    value = args.initialValue,
                    purpose = args.purpose,
                )
            indexEntry(state, entry)
            state.updatedAt = Clock.System.now()
            Ok(entry)
        }

    suspend fun updateEntryStatus(
        tenantId: String,
        args: UpdateEntryStatusArgs
    ): IdkResult<StatusListEntry, IdkError> =
        mutex.withLock {
            val ref = args.entry
            val state = resolveEntryListState(tenantId, ref) ?: return@withLock Err(entryNotFound(ref))
            if (args.value !in 0 until (1 shl state.args.bitsPerStatus)) {
                return@withLock Err(StatusListErrors.invalidStatusValue(args.value, state.args.bitsPerStatus))
            }
            val existing = findEntryIn(state, ref)
            // A status update by index must work on ANY in-range index, allocated
            // or not: rejecting unallocated indexes would reveal which indexes
            // carry issued credentials. Lookups by entry/credential id still
            // require an allocated entry (they reference allocation-time data).
            val index =
                existing?.statusListIndex ?: ref.statusListIndex
                    ?: return@withLock Err(entryNotFound(ref))
            if (index !in 0 until state.args.length) {
                return@withLock Err(StatusListErrors.indexOutOfRange(index, state.args.length))
            }
            state.bitset.set(index, args.value)
            val updated =
                existing?.copy(value = args.value)
                    ?: StatusListEntry(
                        statusListId = state.id,
                        statusListIndex = index,
                        entryCorrelationId = null,
                        credentialId = null,
                        credentialHash = null,
                        value = args.value,
                        purpose = state.args.purposes.firstOrNull() ?: StatusPurpose.REVOCATION,
                    )
            indexEntry(state, updated)
            state.updatedAt = Clock.System.now()
            Ok(updated)
        }

    suspend fun bindCredential(
        tenantId: String,
        entry: EntryRef,
        credentialId: String?,
        credentialHash: String?,
    ): IdkResult<StatusListEntry, IdkError> =
        mutex.withLock {
            val (state, existing) = resolveEntry(tenantId, entry) ?: return@withLock Err(entryNotFound(entry))
            val updated =
                existing.copy(
                    credentialId = credentialId ?: existing.credentialId,
                    credentialHash = credentialHash ?: existing.credentialHash,
                )
            indexEntry(state, updated)
            state.updatedAt = Clock.System.now()
            Ok(updated)
        }

    suspend fun getEntry(
        tenantId: String,
        ref: EntryRef
    ): StatusListEntry? = mutex.withLock { resolveEntry(tenantId, ref)?.second }

    // region helpers

    private fun pickIndex(
        state: ListState,
        explicitIndex: Int?,
    ): IdkResult<Int, IdkError> {
        val length = state.args.length
        if (explicitIndex != null) {
            if (explicitIndex !in 0 until length) return Err(StatusListErrors.indexOutOfRange(explicitIndex, length))
            if (state.entriesByIndex.containsKey(explicitIndex)) return Err(StatusListErrors.indexInUse(explicitIndex))
            return Ok(explicitIndex)
        }
        if (state.entriesByIndex.size >= length) return Err(StatusListErrors.listExhausted(state.args.correlationId))
        // Random-unused: try random candidates, then fall back to enumerating the remaining free slots.
        repeat(RANDOM_ALLOCATION_TRIES) {
            val candidate = Random.nextInt(length)
            if (!state.entriesByIndex.containsKey(candidate)) return Ok(candidate)
        }
        for (i in 0 until length) {
            if (!state.entriesByIndex.containsKey(i)) return Ok(i)
        }
        return Err(StatusListErrors.listExhausted(state.args.correlationId))
    }

    private fun indexEntry(
        state: ListState,
        entry: StatusListEntry,
    ) {
        state.entriesByIndex[entry.statusListIndex] = entry
        entry.entryCorrelationId?.let { state.indexByEntryCorrelationId[it] = entry.statusListIndex }
        entry.credentialId?.let { state.indexByCredentialId[it] = entry.statusListIndex }
    }

    private fun resolveList(
        tenantId: String,
        ref: StatusListRef
    ): ListState? {
        // Status lists are globally unique by hosting URL: resolve URI tenant-agnostic first.
        ref.statusListUri?.let { uri ->
            uriToId[uri]?.let { (ownerTenant, listId) ->
                tenants[ownerTenant]?.lists?.get(listId)?.let { return it }
            }
        }
        val tenant = tenantState(tenantId)
        ref.id?.let { return tenant.lists[it] }
        ref.correlationId?.let { cid -> return tenant.correlationToId[cid]?.let { tenant.lists[it] } }
        return null
    }

    private fun resolveEntry(
        tenantId: String,
        ref: EntryRef
    ): Pair<ListState, StatusListEntry>? {
        val state = resolveEntryListState(tenantId, ref) ?: return null
        val entry = findEntryIn(state, ref) ?: return null
        return state to entry
    }

    private fun resolveEntryListState(
        tenantId: String,
        ref: EntryRef
    ): ListState? {
        val tenant = tenantState(tenantId)
        return when {
            ref.statusListId != null -> tenants.values.firstNotNullOfOrNull { it.lists[ref.statusListId] }
            ref.correlationId != null -> tenant.correlationToId[ref.correlationId]?.let { tenant.lists[it] }
            else -> null
        }
    }

    private fun findEntryIn(
        state: ListState,
        ref: EntryRef
    ): StatusListEntry? {
        val index =
            when {
                ref.statusListIndex != null -> ref.statusListIndex
                ref.entryCorrelationId != null -> state.indexByEntryCorrelationId[ref.entryCorrelationId]
                ref.credentialId != null -> state.indexByCredentialId[ref.credentialId]
                else -> null
            } ?: return null
        return state.entriesByIndex[index]
    }

    private fun ListState.toSummary(): StatusListSummary =
        StatusListSummary(
            id = id,
            correlationId = args.correlationId,
            spec = args.spec,
            purposes = args.purposes,
            proofFormat = args.proofFormat,
            hostingMode = args.hostingMode,
            bitsPerStatus = args.bitsPerStatus,
            length = args.length,
            issuedCount = entriesByIndex.size,
            remainingCapacity = args.length - entriesByIndex.size,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    private fun listNotFound(ref: StatusListRef): IdkError = StatusListErrors.listNotFound(ref.id ?: ref.correlationId ?: "<none>")

    private fun entryNotFound(ref: EntryRef): IdkError =
        StatusListErrors.entryNotFound(
            ref.entryCorrelationId ?: ref.credentialId ?: ref.statusListIndex?.toString() ?: "<none>",
        )

    // endregion

    companion object {
        private const val RANDOM_ALLOCATION_TRIES = 64
    }
}

/**
 * Reference in-memory [StatusListDriver]. Session-scoped because token production uses the
 * session-scoped signer; persistent state lives in the shared [InMemoryStatusListStore]. Ships as
 * the default binding; EDK's Postgres/MySQL drivers replace it for durable deployments. Index
 * allocation defaults to **random unused** (sequential allocation is deliberately not offered).
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<StatusListDriver>())
class InMemoryStatusListDriver(
    private val store: InMemoryStatusListStore,
    private val signer: StatusListSigner,
    private val execution: SessionExecution,
) : StatusListDriver {
    private fun tenantId(): String = execution.tenantId.takeIf { it.isNotBlank() } ?: DEFAULT_TENANT

    override suspend fun createStatusList(args: CreateStatusListArgs): IdkResult<StatusListResult, IdkError> {
        val state = store.createStatusList(tenantId(), args).getOrElse { return Err(it) }
        val token = signToken(state).getOrElse { return Err(it) }
        store.updateToken(tenantId(), StatusListRef(id = state.id), token).getOrElse { return Err(it) }
        return buildResult(state)
    }

    override suspend fun getStatusList(ref: StatusListRef): IdkResult<StatusListResult?, IdkError> {
        val state = store.getState(tenantId(), ref) ?: return Ok(null)
        return buildResult(state)
    }

    override suspend fun listStatusLists(args: ListStatusListsArgs): IdkResult<Page<StatusListSummary>, IdkError> = Ok(store.listSummaries(tenantId(), args))

    override suspend fun deleteStatusList(ref: StatusListRef): IdkResult<Boolean, IdkError> = Ok(store.deleteStatusList(tenantId(), ref))

    override suspend fun allocateEntry(args: AllocateEntryArgs): IdkResult<StatusListEntry, IdkError> {
        val entry = store.allocateEntry(tenantId(), args).getOrElse { return Err(it) }
        refreshToken(StatusListRef(id = entry.statusListId)).getOrElse { return Err(it) }
        return Ok(entry)
    }

    override suspend fun updateEntryStatus(args: UpdateEntryStatusArgs): IdkResult<StatusListEntry, IdkError> {
        val entry = store.updateEntryStatus(tenantId(), args).getOrElse { return Err(it) }
        refreshToken(StatusListRef(id = entry.statusListId)).getOrElse { return Err(it) }
        return Ok(entry)
    }

    override suspend fun bindCredential(
        entry: EntryRef,
        credentialId: String?,
        credentialHash: String?,
    ): IdkResult<StatusListEntry, IdkError> = store.bindCredential(tenantId(), entry, credentialId, credentialHash)

    override suspend fun getEntry(ref: EntryRef): IdkResult<StatusListEntry?, IdkError> = Ok(store.getEntry(tenantId(), ref))

    override suspend fun getStatusListToken(ref: StatusListRef): IdkResult<StatusListToken?, IdkError> = Ok(store.getToken(tenantId(), ref))

    private suspend fun refreshToken(ref: StatusListRef): IdkResult<Unit, IdkError> {
        val state = store.getState(tenantId(), ref) ?: return Err(listNotFound(ref))
        val token = signToken(state).getOrElse { return Err(it) }
        return store.updateToken(tenantId(), ref, token)
    }

    private suspend fun signToken(state: ListState): IdkResult<StatusListToken, IdkError> {
        val encoded = StatusListCodec.encode(state.bitset, state.args.spec)
        return signer.signStatusListToken(
            SignStatusListTokenArgs(
                spec = state.args.spec,
                proofFormat = state.args.proofFormat,
                issuer = state.args.issuer,
                statusListUri = state.args.statusListUri,
                signingKeyAlias = state.args.signingKeyAlias ?: state.args.correlationId,
                signingKeyMode = state.args.signingKeyMode,
                signingVerificationMethodId = state.args.signingVerificationMethodId,
                signingCertChainPath = state.args.signingCertChainPath,
                bitsPerStatus = state.args.bitsPerStatus,
                length = state.args.length,
                purposes = state.args.purposes,
                encodedList = encoded,
                issuedAtEpochSeconds = Clock.System.now().epochSeconds,
                ttlSeconds = state.args.ttlSeconds,
                expiresAtEpochSeconds = state.args.validUntil?.epochSeconds,
            ),
        )
    }

    private suspend fun buildResult(state: ListState): IdkResult<StatusListResult, IdkError> {
        val token = state.signedToken ?: signToken(state).getOrElse { return Err(it) }
        return Ok(
            StatusListResult(
                id = state.id,
                correlationId = state.args.correlationId,
                spec = state.args.spec,
                purposes = state.args.purposes,
                proofFormat = state.args.proofFormat,
                hostingMode = state.args.hostingMode,
                bitsPerStatus = state.args.bitsPerStatus,
                length = state.args.length,
                issuer = state.args.issuer,
                statusListUri = state.args.statusListUri,
                signedToken = token.token,
                contentType = token.contentType,
            ),
        )
    }

    private companion object {
        // Fallback tenant when the session execution carries no tenant id.
        private const val DEFAULT_TENANT = "default"
    }

    private fun listNotFound(ref: StatusListRef): IdkError = StatusListErrors.listNotFound(ref.statusListUri ?: ref.id ?: ref.correlationId ?: "<none>")
}
