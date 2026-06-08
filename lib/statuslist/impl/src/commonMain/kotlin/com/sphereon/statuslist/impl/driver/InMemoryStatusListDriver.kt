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
    val args: CreateStatusListArgs,
    val bitset: StatusBitset,
    val createdAt: Instant,
    var updatedAt: Instant,
) {
    val entriesByIndex = mutableMapOf<Int, StatusListEntry>()
    val indexByEntryCorrelationId = mutableMapOf<String, Int>()
    val indexByCredentialId = mutableMapOf<String, Int>()
}

/**
 * In-memory state store for status lists, shared across sessions ([AppScope]). Holds list metadata,
 * the live bit array, and entry rows; performs storage + index allocation but does **not** sign —
 * signing is session-scoped and lives in [InMemoryStatusListDriver]. EDK replaces the whole driver
 * (and thus this store) with a durable Postgres/MySQL implementation.
 */
@OptIn(ExperimentalUuidApi::class)
@Inject
@SingleIn(AppScope::class)
class InMemoryStatusListStore {
    private val mutex = Mutex()
    private val lists = mutableMapOf<String, ListState>()
    private val correlationToId = mutableMapOf<String, String>()

    internal suspend fun createStatusList(args: CreateStatusListArgs): IdkResult<ListState, IdkError> =
        mutex.withLock {
            if (correlationToId.containsKey(args.correlationId)) {
                return@withLock Err(StatusListErrors.duplicateCorrelationId(args.correlationId))
            }
            if (args.bitsPerStatus !in intArrayOf(1, 2, 4, 8)) {
                return@withLock Err(StatusListErrors.invalidStatusValue(args.bitsPerStatus, args.bitsPerStatus))
            }
            val now = Clock.System.now()
            val state =
                ListState(
                    id = Uuid.random().toString(),
                    args = args,
                    bitset = StatusBitset.create(args.length, args.bitsPerStatus, StatusListCodec.bitOrderFor(args.spec)),
                    createdAt = now,
                    updatedAt = now,
                )
            lists[state.id] = state
            correlationToId[args.correlationId] = state.id
            Ok(state)
        }

    internal suspend fun getState(ref: StatusListRef): ListState? = mutex.withLock { resolveList(ref) }

    suspend fun listSummaries(args: ListStatusListsArgs): Page<StatusListSummary> =
        mutex.withLock {
            val filtered =
                lists.values.filter { s ->
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

    suspend fun deleteStatusList(ref: StatusListRef): Boolean =
        mutex.withLock {
            val state = resolveList(ref) ?: return@withLock false
            lists.remove(state.id)
            correlationToId.remove(state.args.correlationId)
            true
        }

    suspend fun allocateEntry(args: AllocateEntryArgs): IdkResult<StatusListEntry, IdkError> =
        mutex.withLock {
            val state = resolveList(args.statusList) ?: return@withLock Err(listNotFound(args.statusList))
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

    suspend fun updateEntryStatus(args: UpdateEntryStatusArgs): IdkResult<StatusListEntry, IdkError> =
        mutex.withLock {
            val (state, existing) = resolveEntry(args.entry) ?: return@withLock Err(entryNotFound(args.entry))
            if (args.value !in 0 until (1 shl state.args.bitsPerStatus)) {
                return@withLock Err(StatusListErrors.invalidStatusValue(args.value, state.args.bitsPerStatus))
            }
            state.bitset.set(existing.statusListIndex, args.value)
            val updated = existing.copy(value = args.value)
            indexEntry(state, updated)
            state.updatedAt = Clock.System.now()
            Ok(updated)
        }

    suspend fun bindCredential(
        entry: EntryRef,
        credentialId: String?,
        credentialHash: String?,
    ): IdkResult<StatusListEntry, IdkError> =
        mutex.withLock {
            val (state, existing) = resolveEntry(entry) ?: return@withLock Err(entryNotFound(entry))
            val updated =
                existing.copy(
                    credentialId = credentialId ?: existing.credentialId,
                    credentialHash = credentialHash ?: existing.credentialHash,
                )
            indexEntry(state, updated)
            state.updatedAt = Clock.System.now()
            Ok(updated)
        }

    suspend fun getEntry(ref: EntryRef): StatusListEntry? = mutex.withLock { resolveEntry(ref)?.second }

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

    private fun resolveList(ref: StatusListRef): ListState? {
        ref.id?.let { return lists[it] }
        ref.correlationId?.let { cid -> return correlationToId[cid]?.let { lists[it] } }
        return null
    }

    private fun resolveEntry(ref: EntryRef): Pair<ListState, StatusListEntry>? {
        val state =
            when {
                ref.statusListId != null -> lists[ref.statusListId]
                ref.correlationId != null -> correlationToId[ref.correlationId]?.let { lists[it] }
                else -> null
            } ?: return null
        val index =
            when {
                ref.statusListIndex != null -> ref.statusListIndex
                ref.entryCorrelationId != null -> state.indexByEntryCorrelationId[ref.entryCorrelationId]
                ref.credentialId != null -> state.indexByCredentialId[ref.credentialId]
                else -> null
            } ?: return null
        val entry = state.entriesByIndex[index] ?: return null
        return state to entry
    }

    private fun ListState.toSummary(): StatusListSummary =
        StatusListSummary(
            id = id,
            correlationId = args.correlationId,
            spec = args.spec,
            purposes = args.purposes,
            proofFormat = args.proofFormat,
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
) : StatusListDriver {
    override suspend fun createStatusList(args: CreateStatusListArgs): IdkResult<StatusListResult, IdkError> {
        val state = store.createStatusList(args).getOrElse { return Err(it) }
        return buildResult(state)
    }

    override suspend fun getStatusList(ref: StatusListRef): IdkResult<StatusListResult?, IdkError> {
        val state = store.getState(ref) ?: return Ok(null)
        return buildResult(state)
    }

    override suspend fun listStatusLists(args: ListStatusListsArgs): IdkResult<Page<StatusListSummary>, IdkError> = Ok(store.listSummaries(args))

    override suspend fun deleteStatusList(ref: StatusListRef): IdkResult<Boolean, IdkError> = Ok(store.deleteStatusList(ref))

    override suspend fun allocateEntry(args: AllocateEntryArgs): IdkResult<StatusListEntry, IdkError> = store.allocateEntry(args)

    override suspend fun updateEntryStatus(args: UpdateEntryStatusArgs): IdkResult<StatusListEntry, IdkError> = store.updateEntryStatus(args)

    override suspend fun bindCredential(
        entry: EntryRef,
        credentialId: String?,
        credentialHash: String?,
    ): IdkResult<StatusListEntry, IdkError> = store.bindCredential(entry, credentialId, credentialHash)

    override suspend fun getEntry(ref: EntryRef): IdkResult<StatusListEntry?, IdkError> = Ok(store.getEntry(ref))

    override suspend fun getStatusListToken(ref: StatusListRef): IdkResult<StatusListToken?, IdkError> {
        val state = store.getState(ref) ?: return Ok(null)
        return signToken(state)
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
        val token = signToken(state).getOrElse { return Err(it) }
        return Ok(
            StatusListResult(
                id = state.id,
                correlationId = state.args.correlationId,
                spec = state.args.spec,
                purposes = state.args.purposes,
                proofFormat = state.args.proofFormat,
                bitsPerStatus = state.args.bitsPerStatus,
                length = state.args.length,
                issuer = state.args.issuer,
                statusListUri = state.args.statusListUri,
                signedToken = token.token,
                contentType = token.contentType,
            ),
        )
    }
}
