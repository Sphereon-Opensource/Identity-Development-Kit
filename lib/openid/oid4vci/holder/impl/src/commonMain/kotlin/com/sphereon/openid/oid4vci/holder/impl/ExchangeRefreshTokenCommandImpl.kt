/*
 * Copyright 2026 Sphereon International B.V.
 * Licensed under the Apache License, Version 2.0.
 */

package com.sphereon.openid.oid4vci.holder.impl

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vci.holder.ExchangeRefreshTokenArgs
import com.sphereon.openid.oid4vci.holder.ExchangeRefreshTokenCommand
import com.sphereon.openid.oid4vci.holder.TokenResponseWithContext
import com.sphereon.oauth2.client.command.ExchangeTokenArgs
import com.sphereon.oauth2.client.command.ExchangeTokenCommand
import com.sphereon.oauth2.common.command.ApplyClientAuthenticationArgs
import com.sphereon.oauth2.common.command.ApplyClientAuthenticationCommand
import com.sphereon.oauth2.common.model.TokenRequest
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/** OID4VCI holder operation for the OAuth 2.0 refresh-token grant. */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<ExchangeRefreshTokenCommand>())
class ExchangeRefreshTokenCommandImpl(
    execution: SessionExecution,
    private val applyClientAuthentication: ApplyClientAuthenticationCommand,
    private val exchangeToken: ExchangeTokenCommand,
) : TypedServiceCommandAdapter<ExchangeRefreshTokenArgs, TokenResponseWithContext, IdkError>(
        commandId = ExchangeRefreshTokenCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<ExchangeRefreshTokenArgs>(),
        outputTypeToken = typeToken<TokenResponseWithContext>(),
    ),
    ExchangeRefreshTokenCommand {
    override val commandId: String get() = ExchangeRefreshTokenCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is ExchangeRefreshTokenArgs

    override suspend fun doExecute(
        args: ExchangeRefreshTokenArgs,
        applyDuring: (ExchangeRefreshTokenArgs) -> ExchangeRefreshTokenArgs,
    ): IdkResult<TokenResponseWithContext, IdkError> {
        val applied = applyDuring(args)
        val clientAuthentication =
            applied.clientAuthentication?.let { authentication ->
                applyClientAuthentication
                    .execute(ApplyClientAuthenticationArgs(authentication, applied.tokenEndpoint))
                    .getOrElse { return Err(it) }
            }
        val authenticationBody = clientAuthentication?.bodyParameters.orEmpty()
        val response =
            exchangeToken
                .execute(
                    ExchangeTokenArgs(
                        tokenEndpoint = applied.tokenEndpoint,
                        request =
                            TokenRequest(
                                grantType = REFRESH_TOKEN_GRANT_TYPE,
                                refreshToken = applied.refreshToken,
                                clientId = authenticationBody["client_id"] ?: applied.clientId,
                                clientSecret = authenticationBody["client_secret"],
                                clientAssertionType = authenticationBody["client_assertion_type"],
                                clientAssertion = authenticationBody["client_assertion"],
                                dpop = applied.dpopProofJwt,
                                additionalParameters = additionalAuthParameters(authenticationBody),
                                additionalHeaders =
                                    buildMap {
                                        applied.clientAttestationJwt?.let { put("OAuth-Client-Attestation", it) }
                                        applied.clientAttestationPopJwt?.let { put("OAuth-Client-Attestation-PoP", it) }
                                        clientAuthentication?.headers?.forEach { (key, value) -> put(key, value) }
                                    },
                                tokenEndpointAuthMethod = applied.clientAuthentication?.tokenEndpointAuthMethod(),
                            ),
                    ),
                ).getOrElse { return Err(it) }
        return Ok(tokenResponseWithContext(response))
    }

    private companion object {
        const val REFRESH_TOKEN_GRANT_TYPE = "refresh_token"
    }
}
