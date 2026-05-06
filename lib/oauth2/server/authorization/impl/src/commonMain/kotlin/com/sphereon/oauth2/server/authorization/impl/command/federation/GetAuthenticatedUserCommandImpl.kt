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

package com.sphereon.oauth2.server.authorization.impl.command.federation

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.server.authorization.command.federation.AuthenticatedUserResult
import com.sphereon.oauth2.server.authorization.command.federation.GetAuthenticatedUserArgs
import com.sphereon.oauth2.server.authorization.command.federation.GetAuthenticatedUserCommand
import com.sphereon.oauth2.server.authorization.provider.AuthenticatedUser
import com.sphereon.oauth2.server.authorization.provider.AuthenticationError
import com.sphereon.oauth2.server.authorization.provider.AuthenticationMethod
import com.sphereon.oauth2.server.authorization.storage.FederationSessionStore
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.time.Clock

/**
 * Resolves the [AuthenticatedUser] for a given local authorization session by reading the
 * completed pending-federation record and projecting upstream `acr` / `amr` plus the local user
 * id. Falls back to the current clock for `authenticatedAt` when the upstream timestamp is
 * absent, and to a fixed `["fed"]` `amr` when the upstream did not advertise one.
 *
 * Tenant binding comes from [SessionExecution]; the underlying
 * [FederationSessionStore.findCompletedPendingBySession] filters by tenant-bound session, so the
 * lookup never crosses tenants.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<GetAuthenticatedUserCommand>())
class GetAuthenticatedUserCommandImpl(
    execution: SessionExecution,
    private val sessionStore: FederationSessionStore,
    private val clock: Clock,
) : TypedServiceCommandAdapter<GetAuthenticatedUserArgs, AuthenticatedUserResult, AuthenticationError>(
        commandId = GetAuthenticatedUserCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<GetAuthenticatedUserArgs>(),
        outputTypeToken = typeToken<AuthenticatedUserResult>(),
    ),
    GetAuthenticatedUserCommand {
    override val commandId: String get() = GetAuthenticatedUserCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is GetAuthenticatedUserArgs

    override suspend fun doExecute(
        args: GetAuthenticatedUserArgs,
        applyDuring: (GetAuthenticatedUserArgs) -> GetAuthenticatedUserArgs,
    ): IdkResult<AuthenticatedUserResult, AuthenticationError> {
        val applied = applyDuring(args)
        val lookup = sessionStore.findCompletedPendingBySession(applied.sessionId)
        val pending = if (lookup.isOk) lookup.value else null
        if (pending == null) {
            return Ok(AuthenticatedUserResult(user = null))
        }
        val userId = pending.userId ?: return Ok(AuthenticatedUserResult(user = null))
        return Ok(
            AuthenticatedUserResult(
                user =
                    AuthenticatedUser(
                        userId = userId,
                        authenticatedAt = pending.authenticatedAt ?: clock.now(),
                        authenticationMethod = AuthenticationMethod.OAUTH,
                        acr = pending.upstreamAcr,
                        amr = pending.upstreamAmr ?: listOf("fed"),
                    ),
            ),
        )
    }
}
