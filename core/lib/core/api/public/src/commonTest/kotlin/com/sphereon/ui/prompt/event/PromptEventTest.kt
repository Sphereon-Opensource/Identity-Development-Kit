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

package com.sphereon.ui.prompt.event

import com.sphereon.ui.prompt.core.CancelReason
import com.sphereon.ui.prompt.core.NfcPromptRequest
import com.sphereon.ui.prompt.core.NfcPromptResponse
import com.sphereon.ui.prompt.core.PromptId
import com.sphereon.ui.prompt.core.PromptOutcome
import com.sphereon.ui.prompt.core.PromptState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Clock

class PromptEventTest {
    private val testPromptId = PromptId.random()
    private val testTimestamp = Clock.System.now()

    @Test
    fun requestedEventHasCorrectProperties() {
        val request =
            NfcPromptRequest(
                id = testPromptId,
                title = "Test",
                initialMessage = "Testing",
            )
        val event = PromptEvent.Requested(testPromptId, testTimestamp, request)

        assertEquals(testPromptId, event.promptId)
        assertEquals(testTimestamp, event.timestamp)
        assertEquals(request, event.request)
    }

    @Test
    fun presentedEventHasCorrectProperties() {
        val event = PromptEvent.Presented(testPromptId, testTimestamp)

        assertEquals(testPromptId, event.promptId)
        assertEquals(testTimestamp, event.timestamp)
    }

    @Test
    fun stateUpdatedEventHasCorrectProperties() {
        val event =
            PromptEvent.StateUpdated(
                testPromptId,
                testTimestamp,
                previousState = PromptState.PENDING,
                newState = PromptState.ACTIVE,
            )

        assertEquals(testPromptId, event.promptId)
        assertEquals(testTimestamp, event.timestamp)
        assertEquals(PromptState.PENDING, event.previousState)
        assertEquals(PromptState.ACTIVE, event.newState)
    }

    @Test
    fun stateUpdatedEventCanHaveNullPreviousState() {
        val event =
            PromptEvent.StateUpdated(
                testPromptId,
                testTimestamp,
                previousState = null,
                newState = PromptState.PENDING,
            )

        assertEquals(null, event.previousState)
        assertEquals(PromptState.PENDING, event.newState)
    }

    @Test
    fun completedEventHasCorrectProperties() {
        val outcome = PromptOutcome.Success(testPromptId, NfcPromptResponse.Success(testPromptId))
        val event = PromptEvent.Completed(testPromptId, testTimestamp, outcome)

        assertEquals(testPromptId, event.promptId)
        assertEquals(testTimestamp, event.timestamp)
        assertEquals(outcome, event.outcome)
    }

    @Test
    fun deferredEventHasCorrectProperties() {
        val event = PromptEvent.Deferred(testPromptId, testTimestamp, "App in background")

        assertEquals(testPromptId, event.promptId)
        assertEquals(testTimestamp, event.timestamp)
        assertEquals("App in background", event.reason)
    }

    @Test
    fun respondedEventHasCorrectProperties() {
        val response = NfcPromptResponse.Success(testPromptId)
        val event = PromptEvent.Responded(testPromptId, testTimestamp, response)

        assertEquals(testPromptId, event.promptId)
        assertEquals(testTimestamp, event.timestamp)
        assertEquals(response, event.response)
    }

    @Test
    fun cancelledEventHasCorrectProperties() {
        val event = PromptEvent.Cancelled(testPromptId, testTimestamp, CancelReason.USER)

        assertEquals(testPromptId, event.promptId)
        assertEquals(testTimestamp, event.timestamp)
        assertEquals(CancelReason.USER, event.reason)
    }

    @Test
    fun timedOutEventHasCorrectProperties() {
        val event = PromptEvent.TimedOut(testPromptId, testTimestamp)

        assertEquals(testPromptId, event.promptId)
        assertEquals(testTimestamp, event.timestamp)
    }

    @Test
    fun errorEventHasCorrectProperties() {
        val exception = RuntimeException("Test error")
        val event = PromptEvent.Error(testPromptId, testTimestamp, "Error message", exception)

        assertEquals(testPromptId, event.promptId)
        assertEquals(testTimestamp, event.timestamp)
        assertEquals("Error message", event.message)
        assertEquals(exception, event.exception)
    }

    @Test
    fun errorEventCanHaveNullException() {
        val event = PromptEvent.Error(testPromptId, testTimestamp, "Error message", null)

        assertEquals("Error message", event.message)
        assertEquals(null, event.exception)
    }

    // toEventType tests

    @Test
    fun requestedEventToEventType() {
        val request = NfcPromptRequest(id = testPromptId, title = "Test", initialMessage = "Testing")
        val event = PromptEvent.Requested(testPromptId, testTimestamp, request)

        assertEquals(PromptEventTypes.PROMPT_REQUESTED, event.toEventType())
    }

    @Test
    fun presentedEventToEventType() {
        val event = PromptEvent.Presented(testPromptId, testTimestamp)

        assertEquals(PromptEventTypes.PROMPT_PRESENTED, event.toEventType())
    }

    @Test
    fun stateUpdatedEventToEventType() {
        val event = PromptEvent.StateUpdated(testPromptId, testTimestamp, null, PromptState.ACTIVE)

        assertEquals(PromptEventTypes.PROMPT_STATE_UPDATED, event.toEventType())
    }

    @Test
    fun completedEventToEventType() {
        val outcome = PromptOutcome.Cancelled(testPromptId, CancelReason.USER)
        val event = PromptEvent.Completed(testPromptId, testTimestamp, outcome)

        assertEquals(PromptEventTypes.PROMPT_COMPLETED, event.toEventType())
    }

    @Test
    fun deferredEventToEventType() {
        val event = PromptEvent.Deferred(testPromptId, testTimestamp, "reason")

        assertEquals(PromptEventTypes.PROMPT_DEFERRED, event.toEventType())
    }

    @Test
    fun respondedEventToEventType() {
        val response = NfcPromptResponse.Success(testPromptId)
        val event = PromptEvent.Responded(testPromptId, testTimestamp, response)

        assertEquals(PromptEventTypes.PROMPT_RESPONDED, event.toEventType())
    }

    @Test
    fun cancelledEventToEventType() {
        val event = PromptEvent.Cancelled(testPromptId, testTimestamp, CancelReason.USER)

        assertEquals(PromptEventTypes.PROMPT_CANCELLED, event.toEventType())
    }

    @Test
    fun timedOutEventToEventType() {
        val event = PromptEvent.TimedOut(testPromptId, testTimestamp)

        assertEquals(PromptEventTypes.PROMPT_TIMED_OUT, event.toEventType())
    }

    @Test
    fun errorEventToEventType() {
        val event = PromptEvent.Error(testPromptId, testTimestamp, "Error", null)

        assertEquals(PromptEventTypes.PROMPT_ERROR, event.toEventType())
    }

    // PromptState.toEventType tests

    @Test
    fun pendingStateToEventType() {
        assertEquals(PromptEventTypes.PROMPT_REQUESTED, PromptState.PENDING.toEventType())
    }

    @Test
    fun activeStateToEventType() {
        assertEquals(PromptEventTypes.PROMPT_PRESENTED, PromptState.ACTIVE.toEventType())
    }

    @Test
    fun successStateToEventType() {
        assertEquals(PromptEventTypes.PROMPT_RESPONDED, PromptState.SUCCESS.toEventType())
    }

    @Test
    fun cancelledStateToEventType() {
        assertEquals(PromptEventTypes.PROMPT_CANCELLED, PromptState.CANCELLED.toEventType())
    }

    @Test
    fun timedOutStateToEventType() {
        assertEquals(PromptEventTypes.PROMPT_TIMED_OUT, PromptState.TIMED_OUT.toEventType())
    }

    @Test
    fun errorStateToEventType() {
        assertEquals(PromptEventTypes.PROMPT_ERROR, PromptState.ERROR.toEventType())
    }

    // PromptEventTypes and PromptEventSubsystems tests

    @Test
    fun promptEventSubsystemExists() {
        assertNotNull(PromptEventSubsystems.PROMPT)
        assertEquals("prompt", PromptEventSubsystems.PROMPT.value)
    }

    @Test
    fun allEventTypesExist() {
        assertNotNull(PromptEventTypes.PROMPT_REQUESTED)
        assertNotNull(PromptEventTypes.PROMPT_PRESENTED)
        assertNotNull(PromptEventTypes.PROMPT_STATE_UPDATED)
        assertNotNull(PromptEventTypes.PROMPT_COMPLETED)
        assertNotNull(PromptEventTypes.PROMPT_DEFERRED)
        assertNotNull(PromptEventTypes.PROMPT_RESPONDED)
        assertNotNull(PromptEventTypes.PROMPT_CANCELLED)
        assertNotNull(PromptEventTypes.PROMPT_TIMED_OUT)
        assertNotNull(PromptEventTypes.PROMPT_ERROR)
    }
}
