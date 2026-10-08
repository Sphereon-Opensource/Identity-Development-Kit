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

package com.sphereon.oauth2.client.impl.token

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.client.command.ExchangeTokenArgs
import com.sphereon.oauth2.client.command.ExchangeTokenCommand
import com.sphereon.oauth2.client.service.TokenService
import com.sphereon.oauth2.common.model.TokenRequest
import com.sphereon.oauth2.common.model.TokenResponse
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Implementation of TokenService that delegates to command implementations
 *
 * This service is session-scoped to support multi-tenancy, matching the pattern
 * used by other IDK services.
 *
 * @property exchangeTokenCommand Command for exchanging tokens at the token endpoint
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<TokenService>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("TokenServiceImpl", exact = true)
class TokenServiceImpl(
    private val exchangeTokenCommand: ExchangeTokenCommand,
) : TokenService {
    /**
     * Implementation of Commands that exposes the injected command instances.
     */
    inner class CommandsImpl : TokenService.Commands {
        override val exchangeToken: ExchangeTokenCommand = this@TokenServiceImpl.exchangeTokenCommand
    }

    override val commands: TokenService.Commands = CommandsImpl()

    // Delegate service method to command

    override suspend fun exchangeToken(
        tokenEndpoint: String,
        request: TokenRequest,
    ): IdkResult<TokenResponse, IdkError> = exchangeTokenCommand.execute(ExchangeTokenArgs(tokenEndpoint, request))

    @ContributesTo(scope = SessionScope::class)
    interface Graph {
        val tokenService: TokenService
    }
}
