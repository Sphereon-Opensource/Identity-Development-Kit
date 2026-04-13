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

package com.sphereon.ui.prompt.core

import com.sphereon.core.api.error.IdkError
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PromptOutcomeTest {

    private val testPromptId = PromptId.random()

    // Test response implementation
    data class TestResponse(
        override val promptId: PromptId,
        override val state: PromptState = PromptState.SUCCESS,
        val data: String
    ) : PromptResponse

    @Test
    fun successHasCorrectState() {
        val response = TestResponse(promptId = testPromptId, data = "test")
        val outcome = PromptOutcome.Success(testPromptId, response)

        assertEquals(PromptState.SUCCESS, outcome.state)
        assertEquals(testPromptId, outcome.promptId)
        assertEquals(response, outcome.response)
    }

    @Test
    fun cancelledHasCorrectState() {
        val outcome = PromptOutcome.Cancelled(testPromptId, CancelReason.USER)

        assertEquals(PromptState.CANCELLED, outcome.state)
        assertEquals(testPromptId, outcome.promptId)
        assertEquals(CancelReason.USER, outcome.reason)
    }

    @Test
    fun cancelledWithDifferentReasons() {
        val userCancelled = PromptOutcome.Cancelled(testPromptId, CancelReason.USER)
        val systemCancelled = PromptOutcome.Cancelled(testPromptId, CancelReason.SYSTEM)
        val superseded = PromptOutcome.Cancelled(testPromptId, CancelReason.SUPERSEDED)
        val background = PromptOutcome.Cancelled(testPromptId, CancelReason.BACKGROUND)

        assertEquals(CancelReason.USER, userCancelled.reason)
        assertEquals(CancelReason.SYSTEM, systemCancelled.reason)
        assertEquals(CancelReason.SUPERSEDED, superseded.reason)
        assertEquals(CancelReason.BACKGROUND, background.reason)
    }

    @Test
    fun timedOutHasCorrectState() {
        val timestamp = Clock.System.now()
        val outcome = PromptOutcome.TimedOut(testPromptId, timestamp)

        assertEquals(PromptState.TIMED_OUT, outcome.state)
        assertEquals(testPromptId, outcome.promptId)
        assertEquals(timestamp, outcome.at)
    }

    @Test
    fun errorHasCorrectState() {
        val error = IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Test error")
        val outcome = PromptOutcome.Error(testPromptId, error)

        assertEquals(PromptState.ERROR, outcome.state)
        assertEquals(testPromptId, outcome.promptId)
        assertEquals(error, outcome.error)
    }

    @Test
    fun isSuccessExtension() {
        val response = TestResponse(promptId = testPromptId, data = "test")
        val success: PromptOutcome<TestResponse> = PromptOutcome.Success(testPromptId, response)
        val cancelled: PromptOutcome<TestResponse> = PromptOutcome.Cancelled(testPromptId, CancelReason.USER)

        assertTrue(success.isSuccess)
        assertFalse(cancelled.isSuccess)
    }

    @Test
    fun isCancelledExtension() {
        val response = TestResponse(promptId = testPromptId, data = "test")
        val success: PromptOutcome<TestResponse> = PromptOutcome.Success(testPromptId, response)
        val cancelled: PromptOutcome<TestResponse> = PromptOutcome.Cancelled(testPromptId, CancelReason.USER)

        assertFalse(success.isCancelled)
        assertTrue(cancelled.isCancelled)
    }

    @Test
    fun isTimedOutExtension() {
        val response = TestResponse(promptId = testPromptId, data = "test")
        val success: PromptOutcome<TestResponse> = PromptOutcome.Success(testPromptId, response)
        val timedOut: PromptOutcome<TestResponse> = PromptOutcome.TimedOut(testPromptId, Clock.System.now())

        assertFalse(success.isTimedOut)
        assertTrue(timedOut.isTimedOut)
    }

    @Test
    fun isErrorExtension() {
        val response = TestResponse(promptId = testPromptId, data = "test")
        val success: PromptOutcome<TestResponse> = PromptOutcome.Success(testPromptId, response)
        val error: PromptOutcome<TestResponse> = PromptOutcome.Error(testPromptId, IdkError.ILLEGAL_ARGUMENT_ERROR())

        assertFalse(success.isError)
        assertTrue(error.isError)
    }

    @Test
    fun responseOrNullReturnsResponseForSuccess() {
        val response = TestResponse(promptId = testPromptId, data = "test")
        val success: PromptOutcome<TestResponse> = PromptOutcome.Success(testPromptId, response)

        assertEquals(response, success.responseOrNull())
    }

    @Test
    fun responseOrNullReturnsNullForNonSuccess() {
        val cancelled: PromptOutcome<TestResponse> = PromptOutcome.Cancelled(testPromptId, CancelReason.USER)
        val timedOut: PromptOutcome<TestResponse> = PromptOutcome.TimedOut(testPromptId, Clock.System.now())
        val error: PromptOutcome<TestResponse> = PromptOutcome.Error(testPromptId, IdkError.ILLEGAL_ARGUMENT_ERROR())

        assertNull(cancelled.responseOrNull())
        assertNull(timedOut.responseOrNull())
        assertNull(error.responseOrNull())
    }

    @Test
    fun foldCallsCorrectCallback() {
        val response = TestResponse(promptId = testPromptId, data = "test")
        val success: PromptOutcome<TestResponse> = PromptOutcome.Success(testPromptId, response)

        val result = success.fold(
            onSuccess = { "success: ${it.data}" },
            onCancelled = { "cancelled" },
            onTimedOut = { "timed out" },
            onError = { "error" }
        )

        assertEquals("success: test", result)
    }

    @Test
    fun foldCallsCancelledCallback() {
        val cancelled: PromptOutcome<TestResponse> = PromptOutcome.Cancelled(testPromptId, CancelReason.USER)

        val result = cancelled.fold(
            onSuccess = { "success" },
            onCancelled = { "cancelled: $it" },
            onTimedOut = { "timed out" },
            onError = { "error" }
        )

        assertEquals("cancelled: USER", result)
    }

    @Test
    fun foldCallsTimedOutCallback() {
        val timestamp = Clock.System.now()
        val timedOut: PromptOutcome<TestResponse> = PromptOutcome.TimedOut(testPromptId, timestamp)

        val result = timedOut.fold(
            onSuccess = { "success" },
            onCancelled = { "cancelled" },
            onTimedOut = { "timed out at $it" },
            onError = { "error" }
        )

        assertEquals("timed out at $timestamp", result)
    }

    @Test
    fun foldCallsErrorCallback() {
        val idkError = IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Test error")
        val error: PromptOutcome<TestResponse> = PromptOutcome.Error(testPromptId, idkError)

        val result = error.fold(
            onSuccess = { "success" },
            onCancelled = { "cancelled" },
            onTimedOut = { "timed out" },
            onError = { "error: ${it.message.defaultMessage}" }
        )

        assertEquals("error: Test error", result)
    }
}
