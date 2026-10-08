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

package com.sphereon.oauth2.server.authorization.impl.dpop

import com.sphereon.oauth2.server.authorization.dpop.DpopProofJtiCache
import com.sphereon.oauth2.server.authorization.storage.SingleUseObjectNamespaces
import com.sphereon.oauth2.server.authorization.storage.SingleUseObjectStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.time.Instant

/**
 * Default [DpopProofJtiCache] backed by the unified [SingleUseObjectStore]. Every
 * replay-prevention surface (DPoP jti, client-assertion jti, OIDC nonce, attestation
 * pop jti, back-channel logout token jti, action tokens) shares one store and one
 * namespace prefix; deployments swap the store implementation (in-memory default →
 * Postgres `PostgresSingleUseObjectStore`) once and every surface inherits the new
 * backend.
 *
 * Note on tenancy partitioning: per the [DpopProofJtiCache] contract the IDK in-memory
 * default is single-tenant. Multi-tenant deployments should compose their own backing
 * store impl that prefixes [SingleUseObjectStore.recordIfNew] keys with a tenant id, or
 * deploy a per-tenant store instance. EDK Postgres impls handle partitioning at the
 * storage layer.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<DpopProofJtiCache>())
class InMemoryDpopProofJtiCacheImpl(
    private val store: SingleUseObjectStore,
) : DpopProofJtiCache {
    override suspend fun hasBeenUsed(jti: String): Boolean = store.isRecorded(SingleUseObjectNamespaces.DPOP_JTI, jti).valueOrFalse()

    override suspend fun markAsUsed(
        jti: String,
        expiresAt: Instant
    ) {
        // The two-phase contract (`hasBeenUsed` then `markAsUsed`) is inherently TOCTOU —
        // upgrading callers to call [SingleUseObjectStore.recordIfNew] directly closes the
        // race. This adapter preserves the legacy SPI shape for in-flight callers; new code
        // should bypass this cache and use the unified SPI.
        store.recordIfNew(SingleUseObjectNamespaces.DPOP_JTI, jti, expiresAt)
    }

    override suspend fun clear() {
        // The unified store is process-wide; clearing it would also drop every other
        // namespace's records (client-assertion jti, nonce, ...). The DpopProofJtiCache
        // contract documents `clear()` as a test/shutdown hook only, so the trade-off is
        // acceptable: tests get a fresh store on construction, shutdown drops the JVM.
        store.clear()
    }

    private fun com.sphereon.core.api.IdkResult<Boolean, *>.valueOrFalse(): Boolean = if (isOk) value else false
}
