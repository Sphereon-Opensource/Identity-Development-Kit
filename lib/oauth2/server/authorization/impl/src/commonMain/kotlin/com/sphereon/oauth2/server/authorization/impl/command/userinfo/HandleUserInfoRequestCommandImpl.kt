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

package com.sphereon.oauth2.server.authorization.impl.command.userinfo

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.server.authorization.command.GetUserInfoArgs
import com.sphereon.oauth2.server.authorization.command.UserInfoResponse
import com.sphereon.oauth2.server.authorization.command.userinfo.HandleUserInfoRequestArgs
import com.sphereon.oauth2.server.authorization.command.userinfo.HandleUserInfoRequestCommand
import com.sphereon.oauth2.server.authorization.service.AuthorizationServerService
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Implementation of [HandleUserInfoRequestCommand]. Body lifted verbatim from
 * `OAuth2Handlers.handleUserInfoRequest`.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<HandleUserInfoRequestCommand>())
class HandleUserInfoRequestCommandImpl(
    execution: SessionExecution,
    private val authorizationServerService: AuthorizationServerService,
) : TypedServiceCommandAdapter<HandleUserInfoRequestArgs, UserInfoResponse, IdkError>(
        commandId = HandleUserInfoRequestCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<HandleUserInfoRequestArgs>(),
        outputTypeToken = typeToken<UserInfoResponse>(),
    ),
    HandleUserInfoRequestCommand {
    override val commandId: String get() = HandleUserInfoRequestCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is HandleUserInfoRequestArgs

    override suspend fun doExecute(
        args: HandleUserInfoRequestArgs,
        applyDuring: (HandleUserInfoRequestArgs) -> HandleUserInfoRequestArgs,
    ): IdkResult<UserInfoResponse, IdkError> {
        val applied = applyDuring(args)
        return authorizationServerService.commands.getUserInfo.execute(
            GetUserInfoArgs(accessToken = applied.accessToken),
        )
    }
}
