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

package com.sphereon.oauth2.server.authorization.impl.command.discovery

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.model.AuthorizationServerMetadata
import com.sphereon.oauth2.server.authorization.command.BuildServerMetadataArgs
import com.sphereon.oauth2.server.authorization.command.discovery.HandleDiscoveryRequestArgs
import com.sphereon.oauth2.server.authorization.command.discovery.HandleDiscoveryRequestCommand
import com.sphereon.oauth2.server.authorization.service.AuthorizationServerService
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Implementation of [HandleDiscoveryRequestCommand]. Body lifted verbatim from
 * `OAuth2Handlers.handleDiscoveryRequest`: a single delegation to `commands.buildServerMetadata`.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<HandleDiscoveryRequestCommand>())
class HandleDiscoveryRequestCommandImpl(
    execution: SessionExecution,
    private val authorizationServerService: AuthorizationServerService,
) : TypedServiceCommandAdapter<HandleDiscoveryRequestArgs, AuthorizationServerMetadata, IdkError>(
        commandId = HandleDiscoveryRequestCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<HandleDiscoveryRequestArgs>(),
        outputTypeToken = typeToken<AuthorizationServerMetadata>(),
    ),
    HandleDiscoveryRequestCommand {
    override val commandId: String get() = HandleDiscoveryRequestCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is HandleDiscoveryRequestArgs

    override suspend fun doExecute(
        args: HandleDiscoveryRequestArgs,
        applyDuring: (HandleDiscoveryRequestArgs) -> HandleDiscoveryRequestArgs,
    ): IdkResult<AuthorizationServerMetadata, IdkError> {
        val applied = applyDuring(args)
        return authorizationServerService.commands.buildServerMetadata.execute(
            BuildServerMetadataArgs(
                serverId = applied.serverId,
                baseUrlOverride = applied.baseUrlOverride,
            ),
        )
    }
}
