package com.sphereon.oauth2.client.impl.authorization

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.client.command.CreateAuthorizationRequestUrlCommand
import com.sphereon.oauth2.client.command.CreateAuthorizationRequestUrlOptions
import com.sphereon.oauth2.client.command.ParseAuthorizationResponseArgs
import com.sphereon.oauth2.client.command.ParseAuthorizationResponseCommand
import com.sphereon.oauth2.client.command.ParsedAuthorizationResponse
import com.sphereon.oauth2.client.model.AuthorizationRequestUrlResult
import com.sphereon.oauth2.client.service.AuthorizationService
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Implementation of AuthorizationService that delegates to command implementations
 *
 * This service follows the Command/Service pattern for consistency with other IDK services
 * like PkceService, MetadataService, SdJwtService, and JwtService.
 *
 * @property createAuthorizationRequestUrlCommand Command for creating authorization URLs
 * @property parseAuthorizationResponseCommand Command for parsing authorization responses
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<AuthorizationService>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("AuthorizationServiceImpl", exact = true)
class AuthorizationServiceImpl(
    private val createAuthorizationRequestUrlCommand: CreateAuthorizationRequestUrlCommand,
    private val parseAuthorizationResponseCommand: ParseAuthorizationResponseCommand
) : AuthorizationService {

    /**
     * DI component interface for AuthorizationService
     */
    @ContributesTo(scope = SessionScope::class)
    interface Component {
        val authorizationService: AuthorizationService
    }

    /**
     * Inner class exposing commands for direct access
     */
    inner class CommandsImpl : AuthorizationService.Commands {
        override val createAuthorizationRequestUrl = this@AuthorizationServiceImpl.createAuthorizationRequestUrlCommand
        override val parseAuthorizationResponse = this@AuthorizationServiceImpl.parseAuthorizationResponseCommand
    }

    override val commands: AuthorizationService.Commands = CommandsImpl()

    /**
     * Create authorization request URL
     *
     * @param options Options for creating the authorization request URL
     * @return IdkResult containing the authorization URL and associated data
     */
    override suspend fun createAuthorizationRequestUrl(
        options: CreateAuthorizationRequestUrlOptions
    ): IdkResult<AuthorizationRequestUrlResult, IdkError> {
        return createAuthorizationRequestUrlCommand.execute(options)
    }

    /**
     * Parse authorization response from redirect URL
     *
     * @param redirectUrl The full redirect URL with query parameters
     * @return IdkResult containing parsed authorization response
     */
    override suspend fun parseAuthorizationResponse(
        redirectUrl: String
    ): IdkResult<ParsedAuthorizationResponse, IdkError> {
        return parseAuthorizationResponseCommand.execute(ParseAuthorizationResponseArgs(redirectUrl))
    }
}
