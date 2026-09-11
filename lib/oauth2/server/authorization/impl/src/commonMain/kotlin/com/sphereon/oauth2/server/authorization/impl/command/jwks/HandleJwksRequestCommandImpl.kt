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

package com.sphereon.oauth2.server.authorization.impl.command.jwks

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.server.authorization.command.GetJwksArgs
import com.sphereon.oauth2.server.authorization.command.GetJwksCommand
import com.sphereon.oauth2.server.authorization.command.JwksResult
import com.sphereon.oauth2.server.authorization.command.jwks.HandleJwksRequestArgs
import com.sphereon.oauth2.server.authorization.command.jwks.HandleJwksRequestCommand
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Implementation of [HandleJwksRequestCommand]. Body lifted verbatim from
 * `OAuth2Handlers.handleJwksRequest`: a single delegation to [GetJwksCommand]. The exact command
 * is injected directly so publishing public keys cannot construct every unrelated authorization
 * server command in the request SessionScope.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<HandleJwksRequestCommand>())
class HandleJwksRequestCommandImpl(
    execution: SessionExecution,
    private val getJwks: GetJwksCommand,
) : TypedServiceCommandAdapter<HandleJwksRequestArgs, JwksResult, IdkError>(
        commandId = HandleJwksRequestCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<HandleJwksRequestArgs>(),
        outputTypeToken = typeToken<JwksResult>(),
    ),
    HandleJwksRequestCommand {
    override val commandId: String get() = HandleJwksRequestCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is HandleJwksRequestArgs

    override suspend fun doExecute(
        args: HandleJwksRequestArgs,
        applyDuring: (HandleJwksRequestArgs) -> HandleJwksRequestArgs,
    ): IdkResult<JwksResult, IdkError> {
        applyDuring(args)
        return getJwks.execute(GetJwksArgs())
    }
}
