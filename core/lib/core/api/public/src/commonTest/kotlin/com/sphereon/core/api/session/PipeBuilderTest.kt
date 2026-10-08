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
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.events.EventSubsystems
import com.sphereon.core.api.session.testing.CommandTestSupport.testContext
import com.sphereon.di.session.SessionContext
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PipeBuilderTest {
    // Helper to create simple test commands
    private fun <A : Any, R : Any> testCommand(
        commandId: String,
        supportsFn: (Any) -> Boolean = { true },
        executeFn: suspend (A) -> IdkResult<R, IdkError>,
    ): Command<A, R, IdkError> =
        object : Command<A, R, IdkError> {
            override val id = commandId
            override val isEnabled = true
            override val subsystem = EventSubsystems.CUSTOM

            override suspend fun supports(args: Any): Boolean = supportsFn(args)

            override suspend fun execute(args: A) = executeFn(args)
        }

    @Test
    fun pipeBuilderStartCreatesBuilderWithCommand() =
        runTest {
            val parseCommand =
                testCommand<String, Int>("test.pipe.string.parse") { input ->
                    Ok(input.length)
                }

            val pipeline =
                PipeBuilder
                    .start("test.pipe.result.build", parseCommand, IdkErrorCommandErrorMapper)
                    .build()

            assertEquals("test.pipe.result.build", pipeline.id)

            val result = pipeline.execute("hello")
            assertTrue(result.isOk)
            assertEquals(5, result.value)
        }

    @Test
    fun pipeBuilderThenChainsCommands() =
        runTest {
            val parseCommand =
                testCommand<String, Int>("test.pipe.string.parse") { input ->
                    Ok(input.length)
                }

            val doubleCommand =
                testCommand<Int, Int>("test.pipe.int.double") { input ->
                    Ok(input * 2)
                }

            val pipeline =
                pipe("test.pipe.chain.result", parseCommand)
                    .then(doubleCommand)
                    .build()

            val result = pipeline.execute("hello")
            assertTrue(result.isOk)
            assertEquals(10, result.value) // "hello".length * 2
        }

    @Test
    fun pipeBuilderThenMapTransformsWithContext() =
        runTest {
            val parseCommand =
                testCommand<String, Int>("test.pipe.string.parse") { input ->
                    Ok(input.length)
                }

            val pipeline =
                pipe("test.pipe.map.result", parseCommand)
                    .thenMap { value ->
                        Ok(value * 3)
                    }.build()

            val result = pipeline.execute("test")
            assertTrue(result.isOk)
            assertEquals(12, result.value) // "test".length * 3 = 4 * 3
        }

    @Test
    fun pipeBuilderThenMapSimpleTransformsWithoutContext() =
        runTest {
            val parseCommand =
                testCommand<String, Int>("test.pipe.string.parse") { input ->
                    Ok(input.length)
                }

            val pipeline =
                pipe("test.pipe.simple.result", parseCommand)
                    .thenMapSimple { it * 4 }
                    .build()

            val result = pipeline.execute("hi")
            assertTrue(result.isOk)
            assertEquals(8, result.value) // "hi".length * 4 = 2 * 4
        }

    @Test
    fun pipeBuilderFilterPassesThroughWhenPredicatePasses() =
        runTest {
            val parseCommand =
                testCommand<String, Int>("test.pipe.string.parse") { input ->
                    Ok(input.length)
                }

            val pipeline =
                pipe("test.pipe.filter.pass", parseCommand)
                    .filter(
                        predicate = { it > 3 },
                        onFailure = { IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Value must be > 3") },
                    ).build()

            val result = pipeline.execute("hello") // length = 5 > 3
            assertTrue(result.isOk)
            assertEquals(5, result.value)
        }

    @Test
    fun pipeBuilderFilterReturnsErrorWhenPredicateFails() =
        runTest {
            val parseCommand =
                testCommand<String, Int>("test.pipe.string.parse") { input ->
                    Ok(input.length)
                }

            val pipeline =
                pipe("test.pipe.filter.fail", parseCommand)
                    .filter(
                        predicate = { it > 10 },
                        onFailure = { IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Value $it must be > 10") },
                    ).build()

            val result = pipeline.execute("hello") // length = 5, not > 10
            assertTrue(result.isErr)
            assertTrue(
                result.error.message.defaultMessage
                    .contains("must be > 10"),
            )
        }

    @Test
    fun pipeBuilderTapExecutesSideEffectWithoutChangingResult() =
        runTest {
            val parseCommand =
                testCommand<String, Int>("test.pipe.string.parse") { input ->
                    Ok(input.length)
                }

            var sideEffectCalled = false
            var capturedValue = 0

            val pipeline =
                pipe("test.pipe.tap.result", parseCommand)
                    .tap { value ->
                        sideEffectCalled = true
                        capturedValue = value
                    }.build()

            val result = pipeline.execute("testing")
            assertTrue(result.isOk)
            assertEquals(7, result.value)
            assertTrue(sideEffectCalled)
            assertEquals(7, capturedValue)
        }

    @Test
    fun pipeBuilderMultipleStepsChainCorrectly() =
        runTest {
            val parseCommand =
                testCommand<String, Int>("test.pipe.string.parse") { input ->
                    Ok(input.length)
                }

            val doubleCommand =
                testCommand<Int, Int>("test.pipe.int.double") { input ->
                    Ok(input * 2)
                }

            val pipeline =
                pipe("test.pipe.multi.result", parseCommand)
                    .then(doubleCommand)
                    .thenMapSimple { it + 5 }
                    .filter(
                        predicate = { it > 10 },
                        onFailure = { IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Too small") },
                    ).thenMapSimple { "Result: $it" }
                    .build()

            // "hello" -> 5 -> 10 -> 15 -> passes filter -> "Result: 15"
            val result = pipeline.execute("hello")
            assertTrue(result.isOk)
            assertEquals("Result: 15", result.value)
        }

    @Test
    fun pipeConvenienceFunctionWorksWithIdkError() =
        runTest {
            val parseCommand =
                testCommand<String, Int>("test.pipe.string.parse") { input ->
                    Ok(input.length)
                }

            val pipeline =
                pipe("test.pipe.convenience.result", parseCommand)
                    .thenMapSimple { it * 2 }
                    .build()

            val result = pipeline.execute("test")
            assertTrue(result.isOk)
            assertEquals(8, result.value)
        }

    @Test
    fun transformCommandCreatesWorkingCommand() =
        runTest {
            val transform =
                transformCommand<String, Int, IdkError>("test.transform.string.length") { input ->
                    Ok(input.length)
                }

            assertEquals("test.transform.string.length", transform.id)
            assertTrue(transform.isEnabled)
            assertTrue(transform.supports("anything"))

            val result = transform.execute("hello")
            assertTrue(result.isOk)
            assertEquals(5, result.value)
        }

    @Test
    fun transformCommandSimpleVersionWorks() =
        runTest {
            val transform =
                transformCommandSimple<String, Int>("test.transform.string.length") { input ->
                    input.length
                }

            assertEquals("test.transform.string.length", transform.id)

            val result = transform.execute("world")
            assertTrue(result.isOk)
            assertEquals(5, result.value)
        }

    @Test
    fun transformCommandCanBeUsedInPipeline() =
        runTest {
            val parseCommand =
                testCommand<String, Int>("test.pipe.string.parse") { input ->
                    Ok(input.length)
                }

            val formatTransform =
                transformCommandSimple<Int, String>("test.transform.int.format") { value ->
                    "Length: $value"
                }

            val pipeline =
                pipe("test.pipe.transform.result", parseCommand)
                    .then(formatTransform)
                    .build()

            val result = pipeline.execute("testing")
            assertTrue(result.isOk)
            assertEquals("Length: 7", result.value)
        }

    @Test
    fun pipelineReturnsFirstError() =
        runTest {
            val failingCommand =
                testCommand<String, Int>("test.pipe.string.fail") { _ ->
                    Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Parse failed"))
                }

            val doubleCommand =
                testCommand<Int, Int>("test.pipe.int.double") { input ->
                    Ok(input * 2)
                }

            val pipeline =
                pipe("test.pipe.error.result", failingCommand)
                    .then(doubleCommand)
                    .build()

            val result = pipeline.execute("hello")
            assertTrue(result.isErr)
            assertTrue(
                result.error.message.defaultMessage
                    .contains("Parse failed"),
            )
        }

    @Test
    fun pipelinePropagatesAuthorizationErrorFromFirstCommand() =
        runTest {
            var secondExecuted = false
            val deniedCommand =
                testCommand<String, Int>("test.pipe.auth.first") { _ ->
                    Err(
                        CommandErrors.notAuthorized(
                            commandId = CommandId("test.pipe.auth-first"),
                            reason = "policy denied",
                        ),
                    )
                }
            val secondCommand =
                testCommand<Int, Int>("test.pipe.auth.second") { input ->
                    secondExecuted = true
                    Ok(input * 2)
                }

            val pipeline =
                pipe("test.pipe.auth.propagation", deniedCommand)
                    .then(secondCommand)
                    .build()

            val result = pipeline.execute("hello")
            assertTrue(result.isErr)
            assertEquals("COMMAND_NOT_AUTHORIZED", result.error.code)
            assertTrue(!secondExecuted)
        }

    @Test
    fun pipelineReturnsCanonicalUnsupportedWhenSecondCommandDoesNotSupportOutput() =
        runTest {
            val firstCommand =
                testCommand<String, Int>("test.pipe.support.first") { input ->
                    Ok(input.length)
                }
            val unsupportedSecond =
                testCommand<Int, Int>(
                    commandId = "test.pipe.support.second",
                    supportsFn = { false },
                    executeFn = { input -> Ok(input * 2) },
                )

            val pipeline =
                pipe("test.pipe.support.result", firstCommand)
                    .then(unsupportedSecond)
                    .build()

            val result = pipeline.execute("hello")
            assertTrue(result.isErr)
            assertEquals("COMMAND_ARG_NOT_SUPPORTED_ERROR", result.error.code)
        }

    @Test
    fun pipelineSupportsForwardsToFirstCommand() =
        runTest {
            val selectiveCommand =
                testCommand<String, Int>(
                    commandId = "test.pipe.string.selective",
                    supportsFn = { it is String && it.startsWith("valid") },
                    executeFn = { input -> Ok(input.length) },
                )

            val pipeline =
                pipe("test.pipe.selective.result", selectiveCommand)
                    .thenMapSimple { it * 2 }
                    .build()

            assertTrue(pipeline.supports("valid-input"))
            assertTrue(!pipeline.supports("invalid-input"))
            assertTrue(!pipeline.supports(123))
        }

    @Test
    fun pipelineSupportsUsesContextFreePrimaryPath() =
        runTest {
            val selectiveCommand =
                testCommand<String, Int>(
                    commandId = "test.pipe.string.context.free",
                    supportsFn = { it is String && it.startsWith("valid") },
                    executeFn = { input -> Ok(input.length) },
                )

            val pipeline =
                pipe("test.pipe.context.free.supports", selectiveCommand)
                    .thenMapSimple { it * 2 }
                    .build()

            assertTrue(pipeline.supports("valid-input"))
            assertTrue(!pipeline.supports("invalid-input"))
            assertTrue(!pipeline.supports(123))
        }

    @Test
    fun pipelineSupportsWithContextDelegatesToContextFreePrimary() =
        runTest {
            val legacyFirst =
                object : Command<String, Int, IdkError> {
                    override val id = "test.pipe.first"
                    override val isEnabled = true
                    override val subsystem = EventSubsystems.CUSTOM

                    override suspend fun supports(args: Any): Boolean = false

                    override suspend fun execute(args: String): IdkResult<Int, IdkError> = Ok(args.length)
                }

            val pipeline =
                pipe("test.pipe.legacy.supports.bridge", legacyFirst)
                    .thenMapSimple { it * 2 }
                    .build()

            assertTrue(!pipeline.supports("ok-input"))
            assertTrue(!pipeline.supports("ok-input"))

            val supportResult = pipeline.supportsOrError("ok-input", IdkErrorCommandErrorMapper)
            assertTrue(supportResult.isErr)
            assertEquals("COMMAND_ARG_NOT_SUPPORTED_ERROR", supportResult.error.code)
        }

    @Test
    fun pipeBuilderWithCustomErrorMapperWorks() =
        runTest {
            val parseCommand =
                testCommand<String, Int>("test.pipe.string.parse") { input ->
                    Ok(input.length)
                }

            val pipeline =
                PipeBuilder
                    .start("test.pipe.custom.mapper", parseCommand, IdkErrorCommandErrorMapper)
                    .thenMapSimple { it * 2 }
                    .build()

            val result = pipeline.execute("test")
            assertTrue(result.isOk)
            assertEquals(8, result.value)
        }

    @Test
    fun builtPipelineHasCorrectProperties() {
        val parseCommand =
            testCommand<String, Int>("test.pipe.string.parse") { input ->
                Ok(input.length)
            }

        val pipeline = pipe("test.pipe.props.check", parseCommand).build()

        assertEquals("test.pipe.props.check", pipeline.id)
        assertTrue(pipeline.isEnabled)
        assertEquals(EventSubsystems.CUSTOM, pipeline.subsystem)
    }
}
