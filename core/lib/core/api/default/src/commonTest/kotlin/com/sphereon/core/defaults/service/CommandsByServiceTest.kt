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

class CommandsByServiceTest {
    private val registry =
        StubRegistry(
            mapOf(
                "kms.keys.get" to StubCommand("kms.keys.get"),
                "kms.keys.list" to StubCommand("kms.keys.list"),
                "kms.signatures.create" to StubCommand("kms.signatures.create"),
                "party.manager.create" to StubCommand("party.manager.create"),
            ),
        )

    private val byService = CommandsByService(registry)

    @Test
    fun getReturnsAllCommandsInServiceGroup() {
        val keyCommands = byService["kms.keys"]
        assertEquals(2, keyCommands.size)
        assertTrue(keyCommands.containsKey("kms.keys.get"))
        assertTrue(keyCommands.containsKey("kms.keys.list"))
    }

    @Test
    fun getDoesNotIncludeCommandsFromOtherServices() {
        val keyCommands = byService["kms.keys"]
        assertFalse(keyCommands.containsKey("kms.signatures.create"))
    }

    @Test
    fun getReturnsEmptyMapForUnknownService() {
        val commands = byService["kms.unknown"]
        assertTrue(commands.isEmpty())
    }

    @Test
    fun listServicesReturnsDistinctSortedServicePrefixes() {
        val services = byService.listServices()
        assertEquals(listOf("kms.keys", "kms.signatures", "party.manager"), services)
    }

    @Test
    fun hasReturnsTrueForServiceWithCommands() {
        assertTrue(byService.has("kms.keys"))
        assertTrue(byService.has("kms.signatures"))
        assertTrue(byService.has("party.manager"))
    }

    @Test
    fun hasReturnsFalseForUnknownService() {
        assertFalse(byService.has("kms.unknown"))
        assertFalse(byService.has("unknown.service"))
    }
}
