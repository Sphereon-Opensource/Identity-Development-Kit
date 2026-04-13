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
 *
 */

package com.sphereon.core.api.session

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.di.session.SessionContext

/**
 * Policy-based command authorization.
 * Implementations can check actor, subject, resource context.
 */
interface CommandAuthorizer {
    /**
     * Checks if the command is authorized to execute.
     *
     * @param commandId The command ID to authorize
     * @param sessionContext The session context for the current execution
     * @return Ok(Unit) if authorized, Err with IdkError if not authorized
     */
    suspend fun isAuthorized(
        commandId: CommandId,
        sessionContext: SessionContext
    ): IdkResult<Unit, IdkError>
}

/**
 * Permissive authorizer - allows all commands.
 * This is the default in IDK, suitable for development and testing.
 * Production deployments should use PatternCommandAuthorizer or PolicyDecisionProvider.
 */
object PermissiveAuthorizer : CommandAuthorizer {
    override suspend fun isAuthorized(
        commandId: CommandId,
        sessionContext: SessionContext
    ): IdkResult<Unit, IdkError> = Ok(Unit)
}

/**
 * Creates an authorization error for a denied command.
 *
 * @param commandId The command ID that was denied
 * @param reason The reason for the denial
 * @param actor Optional actor ID for audit purposes
 * @return IdkError representing the authorization failure
 */
fun authorizationError(
    commandId: CommandId,
    reason: String,
    actor: String? = null
): IdkError = CommandErrors.notAuthorized(commandId = commandId, reason = reason, actor = actor)
