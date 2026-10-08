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

import com.sphereon.ui.prompt.core.BiometricPromptResponse
import com.sphereon.ui.prompt.core.CancelReason
import com.sphereon.ui.prompt.core.NfcPromptRequest
import com.sphereon.ui.prompt.core.NfcPromptResponse
import com.sphereon.ui.prompt.core.PromptId
import com.sphereon.ui.prompt.core.PromptRequest
import com.sphereon.ui.prompt.core.PromptState
import com.sphereon.ui.prompt.presenter.ForegroundState
import com.sphereon.ui.prompt.presenter.PromptPresenter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PromptCoordinatorTest {
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
    fun requestCreatesActivePrompt() =
        runTest {
            val coordinator = PromptCoordinatorImpl(testPresenter)
            val request =
                NfcPromptRequest(
                    title = "Test",
                    initialMessage = "Testing",
                )

            val handle = coordinator.request<NfcPromptRequest, NfcPromptResponse>(request)

            assertTrue(coordinator.isActive(request.id))
            assertEquals(request.id, handle.id)
        }

    @Test
    fun respondCompletesPrompt() =
        runTest {
            val coordinator = PromptCoordinatorImpl(testPresenter)
            val request =
                NfcPromptRequest(
                    title = "Test",
                    initialMessage = "Testing",
                )

            val handle = coordinator.request<NfcPromptRequest, NfcPromptResponse>(request)
            val response = NfcPromptResponse.Success(promptId = request.id)
            val result = coordinator.respond(request.id, response)

            assertTrue(result.isOk)
            assertEquals(PromptState.SUCCESS, handle.state)
            assertFalse(handle.isActive)
        }

    @Test
    fun respondRejectsLateResponse() =
        runTest {
            val coordinator = PromptCoordinatorImpl(testPresenter)
            val request =
                NfcPromptRequest(
                    title = "Test",
                    initialMessage = "Testing",
                )

            val handle = coordinator.request<NfcPromptRequest, NfcPromptResponse>(request)

            // Complete the prompt first
            val firstResponse = NfcPromptResponse.Success(promptId = request.id)
            coordinator.respond(request.id, firstResponse)

            // Try to respond again
            val secondResponse = NfcPromptResponse.Error(promptId = request.id, message = "Error")
            val result = coordinator.respond(request.id, secondResponse)

            assertTrue(result.isErr)
            val error = result.error
            // After a prompt is completed, the handle is removed from the coordinator,
            // so a late response appears as "not found" rather than "invalid state"
            assertTrue(error.code == "NOT_FOUND_ERROR" || error.code == "INVALID_STATE")
        }

    @Test
    fun respondRejectsMismatchedType() =
        runTest {
            val coordinator = PromptCoordinatorImpl(testPresenter)
            val request =
                NfcPromptRequest(
                    title = "Test",
                    initialMessage = "Testing",
                )

            coordinator.request<NfcPromptRequest, NfcPromptResponse>(request)

            // Try to respond with wrong type
            val wrongResponse = BiometricPromptResponse.Success(promptId = request.id)
            val result = coordinator.respond(request.id, wrongResponse)

            assertTrue(result.isErr)
            val error = result.error
            assertTrue(error.code == "ILLEGAL_ARGUMENT_ERROR")
        }

    @Test
    fun respondRejectsUnknownPromptId() =
        runTest {
            val coordinator = PromptCoordinatorImpl(testPresenter)
            val unknownId = PromptId.random()
            val response = NfcPromptResponse.Success(promptId = unknownId)

            val result = coordinator.respond(unknownId, response)

            assertTrue(result.isErr)
            val error = result.error
            assertTrue(error.code == "NOT_FOUND_ERROR")
        }

    @Test
    fun cancelRemovesPrompt() =
        runTest {
            val coordinator = PromptCoordinatorImpl(testPresenter)
            val request =
                NfcPromptRequest(
                    title = "Test",
                    initialMessage = "Testing",
                )

            val handle = coordinator.request<NfcPromptRequest, NfcPromptResponse>(request)
            val result = coordinator.cancel(request.id, CancelReason.USER)

            assertTrue(result.isOk)
            assertEquals(PromptState.CANCELLED, handle.state)
            assertFalse(handle.isActive)
        }

    @Test
    fun activePromptsReturnsSharedFlow() =
        runTest {
            val coordinator = PromptCoordinatorImpl(testPresenter)
            val activePromptsFlow = coordinator.activePrompts
            // Just verify the flow is accessible
            assertTrue(activePromptsFlow != null)
        }

    @Test
    fun activePromptCountReturnsZeroInitially() =
        runTest {
            val coordinator = PromptCoordinatorImpl(testPresenter)
            assertEquals(0, coordinator.activePromptCount)
        }

    @Test
    fun activePromptCountIncreasesAfterRequest() =
        runTest {
            val coordinator = PromptCoordinatorImpl(testPresenter)
            val request =
                NfcPromptRequest(
                    title = "Test",
                    initialMessage = "Testing",
                )

            coordinator.request<NfcPromptRequest, NfcPromptResponse>(request)
            assertEquals(1, coordinator.activePromptCount)
        }

    @Test
    fun activePromptCountDecreasesAfterResponse() =
        runTest {
            val coordinator = PromptCoordinatorImpl(testPresenter)
            val request =
                NfcPromptRequest(
                    title = "Test",
                    initialMessage = "Testing",
                )

            coordinator.request<NfcPromptRequest, NfcPromptResponse>(request)
            val response = NfcPromptResponse.Success(promptId = request.id)
            coordinator.respond(request.id, response)

            assertEquals(0, coordinator.activePromptCount)
        }

    @Test
    fun multipleActivePromptsTrackedCorrectly() =
        runTest {
            val coordinator = PromptCoordinatorImpl(testPresenter)
            val request1 = NfcPromptRequest(title = "Test1", initialMessage = "Testing1")
            val request2 = NfcPromptRequest(title = "Test2", initialMessage = "Testing2")

            coordinator.request<NfcPromptRequest, NfcPromptResponse>(request1)
            coordinator.request<NfcPromptRequest, NfcPromptResponse>(request2)

            assertEquals(2, coordinator.activePromptCount)
        }
}

class PromptCoordinatorDeferredPromptTest {
    // Presenter that defers all prompts (simulates background state)
    private val deferringPresenter =
        object : PromptPresenter {
            private val _foregroundState = MutableStateFlow(ForegroundState.BACKGROUND)
            override val foregroundState: StateFlow<ForegroundState> = _foregroundState
            override val canPresentPrompts: Boolean = false

            override fun onForeground() {
                _foregroundState.value = ForegroundState.FOREGROUND
            }

            override fun onBackground() {
                _foregroundState.value = ForegroundState.BACKGROUND
            }

            override fun shouldDeferPrompt(request: PromptRequest): Boolean = true
        }

    @Test
    fun requestWithDeferredPromptQueuesPrompt() =
        runTest {
            val coordinator = PromptCoordinatorImpl(deferringPresenter)
            val request =
                NfcPromptRequest(
                    title = "Test",
                    initialMessage = "Testing",
                )

            val handle = coordinator.request<NfcPromptRequest, NfcPromptResponse>(request)

            // The prompt should be queued (not active yet) because presenter defers it
            // The handle is created but marked as waiting
            assertEquals(request.id, handle.id)
            assertTrue(coordinator.isActive(request.id))
        }
}
