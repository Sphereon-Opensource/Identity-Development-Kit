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

/**
 * Registry for command IDs. Used for:
 * - Command ID conformance checks
 * - Policy rule generation/export
 * - Documentation/testing
 */
interface CommandRegistry {
    /**
     * Registers a command ID with its subsystem and optional tags.
     *
     * @param commandId The command ID to register
     * @param subsystem The subsystem this command belongs to
     * @param tags Optional tags for categorization
     * @throws IllegalArgumentException if the command ID format is invalid
     */
    fun register(commandId: String, subsystem: String, tags: Set<String> = emptySet())

    /**
     * Returns all registered command IDs.
     */
    fun listIds(): Set<String>

    /**
     * Finds all command IDs belonging to a subsystem.
     *
     * @param subsystem The subsystem to filter by
     * @return Set of command IDs in the subsystem
     */
    fun findBySubsystem(subsystem: String): Set<String>

    /**
     * Finds all command IDs with a specific tag.
     *
     * @param tag The tag to filter by
     * @return Set of command IDs with the tag
     */
    fun findByTag(tag: String): Set<String>

    /**
     * Finds all command IDs matching a pattern.
     *
     * @param pattern The pattern to match (supports *, **, {a,b})
     * @return Set of matching command IDs
     */
    fun findByPattern(pattern: String): Set<String>

    /**
     * Checks if a command ID is registered.
     *
     * @param commandId The command ID to check
     * @return true if registered, false otherwise
     */
    fun isRegistered(commandId: String): Boolean
}

/**
 * In-memory implementation of CommandRegistry.
 */
class InMemoryCommandRegistry : CommandRegistry {
    private val entries = mutableMapOf<String, RegistryEntry>()

    private data class RegistryEntry(
        val commandId: String,
        val subsystem: String,
        val tags: Set<String>
    )

    override fun register(commandId: String, subsystem: String, tags: Set<String>) {
        require(isValidCommandId(commandId)) { "Invalid command ID: $commandId" }
        entries[commandId] = RegistryEntry(commandId, subsystem, tags)
    }

    override fun listIds(): Set<String> = entries.keys.toSet()

    override fun findBySubsystem(subsystem: String): Set<String> =
        entries.filter { it.value.subsystem == subsystem }.keys

    override fun findByTag(tag: String): Set<String> =
        entries.filter { tag in it.value.tags }.keys

    override fun findByPattern(pattern: String): Set<String> =
        entries.keys.filter { matchesAdvancedPattern(pattern, it) }.toSet()

    override fun isRegistered(commandId: String): Boolean =
        commandId in entries
}

/**
 * Global singleton registry for tests and CI enforcement.
 * In production, prefer injected instances for better testability.
 */
object GlobalCommandRegistry : CommandRegistry by InMemoryCommandRegistry()
