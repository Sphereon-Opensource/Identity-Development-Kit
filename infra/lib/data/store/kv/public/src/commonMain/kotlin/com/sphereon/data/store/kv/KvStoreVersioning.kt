/*
 * Copyright 2026 Sphereon International B.V.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sphereon.data.store.kv

import com.sphereon.core.api.IdkResult
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

/**
 * Explicit capability for immutable, optimistic-concurrency-controlled version chains.
 *
 * Mutable [KvStore] operations and versioned operations address separate data. Callers must opt in
 * to this capability; implementations must not emulate append through mutable [KvStore.put].
 */
@JsExportCompat
interface KvStoreVersioning : KvStore {
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
