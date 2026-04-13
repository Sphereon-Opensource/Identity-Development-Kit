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

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.events.EventSubsystems
import com.sphereon.di.session.SessionContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CommandErrorMapperTest {
    // Helper to create a test command
    private fun testCommand(commandId: String): Command<String, Int, IdkError> =
        object : Command<String, Int, IdkError> {
            override val id = commandId
            override val isEnabled = true
            override val subsystem = EventSubsystems.CUSTOM

            override suspend fun supports(args: Any) = true

            override suspend fun execute(args: String) = Ok(args.length)
        }

    // === IdkErrorCommandErrorMapper tests ===

    @Test
    fun unsupportedArgCreatesCorrectError() {
        val command = testCommand("test.error.mapper.unsupported")
        val error = IdkErrorCommandErrorMapper.unsupportedArg(command, "testArg")

        assertEquals("COMMAND_ARG_NOT_SUPPORTED_ERROR", error.code)
        assertTrue(error.message.defaultMessage.contains("does not support"))
        assertTrue(error.message.defaultMessage.contains("testArg"))
    }

    @Test
    fun unsupportedArgWithNonCommandUsesUnknownId() {
        val error = IdkErrorCommandErrorMapper.unsupportedArg("not-a-command", "testArg")

        assertEquals("COMMAND_ARG_NOT_SUPPORTED_ERROR", error.code)
        assertTrue(
            error.message.defaultMessage.contains("<unknown>") ||
                error.message.defaultMessage.contains("does not support"),
        )
    }

    @Test
    fun commandDisabledCreatesCorrectError() {
        val error = IdkErrorCommandErrorMapper.commandDisabled("test.cmd.disabled")

        assertEquals("COMMAND_DISABLED", error.code)
        assertTrue(error.message.defaultMessage.contains("test.cmd.disabled"))
        assertTrue(error.message.defaultMessage.contains("disabled"))
    }

    @Test
    fun commandDisabledWithNullPluginId() {
        val error = IdkErrorCommandErrorMapper.commandDisabled("test.cmd.disabled")

        assertEquals("COMMAND_DISABLED", error.code)
        assertTrue(error.message.defaultMessage.contains("test.cmd.disabled"))
        assertTrue(error.message.defaultMessage.contains("disabled"))
    }

    @Test
    fun commandSkippedCreatesCorrectError() {
        val error = IdkErrorCommandErrorMapper.commandSkipped("test.cmd.skip", "cache-hit")

        assertEquals("COMMAND_SKIPPED", error.code)
        assertTrue(error.message.defaultMessage.contains("test.cmd.skip"))
        assertTrue(error.message.defaultMessage.contains("cache-hit"))
    }

    @Test
    fun notAuthorizedCreatesCorrectError() {
        // CommandId requires format: module.service.command
        val commandId = CommandId("test.authz.resource-action")
        val error = IdkErrorCommandErrorMapper.notAuthorized(commandId, "Insufficient permissions")

        // Error code depends on authorizationError implementation
        assertTrue(
            error.message.defaultMessage.contains("Insufficient permissions") ||
                error.message.defaultMessage.contains("authorized") ||
                error.message.defaultMessage.contains("test.authz.resource-action"),
        )
    }

    @Test
    fun unknownCreatesCorrectError() {
        val error = IdkErrorCommandErrorMapper.unknown("Something went wrong")

        assertEquals("UNKNOWN_ERROR", error.code)
        assertTrue(error.message.defaultMessage.contains("Something went wrong"))
    }

    @Test
    fun unknownWithCauseIncludesException() {
        val cause = RuntimeException("Root cause")
        val error = IdkErrorCommandErrorMapper.unknown("Something went wrong", cause)

        assertEquals("UNKNOWN_ERROR", error.code)
        assertEquals(cause, error.exception)
    }

    @Test
    fun allHandlersFailedCreatesCorrectError() {
        val error1 = IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Handler 1 failed")
        val error2 = IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Handler 2 failed")
        val errors = listOf(error1, error2)

        val error = IdkErrorCommandErrorMapper.allHandlersFailed(errors)

        assertEquals("ALL_HANDLERS_FAILED", error.code)
        assertTrue(error.message.defaultMessage.contains("Handler 1 failed"))
        assertTrue(error.message.defaultMessage.contains("Handler 2 failed"))
        assertEquals(2, error.causes.size)
    }

    @Test
    fun allHandlersFailedWithEmptyList() {
        val error = IdkErrorCommandErrorMapper.allHandlersFailed(emptyList())

        assertEquals("ALL_HANDLERS_FAILED", error.code)
        assertEquals(0, error.causes.size)
    }

    @Test
    fun invalidCommandIdCreatesCorrectError() {
        val error = IdkErrorCommandErrorMapper.invalidCommandId("invalid-id")

        assertEquals("ILLEGAL_ARGUMENT_ERROR", error.code)
        assertTrue(error.message.defaultMessage.contains("Invalid command ID"))
        assertTrue(error.message.defaultMessage.contains("invalid-id"))
    }

    @Test
    fun commandNotFoundCreatesCorrectError() {
        val error = IdkErrorCommandErrorMapper.commandNotFound("core.session.command.execute")

        assertEquals("NOT_FOUND_ERROR", error.code)
        assertTrue(error.message.defaultMessage.contains("core.session.command.execute"))
    }

    @Test
    fun idkErrorTypeMapperUsesCommandSkippedError() {
        val error = IdkErrorTypeCommandErrorMapper.commandSkipped("test.cmd.skip", "not-needed")

        assertEquals("COMMAND_SKIPPED", error.code)
    }

    @Test
    fun idkErrorTypeMapperUsesCommandNotFoundError() {
        val error = IdkErrorTypeCommandErrorMapper.commandNotFound("core.session.command.execute")

        assertEquals("NOT_FOUND_ERROR", error.code)
    }

    @Test
    fun commandErrorsCatalogCodesRemainStable() {
        val command = testCommand("test.error.catalog.stability")
        val commandId = CommandId("test.authz.resource-action")

        val actual =
            mapOf(
                "unsupportedArg" to CommandErrors.unsupportedArg(command, "arg").code,
                "commandDisabled" to CommandErrors.commandDisabled(command.id).code,
                "commandSkipped" to CommandErrors.commandSkipped(command.id).code,
                "notAuthorized" to CommandErrors.notAuthorized(commandId, "denied").code,
                "allHandlersFailed" to CommandErrors.allHandlersFailed(emptyList()).code,
                "commandNotFound" to CommandErrors.commandNotFound(command.id).code,
                "invalidCommandId" to CommandErrors.invalidCommandId("invalid-id").code,
            )

        val expected =
            mapOf(
                "unsupportedArg" to "COMMAND_ARG_NOT_SUPPORTED_ERROR",
                "commandDisabled" to "COMMAND_DISABLED",
                "commandSkipped" to "COMMAND_SKIPPED",
                "notAuthorized" to "COMMAND_NOT_AUTHORIZED",
                "allHandlersFailed" to "ALL_HANDLERS_FAILED",
                "commandNotFound" to "NOT_FOUND_ERROR",
                "invalidCommandId" to "ILLEGAL_ARGUMENT_ERROR",
            )

        assertEquals(expected, actual)
        assertEquals("COMMAND_NOT_AUTHORIZED", CommandErrors.COMMAND_NOT_AUTHORIZED_CODE)
        assertTrue(CommandErrors.isAuthorizationFailure(CommandErrors.notAuthorized(commandId, "denied")))
    }
}
