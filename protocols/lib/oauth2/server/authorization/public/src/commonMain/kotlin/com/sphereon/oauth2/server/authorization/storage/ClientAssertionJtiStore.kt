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

package com.sphereon.oauth2.server.authorization.storage

import kotlin.time.Instant

/**
 * Replay guard for OIDC Core §9 / RFC 7523 client assertion `jti` values.
 *
 * The authorization server must reject a reused `jti` within an assertion's lifetime. This
 * interface's default implementation is in-memory and TTL-backed, sufficient for single-node
 * conformance; multi-node production deployments should override it with a Redis- or
 * Postgres-backed implementation.
 */
public interface ClientAssertionJtiStore {
    /**
     * Atomically record a `jti` for a client if it hasn't been seen before within TTL.
     *
     * @return `true` if the `jti` was newly recorded (request may proceed);
     *   `false` if it was already seen within its assertion lifetime (reject the request).
     */
    public suspend fun recordIfNew(
        clientId: String,
        jti: String,
        expiresAt: Instant,
    ): Boolean
}
