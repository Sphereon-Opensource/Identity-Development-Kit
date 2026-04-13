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
 */

package com.sphereon.core.api.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InMemoryCommandRegistryTest {

    // Command IDs must have exactly 3 segments: module.service.command

    @Test
    fun registerAddsCommandId() {
        val registry = InMemoryCommandRegistry()
        registry.register("kms.keys.create", "kms")
        assertTrue(registry.isRegistered("kms.keys.create"))
    }

    @Test
    fun registerWithTagsAddsCommandWithTags() {
        val registry = InMemoryCommandRegistry()
        registry.register("kms.keys.create", "kms", setOf("write", "admin"))
        assertTrue(registry.isRegistered("kms.keys.create"))
    }

    @Test
    fun registerThrowsForInvalidCommandId() {
        val registry = InMemoryCommandRegistry()
        assertFailsWith<IllegalArgumentException> {
            registry.register("invalid", "kms")
        }
    }

    @Test
    fun registerThrowsForFourSegmentId() {
        val registry = InMemoryCommandRegistry()
        assertFailsWith<IllegalArgumentException> {
            registry.register("kms.manager.keys.create", "kms")
        }
    }

    @Test
    fun listIdsReturnsAllRegisteredIds() {
        val registry = InMemoryCommandRegistry()
        registry.register("kms.keys.create", "kms")
        registry.register("kms.keys.delete", "kms")
        registry.register("wallet.credentials.list", "wallet")

        val ids = registry.listIds()

        assertEquals(3, ids.size)
        assertTrue(ids.contains("kms.keys.create"))
        assertTrue(ids.contains("kms.keys.delete"))
        assertTrue(ids.contains("wallet.credentials.list"))
    }

    @Test
    fun listIdsReturnsEmptySetWhenEmpty() {
        val registry = InMemoryCommandRegistry()
        assertTrue(registry.listIds().isEmpty())
    }

    @Test
    fun findBySubsystemReturnsMatchingCommands() {
        val registry = InMemoryCommandRegistry()
        registry.register("kms.keys.create", "kms")
        registry.register("kms.keys.delete", "kms")
        registry.register("wallet.credentials.list", "wallet")

        val kmsCommands = registry.findBySubsystem("kms")

        assertEquals(2, kmsCommands.size)
        assertTrue(kmsCommands.contains("kms.keys.create"))
        assertTrue(kmsCommands.contains("kms.keys.delete"))
    }

    @Test
    fun findBySubsystemReturnsEmptyForNoMatch() {
        val registry = InMemoryCommandRegistry()
        registry.register("kms.keys.create", "kms")

        val walletCommands = registry.findBySubsystem("wallet")

        assertTrue(walletCommands.isEmpty())
    }

    @Test
    fun findByTagReturnsMatchingCommands() {
        val registry = InMemoryCommandRegistry()
        registry.register("kms.keys.create", "kms", setOf("write"))
        registry.register("kms.keys.delete", "kms", setOf("write", "admin"))
        registry.register("kms.keys.list", "kms", setOf("read"))

        val writeCommands = registry.findByTag("write")

        assertEquals(2, writeCommands.size)
        assertTrue(writeCommands.contains("kms.keys.create"))
        assertTrue(writeCommands.contains("kms.keys.delete"))
    }

    @Test
    fun findByTagReturnsEmptyForNoMatch() {
        val registry = InMemoryCommandRegistry()
        registry.register("kms.keys.list", "kms", setOf("read"))

        val writeCommands = registry.findByTag("write")

        assertTrue(writeCommands.isEmpty())
    }

    @Test
    fun findByPatternMatchesWildcard() {
        val registry = InMemoryCommandRegistry()
        registry.register("kms.keys.create", "kms")
        registry.register("kms.keys.delete", "kms")
        registry.register("kms.providers.list", "kms")

        val keysCommands = registry.findByPattern("kms.keys.*")

        assertEquals(2, keysCommands.size)
        assertTrue(keysCommands.contains("kms.keys.create"))
        assertTrue(keysCommands.contains("kms.keys.delete"))
    }

    @Test
    fun findByPatternMatchesDoubleWildcard() {
        val registry = InMemoryCommandRegistry()
        registry.register("kms.keys.create", "kms")
        registry.register("kms.keys.list", "kms")

        val kmsCommands = registry.findByPattern("kms.**")

        assertEquals(2, kmsCommands.size)
    }

    @Test
    fun isRegisteredReturnsTrueForRegisteredId() {
        val registry = InMemoryCommandRegistry()
        registry.register("kms.keys.create", "kms")
        assertTrue(registry.isRegistered("kms.keys.create"))
    }

    @Test
    fun isRegisteredReturnsFalseForUnregisteredId() {
        val registry = InMemoryCommandRegistry()
        assertFalse(registry.isRegistered("kms.keys.create"))
    }
}

class GlobalCommandRegistryTest {

    @Test
    fun globalRegistryExists() {
        // GlobalCommandRegistry should exist as a singleton
        val registry: CommandRegistry = GlobalCommandRegistry
        assertTrue(registry is CommandRegistry)
    }
}
