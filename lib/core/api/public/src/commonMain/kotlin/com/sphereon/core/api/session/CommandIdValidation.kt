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

/**
 * Command ID Format: <module>.<service>.<command>
 *
 * Rules:
 * - Lowercase ASCII, dot-separated
 * - Exactly 3 segments: module.service.command
 * - Each segment starts with a letter and contains only lowercase letters, digits, and hyphens
 * - No environment/tenant-specific data in IDs
 *
 * Examples:
 * - kms.keys.get
 * - did.manager.resolve
 * - party.parties.create
 * - identity.identities.read
 */
private val CommandIdRegex = Regex("^[a-z][a-z0-9-]*(\\.[a-z][a-z0-9-]*){2}$")

/**
 * Validates that a command ID matches the hierarchical format.
 *
 * @param id The command ID to validate
 * @return Ok with the validated ID if valid, Err with IdkError if invalid
 */
fun requireCommandId(id: String): IdkResult<String, IdkError> =
    if (CommandIdRegex.matches(id)) {
        Ok(id)
    } else {
        Err(CommandErrors.invalidCommandId(id))
    }

/**
 * Checks if a command ID is valid without returning an error.
 *
 * @param id The command ID to check
 * @return true if valid, false otherwise
 */
fun isValidCommandId(id: String): Boolean = CommandIdRegex.matches(id)
