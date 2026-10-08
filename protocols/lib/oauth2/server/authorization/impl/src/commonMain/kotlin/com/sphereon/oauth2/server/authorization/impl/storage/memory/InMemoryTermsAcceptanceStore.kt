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
import com.sphereon.oauth2.server.authorization.storage.TermsAcceptanceRecord
import com.sphereon.oauth2.server.authorization.storage.TermsAcceptanceStore
import com.sphereon.oauth2.server.authorization.storage.TermsAcceptanceStoreError
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlin.time.Instant

/**
 * In-memory default [TermsAcceptanceStore]. Per-JVM map keyed by
 * `(tenantId, identityId)`; tenant isolation is enforced by the composite key.
 * Production deployments override this binding with a Postgres impl.
 *
 * Memory: O(distinct identities that have ever accepted any ToS version). The
 * store does not prune on its own — acceptance state is meant to outlive a
 * given ToS version since the evaluator compares against the *current* version,
 * not the *recorded* one. Re-acceptance of a newer version overwrites the prior
 * record in place rather than accumulating history.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<TermsAcceptanceStore>())
class InMemoryTermsAcceptanceStore :
    SynchronizedObject(),
    TermsAcceptanceStore {
    /**
     * `(tenantId, identityId) → TermsAcceptanceRecord(version, acceptedAt)`. Insertion-
     * ordered for deterministic iteration during admin diagnostics; the production
     * store shouldn't rely on order.
     */
    private val records: MutableMap<Pair<String, String>, TermsAcceptanceRecord> = mutableMapOf()

    override suspend fun findAcceptance(
        tenantId: String,
        identityId: String,
    ): IdkResult<TermsAcceptanceRecord?, TermsAcceptanceStoreError> =
        synchronized(this) {
            Ok(records[tenantId to identityId])
        }

    override suspend fun recordAcceptance(
        tenantId: String,
        identityId: String,
        version: String,
        now: Instant,
    ): IdkResult<Unit, TermsAcceptanceStoreError> =
        synchronized(this) {
            records[tenantId to identityId] = TermsAcceptanceRecord(version = version, acceptedAt = now)
            Ok(Unit)
        }
}
