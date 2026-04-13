/*
 * Â© 2025 Sphereon International B.V.
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

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.events.EventSubsystems
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SupportsOrErrorTest {

    private fun testCommand(
        commandId: String,
        supportsFn: (Any) -> Boolean = { true }
    ): Command<String, Int, IdkError> = object : Command<String, Int, IdkError> {
        override val id = commandId
        override val isEnabled = true
        override val subsystem = EventSubsystems.CUSTOM
        override suspend fun supports(args: Any) = supportsFn(args)
        override suspend fun execute(args: String) = Ok(args.length)
    }

    @Test
    fun supportsOrErrorReturnsOkWhenSupported() = runTest {
        val command = testCommand("test.supports.ok") { true }
        val result = command.supportsOrError("test-args")
        assertTrue(result.isOk)
        assertEquals(Unit, result.value)
    }

    @Test
    fun supportsOrErrorReturnsErrWhenNotSupported() = runTest {
        val command = testCommand("test.supports.err") { false }
        val result = command.supportsOrError("test-args")
        assertTrue(result.isErr)
        assertEquals("COMMAND_ARG_NOT_SUPPORTED_ERROR", result.error.code)
    }

    @Test
    fun supportsOrErrorWithCustomMapperUsesMapper() = runTest {
        val mapper = object : CommandErrorMapper<IdkError> {
            override fun unsupportedArg(command: Any, arg: Any) =
                IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Custom unsupported: $arg")
            override fun commandDisabled(commandId: String) =
                IdkError.ILLEGAL_ARGUMENT_ERROR(message = "disabled")
            override fun notAuthorized(commandId: CommandId, reason: String) =
                IdkError.ILLEGAL_ARGUMENT_ERROR(message = "not authorized")
            override fun unknown(message: String, cause: Throwable?) = IdkError.UNKNOWN_ERROR(message = message)
            override fun allHandlersFailed(errors: List<IdkError>) = IdkError.UNKNOWN_ERROR(message = "all failed")
            override fun invalidCommandId(commandId: String) =
                IdkError.ILLEGAL_ARGUMENT_ERROR(message = "invalid id")
            override fun commandSkipped(commandId: String, reason: String?) =
                IdkError.ILLEGAL_ARGUMENT_ERROR(message = "skipped")
        }

        val command = testCommand("test.supports.custom") { false }
        val result: IdkResult<Unit, IdkError> = command.supportsOrError("arg", mapper)

        assertTrue(result.isErr)
        assertTrue(result.error.message.defaultMessage.contains("Custom unsupported"))
    }
}
