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

/**
 * Configuration for plugin and command access control.
 * This interface replaces the mutable global RuntimePluginConfig object
 * with a DI-scoped, immutable configuration.
 *
 * Empty sets mean "allow all" for backwards compatibility.
 */
interface PluginConfig {
    /**
     * Set of allowed plugin IDs.
     * Empty set means all plugins are allowed.
     */
    val allowedPluginIds: Set<String>

    /**
     * Set of allowed command patterns.
     * Supports wildcards: "*" (single segment), "**" (all descendants), "{a,b}" (alternatives).
     * Empty set means all commands are allowed.
     */
    val allowedCommandPatterns: Set<String>

    /**
     * Checks if a plugin is allowed.
     *
     * @param pluginId The plugin ID to check
     * @return true if allowed, false if denied
     */
    fun isPluginAllowed(pluginId: String): Boolean = allowedPluginIds.isEmpty() || pluginId in allowedPluginIds

    /**
     * Checks if a command is allowed based on its ID.
     *
     * @param commandId The command ID to check
     * @return true if allowed, false if denied
     */
    fun isCommandAllowed(commandId: CommandId): Boolean = allowedCommandPatterns.isEmpty() || allowedCommandPatterns.any { commandId.matches(it) }

    /**
     * Checks if a command is allowed based on its ID string.
     * Falls back to true if the command ID is invalid (for backwards compatibility).
     *
     * @param commandId The command ID string to check
     * @return true if allowed or invalid ID, false if denied
     */
    fun isCommandAllowed(commandId: String): Boolean {
        val parsed = CommandId.tryParse(commandId) ?: return true
        return isCommandAllowed(parsed)
    }

    companion object {
        /**
         * Permissive configuration that allows all plugins and commands.
         * This is the default for backwards compatibility.
         */
        val Permissive: PluginConfig =
            object : PluginConfig {
                override val allowedPluginIds: Set<String> = emptySet()
                override val allowedCommandPatterns: Set<String> = emptySet()
            }

        /**
         * Creates a PluginConfig that only allows specific plugins.
         */
        fun allowPlugins(vararg pluginIds: String): PluginConfig =
            object : PluginConfig {
                override val allowedPluginIds: Set<String> = pluginIds.toSet()
                override val allowedCommandPatterns: Set<String> = emptySet()
            }

        /**
         * Creates a PluginConfig that only allows commands matching patterns.
         */
        fun allowCommands(vararg patterns: String): PluginConfig =
            object : PluginConfig {
                override val allowedPluginIds: Set<String> = emptySet()
                override val allowedCommandPatterns: Set<String> = patterns.toSet()
            }

        /**
         * Creates a PluginConfig with both plugin and command restrictions.
         */
        fun of(
            pluginIds: Set<String>,
            commandPatterns: Set<String>,
        ): PluginConfig =
            object : PluginConfig {
                override val allowedPluginIds: Set<String> = pluginIds
                override val allowedCommandPatterns: Set<String> = commandPatterns
            }
    }
}
