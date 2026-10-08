package com.sphereon.core.api

import com.sphereon.core.api.error.ErrorCategory
import com.sphereon.core.api.error.IdkError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResultErgonomicsTest {
    @Test
    fun orErrReturnsOkForNonNull() {
        val value: String? = "hello"
        val result = value.orErr { IdkError.NOT_FOUND_ERROR() }
        assertTrue(result.isOk)
        assertEquals("hello", result.value)
    }

    @Test
    fun orErrReturnsErrForNull() {
        val value: String? = null
        val result = value.orErr { IdkError.NOT_FOUND_ERROR(message = "missing") }
        assertTrue(result.isErr)
        assertEquals("NOT_FOUND_ERROR", result.error.code)
    }

    @Test
    fun ensureReturnsOkWhenTrue() {
        val result = ensure(true) { IdkError.ILLEGAL_ARGUMENT_ERROR() }
        assertTrue(result.isOk)
    }

    @Test
    fun ensureReturnsErrWhenFalse() {
        val result = ensure(false) { IdkError.ILLEGAL_ARGUMENT_ERROR(message = "bad") }
        assertTrue(result.isErr)
        assertEquals(ErrorCategory.VALIDATION, result.error.category)
    }

    @Test
    fun idkResultBindReturnsOk() {
        val result: IdkResult<String, IdkError> =
            idkResult {
                val a = Ok("hello").bind()
                val b = Ok(" world").bind()
                a + b
            }
        assertTrue(result.isOk)
        assertEquals("hello world", result.value)
    }

    @Test
    fun idkResultBindShortCircuitsOnErr() {
        var secondBindCalled = false
        val result: IdkResult<String, IdkError> =
            idkResult {
                val a: String = Err(IdkError.NOT_FOUND_ERROR(message = "gone")).bind()
                secondBindCalled = true
                a
            }
        assertTrue(result.isErr)
        assertEquals("NOT_FOUND_ERROR", result.error.code)
        assertEquals(false, secondBindCalled)
    }

    @Test
    fun idkResultBindPreservesErrorDetails() {
        val originalError = IdkError.ILLEGAL_ARGUMENT_ERROR(message = "bad input")
        val result: IdkResult<Unit, IdkError> =
            idkResult {
                Err(originalError).bind()
            }
        assertTrue(result.isErr)
        assertEquals("bad input", result.error.message.defaultMessage)
        assertEquals(ErrorCategory.VALIDATION, result.error.category)
    }

    @Test
    fun idkResultBindWorksWithMultipleResults() {
        fun step1(): IdkResult<Int, IdkError> = Ok(10)

        fun step2(x: Int): IdkResult<Int, IdkError> = Ok(x * 2)

        fun step3(x: Int): IdkResult<String, IdkError> = Ok("result=$x")

        val result =
            idkResult {
                val a = step1().bind()
                val b = step2(a).bind()
                step3(b).bind()
            }
        assertTrue(result.isOk)
        assertEquals("result=20", result.value)
    }

    @Test
    fun idkResultBindFailsAtMiddleStep() {
        fun step1(): IdkResult<Int, IdkError> = Ok(10)

        fun step2(x: Int): IdkResult<Int, IdkError> = Err(IdkError.INVALID_STATE(message = "step2 failed"))

        fun step3(x: Int): IdkResult<String, IdkError> = Ok("result=$x")

        val result =
            idkResult {
                val a = step1().bind()
                val b = step2(a).bind()
                step3(b).bind()
            }
        assertTrue(result.isErr)
        assertEquals("INVALID_STATE", result.error.code)
        assertEquals(ErrorCategory.CONFLICT, result.error.category)
    }
}
