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

package com.sphereon.oauth2.client.transaction

import com.sphereon.core.api.IdkResult
import com.sphereon.oauth2.common.error.Oauth2Error

/**
 * Store of in-flight OIDC login transactions keyed by `state`.
 *
 * CSRF protection for the OIDC authorization-code flow depends on the RP being able to atomically
 * consume a stored transaction at callback time. A stored transaction carries the nonce and PKCE
 * verifier back into the callback handler; `consumeByState` MUST both fetch and remove the entry in
 * one step so concurrent replays of the same `state` cannot both succeed.
 *
 * The default implementation is in-memory (`InMemoryOidcLoginTransactionStore`) — single-node and
 * fine for OIDF conformance. Production deployments should substitute a distributed store (Redis,
 * Postgres) — flagged as a WP5 follow-up.
 *
 * Lives at `AppScope` per the "repositories take `tenantId` as an arg, services above persistence
 * don't" rule: a multi-tenant RP hosting several clients in one app partitions its transactions by
 * `tenantId` so state values collide only within a tenant.
 */
public interface OidcLoginTransactionStore {
    /**
     * Record a newly-initiated login transaction. Keyed on `(state, tenantId)` so two tenants
     * can legally produce identical state values without colliding.
     */
    public suspend fun put(transaction: OidcLoginTransaction): IdkResult<Unit, Oauth2Error>

    /**
     * Atomically fetch and remove the transaction for a callback `state`.
     *
     * Returns [Oauth2Error.InvalidGrant] when:
     * - the state is unknown (CSRF or replay),
     * - the state has already been consumed,
     * - or the transaction has expired.
     */
    public suspend fun consumeByState(
        state: String,
        tenantId: String? = null,
    ): IdkResult<OidcLoginTransaction, Oauth2Error>
}
