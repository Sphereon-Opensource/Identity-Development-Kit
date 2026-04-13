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

package com.sphereon.identity.resolution.impl.command

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.identity.resolution.command.ResolveIdentityCommand
import com.sphereon.identity.resolution.model.IdentityResolutionResult
import com.sphereon.identity.resolution.model.ResolveIdentityArgs
import com.sphereon.identity.resolution.service.IdentityResolver
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Orchestrates identity resolution by trying registered resolvers in priority order.
 * Returns [IdentityResolutionResult] with `resolved=true` if found, `resolved=false` otherwise.
 */
@Inject
@SingleIn(SessionScope::class)
class ResolveIdentityCommandImpl(
    execution: SessionExecution,
    private val resolvers: Set<IdentityResolver>,
) : TypedServiceCommandAdapter<ResolveIdentityArgs, IdentityResolutionResult>(
        commandId = ResolveIdentityCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<ResolveIdentityArgs>(),
        outputTypeToken = typeToken<IdentityResolutionResult>(),
    ),
    ResolveIdentityCommand {
    override val commandId: String get() = ResolveIdentityCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is ResolveIdentityArgs

    override suspend fun doExecute(
        args: ResolveIdentityArgs,
        applyDuring: (ResolveIdentityArgs) -> ResolveIdentityArgs,
    ): IdkResult<IdentityResolutionResult, IdkError> {
        val applied = applyDuring(args)
        val config = applied.config ?: return Ok(IdentityResolutionResult.NOT_FOUND)
        if (!config.enabled) {
            return Ok(IdentityResolutionResult.NOT_FOUND)
        }

        val applicable =
            resolvers
                .filter { it.supports(applied.tenantId, config.resolvers[it.resolverId]) }
                .sortedByDescending { config.resolvers[it.resolverId]?.priority ?: it.priority }

        for (resolver in applicable) {
            val result = resolver.resolve(applied.identifier, applied.tenantId, config.resolvers[resolver.resolverId])
            val resolved = result.getOrElse { return Err(it) }
            if (resolved.resolved) {
                return Ok(resolved)
            }
        }
        return Ok(IdentityResolutionResult.NOT_FOUND)
    }
}
