package com.sphereon.oauth2.client.impl.token

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.client.command.ExchangeTokenArgs
import com.sphereon.oauth2.client.command.ExchangeTokenCommand
import com.sphereon.oauth2.common.model.TokenRequest
import com.sphereon.oauth2.common.model.TokenResponse
import com.sphereon.oauth2.client.service.TokenService
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
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
    private val exchangeTokenCommand: ExchangeTokenCommand
) : TokenService {

    /**
     * DI component interface for TokenService
     */
    @ContributesTo(scope = SessionScope::class)
    interface Component {
        val tokenService: TokenService
    }

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
        request: TokenRequest
    ): IdkResult<TokenResponse, IdkError> {
        return exchangeTokenCommand.execute(ExchangeTokenArgs(tokenEndpoint, request))
    }
}
