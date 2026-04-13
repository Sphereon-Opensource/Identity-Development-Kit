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
 */

package com.sphereon.core.defaults.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommandsByModuleTest {
    private val registry =
        StubRegistry(
            mapOf(
                "kms.keys.get" to StubCommand("kms.keys.get"),
                "kms.keys.list" to StubCommand("kms.keys.list"),
                "kms.signatures.create" to StubCommand("kms.signatures.create"),
                "party.manager.create" to StubCommand("party.manager.create"),
                "party.manager.delete" to StubCommand("party.manager.delete"),
            ),
        )

    private val byModule = CommandsByModule(registry)

    @Test
    fun getReturnsAllCommandsInModule() {
        val kmsCommands = byModule["kms"]
        assertEquals(3, kmsCommands.size)
        assertTrue(kmsCommands.containsKey("kms.keys.get"))
        assertTrue(kmsCommands.containsKey("kms.keys.list"))
        assertTrue(kmsCommands.containsKey("kms.signatures.create"))
    }

    @Test
    fun getReturnsEmptyMapForUnknownModule() {
        val commands = byModule["unknown"]
        assertTrue(commands.isEmpty())
    }

    @Test
    fun listModulesReturnsDistinctSortedModuleNames() {
        val modules = byModule.listModules()
        assertEquals(listOf("kms", "party"), modules)
    }

    @Test
    fun hasReturnsTrueForModuleWithCommands() {
        assertTrue(byModule.has("kms"))
        assertTrue(byModule.has("party"))
    }

    @Test
    fun hasReturnsFalseForUnknownModule() {
        assertFalse(byModule.has("unknown"))
    }
}
