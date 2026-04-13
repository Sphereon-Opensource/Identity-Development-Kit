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
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.events.EventSubsystems
import com.sphereon.core.api.session.testing.CommandTestSupport.testContext
import com.sphereon.core.api.session.testing.CommandTestSupport.testExecute
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SimpleCommandTest {

    @Test
    fun simpleCommandExecutesSuccessfully() = runTest {
        val command = object : SimpleCommand<String, Int> {
            override val id = "test.string.length"
            override suspend fun execute(args: String) = Ok(args.length)
        }

        val result = command.execute("hello")

        assertTrue(result.isOk)
        assertEquals(5, result.value)
    }

    @Test
    fun simpleCommandReturnsError() = runTest {
        val command = object : SimpleCommand<String, Int> {
            override val id = "test.string.parse"
            override suspend fun execute(args: String) =
                args.toIntOrNull()
                    ?.let { Ok(it) }
                    ?: Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Invalid integer"))
        }

        val result = command.execute("not-a-number")

        assertTrue(result.isErr)
        assertTrue(result.error.message.defaultMessage.contains("Invalid integer"))
    }

    @Test
    fun simpleCommandSupportsDefaultsToTrue() {
        val command = object : SimpleCommand<String, String> {
            override val id = "test.string.echo"
            override suspend fun execute(args: String) = Ok(args)
        }

        assertTrue(command.supports("anything"))
        assertTrue(command.supports(123))  // Even wrong type returns true by default
    }

    @Test
    fun simpleCommandSupportsCanBeOverridden() {
        val command = object : SimpleCommand<String, String> {
            override val id = "test.string.echo"
            override suspend fun execute(args: String) = Ok(args)
            override fun supports(args: Any) = args is String && args.isNotBlank()
        }

        assertTrue(command.supports("hello"))
        assertFalse(command.supports(""))
        assertFalse(command.supports("   "))
        assertFalse(command.supports(123))
    }
}

class SimpleCommandAsCommandTest {

    @Test
    fun asCommandConvertsToFullCommand() = runTest {
        val simple = object : SimpleCommand<String, Int> {
            override val id = "test.string.length"
            override suspend fun execute(args: String) = Ok(args.length)
        }

        val fullCommand = simple.asCommand()

        assertEquals("test.string.length", fullCommand.id)
        assertTrue(fullCommand.isEnabled)
        assertEquals(EventSubsystems.CUSTOM, fullCommand.subsystem)
    }

    @Test
    fun asCommandPreservesExecution() = runTest {
        val simple = object : SimpleCommand<String, Int> {
            override val id = "test.string.length"
            override suspend fun execute(args: String) = Ok(args.length)
        }

        val fullCommand = simple.asCommand()
        val result = fullCommand.testExecute("hello")

        assertTrue(result.isOk)
        assertEquals(5, result.value)
    }

    @Test
    fun asCommandPreservesSupports() = runTest {
        val simple = object : SimpleCommand<String, String> {
            override val id = "test.string.echo"
            override suspend fun execute(args: String) = Ok(args)
            override fun supports(args: Any) = args is String && args.length > 3
        }

        val fullCommand = simple.asCommand()

        assertTrue(fullCommand.supports("hello"))
        assertFalse(fullCommand.supports("hi"))
        assertTrue(fullCommand.supports("hello"))
        assertFalse(fullCommand.supports("hi"))
    }

    @Test
    fun asCommandWithCustomSubsystem() = runTest {
        val simple = object : SimpleCommand<String, String> {
            override val id = "test.string.echo"
            override suspend fun execute(args: String) = Ok(args)
        }

        val fullCommand = simple.asCommand(subsystem = EventSubsystems.CRYPTO)

        assertEquals(EventSubsystems.CRYPTO, fullCommand.subsystem)
    }
}

class SimpleCommandFunctionTest {

    @Test
    fun simpleCommandFunctionCreatesCommand() = runTest {
        val command = simpleCommand<String, Int>("test.func.string.length") { str ->
            Ok(str.length)
        }

        val result = command.execute("hello")

        assertTrue(result.isOk)
        assertEquals(5, result.value)
    }

    @Test
    fun simpleCommandFunctionWithCustomSupports() = runTest {
        val command = simpleCommand<String, Int>(
            id = "test.func.string.length",
            supports = { args -> args is String && args.isNotEmpty() }
        ) { str ->
            Ok(str.length)
        }

        assertTrue(command.supports("hello"))
        assertFalse(command.supports(""))
        assertFalse(command.supports(123))
    }

    @Test
    fun simpleCommandFunctionReturnsError() = runTest {
        val command = simpleCommand<Int, Int>("test.func.math.divide") { n ->
            if (n == 0) {
                Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Cannot use zero"))
            } else {
                Ok(100 / n)
            }
        }

        val okResult = command.execute(10)
        assertTrue(okResult.isOk)
        assertEquals(10, okResult.value)

        val errResult = command.execute(0)
        assertTrue(errResult.isErr)
    }
}
