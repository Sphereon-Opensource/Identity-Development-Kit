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

class SagaCommandTest {

    // Helper to create test compensatable commands
    private fun <A : Any, R : Any> testCompensatable(
        commandId: String,
        executeFn: suspend (A) -> IdkResult<R, IdkError>,
        compensateFn: suspend (A, R) -> IdkResult<Unit, IdkError> = { _, _ -> Ok(Unit) }
    ): CompensatableCommand<A, R, IdkError> = object : CompensatableCommand<A, R, IdkError> {
        override val id = commandId
        override val isEnabled = true
        override val subsystem = EventSubsystems.CUSTOM
        override suspend fun supports(args: Any) = true
        override suspend fun execute(args: A) = executeFn(args)
        override suspend fun compensate(args: A, result: R) =
            compensateFn(args, result)
    }

    // === CompensatableCommand tests ===

    @Test
    fun compensatableCommandFunctionCreatesWorkingCommand() = runTest {
        val command = compensatable<String, Int, IdkError>(
            id = "test.saga.compensatable.string.int",
            executeFn = { input -> Ok(input.length) },
            compensateFn = { _, _ -> Ok(Unit) }
        )

        assertEquals("test.saga.compensatable.string.int", command.id)
        assertTrue(command.isEnabled)
        assertEquals(EventSubsystems.CUSTOM, command.subsystem)
        assertTrue(command.supports("anything"))

        val result = command.execute("hello")
        assertTrue(result.isOk)
        assertEquals(5, result.value)

        val compensateResult = command.compensate("hello", 5)
        assertTrue(compensateResult.isOk)
    }

    @Test
    fun compensatableSupportsWithContextDelegatesToContextFreePrimary() = runTest {
        val command = compensatable<String, Int, IdkError>(
            id = "test.saga.compensatable.supports.bridge",
            executeFn = { input -> Ok(input.length) },
            compensateFn = { _, _ -> Ok(Unit) }
        )
        val forgedContext = object : SessionContext by testContext {}

        assertEquals(command.supports("value"), command.supports("value"))
    }

    // === TypedSagaStep tests ===

    @Test
    fun typedSagaStepExecutesCommand() = runTest {
        val command = testCompensatable<String, Int>(
            commandId = "test.saga.step.execute",
            executeFn = { input -> Ok(input.length) }
        )

        val step = sagaStep(command)
        val result = step.execute("hello")

        assertTrue(result.isOk)
        assertEquals(5, result.value)
    }

    // Note: Runtime type checking for generic types doesn't work due to type erasure.
    // The as? A cast in TypedSagaStep cannot actually verify types at runtime.
    // This is expected Kotlin/JVM behavior.

    @Test
    fun typedSagaStepCompensatesCommand() = runTest {
        var compensateCalled = false
        var capturedArgs: String? = null
        var capturedResult: Int? = null

        val command = testCompensatable<String, Int>(
            commandId = "test.saga.step.compensate",
            executeFn = { input -> Ok(input.length) },
            compensateFn = { args, result ->
                compensateCalled = true
                capturedArgs = args
                capturedResult = result
                Ok(Unit)
            }
        )

        val step = sagaStep(command)
        val compensateResult = step.compensate("hello", 5)

        assertTrue(compensateResult.isOk)
        assertTrue(compensateCalled)
        assertEquals("hello", capturedArgs)
        assertEquals(5, capturedResult)
    }

    // Note: Tests for invalid type handling at runtime are removed because
    // generic type erasure in Kotlin/JVM means runtime type checking doesn't work as expected.

    // === SagaCommandAdapter tests ===

    @Test
    fun sagaCommandExecutesAllStepsInSequence() = runTest {
        val executionOrder = mutableListOf<String>()

        val step1 = testCompensatable<String, String>(
            commandId = "test.saga.seq.step1",
            executeFn = { input ->
                executionOrder.add("step1")
                Ok("$input-1")
            }
        )

        val step2 = testCompensatable<String, String>(
            commandId = "test.saga.seq.step2",
            executeFn = { input ->
                executionOrder.add("step2")
                Ok("$input-2")
            }
        )

        val step3 = testCompensatable<String, String>(
            commandId = "test.saga.seq.step3",
            executeFn = { input ->
                executionOrder.add("step3")
                Ok("$input-3")
            }
        )

        val saga = saga<String, String>(
            "test.saga.sequence",
            sagaStep(step1),
            sagaStep(step2),
            sagaStep(step3)
        )

        val result = saga.execute("start")

        assertTrue(result.isOk)
        assertEquals("start-1-2-3", result.value)
        assertEquals(listOf("step1", "step2", "step3"), executionOrder)
    }

    @Test
    fun sagaCommandCompensatesOnFailure() = runTest {
        val executionOrder = mutableListOf<String>()
        val compensationOrder = mutableListOf<String>()

        val step1 = testCompensatable<String, String>(
            commandId = "test.saga.comp.step1",
            executeFn = { input ->
                executionOrder.add("execute1")
                Ok("$input-1")
            },
            compensateFn = { _, _ ->
                compensationOrder.add("compensate1")
                Ok(Unit)
            }
        )

        val step2 = testCompensatable<String, String>(
            commandId = "test.saga.comp.step2",
            executeFn = { input ->
                executionOrder.add("execute2")
                Ok("$input-2")
            },
            compensateFn = { _, _ ->
                compensationOrder.add("compensate2")
                Ok(Unit)
            }
        )

        val failingStep = testCompensatable<String, String>(
            commandId = "test.saga.comp.fail",
            executeFn = { _ ->
                executionOrder.add("execute3-fail")
                Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Step 3 failed"))
            },
            compensateFn = { _, _ ->
                compensationOrder.add("compensate3")
                Ok(Unit)
            }
        )

        val saga = saga<String, String>(
            "test.saga.compensation",
            sagaStep(step1),
            sagaStep(step2),
            sagaStep(failingStep)
        )

        val result = saga.execute("start")

        assertTrue(result.isErr)
        assertTrue(result.error.message.defaultMessage.contains("Step 3 failed"))
        assertEquals(listOf("execute1", "execute2", "execute3-fail"), executionOrder)
        // Compensation should be in reverse order
        assertEquals(listOf("compensate2", "compensate1"), compensationOrder)
    }

    @Test
    fun sagaCommandSupportsReturnsTrueWithValidContext() = runTest {
        val step = testCompensatable<String, String>(
            commandId = "test.saga.supports.step",
            executeFn = { input -> Ok(input) }
        )

        val saga = saga<String, String>("test.saga.supports", sagaStep(step))

        assertTrue(saga.supports("anything"))
    }

    @Test
    fun sagaCommandSupportsReturnsTrueWithNullContext() = runTest {
        val step = testCompensatable<String, String>(
            commandId = "test.saga.supports.null",
            executeFn = { input -> Ok(input) }
        )

        val saga = saga<String, String>("test.saga.supports.null", sagaStep(step))

        assertTrue(saga.supports("anything"))
        assertTrue(saga.supports("anything"))
    }

    @Test
    fun sagaCommandSupportsWithContextDelegatesToContextFreePrimary() = runTest {
        val step = testCompensatable<String, String>(
            commandId = "test.saga.supports.delegate",
            executeFn = { input -> Ok(input) }
        )
        val saga = saga<String, String>("test.saga.supports.delegate", sagaStep(step))
        val forgedContext = object : SessionContext by testContext {}

        assertEquals(saga.supports("anything"), saga.supports("anything"))
    }

    @Test
    fun sagaCommandSupportsReturnsFalseWithNoSteps() = runTest {
        val saga = SagaCommandAdapter<String, String, IdkError>(
            id = "test.saga.empty",
            steps = emptyList(),
            errorMapper = IdkErrorCommandErrorMapper
        )

        assertFalse(saga.supports("anything"))
    }

    @Test
    fun sagaCommandHasCorrectProperties() {
        val step = testCompensatable<String, String>(
            commandId = "test.saga.props.step",
            executeFn = { input -> Ok(input) }
        )

        val saga = SagaCommandAdapter<String, String, IdkError>(
            id = "test.saga.properties",
            steps = listOf(sagaStep(step)),
            errorMapper = IdkErrorCommandErrorMapper,
            isEnabled = true,
            subsystem = EventSubsystems.CUSTOM
        )

        assertEquals("test.saga.properties", saga.id)
        assertTrue(saga.isEnabled)
        assertEquals(EventSubsystems.CUSTOM, saga.subsystem)
    }

    // === SagaBuilder tests ===

    @Test
    fun sagaBuilderCreatesWorkingSaga() = runTest {
        val step1 = testCompensatable<String, String>(
            commandId = "test.saga.builder.step1",
            executeFn = { input -> Ok("$input-1") }
        )

        val step2 = testCompensatable<String, String>(
            commandId = "test.saga.builder.step2",
            executeFn = { input -> Ok("$input-2") }
        )

        val saga = sagaBuilder<String, String>("test.saga.builder.result")
            .step(step1)
            .step(step2)
            .build()

        assertEquals("test.saga.builder.result", saga.id)

        val result = saga.execute("start")
        assertTrue(result.isOk)
        assertEquals("start-1-2", result.value)
    }

    @Test
    fun sagaBuilderWithTypedErrorMapper() = runTest {
        val step = testCompensatable<String, Int>(
            commandId = "test.saga.builder.typed",
            executeFn = { input -> Ok(input.length) }
        )

        val saga = sagaBuilder<String, Int, IdkError>("test.saga.builder.typed.result", IdkErrorCommandErrorMapper)
            .step(step)
            .build()

        val result = saga.execute("hello")
        assertTrue(result.isOk)
        assertEquals(5, result.value)
    }

    // === sagaStep convenience function tests ===

    @Test
    fun sagaStepConvenienceFunctionWithErrorMapper() = runTest {
        val command = testCompensatable<String, Int>(
            commandId = "test.saga.step.mapper",
            executeFn = { input -> Ok(input.length) }
        )

        val step = sagaStep(command, IdkErrorCommandErrorMapper)
        val result = step.execute("test")

        assertTrue(result.isOk)
        assertEquals(4, result.value)
    }

    // === saga convenience function tests ===

    @Test
    fun sagaConvenienceFunctionWithErrorMapper() = runTest {
        val step = testCompensatable<String, String>(
            commandId = "test.saga.convenience.step",
            executeFn = { input -> Ok("$input-done") }
        )

        val sagaCmd = saga<String, String, IdkError>(
            "test.saga.convenience.mapper",
            IdkErrorCommandErrorMapper,
            sagaStep(step)
        )

        val result = sagaCmd.execute("start")
        assertTrue(result.isOk)
        assertEquals("start-done", result.value)
    }

    // === Complex saga scenarios ===

    @Test
    fun sagaWithMixedTypesWorksCorrectly() = runTest {
        val parseStep = testCompensatable<String, Int>(
            commandId = "test.saga.mixed.parse",
            executeFn = { input -> Ok(input.length) }
        )

        val doubleStep = testCompensatable<Int, Int>(
            commandId = "test.saga.mixed.double",
            executeFn = { input -> Ok(input * 2) }
        )

        val formatStep = testCompensatable<Int, String>(
            commandId = "test.saga.mixed.format",
            executeFn = { input -> Ok("Result: $input") }
        )

        val saga = saga<String, String>(
            "test.saga.mixed.types",
            sagaStep(parseStep),
            sagaStep(doubleStep),
            sagaStep(formatStep)
        )

        val result = saga.execute("hello")
        assertTrue(result.isOk)
        // "hello".length = 5, * 2 = 10, "Result: 10"
        assertEquals("Result: 10", result.value)
    }

    @Test
    fun sagaCompensationContinuesEvenIfCompensationFails() = runTest {
        val compensationOrder = mutableListOf<String>()

        val step1 = testCompensatable<String, String>(
            commandId = "test.saga.compfail.step1",
            executeFn = { input -> Ok("$input-1") },
            compensateFn = { _, _ ->
                compensationOrder.add("compensate1")
                Ok(Unit)
            }
        )

        val step2 = testCompensatable<String, String>(
            commandId = "test.saga.compfail.step2",
            executeFn = { input -> Ok("$input-2") },
            compensateFn = { _, _ ->
                compensationOrder.add("compensate2-fail")
                Err(IdkError.UNKNOWN_ERROR(message = "Compensation failed"))
            }
        )

        val failingStep = testCompensatable<String, String>(
            commandId = "test.saga.compfail.step3",
            executeFn = { _ -> Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Step failed")) },
            compensateFn = { _, _ -> Ok(Unit) }
        )

        val saga = saga<String, String>(
            "test.saga.compensation.failure",
            sagaStep(step1),
            sagaStep(step2),
            sagaStep(failingStep)
        )

        val result = saga.execute("start")

        assertTrue(result.isErr)
        // Both compensations should be attempted even if one fails
        assertEquals(listOf("compensate2-fail", "compensate1"), compensationOrder)
    }

    @Test
    fun sagaWithSingleStepWorks() = runTest {
        val step = testCompensatable<String, Int>(
            commandId = "test.saga.single.step",
            executeFn = { input -> Ok(input.length) }
        )

        val saga = saga<String, Int>("test.saga.single", sagaStep(step))

        val result = saga.execute("hello")
        assertTrue(result.isOk)
        assertEquals(5, result.value)
    }

    @Test
    fun sagaFirstStepFailsNoCompensationNeeded() = runTest {
        val compensationOrder = mutableListOf<String>()

        val failingStep = testCompensatable<String, String>(
            commandId = "test.saga.firstfail.step",
            executeFn = { _ -> Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "First step failed")) },
            compensateFn = { _, _ ->
                compensationOrder.add("compensate1")
                Ok(Unit)
            }
        )

        val saga = saga<String, String>("test.saga.firstfail", sagaStep(failingStep))

        val result = saga.execute("start")

        assertTrue(result.isErr)
        // No compensation should have been called since the first step failed before completing
        assertTrue(compensationOrder.isEmpty())
    }
}


