/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
import com.sphereon.openid.oid4vci.holder.ExchangePreAuthorizedCodeArgs
import com.sphereon.openid.oid4vci.holder.ExchangePreAuthorizedCodeCommand
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

/**
 * Exchanges a pre-authorized code for an access token.
 *
 * OID4VCI token exchange is an OAuth 2.0 token request, so this command delegates the actual
 * HTTP/form/error semantics to [ExchangeTokenCommand] and only maps OID4VCI inputs to a
 * [TokenRequest].
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<ExchangePreAuthorizedCodeCommand>())
class ExchangePreAuthorizedCodeCommandImpl(
    execution: SessionExecution,
    private val applyClientAuthenticationCommand: ApplyClientAuthenticationCommand,
    private val exchangeTokenCommand: ExchangeTokenCommand,
) : TypedServiceCommandAdapter<ExchangePreAuthorizedCodeArgs, TokenResponseWithContext, IdkError>(
        commandId = ExchangePreAuthorizedCodeCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<ExchangePreAuthorizedCodeArgs>(),
        outputTypeToken = typeToken<TokenResponseWithContext>(),
    ),
    ExchangePreAuthorizedCodeCommand {
    override val commandId: String get() = ExchangePreAuthorizedCodeCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is ExchangePreAuthorizedCodeArgs

    override suspend fun doExecute(
        args: ExchangePreAuthorizedCodeArgs,
        applyDuring: (ExchangePreAuthorizedCodeArgs) -> ExchangePreAuthorizedCodeArgs,
    ): IdkResult<TokenResponseWithContext, IdkError> {
        val applied = applyDuring(args)

        log.debug("Exchanging pre-authorized code at: ${applied.tokenEndpoint}")

        val clientAuthentication =
            applied.clientAuthentication?.let {
                applyClientAuthenticationCommand
                    .execute(ApplyClientAuthenticationArgs(config = it, tokenEndpoint = applied.tokenEndpoint))
                    .getOrElse { error -> return Err(error) }
            }
        val authBody = clientAuthentication?.bodyParameters.orEmpty()

        val tokenResponse =
            exchangeTokenCommand
                .execute(
                    ExchangeTokenArgs(
                        tokenEndpoint = applied.tokenEndpoint,
                        request =
                            TokenRequest(
                                grantType = PRE_AUTHORIZED_CODE_GRANT_TYPE,
                                preAuthorizedCode = applied.preAuthorizedCode,
                                txCode = applied.txCode,
                                clientId = authBody["client_id"] ?: applied.clientId,
                                clientSecret = authBody["client_secret"],
                                clientAssertionType = authBody["client_assertion_type"],
                                clientAssertion = authBody["client_assertion"],
                                dpop = applied.dpopProofJwt,
                                additionalParameters = additionalAuthParameters(authBody),
                                additionalHeaders =
                                    buildMap {
                                        applied.clientAttestationJwt?.let { put("OAuth-Client-Attestation", it) }
                                        applied.clientAttestationPopJwt?.let { put("OAuth-Client-Attestation-PoP", it) }
                                        clientAuthentication?.headers?.forEach { (key, value) -> put(key, value) }
                                    },
                                tokenEndpointAuthMethod = applied.clientAuthentication?.tokenEndpointAuthMethod(),
                            ),
                    ),
                )
                .getOrElse { return Err(it) }

        log.debug("Successfully obtained access token from: ${applied.tokenEndpoint}")
        return Ok(tokenResponseWithContext(tokenResponse))
    }

    companion object {
        const val PRE_AUTHORIZED_CODE_GRANT_TYPE = "urn:ietf:params:oauth:grant-type:pre-authorized_code"
    }
}
