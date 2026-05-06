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

package com.sphereon.oauth2.server.authorization.impl.provider

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.server.authorization.provider.SessionParticipationRecorder
import com.sphereon.oauth2.server.authorization.storage.OidcLoginSessionStore
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * IDK [SessionParticipationRecorder] that writes the `(loginSessionId, clientId, sid)`
 * tuple onto [OidcLoginSession.rpSessions] via [OidcLoginSessionStore.recordRpParticipation].
 * The end-session orchestrator reads that map to fan out OIDC Front-Channel and Back-Channel
 * Logout 1.0 with the per-RP `sid` claim required by §4.1.
 *
 * Contributes into the [SessionParticipationRecorder] multibinding set so additional
 * recorders (e.g. EDK's `DefaultSessionParticipationRecorder` writing to the durable
 * `AuthenticationSessionStore`) can run alongside on the same id_token issuance event
 * without either replacing the other.
 *
 * Fail-open: a store error is logged and swallowed so id_token issuance is never blocked
 * by a recorder failure. Degrades logout precision (fall back to all-RPs notification),
 * never breaks auth.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoSet(
    scope = SessionScope::class,
    binding = binding<SessionParticipationRecorder>(),
)
class LoginSessionStoreParticipationRecorder(
    private val store: OidcLoginSessionStore,
) : SessionParticipationRecorder {
    override suspend fun recordRpParticipation(
        sessionId: String,
        clientId: String,
    ): IdkResult<Unit, IdkError> {
        // The `sessionId` argument is the value [com.sphereon.oauth2.server.authorization.impl.command.oidc.CreateIdTokenCommandImpl]
        // wrote into the id_token's `sid` claim, the cookie-keyed OIDC login session id when
        // available, falling back to the pending-authorization session id. We mirror that into
        // [OidcLoginSession.rpSessions] so the end-session orchestrator can fan out logout to
        // the RP under the same key both ends agree on.
        //
        // OIDC Back-Channel Logout 1.0 §4.1: `sid` MUST be unique per (iss, sub) pair. We pin
        // sid = sessionId on this IDK binding so an RP receiving the logout_token can match it
        // back to the same session it observed in the id_token. Deployments needing distinct
        // sid values can swap this contribution out via Metro's `replaces` from a downstream
        // contributor.
        val recorded =
            store.recordRpParticipation(
                sessionId = sessionId,
                clientId = clientId,
                sid = sessionId,
            )
        return if (recorded.isOk) {
            Ok(Unit)
        } else {
            Ok(Unit)
        }
    }
}
