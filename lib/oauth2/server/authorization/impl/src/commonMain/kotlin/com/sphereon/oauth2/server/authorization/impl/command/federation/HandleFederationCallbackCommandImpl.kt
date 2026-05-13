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
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.server.authorization.command.federation.ExchangeCodeAndExtractClaimsArgs
import com.sphereon.oauth2.server.authorization.command.federation.ExchangeCodeAndExtractClaimsCommand
import com.sphereon.oauth2.server.authorization.command.federation.FederationCallbackOutcome
import com.sphereon.oauth2.server.authorization.command.federation.HandleFederationCallbackArgs
import com.sphereon.oauth2.server.authorization.command.federation.HandleFederationCallbackCommand
import com.sphereon.oauth2.server.authorization.command.federation.HandleFederationOutcomeArgs
import com.sphereon.oauth2.server.authorization.command.federation.HandleFederationOutcomeCommand
import com.sphereon.oauth2.server.authorization.command.federation.HandleReconciliationOutcomeArgs
import com.sphereon.oauth2.server.authorization.command.federation.HandleReconciliationOutcomeCommand
import com.sphereon.oauth2.server.authorization.config.FederationProviderConfig
import com.sphereon.oauth2.server.authorization.provider.AuthenticationError
import com.sphereon.oauth2.server.authorization.provider.FederationProviderRegistry
import com.sphereon.oauth2.server.authorization.storage.FederationSessionStore
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Top-level federation callback dispatcher. Looks up the pending record, resolves the provider
 * config, delegates token exchange to [ExchangeCodeAndExtractClaimsCommand], then routes to
 * [HandleFederationOutcomeCommand] for normal logins or [HandleReconciliationOutcomeCommand]
 * for IDV reconciliation.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<HandleFederationCallbackCommand>())
class HandleFederationCallbackCommandImpl(
    execution: SessionExecution,
    private val sessionStore: FederationSessionStore,
    private val providerRegistry: FederationProviderRegistry,
    private val exchangeCodeAndExtractClaimsCommand: ExchangeCodeAndExtractClaimsCommand,
    private val handleFederationOutcomeCommand: HandleFederationOutcomeCommand,
    private val handleReconciliationOutcomeCommand: HandleReconciliationOutcomeCommand,
) : TypedServiceCommandAdapter<HandleFederationCallbackArgs, FederationCallbackOutcome, AuthenticationError>(
        commandId = HandleFederationCallbackCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<HandleFederationCallbackArgs>(),
        outputTypeToken = typeToken<FederationCallbackOutcome>(),
    ),
    HandleFederationCallbackCommand {
    override val commandId: String get() = HandleFederationCallbackCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is HandleFederationCallbackArgs

    override suspend fun doExecute(
        args: HandleFederationCallbackArgs,
        applyDuring: (HandleFederationCallbackArgs) -> HandleFederationCallbackArgs,
    ): IdkResult<FederationCallbackOutcome, AuthenticationError> {
        val applied = applyDuring(args)
        val retrieveResult = sessionStore.retrievePendingFederation(applied.state)
        val pending =
            (if (retrieveResult.isOk) retrieveResult.value else null)
                ?: return Err(
                    AuthenticationError.Generic(
                        description = "Unknown or expired federation state",
                    ),
                )

        val providerConfig: FederationProviderConfig =
            resolveProvider(pending.providerId)
                ?: return Err(
                    AuthenticationError.Generic(
                        description = "Provider '${pending.providerId}' no longer available",
                    ),
                )

        val exchange =
            exchangeCodeAndExtractClaimsCommand
                .execute(
                    ExchangeCodeAndExtractClaimsArgs(
                        code = applied.code,
                        pending = pending,
                        providerConfig = providerConfig,
                    ),
                ).getOrElse {
                    return Err(it)
                }

        val flow = pending.flowContext?.flow ?: "federation"
        return if (flow == "reconciliation") {
            handleReconciliationOutcomeCommand
                .execute(
                    HandleReconciliationOutcomeArgs(
                        rawClaims = exchange.claims,
                        pending = pending,
                        providerConfig = providerConfig,
                    ),
                ).map { FederationCallbackOutcome.reconciliationComplete(redirectUrl = it.redirectUrl) }
        } else {
            handleFederationOutcomeCommand
                .execute(
                    HandleFederationOutcomeArgs(
                        exchange = exchange,
                        state = applied.state,
                        pending = pending,
                        providerConfig = providerConfig,
                    ),
                ).map { FederationCallbackOutcome.federationComplete(sessionId = it.sessionId) }
        }
    }

    private fun resolveProvider(providerId: String?): FederationProviderConfig? {
        val id = providerId ?: providerRegistry.defaultProviderId() ?: return null
        return providerRegistry.findById(id)?.takeIf { it.enabled }
    }
}
