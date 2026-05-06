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

package com.sphereon.oauth2.server.authorization.storage

import com.sphereon.core.api.IdkResult
import kotlin.time.Instant

/**
 * Generic single-use-object store: a multi-namespace, expiring key-set used for
 * replay-prevention across the AS. Five separate caches in the AS today have the same
 * shape (record-if-new with TTL, evict expired) but slightly different APIs:
 *
 *  - [com.sphereon.oauth2.server.authorization.dpop.DpopProofJtiCache] — DPoP `jti`
 *  - [ClientAssertionJtiStore] — `client_secret_jwt` / `private_key_jwt` `jti`, namespaced by `client_id`
 *  - [AttestationPopJtiStorage] — attestation PoP `jti`
 *  - [NonceStorage] — OIDC nonce (with `verifyAndConsume` atomicity)
 *  - [com.sphereon.oauth2.server.authorization.backchannellogout.BackChannelLogoutTokenSeenCache] — logout token `jti`
 *
 * This SPI consolidates them. The five existing SPIs become thin specialisations that
 * delegate to this store with a fixed [namespace]. A deployment switching to the
 * Postgres impl gets cross-instance replay-prevention for ALL five at once instead of
 * five separate persistence modules.
 *
 * **Atomicity** is the load-bearing property. A non-atomic check-then-record race lets
 * an attacker submit the same proof twice in parallel and have both succeed, defeating
 * the replay-prevention. [recordIfNew] is the only call shape that's safe for the
 * defense; [isRecorded] is a non-atomic convenience for callers that want to fail-fast
 * without paying the recording cost (e.g. observability dashboards).
 *
 * **TTL** is per-key (`expiresAt`). Background cleanup via [prune] is opportunistic —
 * the store implementations also lazily expire on read so a missed prune doesn't break
 * the contract.
 */
interface SingleUseObjectStore {
    /**
     * Atomically record [key] in [namespace] with the supplied [expiresAt]. Returns:
     *  - `Ok(true)` — newly recorded, request may proceed.
     *  - `Ok(false)` — already recorded with non-expired entry, request MUST be
     *    rejected as replay.
     *  - `Err(...)` — storage backend failure (typically a transient DB error); the
     *    caller decides whether to fail-closed or degrade.
     */
    suspend fun recordIfNew(
        namespace: String,
        key: String,
        expiresAt: Instant,
    ): IdkResult<Boolean, SingleUseObjectStoreError>

    /**
     * Non-atomic check. Returns `true` when [key] in [namespace] is recorded with a
     * non-expired entry. Useful for fail-fast paths that don't want to pay recording
     * cost when the entry is clearly absent. Production replay-prevention MUST go
     * through [recordIfNew] — the gap between this check and any subsequent record is
     * a window an attacker can exploit.
     */
    suspend fun isRecorded(
        namespace: String,
        key: String,
    ): IdkResult<Boolean, SingleUseObjectStoreError>

    /**
     * Drop entries whose `expiresAt` is at or before [now]. Returns the count of
     * dropped rows so an operator scheduler can alert when 0 rows are dropped over a
     * long window (suggests no traffic or a broken pruner). Implementations may also
     * lazily expire on read so the contract is maintained even if [prune] never runs.
     */
    suspend fun prune(now: Instant): IdkResult<Int, SingleUseObjectStoreError>

    /**
     * Drop every entry in every namespace. Intended for tests + administrative
     * operations (e.g. emergency invalidation after key rotation). Production code
     * should NOT call this on a live deployment — it loses every in-flight
     * replay-prevention record.
     */
    suspend fun clear(): IdkResult<Unit, SingleUseObjectStoreError>
}

/**
 * Typed errors surfaced by [SingleUseObjectStore]. Operations return one of these
 * wrapped in [com.sphereon.core.api.IdkResult.Err]; callers translate them into the
 * appropriate wire response (typically `server_error` since a replay-prevention store
 * outage is an AS infrastructure problem, not a client issue).
 */
sealed class SingleUseObjectStoreError {
    /**
     * The store backend (DB, in-memory map) failed. [operation] names the SPI method
     * that triggered it; [details] is a free-text diagnostic that MUST NOT be surfaced
     * to RPs.
     */
    data class StorageFailure(
        val operation: String,
        val details: String,
    ) : SingleUseObjectStoreError()
}

/**
 * Standard namespaces the IDK uses. Adding a new replay-prevention surface? Pick a
 * stable string here so the Postgres `single_use_object` table can be partitioned /
 * pruned per namespace without each caller having to reinvent its naming. Custom
 * deployments may pass any string; collisions across namespaces are forbidden by the
 * PK on (namespace, key).
 */
object SingleUseObjectNamespaces {
    /** DPoP proof `jti` (RFC 9449 §11.1 replay defense). */
    const val DPOP_JTI: String = "dpop.jti"

    /** `client_secret_jwt` / `private_key_jwt` assertion `jti`. */
    const val CLIENT_ASSERTION_JTI: String = "client_assertion.jti"

    /** Attestation PoP `jti` (draft attestation-based-client-auth). */
    const val ATTESTATION_POP_JTI: String = "attestation_pop.jti"

    /** OIDC nonce. */
    const val OIDC_NONCE: String = "oidc.nonce"

    /** OIDC Back-Channel Logout 1.0 `logout_token` `jti`. */
    const val BACKCHANNEL_LOGOUT_JTI: String = "backchannel_logout.jti"

    /** Action tokens (password reset, email verify, magic link) — reserved for P2-K2. */
    const val ACTION_TOKEN: String = "action_token"
}
