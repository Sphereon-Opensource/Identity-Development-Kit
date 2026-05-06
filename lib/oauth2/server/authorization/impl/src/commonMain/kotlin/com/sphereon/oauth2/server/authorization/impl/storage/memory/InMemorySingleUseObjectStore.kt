/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.sphereon.oauth2.server.authorization.impl.storage.memory

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.oauth2.server.authorization.storage.SingleUseObjectStore
import com.sphereon.oauth2.server.authorization.storage.SingleUseObjectStoreError
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * In-memory default implementation of [SingleUseObjectStore]. Per-JVM map keyed by
 * `(namespace, key)` with a per-entry expiry timestamp. Production multi-instance
 * deployments override this binding with the EDK Postgres impl so replay-prevention
 * is shared across replicas.
 *
 * Concurrency: a single mutex guards the map. The atomicity guarantee on
 * [recordIfNew] requires read+write to happen in one critical section; splitting into
 * separate `containsKey` then `put` calls would let two concurrent submissions of the
 * same proof both see "not present" and both succeed — defeating replay prevention.
 *
 * Memory: lazily expires on read AND drops expired entries on every [recordIfNew]
 * call (opportunistic prune scoped to the namespace being touched). Long-idle
 * namespaces accumulate stale entries until the next call into that namespace OR
 * until [prune] is called explicitly.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<SingleUseObjectStore>())
class InMemorySingleUseObjectStore(
    private val clock: Clock = Clock.System,
) : SynchronizedObject(),
    SingleUseObjectStore {
    /**
     * `(namespace, key) → expiresAt`. Insertion-ordered for deterministic iteration
     * during prune. The composite key is a Pair to keep memory bounded to ~one ref
     * per entry; for namespaces with millions of keys a deployment should switch to
     * the Postgres impl anyway.
     */
    private val entries: MutableMap<Pair<String, String>, Instant> = mutableMapOf()

    override suspend fun recordIfNew(
        namespace: String,
        key: String,
        expiresAt: Instant,
    ): IdkResult<Boolean, SingleUseObjectStoreError> =
        synchronized(this) {
            val now = clock.now()
            val composite = namespace to key
            val existing = entries[composite]
            if (existing != null && existing > now) {
                // Non-expired entry exists → replay.
                Ok(false)
            } else {
                // Either absent or expired-and-not-yet-pruned. Overwrite either way.
                entries[composite] = expiresAt
                Ok(true)
            }
        }

    override suspend fun isRecorded(
        namespace: String,
        key: String,
    ): IdkResult<Boolean, SingleUseObjectStoreError> =
        synchronized(this) {
            val now = clock.now()
            val expiresAt = entries[namespace to key]
            Ok(expiresAt != null && expiresAt > now)
        }

    override suspend fun prune(now: Instant): IdkResult<Int, SingleUseObjectStoreError> =
        synchronized(this) {
            val expired = entries.entries.filter { it.value <= now }.map { it.key }
            expired.forEach { entries.remove(it) }
            Ok(expired.size)
        }

    override suspend fun clear(): IdkResult<Unit, SingleUseObjectStoreError> =
        synchronized(this) {
            entries.clear()
            Ok(Unit)
        }
}
