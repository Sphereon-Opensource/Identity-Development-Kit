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

package com.sphereon.oauth2.server.authorization.impl.command.introspection

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.model.TokenIntrospectionResponse
import com.sphereon.oauth2.server.authorization.command.IntrospectTokenArgs
import com.sphereon.oauth2.server.authorization.command.ParseIntrospectionRequestArgs
import com.sphereon.oauth2.server.authorization.command.VerifyClientAuthenticationArgs
import com.sphereon.oauth2.server.authorization.command.introspection.HandleIntrospectionRequestArgs
import com.sphereon.oauth2.server.authorization.command.introspection.HandleIntrospectionRequestCommand
import com.sphereon.oauth2.server.authorization.impl.command.extractClientAuthentication
import com.sphereon.oauth2.server.authorization.service.AuthorizationServerService
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Implementation of [HandleIntrospectionRequestCommand]. Body lifted verbatim from
 * `OAuth2Handlers.handleIntrospectionRequest`. Authenticates the caller before doing anything
 * else (RFC 7662 §2.1) so unauthenticated callers cannot enumerate token metadata.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<HandleIntrospectionRequestCommand>())
class HandleIntrospectionRequestCommandImpl(
    execution: SessionExecution,
    private val authorizationServerService: AuthorizationServerService,
) : TypedServiceCommandAdapter<HandleIntrospectionRequestArgs, TokenIntrospectionResponse, IdkError>(
        commandId = HandleIntrospectionRequestCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<HandleIntrospectionRequestArgs>(),
        outputTypeToken = typeToken<TokenIntrospectionResponse>(),
    ),
    HandleIntrospectionRequestCommand {
    override val commandId: String get() = HandleIntrospectionRequestCommand.COMMAND_ID

    private val commands get() = authorizationServerService.commands

    override suspend fun supports(args: Any): Boolean = args is HandleIntrospectionRequestArgs

    override suspend fun doExecute(
        args: HandleIntrospectionRequestArgs,
        applyDuring: (HandleIntrospectionRequestArgs) -> HandleIntrospectionRequestArgs,
    ): IdkResult<TokenIntrospectionResponse, IdkError> {
        val applied = applyDuring(args)
        val requestBody = applied.requestBody
        val requestHeaders = applied.requestHeaders
        val httpUrl = applied.httpUrl

        // Authenticate the caller before doing anything else (RFC 7662 §2.1). This mirrors the
        // /token path: extract credentials → verify → only then act on the token. Prior revisions
        // of this handler trusted `client_id` from the request body alone, which would let an
        // unauthenticated caller enumerate token metadata (Task 1.13).
        val extracted =
            extractClientAuthentication(requestBody, requestHeaders)
                .getOrElse { error -> return Err(IdkError.fromDTO(error)) }
        val resolvedClientId =
            extracted.clientId
                ?: return Err(
                    IdkError.UNAUTHORIZED_ERROR(
                        message = "client authentication is required at /introspect",
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

        // Parse the introspection request body. The parser still pulls `token` / `token_type_hint`
        // / `client_id`; by this point the caller is authenticated so the parsed client_id is
        // redundant — but we use the authenticated id downstream.
        val introspectionRequest =
            commands.parseIntrospectionRequest
                .execute(ParseIntrospectionRequestArgs(requestBody))
                .getOrElse { error -> return Err(error) }

        return commands.introspectToken.execute(
            IntrospectTokenArgs(
                token = introspectionRequest.token,
                tokenTypeHint = introspectionRequest.tokenTypeHint,
                clientId = resolvedClientId,
            ),
        )
    }
}
