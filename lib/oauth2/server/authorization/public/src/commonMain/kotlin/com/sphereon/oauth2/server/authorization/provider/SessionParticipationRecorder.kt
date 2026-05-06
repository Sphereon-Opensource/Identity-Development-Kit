/*
 * Copyright 2023-2026 Sphereon International B.V.
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

package com.sphereon.oauth2.server.authorization.provider

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError

/**
 * Hook the OAuth2 AS invokes after issuing an id_token / access_token to a
 * specific RP. Recorders persist `(sessionId, clientId)` tuples so OIDC Back-
 * Channel Logout 1.0 §2.4 can push `logout_token` only to RPs the user was
 * actually logged into, not every registered client.
 *
 * Resolved as a `Set<SessionParticipationRecorder>` multibinding by the AS
 * token-issuance commands. Each tier of the stack contributes a recorder that
 * targets its own session-tracking surface:
 *
 *  - IDK contributes `LoginSessionStoreParticipationRecorder` (writes
 *    `(clientId, sid)` into [com.sphereon.oauth2.server.authorization.storage.OidcLoginSession.rpSessions];
 *    consumed by the end-session orchestrator for OIDC Logout 1.0 fan-out).
 *  - EDK contributes `DefaultSessionParticipationRecorder` (writes the
 *    participating-RP set into the durable `AuthenticationSessionStore`;
 *    consumed by cross-session per-identity aggregation queries).
 *
 * Both tiers run on every id_token issuance because they target distinct
 * stores serving distinct OIDC concerns. An empty multibinding set is a valid
 * no-op default, used by AS deployments that opt out of session participation
 * tracking entirely.
 *
 * Failures are returned but callers (token-issuance commands) MUST log and
 * continue. A failed recorder entry degrades logout precision (over-
 * notification at worst) but must never break token issuance.
 */
interface SessionParticipationRecorder {
    suspend fun recordRpParticipation(
        sessionId: String,
        clientId: String,
    ): IdkResult<Unit, IdkError>
}
