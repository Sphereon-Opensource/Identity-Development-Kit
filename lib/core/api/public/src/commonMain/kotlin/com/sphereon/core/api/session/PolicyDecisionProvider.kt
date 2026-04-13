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

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import kotlinx.serialization.Serializable

/**
 * Context for policy decisions.
 * Contains all information needed to evaluate authorization policies.
 *
 * @property actorId The principal performing the action
 * @property subjectId Optional subject being acted upon
 * @property resourceId Optional resource being accessed
 * @property commandId The command being executed
 * @property correlationId Optional correlation ID for tracing
 * @property metadata Additional context for policy evaluation
 */
@Serializable
data class PolicyContext(
    val actorId: String,
    val subjectId: String? = null,
    val resourceId: String? = null,
    val commandId: String,
    val correlationId: String? = null,
    val metadata: Map<String, String> = emptyMap()
)

/**
 * Interface for external policy engines (OPA, Cedarling).
 * IDK provides the interface; EDK/VDX provides implementations.
 *
 * Example OPA Rego policy:
 * ```rego
 * package idk.commands
 *
 * default allow = false
 *
 * allow {
 *   startswith(input.commandId, "kms.")
 *   input.command == "get"
 * }
 * ```
 *
 * Example Cedar policy:
 * ```cedar
 * permit(
 *   principal == User::"acct-123",
 *   action == Action::"create",
 *   resource == Command::"party.parties.create"
 * );
 * ```
 */
interface PolicyDecisionProvider {
    /**
     * Evaluates whether the action is allowed by the policy engine.
     *
     * @param context The policy context containing actor, command, resource info
     * @return Ok(true) if allowed, Ok(false) if denied, Err if policy evaluation failed
     */
    suspend fun isAllowed(context: PolicyContext): IdkResult<Boolean, IdkError>
}

/**
 * Default implementation - always allows (IDK default).
 * Production deployments should replace with actual policy engine integration.
 */
object PermissivePolicyProvider : PolicyDecisionProvider {
    override suspend fun isAllowed(context: PolicyContext): IdkResult<Boolean, IdkError> = Ok(true)
}

/**
 * Denies all requests - useful for testing and fail-closed scenarios.
 */
object DenyAllPolicyProvider : PolicyDecisionProvider {
    override suspend fun isAllowed(context: PolicyContext): IdkResult<Boolean, IdkError> = Ok(false)
}

/**
 * CommandAuthorizer adapter for PolicyDecisionProvider.
 * Bridges the policy engine interface with the command authorization system.
 *
 * @param policy The policy decision provider to delegate to
 * @param contextBuilder Function to extract actor/subject/resource from session context
 */
class PolicyAuthorizer(
    private val policy: PolicyDecisionProvider,
    private val contextBuilder: (CommandId, com.sphereon.di.session.SessionContext) -> PolicyContext = { cmdId, session ->
        PolicyContext(
            actorId = session.sessionId,
            commandId = cmdId.value
        )
    }
) : CommandAuthorizer {

    override suspend fun isAuthorized(
        commandId: CommandId,
        sessionContext: com.sphereon.di.session.SessionContext
    ): IdkResult<Unit, IdkError> {
        val context = contextBuilder(commandId, sessionContext)
        val decision = policy.isAllowed(context)

        return when {
            decision.isErr -> Err(decision.error)
            decision.value -> Ok(Unit)
            else -> Err(
                authorizationError(
                    commandId = commandId,
                    reason = "Policy denied access",
                    actor = context.actorId
                )
            )
        }
    }
}

/**
 * Converts a PolicyContext to a map suitable for policy engine input.
 * Parses the command ID and extracts resource/action fields.
 *
 * @param ctx The policy context to convert
 * @return Map containing the policy context fields plus parsed command ID parts
 */
fun toPolicyResource(ctx: PolicyContext): Map<String, String?> {
    val parts = parseCommandIdResult(ctx.commandId)
    return if (parts.isOk) {
        mapOf(
            "commandId" to ctx.commandId,
            "module" to parts.value.module,
            "service" to parts.value.service,
            "command" to parts.value.command,
            "actorId" to ctx.actorId,
            "subjectId" to ctx.subjectId,
            "resourceId" to ctx.resourceId,
            "correlationId" to ctx.correlationId
        )
    } else {
        mapOf(
            "commandId" to ctx.commandId,
            "actorId" to ctx.actorId,
            "subjectId" to ctx.subjectId,
            "resourceId" to ctx.resourceId,
            "correlationId" to ctx.correlationId
        )
    }
}
