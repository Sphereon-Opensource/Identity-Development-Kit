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
import com.sphereon.di.session.SessionContext

/**
 * Authorization based on pattern matching.
 * Patterns support wildcards: "*" (single segment), "**" (all descendants), "{a,b}" (alternatives)
 *
 * Example patterns:
 * - "openid.oid4vp.verifier.**" - allows all verifier commands
 * - "data.store.{party,resource}.read" - allows read on party or resource
 * - "did.*.did.resolve" - allows resolve across all DID managers
 *
 * @param allowedPatterns Set of patterns that are allowed. Empty set means all commands are allowed.
 */
class PatternCommandAuthorizer(
    private val allowedPatterns: Set<String>
) : CommandAuthorizer {

    override suspend fun isAuthorized(
        commandId: CommandId,
        sessionContext: SessionContext
    ): IdkResult<Unit, IdkError> {
        val isAllowed = allowedPatterns.isEmpty() ||
            allowedPatterns.any { commandId.matches(it) }

        return if (isAllowed) {
            Ok(Unit)
        } else {
            Err(
                authorizationError(
                    commandId = commandId,
                    reason = "Command not in allowed patterns: $allowedPatterns",
                    actor = sessionContext.sessionId
                )
            )
        }
    }

    companion object {
        /**
         * Creates an authorizer that allows all commands.
         */
        fun permissive(): PatternCommandAuthorizer = PatternCommandAuthorizer(emptySet())

        /**
         * Creates an authorizer from a list of allowed patterns.
         */
        fun fromPatterns(vararg patterns: String): PatternCommandAuthorizer =
            PatternCommandAuthorizer(patterns.toSet())
    }
}

/**
 * Composite authorizer - all authorizers must pass for the command to be authorized.
 *
 * @param authorizers List of authorizers to check in order
 */
class CompositeAuthorizer(
    private val authorizers: List<CommandAuthorizer>
) : CommandAuthorizer {

    override suspend fun isAuthorized(
        commandId: CommandId,
        sessionContext: SessionContext
    ): IdkResult<Unit, IdkError> {
        for (authorizer in authorizers) {
            val result = authorizer.isAuthorized(commandId, sessionContext)
            if (result.isErr) return result
        }
        return Ok(Unit)
    }

    companion object {
        /**
         * Creates a composite authorizer from multiple authorizers.
         */
        fun of(vararg authorizers: CommandAuthorizer): CompositeAuthorizer =
            CompositeAuthorizer(authorizers.toList())
    }
}

/**
 * Authorizer that denies specific patterns.
 * Useful for blacklisting certain commands.
 *
 * @param deniedPatterns Set of patterns that are denied
 */
class DenyPatternAuthorizer(
    private val deniedPatterns: Set<String>
) : CommandAuthorizer {

    override suspend fun isAuthorized(
        commandId: CommandId,
        sessionContext: SessionContext
    ): IdkResult<Unit, IdkError> {
        val isDenied = deniedPatterns.any { commandId.matches(it) }

        return if (isDenied) {
            Err(
                authorizationError(
                    commandId = commandId,
                    reason = "Command matches denied pattern",
                    actor = sessionContext.sessionId
                )
            )
        } else {
            Ok(Unit)
        }
    }

    companion object {
        /**
         * Creates an authorizer from a list of denied patterns.
         */
        fun fromPatterns(vararg patterns: String): DenyPatternAuthorizer =
            DenyPatternAuthorizer(patterns.toSet())
    }
}
