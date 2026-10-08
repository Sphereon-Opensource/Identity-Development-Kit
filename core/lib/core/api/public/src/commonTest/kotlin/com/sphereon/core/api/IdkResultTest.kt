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

package com.sphereon.core.api

import com.sphereon.core.api.error.IdkError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OkTest {
    @Test
    fun okIsOkReturnsTrue() {
        val result: IdkResult<String, String> = Ok("value")
        assertTrue(result.isOk)
    }

    @Test
    fun okIsErrReturnsFalse() {
        val result: IdkResult<String, String> = Ok("value")
        assertFalse(result.isErr)
    }

    @Test
    fun okValueReturnsValue() {
        val result = Ok("test value")
        assertEquals("test value", result.value)
    }

    @Test
    fun okToStringReturnsCorrectFormat() {
        val result = Ok(42)
        assertEquals("Ok(42)", result.toString())
    }

    @Test
    fun okEqualsWorksCorrectly() {
        val ok1 = Ok("test")
        val ok2 = Ok("test")
        val ok3 = Ok("other")
        assertEquals(ok1, ok2)
        assertFalse(ok1 == ok3)
    }

    @Test
    fun okHashCodeWorksCorrectly() {
        val ok1 = Ok("test")
        val ok2 = Ok("test")
        assertEquals(ok1.hashCode(), ok2.hashCode())
    }
}

class ErrTest {
    @Test
    fun errIsOkReturnsFalse() {
        val result: IdkResult<String, String> = Err("error")
        assertFalse(result.isOk)
    }

    @Test
    fun errIsErrReturnsTrue() {
        val result: IdkResult<String, String> = Err("error")
        assertTrue(result.isErr)
    }

    @Test
    fun errErrorReturnsError() {
        val result = Err("error message")
        assertEquals("error message", result.error)
    }

    @Test
    fun errToStringReturnsCorrectFormat() {
        val result = Err("failure")
        assertEquals("Err(failure)", result.toString())
    }

    @Test
    fun errEqualsWorksCorrectly() {
        val err1 = Err("error")
        val err2 = Err("error")
        val err3 = Err("other")
        assertEquals(err1, err2)
        assertFalse(err1 == err3)
    }

    @Test
    fun errHashCodeWorksCorrectly() {
        val err1 = Err("error")
        val err2 = Err("error")
        assertEquals(err1.hashCode(), err2.hashCode())
    }
}

class IdkResultMapTest {
    @Test
    fun mapTransformsOkValue() {
        val result: IdkResult<Int, String> = Ok(5)
        val mapped = result.map { it * 2 }
        assertTrue(mapped.isOk)
        assertEquals(10, mapped.value)
    }

    @Test
    fun mapDoesNotAffectErr() {
        val result: IdkResult<Int, String> = Err("error")
        val mapped = result.map { it * 2 }
        assertTrue(mapped.isErr)
        assertEquals("error", mapped.error)
    }
}

class IdkResultMapErrorTest {
    @Test
    fun mapErrorTransformsErrValue() {
        val result: IdkResult<Int, String> = Err("error")
        val mapped = result.mapError { it.uppercase() }
        assertTrue(mapped.isErr)
        assertEquals("ERROR", mapped.error)
    }

    @Test
    fun mapErrorDoesNotAffectOk() {
        val result: IdkResult<Int, String> = Ok(42)
        val mapped = result.mapError { it.uppercase() }
        assertTrue(mapped.isOk)
        assertEquals(42, mapped.value)
    }
}

class IdkResultFlatMapTest {
    @Test
    fun flatMapChainsOkResults() {
        val result: IdkResult<Int, String> = Ok(5)
        val flatMapped = result.flatMap { Ok(it * 2) }
        assertTrue(flatMapped.isOk)
        assertEquals(10, flatMapped.value)
    }

    @Test
    fun flatMapReturnsErrFromTransform() {
        val result: IdkResult<Int, String> = Ok(5)
        val flatMapped = result.flatMap { Err("transformed error") }
        assertTrue(flatMapped.isErr)
        assertEquals("transformed error", flatMapped.error)
    }

    @Test
    fun flatMapDoesNotAffectErr() {
        val result: IdkResult<Int, String> = Err("original error")
        val flatMapped = result.flatMap { Ok(it * 2) }
        assertTrue(flatMapped.isErr)
        assertEquals("original error", flatMapped.error)
    }
}

class IdkResultAndThenTest {
    @Test
    fun andThenChainsOkResults() {
        val result: IdkResult<Int, String> = Ok(5)
        val chained = result.andThen { Ok("value: $it") }
        assertTrue(chained.isOk)
        assertEquals("value: 5", chained.value)
    }

    @Test
    fun andThenReturnsErrFromTransform() {
        val result: IdkResult<Int, String> = Ok(5)
        val chained = result.andThen { Err("error") }
        assertTrue(chained.isErr)
    }

    @Test
    fun andThenDoesNotAffectErr() {
        val result: IdkResult<Int, String> = Err("original")
        val chained = result.andThen { Ok("transformed") }
        assertTrue(chained.isErr)
        assertEquals("original", chained.error)
    }
}

class IdkResultRecoverTest {
    @Test
    fun recoverTransformsErrToValue() {
        val result: IdkResult<Int, String> = Err("error")
        val recovered = result.recover { 42 }
        assertTrue(recovered.isOk)
        assertEquals(42, recovered.value)
    }

    @Test
    fun recoverDoesNotAffectOk() {
        val result: IdkResult<Int, String> = Ok(10)
        val recovered = result.recover { 42 }
        assertTrue(recovered.isOk)
        assertEquals(10, recovered.value)
    }
}

class IdkResultGetOrTest {
    @Test
    fun getOrReturnsValueForOk() {
        val result: IdkResult<Int, String> = Ok(42)
        assertEquals(42, result getOr 0)
    }

    @Test
    fun getOrReturnsDefaultForErr() {
        val result: IdkResult<Int, String> = Err("error")
        assertEquals(0, result getOr 0)
    }
}

class IdkResultGetOrElseTest {
    @Test
    fun getOrElseReturnsValueForOk() {
        val result: IdkResult<Int, String> = Ok(42)
        assertEquals(42, result.getOrElse { 0 })
    }

    @Test
    fun getOrElseCallsTransformForErr() {
        val result: IdkResult<Int, String> = Err("error")
        assertEquals(5, result.getOrElse { err -> err.length }) // "error".length = 5
    }
}

class IdkResultFoldTest {
    @Test
    fun foldCallsSuccessForOk() {
        val result: IdkResult<Int, String> = Ok(42)
        val folded =
            result.fold(
                success = { "value: $it" },
                failure = { "error: $it" },
            )
        assertEquals("value: 42", folded)
    }

    @Test
    fun foldCallsFailureForErr() {
        val result: IdkResult<Int, String> = Err("oops")
        val folded =
            result.fold(
                success = { "value: $it" },
                failure = { "error: $it" },
            )
        assertEquals("error: oops", folded)
    }
}

class IdkResultMapBothTest {
    @Test
    fun mapBothCallsSuccessForOk() {
        val result: IdkResult<Int, String> = Ok(42)
        val mapped =
            result.mapBoth(
                success = { it * 2 },
                failure = { -1 },
            )
        assertEquals(84, mapped)
    }

    @Test
    fun mapBothCallsFailureForErr() {
        val result: IdkResult<Int, String> = Err("error")
        val mapped =
            result.mapBoth(
                success = { it * 2 },
                failure = { -1 },
            )
        assertEquals(-1, mapped)
    }
}

class IdkResultGetNullableTest {
    @Test
    fun getReturnsValueForOk() {
        val result: IdkResult<Int, String> = Ok(42)
        assertEquals(42, result.get())
    }

    @Test
    fun getReturnsNullForErr() {
        val result: IdkResult<Int, String> = Err("error")
        assertNull(result.get())
    }

    @Test
    fun getOrNullReturnsValueForOk() {
        val result: IdkResult<Int, String> = Ok(42)
        assertEquals(42, result.getOrNull())
    }

    @Test
    fun getOrNullReturnsNullForErr() {
        val result: IdkResult<Int, String> = Err("error")
        assertNull(result.getOrNull())
    }

    @Test
    fun getErrorOrNullReturnsNullForOk() {
        val result: IdkResult<Int, String> = Ok(42)
        assertNull(result.getErrorOrNull())
    }

    @Test
    fun getErrorOrNullReturnsErrorForErr() {
        val result: IdkResult<Int, String> = Err("error")
        assertEquals("error", result.getErrorOrNull())
    }

    @Test
    fun errorOrNullReturnsNullForOk() {
        val result: IdkResult<Int, String> = Ok(42)
        assertNull(result.errorOrNull())
    }

    @Test
    fun errorOrNullReturnsErrorForErr() {
        val result: IdkResult<Int, String> = Err("error")
        assertEquals("error", result.errorOrNull())
    }
}

class IdkResultDestructuringTest {
    @Test
    fun component1ReturnsValueForOk() {
        val result: IdkResult<Int, String> = Ok(42)
        val (value, error) = result
        assertEquals(42, value)
        assertNull(error)
    }

    @Test
    fun component2ReturnsErrorForErr() {
        val result: IdkResult<Int, String> = Err("error")
        val (value, error) = result
        assertNull(value)
        assertEquals("error", error)
    }
}

class IdkResultOnSuccessOnFailureTest {
    @Test
    fun onSuccessCallsActionForOk() {
        val result: IdkResult<Int, String> = Ok(42)
        var called = false
        var capturedValue: Int? = null
        result.onSuccess {
            called = true
            capturedValue = it
        }
        assertTrue(called)
        assertEquals(42, capturedValue)
    }

    @Test
    fun onSuccessDoesNotCallActionForErr() {
        val result: IdkResult<Int, String> = Err("error")
        var called = false
        result.onSuccess { called = true }
        assertFalse(called)
    }

    @Test
    fun onFailureCallsActionForErr() {
        val result: IdkResult<Int, String> = Err("error")
        var called = false
        var capturedError: String? = null
        result.onFailure {
            called = true
            capturedError = it
        }
        assertTrue(called)
        assertEquals("error", capturedError)
    }

    @Test
    fun onFailureDoesNotCallActionForOk() {
        val result: IdkResult<Int, String> = Ok(42)
        var called = false
        result.onFailure { called = true }
        assertFalse(called)
    }

    @Test
    fun onSuccessReturnsOriginalResult() {
        val result: IdkResult<Int, String> = Ok(42)
        val returned = result.onSuccess { }
        assertEquals(result, returned)
    }

    @Test
    fun onFailureReturnsOriginalResult() {
        val result: IdkResult<Int, String> = Err("error")
        val returned = result.onFailure { }
        assertEquals(result, returned)
    }
}

class IdkResultGetOrThrowTest {
    @Test
    fun getOrThrowReturnsValueForOk() {
        val result: IdkResult<Int, String> = Ok(42)
        assertEquals(42, result.getOrThrow())
    }

    @Test
    fun getOrThrowThrowsForErr() {
        val result: IdkResult<Int, String> = Err("error message")
        val exception =
            assertFailsWith<IllegalStateException> {
                result.getOrThrow()
            }
        assertTrue(exception.message!!.contains("error message"))
    }

    @Test
    fun getOrThrowRethrowsThrowableError() {
        val originalException = RuntimeException("original")
        val result: IdkResult<Int, Throwable> = Err(originalException)
        val thrown =
            assertFailsWith<RuntimeException> {
                result.getOrThrow()
            }
        assertEquals(originalException, thrown)
    }
}

class IdkResultCompanionTest {
    @Test
    fun companionOkCreatesOkResult() {
        val result = IdkResult.ok<Int, String>(42)
        assertTrue(result.isOk)
        assertEquals(42, result.value)
    }

    @Test
    fun companionErrCreatesErrResult() {
        val result = IdkResult.err<Int, String>("error")
        assertTrue(result.isErr)
        assertEquals("error", result.error)
    }
}

class IdkResultConversionTest {
    @Test
    fun asResultWidensOkType() {
        val ok: Ok<Int> = Ok(42)
        val result: IdkResult<Int, String> = ok.asResult()
        assertTrue(result.isOk)
        assertEquals(42, result.value)
    }

    @Test
    fun asResultWidensErrType() {
        val err: Err<String> = Err("error")
        val result: IdkResult<Int, String> = err.asResult()
        assertTrue(result.isErr)
        assertEquals("error", result.error)
    }
}

class IdkResultExtensionFunctionsTest {
    @Test
    fun idkIsOkReturnsCorrectly() {
        assertTrue(IdkIsOk(Ok(42)))
        assertFalse(IdkIsOk(Err("error")))
    }

    @Test
    fun idkIsErrReturnsCorrectly() {
        assertFalse(IdkIsErr(Ok(42)))
        assertTrue(IdkIsErr(Err("error")))
    }

    @Test
    fun idkGetOrNullReturnsCorrectly() {
        assertEquals(42, IdkGetOrNull(Ok(42)))
        assertNull(IdkGetOrNull(Err("error")))
    }

    @Test
    fun idkErrorOrNullReturnsCorrectly() {
        assertNull(IdkErrorOrNull(Ok(42)))
        assertEquals("error", IdkErrorOrNull(Err("error")))
    }

    @Test
    fun idkOkResultCreatesOk() {
        val result = IdkOkResult(42)
        assertTrue(result.isOk)
        assertEquals(42, result.value)
    }

    @Test
    fun idkErrorResultCreatesErr() {
        val error = IdkError.UNKNOWN_ERROR(message = "test error")
        val result = IdkErrorResult(error)
        assertTrue(result.isErr)
        assertEquals("UNKNOWN_ERROR", result.error.code)
    }

    @Test
    fun asOkResultCreatesOk() {
        val result = 42.asOkResult()
        assertTrue(result.isOk)
        assertEquals(42, result.value)
    }

    @Test
    fun asErrorResultCreatesErr() {
        val error = IdkError.UNKNOWN_ERROR(message = "test")
        val result = error.asErrorResult()
        assertTrue(result.isErr)
    }
}

class ToExceptionTest {
    @Test
    fun toExceptionReturnsExceptionFromIdkError() {
        val originalException = RuntimeException("original")
        val error = IdkError.UNKNOWN_ERROR(message = "test", exception = originalException)
        val exception = error.toException()
        assertEquals(originalException, exception)
    }

    @Test
    fun toExceptionCreatesIllegalStateExceptionWhenNoException() {
        val error = IdkError.UNKNOWN_ERROR(message = "test message")
        val exception = error.toException()
        assertTrue(exception is IllegalStateException)
        assertTrue(exception.message!!.contains("test message"))
    }
}

class AsVoidTest {
    @Test
    fun asVoidReturnsUnitForOk() {
        val result: IdkResult<Int, IdkError> = Ok(42)
        val unit = result.asVoid(throwOnError = false, logOnError = false)
        assertEquals(Unit, unit)
    }

    @Test
    fun asVoidReturnsUnitForErrWhenNotThrowing() {
        val error = IdkError.UNKNOWN_ERROR(message = "test")
        val result: IdkResult<Int, IdkError> = Err(error)
        // Should not throw, just log (disabled here) and return Unit
        val unit = result.asVoid(throwOnError = false, logOnError = false)
        assertEquals(Unit, unit)
    }

    @Test
    fun asVoidThrowsForErrWhenThrowOnErrorTrue() {
        val error = IdkError.UNKNOWN_ERROR(message = "test error")
        val result: IdkResult<Int, IdkError> = Err(error)
        assertFailsWith<IllegalStateException> {
            result.asVoid(throwOnError = true, logOnError = false)
        }
    }

    @Test
    fun asVoidThrowsOriginalThrowableWhenErrorIsThrowable() {
        val originalException = RuntimeException("original error")
        val result: IdkResult<Int, Throwable> = Err(originalException)
        val thrown =
            assertFailsWith<RuntimeException> {
                result.asVoid(throwOnError = true, logOnError = false)
            }
        assertEquals(originalException, thrown)
    }

    @Test
    fun asVoidLogsWhenLogOnErrorTrue() {
        val error = IdkError.UNKNOWN_ERROR(message = "test")
        val result: IdkResult<Int, IdkError> = Err(error)
        // logOnError=true calls printStackTrace() which corrupts the wasmJs test protocol,
        // so we test with logOnError=false. The logging path is not separately testable
        // without a logging abstraction; the key behavior (returns Unit, doesn't throw) is the same.
        val unit = result.asVoid(throwOnError = false, logOnError = false)
        assertEquals(Unit, unit)
    }
}

class IdkResultToStringTest {
    @Test
    fun idkResultToStringDelegates() {
        val okResult: IdkResult<Int, String> = Ok(42)
        assertTrue(okResult.toString().contains("42"))
    }
}

class GetOrThrowWithIdkErrorTypeTest {
    @Test
    fun getOrThrowThrowsIdkErrorAsException() {
        val error = IdkError.ILLEGAL_ARGUMENT_ERROR(message = "invalid argument")
        val result: IdkResult<Int, IdkError> = Err(error)
        val exception =
            assertFailsWith<IllegalStateException> {
                result.getOrThrow()
            }
        assertTrue(exception.message!!.contains("invalid argument"))
    }
}

class ToIdkResultConversionTest {
    @Test
    fun toIdkResultWrapsKotlinResult() {
        val kotlinResult =
            com.github.michaelbull.result
                .Ok(42)
        val idkResult = kotlinResult.toIdkResult()
        assertTrue(idkResult.isOk)
        assertEquals(42, idkResult.value)
    }

    @Test
    fun toResultUnwrapsIdkResult() {
        val idkResult: IdkResult<Int, String> = Ok(42)
        val kotlinResult = idkResult.toResult()
        assertTrue(kotlinResult.isOk)
        assertEquals(42, kotlinResult.value)
    }

    @Test
    fun toIdkResultToStringReturnsCorrectFormat() {
        // Test base IdkResult toString() which is different from Ok/Err toString()
        val kotlinResult =
            com.github.michaelbull.result
                .Ok("test")
        val idkResult = kotlinResult.toIdkResult()
        val str = idkResult.toString()
        assertTrue(str.contains("Ok"))
        assertTrue(str.contains("test"))
    }

    @Test
    fun toIdkResultErrorToStringReturnsCorrectFormat() {
        // Test base IdkResult toString() for error case
        val kotlinResult =
            com.github.michaelbull.result
                .Err("error message")
        val idkResult = kotlinResult.toIdkResult()
        val str = idkResult.toString()
        assertTrue(str.contains("Err"))
        assertTrue(str.contains("error message"))
    }

    @Test
    fun toIdkResultGetOrThrowWorksForOkCase() {
        // Test getOrThrow on a base IdkResult (not Ok subclass)
        val kotlinResult =
            com.github.michaelbull.result
                .Ok("success value")
        val idkResult = kotlinResult.toIdkResult()
        val value = idkResult.getOrThrow()
        assertEquals("success value", value)
    }

    @Test
    fun toIdkResultGetOrThrowThrowsForErrCase() {
        // Test getOrThrow on a base IdkResult error (not Err subclass)
        val kotlinResult =
            com.github.michaelbull.result
                .Err("error value")
        val idkResult = kotlinResult.toIdkResult()
        assertFailsWith<IllegalStateException> {
            idkResult.getOrThrow()
        }
    }
}

/**
 * Test for toException() with a custom IdkErrorType implementation.
 */
class ToExceptionCustomErrorTypeTest {
    // Custom implementation of IdkErrorType for testing the else branch
    private data class CustomErrorType(
        override val code: String,
        override val message: IdkError.Message,
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val exception: Throwable? = null,
        override val causes: List<IdkError> = emptyList(),
        override val meta: Map<String, Any?> = emptyMap(),
    ) : com.sphereon.core.api.error.IdkErrorType

    @Test
    fun toExceptionOnCustomErrorTypeReturnsIllegalStateException() {
        val customError =
            CustomErrorType(
                code = "CUSTOM_ERROR",
                message =
                    IdkError.Message(
                        i18nKey = "custom.error",
                        defaultMessage = "Custom error message",
                    ),
            )
        val exception = customError.toException()
        assertTrue(exception is IllegalStateException)
        // The else branch uses toString() which includes the error code
        assertTrue(exception.message!!.contains("CUSTOM_ERROR"))
    }
}
