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
import com.sphereon.openid.oid4vp.common.ResolveScopeArgs
import com.sphereon.openid.oid4vp.common.ResolveScopeCommand
import com.sphereon.openid.oid4vp.common.ResolveScopeCommandService
import com.sphereon.openid.oid4vp.common.ScopeRegistry
import com.sphereon.openid.oid4vp.common.ScopeResolutionResult
import com.sphereon.openid.oid4vp.common.ScopeResolver
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Implementation of ResolveScopeCommand.
 *
 * This command resolves scope values to DCQL queries using a configurable ScopeRegistry.
 *
 * OpenID4VP 1.0 Final Section 5.5:
 * "Wallets MAY support requesting Presentations using OAuth 2.0 scope values.
 * Such a scope parameter value MUST be an alias for a well-defined DCQL query."
 *
 * The ScopeRegistry can be configured at application startup with scope-to-DCQL mappings
 * from various sources (configuration files, well-known endpoints, etc.).
 *
 * Usage:
 * ```kotlin
 * // Configure registry at app startup
 * val registry = buildScopeRegistry {
 *     scope {
 *         scopeValue("com.example.identity")
 *         description("Identity credential presentation")
 *         dcqlQuery(identityDcqlQuery)
 *     }
 * }
 *
 * // Inject into graph
 * @Inject
 * class MyGraph(private val resolveScopeCommand: ResolveScopeCommand)
 * ```
 */
@Inject
@SingleIn(SessionScope::class)
class ResolveScopeCommandImpl(
    execution: SessionExecution,
    private val scopeRegistry: ScopeRegistry? = null,
) : TypedServiceCommandAdapter<ResolveScopeArgs, ScopeResolutionResult, IdkError>(
        commandId = ResolveScopeCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<ResolveScopeArgs>(),
        outputTypeToken = typeToken<ScopeResolutionResult>(),
    ),
    ResolveScopeCommand,
    ResolveScopeCommandService {
    override val commandId: String get() = ResolveScopeCommand.COMMAND_ID

    private val resolver = ScopeResolver(scopeRegistry ?: ScopeRegistry.empty())

    override suspend fun supports(args: Any): Boolean = args is ResolveScopeArgs

    override suspend fun resolveScope(scopeString: String?): IdkResult<ScopeResolutionResult, IdkError> = execute(ResolveScopeArgs(scopeString))

    override suspend fun doExecute(
        args: ResolveScopeArgs,
        applyDuring: (ResolveScopeArgs) -> ResolveScopeArgs,
    ): IdkResult<ScopeResolutionResult, IdkError> {
        val processedArgs = applyDuring(args)

        // Resolve scope values to DCQL queries
        val result = resolver.resolve(processedArgs.scopeString)

        return Ok(result)
    }
}
