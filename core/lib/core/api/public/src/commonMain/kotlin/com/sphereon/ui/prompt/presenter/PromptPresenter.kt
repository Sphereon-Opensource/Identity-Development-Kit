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

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.core.compat.JsExportIgnoreCompat
import com.sphereon.di.session.SessionScope
import com.sphereon.ui.prompt.core.PromptRequest
import dev.zacsweers.metro.ContributesTo
import kotlinx.coroutines.flow.StateFlow
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Represents the foreground state of the application.
 */
@OptIn(ExperimentalObjCName::class)
@JsExportCompat
@ObjCName("ForegroundState", exact = true)
enum class ForegroundState {
    /**
     * Application is in the foreground and visible.
     */
    FOREGROUND,

    /**
     * Application is in the background.
     */
    BACKGROUND,

    /**
     * Foreground state is unknown or not applicable.
     */
    UNKNOWN,
}

/**
 * Interface for managing prompt presentation based on foreground state.
 *
 * The presenter is responsible for:
 * - Tracking the application's foreground state
 * - Providing information about whether prompts can be displayed
 * - Allowing the UI layer to signal foreground changes
 */
@OptIn(ExperimentalObjCName::class)
@JsExportCompat
@ObjCName("PromptPresenter", exact = true)
interface PromptPresenter {
    /**
     * Current foreground state of the application.
     */
    val foregroundState: StateFlow<ForegroundState>

    /**
     * Whether prompts can currently be presented.
     */
    val canPresentPrompts: Boolean

    /**
     * Signals that the application has entered the foreground.
     */
    fun onForeground()

    /**
     * Signals that the application has entered the background.
     */
    fun onBackground()

    /**
     * Checks if a specific prompt should be deferred based on current state.
     *
     * @param request The prompt request to check.
     * @return true if the prompt should be deferred, false if it can be shown now.
     */
    fun shouldDeferPrompt(request: PromptRequest): Boolean

    @JsExportIgnoreCompat
    @ContributesTo(SessionScope::class)
    interface Graph {
        val promptPresenter: PromptPresenter
    }
}
