/*
 * © 2025 Sphereon International B.V.
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
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.server.authorization.command.ParseRevocationRequestArgs
import com.sphereon.oauth2.server.authorization.command.ParseRevocationRequestCommand
import com.sphereon.oauth2.server.authorization.command.RevocationRequestData
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Implementation of ParseRevocationRequestCommand
 *
 * Parses OAuth2 token revocation requests according to RFC 7009 Section 2.1.
 *
 * Request parameters (application/x-www-form-urlencoded):
 * - token (REQUIRED) - The token to revoke
 * - token_type_hint (OPTIONAL) - Hint about token type ("access_token" or "refresh_token")
 *
 * Client authentication is REQUIRED (RFC 7009 Section 2.1).
 */
@Inject
@SingleIn(SessionScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("ParseRevocationRequestCommandImpl", exact = true)
class ParseRevocationRequestCommandImpl(
    execution: SessionExecution,
) : TypedServiceCommandAdapter<ParseRevocationRequestArgs, RevocationRequestData>(
    commandId = ParseRevocationRequestCommand.COMMAND_ID,
    execution = execution,
    inputTypeToken = typeToken<ParseRevocationRequestArgs>(),
    outputTypeToken = typeToken<RevocationRequestData>(),
), ParseRevocationRequestCommand {

    override val commandId: String get() = ParseRevocationRequestCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is ParseRevocationRequestArgs

    override suspend fun doExecute(
        args: ParseRevocationRequestArgs,
        applyDuring: (ParseRevocationRequestArgs) -> ParseRevocationRequestArgs
    ): IdkResult<RevocationRequestData, IdkError> {
        val applied = applyDuring(args)
        return executeInternal(applied.requestBody).mapError { IdkError.fromDTO(it) }
    }

    private suspend fun executeInternal(
        requestBody: Map<String, List<String>>
    ): IdkResult<RevocationRequestData, AuthorizationServerError> {
        // Extract token (REQUIRED per RFC 7009 Section 2.1)
        val token = requestBody["token"]?.firstOrNull()
        if (token.isNullOrBlank()) {
            return Err(
                AuthorizationServerError.InvalidRequest(
                    details = "Missing required parameter: token"
                )
            )
        }

        // Extract token_type_hint (OPTIONAL)
        val tokenTypeHint = requestBody["token_type_hint"]?.firstOrNull()

        // Extract client_id from body
        val clientId = requestBody["client_id"]?.firstOrNull() ?: return Err(
            AuthorizationServerError.InvalidRequest(
                details = "Missing required parameter: client_id"
            )
        )

        return Ok(
            RevocationRequestData(
                token = token,
                tokenTypeHint = tokenTypeHint,
                clientId = clientId
            )
        )
    }
}
