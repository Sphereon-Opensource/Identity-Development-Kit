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

package com.sphereon.ui.prompt.wizard

import com.sphereon.ui.prompt.core.CancelReason
import com.sphereon.ui.prompt.core.PromptId
import com.sphereon.ui.prompt.core.PromptOutcome
import com.sphereon.ui.prompt.core.PromptResponse
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Default implementation of WizardSession.
 *
 * @param Step The type of wizard step.
 * @param Res The type of final response.
 * @param id The unique identifier for this session.
 * @param initialStep Optional initial step.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("WizardSessionImpl", exact = true)
class WizardSessionImpl<Step : WizardStep, Res : PromptResponse>(
    override val id: PromptId = PromptId.random(),
    initialStep: Step? = null
) : WizardSession<Step, Res> {

    private val _history = mutableListOf<Step>()
    private var _currentIndex = -1
    private val _currentStep = MutableStateFlow<Step?>(null)
    private val deferred = CompletableDeferred<PromptOutcome<Res>>()

    override val currentStep: StateFlow<Step?> = _currentStep.asStateFlow()

    override val stepHistory: List<Step>
        get() = _history.toList()

    override val hasPrevious: Boolean
        get() = _currentIndex > 0

    override val hasNext: Boolean
        get() = _currentIndex < _history.size - 1

    override val currentIndex: Int
        get() = _currentIndex

    init {
        initialStep?.let { goTo(it) }
    }

    override fun goTo(step: Step) {
        // If identical to current, do nothing
        if (step == _currentStep.value) return

        // If we have a next and it equals what we're going to, just move forward
        if (hasNext && _history[_currentIndex + 1] == step) {
            _currentIndex++
            _currentStep.value = step
            return
        }

        // Otherwise, append and advance
        _history.add(step)
        _currentIndex = _history.lastIndex
        _currentStep.value = step
    }

    override fun back(): Step? {
        if (!hasPrevious) return null

        _currentIndex--
        val previous = _history[_currentIndex]
        _currentStep.value = previous
        return previous
    }

    override fun forward(): Step? {
        if (!hasNext) return null

        _currentIndex++
        val next = _history[_currentIndex]
        _currentStep.value = next
        return next
    }

    override fun complete(response: Res) {
        deferred.complete(PromptOutcome.Success(id, response))
    }

    override fun cancel(reason: CancelReason) {
        deferred.complete(PromptOutcome.Cancelled(id, reason))
    }

    override suspend fun await(): PromptOutcome<Res> = deferred.await()

    /**
     * Resets the wizard to the initial state.
     */
    fun reset() {
        _history.clear()
        _currentIndex = -1
        _currentStep.value = null
    }

    /**
     * Clears forward history from the current position.
     * Useful when the user takes a different path from an earlier step.
     */
    fun clearForwardHistory() {
        if (_currentIndex < _history.size - 1) {
            _history.subList(_currentIndex + 1, _history.size).clear()
        }
    }
}
