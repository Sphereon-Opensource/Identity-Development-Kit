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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Clock

class PromptEventBridgeTest {
    @Test
    fun emitSendsEventToSubscribers() =
        runTest {
            val bridge = DefaultPromptEventBridge()
            val promptId = PromptId.random()
            val timestamp = Clock.System.now()
            val event = PromptEvent.Presented(promptId, timestamp)

            val collectedEvents = mutableListOf<PromptEvent>()
            val job =
                launch {
                    bridge.events.take(1).toList(collectedEvents)
                }

            // Give the collector time to start
            testScheduler.advanceUntilIdle()

            bridge.emit(event)
            testScheduler.advanceUntilIdle()
            job.join()

            assertEquals(1, collectedEvents.size)
            assertEquals(event, collectedEvents[0])
        }

    @Test
    fun multipleEventsAreEmitted() =
        runTest {
            val bridge = DefaultPromptEventBridge()
            val promptId = PromptId.random()
            val timestamp = Clock.System.now()

            val events =
                listOf(
                    PromptEvent.Presented(promptId, timestamp),
                    PromptEvent.Cancelled(promptId, timestamp, CancelReason.USER),
                    PromptEvent.TimedOut(promptId, timestamp),
                )

            val collectedEvents = mutableListOf<PromptEvent>()
            val job =
                launch {
                    bridge.events.take(3).toList(collectedEvents)
                }

            testScheduler.advanceUntilIdle()

            events.forEach { bridge.emit(it) }
            testScheduler.advanceUntilIdle()
            job.join()

            assertEquals(3, collectedEvents.size)
            assertEquals(events, collectedEvents)
        }

    @Test
    fun requestedEventCanBeEmitted() =
        runTest {
            val bridge = DefaultPromptEventBridge()
            val promptId = PromptId.random()
            val timestamp = Clock.System.now()
            val request =
                NfcPromptRequest(
                    id = promptId,
                    title = "Test",
                    initialMessage = "Testing",
                )
            val event = PromptEvent.Requested(promptId, timestamp, request)

            val collectedEvents = mutableListOf<PromptEvent>()
            val job =
                launch {
                    bridge.events.take(1).toList(collectedEvents)
                }

            testScheduler.advanceUntilIdle()
            bridge.emit(event)
            testScheduler.advanceUntilIdle()
            job.join()

            assertEquals(1, collectedEvents.size)
            val collected = collectedEvents[0] as PromptEvent.Requested
            assertEquals(request, collected.request)
        }

    @Test
    fun stateUpdatedEventCanBeEmitted() =
        runTest {
            val bridge = DefaultPromptEventBridge()
            val promptId = PromptId.random()
            val timestamp = Clock.System.now()
            val event =
                PromptEvent.StateUpdated(
                    promptId = promptId,
                    timestamp = timestamp,
                    previousState = PromptState.PENDING,
                    newState = PromptState.ACTIVE,
                )

            val collectedEvents = mutableListOf<PromptEvent>()
            val job =
                launch {
                    bridge.events.take(1).toList(collectedEvents)
                }

            testScheduler.advanceUntilIdle()
            bridge.emit(event)
            testScheduler.advanceUntilIdle()
            job.join()

            assertEquals(1, collectedEvents.size)
            val collected = collectedEvents[0] as PromptEvent.StateUpdated
            assertEquals(PromptState.PENDING, collected.previousState)
            assertEquals(PromptState.ACTIVE, collected.newState)
        }

    @Test
    fun completedEventCanBeEmitted() =
        runTest {
            val bridge = DefaultPromptEventBridge()
            val promptId = PromptId.random()
            val timestamp = Clock.System.now()
            val outcome = PromptOutcome.Success(promptId, NfcPromptResponse.Success(promptId))
            val event = PromptEvent.Completed(promptId, timestamp, outcome)

            val collectedEvents = mutableListOf<PromptEvent>()
            val job =
                launch {
                    bridge.events.take(1).toList(collectedEvents)
                }

            testScheduler.advanceUntilIdle()
            bridge.emit(event)
            testScheduler.advanceUntilIdle()
            job.join()

            assertEquals(1, collectedEvents.size)
            val collected = collectedEvents[0] as PromptEvent.Completed
            assertEquals(outcome, collected.outcome)
        }

    @Test
    fun deferredEventCanBeEmitted() =
        runTest {
            val bridge = DefaultPromptEventBridge()
            val promptId = PromptId.random()
            val timestamp = Clock.System.now()
            val event = PromptEvent.Deferred(promptId, timestamp, "App in background")

            val collectedEvents = mutableListOf<PromptEvent>()
            val job =
                launch {
                    bridge.events.take(1).toList(collectedEvents)
                }

            testScheduler.advanceUntilIdle()
            bridge.emit(event)
            testScheduler.advanceUntilIdle()
            job.join()

            assertEquals(1, collectedEvents.size)
            val collected = collectedEvents[0] as PromptEvent.Deferred
            assertEquals("App in background", collected.reason)
        }

    @Test
    fun respondedEventCanBeEmitted() =
        runTest {
            val bridge = DefaultPromptEventBridge()
            val promptId = PromptId.random()
            val timestamp = Clock.System.now()
            val response = NfcPromptResponse.Success(promptId, "tag-data")
            val event = PromptEvent.Responded(promptId, timestamp, response)

            val collectedEvents = mutableListOf<PromptEvent>()
            val job =
                launch {
                    bridge.events.take(1).toList(collectedEvents)
                }

            testScheduler.advanceUntilIdle()
            bridge.emit(event)
            testScheduler.advanceUntilIdle()
            job.join()

            assertEquals(1, collectedEvents.size)
            val collected = collectedEvents[0] as PromptEvent.Responded
            assertEquals(response, collected.response)
        }

    @Test
    fun errorEventCanBeEmitted() =
        runTest {
            val bridge = DefaultPromptEventBridge()
            val promptId = PromptId.random()
            val timestamp = Clock.System.now()
            val exception = RuntimeException("Test error")
            val event = PromptEvent.Error(promptId, timestamp, "Something went wrong", exception)

            val collectedEvents = mutableListOf<PromptEvent>()
            val job =
                launch {
                    bridge.events.take(1).toList(collectedEvents)
                }

            testScheduler.advanceUntilIdle()
            bridge.emit(event)
            testScheduler.advanceUntilIdle()
            job.join()

            assertEquals(1, collectedEvents.size)
            val collected = collectedEvents[0] as PromptEvent.Error
            assertEquals("Something went wrong", collected.message)
            assertEquals(exception, collected.exception)
        }

    @Test
    fun singletonInstanceExists() {
        assertNotNull(DefaultPromptEventBridge.INSTANCE)
    }

    @Test
    fun singletonInstanceCanEmitEvents() =
        runTest {
            val bridge = DefaultPromptEventBridge.INSTANCE
            val promptId = PromptId.random()
            val timestamp = Clock.System.now()
            val event = PromptEvent.Presented(promptId, timestamp)

            val collectedEvents = mutableListOf<PromptEvent>()
            val job =
                launch {
                    bridge.events.take(1).toList(collectedEvents)
                }

            testScheduler.advanceUntilIdle()
            bridge.emit(event)
            testScheduler.advanceUntilIdle()
            job.join()

            assertEquals(1, collectedEvents.size)
        }
}
