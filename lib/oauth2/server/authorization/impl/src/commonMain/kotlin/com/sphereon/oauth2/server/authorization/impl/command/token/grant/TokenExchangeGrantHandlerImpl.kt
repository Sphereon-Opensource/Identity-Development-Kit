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

package com.sphereon.oauth2.server.authorization.impl.command.token.grant

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.model.TokenResponse
import com.sphereon.oauth2.server.authorization.command.GrantParameters
import com.sphereon.oauth2.server.authorization.command.token.GrantContext
import com.sphereon.oauth2.server.authorization.command.token.GrantHandler
import com.sphereon.oauth2.server.authorization.command.token.GrantHandlerKeys
import com.sphereon.oauth2.server.authorization.command.token.HandleTokenRequestArgs
import com.sphereon.oauth2.server.authorization.command.token.TokenExchangeJourneyCommand
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.StringKey
import dev.zacsweers.metro.binding

/**
 * RFC 8693 token-exchange grant handler.
 *
 * The token endpoint hands this grant the raw request before any DPoP proof or client
 * authentication is processed. [TokenExchangeJourneyCommand] then authenticates the client once
 * and runs every exchange step. The pre-authenticated [GrantHandler.handle] entry point is refused:
 * a [GrantContext] built elsewhere is not accepted as authentication evidence for an exchange.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoMap(SessionScope::class, binding = binding<GrantHandler>())
@StringKey(GrantHandlerKeys.TOKEN_EXCHANGE)
class TokenExchangeGrantHandlerImpl(
    private val tokenExchangeJourney: TokenExchangeJourneyCommand,
) : GrantHandler {
    override val grantType: String = GrantHandlerKeys.TOKEN_EXCHANGE

    override fun supports(params: GrantParameters): Boolean = params is GrantParameters.TokenExchange

    override suspend fun handle(
        params: GrantParameters,
        context: GrantContext,
    ): IdkResult<TokenResponse, IdkError> =
        errOf(AuthorizationServerError.ServerError(details = "Token exchange must be dispatched with the raw token request"))

    internal suspend fun handleRawRequest(args: HandleTokenRequestArgs): IdkResult<TokenResponse, IdkError> = tokenExchangeJourney.execute(args)
}
