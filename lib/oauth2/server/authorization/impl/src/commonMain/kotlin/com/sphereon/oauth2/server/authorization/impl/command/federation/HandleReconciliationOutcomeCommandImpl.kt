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

package com.sphereon.oauth2.server.authorization.impl.command.federation

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.server.authorization.command.federation.HandleReconciliationOutcomeArgs
import com.sphereon.oauth2.server.authorization.command.federation.HandleReconciliationOutcomeCommand
import com.sphereon.oauth2.server.authorization.command.federation.ReconciliationCompleteOutcome
import com.sphereon.oauth2.server.authorization.impl.provider.ReconciliationCallbackHandler
import com.sphereon.oauth2.server.authorization.provider.AuthenticationError
import com.sphereon.oauth2.server.authorization.storage.FederationSessionStore
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Reconciliation (IDV) flow completion: removes the pending federation record and forwards the
 * raw upstream claims to the auth-bridge via [ReconciliationCallbackHandler]. Pairs symmetrically
 * with [com.sphereon.oauth2.server.authorization.command.federation.HandleFederationOutcomeCommand];
 * when reconciliation eventually becomes its own provider (see remediation doc "Out of Scope"),
 * the binding moves to a dedicated module without touching this command body.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<HandleReconciliationOutcomeCommand>())
class HandleReconciliationOutcomeCommandImpl(
    execution: SessionExecution,
    private val sessionStore: FederationSessionStore,
    private val reconciliationHandler: ReconciliationCallbackHandler,
) : TypedServiceCommandAdapter<HandleReconciliationOutcomeArgs, ReconciliationCompleteOutcome, AuthenticationError>(
        commandId = HandleReconciliationOutcomeCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<HandleReconciliationOutcomeArgs>(),
        outputTypeToken = typeToken<ReconciliationCompleteOutcome>(),
    ),
    HandleReconciliationOutcomeCommand {
    override val commandId: String get() = HandleReconciliationOutcomeCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is HandleReconciliationOutcomeArgs

    override suspend fun doExecute(
        args: HandleReconciliationOutcomeArgs,
        applyDuring: (HandleReconciliationOutcomeArgs) -> HandleReconciliationOutcomeArgs,
    ): IdkResult<ReconciliationCompleteOutcome, AuthenticationError> {
        val applied = applyDuring(args)
        val pending = applied.pending
        val providerConfig = applied.providerConfig

        val oid4vpSessionId =
            pending.flowContext?.oid4vpSessionId
                ?: return Err(
                    AuthenticationError.Generic(
                        description = "Missing OID4VP session ID for reconciliation flow",
                    ),
                )

        sessionStore.removePendingFederation(pending.state)

        val redirectUrl =
            reconciliationHandler.onReconciliationComplete(
                claims = applied.rawClaims,
                providerId = pending.providerId,
                issuer = providerConfig.issuerUrl,
                oid4vpSessionId = oid4vpSessionId,
            )

        return Ok(ReconciliationCompleteOutcome(redirectUrl = redirectUrl))
    }
}
