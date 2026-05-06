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

package com.sphereon.oauth2.server.authorization.dpop

/**
 * Server-issued DPoP nonce manager (RFC 9449 §8). When the AS or RS requires DPoP nonces, this
 * component issues fresh nonces, validates inbound nonces against a rolling window, and rotates
 * the active nonce on a configured cadence.
 *
 * Implementations MUST be thread-safe and bounded: only the configured rolling-window size of
 * nonces is retained; older nonces age out and stop validating.
 *
 * Distinct from [DpopProofJtiCache], which dedup-tracks proof `jti` values for replay protection.
 *
 * **Tenancy partitioning contract**:
 *
 * IDK ships an in-memory implementation that is single-tenant: every caller into a given binding
 * shares the same nonce window. Implementations backing a multi-tenant deployment MUST partition
 * the nonce keyspace by tenant so that tenant A cannot observe, replay, or invalidate tenant B's
 * nonces. EDK durable implementations (Redis, SQL, Caffeine-with-namespacing, etc.) are
 * responsible for this partitioning, typically via a tenant-prefixed storage key, a per-tenant
 * table partition, or a per-tenant manager instance resolved through SessionScope. Failing to
 * partition lets a hostile tenant invalidate another tenant's `currentNonce()` and force
 * `use_dpop_nonce` retries across the entire deployment.
 */
interface DpopNonceManager {
    /**
     * Returns the current active nonce. Callers SHOULD include this on every DPoP-bearing
     * response (RFC 9449 §8) so clients can rotate proactively. The nonce is rotated when its
     * configured TTL elapses; until then the same value is returned.
     */
    suspend fun currentNonce(): String

    /**
     * Forces immediate rotation: a new nonce is generated, returned, and added to the rolling
     * window. The previous nonce remains valid until it ages out of the window. Used after the
     * server emits `use_dpop_nonce` so the client gets a fresh value to retry with.
     */
    suspend fun rotate(): String

    /**
     * Returns `true` when [nonce] is in the rolling window of accepted values, `false` when the
     * nonce is unknown, malformed, or has aged out.
     */
    suspend fun isValid(nonce: String): Boolean
}
