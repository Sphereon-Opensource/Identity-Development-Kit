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

import com.sphereon.core.api.IdkResult
import com.sphereon.oauth2.server.authorization.command.VerifiedAuthorizationRequest
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import kotlin.time.Instant

/**
 * Storage for Pushed Authorization Requests (RFC 9126).
 *
 * Each entry is keyed by the issued `request_uri` (`urn:ietf:params:oauth:request_uri:<id>`)
 * and carries the verified authorization request plus its expiry. RFC 9126 §2.2 requires that
 * entries be:
 *
 *  - short-lived (5 s to 10 min, typically 60 s),
 *  - single-use AT THE POINT OF AUTHORIZATION (i.e. when the AS issues an auth code,
 *    not on every `/authorize` visit — see FAPI 2.0 SP §5.3.2.2 Note 3),
 *  - rejected after expiry (returned as `null`).
 *
 * The two reads — [lookupRequest] and [consumeRequest] — codify that split:
 *  - `/authorize` calls [lookupRequest] (peek) so the user can hit the endpoint twice in a
 *    row, navigate back, refresh, etc., without burning the request_uri.
 *  - The auth-code issuance path calls [consumeRequest] (atomic remove) so single-use is
 *    enforced exactly once, when the authorization actually completes.
 *
 * Implementations MUST be thread-safe; [consumeRequest] MUST be atomic so two concurrent
 * code issuances against the same `request_uri` cannot both succeed.
 */
interface PushedAuthorizationRequestStorage {
    /**
     * Stores a verified pushed authorization request under [requestUri]. The entry expires at
     * [expiresAt]; reads after that instant MUST behave as if the entry was never stored.
     */
    suspend fun storeRequest(
        requestUri: String,
        request: VerifiedAuthorizationRequest,
        expiresAt: Instant,
    ): IdkResult<Unit, AuthorizationServerError.StorageError>

    /**
     * Returns the request stored under [requestUri] WITHOUT removing it, or `null` if no
     * entry exists or the entry expired before this call. Repeated lookups MUST keep
     * returning the same entry until [consumeRequest] (or expiry) clears it. Used by the
     * `/authorize` endpoint per FAPI 2.0 SP §5.3.2.2 Note 3.
     */
    suspend fun lookupRequest(requestUri: String): IdkResult<VerifiedAuthorizationRequest?, AuthorizationServerError.StorageError>

    /**
     * Atomically removes and returns the request stored under [requestUri], or `null` if no
     * entry exists or the entry expired before this call. Single-use enforcement: a second
     * call with the same [requestUri] always returns `null`. Called at the auth-code
     * issuance step so the request_uri is consumed when authorization actually completes.
     */
    suspend fun consumeRequest(requestUri: String): IdkResult<VerifiedAuthorizationRequest?, AuthorizationServerError.StorageError>

    /**
     * Drops every recorded request_uri. Intended for tests and shutdown hooks.
     */
    suspend fun clear(): IdkResult<Unit, AuthorizationServerError.StorageError>
}
