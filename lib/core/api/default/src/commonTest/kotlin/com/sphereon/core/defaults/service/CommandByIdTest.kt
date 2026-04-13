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

import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.core.api.service.SessionScopedCommandRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CommandByIdTest {
    private val registry =
        StubRegistry(
            mapOf(
                "kms.keys.get" to StubCommand("kms.keys.get"),
                "kms.keys.list" to StubCommand("kms.keys.list"),
                "party.manager.create" to StubCommand("party.manager.create"),
            ),
        )

    private val commandById = CommandById(registry)

    @Test
    fun getReturnsCommandFromRegistry() {
        val cmd = commandById["kms.keys.get"]
        assertEquals("kms.keys.get", cmd.commandId)
    }

    @Test
    fun getThrowsForUnknownCommandId() {
        assertFailsWith<IllegalArgumentException> {
            commandById["unknown.command.id"]
        }
    }

    @Test
    fun getOrNullReturnsCommandWhenFound() {
        val cmd = commandById.getOrNull("kms.keys.get")
        assertNotNull(cmd)
        assertEquals("kms.keys.get", cmd.commandId)
    }

    @Test
    fun getOrNullReturnsNullWhenNotFound() {
        assertNull(commandById.getOrNull("unknown.command.id"))
    }

    @Test
    fun typedReturnsCastCommand() {
        val cmd = commandById.typed<Any, Any>("kms.keys.get")
        assertEquals("kms.keys.get", cmd.commandId)
    }

    @Test
    fun hasReturnsTrueForExistingCommand() {
        assertTrue(commandById.has("kms.keys.get"))
    }

    @Test
    fun hasReturnsFalseForUnknownCommand() {
        assertFalse(commandById.has("unknown.command.id"))
    }

    @Test
    fun listIdsReturnsAllCommandIds() {
        val ids = commandById.listIds()
        assertEquals(3, ids.size)
        assertTrue(ids.contains("kms.keys.get"))
        assertTrue(ids.contains("kms.keys.list"))
        assertTrue(ids.contains("party.manager.create"))
    }
}
