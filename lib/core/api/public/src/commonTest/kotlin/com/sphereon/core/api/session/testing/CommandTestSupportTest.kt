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

package com.sphereon.core.api.session.testing

import com.sphereon.core.api.Err
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.session.testing.CommandTestSupport.testContext
import com.sphereon.core.api.session.testing.CommandTestSupport.testExecute
import com.sphereon.core.api.session.testing.CommandTestSupport.testSupports
import com.sphereon.di.session.SessionContext
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CommandTestSupportTest {
    @Test
    fun testContextIsNotNull() {
        assertNotNull(testContext)
    }

    @Test
    fun testErrorMapperIsNotNull() {
        assertNotNull(CommandTestSupport.testErrorMapper)
    }

    @Test
    fun testExecuteWorksWithMockCommand() =
        runTest {
            val command =
                mockCommand<String, Int>("test.support.string.length") { args ->
                    Ok(args.length)
                }

            val result = command.testExecute("hello")

            assertTrue(result.isOk)
            assertEquals(5, result.value)
        }

    @Test
    fun testSupportsWorksWithMockCommand() =
        runTest {
            val command =
                mockCommand<String, Int>(
                    id = "test.support.string.length",
                    supports = { args -> args is String && (args as String).isNotEmpty() },
                ) { args ->
                    Ok(args.length)
                }

            assertTrue(command.testSupports("hello"))
            assertFalse(command.testSupports(""))
            assertFalse(command.testSupports(123))
        }

    @Test
    fun testSupportsUsesContextFreePrimaryPath() =
        runTest {
            val command =
                mockCommand<String, String>(
                    id = "test.support.context.sensitive",
                    supports = { args -> args is String && args.startsWith("value") },
                ) { args ->
                    Ok(args)
                }

            assertTrue(command.testSupports("value"))
            assertTrue(command.supports("value"))
        }

    @Test
    fun testSupportsWithContextUsesStrictContextFreeForwardingByDefault() =
        runTest {
            val seenArgs = mutableListOf<Any>()
            val command =
                mockCommand<String, String>(
                    id = "test.support.context.forwarding.strict",
                    supports = { args ->
                        seenArgs.add(args)
                        args is String && args.startsWith("value")
                    },
                ) { args ->
                    Ok(args)
                }

            assertTrue(command.supports("value"))
            assertEquals(listOf<Any>("value"), seenArgs)
        }
}

class MockCommandTest {
    @Test
    fun mockCommandExecutesSuccessfully() =
        runTest {
            val command =
                mockCommand<String, Int>("test.mock.string.length") { args ->
                    Ok(args.length)
                }

            val result = command.execute("hello")

            assertTrue(result.isOk)
            assertEquals(5, result.value)
        }

    @Test
    fun mockCommandReturnsError() =
        runTest {
            val command =
                mockCommand<String, Int>("test.mock.string.parse") { args ->
                    args
                        .toIntOrNull()
                        ?.let { Ok(it) }
                        ?: Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Invalid"))
                }

            val errResult = command.execute("not-a-number")
            assertTrue(errResult.isErr)
        }

    @Test
    fun mockCommandWithCustomSupports() =
        runTest {
            val command =
                mockCommand<String, String>(
                    id = "test.mock.string.echo",
                    supports = { args -> args is String && (args as String).length > 3 },
                ) { args ->
                    Ok(args)
                }

            assertTrue(command.supports("hello"))
            assertFalse(command.supports("hi"))
            assertTrue(command.supports("hello"))
            assertFalse(command.supports("hi"))
        }

    @Test
    fun mockCommandSupportsWithContextCanBridgeLegacyContextSensitivePredicate() =
        runTest {
            val command =
                mockCommand<String, String>(
                    id = "test.mock.string.context.bridge",
                    supports = { args -> args is String && args.startsWith("h") },
                ) { args ->
                    Ok(args)
                }

            assertTrue(command.supports("hello"))
            assertFalse(command.supports("bye"))
        }

    @Test
    fun mockCommandLegacyBridgeUsesUtilityOwnedContext() =
        runTest {
            val seenArgs = mutableListOf<Any>()
            val command =
                mockCommand<String, String>(
                    id = "test.mock.string.context.utility-owned",
                    supports = { args ->
                        seenArgs.add(args)
                        args is String && args == "hello"
                    },
                ) { args ->
                    Ok(args)
                }

            assertTrue(command.supports("hello"))
            assertFalse(command.supports("bye"))
            assertEquals(listOf<Any>("hello", "bye"), seenArgs)
        }

    @Test
    fun mockCommandDefaultIdIsUsed() {
        val command = mockCommand<String, String> { args -> Ok(args) }
        assertEquals("test.mock.execute", command.id)
    }

    @Test
    fun mockCommandIsAlwaysEnabled() {
        val command = mockCommand<String, String> { args -> Ok(args) }
        assertTrue(command.isEnabled)
    }
}

class SucceedingCommandTest {
    @Test
    fun succeedingCommandAlwaysReturnsOk() =
        runTest {
            val command = succeedingCommand<String, Int>("test.succeed.command", 42)

            val result1 = command.execute("anything")
            val result2 = command.execute("something else")

            assertTrue(result1.isOk)
            assertEquals(42, result1.value)
            assertTrue(result2.isOk)
            assertEquals(42, result2.value)
        }

    @Test
    fun succeedingCommandUsesDefaultId() {
        val command = succeedingCommand<String, String>(result = "value")
        assertEquals("test.mock.succeed", command.id)
    }
}

class FailingCommandTest {
    @Test
    fun failingCommandAlwaysReturnsErr() =
        runTest {
            val error = IdkError.UNKNOWN_ERROR(message = "Test failure")
            val command = failingCommand<String, Int>("test.fail.command", error)

            val result = command.execute("anything")

            assertTrue(result.isErr)
            assertEquals("Test failure", result.error.message.defaultMessage)
        }

    @Test
    fun failingCommandUsesDefaultError() =
        runTest {
            val command = failingCommand<String, Int>()

            val result = command.execute("anything")

            assertTrue(result.isErr)
            assertEquals("Test error", result.error.message.defaultMessage)
        }

    @Test
    fun failingCommandUsesDefaultId() {
        val command = failingCommand<String, String>()
        assertEquals("test.mock.fail", command.id)
    }
}

class TypedMockCommandTest {
    @Test
    fun typedMockCommandSupportsCorrectType() =
        runTest {
            val command =
                typedMockCommand<String, Int>("test.typed.string.length") { args ->
                    Ok(args.length)
                }

            assertTrue(command.supports("hello"))
            assertFalse(command.supports(123))
            assertFalse(command.supports(listOf("a", "b")))
            assertTrue(command.supports("hello"))
            assertFalse(command.supports(123))
            assertFalse(command.supports(listOf("a", "b")))
        }

    @Test
    fun typedMockCommandExecutes() =
        runTest {
            val command =
                typedMockCommand<String, Int> { args ->
                    Ok(args.length)
                }

            val result = command.execute("hello")

            assertTrue(result.isOk)
            assertEquals(5, result.value)
        }
}

class AssertOkOrFailTest {
    @Test
    fun assertOkOrFailReturnsValueForOk() {
        val result = Ok("success")
        val value = result.assertOkOrFail()
        assertEquals("success", value)
    }

    @Test
    fun assertOkOrFailThrowsForErr() {
        val result = Err(IdkError.UNKNOWN_ERROR(message = "test error"))
        assertFailsWith<AssertionError> {
            result.assertOkOrFail()
        }
    }

    @Test
    fun assertOkOrFailIncludesCustomMessage() {
        val result = Err(IdkError.UNKNOWN_ERROR(message = "test error"))
        val exception =
            assertFailsWith<AssertionError> {
                result.assertOkOrFail("Custom message")
            }
        assertTrue(exception.message?.contains("Custom message") == true)
    }
}

class AssertErrOrFailTest {
    @Test
    fun assertErrOrFailReturnsErrorForErr() {
        val error = IdkError.UNKNOWN_ERROR(message = "test error")
        val result = Err(error)
        val returnedError = result.assertErrOrFail()
        assertEquals(error.message.defaultMessage, returnedError.message.defaultMessage)
    }

    @Test
    fun assertErrOrFailThrowsForOk() {
        val result = Ok("success")
        assertFailsWith<AssertionError> {
            result.assertErrOrFail()
        }
    }

    @Test
    fun assertErrOrFailIncludesCustomMessage() {
        val result = Ok("success")
        val exception =
            assertFailsWith<AssertionError> {
                result.assertErrOrFail("Custom message")
            }
        assertTrue(exception.message?.contains("Custom message") == true)
    }
}

class ExpectOkTest {
    @Test
    fun expectOkReturnsOkForSuccess() {
        val original = Ok("value")
        val converted = original.expectOk()
        assertTrue(converted.isOk)
        assertEquals("value", converted.value)
    }

    @Test
    fun expectOkReturnsErrForFailure() {
        val original = Err(IdkError.UNKNOWN_ERROR(message = "original error"))
        val converted = original.expectOk()
        assertTrue(converted.isErr)
        assertTrue(
            converted.error.message.defaultMessage
                .contains("Expected Ok"),
        )
    }

    @Test
    fun expectOkUsesCustomMessage() {
        val original = Err(IdkError.UNKNOWN_ERROR(message = "original error"))
        val converted = original.expectOk("Should have succeeded")
        assertTrue(converted.isErr)
        assertTrue(
            converted.error.message.defaultMessage
                .contains("Should have succeeded"),
        )
    }
}

class ExpectErrTest {
    @Test
    fun expectErrReturnsOkForFailure() {
        val error = IdkError.UNKNOWN_ERROR(message = "test error")
        val original = Err(error)
        val converted = original.expectErr()
        assertTrue(converted.isOk)
        assertEquals(error.code, converted.value.code)
    }

    @Test
    fun expectErrReturnsErrForSuccess() {
        val original = Ok("value")
        val converted = original.expectErr()
        assertTrue(converted.isErr)
        assertTrue(
            converted.error.message.defaultMessage
                .contains("Expected Err"),
        )
    }

    @Test
    fun expectErrUsesCustomMessage() {
        val original = Ok("value")
        val converted = original.expectErr("Should have failed")
        assertTrue(converted.isErr)
        assertTrue(
            converted.error.message.defaultMessage
                .contains("Should have failed"),
        )
    }
}
