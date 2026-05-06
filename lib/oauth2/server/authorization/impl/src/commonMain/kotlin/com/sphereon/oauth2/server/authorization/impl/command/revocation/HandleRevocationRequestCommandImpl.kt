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

package com.sphereon.oauth2.server.authorization.impl.command.revocation

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.server.authorization.command.ParseRevocationRequestArgs
import com.sphereon.oauth2.server.authorization.command.RevokeTokenArgs
import com.sphereon.oauth2.server.authorization.command.VerifyClientAuthenticationArgs
import com.sphereon.oauth2.server.authorization.command.revocation.HandleRevocationRequestArgs
import com.sphereon.oauth2.server.authorization.command.revocation.HandleRevocationRequestCommand
import com.sphereon.oauth2.server.authorization.impl.command.extractClientAuthentication
import com.sphereon.oauth2.server.authorization.service.AuthorizationServerService
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Implementation of [HandleRevocationRequestCommand]. Body lifted verbatim from
 * `OAuth2Handlers.handleRevocationRequest`. Same authentication pattern as introspection per
 * RFC 7009 §2.1 — the endpoint must authenticate the client before acting on a potentially
 * valuable token.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<HandleRevocationRequestCommand>())
class HandleRevocationRequestCommandImpl(
    execution: SessionExecution,
    private val authorizationServerService: AuthorizationServerService,
) : TypedServiceCommandAdapter<HandleRevocationRequestArgs, Unit, IdkError>(
        commandId = HandleRevocationRequestCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<HandleRevocationRequestArgs>(),
        outputTypeToken = typeToken<Unit>(),
    ),
    HandleRevocationRequestCommand {
    override val commandId: String get() = HandleRevocationRequestCommand.COMMAND_ID

    private val commands get() = authorizationServerService.commands

    override suspend fun supports(args: Any): Boolean = args is HandleRevocationRequestArgs

    override suspend fun doExecute(
        args: HandleRevocationRequestArgs,
        applyDuring: (HandleRevocationRequestArgs) -> HandleRevocationRequestArgs,
    ): IdkResult<Unit, IdkError> {
        val applied = applyDuring(args)
        val requestBody = applied.requestBody
        val requestHeaders = applied.requestHeaders
        val httpUrl = applied.httpUrl

        // Same authentication pattern as [handleIntrospectionRequest] per RFC 7009 §2.1 — the
        // endpoint must authenticate the client before acting on a potentially valuable token.
        val extracted =
            extractClientAuthentication(requestBody, requestHeaders)
                .getOrElse { error -> return Err(IdkError.fromDTO(error)) }
        val resolvedClientId =
            extracted.clientId
                ?: return Err(
                    IdkError.UNAUTHORIZED_ERROR(
                        message = "client authentication is required at /revoke",
                    ),
                )
        commands.verifyClientAuthentication
            .execute(
                VerifyClientAuthenticationArgs(
                    clientAuthentication = extracted.clientAuthentication,
                    clientId = resolvedClientId,
                    tokenEndpointUrl = httpUrl,
                ),
            ).getOrElse { error -> return Err(error) }

        val revocationRequest =
            commands.parseRevocationRequest
                .execute(ParseRevocationRequestArgs(requestBody))
                .getOrElse { error -> return Err(error) }

        return commands.revokeToken.execute(
            RevokeTokenArgs(
                token = revocationRequest.token,
                tokenTypeHint = revocationRequest.tokenTypeHint,
                clientId = resolvedClientId,
            ),
        )
    }
}
