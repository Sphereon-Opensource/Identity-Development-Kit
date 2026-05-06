/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

@file:OptIn(ExperimentalTime::class)

package com.sphereon.oauth2.server.authorization.backchannellogout

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Per-message dedup for the inbound OIDC Back-Channel Logout receiver.
 *
 * BCL 1.0 §2.6 requires "reasonable" `jti` dedup so a replayed `logout_token`
 * doesn't trigger a second termination wave (idempotent semantics, but also
 * to keep audit signal honest). Implementations track `(tenantId, jti)`
 * tuples until [expiresAt] then prune.
 *
 * Default in-memory binding ships in `-impl` for pure-IDK / single-replica
 * deployments. Multi-replica deployments contribute a Postgres-backed binding
 * via `replaces = [InMemoryBackChannelLogoutTokenSeenCache::class]`.
 */
interface BackChannelLogoutTokenSeenCache {
    /**
     * Atomically check + mark a `(tenantId, jti)` as seen.
     *
     * Returns `Ok(true)` when the token was previously unseen and is now
     * marked; `Ok(false)` when this jti has already been processed (the
     * receiver should idempotently return 200 + no-op).
     *
     * [expiresAt] tells the cache when the marker can safely be pruned —
     * the receiver should pass the JWT's `exp` claim (or a configured upper
     * bound, e.g. `now + 24h`).
     */
    suspend fun markSeen(
        tenantId: String,
        jti: String,
        expiresAt: Instant,
    ): IdkResult<Boolean, IdkError>
}
