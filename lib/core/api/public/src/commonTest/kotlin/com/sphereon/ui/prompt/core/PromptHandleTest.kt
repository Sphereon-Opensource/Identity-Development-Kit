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
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class PromptHandleTest {

    // Test request implementation
    data class TestRequest(
        override val id: PromptId = PromptId.random(),
        override val title: String = "Test",
        override val subtitle: String? = null,
        override val presentationHint: PromptPresentationHint = PromptPresentationHint.DEFAULT
    ) : SinglePromptRequest

    // Test response implementation
    data class TestResponse(
        override val promptId: PromptId,
        override val state: PromptState = PromptState.SUCCESS,
        val data: String = "test"
    ) : PromptResponse

    @Test
    fun handleStartsInPendingState() {
        val request = TestRequest()
        val handle = PromptHandleImpl<TestRequest, TestResponse>(request.id, request)

        assertEquals(PromptState.PENDING, handle.state)
        assertTrue(handle.isActive)
    }

    @Test
    fun markActiveChangesStateToPending() {
        val request = TestRequest()
        val handle = PromptHandleImpl<TestRequest, TestResponse>(request.id, request)

        handle.markActive()

        assertEquals(PromptState.ACTIVE, handle.state)
        assertTrue(handle.isActive)
    }

    @Test
    fun markActiveOnlyWorksFromPending() {
        val request = TestRequest()
        val handle = PromptHandleImpl<TestRequest, TestResponse>(request.id, request)
        handle.markActive()
        handle.cancel(CancelReason.USER)

        // Try to mark active again after cancellation
        handle.markActive()

        // Should still be cancelled
        assertEquals(PromptState.CANCELLED, handle.state)
    }

    @Test
    fun tryCompleteChangesStateToSuccess() = runTest {
        val request = TestRequest()
        val handle = PromptHandleImpl<TestRequest, TestResponse>(request.id, request)
        handle.markActive()

        val response = TestResponse(promptId = request.id)
        val result = handle.tryComplete(response)

        assertTrue(result)
        assertEquals(PromptState.SUCCESS, handle.state)
        assertFalse(handle.isActive)
    }

    @Test
    fun tryCompleteFailsWhenNotActive() {
        val request = TestRequest()
        val handle = PromptHandleImpl<TestRequest, TestResponse>(request.id, request)
        handle.cancel(CancelReason.USER)

        val response = TestResponse(promptId = request.id)
        val result = handle.tryComplete(response)

        assertFalse(result)
    }

    @Test
    fun cancelChangesStateToCancelled() {
        val request = TestRequest()
        val handle = PromptHandleImpl<TestRequest, TestResponse>(request.id, request)
        handle.markActive()

        val result = handle.cancel(CancelReason.USER)

        assertTrue(result)
        assertEquals(PromptState.CANCELLED, handle.state)
        assertFalse(handle.isActive)
    }

    @Test
    fun cancelFailsWhenAlreadyCompleted() = runTest {
        val request = TestRequest()
        val handle = PromptHandleImpl<TestRequest, TestResponse>(request.id, request)
        handle.markActive()
        handle.tryComplete(TestResponse(promptId = request.id))

        val result = handle.cancel(CancelReason.USER)

        assertFalse(result)
        assertEquals(PromptState.SUCCESS, handle.state)
    }

    @Test
    fun completeWithErrorChangesStateToError() {
        val request = TestRequest()
        val handle = PromptHandleImpl<TestRequest, TestResponse>(request.id, request)
        handle.markActive()

        val error = IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Test error")
        val result = handle.completeWithError(error)

        assertTrue(result)
        assertEquals(PromptState.ERROR, handle.state)
        assertFalse(handle.isActive)
    }

    @Test
    fun completeWithErrorFailsWhenNotActive() {
        val request = TestRequest()
        val handle = PromptHandleImpl<TestRequest, TestResponse>(request.id, request)
        handle.cancel(CancelReason.USER)

        val error = IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Test error")
        val result = handle.completeWithError(error)

        assertFalse(result)
    }

    @Test
    fun completeWithTimeoutChangesStateToTimedOut() {
        val request = TestRequest()
        val handle = PromptHandleImpl<TestRequest, TestResponse>(request.id, request)
        handle.markActive()

        val result = handle.completeWithTimeout()

        assertTrue(result)
        assertEquals(PromptState.TIMED_OUT, handle.state)
        assertFalse(handle.isActive)
    }

    @Test
    fun completeWithTimeoutFailsWhenNotActive() {
        val request = TestRequest()
        val handle = PromptHandleImpl<TestRequest, TestResponse>(request.id, request)
        handle.cancel(CancelReason.USER)

        val result = handle.completeWithTimeout()

        assertFalse(result)
    }

    @Test
    fun awaitReturnsSuccessOutcome() = runTest {
        val request = TestRequest()
        val handle = PromptHandleImpl<TestRequest, TestResponse>(request.id, request)
        handle.markActive()

        val response = TestResponse(promptId = request.id, data = "result")

        val outcome = async { handle.await() }
        handle.tryComplete(response)

        val result = outcome.await()
        assertTrue(result is PromptOutcome.Success)
        assertEquals(response, (result as PromptOutcome.Success).response)
    }

    @Test
    fun awaitReturnsCancelledOutcome() = runTest {
        val request = TestRequest()
        val handle = PromptHandleImpl<TestRequest, TestResponse>(request.id, request)
        handle.markActive()

        val outcome = async { handle.await() }
        handle.cancel(CancelReason.USER)

        val result = outcome.await()
        assertTrue(result is PromptOutcome.Cancelled)
        assertEquals(CancelReason.USER, (result as PromptOutcome.Cancelled).reason)
    }

    @Test
    fun awaitReturnsErrorOutcome() = runTest {
        val request = TestRequest()
        val handle = PromptHandleImpl<TestRequest, TestResponse>(request.id, request)
        handle.markActive()

        val error = IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Test error")

        val outcome = async { handle.await() }
        handle.completeWithError(error)

        val result = outcome.await()
        assertTrue(result is PromptOutcome.Error)
        assertEquals(error, (result as PromptOutcome.Error).error)
    }

    @Test
    fun awaitWithTimeoutReturnsTimedOutOnTimeout() = runTest {
        val request = TestRequest()
        val handle = PromptHandleImpl<TestRequest, TestResponse>(request.id, request)
        handle.markActive()

        val result = handle.await(1.milliseconds)

        assertTrue(result is PromptOutcome.TimedOut)
        assertEquals(PromptState.TIMED_OUT, handle.state)
    }

    @Test
    fun requestPropertyReturnsOriginalRequest() {
        val request = TestRequest(title = "My Request")
        val handle = PromptHandleImpl<TestRequest, TestResponse>(request.id, request)

        assertEquals(request, handle.request)
        assertEquals("My Request", handle.request.title)
    }

    @Test
    fun idPropertyReturnsCorrectId() {
        val id = PromptId.random()
        val request = TestRequest(id = id)
        val handle = PromptHandleImpl<TestRequest, TestResponse>(id, request)

        assertEquals(id, handle.id)
    }
}
