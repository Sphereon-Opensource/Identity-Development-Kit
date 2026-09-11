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
import com.sphereon.oauth2.server.authorization.provider.FederationProviderRuntimeResolver
import com.sphereon.oauth2.server.authorization.storage.FederationSessionStore
import com.sphereon.oauth2.server.authorization.routing.AuthenticationRoutePlanner
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
    private val sessionExecution: SessionExecution,
    private val sessionStore: FederationSessionStore,
    private val providerResolver: FederationProviderRuntimeResolver,
    private val exchangeCodeAndExtractClaimsCommand: ExchangeCodeAndExtractClaimsCommand,
    private val handleFederationOutcomeCommand: HandleFederationOutcomeCommand,
    private val handleReconciliationOutcomeCommand: HandleReconciliationOutcomeCommand,
    private val authenticationRoutePlanner: AuthenticationRoutePlanner,
    private val clock: kotlin.time.Clock,
) : TypedServiceCommandAdapter<HandleFederationCallbackArgs, FederationCallbackOutcome, AuthenticationError>(
        commandId = HandleFederationCallbackCommand.COMMAND_ID,
        execution = sessionExecution,
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
        if (applied.code != null && applied.error != null) {
            return Err(AuthenticationError.Generic(description = "Federation callback cannot contain both a code and an upstream error"))
        }
        val retrieveResult = sessionStore.consumePendingFederation(applied.state)
        val pending =
            (if (retrieveResult.isOk) retrieveResult.value else null)
                ?: return Err(
                    AuthenticationError.Generic(
                        description = "Unknown or expired federation state",
                    ),
                )
        if (pending.tenantId != sessionExecution.tenantId) {
            return Err(AuthenticationError.Generic(description = "Federation callback tenant mismatch"))
        }
        if (pending.expiresAt <= clock.now()) {
            return Err(AuthenticationError.Generic(description = "Federation callback transaction expired"))
        }
        if (pending.federationBindingId != pending.authenticationRoute.selectedBindingId || pending.upstreamIssuer != pending.metadata.issuer) {
            return Err(AuthenticationError.Generic(description = "Federation callback transaction binding mismatch"))
        }
        val routeValidation = authenticationRoutePlanner.revalidate(pending.authenticationRoute, pending.federationBindingId)
        if (routeValidation.isErr) {
            return Err(AuthenticationError.Generic(description = routeValidation.error.message.defaultMessage))
        }

        applied.error?.let { upstreamError ->
            val mapped = upstreamError.takeIf { it in UPSTREAM_ERROR_ALLOWLIST } ?: "server_error"
            return com.sphereon.core.api.Ok(
                FederationCallbackOutcome.upstreamError(
                    sessionId = pending.sessionId,
                    error = mapped,
                    errorDescription = applied.errorDescription?.take(512),
                ),
            )
        }
        val authorizationCode = applied.code
            ?: return Err(AuthenticationError.Generic(description = "Federation callback did not contain a code or upstream error"))

        val providerConfig: FederationProviderConfig = providerResolver.resolve(pending.providerId).getOrElse { return Err(it) }
        if (providerConfig.issuerUrl != pending.upstreamIssuer || providerConfig.id != pending.federationBindingId) {
            return Err(AuthenticationError.Generic(description = "Resolved provider does not match the pinned federation transaction"))
        }

        val exchange =
            exchangeCodeAndExtractClaimsCommand
                .execute(
                    ExchangeCodeAndExtractClaimsArgs(
                        code = authorizationCode,
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

    private companion object {
        val UPSTREAM_ERROR_ALLOWLIST = setOf("access_denied", "login_required", "interaction_required", "temporarily_unavailable", "server_error")
    }
}
