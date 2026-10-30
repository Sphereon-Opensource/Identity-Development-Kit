/*
 * Copyright 2026 Sphereon International B.V.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sphereon.data.store.kv

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Err
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.compat.JsExportCompat
import kotlin.time.Duration

/**
 * An immutable value in a version chain.
 *
 * [versionId] is an opaque backend-generated identifier. [previousVersionId] is null only for the
 * first version in a chain. The entry metadata has the same TTL semantics as a normal [KvEntry].
 */
@JsExportCompat
data class KvVersionedEntry<V : Any>(
    val versionId: String,
    val previousVersionId: String?,
    val value: V,
    val metadata: KvEntryMetadata,
)

/** The normal outcome of an optimistic append operation. */
@JsExportCompat
sealed class KvVersionAppendResult<V : Any> {
    /** The new immutable version was appended and is now the chain head. */
    data class Applied<V : Any>(
        val entry: KvVersionedEntry<V>,
    ) : KvVersionAppendResult<V>()

    /** The expected predecessor did not match the current head. */
    data class Conflict<V : Any>(
        val currentHead: KvVersionedEntry<V>?,
    ) : KvVersionAppendResult<V>()
}

/** One typed append in a bounded multi-stream compare-and-append transaction. */
@JsExportCompat data class KvVersionAppendOperation<V : Any>(val namespace: KvNamespace<V>, val key: String,
    val expectedPreviousVersionId: String?, val value: V, val ttl: Duration)

@JsExportCompat sealed class KvAtomicAppendResult {
    /** Opaque generated version IDs in the same order as the submitted operations. */
    data class Applied(val versionIds: List<String>) : KvAtomicAppendResult()
    /** No operation was applied; the named head did not match its expected predecessor. */
    data class Conflict(val namespace: String, val key: String, val currentVersionId: String?) : KvAtomicAppendResult()
}

/**
 * Explicit capability for immutable, optimistic-concurrency-controlled version chains.
 *
 * Mutable [KvStore] operations and versioned operations address separate data. Callers must opt in
 * to this capability; implementations must not emulate append through mutable [KvStore.put].
 */
@JsExportCompat
interface KvStoreVersioning : KvStore {
    /** Atomically append 1..32 distinct typed streams; unsupported stores must fail closed. */
    suspend fun appendAtomically(operations: List<KvVersionAppendOperation<*>>): IdkResult<KvAtomicAppendResult, IdkError> =
        Err(IdkError.UNSUPPORTED_OPERATION_ERROR("kv.append-atomically", "Atomic multi-key versioning is unavailable"))

    suspend fun <V : Any> getHead(
        namespace: KvNamespace<V>,
        key: String,
    ): IdkResult<KvVersionedEntry<V>?, IdkError>

    suspend fun <V : Any> getVersion(
        namespace: KvNamespace<V>,
        key: String,
        versionId: String,
    ): IdkResult<KvVersionedEntry<V>?, IdkError>

    /**
     * Appends a value if [expectedPreviousVersionId] matches the current head.
     *
     * A null expected predecessor creates a chain only when no live chain exists. A mismatch is
     * returned as [KvVersionAppendResult.Conflict], not as an infrastructure error.
     */
    suspend fun <V : Any> append(
        namespace: KvNamespace<V>,
        key: String,
        expectedPreviousVersionId: String?,
        value: V,
        ttl: Duration,
    ): IdkResult<KvVersionAppendResult<V>, IdkError>

    /** Deletes the complete version chain without affecting a mutable entry at the same key. */
    suspend fun deleteVersioned(
        namespace: KvNamespaceId,
        key: String,
    ): IdkResult<Boolean, IdkError>
}
