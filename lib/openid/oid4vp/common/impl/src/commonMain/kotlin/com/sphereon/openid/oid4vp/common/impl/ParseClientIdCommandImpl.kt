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

package com.sphereon.openid.oid4vp.common.impl

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vp.common.ClientIdScheme
import com.sphereon.openid.oid4vp.common.ParseClientIdArgs
import com.sphereon.openid.oid4vp.common.ParseClientIdCommand
import com.sphereon.openid.oid4vp.common.ParseClientIdCommandService
import com.sphereon.openid.oid4vp.common.ParsedClientId
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Implementation of ParseClientIdCommand.
 *
 * Parses client_id to determine the scheme per OpenID4VP 1.0 Final section 5.9.
 *
 * Parsing rules from spec:
 * - Uses presence of : character to determine if a prefix is used
 * - The prefix is the string before the (first) : character
 * - If : is not present, treat as PRE_REGISTERED
 * - If : is present but prefix is not recognized, can treat as PRE_REGISTERED or refuse
 *
 * Returns:
 * - clientIdScheme: The detected scheme (e.g., REDIRECT_URI, DECENTRALIZED_IDENTIFIER, etc.)
 * - clientId: The part after the prefix and colon
 * - clientIdWithScheme: The complete client_id value including prefix
 */
@Inject
@SingleIn(SessionScope::class)
class ParseClientIdCommandImpl(
    execution: SessionExecution,
) : TypedServiceCommandAdapter<ParseClientIdArgs, ParsedClientId>(
        commandId = ParseClientIdCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<ParseClientIdArgs>(),
        outputTypeToken = typeToken<ParsedClientId>(),
    ),
    ParseClientIdCommand,
    ParseClientIdCommandService {
    override val commandId: String get() = ParseClientIdCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is ParseClientIdArgs

    override suspend fun parseClientId(clientId: String): IdkResult<ParsedClientId, IdkError> = execute(ParseClientIdArgs(clientId))

    override suspend fun doExecute(
        args: ParseClientIdArgs,
        applyDuring: (ParseClientIdArgs) -> ParseClientIdArgs,
    ): IdkResult<ParsedClientId, IdkError> {
        val applied = applyDuring(args)
        val fullClientId = applied.clientId

        // Parse using the ClientIdScheme companion object
        val scheme = ClientIdScheme.fromClientId(fullClientId)
        val clientId = ClientIdScheme.extractClientIdWithoutScheme(fullClientId)

        val result =
            ParsedClientId(
                clientIdScheme = scheme,
                clientId = clientId,
                clientIdWithScheme = fullClientId,
            )

        return Ok(result)
    }
}
