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

import com.sphereon.core.api.Err
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.events.EventSubsystems
import com.sphereon.core.api.session.testing.CommandTestSupport.testContext
import com.sphereon.core.api.session.testing.CommandTestSupport.testExecute
import com.sphereon.di.session.SessionContext
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CommandDslTest {
    @Test
    fun commandDslCreatesBasicCommand() =
        runTest {
            val cmd =
                command<String, Int>("test.dsl.string.length") {
                    execute { str -> Ok(str.length) }
                }

            assertEquals("test.dsl.string.length", cmd.id)
            assertTrue(cmd.isEnabled)
            assertEquals(EventSubsystems.CUSTOM, cmd.subsystem)

            val result = cmd.testExecute("hello")
            assertTrue(result.isOk)
            assertEquals(5, result.value)
        }

    @Test
    fun commandDslWithCustomSubsystem() =
        runTest {
            val cmd =
                command<String, String>("test.dsl.crypto.encrypt") {
                    subsystem(EventSubsystems.CRYPTO)
                    execute { str -> Ok(str.reversed()) }
                }

            assertEquals(EventSubsystems.CRYPTO, cmd.subsystem)
        }

    @Test
    fun commandDslWithCustomSupports() =
        runTest {
            val cmd =
                command<String, String>("test.dsl.string.uppercase") {
                    supports { args -> args is String && args.isNotBlank() }
                    execute { str -> Ok(str.uppercase()) }
                }

            assertTrue(cmd.supports("hello"))
            assertFalse(cmd.supports(""))
            assertFalse(cmd.supports("   "))
            assertFalse(cmd.supports(123))

            assertTrue(cmd.supports("hello"))
            assertFalse(cmd.supports(""))
            assertFalse(cmd.supports("   "))
            assertFalse(cmd.supports(123))
        }

    @Test
    fun commandDslWithEnabledFalse() =
        runTest {
            val cmd =
                command<String, String>("test.dsl.string.disabled") {
                    enabled(false)
                    execute { str -> Ok(str) }
                }

            assertFalse(cmd.isEnabled)
        }

    @Test
    fun commandDslReturnsError() =
        runTest {
            val cmd =
                command<Int, Int>("test.dsl.math.divide") {
                    execute { n ->
                        if (n == 0) {
                            Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Division by zero"))
                        } else {
                            Ok(100 / n)
                        }
                    }
                }

            val okResult = cmd.testExecute(10)
            assertTrue(okResult.isOk)
            assertEquals(10, okResult.value)

            val errResult = cmd.testExecute(0)
            assertTrue(errResult.isErr)
            assertTrue(
                errResult.error.message.defaultMessage
                    .contains("Division by zero"),
            )
        }

    @Test
    fun commandDslWithSessionContextAccess() =
        runTest {
            val cmd =
                command<String, String>("test.dsl.session.info") {
                    execute { str ->
                        Ok("$str from command")
                    }
                }

            val result = cmd.testExecute("hello")
            assertTrue(result.isOk)
            assertEquals("hello from command", result.value)
        }

    @Test
    fun commandDslFailsWithoutExecuteBlock() {
        assertFailsWith<IllegalStateException> {
            CommandBuilder<String, String>("test.dsl.missing.execute").build()
        }
    }

    @Test
    fun commandDslWithAllOptions() =
        runTest {
            val cmd =
                command<String, Int>("test.dsl.string.full") {
                    subsystem(EventSubsystems.SDJWT)
                    enabled(true)
                    supports { args -> args is String }
                    execute { str -> Ok(str.length) }
                }

            assertEquals("test.dsl.string.full", cmd.id)
            assertEquals(EventSubsystems.SDJWT, cmd.subsystem)
            assertTrue(cmd.isEnabled)
            assertTrue(cmd.supports("test"))
            assertTrue(cmd.supports("test"))

            val result = cmd.testExecute("hello")
            assertTrue(result.isOk)
            assertEquals(5, result.value)
        }

    @Test
    fun commandDslSupportsWithContextDelegatesToContextFreePrimary() =
        runTest {
            val cmd =
                command<String, String>("test.dsl.legacy.supports.bridge") {
                    supports { args ->
                        args is String && args.startsWith("ok")
                    }
                    execute { str -> Ok(str) }
                }

            assertTrue(cmd.supports("ok-value"))
            assertTrue(cmd.supports("ok-value"))

            val supportResult = cmd.supportsOrError("ok-value", IdkErrorCommandErrorMapper)
            assertTrue(supportResult.isOk)
        }
}

class SimpleCommandDslTest {
    @Test
    fun simpleCommandDslCreatesBasicCommand() =
        runTest {
            val cmd =
                simpleCommandDsl<String, Int>("test.simple.dsl.length") {
                    execute { str -> Ok(str.length) }
                }

            assertEquals("test.simple.dsl.length", cmd.id)

            val result = cmd.execute("hello")
            assertTrue(result.isOk)
            assertEquals(5, result.value)
        }

    @Test
    fun simpleCommandDslWithCustomSupports() =
        runTest {
            val cmd =
                simpleCommandDsl<String, String>("test.simple.dsl.upper") {
                    supports { args -> args is String && args.isNotBlank() }
                    execute { str -> Ok(str.uppercase()) }
                }

            assertTrue(cmd.supports("hello"))
            assertFalse(cmd.supports(""))
            assertFalse(cmd.supports(123))
        }

    @Test
    fun simpleCommandDslReturnsError() =
        runTest {
            val cmd =
                simpleCommandDsl<String, Int>("test.simple.dsl.parse") {
                    execute { str ->
                        str
                            .toIntOrNull()
                            ?.let { Ok(it) }
                            ?: Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Not a number: $str"))
                    }
                }

            val okResult = cmd.execute("42")
            assertTrue(okResult.isOk)
            assertEquals(42, okResult.value)

            val errResult = cmd.execute("abc")
            assertTrue(errResult.isErr)
        }

    @Test
    fun simpleCommandDslFailsWithoutExecuteBlock() {
        assertFailsWith<IllegalStateException> {
            SimpleCommandBuilder<String, String>("test.simple.dsl.missing").build()
        }
    }

    @Test
    fun simpleCommandDslConvertsToFullCommand() =
        runTest {
            val simple =
                simpleCommandDsl<String, Int>("test.simple.dsl.convert") {
                    execute { str -> Ok(str.length) }
                }

            val full = simple.asCommand()

            assertEquals("test.simple.dsl.convert", full.id)
            assertTrue(full.isEnabled)

            val result = full.testExecute("world")
            assertTrue(result.isOk)
            assertEquals(5, result.value)
        }

    @Test
    fun simpleCommandAsCommandSupportsWithContextDelegatesToContextFreePrimary() =
        runTest {
            val simple =
                simpleCommandDsl<String, String>("test.simple.dsl.supports.delegate") {
                    supports { args -> args is String && args.startsWith("ok") }
                    execute { str -> Ok(str) }
                }
            val full = simple.asCommand()
            val forgedContext = object : SessionContext by testContext {}

            assertTrue(full.supports("ok-value"))
            assertFalse(full.supports("bad-value"))
            assertEquals(full.supports("ok-value"), full.supports("ok-value"))
            assertEquals(full.supports("bad-value"), full.supports("bad-value"))
        }

    @Test
    fun simpleCommandAsCommandWithErrorSupportsWithContextDelegatesToContextFreePrimary() =
        runTest {
            val simple =
                simpleCommandDsl<String, String>("test.simple.dsl.supports.error.delegate") {
                    supports { args -> args == "ok" }
                    execute { str -> Ok(str) }
                }
            val full = simple.asCommandWithError(errorMapper = { it })
            val forgedContext = object : SessionContext by testContext {}

            assertEquals(full.supports("ok"), full.supports("ok"))
            assertEquals(full.supports("bad"), full.supports("bad"))
        }
}
