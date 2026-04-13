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

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PluginConfigPermissiveTest {

    @Test
    fun permissiveAllowsAllPlugins() {
        val config = PluginConfig.Permissive
        assertTrue(config.isPluginAllowed("any-plugin-id"))
        assertTrue(config.isPluginAllowed("another-plugin"))
    }

    @Test
    fun permissiveAllowsAllCommands() {
        val config = PluginConfig.Permissive
        assertTrue(config.isCommandAllowed("kms.keys.get"))
        assertTrue(config.isCommandAllowed("did.manager.resolve"))
    }

    @Test
    fun permissiveAllowsCommandId() {
        val config = PluginConfig.Permissive
        val commandId = CommandId.tryParse("kms.keys.get")!!
        assertTrue(config.isCommandAllowed(commandId))
    }

    @Test
    fun permissiveHasEmptyAllowedPluginIds() {
        val config = PluginConfig.Permissive
        assertTrue(config.allowedPluginIds.isEmpty())
    }

    @Test
    fun permissiveHasEmptyAllowedCommandPatterns() {
        val config = PluginConfig.Permissive
        assertTrue(config.allowedCommandPatterns.isEmpty())
    }
}

class PluginConfigAllowPluginsTest {

    @Test
    fun allowPluginsAllowsSpecifiedPlugin() {
        val config = PluginConfig.allowPlugins("plugin-a", "plugin-b")
        assertTrue(config.isPluginAllowed("plugin-a"))
        assertTrue(config.isPluginAllowed("plugin-b"))
    }

    @Test
    fun allowPluginsDeniesUnspecifiedPlugin() {
        val config = PluginConfig.allowPlugins("plugin-a", "plugin-b")
        assertFalse(config.isPluginAllowed("plugin-c"))
    }

    @Test
    fun allowPluginsAllowsAllCommands() {
        val config = PluginConfig.allowPlugins("plugin-a")
        assertTrue(config.isCommandAllowed("kms.keys.get"))
    }

    @Test
    fun allowPluginsHasEmptyCommandPatterns() {
        val config = PluginConfig.allowPlugins("plugin-a")
        assertTrue(config.allowedCommandPatterns.isEmpty())
    }
}

class PluginConfigAllowCommandsTest {

    @Test
    fun allowCommandsAllowsMatchingCommand() {
        val config = PluginConfig.allowCommands("kms.**")
        assertTrue(config.isCommandAllowed("kms.keys.get"))
    }

    @Test
    fun allowCommandsDeniesNonMatchingCommand() {
        val config = PluginConfig.allowCommands("kms.**")
        assertFalse(config.isCommandAllowed("did.manager.resolve"))
    }

    @Test
    fun allowCommandsAllowsAllPlugins() {
        val config = PluginConfig.allowCommands("kms.**")
        assertTrue(config.isPluginAllowed("any-plugin"))
    }

    @Test
    fun allowCommandsHasEmptyPluginIds() {
        val config = PluginConfig.allowCommands("kms.**")
        assertTrue(config.allowedPluginIds.isEmpty())
    }

    @Test
    fun allowCommandsSupportsExactMatch() {
        val config = PluginConfig.allowCommands("kms.keys.get")
        assertTrue(config.isCommandAllowed("kms.keys.get"))
        assertFalse(config.isCommandAllowed("kms.keys.delete"))
    }
}

class PluginConfigOfTest {

    @Test
    fun ofWithBothRestrictions() {
        val config = PluginConfig.of(
            pluginIds = setOf("plugin-a"),
            commandPatterns = setOf("kms.**")
        )
        assertTrue(config.isPluginAllowed("plugin-a"))
        assertFalse(config.isPluginAllowed("plugin-b"))
        assertTrue(config.isCommandAllowed("kms.keys.get"))
        assertFalse(config.isCommandAllowed("did.manager.resolve"))
    }

    @Test
    fun ofWithEmptyPluginIdsAllowsAllPlugins() {
        val config = PluginConfig.of(
            pluginIds = emptySet(),
            commandPatterns = setOf("kms.**")
        )
        assertTrue(config.isPluginAllowed("any-plugin"))
    }

    @Test
    fun ofWithEmptyCommandPatternsAllowsAllCommands() {
        val config = PluginConfig.of(
            pluginIds = setOf("plugin-a"),
            commandPatterns = emptySet()
        )
        assertTrue(config.isCommandAllowed("any.command.here"))
    }
}

class PluginConfigIsCommandAllowedStringTest {

    @Test
    fun isCommandAllowedStringAllowsValidMatchingCommand() {
        val config = PluginConfig.allowCommands("kms.**")
        assertTrue(config.isCommandAllowed("kms.keys.get"))
    }

    @Test
    fun isCommandAllowedStringDeniesValidNonMatchingCommand() {
        val config = PluginConfig.allowCommands("kms.**")
        assertFalse(config.isCommandAllowed("did.manager.resolve"))
    }

    @Test
    fun isCommandAllowedStringAllowsInvalidCommandIdForBackwardsCompatibility() {
        val config = PluginConfig.allowCommands("kms.**")
        // Invalid command IDs return true for backwards compatibility
        assertTrue(config.isCommandAllowed("invalid"))
    }
}

class PluginConfigIsPluginAllowedTest {

    @Test
    fun isPluginAllowedWithEmptySetAllowsAll() {
        val config = object : PluginConfig {
            override val allowedPluginIds: Set<String> = emptySet()
            override val allowedCommandPatterns: Set<String> = emptySet()
        }
        assertTrue(config.isPluginAllowed("any-plugin"))
    }

    @Test
    fun isPluginAllowedWithSetAllowsOnlyListed() {
        val config = object : PluginConfig {
            override val allowedPluginIds: Set<String> = setOf("allowed-plugin")
            override val allowedCommandPatterns: Set<String> = emptySet()
        }
        assertTrue(config.isPluginAllowed("allowed-plugin"))
        assertFalse(config.isPluginAllowed("not-allowed-plugin"))
    }
}

class PluginConfigIsCommandAllowedCommandIdTest {

    @Test
    fun isCommandAllowedWithEmptyPatternsAllowsAll() {
        val config = object : PluginConfig {
            override val allowedPluginIds: Set<String> = emptySet()
            override val allowedCommandPatterns: Set<String> = emptySet()
        }
        val commandId = CommandId.tryParse("any.command.here")!!
        assertTrue(config.isCommandAllowed(commandId))
    }

    @Test
    fun isCommandAllowedMatchesPattern() {
        val config = object : PluginConfig {
            override val allowedPluginIds: Set<String> = emptySet()
            override val allowedCommandPatterns: Set<String> = setOf("kms.*.*")
        }
        val commandId = CommandId.tryParse("kms.keys.get")!!
        assertTrue(config.isCommandAllowed(commandId))
    }

    @Test
    fun isCommandAllowedDoesNotMatchPattern() {
        val config = object : PluginConfig {
            override val allowedPluginIds: Set<String> = emptySet()
            override val allowedCommandPatterns: Set<String> = setOf("kms.*.*")
        }
        val commandId = CommandId.tryParse("did.manager.resolve")!!
        assertFalse(config.isCommandAllowed(commandId))
    }
}
