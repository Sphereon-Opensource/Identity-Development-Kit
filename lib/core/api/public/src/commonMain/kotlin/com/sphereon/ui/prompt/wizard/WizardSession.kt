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
import com.sphereon.core.compat.JsExportCompat
import kotlin.native.ObjCName
import kotlinx.coroutines.flow.StateFlow

/**
 * Represents a step in a wizard flow.
 */
@OptIn(ExperimentalObjCName::class)
@JsExportCompat
@ObjCName("WizardStep", exact = true)
interface WizardStep {
    /**
     * Unique identifier for this step.
     */
    val id: String

    /**
     * Whether this is a terminal step (wizard should complete after this).
     */
    val isFinal: Boolean get() = false

    /**
     * Optional data associated with this step.
     */
    val data: Any? get() = null
}

/**
 * Session for managing a multi-step wizard flow.
 *
 * @param Step The type of wizard step.
 * @param Res The type of final response.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("WizardSession", exact = true)
interface WizardSession<Step : WizardStep, Res : PromptResponse> {
    /**
     * Unique identifier for this wizard session.
     */
    val id: PromptId

    /**
     * Flow of the current wizard step.
     */
    val currentStep: StateFlow<Step?>

    /**
     * All steps in the history.
     */
    val stepHistory: List<Step>

    /**
     * Whether there is a previous step to navigate to.
     */
    val hasPrevious: Boolean

    /**
     * Whether there is a next step in history.
     */
    val hasNext: Boolean

    /**
     * Current index in the step history.
     */
    val currentIndex: Int

    /**
     * Advances to the next step.
     *
     * @param step The next step to navigate to.
     */
    fun goTo(step: Step)

    /**
     * Goes back to the previous step.
     *
     * @return The previous step, or null if at the beginning.
     */
    fun back(): Step?

    /**
     * Goes forward to the next step in history.
     *
     * @return The next step, or null if at the end.
     */
    fun forward(): Step?

    /**
     * Completes the wizard with a response.
     *
     * @param response The final response.
     */
    fun complete(response: Res)

    /**
     * Cancels the wizard.
     *
     * @param reason The cancellation reason.
     */
    fun cancel(reason: CancelReason = CancelReason.USER)

    /**
     * Suspends until the wizard is completed.
     *
     * @return The outcome of the wizard.
     */
    suspend fun await(): PromptOutcome<Res>
}
