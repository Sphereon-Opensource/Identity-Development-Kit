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

package com.sphereon.ui.prompt.coordinator

import com.sphereon.ui.prompt.core.NfcPromptRequest
import com.sphereon.ui.prompt.core.PromptId
import com.sphereon.ui.prompt.core.PromptPresentationHint
import com.sphereon.ui.prompt.core.PromptPriority
import com.sphereon.ui.prompt.core.PromptRequest
import com.sphereon.ui.prompt.core.PromptState
import com.sphereon.ui.prompt.presenter.ForegroundState
import com.sphereon.ui.prompt.presenter.PromptPresenter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PromptSchedulerTest {
    private val testPresenter =
        object : PromptPresenter {
            private val _foregroundState = MutableStateFlow(ForegroundState.FOREGROUND)
            override val foregroundState: StateFlow<ForegroundState> = _foregroundState
            override val canPresentPrompts: Boolean = true

            override fun onForeground() {
                _foregroundState.value = ForegroundState.FOREGROUND
            }

            override fun onBackground() {
                _foregroundState.value = ForegroundState.BACKGROUND
            }

            override fun shouldDeferPrompt(request: PromptRequest): Boolean = false
        }

    @Test
    fun scheduleReturnsImmediateWhenNotDeferred() =
        runTest {
            val scheduler = PromptSchedulerImpl(testPresenter)
            val request =
                NfcPromptRequest(
                    title = "Test",
                    initialMessage = "Testing",
                )

            val result = scheduler.schedule(request)

            assertTrue(result == ScheduleResult.Immediate)
            assertEquals(request.id, scheduler.currentPromptId)
        }

    @Test
    fun scheduleReturnsDeferredWhenShouldDefer() =
        runTest {
            val deferringPresenter =
                object : PromptPresenter {
                    private val _foregroundState = MutableStateFlow(ForegroundState.FOREGROUND)
                    override val foregroundState: StateFlow<ForegroundState> = _foregroundState
                    override val canPresentPrompts: Boolean = true

                    override fun onForeground() {}

                    override fun onBackground() {}

                    override fun shouldDeferPrompt(request: PromptRequest): Boolean = true
                }

            val scheduler = PromptSchedulerImpl(deferringPresenter)
            val request =
                NfcPromptRequest(
                    title = "Test",
                    initialMessage = "Testing",
                )

            val result = scheduler.schedule(request)

            assertTrue(result == ScheduleResult.Deferred)
            assertNull(scheduler.currentPromptId)
            assertEquals(1, scheduler.queueSize)
        }

    @Test
    fun secondPromptIsDeferredWhenFirstIsActive() =
        runTest {
            val scheduler = PromptSchedulerImpl(testPresenter)

            val lowPriorityRequest =
                NfcPromptRequest(
                    title = "Low",
                    initialMessage = "Testing",
                    presentationHint = PromptPresentationHint(priority = PromptPriority.LOW),
                )

            val highPriorityRequest =
                NfcPromptRequest(
                    title = "High",
                    initialMessage = "Testing",
                    presentationHint = PromptPresentationHint(priority = PromptPriority.HIGH),
                )

            val result1 = scheduler.schedule(lowPriorityRequest)
            val result2 = scheduler.schedule(highPriorityRequest)

            // First one is immediate, second is deferred (since first is still active)
            assertTrue(result1 == ScheduleResult.Immediate)
            assertTrue(result2 == ScheduleResult.Deferred)
            assertEquals(lowPriorityRequest.id, scheduler.currentPromptId)
            assertEquals(1, scheduler.queueSize)
        }

    @Test
    fun releaseRemovesFromCurrent() =
        runTest {
            val scheduler = PromptSchedulerImpl(testPresenter)
            val request =
                NfcPromptRequest(
                    title = "Test",
                    initialMessage = "Testing",
                )

            scheduler.schedule(request)
            scheduler.release(request.id, PromptState.SUCCESS)

            assertNull(scheduler.currentPromptId)
        }

    @Test
    fun onReleaseCallbackIsInvoked() =
        runTest {
            val scheduler = PromptSchedulerImpl(testPresenter)
            var releasedId: PromptId? = null
            var releasedState: PromptState? = null

            scheduler.setOnRelease { id, state ->
                releasedId = id
                releasedState = state
            }

            val request =
                NfcPromptRequest(
                    title = "Test",
                    initialMessage = "Testing",
                )

            scheduler.schedule(request)
            scheduler.release(request.id, PromptState.CANCELLED)

            assertEquals(request.id, releasedId)
            assertEquals(PromptState.CANCELLED, releasedState)
        }

    @Test
    fun queueSizeTracksCorrectly() =
        runTest {
            val deferringPresenter =
                object : PromptPresenter {
                    private val _foregroundState = MutableStateFlow(ForegroundState.FOREGROUND)
                    override val foregroundState: StateFlow<ForegroundState> = _foregroundState
                    override val canPresentPrompts: Boolean = true

                    override fun onForeground() {}

                    override fun onBackground() {}

                    override fun shouldDeferPrompt(request: PromptRequest): Boolean = true
                }

            val scheduler = PromptSchedulerImpl(deferringPresenter)

            val request1 = NfcPromptRequest(title = "Test1", initialMessage = "Testing")
            val request2 = NfcPromptRequest(title = "Test2", initialMessage = "Testing")

            scheduler.schedule(request1)
            scheduler.schedule(request2)

            assertEquals(2, scheduler.queueSize)
        }

    @Test
    fun releasePresentsNextDeferredPrompt() =
        runTest {
            val scheduler = PromptSchedulerImpl(testPresenter)

            val request1 = NfcPromptRequest(title = "First", initialMessage = "Testing")
            val request2 = NfcPromptRequest(title = "Second", initialMessage = "Testing")

            scheduler.schedule(request1)
            scheduler.schedule(request2) // Gets deferred

            assertEquals(request1.id, scheduler.currentPromptId)
            assertEquals(1, scheduler.queueSize)

            // Release the first one
            scheduler.release(request1.id, PromptState.SUCCESS)

            // Second should now be current
            assertEquals(request2.id, scheduler.currentPromptId)
            assertEquals(0, scheduler.queueSize)
        }

    @Test
    fun releaseDeferredPromptInvokesCallback() =
        runTest {
            val deferringPresenter =
                object : PromptPresenter {
                    private val _foregroundState = MutableStateFlow(ForegroundState.FOREGROUND)
                    override val foregroundState: StateFlow<ForegroundState> = _foregroundState
                    override val canPresentPrompts: Boolean = true

                    override fun onForeground() {}

                    override fun onBackground() {}

                    override fun shouldDeferPrompt(request: PromptRequest): Boolean = true
                }

            val scheduler = PromptSchedulerImpl(deferringPresenter)
            var releasedId: PromptId? = null
            var releasedState: PromptState? = null

            scheduler.setOnRelease { id, state ->
                releasedId = id
                releasedState = state
            }

            val request = NfcPromptRequest(title = "Test", initialMessage = "Testing")

            scheduler.schedule(request) // Gets deferred
            assertEquals(1, scheduler.queueSize)

            // Release the deferred prompt
            scheduler.release(request.id, PromptState.CANCELLED)

            assertEquals(request.id, releasedId)
            assertEquals(PromptState.CANCELLED, releasedState)
            assertEquals(0, scheduler.queueSize)
        }

    @Test
    fun tryPresentNextPresentsWhenNoCurrentPrompt() =
        runTest {
            // Create a presenter that initially defers, then allows
            var shouldDefer = true
            val toggleablePresenter =
                object : PromptPresenter {
                    private val _foregroundState = MutableStateFlow(ForegroundState.FOREGROUND)
                    override val foregroundState: StateFlow<ForegroundState> = _foregroundState
                    override val canPresentPrompts: Boolean = true

                    override fun onForeground() {}

                    override fun onBackground() {}

                    override fun shouldDeferPrompt(request: PromptRequest): Boolean = shouldDefer
                }

            val scheduler = PromptSchedulerImpl(toggleablePresenter)
            val request = NfcPromptRequest(title = "Test", initialMessage = "Testing")

            // Schedule while deferring
            scheduler.schedule(request)
            assertEquals(1, scheduler.queueSize)
            assertNull(scheduler.currentPromptId)

            // Now allow presenting
            shouldDefer = false

            // Try to present next
            scheduler.tryPresentNext()

            // Should now be current
            assertEquals(request.id, scheduler.currentPromptId)
            assertEquals(0, scheduler.queueSize)
        }

    @Test
    fun tryPresentNextDoesNothingWhenPromptActive() =
        runTest {
            val scheduler = PromptSchedulerImpl(testPresenter)

            val request1 = NfcPromptRequest(title = "First", initialMessage = "Testing")
            val request2 = NfcPromptRequest(title = "Second", initialMessage = "Testing")

            scheduler.schedule(request1)
            scheduler.schedule(request2) // Gets deferred

            assertEquals(request1.id, scheduler.currentPromptId)
            assertEquals(1, scheduler.queueSize)

            // Try to present next - should do nothing since request1 is active
            scheduler.tryPresentNext()

            // Still the same
            assertEquals(request1.id, scheduler.currentPromptId)
            assertEquals(1, scheduler.queueSize)
        }

    @Test
    fun deferredPromptsArePresentedInPriorityOrder() =
        runTest {
            // Create a presenter that initially defers all, then allows
            var shouldDefer = true
            val toggleablePresenter =
                object : PromptPresenter {
                    private val _foregroundState = MutableStateFlow(ForegroundState.FOREGROUND)
                    override val foregroundState: StateFlow<ForegroundState> = _foregroundState
                    override val canPresentPrompts: Boolean = true

                    override fun onForeground() {}

                    override fun onBackground() {}

                    override fun shouldDeferPrompt(request: PromptRequest): Boolean = shouldDefer
                }

            val scheduler = PromptSchedulerImpl(toggleablePresenter)

            // Schedule low priority first, then high priority
            val lowPriorityRequest =
                NfcPromptRequest(
                    title = "Low",
                    initialMessage = "Testing",
                    presentationHint = PromptPresentationHint(priority = PromptPriority.LOW),
                )
            val highPriorityRequest =
                NfcPromptRequest(
                    title = "High",
                    initialMessage = "Testing",
                    presentationHint = PromptPresentationHint(priority = PromptPriority.HIGH),
                )

            scheduler.schedule(lowPriorityRequest)
            scheduler.schedule(highPriorityRequest)

            assertEquals(2, scheduler.queueSize)
            assertNull(scheduler.currentPromptId)

            // Now allow presenting
            shouldDefer = false
            scheduler.tryPresentNext()

            // High priority should be presented first (lower order number = higher priority)
            assertEquals(highPriorityRequest.id, scheduler.currentPromptId)
            assertEquals(1, scheduler.queueSize)
        }

    @Test
    fun deferredPromptStaysInQueueIfStillShouldDefer() =
        runTest {
            // Always defer
            val alwaysDeferPresenter =
                object : PromptPresenter {
                    private val _foregroundState = MutableStateFlow(ForegroundState.FOREGROUND)
                    override val foregroundState: StateFlow<ForegroundState> = _foregroundState
                    override val canPresentPrompts: Boolean = true

                    override fun onForeground() {}

                    override fun onBackground() {}

                    override fun shouldDeferPrompt(request: PromptRequest): Boolean = true
                }

            val scheduler = PromptSchedulerImpl(alwaysDeferPresenter)
            val request = NfcPromptRequest(title = "Test", initialMessage = "Testing")

            scheduler.schedule(request)
            assertEquals(1, scheduler.queueSize)

            // Try to present - should fail since it still should defer
            scheduler.tryPresentNext()

            // Still in queue
            assertEquals(1, scheduler.queueSize)
            assertNull(scheduler.currentPromptId)
        }
}
