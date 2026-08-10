/*
 * Copyright 2026 Sphereon International B.V.
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

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.oauth2.common.model.TokenResponse
import com.sphereon.oauth2.server.authorization.command.GrantParameters
import com.sphereon.oauth2.server.authorization.command.VerifiedClientAuthorization
import com.sphereon.oauth2.server.authorization.command.token.GrantContext
import com.sphereon.oauth2.server.authorization.command.token.GrantHandler
import com.sphereon.oauth2.server.authorization.impl.command.token.grant.AuthorizationCodeGrantHandlerImpl
import com.sphereon.oauth2.server.authorization.impl.command.token.grant.ClientCredentialsGrantHandlerImpl
import com.sphereon.oauth2.server.authorization.impl.command.token.grant.PasswordGrantHandlerImpl
import com.sphereon.oauth2.server.authorization.impl.command.token.grant.TokenExchangeGrantHandlerImpl

/**
 * Keeps verified registration facts inside the implementation module. Public contributed handlers
 * retain their normal extension contract, while the token-endpoint orchestrator can avoid a
 * second registry assembly only for the built-in handlers it authenticated itself.
 */
internal suspend fun dispatchWithVerifiedClientAuthorization(
    handler: GrantHandler,
    params: GrantParameters,
    context: GrantContext,
    clientAuthorization: VerifiedClientAuthorization?,
): IdkResult<TokenResponse, IdkError> =
    when (handler) {
        is AuthorizationCodeGrantHandlerImpl -> handler.handleTrusted(params, context, clientAuthorization)
        is ClientCredentialsGrantHandlerImpl -> handler.handleTrusted(params, context, clientAuthorization)
        is PasswordGrantHandlerImpl -> handler.handleTrusted(params, context, clientAuthorization)
        is TokenExchangeGrantHandlerImpl -> handler.handleTrusted(params, context, clientAuthorization)
        else -> handler.handle(params, context)
    }
