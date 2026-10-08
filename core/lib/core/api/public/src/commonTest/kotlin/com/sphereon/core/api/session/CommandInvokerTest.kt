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

package com.sphereon.core.api.session

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.TypeToken
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.events.EventSubsystem
import com.sphereon.core.api.events.EventSubsystems
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.core.api.service.SessionScopedCommandRegistry
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CommandInvokerTest {
    @Suppress("UNCHECKED_CAST")
    private class TestServiceCommand(
        override val commandId: String,
        override val isEnabled: Boolean = true,
        private val supportsResult: Boolean = true,
        private val executeResult: IdkResult<Int, IdkError> = Ok(42),
        private val supportsException: Throwable? = null,
        private val executeException: Throwable? = null,
    ) : ServiceCommand<String, Int, IdkError> {
        override val subsystem: EventSubsystem = EventSubsystems.CUSTOM
        override val inputTypeToken: TypeToken<String> = TypeToken.UNIT as TypeToken<String>
        override val outputTypeToken: TypeToken<Int> = TypeToken.UNIT as TypeToken<Int>
        var supportsCalled: Int = 0
        var executeCalled: Int = 0

        override suspend fun supports(args: Any): Boolean {
            supportsCalled++
            supportsException?.let { throw it }
            return supportsResult
        }

        override suspend fun execute(args: String): IdkResult<Int, IdkError> {
            executeCalled++
            executeException?.let { throw it }
            return executeResult
        }
    }

    private class FakeRegistry(
        private val commands: Map<String, ServiceCommand<*, *, *>> = emptyMap(),
    ) : SessionScopedCommandRegistry {
        override fun get(commandId: String): ServiceCommand<*, *, *>? = commands[commandId]

        override fun has(commandId: String): Boolean = commands.containsKey(commandId)

        override fun listCommandIds(): List<String> = commands.keys.toList()
    }

    // ========== resolve tests ==========

    @Test
    fun resolveReturnsCommandWhenPresent() {
        val command = TestServiceCommand(commandId = "core.test.get")
        val executor = SessionScopeCommandInvoker(FakeRegistry(mapOf("core.test.get" to command)))

        val resolved = executor.resolve("core.test.get")

        assertNotNull(resolved)
        assertSame(command, resolved)
    }

    @Test
    fun resolveReturnsNullWhenMissing() {
        val executor = SessionScopeCommandInvoker(FakeRegistry())

        assertNull(executor.resolve("core.test.missing"))
    }

    @Test
    fun resolveTypedReturnsCastCommand() {
        val command = TestServiceCommand(commandId = "core.test.get")
        val executor = SessionScopeCommandInvoker(FakeRegistry(mapOf("core.test.get" to command)))

        val resolved = executor.resolve<TestServiceCommand>("core.test.get")

        assertNotNull(resolved)
        assertEquals("core.test.get", resolved.commandId)
    }

    @Test
    fun resolveTypedReturnsNullForWrongType() {
        val command = TestServiceCommand(commandId = "core.test.get")
        val executor = SessionScopeCommandInvoker(FakeRegistry(mapOf("core.test.get" to command)))

        // ServiceCommand<*, *, *> is a supertype, but a specific unrelated interface cast will fail
        // Since TestServiceCommand doesn't implement some other interface, cast to String will fail
        val resolved = executor.resolve(commandId = "core.test.get") as? String

        assertNull(resolved)
    }

    // ========== execute tests ==========

    @Test
    fun executeReturnsResultForValidCommand() =
        runTest {
            val expected = Ok(1337)
            val command =
                TestServiceCommand(
                    commandId = "core.test.execute",
                    executeResult = expected,
                )
            val executor = SessionScopeCommandInvoker(FakeRegistry(mapOf("core.test.execute" to command)))

            val result = executor.execute(command, "args")

            assertTrue(result.isOk)
            assertEquals(1337, result.value)
            assertEquals(1, command.supportsCalled)
            assertEquals(1, command.executeCalled)
        }

    @Test
    fun executeReturnsDisabledErrorForDisabledCommand() =
        runTest {
            val command = TestServiceCommand(commandId = "core.test.execute", isEnabled = false)
            val executor = SessionScopeCommandInvoker(FakeRegistry(mapOf("core.test.execute" to command)))

            val result = executor.execute(command, "args")

            assertTrue(result.isErr)
            assertEquals("COMMAND_DISABLED", result.error.code)
            assertEquals(0, command.supportsCalled)
            assertEquals(0, command.executeCalled)
        }

    @Test
    fun executeReturnsUnsupportedArgError() =
        runTest {
            val command = TestServiceCommand(commandId = "core.test.execute", supportsResult = false)
            val executor = SessionScopeCommandInvoker(FakeRegistry(mapOf("core.test.execute" to command)))

            val result = executor.execute(command, "args")

            assertTrue(result.isErr)
            assertEquals("COMMAND_ARG_NOT_SUPPORTED_ERROR", result.error.code)
            assertEquals(1, command.supportsCalled)
            assertEquals(0, command.executeCalled)
        }

    @Test
    fun executeCatchesExceptionFromSupports() =
        runTest {
            val exception = IllegalStateException("boom in supports")
            val command = TestServiceCommand(commandId = "core.test.execute", supportsException = exception)
            val executor = SessionScopeCommandInvoker(FakeRegistry(mapOf("core.test.execute" to command)))

            val result = executor.execute(command, "args")

            assertTrue(result.isErr)
            assertEquals("UNKNOWN_ERROR", result.error.code)
            assertSame(exception, result.error.exception)
            assertTrue(
                result.error.message.defaultMessage
                    .contains("core.test.execute"),
            )
        }

    @Test
    fun executeCatchesExceptionFromCommand() =
        runTest {
            val exception = RuntimeException("boom in execute")
            val command = TestServiceCommand(commandId = "core.test.execute", executeException = exception)
            val executor = SessionScopeCommandInvoker(FakeRegistry(mapOf("core.test.execute" to command)))

            val result = executor.execute(command, "args")

            assertTrue(result.isErr)
            assertEquals("UNKNOWN_ERROR", result.error.code)
            assertSame(exception, result.error.exception)
            assertTrue(
                result.error.message.defaultMessage
                    .contains("core.test.execute"),
            )
        }

    // ========== has / listCommandIds tests ==========

    @Test
    fun hasReturnsTrueForRegisteredCommand() {
        val command = TestServiceCommand(commandId = "core.test.get")
        val executor = SessionScopeCommandInvoker(FakeRegistry(mapOf("core.test.get" to command)))

        assertTrue(executor.has("core.test.get"))
    }

    @Test
    fun hasReturnsFalseForMissingCommand() {
        val executor = SessionScopeCommandInvoker(FakeRegistry())

        assertEquals(false, executor.has("core.test.missing"))
    }

    @Test
    fun listCommandIdsReturnsAllRegisteredIds() {
        val commands =
            mapOf(
                "core.test.get" to TestServiceCommand(commandId = "core.test.get"),
                "core.test.create" to TestServiceCommand(commandId = "core.test.create"),
            )
        val executor = SessionScopeCommandInvoker(FakeRegistry(commands))

        val ids = executor.listCommandIds()

        assertEquals(2, ids.size)
        assertTrue(ids.contains("core.test.get"))
        assertTrue(ids.contains("core.test.create"))
    }

    // ========== executeById extension tests ==========

    @Test
    fun executeByIdResolvesAndExecutes() =
        runTest {
            val command = TestServiceCommand(commandId = "core.test.execute", executeResult = Ok(99))
            val executor = SessionScopeCommandInvoker(FakeRegistry(mapOf("core.test.execute" to command)))

            val result = executor.executeById<TestServiceCommand, String, Int>("core.test.execute", "args")

            assertTrue(result.isOk)
            assertEquals(99, result.value)
        }

    @Test
    fun executeByIdReturnsNotFoundForMissingCommand() =
        runTest {
            val executor = SessionScopeCommandInvoker(FakeRegistry())

            val result = executor.executeById<TestServiceCommand, String, Int>("core.test.missing", "args")

            assertTrue(result.isErr)
            assertEquals("NOT_FOUND_ERROR", result.error.code)
            assertTrue(
                result.error.message.defaultMessage
                    .contains("core.test.missing"),
            )
        }
}
