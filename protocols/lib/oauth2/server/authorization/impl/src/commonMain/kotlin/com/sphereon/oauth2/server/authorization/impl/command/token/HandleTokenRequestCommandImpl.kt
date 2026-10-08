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

package com.sphereon.oauth2.server.authorization.impl.command.token

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.command.VerifyDpopProofCommand
import com.sphereon.oauth2.common.config.OAuth2ServersConfigProvider
import com.sphereon.oauth2.common.model.GrantType
import com.sphereon.oauth2.common.model.TokenResponse
import com.sphereon.oauth2.server.authorization.command.ParseTokenRequestCommand
import com.sphereon.oauth2.server.authorization.command.VerifyClientAuthenticationCommand
import com.sphereon.oauth2.server.authorization.command.token.GrantHandler
import com.sphereon.oauth2.server.authorization.command.token.HandleTokenRequestArgs
import com.sphereon.oauth2.server.authorization.command.token.HandleTokenRequestCommand
import com.sphereon.oauth2.server.authorization.dpop.DpopNonceManager
import com.sphereon.oauth2.server.authorization.dpop.DpopProofJtiCache
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import com.sphereon.oauth2.server.authorization.impl.command.TokenPathStage
import com.sphereon.oauth2.server.authorization.impl.command.TokenPathStageTimings
import com.sphereon.oauth2.server.authorization.impl.command.token.grant.TokenExchangeGrantHandlerImpl
import com.sphereon.oauth2.server.authorization.impl.command.token.grant.errOf
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

import dev.zacsweers.metro.ExposeImplBinding
/**
 * Implementation of [HandleTokenRequestCommand]: orchestrates `POST /token` (RFC 6749 §3.2).
 *
 * Steps: parse the request through [com.sphereon.oauth2.server.authorization.command.ParseTokenRequestCommand],
 * verify the DPoP proof (RFC 9449) when present and enforce the AS nonce policy, optionally derive
 * the mTLS cert thumbprint for cert-bound tokens (RFC 8705 §3), verify client authentication, then
 * dispatch to the [GrantHandler] whose [GrantHandler.supports] matches the parsed grant. RFC 8693
 * exchanges are handed to their grant handler as the raw request after parsing; the
 * token-exchange journey performs proof verification and client authentication itself. Each
 * grant-specific handler owns the access / refresh / id-token minting and any grant-specific
 * post-issuance bookkeeping (refresh-token rotation, device-code consumption).
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<HandleTokenRequestCommand>())
@ExposeImplBinding
class HandleTokenRequestCommandImpl(
    execution: SessionExecution,
    private val parseTokenRequestCommand: ParseTokenRequestCommand,
    private val verifyClientAuthenticationCommand: VerifyClientAuthenticationCommand,
    private val serversConfigProvider: OAuth2ServersConfigProvider,
    private val verifyDpopProofCommand: Lazy<VerifyDpopProofCommand>,
    private val dpopProofJtiCache: Lazy<DpopProofJtiCache>,
    private val dpopNonceManager: Lazy<DpopNonceManager>,
    private val grantHandlers: Map<String, Lazy<GrantHandler>>,
) : TypedServiceCommandAdapter<HandleTokenRequestArgs, TokenResponse, IdkError>(
        commandId = HandleTokenRequestCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<HandleTokenRequestArgs>(),
        outputTypeToken = typeToken<TokenResponse>(),
    ),
    HandleTokenRequestCommand {
    override val commandId: String get() = HandleTokenRequestCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is HandleTokenRequestArgs

    override suspend fun doExecute(
        args: HandleTokenRequestArgs,
        applyDuring: (HandleTokenRequestArgs) -> HandleTokenRequestArgs,
    ): IdkResult<TokenResponse, IdkError> {
        val timings = TokenPathStageTimings(operation = "token-request")
        var outcome = "failed"
        return try {
            executeTokenRequest(args, applyDuring, timings).also { result ->
                outcome = if (result.isOk) "success" else "rejected"
            }
        } finally {
            timings.report(log, outcome)
        }
    }

    private val authenticator =
        TokenEndpointRequestAuthenticator(
            parseTokenRequestCommand = parseTokenRequestCommand,
            verifyClientAuthenticationCommand = verifyClientAuthenticationCommand,
            serversConfigProvider = serversConfigProvider,
            verifyDpopProofCommand = verifyDpopProofCommand,
            dpopProofJtiCache = dpopProofJtiCache,
            dpopNonceManager = dpopNonceManager,
            log = log,
        )

    private suspend fun executeTokenRequest(
        args: HandleTokenRequestArgs,
        applyDuring: (HandleTokenRequestArgs) -> HandleTokenRequestArgs,
        timings: TokenPathStageTimings,
    ): IdkResult<TokenResponse, IdkError> {
        val applied = applyDuring(args)
        val tokenRequest = authenticator.parse(applied, timings).getOrElse { error -> return Err(error) }
        val grantType = tokenRequest.grantType.value

        // RFC 8693 exchanges run as one journey that authenticates the client itself. Route the
        // raw request before any proof or client-assertion replay state is consumed here.
        if (tokenRequest.grantType == GrantType.TOKEN_EXCHANGE) {
            val handler =
                grantHandlers[grantType]?.value
                    ?: return errOf(AuthorizationServerError.UnsupportedGrantType(grantType = grantType))
            if (handler !is TokenExchangeGrantHandlerImpl || !handler.supports(tokenRequest.grantParameters)) {
                return errOf(AuthorizationServerError.ServerError(details = "Grant handler binding mismatch for '$grantType'"))
            }
            return timings.record(TokenPathStage.GRANT_DISPATCH) { handler.handleRawRequest(applied) }
        }

        val authenticated =
            authenticator
                .authenticate(applied, tokenRequest, execution.tenantId, timings)
                .getOrElse { error -> return Err(error) }

        val handler =
            grantHandlers[grantType]?.value
                ?: return errOf(AuthorizationServerError.UnsupportedGrantType(grantType = grantType))
        if (handler.grantType != grantType || !handler.supports(tokenRequest.grantParameters)) {
            return errOf(
                AuthorizationServerError.ServerError(
                    details = "Grant handler binding mismatch for '$grantType'",
                ),
            )
        }

        return timings.record(TokenPathStage.GRANT_DISPATCH) {
            dispatchWithVerifiedClientAuthorization(
                handler = handler,
                params = tokenRequest.grantParameters,
                context = authenticated.context,
                clientAuthorization = authenticated.clientAuthorization,
            )
        }
    }
}
