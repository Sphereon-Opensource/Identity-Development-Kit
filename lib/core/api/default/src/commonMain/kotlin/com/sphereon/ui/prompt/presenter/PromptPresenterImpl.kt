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

package com.sphereon.ui.prompt.presenter

import com.sphereon.di.session.SessionScope
import com.sphereon.ui.prompt.core.PromptPriority
import com.sphereon.ui.prompt.core.PromptRequest
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Default implementation of PromptPresenter.
 */
@Inject
@ContributesBinding(SessionScope::class, binding = binding<PromptPresenter>())
@SingleIn(SessionScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("PromptPresenterImpl", exact = true)
class PromptPresenterImpl : PromptPresenter {
    private val _foregroundState = MutableStateFlow(ForegroundState.UNKNOWN)

    override val foregroundState: StateFlow<ForegroundState> = _foregroundState.asStateFlow()

    override val canPresentPrompts: Boolean
        get() = _foregroundState.value != ForegroundState.BACKGROUND

    override fun onForeground() {
        _foregroundState.value = ForegroundState.FOREGROUND
    }

    override fun onBackground() {
        _foregroundState.value = ForegroundState.BACKGROUND
    }

    override fun shouldDeferPrompt(request: PromptRequest): Boolean {
        val hint = request.presentationHint

        // Never defer critical prompts
        if (hint.priority == PromptPriority.CRITICAL) {
            return false
        }

        // If deferUntilForeground is set and we're in background, defer
        if (hint.deferUntilForeground && _foregroundState.value == ForegroundState.BACKGROUND) {
            return true
        }

        // If we're in background and can't show over lock screen, defer
        if (_foregroundState.value == ForegroundState.BACKGROUND && !hint.showOverLockScreen) {
            return true
        }

        return false
    }
}
