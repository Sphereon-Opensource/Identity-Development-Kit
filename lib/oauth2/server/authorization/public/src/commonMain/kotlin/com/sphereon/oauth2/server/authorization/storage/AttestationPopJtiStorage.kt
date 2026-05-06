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
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError

/**
 * Storage abstraction for Client Attestation PoP `jti` values used in replay detection
 * (draft-ietf-oauth-attestation-based-client-auth §10.5 / §12.1; both drafts 07 and 08 require
 * the jti and use it as the replay key).
 *
 * Semantics differ from [AttestationChallengeStorage]: jti values are *minted by the client*,
 * not by the AS. The AS records each jti on first sight together with its expiry time and
 * rejects any subsequent presentation of the same jti while it is still inside the sliding
 * window anchored on the PoP `iat`.
 *
 * Implementation requirements:
 * - Atomic record-or-reject (no time-of-check/time-of-use gap on concurrent submissions).
 * - Sliding window keyed off the PoP `iat` plus the AS-configured replay window length, so the
 *   table stays bounded.
 * - Thread-safe.
 */
interface AttestationPopJtiStorage {
    /**
     * Atomically record the supplied [jti] for the supplied PoP [iat]. Returns
     * [AuthorizationServerError.InvalidClientAttestation] when the jti has already been seen
     * within its sliding window (replay), and an error from the underlying storage backend on
     * failure.
     *
     * The [windowSeconds] parameter is supplied per call so the AS can tune the window
     * dynamically from [com.sphereon.oauth2.common.config.OAuth2ServerInstanceConfig.attestationPopJtiReplayWindowSeconds]
     * without the storage having to re-read configuration.
     */
    suspend fun recordOrReject(
        jti: String,
        iat: Long,
        windowSeconds: Int,
    ): IdkResult<Unit, AuthorizationServerError>
}
