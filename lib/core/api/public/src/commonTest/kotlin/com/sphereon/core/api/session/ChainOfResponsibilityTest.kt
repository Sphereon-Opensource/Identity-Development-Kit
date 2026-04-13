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
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.events.EventSubsystems
import com.sphereon.core.api.session.testing.CommandTestSupport.testContext
import com.sphereon.di.session.SessionContext
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChainOfResponsibilityTest {

    // Helper to create test handlers with specific support criteria
    private fun <A : Any, R : Any> testHandler(
        name: String,
        supportsFn: (Any) -> Boolean,
        executeFn: suspend (A) -> IdkResult<R, IdkError>
    ): BaseCommand<A, R, IdkError> = object : BaseCommand<A, R, IdkError> {
        override suspend fun supports(args: Any) = supportsFn(args)
        override suspend fun execute(args: A) = executeFn(args)
    }

    @Test
    fun chainOfResponsibilityReturnsFirstSuccessfulHandler() = runTest {
        val handler1 = testHandler<String, String>(
            name = "handler1",
            supportsFn = { it is String && it.startsWith("a") },
            executeFn = { s -> Ok("handler1: $s") }
        )

        val handler2 = testHandler<String, String>(
            name = "handler2",
            supportsFn = { it is String && it.startsWith("b") },
            executeFn = { s -> Ok("handler2: $s") }
        )

        val chain = chainOfResponsibility(
            id = "test.cor.string.process",
            handlers = listOf(handler1, handler2)
        )

        val result1 = chain.execute("apple")
        assertTrue(result1.isOk)
        assertEquals("handler1: apple", result1.value)

        val result2 = chain.execute("banana")
        assertTrue(result2.isOk)
        assertEquals("handler2: banana", result2.value)
    }

    @Test
    fun chainOfResponsibilityTriesNextHandlerOnFailure() = runTest {
        val failingHandler = testHandler<String, String>(
            name = "failing",
            supportsFn = { true },
            executeFn = { _ -> Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Handler 1 failed")) }
        )

        val successHandler = testHandler<String, String>(
            name = "success",
            supportsFn = { true },
            executeFn = { s -> Ok("success: $s") }
        )

        val chain = chainOfResponsibility(
            id = "test.cor.string.fallback",
            handlers = listOf(failingHandler, successHandler)
        )

        val result = chain.execute("test")
        assertTrue(result.isOk)
        assertEquals("success: test", result.value)
    }

    @Test
    fun chainOfResponsibilityReturnsErrorWhenAllHandlersFail() = runTest {
        val handler1 = testHandler<String, String>(
            name = "handler1",
            supportsFn = { true },
            executeFn = { _ -> Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Handler 1 failed")) }
        )

        val handler2 = testHandler<String, String>(
            name = "handler2",
            supportsFn = { true },
            executeFn = { _ -> Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Handler 2 failed")) }
        )

        val chain = chainOfResponsibility(
            id = "test.cor.string.allfail",
            handlers = listOf(handler1, handler2)
        )

        val result = chain.execute("test")
        assertTrue(result.isErr)
        // Should contain info about all failures
        assertTrue(result.error.message.defaultMessage.contains("All") ||
                result.error.message.defaultMessage.contains("handler"))
    }

    @Test
    fun chainOfResponsibilityReturnsErrorWhenNoHandlerSupports() = runTest {
        val handler1 = testHandler<String, String>(
            name = "handler1",
            supportsFn = { it is String && it.startsWith("a") },
            executeFn = { s -> Ok("handler1: $s") }
        )

        val handler2 = testHandler<String, String>(
            name = "handler2",
            supportsFn = { it is String && it.startsWith("b") },
            executeFn = { s -> Ok("handler2: $s") }
        )

        val chain = chainOfResponsibility(
            id = "test.cor.string.nosupport",
            handlers = listOf(handler1, handler2)
        )

        val result = chain.execute("zebra") // Neither handler supports "z"
        assertTrue(result.isErr)
        assertEquals("COMMAND_ARG_NOT_SUPPORTED_ERROR", result.error.code)
        assertTrue(result.error.message.defaultMessage.contains("does not support"))
    }

    @Test
    fun chainOfResponsibilityShortCircuitsOnAuthorizationFailure() = runTest {
        var secondHandlerExecuted = false
        val unauthorizedHandler = testHandler<String, String>(
            name = "unauthorized",
            supportsFn = { true },
            executeFn = { _ ->
                Err(
                    IdkError.COMMAND_NOT_AUTHORIZED_ERROR(
                        commandId = "test.cor.authz",
                        reason = "Denied by policy"
                    )
                )
            }
        )
        val secondHandler = testHandler<String, String>(
            name = "second",
            supportsFn = { true },
            executeFn = { s ->
                secondHandlerExecuted = true
                Ok("second: $s")
            }
        )
        val chain = chainOfResponsibility(
            id = "test.cor.authz.shortcircuit",
            handlers = listOf(unauthorizedHandler, secondHandler)
        )

        val result = chain.execute("test")

        assertTrue(result.isErr)
        assertEquals("COMMAND_NOT_AUTHORIZED", result.error.code)
        assertFalse(secondHandlerExecuted)
    }

    @Test
    fun chainOfResponsibilitySupportsReturnsTrueIfAnyHandlerSupports() = runTest {
        val handler1 = testHandler<String, String>(
            name = "handler1",
            supportsFn = { it is String && it.startsWith("a") },
            executeFn = { s -> Ok("handler1: $s") }
        )

        val handler2 = testHandler<String, String>(
            name = "handler2",
            supportsFn = { it is String && it.startsWith("b") },
            executeFn = { s -> Ok("handler2: $s") }
        )

        val chain = chainOfResponsibility(
            id = "test.cor.string.supports",
            handlers = listOf(handler1, handler2)
        )

        assertTrue(chain.supports("apple"))
        assertTrue(chain.supports("banana"))
        assertFalse(chain.supports("cherry"))
        assertFalse(chain.supports(123))
    }

    @Test
    fun chainOfResponsibilitySupportsWithContextUsesContextFreePrimaryPath() = runTest {
        val handler = object : BaseCommand<String, String, IdkError> {
            override suspend fun supports(args: Any): Boolean =
                false

            override suspend fun execute(args: String): IdkResult<String, IdkError> =
                Ok("handler: $args")
        }

        val chain = chainOfResponsibility(
            id = "test.cor.string.supports.context.bridge",
            handlers = listOf(handler)
        )

        assertFalse(chain.supports("ctx-only"))
        assertFalse(chain.supports("ctx-only"))
    }

    @Test
    fun chainOfResponsibilityExecuteUsesContextFreePrimaryForHandlerSupports() = runTest {
        var handlerExecuted = false
        val handler = object : BaseCommand<String, String, IdkError> {
            override suspend fun supports(args: Any): Boolean =
                false

            override suspend fun execute(args: String): IdkResult<String, IdkError> {
                handlerExecuted = true
                return Ok("handler: $args")
            }
        }

        val chain = chainOfResponsibility(
            id = "test.cor.string.execute.context.bridge",
            handlers = listOf(handler)
        )

        val result = chain.execute("ctx-only")
        assertTrue(result.isErr)
        assertEquals("COMMAND_ARG_NOT_SUPPORTED_ERROR", result.error.code)
        assertFalse(handlerExecuted)
    }

    @Test
    fun chainOfResponsibilityHasCorrectProperties() {
        val handler = testHandler<String, String>(
            name = "handler",
            supportsFn = { true },
            executeFn = { s -> Ok(s) }
        )

        val chain = chainOfResponsibility(
            id = "test.cor.props.check",
            handlers = listOf(handler)
        )

        assertEquals("test.cor.props.check", chain.id)
        assertTrue(chain.isEnabled)
        assertEquals(EventSubsystems.CUSTOM, chain.subsystem)
    }

    @Test
    fun chainOfResponsibilityVarargVersionWorks() = runTest {
        val handler1 = testHandler<String, String>(
            name = "handler1",
            supportsFn = { true },
            executeFn = { s -> Ok("first: $s") }
        )

        val handler2 = testHandler<String, String>(
            name = "handler2",
            supportsFn = { true },
            executeFn = { s -> Ok("second: $s") }
        )

        val chain = chainOfResponsibility(
            id = "test.cor.string.vararg",
            errorMapper = IdkErrorCommandErrorMapper,
            handler1, handler2
        )

        val result = chain.execute("test")
        assertTrue(result.isOk)
        assertEquals("first: test", result.value)
    }

    // === ChainOfResponsibilityConfig tests ===

    @Test
    fun chainOfResponsibilityConfiguredStopOnFirstSupportingReturnsFirstMatchError() = runTest {
        val failingHandler = testHandler<String, String>(
            name = "failing",
            supportsFn = { true },
            executeFn = { _ -> Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "First handler failed")) }
        )

        val successHandler = testHandler<String, String>(
            name = "success",
            supportsFn = { true },
            executeFn = { s -> Ok("success: $s") }
        )

        val chain = chainOfResponsibilityConfigured(
            id = "test.cor.config.stopfirst",
            handlers = listOf(failingHandler, successHandler),
            errorMapper = IdkErrorCommandErrorMapper,
            config = ChainOfResponsibilityConfig(stopOnFirstSupporting = true)
        )

        // Should stop at first supporting handler, even if it fails
        val result = chain.execute("test")
        assertTrue(result.isErr)
        assertTrue(result.error.message.defaultMessage.contains("First handler failed"))
    }

    @Test
    fun chainOfResponsibilityConfiguredCollectAllErrorsTrue() = runTest {
        val handler1 = testHandler<String, String>(
            name = "handler1",
            supportsFn = { true },
            executeFn = { _ -> Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Error 1")) }
        )

        val handler2 = testHandler<String, String>(
            name = "handler2",
            supportsFn = { true },
            executeFn = { _ -> Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Error 2")) }
        )

        val chain = chainOfResponsibilityConfigured(
            id = "test.cor.config.collectall",
            handlers = listOf(handler1, handler2),
            errorMapper = IdkErrorCommandErrorMapper,
            config = ChainOfResponsibilityConfig(collectAllErrors = true)
        )

        val result = chain.execute("test")
        assertTrue(result.isErr)
        // Should combine both errors
    }

    @Test
    fun chainOfResponsibilityConfiguredCollectAllErrorsFalseReturnsLastError() = runTest {
        val handler1 = testHandler<String, String>(
            name = "handler1",
            supportsFn = { true },
            executeFn = { _ -> Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Error 1")) }
        )

        val handler2 = testHandler<String, String>(
            name = "handler2",
            supportsFn = { true },
            executeFn = { _ -> Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Error 2 is last")) }
        )

        val chain = chainOfResponsibilityConfigured(
            id = "test.cor.config.lasterror",
            handlers = listOf(handler1, handler2),
            errorMapper = IdkErrorCommandErrorMapper,
            config = ChainOfResponsibilityConfig(collectAllErrors = false)
        )

        val result = chain.execute("test")
        assertTrue(result.isErr)
        // Should only have the last error
        assertTrue(result.error.message.defaultMessage.contains("Error 2 is last"))
    }

    // === firstMatch tests ===

    @Test
    fun firstMatchReturnsFirstSupportingHandlerResult() = runTest {
        val handler1 = testHandler<String, String>(
            name = "handler1",
            supportsFn = { true },
            executeFn = { _ -> Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "First failed")) }
        )

        val handler2 = testHandler<String, String>(
            name = "handler2",
            supportsFn = { true },
            executeFn = { s -> Ok("second: $s") }
        )

        val chain = firstMatch(
            id = "test.cor.firstmatch.result",
            handlers = listOf(handler1, handler2),
            errorMapper = IdkErrorCommandErrorMapper
        )

        // firstMatch should return the first handler's result (even if error)
        val result = chain.execute("test")
        assertTrue(result.isErr)
        assertTrue(result.error.message.defaultMessage.contains("First failed"))
    }

    @Test
    fun firstMatchReturnsSuccessIfFirstHandlerSucceeds() = runTest {
        val handler1 = testHandler<String, String>(
            name = "handler1",
            supportsFn = { true },
            executeFn = { s -> Ok("first: $s") }
        )

        val handler2 = testHandler<String, String>(
            name = "handler2",
            supportsFn = { true },
            executeFn = { _ -> Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Should not reach")) }
        )

        val chain = firstMatch(
            id = "test.cor.firstmatch.success",
            handlers = listOf(handler1, handler2),
            errorMapper = IdkErrorCommandErrorMapper
        )

        val result = chain.execute("test")
        assertTrue(result.isOk)
        assertEquals("first: test", result.value)
    }

    // === firstSuccess tests ===

    @Test
    fun firstSuccessTriesUntilOneSucceeds() = runTest {
        val handler1 = testHandler<String, String>(
            name = "handler1",
            supportsFn = { true },
            executeFn = { _ -> Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "First failed")) }
        )

        val handler2 = testHandler<String, String>(
            name = "handler2",
            supportsFn = { true },
            executeFn = { s -> Ok("second: $s") }
        )

        val chain = firstSuccess(
            id = "test.cor.firstsuccess.result",
            handlers = listOf(handler1, handler2),
            errorMapper = IdkErrorCommandErrorMapper
        )

        // firstSuccess should try until one succeeds
        val result = chain.execute("test")
        assertTrue(result.isOk)
        assertEquals("second: test", result.value)
    }

    @Test
    fun firstSuccessReturnsErrorIfAllFail() = runTest {
        val handler1 = testHandler<String, String>(
            name = "handler1",
            supportsFn = { true },
            executeFn = { _ -> Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "First failed")) }
        )

        val handler2 = testHandler<String, String>(
            name = "handler2",
            supportsFn = { true },
            executeFn = { _ -> Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Second failed")) }
        )

        val chain = firstSuccess(
            id = "test.cor.firstsuccess.allfail",
            handlers = listOf(handler1, handler2),
            errorMapper = IdkErrorCommandErrorMapper
        )

        val result = chain.execute("test")
        assertTrue(result.isErr)
    }

    // === ChainOfResponsibilityConfig data class tests ===

    @Test
    fun chainOfResponsibilityConfigDefaultValues() {
        val config = ChainOfResponsibilityConfig()
        assertFalse(config.stopOnFirstSupporting)
        assertTrue(config.collectAllErrors)
    }

    @Test
    fun chainOfResponsibilityConfigCustomValues() {
        val config = ChainOfResponsibilityConfig(
            stopOnFirstSupporting = true,
            collectAllErrors = false
        )
        assertTrue(config.stopOnFirstSupporting)
        assertFalse(config.collectAllErrors)
    }

    @Test
    fun chainOfResponsibilityConfigCopy() {
        val config = ChainOfResponsibilityConfig()
        val modified = config.copy(stopOnFirstSupporting = true)
        assertTrue(modified.stopOnFirstSupporting)
        assertTrue(modified.collectAllErrors)
    }
}

