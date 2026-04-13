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

import kotlin.experimental.ExperimentalObjCName
import com.sphereon.core.compat.JsExportCompat
import kotlin.native.ObjCName
import kotlin.time.Duration

/**
 * Hints for how a prompt should be presented to the user.
 * These are suggestions that the UI layer may or may not honor.
 *
 * @property priority The scheduling priority for this prompt.
 * @property deferUntilForeground If true, the prompt should wait until the app is in foreground.
 * @property timeout Optional timeout after which the prompt should be cancelled.
 * @property dismissOnBackground If true, the prompt should be cancelled when app goes to background.
 * @property showOverLockScreen If true, the prompt can be shown even when device is locked.
 */
@OptIn(ExperimentalObjCName::class)
@JsExportCompat
@ObjCName("PromptPresentationHint", exact = true)
data class PromptPresentationHint(
    val priority: PromptPriority = PromptPriority.NORMAL,
    val deferUntilForeground: Boolean = false,
    val timeout: Duration? = null,
    val dismissOnBackground: Boolean = false,
    val showOverLockScreen: Boolean = false
) {
    companion object {
        /**
         * Default presentation hint with normal priority.
         */
        val DEFAULT = PromptPresentationHint()

        /**
         * Presentation hint for critical prompts that should be shown immediately.
         */
        val CRITICAL = PromptPresentationHint(
            priority = PromptPriority.CRITICAL,
            showOverLockScreen = true
        )

        /**
         * Presentation hint for prompts that can wait until the app is in foreground.
         */
        val DEFERRED = PromptPresentationHint(
            priority = PromptPriority.LOW,
            deferUntilForeground = true
        )
    }
}
