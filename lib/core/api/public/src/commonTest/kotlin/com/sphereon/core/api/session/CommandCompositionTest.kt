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
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommandCompositionTest {

    // Helper to create simple test commands
    private fun <A : Any, R : Any> testCommand(
        commandId: String,
        supportsFn: (Any) -> Boolean = { true },
        executeFn: suspend (A) -> IdkResult<R, IdkError>
    ): BaseCommand<A, R, IdkError> = object : BaseCommand<A, R, IdkError> {
        override suspend fun supports(args: Any) = supportsFn(args)
        override suspend fun execute(args: A) = executeFn(args)
    }

    // === mapInput tests ===

    @Test
    fun mapInputTransformsArgumentsBeforeExecution() = runTest {
        val intCommand = testCommand<Int, String>("test.comp.int.string") { num ->
            Ok("Number: $num")
        }

        val stringCommand = intCommand.mapInput<String, Int, String, IdkError> { str ->
            Ok(str.length)
        }

        val result = stringCommand.execute("hello")
        assertTrue(result.isOk)
        assertEquals("Number: 5", result.value)
    }

    @Test
    fun mapInputReturnsErrorWhenTransformFails() = runTest {
        val intCommand = testCommand<Int, String>("test.comp.int.string") { num ->
            Ok("Number: $num")
        }

        val stringCommand = intCommand.mapInput<String, Int, String, IdkError> { str ->
            if (str.isEmpty()) {
                Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Empty string not allowed"))
            } else {
                Ok(str.length)
            }
        }

        val result = stringCommand.execute("")
        assertTrue(result.isErr)
        assertTrue(result.error.message.defaultMessage.contains("Empty string not allowed"))
    }

    @Test
    fun mapInputSupportsReturnsFalseWhenTransformFails() = runTest {
        val intCommand = testCommand<Int, String>("test.comp.int.string") { num ->
            Ok("Number: $num")
        }

        val stringCommand = intCommand.mapInput<String, Int, String, IdkError> { str ->
            if (str.isEmpty()) {
                Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Empty"))
            } else {
                Ok(str.length)
            }
        }

        assertTrue(stringCommand.supports("hello"))
        assertFalse(stringCommand.supports(""))
    }

    @Test
    fun mapInputSupportsWithContextDelegatesToContextFreePrimary() = runTest {
        val intCommand = testCommand<Int, String>("test.comp.int.string") { num ->
            Ok("Number: $num")
        }

        val stringCommand = intCommand.mapInput<String, Int, String, IdkError> { str ->
            Ok(str.length)
        }

        assertTrue(stringCommand.supports("hello"))
        assertTrue(stringCommand.supports("hello"))
        assertTrue(stringCommand.supports("hello"))
    }

    @Test
    fun mapInputSupportsUsesTransformPredicate() = runTest {
        val intCommand = testCommand<Int, String>("test.comp.int.string") { num ->
            Ok("Number: $num")
        }
        val seenInputs = mutableListOf<String>()

        val stringCommand = intCommand.mapInput<String, Int, String, IdkError> { str ->
            seenInputs += str
            Ok(str.length)
        }

        assertTrue(stringCommand.supports("hello"))
        assertTrue(stringCommand.supports("hello"))
        assertEquals(listOf("hello", "hello"), seenInputs)
    }

    @Test
    fun mapInputSupportsWithContextDoesNotUseLegacyFallbackInWrappedCommand() = runTest {
        val legacyContextSensitive = object : BaseCommand<String, Int, IdkError> {
            override suspend fun supports(args: Any): Boolean =
                args is String && args.startsWith("ok")

            override suspend fun execute(args: String): IdkResult<Int, IdkError> =
                Ok(args.length)
        }

        val mappedCommand = legacyContextSensitive.mapInput<String, String, Int, IdkError> { arg ->
            Ok(arg)
        }

        assertTrue(mappedCommand.supports("ok-value"))
    }

    @Test
    fun mapInputSimpleTransformsWithoutContext() = runTest {
        val intCommand = testCommand<Int, String>("test.comp.int.string") { num ->
            Ok("Number: $num")
        }

        val stringCommand = intCommand.mapInputSimple<String, Int, String, IdkError> { str ->
            str.length
        }

        val result = stringCommand.execute("world")
        assertTrue(result.isOk)
        assertEquals("Number: 5", result.value)
    }

    // === mapOutput tests ===

    @Test
    fun mapOutputTransformsResultAfterExecution() = runTest {
        val stringCommand = testCommand<String, Int>("test.comp.string.int") { str ->
            Ok(str.length)
        }

        val doubledCommand = stringCommand.mapOutput<String, Int, Int, IdkError> { result ->
            Ok(result * 2)
        }

        val result = doubledCommand.execute("hello")
        assertTrue(result.isOk)
        assertEquals(10, result.value)
    }

    @Test
    fun mapOutputPreservesErrorFromOriginalCommand() = runTest {
        val failingCommand = testCommand<String, Int>("test.comp.string.fail") { _ ->
            Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Original error"))
        }

        val mappedCommand = failingCommand.mapOutput<String, Int, String, IdkError> { result ->
            Ok("Result: $result")
        }

        val result = mappedCommand.execute("anything")
        assertTrue(result.isErr)
        assertTrue(result.error.message.defaultMessage.contains("Original error"))
    }

    @Test
    fun mapOutputSimpleTransformsWithoutContext() = runTest {
        val stringCommand = testCommand<String, Int>("test.comp.string.int") { str ->
            Ok(str.length)
        }

        val formattedCommand = stringCommand.mapOutputSimple<String, Int, String, IdkError> { num ->
            "Length is $num"
        }

        val result = formattedCommand.execute("test")
        assertTrue(result.isOk)
        assertEquals("Length is 4", result.value)
    }

    @Test
    fun mapOutputPreservesSupports() = runTest {
        val selectiveCommand = testCommand<String, Int>(
            commandId = "test.comp.string.selective",
            supportsFn = { it is String && it.length > 3 },
            executeFn = { str -> Ok(str.length) }
        )

        val mappedCommand = selectiveCommand.mapOutputSimple<String, Int, String, IdkError> { num ->
            "Result: $num"
        }

        assertTrue(mappedCommand.supports("hello"))
        assertFalse(mappedCommand.supports("hi"))
    }

    @Test
    fun mapOutputSupportsWithContextDelegatesToContextFreePrimary() = runTest {
        val legacyContextSensitive = object : BaseCommand<String, Int, IdkError> {
            override suspend fun supports(args: Any): Boolean =
                args is String && args.startsWith("ok")

            override suspend fun execute(args: String): IdkResult<Int, IdkError> =
                Ok(args.length)
        }

        val mappedCommand = legacyContextSensitive.mapOutputSimple<String, Int, Int, IdkError> { it * 2 }

        assertTrue(mappedCommand.supports("ok-value"))
        assertTrue(mappedCommand.supports("ok-value"))

        val supportResult = mappedCommand.supportsOrError("ok-value", IdkErrorCommandErrorMapper)
        assertTrue(supportResult.isOk)
    }

    // === mapError tests ===

    @Test
    fun mapErrorTransformsErrors() = runTest {
        val failingCommand = testCommand<String, Int>("test.comp.string.fail") { _ ->
            Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Original"))
        }

        val mappedCommand = failingCommand.mapError<String, Int, IdkError, IdkError> { error ->
            IdkError.UNKNOWN_ERROR(message = "Transformed: ${error.message.defaultMessage}")
        }

        val result = mappedCommand.execute("anything")
        assertTrue(result.isErr)
        assertTrue(result.error.message.defaultMessage.contains("Transformed: Original"))
    }

    @Test
    fun mapErrorPreservesSuccessResult() = runTest {
        val successCommand = testCommand<String, Int>("test.comp.string.int") { str ->
            Ok(str.length)
        }

        val mappedCommand = successCommand.mapError<String, Int, IdkError, IdkError> { error ->
            IdkError.UNKNOWN_ERROR(message = "Should not be called")
        }

        val result = mappedCommand.execute("hello")
        assertTrue(result.isOk)
        assertEquals(5, result.value)
    }

    // === filterOutput tests ===

    @Test
    fun filterOutputPassesThroughWhenPredicatePasses() = runTest {
        val stringCommand = testCommand<String, Int>("test.comp.string.int") { str ->
            Ok(str.length)
        }

        val filteredCommand = stringCommand.filterOutput(
            predicate = { it >= 5 },
            onFailure = { IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Too short: $it") }
        )

        val result = filteredCommand.execute("hello")
        assertTrue(result.isOk)
        assertEquals(5, result.value)
    }

    @Test
    fun filterOutputReturnsErrorWhenPredicateFails() = runTest {
        val stringCommand = testCommand<String, Int>("test.comp.string.int") { str ->
            Ok(str.length)
        }

        val filteredCommand = stringCommand.filterOutput(
            predicate = { it >= 10 },
            onFailure = { IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Too short: $it") }
        )

        val result = filteredCommand.execute("hello")
        assertTrue(result.isErr)
        assertTrue(result.error.message.defaultMessage.contains("Too short: 5"))
    }

    @Test
    fun filterOutputPreservesOriginalError() = runTest {
        val failingCommand = testCommand<String, Int>("test.comp.string.fail") { _ ->
            Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Original failure"))
        }

        val filteredCommand = failingCommand.filterOutput(
            predicate = { it >= 5 },
            onFailure = { IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Filter failure") }
        )

        val result = filteredCommand.execute("anything")
        assertTrue(result.isErr)
        assertTrue(result.error.message.defaultMessage.contains("Original failure"))
    }

    // === recover tests ===

    @Test
    fun recoverProvidesAlternativeOnError() = runTest {
        val failingCommand = testCommand<String, Int>("test.comp.string.fail") { _ ->
            Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Failed"))
        }

        val recoveredCommand = failingCommand.recover { error ->
            Ok(0) // Return default value on error
        }

        val result = recoveredCommand.execute("anything")
        assertTrue(result.isOk)
        assertEquals(0, result.value)
    }

    @Test
    fun recoverCanReturnDifferentError() = runTest {
        val failingCommand = testCommand<String, Int>("test.comp.string.fail") { _ ->
            Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "First error"))
        }

        val recoveredCommand = failingCommand.recover { error ->
            Err(IdkError.UNKNOWN_ERROR(message = "Recovery failed too"))
        }

        val result = recoveredCommand.execute("anything")
        assertTrue(result.isErr)
        assertTrue(result.error.message.defaultMessage.contains("Recovery failed too"))
    }

    @Test
    fun recoverDoesNotAffectSuccess() = runTest {
        val successCommand = testCommand<String, Int>("test.comp.string.int") { str ->
            Ok(str.length)
        }

        val recoveredCommand = successCommand.recover { _ ->
            Ok(999) // Should not be used
        }

        val result = recoveredCommand.execute("hello")
        assertTrue(result.isOk)
        assertEquals(5, result.value) // Original result, not recovery
    }

    // === withFallback tests ===

    @Test
    fun withFallbackReturnsFallbackOnError() = runTest {
        val failingCommand = testCommand<String, Int>("test.comp.string.fail") { _ ->
            Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Failed"))
        }

        val fallbackCommand = failingCommand.withFallback(-1)

        val result = fallbackCommand.execute("anything")
        assertTrue(result.isOk)
        assertEquals(-1, result.value)
    }

    @Test
    fun withFallbackPreservesSuccessResult() = runTest {
        val successCommand = testCommand<String, Int>("test.comp.string.int") { str ->
            Ok(str.length)
        }

        val fallbackCommand = successCommand.withFallback(-1)

        val result = fallbackCommand.execute("hello")
        assertTrue(result.isOk)
        assertEquals(5, result.value) // Original, not fallback
    }

    // === onSuccess tests ===

    @Test
    fun onSuccessRunsEffectOnSuccess() = runTest {
        var effectCalled = false
        var capturedValue = 0

        val successCommand = testCommand<String, Int>("test.comp.string.int") { str ->
            Ok(str.length)
        }

        val withEffect = successCommand.onSuccess { value ->
            effectCalled = true
            capturedValue = value
        }

        val result = withEffect.execute("hello")
        assertTrue(result.isOk)
        assertEquals(5, result.value)
        assertTrue(effectCalled)
        assertEquals(5, capturedValue)
    }

    @Test
    fun onSuccessDoesNotRunOnFailure() = runTest {
        var effectCalled = false

        val failingCommand = testCommand<String, Int>("test.comp.string.fail") { _ ->
            Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Failed"))
        }

        val withEffect = failingCommand.onSuccess { _ ->
            effectCalled = true
        }

        val result = withEffect.execute("anything")
        assertTrue(result.isErr)
        assertFalse(effectCalled)
    }

    // === onFailure tests ===

    @Test
    fun onFailureRunsEffectOnError() = runTest {
        var effectCalled = false
        var capturedMessage = ""

        val failingCommand = testCommand<String, Int>("test.comp.string.fail") { _ ->
            Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Test error"))
        }

        val withEffect = failingCommand.onFailure { error ->
            effectCalled = true
            capturedMessage = error.message.defaultMessage
        }

        val result = withEffect.execute("anything")
        assertTrue(result.isErr)
        assertTrue(effectCalled)
        assertTrue(capturedMessage.contains("Test error"))
    }

    @Test
    fun onFailureDoesNotRunOnSuccess() = runTest {
        var effectCalled = false

        val successCommand = testCommand<String, Int>("test.comp.string.int") { str ->
            Ok(str.length)
        }

        val withEffect = successCommand.onFailure { _ ->
            effectCalled = true
        }

        val result = withEffect.execute("hello")
        assertTrue(result.isOk)
        assertFalse(effectCalled)
    }

    // === Composition combination tests ===

    @Test
    fun compositionFunctionsCanBeCombined() = runTest {
        val baseCommand = testCommand<String, Int>("test.comp.string.int") { str ->
            Ok(str.length)
        }

        var successEffectCalled = false
        var failureEffectCalled = false

        val composedCommand = baseCommand
            .mapOutputSimple<String, Int, Int, IdkError> { it * 2 }
            .filterOutput(
                predicate = { it >= 8 },
                onFailure = { IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Too small: $it") }
            )
            .onSuccess { _ -> successEffectCalled = true }
            .onFailure { _ -> failureEffectCalled = true }

        // "hello" -> 5 -> 10 -> passes filter (>= 8)
        val successResult = composedCommand.execute("hello")
        assertTrue(successResult.isOk)
        assertEquals(10, successResult.value)
        assertTrue(successEffectCalled)
        assertFalse(failureEffectCalled)

        // Reset for second test
        successEffectCalled = false
        failureEffectCalled = false

        // "hi" -> 2 -> 4 -> fails filter (< 8)
        val failResult = composedCommand.execute("hi")
        assertTrue(failResult.isErr)
        assertFalse(successEffectCalled)
        assertTrue(failureEffectCalled)
    }

    @Test
    fun mapInputAndMapOutputCanBeCombined() = runTest {
        // Original: Int -> String
        val intCommand = testCommand<Int, String>("test.comp.int.string") { num ->
            Ok("Value: $num")
        }

        // Adapted: String -> Int (via mapping on both ends)
        val adaptedCommand = intCommand
            .mapInputSimple<String, Int, String, IdkError> { it.length }
            .mapOutputSimple<String, String, Int, IdkError> { it.length }

        val result = adaptedCommand.execute("hello")
        assertTrue(result.isOk)
        // "hello" -> 5 -> "Value: 5" -> 8 (8 characters in "Value: 5")
        assertEquals(8, result.value)
    }
}

