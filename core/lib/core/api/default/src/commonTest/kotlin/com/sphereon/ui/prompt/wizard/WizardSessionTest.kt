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

package com.sphereon.ui.prompt.wizard

import com.sphereon.ui.prompt.core.CancelReason
import com.sphereon.ui.prompt.core.PromptId
import com.sphereon.ui.prompt.core.PromptOutcome
import com.sphereon.ui.prompt.core.PromptResponse
import com.sphereon.ui.prompt.core.PromptState
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WizardSessionTest {
    // Test step implementation
    data class TestStep(
        override val id: String,
        override val isFinal: Boolean = false,
        override val data: Any? = null,
    ) : WizardStep

    // Test response implementation
    data class TestResponse(
        override val promptId: PromptId,
        override val state: PromptState = PromptState.SUCCESS,
        val result: String,
    ) : PromptResponse

    @Test
    fun goToAddsStepToHistory() {
        val session = WizardSessionImpl<TestStep, TestResponse>()
        val step = TestStep(id = "step1")

        session.goTo(step)

        assertEquals(1, session.stepHistory.size)
        assertEquals(step, session.currentStep.value)
    }

    @Test
    fun goToSameStepDoesNotDuplicate() {
        val session = WizardSessionImpl<TestStep, TestResponse>()
        val step = TestStep(id = "step1")

        session.goTo(step)
        session.goTo(step) // Same step again

        assertEquals(1, session.stepHistory.size)
    }

    @Test
    fun backNavigatesToPreviousStep() {
        val session = WizardSessionImpl<TestStep, TestResponse>()
        val step1 = TestStep(id = "step1")
        val step2 = TestStep(id = "step2")

        session.goTo(step1)
        session.goTo(step2)

        val previous = session.back()

        assertEquals(step1, previous)
        assertEquals(step1, session.currentStep.value)
    }

    @Test
    fun backReturnsNullAtBeginning() {
        val session = WizardSessionImpl<TestStep, TestResponse>()
        val step = TestStep(id = "step1")

        session.goTo(step)

        val previous = session.back()

        assertNull(previous)
    }

    @Test
    fun forwardNavigatesToNextStep() {
        val session = WizardSessionImpl<TestStep, TestResponse>()
        val step1 = TestStep(id = "step1")
        val step2 = TestStep(id = "step2")

        session.goTo(step1)
        session.goTo(step2)
        session.back()

        val next = session.forward()

        assertEquals(step2, next)
        assertEquals(step2, session.currentStep.value)
    }

    @Test
    fun forwardReturnsNullAtEnd() {
        val session = WizardSessionImpl<TestStep, TestResponse>()
        val step = TestStep(id = "step1")

        session.goTo(step)

        val next = session.forward()

        assertNull(next)
    }

    @Test
    fun hasPreviousIsFalseAtBeginning() {
        val session = WizardSessionImpl<TestStep, TestResponse>()
        val step = TestStep(id = "step1")

        session.goTo(step)

        assertFalse(session.hasPrevious)
    }

    @Test
    fun hasPreviousIsTrueWithHistory() {
        val session = WizardSessionImpl<TestStep, TestResponse>()
        val step1 = TestStep(id = "step1")
        val step2 = TestStep(id = "step2")

        session.goTo(step1)
        session.goTo(step2)

        assertTrue(session.hasPrevious)
    }

    @Test
    fun completeResolvesAwait() =
        runTest {
            val session = WizardSessionImpl<TestStep, TestResponse>()
            val promptId = session.id
            val response = TestResponse(promptId = promptId, result = "success")

            val outcome = async { session.await() }
            session.complete(response)

            val result = outcome.await()
            assertTrue(result is PromptOutcome.Success)
            assertEquals(response, (result as PromptOutcome.Success).response)
        }

    @Test
    fun cancelResolvesAwaitWithCancelled() =
        runTest {
            val session = WizardSessionImpl<TestStep, TestResponse>()

            val outcome = async { session.await() }
            session.cancel(CancelReason.USER)

            val result = outcome.await()
            assertTrue(result is PromptOutcome.Cancelled)
            assertEquals(CancelReason.USER, (result as PromptOutcome.Cancelled).reason)
        }

    @Test
    fun initialStepIsSet() {
        val step = TestStep(id = "initial")
        val session = WizardSessionImpl<TestStep, TestResponse>(initialStep = step)

        assertEquals(step, session.currentStep.value)
        assertEquals(1, session.stepHistory.size)
    }

    @Test
    fun resetClearsHistory() {
        val session = WizardSessionImpl<TestStep, TestResponse>()
        val step1 = TestStep(id = "step1")
        val step2 = TestStep(id = "step2")

        session.goTo(step1)
        session.goTo(step2)
        session.reset()

        assertTrue(session.stepHistory.isEmpty())
        assertNull(session.currentStep.value)
    }

    @Test
    fun clearForwardHistoryRemovesFutureSteps() {
        val session = WizardSessionImpl<TestStep, TestResponse>()
        val step1 = TestStep(id = "step1")
        val step2 = TestStep(id = "step2")
        val step3 = TestStep(id = "step3")

        session.goTo(step1)
        session.goTo(step2)
        session.goTo(step3)
        session.back() // Now at step2
        session.back() // Now at step1

        session.clearForwardHistory()

        assertEquals(1, session.stepHistory.size)
        assertEquals(step1, session.stepHistory[0])
        assertFalse(session.hasNext)
    }

    @Test
    fun clearForwardHistoryDoesNothingAtEnd() {
        val session = WizardSessionImpl<TestStep, TestResponse>()
        val step1 = TestStep(id = "step1")
        val step2 = TestStep(id = "step2")

        session.goTo(step1)
        session.goTo(step2)

        session.clearForwardHistory()

        // Should not change anything since we're at the end
        assertEquals(2, session.stepHistory.size)
    }

    @Test
    fun goToNextStepInHistoryUsesOptimizedPath() {
        val session = WizardSessionImpl<TestStep, TestResponse>()
        val step1 = TestStep(id = "step1")
        val step2 = TestStep(id = "step2")

        session.goTo(step1)
        session.goTo(step2)
        session.back() // Now at step1, step2 is in forward history

        // Go to the same step that's already next in history
        session.goTo(step2)

        // Should reuse existing history entry, not add a duplicate
        assertEquals(2, session.stepHistory.size)
        assertEquals(step2, session.currentStep.value)
        assertEquals(1, session.currentIndex)
    }

    @Test
    fun cancelWithDefaultReasonUsesUser() =
        runTest {
            val session = WizardSessionImpl<TestStep, TestResponse>()

            val outcome = async { session.await() }
            session.cancel() // Uses default CancelReason.USER

            val result = outcome.await()
            assertTrue(result is PromptOutcome.Cancelled)
            assertEquals(CancelReason.USER, (result as PromptOutcome.Cancelled).reason)
        }

    @Test
    fun currentIndexReflectsPosition() {
        val session = WizardSessionImpl<TestStep, TestResponse>()

        assertEquals(-1, session.currentIndex) // No steps yet

        val step1 = TestStep(id = "step1")
        session.goTo(step1)
        assertEquals(0, session.currentIndex)

        val step2 = TestStep(id = "step2")
        session.goTo(step2)
        assertEquals(1, session.currentIndex)

        session.back()
        assertEquals(0, session.currentIndex)
    }
}

// ========== WizardStep Interface Tests ==========

class WizardStepDefaultsTest {
    // Minimal implementation that uses default values
    private class MinimalWizardStep(
        override val id: String,
    ) : WizardStep

    @Test
    fun isFinalDefaultsToFalse() {
        val step = MinimalWizardStep("test")
        assertFalse(step.isFinal)
    }

    @Test
    fun dataDefaultsToNull() {
        val step = MinimalWizardStep("test")
        assertNull(step.data)
    }

    @Test
    fun stepWithCustomIsFinal() {
        val step =
            object : WizardStep {
                override val id = "final-step"
                override val isFinal = true
            }
        assertTrue(step.isFinal)
    }

    @Test
    fun stepWithCustomData() {
        val customData = mapOf("key" to "value")
        val step =
            object : WizardStep {
                override val id = "data-step"
                override val data = customData
            }
        assertEquals(customData, step.data)
    }
}
