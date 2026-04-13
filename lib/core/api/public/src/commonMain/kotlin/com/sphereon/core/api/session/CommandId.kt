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
 *
 */

package com.sphereon.core.api.session

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * Hierarchical command ID value class.
 *
 * Format: <module>.<service>.<command>
 *
 * Examples:
 * - kms.keys.get
 * - did.manager.resolve
 * - party.parties.create
 * - identity.identities.read
 */
@Serializable
@JvmInline
value class CommandId(
    val value: String,
) {
    init {
        require(isValidCommandId(value)) { "Invalid command ID format: $value. Format: module.service.command" }
    }

    /**
     * Returns the segments of the command ID.
     */
    val segments: List<String> get() = value.split(".")

    /**
     * The module segment (first segment).
     * Example: "kms", "did", "party", "identity"
     */
    val module: String get() = segments[0]

    /**
     * The service segment (second segment).
     * Example: "keys", "manager", "parties", "identities"
     */
    val service: String get() = segments[1]

    /**
     * The command segment (third segment).
     * Example: "get", "resolve", "create", "read"
     */
    val command: String get() = segments[2]

    /**
     * Pattern matching with wildcards:
     * - `*` matches any single segment
     * - `**` matches any remaining segments (any depth)
     * - `{a,b}` matches one of multiple segment values
     */
    fun matches(pattern: String): Boolean = matchesAdvancedPattern(pattern, value)

    override fun toString(): String = value

    companion object {
        /**
         * Creates a CommandId from individual components.
         *
         * @param module The module segment (e.g., "kms", "did", "party")
         * @param service The service segment (e.g., "keys", "manager", "parties")
         * @param command The command segment (e.g., "get", "resolve", "create")
         */
        fun of(
            module: String,
            service: String,
            command: String,
        ): CommandId = CommandId("$module.$service.$command")

        /**
         * Attempts to parse a command ID string, returning null if invalid.
         *
         * @param id The command ID string to parse
         * @return CommandId if valid, null otherwise
         */
        fun tryParse(id: String): CommandId? =
            if (isValidCommandId(id)) {
                CommandId(id)
            } else {
                null
            }

        /**
         * Attempts to parse a command ID string, returning an IdkResult.
         *
         * @param id The command ID string to parse
         * @return Ok with CommandId if valid, Err with IdkError if invalid
         */
        fun tryParseResult(id: String): IdkResult<CommandId, IdkError> {
            val valid = requireCommandId(id)
            if (valid.isErr) {
                return Err(valid.error)
            }
            return Ok(CommandId(id))
        }
    }
}

/**
 * Convenience helper to extract the command from a command ID string.
 * Returns the last segment of the ID.
 */
fun extractCommand(commandId: String): String = commandId.substringAfterLast('.')
