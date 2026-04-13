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
 * Parsed components of a command ID.
 *
 * @property module The module segment (e.g., "kms", "did", "party")
 * @property service The service segment (e.g., "keys", "manager", "parties")
 * @property command The command segment (e.g., "get", "resolve", "create")
 */
@Serializable
data class CommandIdParts(
    val module: String,
    val service: String,
    val command: String
) {
    /**
     * Reconstructs the command ID string from its parts.
     */
    fun toCommandIdString(): String = "$module.$service.$command"

    /**
     * Converts to a CommandId value class.
     */
    fun toCommandId(): CommandId = CommandId(toCommandIdString())
}

/**
 * Parses a command ID string into its component parts.
 *
 * @param commandId The command ID string to parse
 * @return CommandIdParts if valid, null otherwise
 */
fun parseCommandId(commandId: String): CommandIdParts? {
    val parts = commandId.split('.')
    if (parts.size != 3) return null

    return CommandIdParts(
        module = parts[0],
        service = parts[1],
        command = parts[2]
    )
}

/**
 * Parses a command ID string into its component parts, returning an IdkResult.
 *
 * @param commandId The command ID string to parse
 * @return Ok with CommandIdParts if valid, Err with IdkError if invalid
 */
fun parseCommandIdResult(commandId: String): IdkResult<CommandIdParts, IdkError> {
    val valid = requireCommandId(commandId)
    if (valid.isErr) return Err(valid.error)

    val parts = commandId.split('.')
    if (parts.size != 3) {
        return Err(CommandErrors.invalidCommandId(commandId))
    }

    return Ok(
        CommandIdParts(
            module = parts[0],
            service = parts[1],
            command = parts[2]
        )
    )
}

/**
 * Convenience helper to extract the command from a command ID string.
 * Returns the last segment of the ID.
 */
fun extractCommand(commandId: String): String = commandId.substringAfterLast('.')
